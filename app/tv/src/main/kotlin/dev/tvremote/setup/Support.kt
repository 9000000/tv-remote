package dev.tvremote.setup

import android.content.Context
import android.os.Build
import dev.tvremote.core.Census
import dev.tvremote.core.Scrub
import dev.tvremote.core.WireLog
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Help for a TV nobody here can reach (an FPT Play behind its ISP's lock, 2026-10-25: AnyDesk, Tailscale, then a pairing code
 * read out over TeamViewer, one evening for four answers). [send]: the facts AdbReport gathers and this app's log go to the census
 * worker (dev/census/src/support.js) on a press, and the TV shows the 8-digit code to read out. [open]: for [MINUTES] the owner
 * may GET a few of this app's own read-only routes (`dev/census/support.py CODE /api/state`); the TV asks the worker every 2 s,
 * runs them on its own server with a pass of its own, listed among the paired devices while it lasts, and stops when the time
 * is up, on Stop, or when TV Setup restarts. Nothing is ever pressed or changed: GET only, [ROUTES] only.
 */
object Support {
    const val MINUTES = 30
    private const val PREFS = "support"
    private const val KIND = "support"
    // read-only answers worth having from afar; never the clipboard, files, the screen or anything that acts
    private val ROUTES = setOf("/api/about", "/api/state", "/api/diagnose", "/api/setup/version", "/api/setup/need", "/api/wirelog",
        "/api/apps", "/api/playing", "/api/caps", "/api/rules", "/api/paired", "/api/health", "/api/stats", "/api/tailscale")

    @Volatile private var thread: Thread? = null
    @Volatile var until = 0L; private set
    @Volatile var code = ""; private set
    @Volatile var asked = 0; private set
    @Volatile private var lastKey = ""
    @Volatile private var liveKey = ""                             // the open code's key: a later send must not make Stop close another
    /** Sends today has left after the last one, as the worker counts them (20 a day); -1 not known, 0 with no code: none left. */
    @Volatile var left = -1; private set

    fun open() = until > System.currentTimeMillis() && thread != null

    private fun base() = Census.url.trimEnd('/') + "/v1/support"

    /** The report as sent: what AdbReport reads without ADB, this app's state, its log. */
    fun report(c: Context, version: String): String = Scrub.blank(buildString {
        append("TV Setup ").append(version).append(" · adb ").append(RemoteService.adbState()).append(' ').append(RemoteService.adbDetail()).append('\n')
        append(AdbReport.facts(c, RemoteService.adbState(), RemoteService.adbDetail())).append('\n')
        append(WireLog.all.report().takeLast(48_000))
    })

    /** Sends the report; the code to read out, or null when the worker cannot be reached. Runs off the main thread. */
    fun send(c: Context, version: String): String? {
        val body = JSONObject().put("id", Census.id().ifEmpty { java.util.UUID.randomUUID().toString() }).put("app", "setup").put("v", version).put("model", Build.MODEL).put("report", fit(report(c, version), 56_000, tail = true))
        left = -1
        val r = post(base(), body) ?: return null
        left = r.optInt("left", -1)
        val got = r.optString("code").takeIf { it.length == 8 } ?: return null
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("code", got).putString("key", r.optString("key")).apply()
        lastKey = r.optString("key")
        if (!open()) code = got                                       // while support looks, Stop is about that code, not this one
        return got
    }

    /** Lets the owner read for [MINUTES], sending a fresh report first when there is none from today. [said] gets the code, or null. */
    fun start(c: Context, version: String, said: (String?) -> Unit) {
        val app = c.applicationContext
        Thread({
            val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val code = send(app, version) ?: return@Thread said(null)   // always fresh: the facts of now with the code
            val key = lastKey
            val r = post("${base()}/live", JSONObject().put("code", code).put("key", key).put("minutes", MINUTES))?.takeIf { it.has("live") } ?: return@Thread said(null)
            stopThread()
            until = ends(r, System.currentTimeMillis() + MINUTES * 60_000L)
            asked = 0; this.code = code; liveKey = key
            forgetPass(app)                                           // a second press before the first returned: one pass, not two
            val token = Paired.add(app, KIND, "Hỗ trợ / Support $code", "relay", false)
            val t = Thread({ loop(app, code, key, token) }, "support").apply { isDaemon = true }
            thread = t; t.start()                                     // assigned first: the loop runs only while it is [thread]
            WireLog.all.add("·", "support open $code for $MINUTES min")
            said(code)
        }, "support-start").apply { isDaemon = true }.start()
    }

