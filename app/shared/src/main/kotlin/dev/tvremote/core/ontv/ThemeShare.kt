package dev.tvremote.core.ontv
import dev.tvremote.core.B64
import dev.tvremote.core.adb.Adb
import dev.tvremote.core.picture.Jpeg
import dev.tvremote.core.picture.Tv
import dev.tvremote.home.HomeConfig
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sharing one of the person's own TV Home themes with the community store (a Cloudflare worker, review before anyone sees it). Done
 * here, on the device that holds the ADB link, so the page needs no CORS and the phone's web view needs nothing special:
 * the theme is put on (and the look that was on is put back after), a real screenshot of TV Home is taken, shrunk to a JPEG under
 * [PREVIEW_MAX], the theme's own photo wall (if it has one) is read from TV Home's files, and all of it is POSTed as the worker asks:
 * {tvtheme:1, name, by, lang, conf, preview (base64 jpeg), wall? (base64 jpeg)}. A job runs in the background; the page asks [Job.json]
 * until it is over and can [Job.cancel] it. Its end is `state` "done" (with `id`) or "error" with a short `error` code the page has words for.
 */
object ThemeShare {
    /** The store's address; tests point it at a stub with TVR_THEME_STORE (or -Dtvr.themestore). */
    val STORE: String get() = System.getenv("TVR_THEME_STORE") ?: System.getProperty("tvr.themestore") ?: "https://tvr-themes.99-hoangtran.workers.dev/v1/themes"
    const val PREVIEW_MAX = 150 * 1024
    const val WALL_MAX = 1024 * 1024
    private const val BODY_MAX = 1_600_000
    private const val SETTLE_MS = 3500L                              // TV Home draws the new look (a web page background gets more)
    private const val SETTLE_WEB_MS = 7000L

    class Cancelled : IOException("cancelled")
    class Fail(val code: String, msg: String = code) : IOException(msg)

    class Job {
        @Volatile var state = "run"                                  // run | done | error | cancelled
        @Volatile var step = "apply"                                 // apply | shot | photo | send
        @Volatile var error = ""
        @Volatile var id = ""
        @Volatile var shotMs = 0L; @Volatile var shotKb = 0L; @Volatile var previewKb = 0L; @Volatile var wallKb = 0L
        @Volatile var cancel = false
        @Volatile internal var conn: HttpURLConnection? = null
        fun cancel() { cancel = true; conn?.disconnect() }
        fun json(): JSONObject = JSONObject().put("state", state).put("step", step).put("error", error).put("id", id)
            .put("shot_ms", shotMs).put("shot_kb", shotKb).put("preview_kb", previewKb).put("wall_kb", wallKb)
    }

    /** Starts the job for the theme [themeId] named by [by]; [lang] is "vi" or "en". */
    fun start(adb: Adb, themeId: String, by: String, lang: String): Job {
        val job = Job()
        Thread({
            try { run(job, adb, themeId, HomeConfig.themeName(by), if (lang == "vi") "vi" else "en"); if (job.state == "run") job.state = "done" }
            catch (e: Cancelled) { job.state = "cancelled" }
            catch (e: Fail) { job.error = e.code; job.state = "error" }
            catch (e: IOException) { job.error = if (job.cancel) "cancelled" else "tv"; job.state = if (job.cancel) "cancelled" else "error" }
            catch (e: Exception) { job.error = "tv"; job.state = "error" }
        }, "themeshare").apply { isDaemon = true }.start()
        return job
    }

    private fun check(j: Job) { if (j.cancel) throw Cancelled() }
    private fun wait(j: Job, ms: Long) { var t = 0L; while (t < ms) { check(j); Thread.sleep(250); t += 250 } }

