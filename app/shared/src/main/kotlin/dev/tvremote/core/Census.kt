package dev.tvremote.core
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * The anonymous census: which TV models the apps ran on, so that the ones that work (and the ones that do not) are known.
 * One POST of [payload] to [url] + `/v1/ping`, and nothing else: a random id made once per install, the app and its version,
 * eight facts about the TV's model (Android's own build properties) and three yes / no results. No address, serial, Android ID,
 * app list, account, Wi-Fi name or the name a person gave the TV. Off with the Settings switch ("Send anonymous stats", ON by
 * default): then nothing is sent and nothing is asked of the TV. Sent at most once a day per (app, TV model), again when the
 * app's version changed or a result became true. Never throws to its callers and never waits on the main thread.
 */
object Census {
    /** The server. ONE place to change; a host containing REPLACE (a placeholder) is never sent to. */
    const val URL = "https://tvr-census.99-hoangtran.workers.dev"

    /** Where the app keeps the switch, the id and what it sent (prefs on Android, tvs.json on the computer). */
    interface Store {
        fun get(key: String): String?
        fun put(key: String, value: String)
    }

    @Volatile var store: Store? = null
    @Volatile var app = ""                                            // setup | phone | pc
    @Volatile var version = ""
    /** TVR_CENSUS_URL (computer) or [useTestFile] (apps): a local catcher, to see what would be sent. */
    @Volatile var url: String = System.getenv("TVR_CENSUS_URL")?.trim()?.takeIf { it.isNotEmpty() } ?: URL
    @Volatile var clock: () -> Long = System::currentTimeMillis
    /** false: work inline (tests). */
    @Volatile var background = true
    /** The send: true when the server took it. Replaced in tests. */
    @Volatile var post: (String, String) -> Boolean = ::httpPost

    fun init(app: String, version: String, store: Store) { this.app = app; this.version = version; this.store = store }

    /** The apps (no environment): `census-url.txt` in the app's external files folder, if someone put one there (emulator tests). */
    fun useTestFile(dir: File?) {
        val u = try { File(dir, "census-url.txt").takeIf { it.isFile }?.readText()?.trim() } catch (e: IOException) { null }
        if (!u.isNullOrEmpty()) url = u
    }

    var enabled: Boolean
        get() = store?.get("census.on") != "0"
        set(v) { store?.put("census.on", if (v) "1" else "0") }

    private fun live() = enabled && app.isNotEmpty() && store != null && !url.contains("REPLACE")

    /** A random id, made once for this install. It tells installs apart and nothing more. */
    fun id(): String {
        val s = store ?: return ""
        s.get("census.id")?.takeIf { it.length == 36 }?.let { return it }
        return UUID.randomUUID().toString().also { s.put("census.id", it) }
    }

    // ------------------------------------------------------------------ what the TV says about its model

    /** One call over ADB: all properties, then a line with whether the TV is an Android TV (leanback). */
    const val CMD = "getprop; echo @@; pm list features | grep -c software.leanback"

    class Props(val map: Map<String, String>, val leanback: Boolean)

