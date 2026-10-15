package dev.tvremote.phone

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.tvremote.core.Adb
import dev.tvremote.core.Atv
import dev.tvremote.core.Tv
import dev.tvremote.update.Release
import java.util.Locale

/** Phone remote: pick a TV (saved, found on the Wi-Fi, or typed IP), then live picture with touch, and remote buttons. */
class MainActivity : Activity(), Tv.Listener, SurfaceHolder.Callback {
    // The screens and helpers are in the other files of this folder, as extension functions on this class; the state they
    // share lives here, grouped by the file that mostly uses it.
    internal val ui = Handler(Looper.getMainLooper())
    internal lateinit var prefs: SharedPreferences
    internal lateinit var settings: View
    internal lateinit var about: View
    internal lateinit var managing: Managing                           // apps, files, installs, health and terminal, shown in a web view

    // ---- the session (this file)
    @Volatile internal var tv: Tv? = null                              // read by the session thread to tell its own messages from a replaced one's
    internal var basic: Atv? = null                                    // keys only, for a TV whose ADB is still off (see Atv, KeysOnly.kt)
    internal var host: String? = null                                  // the TV we want to be connected to, null on the picker
    internal var entry: String? = null                                 // the saved TV that [host] is another address of (its Tailscale or home IP), if so
    // Switching to another app and back must find the TV again, not the list of TVs: a session that ends while away (the
    // phone's Wi-Fi asleep, the TV's ADB restarted) is opened again on return, and one that worked before is retried a few
    // times before giving up (the network often needs a moment after the screen comes back).
    internal var foreground = false
    internal var wasLive = false                                       // this TV's session has worked since it was picked
    internal var retries = 0                                           // reconnects tried in a row since it last worked
    internal var keysAway = false                                      // the keys-only connection ended while away: opened again on return
    internal val retry = Runnable { if (host != null && tv == null && basic == null) startSession() }
    internal var wakeNext = false                                      // wakeNext: the next session turns the TV's screen on first
    internal var pendingAsk: String? = null                            // heard while the session was restarting; sent when it is live again
    internal var video = false                                         // video: show the TV picture (off = buttons only, no load on the TV)
    internal var surfaceReady = false
    internal var listen = false                                        // the TV's sound plays here (Listen.kt)
    internal var listenShare = false                                   // ...and on the TV's speakers too
    internal var bgListen = false                                      // listening with the app in the background: the session runs without its picture
    internal var appNames: Map<String, String> = emptyMap()           // the TV's apps by package, for "open <app>" spoken (Automation.kt)
    internal var appNamesFor: String? = null
    internal lateinit var waiting: FrameLayout                         // covers the remote while a TV is being connected: nothing on it works yet
    internal lateinit var waitName: TextView
    internal lateinit var waitStep: TextView

    // ---- PickerScreen.kt
    internal lateinit var picker: LinearLayout
    internal lateinit var savedList: LinearLayout
    internal lateinit var foundList: LinearLayout
    internal lateinit var pickerMsg: TextView
    internal lateinit var ipBox: EditText
    internal lateinit var scanBtn: Button
    internal val route = HashMap<String, String>()                     // saved TV -> the address that answered last: itself, its Tailscale IP or its home IP
    internal var waking = false
    // Each saved TV says whether it answers right now, so nobody has to tap it to find out; the wake button appears only when it does not.
    internal val savedRows = HashMap<String, Pair<Button, View>>()
    internal val reach = HashMap<String, Boolean>()                    // absent: not checked yet
    internal val awayFrom = HashMap<String, Boolean>()                 // a TV that is off can only be woken from its own Wi-Fi
    internal val probe = Runnable { probeSaved() }