    /** Stops at once: the worker is told (best effort), the pass is forgotten. */
    fun stop(c: Context) {
        val app = c.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = this.code.ifEmpty { p.getString("code", "") ?: "" }; val key = liveKey.ifEmpty { p.getString("key", "") ?: "" }
        until = 0
        stopThread()
        forgetPass(app)
        if (code.isNotEmpty()) Thread({ post("${base()}/live", JSONObject().put("code", code).put("key", key).put("minutes", 0)) }, "support-stop").apply { isDaemon = true }.start()
    }

    /** At start: a pass left by a TV Setup that was killed while open is not kept. */
    fun clean(c: Context) = forgetPass(c)

    private fun forgetPass(c: Context) { Paired.list(c).filter { it.kind == KIND }.forEach { Paired.forget(c, it.id) } }

    private fun stopThread() { thread?.interrupt(); thread = null }

    private fun loop(c: Context, code: String, key: String, token: String) {
        val me = Thread.currentThread()
        try {
            while (thread === me && System.currentTimeMillis() < until) {
                val r = post("${base()}/poll", JSONObject().put("code", code).put("key", key))
                if (r != null && r.has("error")) break                // the code is gone (deleted, expired): nobody can ask any more
                if (r != null) {
                    if (r.has("live")) until = ends(r, until)
                    val asks = r.optJSONArray("asks")
                    for (i in 0 until (asks?.length() ?: 0)) {
                        val a = asks!!.getJSONObject(i)
                        val (status, text) = answer(a.optString("path"), token)
                        asked++
                        WireLog.all.add("·", "support GET ${a.optString("path").take(80)} -> $status")
                        val sent = post("${base()}/answer", JSONObject().put("code", code).put("key", key).put("id", a.optInt("id")).put("status", status).put("body", text))
                        if (sent == null || sent.has("error"))           // refused or lost: say so, or the owner waits for nothing
                            post("${base()}/answer", JSONObject().put("code", code).put("key", key).put("id", a.optInt("id")).put("status", 0).put("body", "answer not delivered ($status, ${text.length} chars)"))
                    }
                }
                Thread.sleep(2000)
            }
        } catch (e: InterruptedException) { return }
        if (thread === me) { thread = null; until = 0; forgetPass(c) }
    }

    /** One GET on this app's own server, as a paired browser would; only [ROUTES]. */
    fun answer(path: String, token: String): Pair<Int, String> {
        val route = path.substringBefore('?')
        if (route !in ROUTES || ".." in path) return 403 to "not allowed for support: $route"
        return try {
            val c = URL("http://127.0.0.1:${RemoteService.PORT}$path").openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 3000; c.readTimeout = 25000
                c.setRequestProperty("Cookie", RemoteService.SELF_COOKIE + "=" + token)
                val code = c.responseCode
                val text = (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                code to fit(Scrub.blank(text), 200_000, tail = false)
            } finally { c.disconnect() }
        } catch (e: Exception) { 0 to (e.message ?: e.javaClass.simpleName) }
    }

    /** The end of support by this TV's clock: the worker's `live` is its own time, and a TV's clock may be half an hour off. */
    private fun ends(r: JSONObject, or: Long): Long {
        val live = r.optLong("live"); val now = r.optLong("now")
        return if (live <= 0) 0 else if (now > 0) System.currentTimeMillis() + (live - now) else or
    }

    /** [s] cut to at most [bytes] as it travels (JSON-escaped UTF-8: Vietnamese and quotes grow), keeping its end or its start. */
    fun fit(s: String, bytes: Int, tail: Boolean): String {
        var t = s
        while (JSONObject.quote(t).toByteArray().size > bytes) t = if (tail) t.takeLast(t.length * 4 / 5) else t.take(t.length * 4 / 5)
        return t
    }

    private fun post(to: String, body: JSONObject): JSONObject? = try {
        val c = URL(to).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 6000; c.readTimeout = 10000
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode == 403 || c.responseCode == 404 || c.responseCode == 429) JSONObject().put("error", c.responseCode).put("left", 0)   // refused: not a network hiccup to wait out
            else if (c.responseCode !in 200..299) null
            else c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }.let { if (it.isBlank()) JSONObject() else JSONObject(it) }
        } finally { c.disconnect() }
    } catch (e: Exception) { null }
}
