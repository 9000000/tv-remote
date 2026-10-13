package dev.tvremote.setup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import dev.tvremote.core.AdbLink
import dev.tvremote.core.HttpServer
import dev.tvremote.core.Manage
import dev.tvremote.core.Tv
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The web remote that lives on the TV: an HTTP server on port 8765 serving one page, and an input session to the TV's
 * own ADB (scrcpy-server without picture) that turns the page's button presses into key events.
 * A browser must be paired first with a code this service shows on the TV; after that it holds a cookie.
 */
class RemoteService : Service(), Tv.Listener, HttpServer.Handler {
    companion object {
        const val PORT = 8765
        private const val PREFS = "remote"
        private const val COOKIE = "tvr"
        private val KEYS: Map<String, Int> = HashMap<String, Int>().apply {
            putAll(mapOf("UP" to 19, "DOWN" to 20, "LEFT" to 21, "RIGHT" to 22, "OK" to 23, "BACK" to 4, "HOME" to 3, "MENU" to 82, "POWER" to 26, "WAKE" to 224,
                "VOL_UP" to 24, "VOL_DOWN" to 25, "MUTE" to 164, "PLAY_PAUSE" to 85, "NEXT" to 87, "PREV" to 88, "SETTINGS" to 176, "INPUT" to 178,
                "DEL" to 67, "ENTER" to 66, "ASSIST" to 219, "CH_UP" to 166, "CH_DOWN" to 167,
                "INFO" to 165, "GUIDE" to 172, "LIVE" to 170, "SUBTITLE" to 175, "REWIND" to 89, "STOP" to 86, "FFWD" to 90))
            for (d in 0..9) put(d.toString(), 7 + d)
        }

        // pairing: one code at a time, shown on the TV, good for two minutes and five guesses
        @Volatile private var code: String? = null
        @Volatile private var codeUntil = 0L
        @Volatile private var guesses = 0
        @Volatile private var running: RemoteService? = null
        @Volatile private var adbState = "connecting"

        fun pairingCode(): String? { val c = code; return if (c != null && System.currentTimeMillis() < codeUntil) c else null }
        fun cancelPairing() { code = null }
        fun isRunning(): Boolean = running != null
        /** connecting | allow | ready | error: the state of the input session, for the app's own screen. */
        fun adbState(): String = adbState

        fun enabled(c: Context): Boolean = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", true)

        fun setEnabled(c: Context, on: Boolean) {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
            if (on) start(c) else c.stopService(Intent(c, RemoteService::class.java))
        }

        fun start(c: Context) {
            val i = Intent(c, RemoteService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }

        fun pairedCount(c: Context): Int = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("tokens", HashSet())?.size ?: 0

        fun forgetAll(c: Context) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("tokens").apply() }

        private const val REST_MS = 3 * 60 * 1000L
        private fun ok(): HttpServer.Response = HttpServer.Response.ok()
    }