    // ---- RemoteScreen.kt
    internal lateinit var remote: LinearLayout
    internal lateinit var controls: LinearLayout
    internal lateinit var status: TextView
    internal lateinit var frame: VideoFrame
    internal lateinit var surface: SurfaceView
    internal lateinit var tapMark: TapMark                            // where the finger is on the picture
    internal lateinit var stage: FrameLayout                           // the picture with the full screen button over it
    internal lateinit var videoBtn: IconButton
    internal lateinit var fullBtn: IconButton
    internal lateinit var exitBtn: IconButton
    internal lateinit var qualBtn: Button
    internal lateinit var listenBtn: IconButton
    internal lateinit var topBar: LinearLayout                         // the TV's name and state, with the buttons beside or under it
    internal lateinit var topTools: LinearLayout
    internal lateinit var basicBar: Button
    internal val SHOT = -1                                             // not a key: the screenshot button in the extra keys
    internal val OPEN = -2                                             // not a key: "open on the TV" in the extra keys
    internal val TIMER = -3                                            // not a key: the sleep timer (Automation.kt)
    internal val SCENES = -4                                           // not a key: the scenes (Automation.kt)

    // ---- Typing.kt. Two ways to type. Send: write here, then send it in one go. Live (⚡): the TV follows every letter and
    // every deletion as it happens, so one wrong letter is fixed by fixing it here.
    internal lateinit var textBox: EditText
    internal lateinit var liveBtn: Button
    internal var live = false
    internal var onTv = ""                                             // what the TV has received from the box so far
    internal var quiet = false                                         // the box is being changed by the app, not typed in

    // ---- KeysOnly.kt, AdbHelp.kt
    internal val adbFailed = HashSet<String>()                        // TVs whose ADB port answers but would not let us in, this session
    // Getting to the TV's Settings without ADB. Most TVs open them on the Settings key. Operator boxes may ignore it and hide
    // Settings on their own home screen; then the way in is another home screen app from the Play Store, which the TV opens
    // when asked for the app's package (the app itself when it is installed, its store page when it is not).
    internal val HOMES = arrayOf("Projectivy Launcher" to "com.spocky.projengmenu", "ATV Launcher" to "ca.dstudio.atvlauncher.free", "FLauncher" to "me.efesser.flauncher")
    /**
     * One job on the TV at a time. These jobs open apps and press keys there over several seconds; a second tap on the same
     * button while one runs would send it all again and leave the TV in a muddle, so it is refused with a word of why.
     */
    internal val tvBusy = java.util.concurrent.atomic.AtomicBoolean(false)

    // ---- Media.kt
    internal var shared: Intent? = null                                // shared from another app, kept until a TV is connected
    internal val PICK_CAST = 71
    internal val PICK_FLEET = 72                                       // ManyTvs.kt: the APK for several TVs

    // ---- SetupBanner.kt
    internal lateinit var setupBar: Button
    internal val setupChecked = HashMap<String, Long>()                // when each TV's TV Setup was last looked at
    internal val setupNews = HashMap<String, SetupNews>()

    // ---- Updater.kt
    internal lateinit var updateBtn: Button
    internal lateinit var updateNote: TextView
    internal var update: Release? = null                               // a newer release found on GitHub, or null
    internal var updating = false

    internal fun pref(key: String): String = prefs.getString(key, "") ?: ""

