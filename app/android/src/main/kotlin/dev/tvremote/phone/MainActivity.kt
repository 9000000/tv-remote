package dev.tvremote.phone

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
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
import dev.tvremote.core.adb.Adb
import dev.tvremote.core.remote.Atv
import dev.tvremote.core.remote.Remote
import dev.tvremote.core.picture.Tv
import dev.tvremote.core.adb.TvShell
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
    @Volatile internal var pic: Tv? = null                             // the picture: a session of its own, as on the computer, so that
                                                                      // switching it on or off leaves the keys' one (and what rides on it) alone
    private var picTries = 0
    @Volatile internal var sound: Tv? = null                           // the TV's sound, a session of its own (Listen.kt)
    internal var soundTries = 0
    internal var basic: Remote? = null                                 // keys only: an Android TV whose ADB is still off (Atv), or an LG / Samsung TV (KeysOnly.kt)
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
    internal lateinit var fleetBtn: android.widget.Button
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
    internal lateinit var listenBtn: IconButton
    internal lateinit var exitBtn: IconButton
    internal lateinit var picBar: LinearLayout
    internal lateinit var qualBtn: android.widget.TextView
    internal lateinit var fullBtn: IconButton
    internal lateinit var listenChip: android.widget.TextView
    internal lateinit var hqBtn: android.widget.TextView
    internal lateinit var topBar: LinearLayout                         // the TV's name and state, with the buttons beside or under it
    internal lateinit var basicBar: Button


    // ---- Drawing.kt: the finger on the picture draws on the TV's screen instead of touching it.
    internal var drawing = false
    internal var drawBtn: IconButton? = null                          // ✏️ among the keys: lit while drawing
    internal lateinit var drawBar: LinearLayout
    internal lateinit var drawView: DrawView
    internal val drawer = dev.tvremote.core.ontv.Draw { tv?.link() ?: throw java.io.IOException("no TV") }
    internal val drawIo = java.util.concurrent.Executors.newSingleThreadExecutor()
    internal val drawQ = ArrayList<org.json.JSONObject>()              // what waits for the request on its way (drawQ's lock)
    internal var drawBusy = false
    internal var drawColor = DRAW_COLORS[0]
    internal var drawWidth = 60
    internal var drawId = 0

    // ---- Typing.kt. Two ways to type. Send: write here, then send it in one go. Live (⚡): the TV follows every letter and
    // every deletion as it happens, so one wrong letter is fixed by fixing it here.
    internal lateinit var textBox: EditText
    internal lateinit var liveBtn: IconButton
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
    internal val PICK_DRAW = 73                                       // 🖼️ a picture to put on the TV's screen (Drawing.kt)
    internal val PICK_FLEET = 72                                       // ManyTvs.kt: the APK for several TVs

    // ---- SetupBanner.kt
    internal lateinit var setupBar: Button
    internal lateinit var playing: PlayingBar                          // NowPlayingBar.kt
    internal lateinit var nowApp: NowApp                              // NowApp.kt
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
        applyPalette(this)                                        // before the theme and every view: they read the colours
        setTheme(if (LIGHT) R.style.AppLight else R.style.AppDark)   // dialogs and the keyboard's look follow
        super.onCreate(b)
        prefs = getSharedPreferences("tvs", MODE_PRIVATE)
        reachAroundVpn()                                          // a TV at home stays reachable with a VPN on
        dev.tvremote.core.WireLog.app = "TV Remote " + version() + " (phone, Android " + android.os.Build.VERSION.RELEASE + ")"
        video = prefs.getBoolean("video", true)
        loadLang(this)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC     // while listening the phone's own volume keys set the sound here
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        paintBars()
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
        bgListen = false                                              // back on screen: the picture again (the sound plays on: its own session)
        startSound()                                                  // given up on while away: once more
        managing.resume()
        if (keysAway) { keysAway = false; host?.let { resumeBasic(it) } } else startSession()
    }

    /**
     * Not watching: stop the encoder on the TV. The keys' session costs the TV nothing, so it stays, and coming back from
     * another app needs no new connection; only after a long while away is it closed.
     */
    override fun onStop() {
        super.onStop()
        foreground = false
        ui.removeCallbacks(retry)                                     // tried again on return instead
        ui.removeCallbacks(picRetry)
        if (listen) {                                                 // listening goes on with the phone's screen off, without the picture
            if (video && host != null) bgListen = true
            stopPicture()
            return
        }
        stopPicture()
        ui.postDelayed(idleStop, 10 * 60 * 1000L)
    }

    internal val idleStop = Runnable { stopSession() }

    override fun onDestroy() {
        managing.destroy()
        Popup.closeAll(this)                                           // or the window leaks, in the old colours
        if (::nowApp.isInitialized) nowApp.close()
        // recreated (dark mode at sunset, a font size, the language): this instance's session, sound and keys end with it, or
        // they would go on beside the new one's, with nothing left on screen that can stop them
        ui.removeCallbacksAndMessages(null)
        if (listen) { listen = false; listenService(false); stopSound() }
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
        stopPicture()
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

    /** Picture on or off. Off: the TV encodes nothing, only the buttons work. Only the picture's session starts or stops. */
    internal fun toggleVideo() {
        video = !video
        prefs.edit().putBoolean("video", video).apply()
        if (!video) stopPicture()
        picTries = 0
        layoutRemote()                                                // showing the picture creates the surface, which starts it
        status.text = title(entry ?: host ?: "") + " · " + when {
            tv?.ready() != true -> t("đang kết nối...", "connecting...")
            video -> t("đang mở hình...", "opening the picture...")
            else -> t("đang tắt hình", "picture off")
        }
        startSession()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (managing.back()) return
        if (fullscreen) { setFullscreen(false); return }
        if (about.visibility == View.VISIBLE) about.visibility = View.GONE
        else if (settings.visibility == View.VISIBLE) settings.visibility = View.GONE
        else if (host != null) leave()
        else super.onBackPressed()
    }

    /** The phone's volume buttons change the TV's volume while connected. */
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val c = e.keyCode
        // a game controller (Bluetooth / USB) drives the TV, as on the page (PadMap); a text field in focus keeps its keys
        if (live() && e.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK) != 0 && currentFocus !is EditText) {
            val k = PadMap.key(c, tv != null)
            if (k != null) {
                val moves = c in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT || c == KeyEvent.KEYCODE_BUTTON_L1 || c == KeyEvent.KEYCODE_BUTTON_R1 || c == KeyEvent.KEYCODE_BUTTON_L2 || c == KeyEvent.KEYCODE_BUTTON_R2
                if (e.action == KeyEvent.ACTION_DOWN && (e.repeatCount == 0 || moves)) press(k)   // the system repeats a held key
                return true
            }
        }
        // the TV's volume, unless the sound plays here (🎧) or Settings says the keys are the phone's
        if (live() && !listen && prefs.getBoolean("volKeysTv", true) && (c == KeyEvent.KEYCODE_VOLUME_UP || c == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (e.action == KeyEvent.ACTION_DOWN) press(c, false)
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    private var stickKey: Int? = null; private var stickAt = 0L; private var stickLast = 0L
    /** The left stick / hat of a game controller: one arrow while it is past the deadzone, repeating when held (PadMap.arrow). */
    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (e.source and InputDevice.SOURCE_JOYSTICK != 0 && e.action == MotionEvent.ACTION_MOVE && live() && currentFocus !is EditText) {
            val k = PadMap.arrow(e.getAxisValue(MotionEvent.AXIS_X).let { if (it == 0f) e.getAxisValue(MotionEvent.AXIS_HAT_X) else it },
                e.getAxisValue(MotionEvent.AXIS_Y).let { if (it == 0f) e.getAxisValue(MotionEvent.AXIS_HAT_Y) else it })
            val now = android.os.SystemClock.uptimeMillis()
            if (k == null) stickKey = null
            else if (k != stickKey) { stickKey = k; stickAt = now; stickLast = now; press(k) }
            else if (now - stickAt >= 400 && now - stickLast >= 130) { stickLast = now; press(k) }
            return true
        }
        return super.onGenericMotionEvent(e)
    }

    // ---------------------------------------------------------------- session
    /** A saved TV: through whichever of its addresses answered (at home its Wi-Fi IP, away its Tailscale IP). */
    internal fun connectSaved(ip: String) { val r = route[ip] ?: ip; connect(r, if (r != ip) ip else null) }

    internal fun connect(ip: String, from: String? = null) {
        entry = from ?: savedOwner(ip.trim())                         // a saved TV's other address: the same row, not a second one
        wasLive = false; retries = 0
        val h = ip.trim()
        host = h
        if (h.isEmpty()) return
        hideKeyboard()
        if (kindOf(h) != Remote.Kind.ANDROID) { host = null; startBasic(h, false); return }   // LG / Samsung: their own remote link, no ADB
        status.text = title(h) + " · " + t("đang kết nối...", "connecting...")
        surface.visibility = View.VISIBLE                              // a fresh, black surface (see disconnect)
        show(true)
        waitFor(title(from ?: h), t("Đang tìm TV ở ", "Looking for the TV at ") + h + "...")
        startSession()
    }

    /** The keys' session (no picture: keys, text, the clipboard, what the TV plays), then the picture's when it is wanted. */
    internal fun startSession() {
        val h = host
        if (h == null || basic != null || keysAway || kindOf(entry ?: h) != Remote.Kind.ANDROID) return
        if (tv == null) { tv = Tv(this, h, null, 0, 0, wakeNext, this); wakeNext = false }
        startPicture()
    }

    /** Beside the keys' session, at once (the TV asks "Allow USB debugging?" once for both). It carries the touches too:
     *  scrcpy places a finger by the size of the picture it sends. */
    internal fun startPicture() {
        val h = host ?: return
        if (pic != null || tv == null || !video || bgListen || !surfaceReady) return
        val dm = resources.displayMetrics
        pic = Tv(this, h, surface.holder.surface, prefs.getInt("quality", 1), maxOf(dm.widthPixels, dm.heightPixels), false, picListener,
            fps = prefs.getInt("picFps", 0), bitRate = prefs.getInt("picRate", 0) * 1_000_000, encoder = prefs.getString("picEnc", "auto")!!)
    }

    internal fun stopPicture() {
        ui.removeCallbacks(picRetry)
        pic?.stop()
        pic = null
    }

    internal fun stopSession() {
        stopPicture()
        tv?.stop()
        tv = null
    }

    private val picRetry = Runnable { startPicture() }

    /** The picture's session says only how it shows; whether the TV is there at all is the keys' session's to tell. */
    private val picListener = object : Tv.Listener {
        override fun onStatus(key: String, detail: String) {
            val from = pic
            ui.post {
                val h = host
                if (pic !== from || from == null || h == null) return@post
                when (key) {
                    "rate" -> status.text = title(entry ?: h) + " · " + (if (listen) "🎧 " else "") + detail + " KB/s"
                    "live" -> {
                        picTries = 0
                        waiting.visibility = View.GONE
                        status.text = title(entry ?: h) + " · " + if (listen) t("🎧 đang nghe trên điện thoại", "🎧 listening on the phone") else t("chạm hình để bấm", "touch the picture to tap")
                    }
                    "closed", "error" -> {
                        pic = null
                        if (tv?.ready() != true) return@post          // the TV is gone: the keys' session reconnects both
                        if (++picTries <= 3) ui.postDelayed(picRetry, 1000L * picTries)
                        else status.text = title(entry ?: h) + " · " + t("TV không mở được hình", "the TV did not open its picture")
                    }
                }
            }
        }
        override fun onVideoSize(w: Int, h: Int) = this@MainActivity.onVideoSize(w, h)
        override fun onNetwork(mac: String, lan: String, tailscale: String) {}
        override fun wantsNetwork(): Boolean = false
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
            val r = dev.tvremote.core.adb.AdbPrompt.ask(ip, token, fp, 30)
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
        val fp = try { dev.tvremote.core.adb.Adb(filesDir).fingerprint() } catch (e: Exception) { "" }
        if (fp.isNotEmpty()) line(t("Mã trên TV phải trùng mã của máy này:\n", "The code on the TV must match this phone’s:\n") + fp, 13f, MUTED, false, 18)
        val helper = TextView(this).apply { textSize = 15f; setTextColor(ACCENT_TEXT); gravity = Gravity.CENTER; visibility = View.GONE }
        col.addView(helper, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        line(t("Màn hình này tự đóng khi TV đã cho phép.", "This screen closes by itself once the TV has allowed it."), 15f, MUTED, false, 26)
        askSetup(fp, helper)
        col.addView(button(t("Để sau", "Later"), CARD).apply { setOnClickListener { d.dismiss() } }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(30) })
        d.setContentView(col)
        allowBox = d
        try { d.show() } catch (e: Exception) { allowBox = null }
    }

    private var workOn = false
    private var workWhat = ""
    private var workPct = -2

    /** Work under way on the Manage screen ([what], "" when over): kept running in the background by WorkService, its
     *  notification showing how far it is. */
    internal fun working(what: String) {
        try {
            if (what.isNotEmpty()) {
                workWhat = what
                val i = Intent(this, WorkService::class.java).putExtra(WorkService.WHAT, what)
                if (!workOn) { askNotifications(); if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i); ui.postDelayed(workTick, 1000) }
                else WorkService.show(this, what, managing.progress())
                workOn = true
            } else if (workOn) { workOn = false; workPct = -2; ui.removeCallbacks(workTick); stopService(Intent(this, WorkService::class.java)) }
        } catch (e: Exception) { }                                    // not allowed from the background: it only runs until Android freezes the app
    }
    private val workTick = object : Runnable {
        override fun run() {
            if (!workOn) return
            val p = managing.progress()
            if (p != workPct) { workPct = p; WorkService.show(this@MainActivity, workWhat, p) }
            ui.postDelayed(this, 1000)
        }
    }

    /** Android 13+ shows no notification until allowed, and asks only when an app asks: without it nothing says the TV's
     *  sound plays or a copy goes on in the background. Asked the first time either starts; Android itself stops asking after "Don't allow" twice. */
    internal fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
    }

    /** Leaving the TV by choice: asks first when something is under way there, which leaving would cut off. */
    internal fun leave() {
        val w = managing.working()
        if (w.isEmpty()) { disconnect(null); return }
        Popup.Builder(this).setTitle(WorkService.say(w) + "...")
            .setMessage(t("Rời TV này sẽ hủy việc đang làm.", "Leaving this TV cancels it."))
            .setPositiveButton(t("Ở lại", "Stay"), null)
            .setNegativeButton(t("Vẫn rời", "Leave anyway")) { _, _ -> disconnect(null) }.show()
    }

    internal fun disconnect(message: String?) {
        allowAlert(false)
        fullscreen = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        clearBox()                                                    // the next TV has received nothing from the box
        managing.close()
        setupBar.visibility = View.GONE
        if (listen) { listen = false; bgListen = false; listenService(false); stopSound() }
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
        // the focus leaving the picker's address field would land on the text box below the keys, and the remote would open
        // scrolled down to it (Android 8 and older): the screen itself takes it first
        if (remoteScreen) { remote.isFocusableInTouchMode = true; remote.requestFocus() }
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
                    status.text = title(entry ?: h) + " · " + t("đang kết nối...", "connecting...")
                    waitStep.text = t("Đang tìm TV ở ", "Looking for the TV at ") + detail + "..."
                }
                "allow" -> {
                    status.text = title(entry ?: h) + " · " + t("nhìn lên TV, chọn Cho phép", "look at the TV, choose Allow")
                    waitStep.text = t("Chờ cho phép trên TV: tích “Luôn cho phép” rồi chọn OK.", "Waiting for the TV: tick “Always allow”, then OK.")
                }
                "starting" -> {
                    save(entry ?: h, detail)
                    status.text = title(entry ?: h) + " · " + t("đang mở điều khiển...", "opening the controls...")
                    waitName.text = title(entry ?: h)
                    waitStep.text = t("Đã kết nối, đang mở điều khiển...", "Connected, opening the controls...")
                    val s = from
                    ui.postDelayed({ if (tv === s && s != null && s.ready()) waiting.visibility = View.GONE }, 10000)   // no picture yet, but the buttons work
                }
                "live" -> {
                    wasLive = true; retries = 0
                    from?.let { checkSetup(it, h) }
                    waiting.visibility = View.GONE
                    pendingAsk?.let { q -> pendingAsk = null; Thread { val to = from?.search(q); ui.post { searched(q, to) } }.start() }
                    if (pic?.ready() != true) status.text = title(entry ?: h) + " · " + when {   // the picture's session says the rest
                        listen -> t("🎧 đang nghe trên điện thoại", "🎧 listening on the phone")
                        video -> t("đang mở hình...", "opening the picture...")
                        else -> t("đang tắt hình", "picture off")
                    }
                }
                "closed" -> {
                    tv = null; stopPicture()
                    reconnect(t("TV đã ngắt kết nối.", "The TV closed the connection."))
                }
                else -> {
                    tv = null; stopPicture()
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

    /** [feel]: a short buzz, as a key under a finger gives; not for keys sent without a touch (voice, the volume buttons). */
    internal fun press(code: Int, feel: Boolean = true) {
        if (feel) buzz()
        val tv = tv
        if (tv != null) { if (tv.ready()) tv.press(code) }
        else if (code in 19..23) basic?.pulse(code, 110)              // arrows and OK are held for a moment: see Atv.pulse
        else basic?.key(code, Atv.SHORT)
    }

    internal fun keyDown(code: Int, repeat: Int) {
        if (repeat == 0) buzz()
        val tv = tv
        if (tv != null) tv.key(0, code, repeat, 0) else if (repeat == 0) basic?.key(code, Atv.DOWN)
    }

    private val vibrator by lazy { getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator }

    /** The tap a key gives under the finger (Settings: Vibrate on key press, on by default). */
    @Suppress("DEPRECATION")
    internal fun buzz() {
        if (!prefs.getBoolean("buzz", true)) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            when {
                // a remote's key, not a touch: the system's "touch feedback" switch (often off) does not silence it
                Build.VERSION.SDK_INT >= 33 -> v.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK),
                    android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_HARDWARE_FEEDBACK))
                Build.VERSION.SDK_INT >= 29 -> v.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
                Build.VERSION.SDK_INT >= 26 -> v.vibrate(android.os.VibrationEffect.createOneShot(18, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                else -> v.vibrate(18)
            }
        } catch (ignored: Exception) { }
    }

    internal fun keyUp(code: Int) {
        val tv = tv
        if (tv != null) tv.key(1, code, 0, 0) else basic?.key(code, Atv.UP)
    }

    /** True, with a word of explanation, when the button needs ADB and there is only the keys-only connection. */
    internal fun needsAdb(): Boolean {
        val b = basic ?: return false
        if (b.kind != Remote.Kind.ANDROID) kindOff()
        else Toast.makeText(this, t("Cần bật Gỡ lỗi ADB trên TV cho chức năng này", "This needs ADB debugging on for the TV"), Toast.LENGTH_SHORT).show()
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
        foldSaved(ip)                                                 // now known by its MAC: an older row of it (other address) goes
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
        if (request == PICK_DRAW && result == RESULT_OK) data?.data?.let { drawPicked(it) }
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

    /** Not a command: looked up on the TV, on YouTube when it has it (TvShell.search); keys only, YouTube by its link. */
    internal fun ask(sentence: String) {
        val tv = tv
        basic?.let { if (!it.search(sentence)) it.launch(TvShell.youtube(sentence)); searched(sentence, "yt"); return }
        if (tv != null && tv.ready()) Thread { val to = tv.search(sentence); ui.post { searched(sentence, to) } }.start()
        else { pendingAsk = sentence; Toast.makeText(this, t("Đang chờ TV: ", "Waiting for the TV: ") + sentence, Toast.LENGTH_SHORT).show() }
    }

    internal fun searched(sentence: String, to: String?) {
        if (isFinishing) return
        Toast.makeText(this, when (to) {
            "yt" -> t("Đang tìm trên YouTube: ", "Searching YouTube for: ") + sentence
            "gs" -> t("Đã gửi cho TV: ", "Sent to the TV: ") + sentence
            else -> t("TV này không có YouTube hay tìm kiếm của Google để tìm câu này.", "This TV has neither YouTube nor Google search to look this up.")
        }, Toast.LENGTH_SHORT).show()
    }

    /** 🎤: say it or type it, the phone's keyboard or its own mic key; a command is done here, anything else is looked up. */
    internal fun talk() {
        val box = LinearLayout(this)
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(dp(20), dp(12), dp(20), 0)
        val field = input(t("vd: mở YouTube, Sơn Tùng", "e.g. open YouTube, lo-fi music"))
        field.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
        field.background = box(BTN, 12)                                // the dialog is CARD: the box must stand out from it
        box.addView(field, LinearLayout.LayoutParams(0, dp(48), 1f))
        val mic = icon("@mic", t("Nói", "Speak"), ACCENT)
        mic.lit = true
        box.addView(mic, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(8) })
        var dlg: Popup? = null
        val send = { val s = field.text.toString().trim(); if (s.isNotEmpty()) { dlg?.dismiss(); voice(s) } }
        mic.setOnClickListener { dlg?.dismiss(); speak() }
        field.setOnEditorActionListener { _, _, _ -> send(); true }
        dlg = Popup.Builder(this).setTitle(t("Nói hoặc gõ cho TV", "Speak or type to the TV")).setView(box)
            .setPositiveButton(t("Gửi", "Send"), null).setNegativeButton(t("Đóng", "Close"), null).create()
        dlg.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.setOnShowListener { dlg.getButton(Popup.BUTTON_POSITIVE).setOnClickListener { send() } }
        dlg.show()
        field.requestFocus()
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
