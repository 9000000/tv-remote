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
import dev.tvremote.core.Caps
import dev.tvremote.core.Feeds
import dev.tvremote.core.HttpServer
import dev.tvremote.core.Manage
import dev.tvremote.core.Scenes
import dev.tvremote.core.Setup
import dev.tvremote.core.SetupUpdate
import dev.tvremote.core.Tv
import dev.tvremote.core.WebPage
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
        private val KEYS: Map<String, Int> = Scenes.KEYS

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

        /** The service runs for the web remote, and also, with the web remote off, while timers or limits need watching. */
        fun wanted(c: Context): Boolean = enabled(c) || Rules.active(c)

        fun setEnabled(c: Context, on: Boolean) {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).commit()
            c.stopService(Intent(c, RemoteService::class.java))       // started again with or without its web server
            if (wanted(c)) start(c)
        }

        fun start(c: Context) {
            val i = Intent(c, RemoteService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }

        fun pairedCount(c: Context): Int = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("tokens", HashSet())?.size ?: 0

        /** A new pass for a device, kept with the others (pairing hands them out, and TrustReceiver to ADB). */
        fun newToken(c: Context): String {
            val raw = ByteArray(24)
            java.security.SecureRandom().nextBytes(raw)
            val token = raw.joinToString("") { String.format("%02x", it) }
            val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            synchronized(RemoteService::class.java) {
                val tokens = HashSet(p.getStringSet("tokens", HashSet()) ?: emptySet())
                tokens.add(token)
                p.edit().putStringSet("tokens", tokens).commit()
            }
            return token
        }

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
    private val feeds = Feeds()                                       // the picture and the sound for one browser each
    @Volatile private var stopping = false
    @Volatile private var lastUse = 0L                                // when a paired browser last asked for anything
    @Volatile private var resting = false                             // the input session is closed on purpose: nobody is using the remote
    private var lastPairStart = 0L
    @Volatile private var wrong = 0                                  // wrong codes since the last pairing or lockout
    @Volatile private var lockedUntil = 0L
    private var guard: Guard? = null
    private var tsWatch: TailscaleWatch? = null
    private var dnsWatch: DnsWatch? = null
    private var keepAdb: KeepAdb? = null
    private var updates: AutoUpdate? = null
    private var serving = true                                        // the web server started, when it is wanted
    @Volatile private var saidAlive = false
    private val version by lazy { try { packageManager.getPackageInfo(packageName, 0).versionName ?: "0" } catch (e: Exception) { "0" } }
    private val page by lazy { WebPage({ assets.open(it) }, "tv", try { packageManager.getPackageInfo(packageName, 0).let { it.versionName + "-" + it.lastUpdateTime / 1000 } } catch (e: Exception) { "0" }) }

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
            private val caps by lazy { Caps.report(this@RemoteService) }   // the hardware does not change while TV Setup runs
            override fun caps(): String = caps
        }, getExternalFilesDir(null)) { assets.open("tvr-helper.jar").use { it.readBytes() } }
        manage?.allowed = { fp ->                                      // whoever is at the TV sees that a device was let in
            ui.post { android.widget.Toast.makeText(this, (if (java.util.Locale.getDefault().language == "vi") "Đã cho phép gỡ lỗi USB cho một thiết bị đã ghép\n" else "USB debugging allowed for a paired device\n") + fp, android.widget.Toast.LENGTH_LONG).show() }
        }
        if (enabled(this)) try { server.start(PORT, false) } catch (e: Exception) { adbState = "error"; serving = false }
        lastUse = System.currentTimeMillis()
        connect()
        guard = Guard(this) { try { link?.get() } catch (e: Exception) { null } }.also { it.start() }
        tsWatch = TailscaleWatch(this) { try { link?.get() } catch (e: Exception) { null } }.also { it.start() }
        dnsWatch = DnsWatch(this) { try { link?.get() } catch (e: Exception) { null } }.also { it.start() }
        keepAdb = KeepAdb(this).also { it.start() }
        updates = AutoUpdate(this, version, { try { link?.get() } catch (e: Exception) { null } }) { busyWhy() }.also { it.start() }
        ui.postDelayed(rest, 30000)
        ui.postDelayed(diary, 20000)
        stats.watchNet()
    }

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
        if (saidAlive) afterUpdate()                                  // started again by an update's script that missed the first word
        return START_STICKY
    }

    override fun onDestroy() {
        stopping = true
        running = null
        code = null
        http?.stop()
        guard?.stop()
        tsWatch?.stop()
        dnsWatch?.stop()
        keepAdb?.stop()
        updates?.stop()
        stats.stopNet()
        link?.close()
        tv?.stop()
        feeds.stop()
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
            override fun onNetwork(mac: String, lan: String, tailscale: String) { }
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
                if (!usageAllowed) usageAllowed = m?.allowUsage(packageName) == true
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
            if (t != null && feeds.viewer == null && adbState == "ready" && System.currentTimeMillis() - lastUse > REST_MS) {
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
            "live" -> {
                adbState = "ready"
                if (!saidAlive && serving) { saidAlive = true; afterUpdate() }
                if (!KeepAdb.can(this)) Thread({ try { link?.get()?.let { KeepAdb.grant(this, it) } } catch (e: Exception) { } }, "grant").apply { isDaemon = true }.start()
            }
            "closed", "error" -> {                                    // ADB off, TV just booted, session dropped: keep trying
                if (resting) return                                   // we closed it ourselves
                adbState = if (key == "error") "error" else "connecting"
                tv = null
                ui.postDelayed({ connect() }, 5000)
            }
        }
    }

    override fun onVideoSize(w: Int, h: Int) { }
    override fun onNetwork(mac: String, lan: String, tailscale: String) { }

    // ---------------------------------------------------------------- updates of this app (AutoUpdate, SetupUpdate)
    /** Up, serving and through to the TV's ADB: an update that is waiting to know whether this version works keeps it. */
    private fun afterUpdate() = Thread({
        try { link?.get()?.let { SetupUpdate.alive(it, version) } } catch (e: Exception) { }
        if (!wanted(this)) ui.post { stopSelf() }                     // started by the update only: nothing for it to do
    }, "alive").apply { isDaemon = true }.start()

    /** Why an update would be noticed now, or null when nobody would. */
    private fun busyWhy(): String? {
        val now = System.currentTimeMillis()
        if (now - lastUse < 30 * 60_000L) return "the remote was used lately"
        if (tv != null || feeds.viewer != null) return "a remote session is open"
        if (pairingCode() != null) return "pairing"
        if (MainActivity.shown) return "TV Setup is on screen"
        val o = Rules.load(this)
        val at = o.optLong("sleep_at")
        if (at > 0 && at - now < 15 * 60_000L) return "the sleep timer ends soon"
        if (Rules.inBedtime(o, now) && (getSystemService(POWER_SERVICE) as PowerManager).isInteractive) return "bedtime"
        return null
    }

    // ---------------------------------------------------------------- the picture, for one browser at a time
    /** The picture for the page, from a second session made only while a browser watches: see Feeds. */
    private fun video(quality: Int): HttpServer.Response = feeds.video { l, sink -> Tv(this, "127.0.0.1", null, quality, 1280, false, l, sink) }

    private fun scenes(): JSONArray = try { JSONArray(prefs.getString("scenes", "[]")) } catch (e: Exception) { JSONArray() }

    // ---------------------------------------------------------------- the sound, for one browser at a time
    /** While someone listens the TV's own speakers are silent (unless [share] works there); they come back when the browser stops. */
    private fun sound(share: Boolean, hq: Boolean): HttpServer.Response =
        feeds.sound(hq, false) { l, sink -> Tv(this, "127.0.0.1", null, 0, 0, false, l, null, "raw", sink, false, share) }

    // ---------------------------------------------------------------- HTTP
    @Throws(Exception::class)
    override fun handle(r: HttpServer.Request): HttpServer.Response {
        // a web page elsewhere must not be able to talk to us through the visitor's browser (DNS rebinding): only IPs are served
        val host = r.headers["host"].toString().replaceFirst(Regex(":\\d+$"), "")
        if (!host.matches(Regex("[0-9.]+|localhost|\\[[0-9a-fA-F:]+\\]"))) return HttpServer.Response.error(403, "open this page by the TV's IP address")

        page.serve(r.path)?.let { return it }                        // the page and its scripts need no pairing: they hold no secrets
        if (r.path == "/api/pair/start" && r.method == "POST") return pairStart()
        if (r.path == "/api/pair/finish" && r.method == "POST") return pairFinish(r)
        if (!tokens().contains(r.cookie(COOKIE)) || r.cookie(COOKIE).isEmpty())
            return HttpServer.Response.json(401, JSONObject().put("error", "not paired").put("name", Build.MODEL))

        inUse()
        val post = r.method == "POST"
        when (r.path) {
            "/api/state" -> return HttpServer.Response.json(state(host))
            "/api/about" -> return HttpServer.Response.json(state(host))
            "/api/setup/need" -> return HttpServer.Response.json(JSONObject().put("ok", true))   // this is TV Setup: it has what this page knows
            "/api/screen" -> {                                        // one picture of the screen: the Screenshots mode, and the camera key
                val png = tv?.screenshot() ?: return HttpServer.Response.error(503, "no screenshot")
                val shot = HttpServer.Response.of("image/png", png)
                shot.headers["Cache-Control"] = "no-store"
                return shot
            }
            "/api/video" -> return video(r.q("q").toIntOrNull() ?: 1)
            "/api/audio" -> return sound(r.query["share"] == "1", r.query["hq"] == "1")
            "/api/rules" -> return try {
                val st = Rules.change(this, if (r.method == "POST") r.json() else JSONObject())
                HttpServer.Response.json(st)
            } catch (e: IllegalArgumentException) { HttpServer.Response.error(400, e.message.toString()) }
            "/api/scenes" -> {
                if (r.method == "POST") prefs.edit().putString("scenes", Scenes.clean(r.json().optJSONArray("scenes") ?: JSONArray()).toString()).apply()
                if (r.query["download"] != null) return Scenes.backup(scenes())
                return HttpServer.Response.json(JSONObject().put("scenes", scenes()))
            }
            "/api/scene/run" -> {
                val d = r.json()
                val steps = d.optJSONArray("steps") ?: (0 until scenes().length()).map { scenes().getJSONObject(it) }.firstOrNull { it.optString("id") == d.optString("id") }?.optJSONArray("steps")
                    ?: return HttpServer.Response.error(404, "no such scene")
                val l = link ?: return HttpServer.Response.error(503, "adb:$adbState")
                val failed = Scenes.run(Scenes.clean(JSONArray().put(JSONObject().put("name", "x").put("steps", steps))).optJSONObject(0)?.optJSONArray("steps") ?: JSONArray(),
                    Scenes.Shell({ l.get().shell(it) }) { m -> Rules.change(this, JSONObject().put("do", "sleep").put("minutes", m)) })
                return HttpServer.Response.json(JSONObject().put("ok", failed == null).put("failed", failed ?: ""))
            }
            "/api/touch" -> {
                val d = r.json()
                val a = when (d.optString("a")) { "down", "0" -> 0; "up", "1" -> 1; "move", "2" -> 2; else -> -1 }   // no wheel here
                feeds.viewer?.let { if (a >= 0 && it.ready()) it.touch(a, d.optDouble("x").toFloat(), d.optDouble("y").toFloat()) }
                return ok()
            }
            "/api/apps" -> if (r.q("system") != "1") return HttpServer.Response.json(JSONObject().put("apps", apps()))   // every package: Manage
            "/api/app/icon" -> {
                val png = icon(r.query["pkg"].toString()) ?: return HttpServer.Response.error(404, "no icon")
                val icon = HttpServer.Response.of("image/png", png)
                icon.headers["Cache-Control"] = "max-age=86400"
                return icon
            }
            "/api/clip" -> {                                          // the TV's clipboard: through scrcpy, whose shell may read it
                val t = tv
                if (t == null || !t.ready()) return HttpServer.Response.error(503, "adb:$adbState")
                if (post) { val d = r.json(); t.setClipboard(d.optString("text").take(100000), d.optBoolean("paste")); return ok() }
                val text = t.clipboard() ?: return HttpServer.Response.error(504, "the TV did not answer")
                return HttpServer.Response.json(JSONObject().put("text", text))
            }
            "/api/adb/connect" -> { if (tv == null) connect(); inUse(); return HttpServer.Response.json(JSONObject().put("state", adbWord())) }
        }
        val managed = manage?.handle(r)
        if (managed != null) return managed
        if (!post) return HttpServer.Response.error(404, "not found")
        val t = tv
        val d = r.json()
        when (r.path) {
            "/api/key" -> {
                val keyCode = KEYS[d.optString("key").uppercase(Locale.ROOT)] ?: return HttpServer.Response.error(400, "unknown key")
                if (t == null || !t.ready()) return HttpServer.Response.error(503, "adb:$adbState")
                val action = when (d.optString("dir")) { "START_LONG" -> "down"; "END_LONG" -> "up"; else -> "press" }   // the words of the computer's server
                if (action == "press" && keyCode in Tv.OPENERS)   // keys that should open something
                    return HttpServer.Response.json(JSONObject().put("ok", true).put("noop", !t.pressSeen(keyCode)))
                if (keyCode == 26) {                                  // sleep or wake by what the TV is doing now, so a tap never does the opposite
                    if (action != "up") t.press(if ((getSystemService(POWER_SERVICE) as android.os.PowerManager).isInteractive) 223 else 224)
                } else if (keyCode == 176) { if (action != "up") t.settings() }   // the key itself does nothing on many TVs
                else key(t, keyCode, action)
                return ok()
            }
            "/api/type" -> {
                if (t == null || !t.ready()) return HttpServer.Response.error(503, "adb:$adbState")
                repeat(minOf(d.optInt("del"), 500)) { t.press(67) }    // live typing: what changed is deleted, then typed again
                if (d.optString("text").isNotEmpty()) t.text(d.optString("text"))
                repeat(minOf(d.optInt("fdel"), 500)) { t.press(112) }
                if (d.optBoolean("enter")) t.press(66)
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

    /** ready, unauthorized, closed, error or unknown: the words the page knows (the computer's server uses them too). */
    private fun adbWord(): String = when (adbState) { "ready" -> "ready"; "allow" -> "unauthorized"; "connecting" -> "unknown"; "error" -> "error"; else -> "closed" }

    /** What the page shows at the top, in the shape the computer's server gives it. */
    @Throws(Exception::class)
    private fun state(host: String): JSONObject {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        var version: String? = ""
        try { version = packageManager.getPackageInfo(packageName, 0).versionName } catch (ignored: Exception) { }
        return JSONObject().put("core", "tv").put("host", host).put("name", Build.MODEL).put("connected", true).put("is_on", pm.isInteractive).put("paired", true)
            .put("device", JSONObject().put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL).put("android", Build.VERSION.RELEASE))
            .put("version", version).put("app_name", "TV Setup")
            .put("released", try { getString(resources.getIdentifier("released", "string", packageName)).split("-").let { it[2] + "/" + it[1] + "/" + it[0] } } catch (e: Exception) { "" })
            .put("adb", JSONObject().put("state", adbWord()))
            .put("volume", JSONObject().put("level", am.getStreamVolume(AudioManager.STREAM_MUSIC)).put("max", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                .put("muted", Build.VERSION.SDK_INT >= 23 && am.isStreamMute(AudioManager.STREAM_MUSIC)))
    }

    // ---------------------------------------------------------------- pairing
    @Synchronized @Throws(Exception::class)
    private fun pairStart(): HttpServer.Response {
        val now = System.currentTimeMillis()
        if (now - lastPairStart < 3000) return HttpServer.Response.error(429, "wait a moment")
        // asking for code after code would give five guesses each: wrong guesses count across codes, and too many lock it
        if (now < lockedUntil) return HttpServer.Response.error(429, "Nhập sai mã nhiều lần: đợi 15 phút rồi thử lại.")
        lastPairStart = now
        if (pairingCode() == null || codeUntil - now < 30000) {          // the code on the TV stays the same when asked again
            code = String.format("%06d", random.nextInt(1000000))
            codeUntil = now + 120000
            guesses = 0
        }
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
            if (++wrong >= 15) { wrong = 0; code = null; lockedUntil = System.currentTimeMillis() + 15 * 60000 }
            return HttpServer.Response.error(400, if (code == null) "expired" else "wrong")
        }
        code = null
        wrong = 0
        val token = newToken(this)
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
                if (!seen.add(pkg)) continue                          // TV Setup itself too: it has a screen of its own now
                out.put(JSONObject().put("pkg", pkg).put("keep", Setup.PROTECTED.matches(pkg)).put("meta", JSONObject().put("label", ri.loadLabel(pm).toString()).put("icon", true).put("banner", true)))
            }
        }
        // the apps turned off (they have no launcher entry then), so "Turn on" is always at hand
        @Suppress("DEPRECATION")
        for (ai in pm.getInstalledApplications(android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS)) {
            if (ai.enabled || !seen.add(ai.packageName)) continue
            out.put(JSONObject().put("pkg", ai.packageName).put("off", true).put("meta", JSONObject().put("label", ai.loadLabel(pm).toString()).put("icon", true).put("banner", true)))
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
}