    // ---------------------------------------------------------------- lifecycle
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("tvs", MODE_PRIVATE)
        video = prefs.getBoolean("video", true)
        loadLang(this)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC     // while listening the phone's own volume keys set the sound here
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        val root = FrameLayout(this)
        root.setBackgroundColor(BG)
        root.fitsSystemWindows = true
        buildPicker()
        buildRemote()
        val sv = ScrollView(this)
        sv.addView(picker)
        sv.tag = "picker"
        root.addView(sv, FrameLayout.LayoutParams(-1, -1))
        root.addView(remote, FrameLayout.LayoutParams(-1, -1))
        waiting = buildWaiting()
        root.addView(waiting, FrameLayout.LayoutParams(-1, -1))
        settings = buildSettings()
        root.addView(settings, FrameLayout.LayoutParams(-1, -1))
        about = buildAbout()
        root.addView(about, FrameLayout.LayoutParams(-1, -1))
        managing = Managing(this, root)
        setContentView(root)
        show(false)
        if (b != null) { host = b.getString("host"); entry = b.getString("entry"); keysAway = b.getBoolean("keys"); wasLive = host != null }   // Android ended the app while away
        if (keysAway && host != null) waitFor(title(entry ?: host!!), t("Đang kết nối lại...", "Reconnecting..."))
        if (host != null) show(true)
        takeShare(intent)
        if (b != null && b.getBoolean("settings")) settings.visibility = View.VISIBLE
        if (b != null && b.getBoolean("about")) about.visibility = View.VISIBLE
        if (prefs.getBoolean("autoUpdate", true)) checkUpdate(false)
        SceneStore.changed(this)                                      // the widget and shortcuts after an update of the app
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString("host", host)
        out.putString("entry", entry)
        out.putBoolean("keys", basic != null || keysAway)
        out.putBoolean("settings", settings.visibility == View.VISIBLE)
        out.putBoolean("about", about.visibility == View.VISIBLE)
    }

    override fun onStart() {
        super.onStart()
        foreground = true
        ui.removeCallbacks(idleStop)
        refreshSaved()
        if (bgListen) { bgListen = false; stopSession() }             // back on screen: the picture again
        managing.resume()
        if (keysAway) { keysAway = false; host?.let { resumeBasic(it) } } else startSession()
    }

    /**
     * Not watching: stop the encoder on the TV. Buttons only (picture off) costs the TV nothing, so that session stays, and
     * coming back from another app needs no new connection; only after a long while away is it closed.
     */
    override fun onStop() {
        super.onStop()
        foreground = false
        ui.removeCallbacks(retry)                                     // tried again on return instead
        if (listen) {                                                 // listening goes on with the phone's screen off, without the picture
            if (video && !bgListen && host != null) { bgListen = true; stopSession(); startSession() }
            return
        }
        if (video) stopSession() else ui.postDelayed(idleStop, 10 * 60 * 1000L)
    }

    internal val idleStop = Runnable { stopSession() }

    override fun onDestroy() {
        managing.destroy()
        // recreated (dark mode at sunset, a font size, the language): this instance's session, sound and keys end with it, or
        // they would go on beside the new one's, with nothing left on screen that can stop them
        ui.removeCallbacksAndMessages(null)
        if (listen) { listen = false; listenService(false) }
        stopSession()
        basic?.close()
        basic = null
        super.onDestroy()
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        surfaceReady = true
        startSession()
    }

    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}

    override fun surfaceDestroyed(h: SurfaceHolder) {
        surfaceReady = false
        if (video && !bgListen) stopSession()
    }

    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        layoutRemote()
    }

    internal var fullscreen = false                                   // only the picture, sideways, with a faint button to leave
        private set

    /** Full screen turns the phone's screen sideways and hides everything but the picture; leaving it goes back upright. */
    internal fun setFullscreen(on: Boolean) {
        fullscreen = on
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        layoutRemote()
    }

    /** Picture on or off. Off: the TV encodes nothing, only the buttons work. The session restarts either way. */
    internal fun toggleVideo() {
        stopSession()
        video = !video
        prefs.edit().putBoolean("video", video).apply()
        layoutRemote()                                                // showing the picture creates the surface, which starts the session
        status.text = t("Đang kết nối ", "Connecting to ") + host + "..."
        startSession()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (managing.back()) return
        if (fullscreen) { setFullscreen(false); return }
        if (about.visibility == View.VISIBLE) about.visibility = View.GONE
        else if (settings.visibility == View.VISIBLE) settings.visibility = View.GONE
        else if (host != null) disconnect(null)
        else super.onBackPressed()
    }

    /** The phone's volume buttons change the TV's volume while connected. */
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val c = e.keyCode
        // the TV's volume, unless the sound plays here (🎧) or Settings says the keys are the phone's
        if (live() && !listen && prefs.getBoolean("volKeysTv", true) && (c == KeyEvent.KEYCODE_VOLUME_UP || c == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (e.action == KeyEvent.ACTION_DOWN) press(c)
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    // ---------------------------------------------------------------- session
    /** A saved TV: through whichever of its addresses answered (at home its Wi-Fi IP, away its Tailscale IP). */
    internal fun connectSaved(ip: String) { val r = route[ip] ?: ip; connect(r, if (r != ip) ip else null) }

    internal fun connect(ip: String, from: String? = null) {
        entry = from
        wasLive = false; retries = 0
        val h = ip.trim()
        host = h
        if (h.isEmpty()) return
        hideKeyboard()
        status.text = t("Đang kết nối ", "Connecting to ") + h + "..."
        surface.visibility = View.VISIBLE                              // a fresh, black surface (see disconnect)
        show(true)
        waitFor(title(from ?: h), t("Đang tìm TV ở ", "Looking for the TV at ") + h + "...")
        startSession()
    }

    internal fun startSession() {
        val h = host
        val picture = video && !bgListen
        if (h == null || tv != null || basic != null || keysAway || picture && !surfaceReady) return
        val dm = resources.displayMetrics
        tv = Tv(this, h, if (picture) surface.holder.surface else null, prefs.getInt("quality", 1), maxOf(dm.widthPixels, dm.heightPixels), wakeNext, this,
            audio = if (listen) "opus" else null, audioShare = listenShare, audioBitRate = if (prefs.getBoolean("listenHq", false)) 160000 else 64000)
        wakeNext = false
    }

    internal fun stopSession() {
        tv?.stop()
        tv = null
    }

    internal var allowBox: android.app.Dialog? = null

    /**
     * The TV is asking "Allow USB debugging?" and nothing goes on until somebody answers it there. A line in the status bar is
     * easy to miss, so this takes the whole screen; it goes away by itself once the TV has answered.
     */
    /**
     * Away from the TV: TV Setup on it answers the question for this phone's own key, with the pass it gave us while ADB
     * worked (see AdbPrompt). Shown in [helper]; the person can still answer on the TV themselves.
     */
    internal fun askSetup(fp: String, helper: TextView) {
        val ip = host ?: return
        val key = entry ?: ip
        val token = pref("tvs:$key")
        if (fp.isEmpty() || token.isEmpty()) return
        helper.visibility = View.VISIBLE
        helper.text = t("TV Setup trên TV đang tự cho phép máy này...", "TV Setup on the TV is allowing this phone...")
        Thread {
            val r = dev.tvremote.core.AdbPrompt.ask(ip, token, fp, 30)
            if (r == "forgotten") prefs.edit().remove("tvs:$key").apply()   // "Unpair all" was pressed on the TV
            ui.post {
                if (allowBox == null) return@post
                helper.text = when {
                    r == "allowed" -> t("✓ TV Setup đã cho phép. Đang kết nối...", "✓ TV Setup allowed it. Connecting...")
                    r == "forgotten" -> t("TV Setup đã hủy ghép máy này: hãy bấm trên TV.", "TV Setup has unpaired this phone: answer on the TV.")
                    r == "unreachable" -> t("Không gọi được TV Setup (remote qua web đang tắt?): hãy bấm trên TV.", "Could not reach TV Setup (web remote off?): answer on the TV.")
                    r.startsWith("other:") -> t("TV đang hỏi cho một máy khác. Hãy bấm trên TV.", "The TV is asking about another device. Answer on the TV.")
                    else -> t("TV Setup không cho phép được: hãy bấm trên TV.", "TV Setup could not allow it: answer on the TV.")
                }
            }
        }.start()
    }

    internal fun allowAlert(on: Boolean) {
        if (!on) { allowBox?.dismiss(); allowBox = null; return }
        if (allowBox != null || isFinishing) return
        val d = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(BG); setPadding(dp(28), dp(28), dp(28), dp(28)) }
        val line = { text: String, size: Float, color: Int, bold: Boolean, top: Int ->
            col.addView(TextView(this).apply { this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER; if (bold) typeface = Typeface.DEFAULT_BOLD; setLineSpacing(dp(4).toFloat(), 1f) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
        }
        line("📺", 64f, TEXT, false, 0)
        line(t("Nhìn lên TV", "Look at the TV"), 30f, TEXT, true, 8)
        line(t("TV đang hỏi “Cho phép gỡ lỗi USB?”", "The TV is asking “Allow USB debugging?”"), 18f, MUTED, false, 10)
        line(t("1. Tích “Luôn cho phép”\n2. Chọn OK (Cho phép)", "1. Tick “Always allow”\n2. Choose OK (Allow)"), 20f, TEXT, true, 26)
        val fp = try { dev.tvremote.core.Adb(filesDir).fingerprint() } catch (e: Exception) { "" }
        if (fp.isNotEmpty()) line(t("Mã trên TV phải trùng mã của máy này:\n", "The code on the TV must match this phone’s:\n") + fp, 13f, MUTED, false, 18)
        val helper = TextView(this).apply { textSize = 15f; setTextColor(ACCENT); gravity = Gravity.CENTER; visibility = View.GONE }
        col.addView(helper, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        line(t("Màn hình này tự đóng khi TV đã cho phép.", "This screen closes by itself once the TV has allowed it."), 15f, MUTED, false, 26)
        askSetup(fp, helper)
        col.addView(button(t("Để sau", "Later"), CARD).apply { setOnClickListener { d.dismiss() } }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(30) })
        d.setContentView(col)
        allowBox = d
        try { d.show() } catch (e: Exception) { allowBox = null }
    }

    internal fun disconnect(message: String?) {
        allowAlert(false)
        fullscreen = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        clearBox()                                                    // the next TV has received nothing from the box
        managing.close()
        setupBar.visibility = View.GONE
        if (listen) { listen = false; bgListen = false; listenService(false) }
        stopSession()
        waiting.visibility = View.GONE
        surface.visibility = View.GONE                                 // drops the surface, or the next TV opens on this one's last frame
        basic?.close()
        basic = null
        host = null
        wasLive = false; retries = 0; keysAway = false
        ui.removeCallbacks(retry)
        layoutRemote()                                                // also brings the system bars back
        show(false)
        message(message, true)
        refreshSaved()
    }

    internal fun show(remoteScreen: Boolean) {
        remote.visibility = if (remoteScreen) View.VISIBLE else View.GONE
        (picker.parent as View).visibility = if (remoteScreen) View.GONE else View.VISIBLE
    }

    /** The saved name of a TV, or its address when it has none yet. */
    internal fun title(ip: String): String = name(ip).ifEmpty { ip }

    override fun onStatus(key: String, detail: String) {
        val from = tv
        ui.post {
            val h = host
            if (tv !== from || h == null) return@post                 // from a session that has been replaced
            allowAlert(key == "allow")
            when (key) {
                "connecting" -> {
                    status.text = t("Đang kết nối ", "Connecting to ") + detail + "..."
                    waitStep.text = t("Đang tìm TV ở ", "Looking for the TV at ") + detail + "..."
                }
                "allow" -> {
                    status.text = t("Nhìn lên TV: tích “Luôn cho phép từ máy tính này” rồi chọn OK.", "Look at the TV: tick “Always allow from this computer”, then OK.")
                    waitStep.text = t("Chờ cho phép trên TV: tích “Luôn cho phép” rồi chọn OK.", "Waiting for the TV: tick “Always allow”, then OK.")
                }
                "starting" -> {
                    save(entry ?: h, detail)
                    status.text = if (video) t("Đang mở hình từ ", "Opening the picture from ") + detail.ifEmpty { h } + "..."
                        else t("Đang mở điều khiển ", "Opening the controls of ") + detail.ifEmpty { h } + "..."
                    waitName.text = title(entry ?: h)
                    waitStep.text = if (video) t("Đã kết nối, đang mở hình TV...", "Connected, opening the TV picture...") else t("Đã kết nối, đang mở điều khiển...", "Connected, opening the controls...")
                    val s = from
                    ui.postDelayed({ if (tv === s && s != null && s.ready()) waiting.visibility = View.GONE }, 10000)   // no picture yet, but the buttons work
                }
                "rate" -> if (video) status.text = title(entry ?: h) + " · " + detail + " KB/s"
                "arate" -> if (!video || bgListen) status.text = title(entry ?: h) + " · 🎧 " + detail + " KB/s"
                "audio" -> if (detail == "noshare") { listenShare = false; Toast.makeText(this, t("TV này dưới Android 13 nên chỉ nghe riêng được: loa TV tạm im.", "This TV is older than Android 13, so only private listening works: the TV’s speakers are quiet."), Toast.LENGTH_LONG).show() }
                    else stopListen(t("TV này chưa gửi được tiếng sang điện thoại (cần Android 11 trở lên).", "This TV cannot send its sound to the phone (Android 11 or newer is needed)."))
                "live" -> {
                    wasLive = true; retries = 0
                    from?.let { checkSetup(it, h) }
                    waiting.visibility = View.GONE
                    pendingAsk?.let {
                        from?.search(it)
                        pendingAsk = null
                    }
                    status.text = title(entry ?: h) + " · " + (if (listen) t("🎧 đang nghe trên điện thoại", "🎧 listening on the phone")
                    else if (video) t("chạm vào hình để bấm trên TV", "touch the picture to tap on the TV")
                    else t("đang tắt hình", "picture off"))
                }
                "closed" -> {
                    tv = null
                    reconnect(t("TV đã ngắt kết nối.", "The TV closed the connection."))
                }
                else -> {
                    tv = null
                    if (wasLive) { reconnect(explain(detail)); return@post }   // it worked a moment ago: the same way again, not keys only
                    waitStep.text = t("Không vào được bằng ADB, đang thử chế độ chỉ bấm phím...", "ADB did not work, trying keys only...")
                    offerBasic(h, explain(detail))                    // no ADB (refused, or filtered and timed out), but the TV may still take keys
                }
            }
        }
    }

    /** The session ended or failed after it had worked: opened again on return when away, else tried again a few times. */
    internal fun reconnect(error: String) {
        val h = host ?: return
        if (!foreground) { status.text = title(entry ?: h) + " · " + t("kết nối lại khi mở app", "reconnects when the app is open"); return }
        if (retries >= 4) { disconnect(error); return }               // about 15 s in all: the TV is off or gone
        retries++
        waitFor(title(entry ?: h), t("Mất kết nối, đang kết nối lại", "Connection lost, reconnecting") + if (retries > 1) " ($retries)..." else "...")
        ui.postDelayed(retry, 1000L * retries)
    }

    // ---------------------------------------------------------------- keys only, while the TV's ADB is off
    internal fun live(): Boolean = tv?.ready() == true || basic?.ready() == true

    internal fun press(code: Int) {
        val tv = tv
        if (tv != null) { if (tv.ready()) tv.press(code) }
        else if (code in 19..23) basic?.pulse(code, 110)              // arrows and OK are held for a moment: see Atv.pulse
        else basic?.key(code, Atv.SHORT)
    }

    internal fun keyDown(code: Int, repeat: Int) {
        val tv = tv
        if (tv != null) tv.key(0, code, repeat, 0) else if (repeat == 0) basic?.key(code, Atv.DOWN)
    }

    internal fun keyUp(code: Int) {
        val tv = tv
        if (tv != null) tv.key(1, code, 0, 0) else basic?.key(code, Atv.UP)
    }

    /** True, with a word of explanation, when the button needs ADB and there is only the keys-only connection. */
    internal fun needsAdb(): Boolean {
        if (basic == null) return false
        Toast.makeText(this, t("Cần bật Gỡ lỗi ADB trên TV cho chức năng này", "This needs ADB debugging on for the TV"), Toast.LENGTH_SHORT).show()
        return true
    }

    override fun onVideoSize(w: Int, h: Int) {
        ui.post { frame.setAspect(w, h) }
    }

    override fun wantsPass(): Boolean = (entry ?: host)?.let { pref("tvs:$it").isEmpty() } ?: false

    override fun onPass(token: String) { (entry ?: host)?.let { prefs.edit().putString("tvs:$it", token).apply() } }

    override fun onNetwork(mac: String, lan: String, tailscale: String) {
        val ip = entry ?: host ?: return
        val e = prefs.edit().putString("mac:$ip", mac).putString("lan:$ip", lan)
        if (tailscale.isNotEmpty() && tailscale != ip) e.putString("ts:$ip", tailscale)   // remembered at home, used when away
        e.apply()
    }

    /** Android reports back here: a share from another app, or the progress of an update's install. */
    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        if (takeShare(i)) return
        if (i.action == ListenService.STOP) { stopListen(null); return }   // "Stop" in the listening notification
        installResult(i)
    }

    override fun onResume() { super.onResume(); probe.run() }

    override fun onPause() { ui.removeCallbacks(probe); super.onPause() }

    // ---------------------------------------------------------------- managing the TV: apps, files, installs, health, terminal
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == PICK_FILE) managing.picked(result, data)
        if (request == PICK_CAST && result == RESULT_OK) data?.data?.let { castFile(it) }
        if (request == PICK_FLEET && result == RESULT_OK) data?.data?.let { fleetApk(it) }
        if (request == SPEAK && result == RESULT_OK && data != null) {
            val heard = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (heard != null && heard.isNotEmpty()) voice(heard[0])
        }
    }

    // ---------------------------------------------------------------- speaking to the TV
    /** The phone turns speech into text (its own Google speech service) and the TV's search / Assistant gets the sentence. */
    internal fun speak() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (VI) "vi-VN" else "en-US")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, t("Nói điều muốn TV làm, vd: mở YouTube", "Say what the TV should do, e.g. open YouTube"))
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, SPEAK)
        } catch (noRecognizer: Exception) {                           // no speech service on this phone: open the TV's own Assistant instead
            tv?.let { if (it.ready()) it.press(219) }
        }
    }

    internal fun ask(sentence: String) {
        if (needsAdb()) return
        Toast.makeText(this, t("Đã gửi cho TV: ", "Sent to the TV: ") + sentence, Toast.LENGTH_SHORT).show()
        val tv = tv
        if (tv != null && tv.ready()) tv.search(sentence) else pendingAsk = sentence
    }

    // ---------------------------------------------------------------- small view helpers
    /** Shows the waiting screen for [name], with what is going on now. */
    internal fun waitFor(name: String, step: String) {
        waitName.text = name
        waitStep.text = step
        waiting.visibility = View.VISIBLE
    }

    internal fun buildWaiting(): FrameLayout {
        val f = FrameLayout(this)
        f.setBackgroundColor(BG)
        f.isClickable = true                                          // the remote underneath takes no touches meanwhile
        f.visibility = View.GONE
        val c = col()
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.setPadding(dp(28), 0, dp(28), 0)
        val spin = ProgressBar(this)
        spin.isIndeterminate = true
        spin.indeterminateTintList = android.content.res.ColorStateList.valueOf(ACCENT)
        c.addView(spin, LinearLayout.LayoutParams(dp(64), dp(64)))
        c.addView(TextView(this).apply { text = t("Đang kết nối", "Connecting"); textSize = 15f; setTextColor(MUTED); gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
        waitName = TextView(this).apply { textSize = 28f; setTextColor(TEXT); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        c.addView(waitName, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        waitStep = TextView(this).apply { textSize = 16f; setTextColor(MUTED); gravity = Gravity.CENTER; setLineSpacing(dp(3).toFloat(), 1f) }
        c.addView(waitStep, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        c.addView(button(t("Hủy", "Cancel"), CARD).apply { setOnClickListener { disconnect(null) } },
            LinearLayout.LayoutParams(dp(200), dp(54)).apply { topMargin = dp(40) })
        f.addView(c, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        return f
    }

}
