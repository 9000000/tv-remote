package dev.tvremote.core

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The sending core of the anonymous stats, with no other dependency (plain java.net + org.json), so that Census (:shared, :desktop) and
 * TV Home, TV Channels and TV Games (OwnReport, no :shared) use the very same code: the crash body, the ping body, the daily gate,
 * the POST. Source set `app/shared/src/report`, compiled into every one of them.
 */
object Report {
    /** The server. ONE place to change; a host containing REPLACE (a placeholder) is never sent to. */
    const val URL = "https://tvr-census.99-hoangtran.workers.dev"

    /** One POST of a JSON body: true when the server took it. Short timeouts, whatever goes wrong is a plain false. */
    fun httpPost(to: String, body: String): Boolean = try {
        val c = URL(to).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 4000
            c.readTimeout = 4000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
            c.responseCode in 200..299
        } finally { c.disconnect() }
    } catch (e: Exception) { false }

    fun clean(s: String?): String = (s ?: "").filter { !it.isISOControl() }.trim().take(64)

    /**
     * Whose switch counts: TV Setup's when it is on the TV and answered ([setup] true / false), else the app's own ([own], on by default).
     * null = TV Setup missing, older than the question, or signed by another key.
     */
    fun decide(setup: Boolean?, own: Boolean): Boolean = setup ?: own

    /** The install id to send: the one TV Setup keeps on this TV (one TV is then one install), else the app's own. */
    fun chooseId(setup: String?, own: String): String = setup?.takeIf { it.length == 36 } ?: own

    // ------------------------------------------------------------------ the daily ping

    /** [prev] is what was kept after the last send: `day|version|mask`. Due when none, the day or version changed, or a result became true. */
    fun due(prev: String?, day: Long, ver: String, mask: Int): Boolean {
        if (prev == null) return true
        val p = prev.split('|')
        if (p.size != 3) return true
        val was = p[2].toIntOrNull() ?: return true
        return p[0] != day.toString() || p[1] != ver || (mask and was.inv()) != 0
    }

    /** The whole body of a ping: these fields and no others. [m] holds Android's build properties (ro.product.model ...). */
    fun pingBody(id: String, app: String, v: String, m: Map<String, String>, leanback: Boolean, adb: Boolean?, video: Boolean?, optimize: Boolean?): JSONObject {
        val tv = JSONObject()
            .put("brand", clean(m["ro.product.brand"])).put("model", clean(m["ro.product.model"]))
            .put("device", clean(m["ro.product.device"])).put("board", clean(m["ro.product.board"]))
            .put("hw", clean(m["ro.hardware"]))
            .put("soc", clean(m["ro.soc.model"]).ifEmpty { clean(m["ro.board.platform"]) })
            .put("android", clean(m["ro.build.version.release"])).put("sdk", m["ro.build.version.sdk"]?.trim()?.toIntOrNull() ?: 0)
            .put("leanback", leanback)
        return JSONObject().put("id", id).put("app", app).put("v", clean(v)).put("tv", tv)
            .put("ok", JSONObject().put("adb", adb ?: JSONObject.NULL).put("video", video ?: JSONObject.NULL).put("optimize", optimize ?: JSONObject.NULL))
    }

    // ------------------------------------------------------------------ crash reports

    private val LINES = Regex("(:\\d+){1,2}(?=\\)|$)")

    /** "The same error": the kind and the first five frames without their line numbers. */
    fun crashKey(kind: String, stack: String): String =
        kind + stack.lineSequence().map { it.trim() }.filter { it.startsWith("at ") || it.contains('@') }.take(5).joinToString("\n", "\n") { it.replace(LINES, "") }

    /** The body of a crash report, every free text scrubbed (Scrub). [where] is "kotlin" or "page"; [dev] the device facts. */
    fun crashBody(id: String, app: String, v: String, where: String, dev: JSONObject, kind: String, msg: String, stack: String): JSONObject {
        val k = Scrub.text(kind).take(120).ifBlank { "Error" }
        val m = Scrub.text(msg).replace(Regex("\\s+"), " ").trim().take(300)
        val st = Scrub.text(stack).trim().take(8192)
        return JSONObject().put("id", id).put("app", app).put("v", clean(v)).put("where", where).put("dev", dev).put("kind", k).put("msg", m).put("stack", st)
    }

    /** Once per error per run, and after a failed send a minute of quiet. */
    class Once(private val clock: () -> Long) {
        private val sent = HashSet<String>()
        @Volatile private var retryAt = 0L

        /** true = the server has it (now or earlier in this run). [body] builds the JSON only when a send is needed. */
        fun send(to: String, key: String, post: (String, String) -> Boolean, body: () -> JSONObject): Boolean {
            synchronized(sent) { if (key in sent) return true }
            if (clock() < retryAt) return false
            return if (post(to, body().toString())) { synchronized(sent) { sent.add(key) }; true } else { retryAt = clock() + 60_000; false }
        }

        fun reset() { synchronized(sent) { sent.clear() }; retryAt = 0L }
    }

    /** Off the caller's thread, and whatever goes wrong stays there. */
    fun later(name: String, f: () -> Unit) {
        Thread({ try { f() } catch (e: Throwable) { } }, name).apply { isDaemon = true }.start()
    }
}