    private val ui = Handler(Looper.getMainLooper())
    private val random = SecureRandom()
    private val icons: MutableMap<String, ByteArray> = ConcurrentHashMap()
    private val held = HashMap<Int, Runnable>()
    private var http: HttpServer? = null
    private var link: AdbLink? = null                                 // for apps, files, installs and health; the keys go through `tv`
    private var manage: Manage? = null
    private lateinit var prefs: SharedPreferences
    @Volatile private var tv: Tv? = null
    @Volatile private var viewer: Tv? = null                         // a second session that carries the picture, only while a browser watches
    @Volatile private var stopping = false
    @Volatile private var lastUse = 0L                                // when a paired browser last asked for anything
    @Volatile private var resting = false                             // the input session is closed on purpose: nobody is using the remote
    private var lastPairStart = 0L
    private var page: ByteArray? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        running = this
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel("remote", "TV Remote", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        @Suppress("DEPRECATION")
        val n = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "remote") else Notification.Builder(this)
        startForeground(1, n.setSmallIcon(R.drawable.ic_power).setContentTitle("TV Remote").setContentText("Web remote: port $PORT").setOngoing(true).build())
        val server = HttpServer(this)
        http = server
        val l = AdbLink(filesDir, "127.0.0.1")
        link = l
        manage = Manage(l, object : Manage.Labels {
            override fun label(pkg: String): String? =
                try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { null }
            override fun icons(): Boolean = true
        }, getExternalFilesDir(null))
        try { server.start(PORT, false) } catch (e: Exception) { adbState = "error" }
        lastUse = System.currentTimeMillis()
        connect()
        ui.postDelayed(rest, 30000)
        ui.postDelayed(diary, 20000)
    }

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int = START_STICKY

    override fun onDestroy() {
        stopping = true
        running = null
        code = null
        http?.stop()
        link?.close()
        tv?.stop()
        viewer?.stop()
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- the input session to the TV's own ADB
    @Synchronized
    private fun connect() {
        if (stopping || tv != null) return
        resting = false
        adbState = "connecting"
        var mine: Tv? = null
        mine = Tv(this, "127.0.0.1", null, 0, 0, false, object : Tv.Listener {   // a session we have let go of no longer has a say
            override fun onStatus(key: String, detail: String) { if (mine == null || tv === mine) this@RemoteService.onStatus(key, detail) }
            override fun onVideoSize(w: Int, h: Int) { }
            override fun onNetwork(mac: String, lan: String) { }
        })
        tv = mine
    }

    /**
     * The input session keeps a scrcpy-server process alive on the TV, about 70 MB of its 2 GB. So it only runs while
     * somebody has the remote open: every request from a paired browser counts as use, and after [REST_MS] without one
     * the session is closed. The next request opens it again and waits a moment for it, so the first key still works.
     */
    private fun inUse() {
        lastUse = System.currentTimeMillis()
        if (tv == null && resting) connect()
        if (adbState == "connecting") for (i in 0 until 40) {         // up to 4 s, usually well under one
            if (adbState != "connecting") break
            try { Thread.sleep(100) } catch (e: InterruptedException) { break }
        }
    }

    /** The diary for the History view: one entry every five minutes, the first shortly after the service starts. */
    private val diary = object : Runnable {
        override fun run() {
            if (stopping) return
            Thread({
                val m = manage
                val on = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
                if (!usageAllowed) { m?.allowUsage(packageName); usageAllowed = true }
                stats.tick(if (on) m?.cpuNow() else null)             // asleep: do not wake ADB for a number nobody needs
            }, "diary").apply { isDaemon = true }.start()
            ui.postDelayed(this, 5 * 60 * 1000L)
        }
    }
    private val stats by lazy { Stats(this) }
    private var usageAllowed = false

    private val rest = object : Runnable {
        override fun run() {
            if (stopping) return
            val t = tv
            if (t != null && viewer == null && adbState == "ready" && System.currentTimeMillis() - lastUse > REST_MS) {
                resting = true
                tv = null
                t.stop()
                adbState = "ready"                                    // nothing is wrong; it comes back with the next request
            }
            ui.postDelayed(this, 30000)
        }
    }

    override fun onStatus(key: String, detail: String) {
        if (stopping) return
        when (key) {
            "allow" -> adbState = "allow"                             // the TV is asking "Allow USB debugging?"
            "live" -> adbState = "ready"
            "closed", "error" -> {                                    // ADB off, TV just booted, session dropped: keep trying
                if (resting) return                                   // we closed it ourselves
                adbState = if (key == "error") "error" else "connecting"
                tv = null
                ui.postDelayed({ connect() }, 5000)
            }
        }
    }

    override fun onVideoSize(w: Int, h: Int) { }
    override fun onNetwork(mac: String, lan: String) { }

    // ---------------------------------------------------------------- the picture, for one browser at a time
    /**
     * The TV's screen as a stream of packets: 1 byte kind (0 size: width, height; 1 codec config; 2 key frame; 3 frame;
     * 4 nothing new), 4 bytes length, then the data. A still picture sends nothing, so kind 4 goes out now and then:
     * that is how we notice the browser has left, and stop the encoder on the TV.
     */
    private fun video(quality: Int): HttpServer.Response {
        viewer?.stop()                                                // a new viewer takes over
        val q = java.util.concurrent.LinkedBlockingQueue<ByteArray>()
        val end = ByteArray(0)
        fun packet(kind: Int, data: ByteArray) = java.nio.ByteBuffer.allocate(5 + data.size).put(kind.toByte()).putInt(data.size).put(data).array()
        val v = Tv(this, "127.0.0.1", null, quality, 1280, false, object : Tv.Listener {
            override fun onStatus(key: String, detail: String) { if (key == "closed" || key == "error") q.add(end) }
            override fun onVideoSize(w: Int, h: Int) { q.add(packet(0, java.nio.ByteBuffer.allocate(8).putInt(w).putInt(h).array())) }
            override fun onNetwork(mac: String, lan: String) { }
        }) { config, key, data ->
            if (q.size > 150) q.add(end)                              // the browser cannot keep up: stop instead of filling memory
            else q.add(packet(if (config) 1 else if (key) 2 else 3, data))
        }
        viewer = v
        return HttpServer.Response.stream("application/octet-stream", -1) { out ->
            try {
                while (true) {
                    val p = q.poll(5, java.util.concurrent.TimeUnit.SECONDS) ?: packet(4, end)
                    if (p === end) break
                    out.write(p)
                    out.flush()
                }
            } finally {
                v.stop()
                if (viewer === v) viewer = null
            }
        }
    }

    // ---------------------------------------------------------------- HTTP
    @Throws(Exception::class)
    override fun handle(r: HttpServer.Request): HttpServer.Response {
        // a web page elsewhere must not be able to talk to us through the visitor's browser (DNS rebinding): only IPs are served
        val host = r.headers["host"].toString().replaceFirst(Regex(":\\d+$"), "")
        if (!host.matches(Regex("[0-9.]+|localhost|\\[[0-9a-fA-F:]+\\]"))) return HttpServer.Response.error(403, "open this page by the TV's IP address")

        if (r.path == "/" || r.path == "/index.html") {
            val page = HttpServer.Response.of("text/html; charset=utf-8", page())
            page.headers["Cache-Control"] = "no-store"
            return page
        }
        if (r.path == "/stats.js") {                                   // draws the History view
            val js = HttpServer.Response.of("text/javascript; charset=utf-8", assets.open("stats.js").use { it.readBytes() })
            js.headers["Cache-Control"] = "no-store"
            return js
        }
        if (r.path == "/jmuxer.min.js") {                              // turns the TV's H.264 into something a <video> can play
            val js = HttpServer.Response.of("text/javascript; charset=utf-8", assets.open("jmuxer.min.js").use { it.readBytes() })
            js.headers["Cache-Control"] = "max-age=86400"
            return js
        }
        if (r.path == "/api/pair/start" && r.method == "POST") return pairStart()
        if (r.path == "/api/pair/finish" && r.method == "POST") return pairFinish(r)
        if (!tokens().contains(r.cookie(COOKIE)) || r.cookie(COOKIE).isEmpty())
            return HttpServer.Response.json(401, JSONObject().put("error", "not paired").put("name", Build.MODEL))

        inUse()
        val post = r.method == "POST"
        when (r.path) {
            "/api/state" -> return HttpServer.Response.json(state())
            "/api/screenshot" -> {
                val png = tv?.screenshot() ?: return HttpServer.Response.error(503, "no screenshot")
                val shot = HttpServer.Response.of("image/png", png)
                shot.headers["Content-Disposition"] = "attachment; filename=\"tv-" + System.currentTimeMillis() / 1000 + ".png\""
                return shot
            }
            "/api/video" -> return video(r.q("q").toIntOrNull() ?: 1)
            "/api/touch" -> {
                val d = r.json()
                viewer?.let { if (it.ready()) it.touch(d.optInt("a"), d.optDouble("x").toFloat(), d.optDouble("y").toFloat()) }
                return ok()
            }
            "/api/apps" -> return HttpServer.Response.json(JSONObject().put("apps", apps()))
            "/api/icon" -> {
                val png = icon(r.query["pkg"].toString()) ?: return HttpServer.Response.error(404, "no icon")
                val icon = HttpServer.Response.of("image/png", png)
                icon.headers["Cache-Control"] = "max-age=86400"
                return icon
            }
        }
        val managed = manage?.handle(r)
        if (managed != null) return managed
        if (!post) return HttpServer.Response.error(404, "not found")
        val t = tv
        val d = r.json()
        when (r.path) {
            "/api/volume" -> {
                val am = getSystemService(AUDIO_SERVICE) as AudioManager
                am.setStreamVolume(AudioManager.STREAM_MUSIC, maxOf(0, minOf(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), d.optInt("level"))), AudioManager.FLAG_SHOW_UI)
                return HttpServer.Response.json(state())
            }
            "/api/key" -> {
                val keyCode = KEYS[d.optString("key").uppercase(Locale.ROOT)] ?: return HttpServer.Response.error(400, "unknown key")
                if (t == null || !t.ready()) return HttpServer.Response.error(503, "adb:$adbState")
                if (d.optString("action", "press") == "press" && keyCode in Tv.OPENERS)   // keys that should open something
                    return HttpServer.Response.json(JSONObject().put("ok", true).put("noop", !t.pressSeen(keyCode)))
                if (keyCode == 26) {                                  // sleep or wake by what the TV is doing now, so a tap never does the opposite
                    if (d.optString("action", "press") != "up") t.press(if ((getSystemService(POWER_SERVICE) as android.os.PowerManager).isInteractive) 223 else 224)
                } else if (keyCode == 176) { if (d.optString("action", "press") != "up") t.settings() }   // the key itself does nothing on many TVs
                else key(t, keyCode, d.optString("action", "press"))
                return ok()
            }
            "/api/text" -> {
                if (t == null || !t.ready()) return HttpServer.Response.error(503, "adb:$adbState")
                repeat(minOf(d.optInt("del"), 500)) { t.press(67) }    // live typing: what changed is deleted, then typed again
                if (d.optString("text").isNotEmpty()) t.text(d.optString("text"))
                if (d.optBoolean("enter")) t.press(66)
                return ok()
            }
            "/api/launch" -> {
                if (t == null || !t.ready()) return HttpServer.Response.error(503, "adb:$adbState")
                t.launch(d.optString("component"))
                return ok()
            }
            else -> return HttpServer.Response.error(404, "not found")
        }
    }

    private fun tokens(): Set<String> = prefs.getStringSet("tokens", HashSet()) ?: emptySet()

    /**
     * down / up are separate so holding a button in the browser is a long press on the TV. While it is held the key repeats
     * (fast scrolling, volume), and a lost "up" is sent for it after 8 s.
     */
    private fun key(t: Tv, keyCode: Int, action: String) {
        synchronized(held) {
            val old = held.remove(keyCode)
            if (old != null) ui.removeCallbacks(old)
            if (action == "down") {
                t.key(0, keyCode, 0, 0)
                val until = System.currentTimeMillis() + 8000
                val repeat = object : Runnable {
                    var count = 0
                    override fun run() {
                        synchronized(held) {
                            if (held[keyCode] !== this) return
                            if (System.currentTimeMillis() > until) { held.remove(keyCode); t.key(1, keyCode, 0, 0); return }
                            t.key(0, keyCode, ++count, 0)
                            ui.postDelayed(this, 80)
                        }
                    }
                }
                held[keyCode] = repeat
                ui.postDelayed(repeat, 450)
            } else if (action == "up") {
                t.key(1, keyCode, 0, 0)
            } else {
                if (old != null) t.key(1, keyCode, 0, 0)
                t.press(keyCode)
            }
        }
    }

    @Throws(Exception::class)
    private fun state(): JSONObject {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        var version: String? = ""
        try { version = packageManager.getPackageInfo(packageName, 0).versionName } catch (ignored: Exception) { }
        return JSONObject().put("name", Build.MODEL).put("maker", Build.MANUFACTURER).put("android", Build.VERSION.RELEASE).put("version", version)
            .put("adb", adbState).put("on", pm.isInteractive)
            .put("volume", am.getStreamVolume(AudioManager.STREAM_MUSIC)).put("volume_max", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            .put("muted", Build.VERSION.SDK_INT >= 23 && am.isStreamMute(AudioManager.STREAM_MUSIC))
    }

    // ---------------------------------------------------------------- pairing
    @Synchronized @Throws(Exception::class)
    private fun pairStart(): HttpServer.Response {
        val now = System.currentTimeMillis()
        if (now - lastPairStart < 3000) return HttpServer.Response.error(429, "wait a moment")
        lastPairStart = now
        code = String.format("%06d", random.nextInt(1000000))
        codeUntil = now + 120000
        guesses = 0
        // Since Android 10 a service may not open a screen by itself; the shell user (our ADB session) still may.
        val t = tv
        var shown = false
        if (t != null && t.ready()) {
            try { t.shell("am start -n $packageName/.PairActivity"); shown = true } catch (ignored: Exception) { }
        }
        if (!shown) {
            try { startActivity(Intent(this, PairActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (ignored: Exception) { }
        }
        return HttpServer.Response.json(JSONObject().put("ok", true).put("seconds", 120))
    }

    @Synchronized @Throws(Exception::class)
    private fun pairFinish(r: HttpServer.Request): HttpServer.Response {
        val want = pairingCode() ?: return HttpServer.Response.error(400, "expired")
        val got = r.json().optString("code").replace(Regex("\\D"), "")
        if (want != got) {
            if (++guesses >= 5) code = null
            return HttpServer.Response.error(400, if (code == null) "expired" else "wrong")
        }
        code = null
        val raw = ByteArray(24)
        random.nextBytes(raw)
        val token = raw.joinToString("") { String.format("%02x", it) }
        val tokens = HashSet(tokens())
        tokens.add(token)
        prefs.edit().putStringSet("tokens", tokens).apply()
        val ok = ok()
        ok.headers["Set-Cookie"] = "$COOKIE=$token; Max-Age=315360000; Path=/; HttpOnly; SameSite=Strict"
        return ok
    }

    // ---------------------------------------------------------------- apps on the TV's home screen
    @Throws(Exception::class)
    private fun apps(): JSONArray {
        val pm = packageManager
        val out = JSONArray()
        val seen = HashSet<String>()
        for (category in arrayOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                val pkg = ri.activityInfo.packageName
                if (!seen.add(pkg) || pkg == packageName) continue
                out.put(JSONObject().put("pkg", pkg).put("component", pkg + "/" + ri.activityInfo.name).put("label", ri.loadLabel(pm).toString()))
            }
        }
        return out
    }

    private fun icon(pkg: String): ByteArray? {
        icons[pkg]?.let { return it }
        try {
            val pm = packageManager
            var d: Drawable? = null
            try { d = pm.getApplicationBanner(pkg) } catch (ignored: Exception) { }   // TV apps carry a wide banner; fall back to the icon
            val banner = d != null
            val drawable = d ?: pm.getApplicationIcon(pkg)
            val bmp = Bitmap.createBitmap(if (banner) 320 else 144, if (banner) 180 else 144, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, bmp.width, bmp.height)
            drawable.draw(Canvas(bmp))
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            val png = out.toByteArray()
            if (icons.size < 200) icons[pkg] = png
            return png
        } catch (e: Exception) {
            return null
        }
    }

    @Throws(Exception::class)
    private fun page(): ByteArray {
        page?.let { return it }
        val bytes = assets.open("index.html").use { it.readBytes() }
        page = bytes
        return bytes
    }
}
