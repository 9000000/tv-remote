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
                "DEL" to 67, "ENTER" to 66, "ASSIST" to 219, "CH_UP" to 166, "CH_DOWN" to 167))
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
    @Volatile private var stopping = false
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
        })
        try { server.start(PORT, false) } catch (e: Exception) { adbState = "error" }
        connect()
    }

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int = START_STICKY

    override fun onDestroy() {
        stopping = true
        running = null
        code = null
        http?.stop()
        link?.close()
        tv?.stop()
        ui.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- the input session to the TV's own ADB
    private fun connect() {
        if (stopping) return
        adbState = "connecting"
        tv = Tv(this, "127.0.0.1", null, 0, 0, false, this)
    }

    override fun onStatus(key: String, detail: String) {
        if (stopping) return
        when (key) {
            "allow" -> adbState = "allow"                             // the TV is asking "Allow USB debugging?"
            "live" -> adbState = "ready"
            "closed", "error" -> {                                    // ADB off, TV just booted, session dropped: keep trying
                adbState = if (key == "error") "error" else "connecting"
                tv = null
                ui.postDelayed({ connect() }, 5000)
            }
        }
    }

    override fun onVideoSize(w: Int, h: Int) { }
    override fun onNetwork(mac: String, lan: String) { }

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
        if (r.path == "/api/pair/start" && r.method == "POST") return pairStart()
        if (r.path == "/api/pair/finish" && r.method == "POST") return pairFinish(r)
        if (!tokens().contains(r.cookie(COOKIE)) || r.cookie(COOKIE).isEmpty())
            return HttpServer.Response.json(401, JSONObject().put("error", "not paired").put("name", Build.MODEL))

        val post = r.method == "POST"
        when (r.path) {
            "/api/state" -> return HttpServer.Response.json(state())
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
                if (keyCode == 176) { if (d.optString("action", "press") != "up") t.settings() }   // the key itself does nothing on many TVs
                else key(t, keyCode, d.optString("action", "press"))
                return ok()
            }
            "/api/text" -> {
                if (t == null || !t.ready()) return HttpServer.Response.error(503, "adb:$adbState")
                if (d.optString("text").isNotEmpty()) t.text(d.optString("text"))
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