    /** `[ro.product.model]: [Chromecast]` lines; after the `@@` line, the count of leanback features. */
    fun parse(text: String): Props {
        val m = HashMap<String, String>()
        var lean = false
        var after = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line == "@@") { after = true; continue }
            if (after) { if (line.toIntOrNull()?.let { it > 0 } == true) lean = true; continue }
            if (!line.startsWith("[")) continue
            val k = line.substringAfter('[').substringBefore("]: [", "")
            if (k.isEmpty() || !line.endsWith("]")) continue
            if (k in KEYS) m[k] = line.substringAfter("]: [").removeSuffix("]")
        }
        return Props(m, lean)
    }

    private val KEYS = setOf("ro.product.brand", "ro.product.model", "ro.product.device", "ro.product.board", "ro.hardware",
        "ro.soc.model", "ro.board.platform", "ro.build.version.release", "ro.build.version.sdk")

    private fun clean(s: String?): String = (s ?: "").filter { !it.isISOControl() }.trim().take(64)

    /** Three results; null = not known (yet). */
    class Ok(val adb: Boolean? = null, val video: Boolean? = null, val optimize: Boolean? = null) {
        /** The yes results as bits, for the "a result became true" test. */
        val mask: Int get() = (if (adb == true) 1 else 0) or (if (video == true) 2 else 0) or (if (optimize == true) 4 else 0)
    }

    /** The whole body of a ping: these fields and no others. */
    fun payload(id: String, app: String, v: String, p: Props, ok: Ok): JSONObject {
        val m = p.map
        val tv = JSONObject()
            .put("brand", clean(m["ro.product.brand"])).put("model", clean(m["ro.product.model"]))
            .put("device", clean(m["ro.product.device"])).put("board", clean(m["ro.product.board"]))
            .put("hw", clean(m["ro.hardware"]))
            .put("soc", clean(m["ro.soc.model"]).ifEmpty { clean(m["ro.board.platform"]) })
            .put("android", clean(m["ro.build.version.release"])).put("sdk", m["ro.build.version.sdk"]?.trim()?.toIntOrNull() ?: 0)
            .put("leanback", p.leanback)
        return JSONObject().put("id", id).put("app", app).put("v", clean(v)).put("tv", tv)
            .put("ok", JSONObject().put("adb", ok.adb ?: JSONObject.NULL).put("video", ok.video ?: JSONObject.NULL).put("optimize", ok.optimize ?: JSONObject.NULL))
    }

    // ------------------------------------------------------------------ the once-a-day gate

    /** [prev] is what was kept after the last send: `day|version|mask`. Due when none, the day or version changed, or a result became true. */
    fun due(prev: String?, day: Long, ver: String, mask: Int): Boolean {
        if (prev == null) return true
        val p = prev.split('|')
        if (p.size != 3) return true
        val was = p[2].toIntOrNull() ?: return true
        return p[0] != day.toString() || p[1] != ver || (mask and was.inv()) != 0
    }

    // ------------------------------------------------------------------ the events the apps report

    private class State(var props: Props? = null, var adb: Boolean? = null, var video: Boolean? = null, var optimize: Boolean? = null)
    private val tvs = HashMap<String, State>()                        // by the address, in memory only: it is never sent
    private val sendLock = Any()
    private val retryAt = HashMap<String, Long>()                     // a failed send waits before it is tried again

    /** TV Setup only: its own build properties (it is the TV), instead of asking through ADB. */
    @Volatile var self: (() -> Props)? = null

    /** ADB to the TV at [host] works: ask its model once (one call through [shell]) and report. */
    fun connected(host: String, shell: (String) -> String) = later {
        val s = synchronized(tvs) { tvs.getOrPut(host) { State() } }
        if (s.props == null) s.props = self?.invoke() ?: parse(shell(CMD))
        s.adb = true
        send(host)
    }

    /** TV Setup: its own ADB link did not come up. Only TV Setup can say so (it knows its model without ADB). */
    fun failed(host: String) = later {
        val own = self ?: return@later
        val s = synchronized(tvs) { tvs.getOrPut(host) { State() } }
        if (s.adb == true) return@later
        if (s.props == null) s.props = own()
        s.adb = false
        send(host)
    }

    /** A scrcpy picture arrived from the TV at [host]. */
    fun video(host: String) = later { mark(host) { it.video = true } }

    /** Optimize TV ran to the end on the TV at [host]. */
    fun optimize(host: String) = later { mark(host) { it.optimize = true } }

    private fun mark(host: String, f: (State) -> Unit) {
        if (!live()) return
        val s = synchronized(tvs) { tvs[host] } ?: return           // no model known for it (reached without ADB): nothing worth sending
        f(s)
        send(host)
    }

    private fun send(host: String) {
        val s = synchronized(tvs) { tvs[host] } ?: return
        val p = s.props ?: return
        val model = clean(p.map["ro.product.model"])
        if (model.isEmpty()) return
        val st = store ?: return
        val ok = Ok(s.adb, s.video, s.optimize)
        val key = "census.sent." + app + "." + model.lowercase().replace(Regex("[^a-z0-9]+"), "_")
        val day = clock() / 86_400_000L
        synchronized(sendLock) {                                    // one at a time: a result that arrives meanwhile is sent after
            if (!due(st.get(key), day, version, ok.mask)) return
            if ((retryAt[key] ?: 0L) > clock()) return
            if (post(url.trimEnd('/') + "/v1/ping", payload(id(), app, version, p, ok).toString())) {
                st.put(key, "$day|$version|${ok.mask}")
                retryAt.remove(key)
            } else retryAt[key] = clock() + 600_000                  // ten minutes before the next try
        }
    }

    /** Off the caller's thread, and whatever goes wrong stays here. */
    private fun later(f: () -> Unit) {
        if (!live()) return                                           // off: not even a thread
        if (!background) { try { f() } catch (e: Throwable) { } ; return }
        Thread({ try { f() } catch (e: Throwable) { } }, "census").apply { isDaemon = true }.start()
    }

    private fun httpPost(to: String, body: String): Boolean = try {
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

    // ------------------------------------------------------------------ crash reports (the error box / dialog, with the same switch)

    /** The device facts a crash carries, like a ping's `tv`: the computer says os / arch / java, the Android apps (CensusAndroid) their Build. */
    @Volatile var device: () -> JSONObject = {
        JSONObject().put("os", System.getProperty("os.name", "") + " " + System.getProperty("os.version", "")).put("arch", System.getProperty("os.arch", ""))
            .put("java", System.getProperty("java.version", ""))
    }
    private val crashSent = HashSet<String>()                         // errors already reported in this run
    @Volatile private var crashRetryAt = 0L                           // after a failed send, a minute of quiet

    private val LINES = Regex("(:\\d+){1,2}(?=\\)|$)")
    /** "The same error": the kind and the first five frames without their line numbers. */
    fun crashKey(kind: String, stack: String): String =
        kind + stack.lineSequence().map { it.trim() }.filter { it.startsWith("at ") || it.contains('@') }.take(5).joinToString("\n", "\n") { it.replace(LINES, "") }

    /**
     * Report one error, scrubbed (Scrub): [where] is "kotlin" or "page". Sent once per error per run, only when the stats switch is on,
     * blocking up to the 4 s timeout: callers on the UI thread use [crash]. true = the server has it (now or earlier in this run).
     */
    fun crashNow(where: String, kind: String, msg: String, stack: String): Boolean {
        if (!live()) return false
        val k = Scrub.text(kind).take(120).ifBlank { "Error" }
        val m = Scrub.text(msg).replace(Regex("\\s+"), " ").trim().take(300)
        val st = Scrub.text(stack).trim().take(8192)
        val key = crashKey(k, st)
        synchronized(crashSent) { if (key in crashSent) return true }
        if (clock() < crashRetryAt) return false
        val body = JSONObject().put("id", id()).put("app", app).put("v", clean(version)).put("where", where)
            .put("dev", try { device() } catch (e: Throwable) { JSONObject() }).put("kind", k).put("msg", m).put("stack", st).toString()
        return if (post(url.trimEnd('/') + "/v1/crash", body)) { synchronized(crashSent) { crashSent.add(key) }; true }
        else { crashRetryAt = clock() + 60_000; false }
    }

    /** [crashNow] off the caller's thread; [done] gets the answer (on that thread). */
    fun crash(where: String, kind: String, msg: String, stack: String, done: ((Boolean) -> Unit)? = null) {
        if (!live()) { done?.invoke(false); return }
        later { done?.invoke(crashNow(where, kind, msg, stack)) }
    }

    /** For tests: forget what is kept in memory. */
    fun reset() { synchronized(tvs) { tvs.clear() }; synchronized(sendLock) { retryAt.clear() }; synchronized(crashSent) { crashSent.clear() }; crashRetryAt = 0L }
}