    private fun run(j: Job, adb: Adb, themeId: String, by: String, lang: String) {
        if (by.isEmpty()) throw Fail("by")
        val sh: (String) -> String = { adb.shell(it) }
        val now = Home.call(sh, null)
        if (!now.optBoolean("installed")) throw Fail("home_missing")
        val cfg = now.optJSONObject("config") ?: throw Fail("home_missing")
        if (!now.optJSONObject("config")!!.optBoolean("themesOk")) throw Fail("home_old")
        val mine = cfg.optJSONArray("themes")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }?.firstOrNull { it.optString("id") == themeId }
            ?: throw Fail("theme_missing")
        val name = mine.optString("name"); val conf = HomeConfig.pickTheme(mine.optJSONObject("conf") ?: JSONObject())
        val wall = conf.optString("wall")
        val rotation = conf.optJSONArray("walls")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        if (wall.startsWith("vid:") || rotation.any { it.startsWith("vid:") }) throw Fail("video")
        val photo = HomeConfig.isPhoto(wall)
        val was = cfg.optString("theme")                              // the look on now: a saved theme's id, or "" (then TV Home keeps it as "before")
        var preview: ByteArray? = null; var wallJpeg: ByteArray? = null
        try {
            check(j)
            if (was != themeId) Home.call(sh, JSONObject().put("theme", JSONObject().put("apply", themeId)))
            sh("input keyevent 3")                                    // HOME: TV Home in front, whatever was there
            wait(j, if (wall == HomeConfig.WEB || wall == HomeConfig.BING) SETTLE_WEB_MS else SETTLE_MS)
            j.step = "shot"; check(j)
            if (!sh("dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'").contains(HomeConfig.PKG + "/")) throw Fail("not_home")
            val t0 = System.currentTimeMillis()
            val png = Tv.screencap(adb) ?: throw Fail("shot")
            j.shotMs = System.currentTimeMillis() - t0; j.shotKb = png.size / 1024L
            preview = Jpeg.fromPng(png, 1280, 720, PREVIEW_MAX) ?: throw Fail("shot")
            j.previewKb = preview.size / 1024L
            if (photo) {
                j.step = "photo"; check(j)
                val b = readFile(adb, HomeConfig.photoPath(wall))
                if (b.size < 4 || b[0] != 0xFF.toByte() || b[1] != 0xD8.toByte()) throw Fail("photo_missing")
                if (b.size > WALL_MAX) throw Fail("photo_big")
                wallJpeg = b; j.wallKb = b.size / 1024L
            }
        } finally {
            if (was != themeId) try { Home.call(sh, JSONObject().put("theme", JSONObject().put("apply", was.ifEmpty { HomeConfig.BEFORE }))) } catch (e: Exception) { }   // the person's look is put back
        }
        j.step = "send"; check(j)
        val body = JSONObject().put("tvtheme", 1).put("name", name).put("by", by).put("lang", lang).put("conf", conf)
            .put("preview", B64.encode(preview!!))
        if (wallJpeg != null) body.put("wall", B64.encode(wallJpeg))
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > BODY_MAX) throw Fail("too_big")
        j.id = post(j, bytes)
    }

    @Throws(IOException::class)
    private fun readFile(adb: Adb, path: String): ByteArray {
        val out = ByteArrayOutputStream(); val buf = ByteArray(65536)
        adb.open("exec:cat ${Adb.q(path)}").use { while (true) { val n = it.read(buf, 0, buf.size, 10000); if (n <= 0) break; out.write(buf, 0, n); if (out.size() > WALL_MAX * 4) break } }
        return out.toByteArray()
    }

    /** The POST: 202 {id}; 400 / 413 / 429 {error} become codes; anything that does not reach the worker is "network". */
    private fun post(j: Job, body: ByteArray): String {
        val c = URL(STORE).openConnection() as HttpURLConnection
        j.conn = c
        try {
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10000; c.readTimeout = 60000
            c.setRequestProperty("Content-Type", "application/json"); c.setFixedLengthStreamingMode(body.size)
            try { c.outputStream.use { it.write(body) } } catch (e: IOException) { check(j); throw Fail("network") }
            val code = try { c.responseCode } catch (e: IOException) { check(j); throw Fail("network") }
            val text = try { (if (code < 400) c.inputStream else c.errorStream)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: "" } catch (e: IOException) { "" }
            val o = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
            if (code == 202 || code == 200) return o.optString("id").ifEmpty { "ok" }
            val msg = o.optString("error").lowercase()
            throw Fail(when {
                code == 413 -> "too_big"
                code == 429 && msg.contains("queue_full") -> "queue"
                code == 429 -> "limit"                       // "daily_limit": 3 a day
                code == 400 -> "rejected"
                else -> "server"
            }, msg.ifEmpty { "http $code" })
        } finally { c.disconnect(); j.conn = null }
    }
}
