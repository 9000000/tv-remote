package dev.tvremote.phone

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageInstaller
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.tvremote.core.AdbLink
import dev.tvremote.core.HttpServer
import dev.tvremote.core.Manage
import dev.tvremote.core.RemoteLabels
import dev.tvremote.core.Adb
import dev.tvremote.core.Atv
import dev.tvremote.core.Tv
import dev.tvremote.update.Release
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.roundToInt

/** Phone remote: pick a TV (saved, found on the Wi-Fi, or typed IP), then live picture with touch, and remote buttons. */
class MainActivity : Activity(), Tv.Listener, SurfaceHolder.Callback {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var picker: LinearLayout
    private lateinit var remote: LinearLayout
    private lateinit var controls: LinearLayout
    private lateinit var savedList: LinearLayout
    private lateinit var foundList: LinearLayout
    private lateinit var settings: View
    private lateinit var about: View
    private lateinit var managing: Managing                           // apps, files, installs, health and terminal, shown in a web view
    private lateinit var pickerMsg: TextView
    private lateinit var status: TextView
    private lateinit var ipBox: EditText
    private lateinit var textBox: EditText
    private lateinit var scanBtn: Button
    private lateinit var updateBtn: Button
    private lateinit var updateNote: TextView
    private var update: Release? = null                               // a newer release found on GitHub, or null
    private var updating = false
    private lateinit var frame: VideoFrame
    private lateinit var surface: SurfaceView
    private var surfaceReady = false
    private var video = false                                         // video: show the TV picture (off = buttons only, no load on the TV)
    private lateinit var videoBtn: IconButton
    private lateinit var fullBtn: IconButton
    private lateinit var exitBtn: IconButton
    private lateinit var qualBtn: Button
    private val SHOT = -1                                             // not a key: the screenshot button in the extra keys
    private val OPEN = -2                                             // not a key: "open on the TV" in the extra keys
    private lateinit var stage: FrameLayout                           // the picture with the full screen button over it
    private var tv: Tv? = null
    private var basic: Atv? = null                                    // keys only, for a TV whose ADB is still off (see Atv)
    private lateinit var basicBar: Button
    private var host: String? = null                                  // the TV we want to be connected to, null on the picker
    private var entry: String? = null                                 // the saved TV that [host] is another address of (its Tailscale or home IP), if so
    private val route = HashMap<String, String>()                     // saved TV -> the address that answered last: itself, its Tailscale IP or its home IP
    private var wakeNext = false                                      // wakeNext: the next session turns the TV's screen on first
    private var waking = false
    private var pendingAsk: String? = null                            // heard while the session was restarting; sent when it is live again

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).roundToInt()
    private fun dp(v: Int): Int = dp(v.toFloat())

    private fun pref(key: String): String = prefs.getString(key, "") ?: ""

    // ---------------------------------------------------------------- lifecycle
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = getSharedPreferences("tvs", MODE_PRIVATE)
        video = prefs.getBoolean("video", true)
        val lang = pref("lang")                                       // "" = follow the phone
        VI = "vi" == (if (lang.isEmpty()) Locale.getDefault().language else lang)
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
        settings = buildSettings()
        root.addView(settings, FrameLayout.LayoutParams(-1, -1))
        about = buildAbout()
        root.addView(about, FrameLayout.LayoutParams(-1, -1))
        managing = Managing(root)
        setContentView(root)
        show(false)
        if (b != null) host = b.getString("host")
        if (host != null) show(true)
        takeShare(intent)
        if (b != null && b.getBoolean("settings")) settings.visibility = View.VISIBLE
        if (b != null && b.getBoolean("about")) about.visibility = View.VISIBLE
        if (prefs.getBoolean("autoUpdate", true)) checkUpdate(false)
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString("host", host)
        out.putBoolean("settings", settings.visibility == View.VISIBLE)
        out.putBoolean("about", about.visibility == View.VISIBLE)
    }

    override fun onStart() {
        super.onStart()
        refreshSaved()
        startSession()
    }

    override fun onStop() {                                           // not watching: stop the encoder on the TV
        super.onStop()
        stopSession()
    }

    override fun onDestroy() {
        managing.destroy()
        super.onDestroy()
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        surfaceReady = true
        startSession()
    }

    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}

    override fun surfaceDestroyed(h: SurfaceHolder) {
        surfaceReady = false
        if (video) stopSession()
    }

    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        layoutRemote()
    }

    private var fullscreen = false                                   // only the picture, sideways, with a faint button to leave

    /** Full screen turns the phone's screen sideways and hides everything but the picture; leaving it goes back upright. */
    private fun setFullscreen(on: Boolean) {
        fullscreen = on
        requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        layoutRemote()
    }

    @Suppress("DEPRECATION")
    private fun layoutRemote() {
        val video = video && basic == null                            // keys only: there is no picture to show
        videoBtn.visibility = if (basic == null) View.VISIBLE else View.GONE
        basicBar.visibility = if (basic == null) View.GONE else View.VISIBLE
        val full = video && host != null && (fullscreen || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)   // landscape: the picture fills the screen
        (controls.parent as View).visibility = if (full) View.GONE else View.VISIBLE
        (status.parent as View).visibility = if (full) View.GONE else View.VISIBLE
        stage.layoutParams = LinearLayout.LayoutParams(-1, if (full) -1 else -2)
        frame.layoutParams = FrameLayout.LayoutParams(-1, if (full) -1 else -2)
        stage.visibility = if (video) View.VISIBLE else View.GONE
        fullBtn.visibility = if (video) View.VISIBLE else View.GONE
        qualBtn.visibility = fullBtn.visibility
        qualBtn.text = Tv.QUALITY[prefs.getInt("quality", 1).coerceIn(0, Tv.QUALITY.size - 1)][0].toString() + "p"
        exitBtn.visibility = if (full) View.VISIBLE else View.GONE
        window.decorView.systemUiVisibility = if (full) View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else 0
        videoBtn.setIcon(if (video) "@eye_off" else "@eye", TEXT)
        videoBtn.contentDescription = if (video) t("Ẩn hình TV", "Hide the TV picture") else t("Xem hình TV", "Show the TV picture")
    }

    /** Picture on or off. Off: the TV encodes nothing, only the buttons work. The session restarts either way. */
    private fun toggleVideo() {
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
        if (live() && (c == KeyEvent.KEYCODE_VOLUME_UP || c == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (e.action == KeyEvent.ACTION_DOWN) press(c)
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    // ---------------------------------------------------------------- session
    /** A saved TV: through whichever of its addresses answered (at home its Wi-Fi IP, away its Tailscale IP). */
    private fun connectSaved(ip: String) { val r = route[ip] ?: ip; connect(r, if (r != ip) ip else null) }

    private fun connect(ip: String, from: String? = null) {
        entry = from
        val h = ip.trim()
        host = h
        if (h.isEmpty()) return
        hideKeyboard()
        status.text = t("Đang kết nối ", "Connecting to ") + h + "..."
        show(true)
        startSession()
    }

    private fun startSession() {
        val h = host
        if (h == null || tv != null || basic != null || video && !surfaceReady) return
        val dm = resources.displayMetrics
        tv = Tv(this, h, if (video) surface.holder.surface else null, prefs.getInt("quality", 1), maxOf(dm.widthPixels, dm.heightPixels), wakeNext, this)
        wakeNext = false
    }

    private fun stopSession() {
        tv?.stop()
        tv = null
    }

    private var allowBox: android.app.Dialog? = null

    /**
     * The TV is asking "Allow USB debugging?" and nothing goes on until somebody answers it there. A line in the status bar is
     * easy to miss, so this takes the whole screen; it goes away by itself once the TV has answered.
     */
    /**
     * Away from the TV: TV Setup on it answers the question for this phone's own key, with the pass it gave us while ADB
     * worked (see AdbPrompt). Shown in [helper]; the person can still answer on the TV themselves.
     */
    private fun askSetup(fp: String, helper: TextView) {
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

    private fun allowAlert(on: Boolean) {
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

    private fun disconnect(message: String?) {
        allowAlert(false)
        fullscreen = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        clearBox()                                                    // the next TV has received nothing from the box
        managing.close()
        stopSession()
        basic?.close()
        basic = null
        host = null
        layoutRemote()                                                // also brings the system bars back
        show(false)
        message(message, true)
        refreshSaved()
    }

    /** The box at the top of the picker: an error (red) or a progress note. */
    private fun message(text: String?, error: Boolean) {
        pickerMsg.text = text ?: ""
        pickerMsg.setTextColor(if (error) ERR_TEXT else TEXT)
        pickerMsg.background = box(if (error) ERR_BG else CARD, 12)
        pickerMsg.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    private fun show(remoteScreen: Boolean) {
        remote.visibility = if (remoteScreen) View.VISIBLE else View.GONE
        (picker.parent as View).visibility = if (remoteScreen) View.GONE else View.VISIBLE
    }

    /** The saved name of a TV, or its address when it has none yet. */
    private fun title(ip: String): String = name(ip).ifEmpty { ip }

    override fun onStatus(key: String, detail: String) {
        val from = tv
        ui.post {
            val h = host
            if (tv !== from || h == null) return@post                 // from a session that has been replaced
            allowAlert(key == "allow")
            when (key) {
                "connecting" -> status.text = t("Đang kết nối ", "Connecting to ") + detail + "..."
                "allow" -> status.text = t("Nhìn lên TV: tích “Luôn cho phép từ máy tính này” rồi chọn OK.", "Look at the TV: tick “Always allow from this computer”, then OK.")
                "starting" -> {
                    save(entry ?: h, detail)
                    status.text = t("Đang mở hình từ ", "Opening the picture from ") + detail.ifEmpty { h } + "..."
                }
                "rate" -> if (video) status.text = title(entry ?: h) + " · " + detail + " KB/s"
                "live" -> {
                    pendingAsk?.let {
                        from?.search(it)
                        pendingAsk = null
                    }
                    status.text = title(entry ?: h) + " · " + (if (video) t("chạm vào hình để bấm trên TV", "touch the picture to tap on the TV")
                    else t("đang tắt hình", "picture off"))
                }
                "closed" -> {
                    tv = null
                    disconnect(t("TV đã ngắt kết nối.", "The TV closed the connection."))
                }
                else -> {
                    tv = null
                    offerBasic(h, explain(detail))                    // no ADB (refused, or filtered and timed out), but the TV may still take keys
                }
            }
        }
    }

    private fun refused(err: String?): Boolean {
        val e = err?.lowercase(Locale.ROOT) ?: ""
        return e.contains("refused") && !e.contains("refused:") || e.contains("econnrefused")
    }

    // ---------------------------------------------------------------- keys only, while the TV's ADB is off
    private fun live(): Boolean = tv?.ready() == true || basic?.ready() == true

    private fun press(code: Int) {
        val tv = tv
        if (tv != null) { if (tv.ready()) tv.press(code) }
        else if (code in 19..23) basic?.pulse(code, 110)              // arrows and OK are held for a moment: see Atv.pulse
        else basic?.key(code, Atv.SHORT)
    }

    private fun keyDown(code: Int, repeat: Int) {
        val tv = tv
        if (tv != null) tv.key(0, code, repeat, 0) else if (repeat == 0) basic?.key(code, Atv.DOWN)
    }

    private fun keyUp(code: Int) {
        val tv = tv
        if (tv != null) tv.key(1, code, 0, 0) else basic?.key(code, Atv.UP)
    }

    /** True, with a word of explanation, when the button needs ADB and there is only the keys-only connection. */
    private fun needsAdb(): Boolean {
        if (basic == null) return false
        Toast.makeText(this, t("Cần bật Gỡ lỗi ADB trên TV cho chức năng này", "This needs ADB debugging on for the TV"), Toast.LENGTH_SHORT).show()
        return true
    }

    /**
     * ADB did not work. Before saying so, see whether the TV answers as an Android TV: then the keys-only remote takes over
     * (straight away when this phone is paired with it, after asking otherwise). [error] is shown only when that is not possible.
     */
    private fun offerBasic(ip: String, error: String) {
        Thread {
            if (open(ip, 5555, 1500)) adbFailed.add(ip)                // the port answers, yet ADB did not work (not allowed on the TV, say): do not keep retrying by ourselves
            val keys = open(ip, 6466, 2500)                            // an Android TV: it takes keys
            ui.post {
                if (isFinishing || host != ip || tv != null) return@post   // the person has moved on
                if (!keys) { disconnect(error); return@post }
                disconnect(null)
                if (prefs.getBoolean("atv:$ip", false)) startBasic(ip, false)
                else AlertDialog.Builder(this).setTitle(t("TV chưa bật Gỡ lỗi ADB", "ADB debugging is off on this TV"))
                    .setMessage(t("Vẫn bấm phím điều khiển được, sau khi ghép bằng mã hiện trên TV. Từ đó bạn mở Cài đặt của TV để bật ADB, rồi dùng đủ chức năng (xem hình, ứng dụng, tệp...).",
                        "You can still press keys, after pairing with a code the TV shows. From there open the TV’s Settings to turn ADB on, then everything works (picture, apps, files...)."))
                    .setPositiveButton(t("Ghép bằng mã", "Pair with a code")) { _, _ -> startBasic(ip, false) }
                    .setNegativeButton(t("Đóng", "Close")) { _, _ -> message(error, true) }
                    .setOnCancelListener { message(error, true) }.show()
            }
        }.start()
    }

    private fun startBasic(ip: String, justPaired: Boolean) {
        message(t("Đang kết nối ", "Connecting to ") + ip + "...", false)
        Thread {
            val a = Atv(filesDir, ip)
            try {
                a.connect { ui.post { if (basic === a) disconnect(t("TV đã ngắt kết nối.", "The TV closed the connection.")) } }
                ui.post {
                    if (isFinishing || host != null) { a.close(); return@post }
                    prefs.edit().putBoolean("atv:$ip", true).apply()
                    basic = a
                    host = ip
                    save(entry ?: ip, a.model)
                    message(null, false)
                    show(true)
                    layoutRemote()
                    status.text = title(ip) + " · " + t("chỉ bấm phím", "keys only")
                    watchAdb(a, ip)
                }
            } catch (e: Atv.NotPaired) {
                a.close()
                if (justPaired) ui.post { message(t("TV không nhận kết nối sau khi ghép. Thử lại.", "The TV did not accept the connection after pairing. Try again."), true) }
                else pair(ip)
            } catch (e: Exception) {
                a.close()
                ui.post { message(explain(e.message), true) }
            }
        }.start()
    }

    private val adbFailed = HashSet<String>()                        // TVs whose ADB port answers but would not let us in, this session

    /** While on the keys-only remote: the moment the TV starts answering on its ADB port, move over to the full connection. */
    private fun watchAdb(a: Atv, ip: String) {
        if (ip in adbFailed) return
        Thread {
            while (basic === a && !isFinishing) {
                Thread.sleep(4000)
                if (basic === a && open(ip, 5555, 1200)) {
                    ui.post {
                        if (basic !== a || isFinishing) return@post
                        Toast.makeText(this, t("TV đã bật ADB: đang chuyển sang kết nối đầy đủ. Nhìn lên TV và chọn “Luôn cho phép”.", "ADB is on: switching to the full connection. Look at the TV and choose “Always allow”."), Toast.LENGTH_LONG).show()
                        val e = entry
                        disconnect(null)
                        connect(ip, e)
                    }
                    return@Thread
                }
            }
        }.apply { isDaemon = true }.start()
    }

    /** On a worker thread: the TV shows a code, the person types it here. */
    private fun pair(ip: String) {
        val p = Atv(filesDir, ip)
        try { p.startPairing() } catch (e: Exception) {
            p.close()
            ui.post { message(t("Không ghép được với TV: ", "Could not pair with the TV: ") + e.message, true) }
            return
        }
        ui.post {
            message(null, false)
            val code = input(t("Mã 6 ký tự trên TV", "The 6 characters on the TV"))
            code.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            val wrap = FrameLayout(this)
            wrap.setPadding(dp(20), dp(8), dp(20), 0)
            wrap.addView(code)
            AlertDialog.Builder(this).setTitle(t("Nhập mã đang hiện trên TV", "Type the code shown on the TV")).setView(wrap)
                .setPositiveButton(t("Ghép", "Pair")) { _, _ ->
                    val typed = code.text.toString()
                    Thread {
                        try {
                            p.finishPairing(typed)
                            ui.post { startBasic(ip, true) }
                        } catch (e: Exception) {
                            p.close()
                            ui.post { message(t("Mã không đúng. Bấm vào TV trong danh sách để thử lại, TV sẽ hiện mã mới.", "Wrong code. Tap the TV in the list to try again: it will show a new code."), true) }
                        }
                    }.start()
                }
                .setNegativeButton(t("Hủy", "Cancel")) { _, _ -> p.close() }
                .setOnCancelListener { p.close() }.show()
        }
    }

    // Getting to the TV's Settings without ADB. Most TVs open them on the Settings key. Operator boxes may ignore it and hide
    // Settings on their own home screen; then the way in is another home screen app from the Play Store, which the TV opens
    // when asked for the app's package (the app itself when it is installed, its store page when it is not).
    private val HOMES = arrayOf("Projectivy Launcher" to "com.spocky.projengmenu", "ATV Launcher" to "ca.dstudio.atvlauncher.free", "FLauncher" to "me.efesser.flauncher")

    /** Everything the keys-only mode can do towards turning ADB on, in the order one needs it. */
    /** Makes the TV open a link: a Play Store page, a YouTube video, a web address. Works with and without ADB. */
    private fun openLink(link: String) {
        val b = basic
        val tv = tv
        if (b != null) b.launch(link)
        else if (tv != null && tv.ready()) Thread { try { tv.shell("am start -a android.intent.action.VIEW -d " + Adb.q(link)) } catch (ignored: Exception) { } }.start()
    }

    // ---------------------------------------------------------------- "Share → TV Remote" from other apps, and playing a file on the TV
    private var shared: Intent? = null
    /** On the remote screen the picker's message line is out of sight: say it in a toast there. */
    private fun say(text: String, error: Boolean) {
        if (host == null) message(text, error) else Toast.makeText(this, text, if (error) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
    private val PICK_CAST = 71

    /** Another app shared a link or a file with us: keep it until a TV is connected, then send it there. */
    private fun takeShare(i: Intent?): Boolean {
        if (i == null || Intent.ACTION_SEND != i.action) return false
        shared = i
        setIntent(Intent(this, MainActivity::class.java))             // not again after a rotation
        sendShared(0)
        return true
    }

    private fun sendShared(tries: Int) {
        val i = shared ?: return
        if (!live()) {
            if (host == null) message(t("Chọn TV để phát nội dung vừa chia sẻ.", "Choose a TV to play what you shared."), false)
            if (tries < 90) ui.postDelayed({ if (!isFinishing) sendShared(tries + 1) }, 1000) else shared = null
            return
        }
        shared = null
        @Suppress("DEPRECATION")
        val file = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (file != null) { castFile(file); return }
        val link = Regex("[a-zA-Z][a-zA-Z0-9+.-]*://\\S+").find(i.getStringExtra(Intent.EXTRA_TEXT) ?: "")?.value
        if (link == null) say(t("Nội dung chia sẻ không có đường dẫn nào để mở trên TV.", "What was shared has no link to open on the TV."), true)
        else { openLink(link); Toast.makeText(this, t("Đã gửi lên TV", "Sent to the TV"), Toast.LENGTH_SHORT).show() }
    }

    /** A photo, video or song from this phone: copied to the TV's Download folder, then opened by the TV's own viewer or player. */
    private fun castFile(uri: Uri) {
        val ip = host
        if (ip == null || tv?.ready() != true) {
            say(t("Phát tệp lên TV cần ADB. TV này mới chỉ nối bằng nút remote: vào “Bật ADB cho TV này” trước.", "Playing a file on the TV needs ADB. This TV is connected with the remote buttons only: use “Turn on ADB for this TV” first."), true)
            return
        }
        var name = "shared"
        var size = -1L
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME).let { if (it >= 0) c.getString(it)?.let { n -> name = n } }
                    c.getColumnIndex(android.provider.OpenableColumns.SIZE).let { if (it >= 0 && !c.isNull(it)) size = c.getLong(it) }
                }
            }
        } catch (ignored: Exception) {
        }
        if (!name.contains('.')) android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(contentResolver.getType(uri))?.let { name += ".$it" }
        say(t("Đang gửi lên TV: ", "Sending to the TV: ") + name + "...", false)
        Thread {
            val link = AdbLink(filesDir, ip)
            val err = try {
                contentResolver.openInputStream(uri).use { input ->
                    if (input == null) throw java.io.IOException("cannot read the file")
                    Manage(link, null, cacheDir).cast(input, size, name)
                }
                null
            } catch (e: Exception) { e.message ?: e.toString() } finally { link.close() }
            ui.post {
                if (isFinishing) return@post
                if (err == null) say(t("Đang phát trên TV: ", "Playing on the TV: ") + name, false)
                else say(t("Không phát được: ", "Could not play it: ") + err, true)
            }
        }.start()
    }

    private fun askText(title: String, hint: String, ok: String, then: (String) -> Unit) {
        val box = input(hint)
        val wrap = FrameLayout(this)
        wrap.setPadding(dp(20), dp(8), dp(20), 0)
        wrap.addView(box)
        AlertDialog.Builder(this).setTitle(title).setView(wrap)
            .setPositiveButton(ok) { _, _ -> box.text.toString().trim().let { if (it.isNotEmpty()) then(it) } }
            .setNegativeButton(t("Hủy", "Cancel"), null).show()
    }

    /** Things the TV can be told to open straight from the phone, saving the hunt with the TV's own keyboard. */
    private fun quickOpen() {
        if (!live()) return
        val store = { id: String -> openLink("https://play.google.com/store/apps/details?id=$id") }
        val items = ArrayList<Triple<String, String, () -> Unit>>()
        items.add(Triple(t("Tìm app trên CH Play", "Search the Play Store"), t("Gõ tên app ở đây, TV mở kết quả tìm kiếm", "Type the name here; the TV shows the results")) {
            askText(t("Tìm app trên CH Play", "Search the Play Store"), t("vd: vlc", "e.g. vlc"), t("Tìm", "Search")) { openLink("https://play.google.com/store/search?c=apps&q=" + java.net.URLEncoder.encode(it, "UTF-8")) }
        })
        items.add(Triple(t("Mở trang CH Play theo app id", "Open a Play Store page by app id"), t("vd: org.videolan.vlc. Từ đó bấm Cài đặt hoặc Mở trên TV", "e.g. org.videolan.vlc. Then choose Install or Open on the TV")) {
            askText(t("App id (tên gói)", "App id (package name)"), "org.videolan.vlc", t("Mở", "Open")) { store(it) }
        })
        items.add(Triple(t("Mở đường dẫn trên TV", "Open a link on the TV"), t("Video YouTube, trang web, file tải về... TV mở bằng app phù hợp", "A YouTube video, a web page, a download... the TV opens it with a fitting app")) {
            askText(t("Đường dẫn", "Link"), "https://youtu.be/...", t("Mở", "Open")) { openLink(if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(it)) it else "https://$it") }
        })
        items.add(Triple(t("Phát ảnh / video / nhạc từ điện thoại", "Play a photo / video / song from the phone"), t("Chọn tệp, app chép sang TV rồi TV tự mở. Cần ADB. Cũng dùng được nút Chia sẻ → TV Remote ở app khác", "Pick a file; it is copied to the TV, which opens it. Needs ADB. Share → TV Remote in other apps does the same")) {
            try { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*", "audio/*")).addCategory(Intent.CATEGORY_OPENABLE), PICK_CAST) }
            catch (e: Exception) { say(t("Điện thoại không có app chọn tệp.", "This phone has no file picker."), true) }
        })
        items.add(Triple(t("Chiếu màn hình điện thoại lên TV", "Mirror the phone’s screen on the TV"), t("Mở phần Truyền (Cast) của Android: chọn TV trong danh sách. Điện thoại và TV phải cùng Wi-Fi", "Opens Android’s Cast screen: pick the TV in the list. Phone and TV must be on the same Wi-Fi")) {
            try { startActivity(Intent("android.settings.CAST_SETTINGS")) }
            catch (e: Exception) { say(t("Điện thoại này không có phần Truyền (Cast) trong Cài đặt. Thử nút Truyền / Smart View ở thanh thông báo.", "This phone has no Cast screen in Settings. Try the Cast / Smart View tile in the notification shade."), true) }
        })
        for ((name, id) in listOf("YouTube" to "com.google.android.youtube.tv", "Netflix" to "com.netflix.ninja", "VLC" to "org.videolan.vlc", "Kodi" to "org.xbmc.kodi",
            "Downloader" to "com.esaba.downloader", "Projectivy Launcher" to "com.spocky.projengmenu", "Tailscale" to "com.tailscale.ipn"))
            items.add(Triple(name, t("Trang CH Play: cài hoặc mở", "Play Store page: install or open")) { store(id) })
        menu(t("Mở trên TV", "Open on the TV"), -1, items)
    }

    /**
     * A menu whose choices look like what they are: buttons. Each has a title, a line saying what it does, and an arrow.
     * [primary] is the one to press first. Each item: title, explanation, action.
     */
    private fun menu(title: String, primary: Int, items: List<Triple<String, String, () -> Unit>>) {
        val list = col()
        list.setPadding(dp(16), dp(8), dp(16), dp(4))
        var dialog: AlertDialog? = null
        items.forEachIndexed { i, (name, what, run) ->
            val row = LinearLayout(this)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(14), dp(12), dp(10), dp(12))
            val color = if (i == primary) ACCENT else BTN
            val normal = box(color, 14)
            row.background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x40ffffff), normal, null)
            val texts = col()
            val head = label(name, 16, if (i == primary) Color.WHITE else TEXT)
            head.typeface = Typeface.DEFAULT_BOLD
            texts.addView(head)
            if (what.isNotEmpty()) texts.addView(label(what, 13, if (i == primary) 0xffe3ebff.toInt() else MUTED))
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(label("›", 24, if (i == primary) Color.WHITE else MUTED))
            row.isClickable = true
            row.isFocusable = true
            row.contentDescription = name
            row.setOnClickListener { dialog?.dismiss(); run() }
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = dp(8)
            list.addView(row, lp)
        }
        val sv = ScrollView(this)
        sv.addView(list)
        dialog = AlertDialog.Builder(this).setTitle(title).setView(sv).setNegativeButton(t("Đóng", "Close"), null).show()
    }

    /**
     * One job on the TV at a time. These jobs open apps and press keys there over several seconds; a second tap on the same
     * button while one runs would send it all again and leave the TV in a muddle, so it is refused with a word of why.
     */
    private val tvBusy = java.util.concurrent.atomic.AtomicBoolean(false)
    private fun tvFree(): Boolean {
        if (tvBusy.compareAndSet(false, true)) return true
        Toast.makeText(this, t("TV đang làm việc vừa yêu cầu. Chờ vài giây rồi bấm lại.", "The TV is still doing what was just asked. Wait a few seconds, then tap again."), Toast.LENGTH_SHORT).show()
        return false
    }
    private fun tvJob(work: () -> Unit) {
        if (!tvFree()) return
        Toast.makeText(this, t("Đang làm trên TV...", "Working on the TV..."), Toast.LENGTH_SHORT).show()
        Thread { try { work() } catch (ignored: Exception) { } finally { tvBusy.set(false) } }.start()
    }

    /** Everything the keys-only mode can do towards turning ADB on, in the order one needs it. */
    private fun adbHelp() {
        menu(t("Bật ADB cho TV này", "Turn on ADB for this TV"), 0, listOf(
            Triple(t("Tự động: cài TV Setup lên TV", "Automatic: install TV Setup on the TV"), t("App tự tải và mở app TV Setup trên TV; nó mở thẳng Tùy chọn nhà phát triển. Bạn chỉ xác nhận trên TV", "The app fetches and opens the TV Setup app on the TV, which opens Developer options directly. You only confirm on the TV")) { installSetup() },
            Triple(t("Tự làm: mở Cài đặt TV", "By hand: open TV Settings"), t("App thử mọi cách mở Cài đặt, rồi chỉ các bước bật Gỡ lỗi ADB", "The app tries every way to open Settings, then shows the steps to turn ADB on")) { findSettings() },
            Triple(t("Cài màn hình chính khác", "Install another home screen"), t("Khi TV giấu Cài đặt: mở qua một launcher từ CH Play", "When the TV hides Settings: go through a launcher from the Play Store")) { otherHome() },
            Triple(t("Kết nối đầy đủ", "Full connection"), t("App tự chuyển khi TV bật ADB; bấm nếu muốn thử ngay", "The app switches by itself once ADB is on; tap to try now")) {
                val ip = host
                adbFailed.remove(ip)
                val e = entry
                disconnect(null)
                if (ip != null) connect(ip, e)
            },
            Triple(t("Mở CH Play trên TV", "Open the Play Store on the TV"), "") { basic?.launch("https://play.google.com/store/apps"); Unit },
            Triple(t("Mở app theo tên gói", "Open an app by package name"), t("Đã cài thì mở app, chưa cài thì mở trang CH Play của nó", "Opens the app, or its Play Store page when not installed")) {
                val box = input("com.example.app")
                val wrap = FrameLayout(this)
                wrap.setPadding(dp(20), dp(8), dp(20), 0)
                wrap.addView(box)
                AlertDialog.Builder(this).setTitle(t("Tên gói hoặc đường dẫn", "Package name or link")).setView(wrap)
                    .setPositiveButton(t("Mở", "Open")) { _, _ ->
                        val what = box.text.toString().trim()
                        val b = basic
                        if (what.isNotEmpty() && b != null) { if (what.contains(":")) b.launch(what) else tvJob { openOnTv(b, what) { false } } }
                    }
                    .setNegativeButton(t("Hủy", "Cancel"), null).show()
                Unit
            }))
    }

    /**
     * Getting our TV app onto a TV that has no ADB yet, in one tap: the Downloader app (installed from the Play Store when
     * missing) fetches it, and the phone opens Downloader, types the link and presses Go. What only the person can do is
     * confirm the install on the TV.
     */
    private fun installSetup() {
        val a = basic ?: return
        if (!tvFree()) return
        val link = "github.com/hoangtran0410/tv-remote/releases/latest/download/tv-setup.apk"
        val dl = "com.esaba.downloader"
        var stop = false
        var installed = false                                         // the person says the TV has finished installing Downloader
        val box = AlertDialog.Builder(this).setTitle(t("Cài TV Setup lên TV", "Install TV Setup on the TV")).setMessage(t("Đang mở Downloader trên TV...", "Opening Downloader on the TV..."))
            .setNeutralButton(t("Đã cài xong", "Installed"), null)
            .setNegativeButton(t("Dừng", "Stop")) { _, _ -> stop = true }.setOnCancelListener { stop = true }.show()
        val doneBtn = box.getButton(AlertDialog.BUTTON_NEUTRAL)
        val say = { vi: String, en: String -> ui.post { if (box.isShowing) box.setMessage(t(vi, en)) } }
        // The box changes its words as it goes. It must not change its size with them, or the buttons slide away from under
        // a finger that is still tapping, and a tap that lands beside the box must not close it and stop the whole thing.
        box.setCanceledOnTouchOutside(false)
        box.findViewById<TextView>(android.R.id.message)?.minLines = 7
        // greyed out while the check runs; its words stay, or the buttons would shift under a finger that is still tapping
        val checking = { on: Boolean -> ui.post { doneBtn.isEnabled = !on; doneBtn.alpha = if (on) 0.4f else 1f } }
        doneBtn.setOnClickListener { if (!installed) { installed = true; checking(true); say("Đang kiểm tra trên TV, chờ khoảng nửa phút...", "Checking on the TV, about half a minute...") } }   // set here so that the box stays open; one check at a time
        // waits until the TV says [pkg] is in front; false when it does not within [seconds], or the person stopped it
        val front = { pkg: String, seconds: Int ->
            var ok = false
            for (i in 0 until seconds * 5) { if (stop || basic !== a) break; if (a.app == pkg) { ok = true; break }; Thread.sleep(200) }
            ok
        }
        Thread {
            try {
                if (a.app.isEmpty()) {                                    // this TV does not say what is on its screen: no way to follow along
                    ui.post { box.dismiss(); installSetupByHand() }
                    return@Thread
                }
                // The short way: hand the TV the link itself. A TV with an app that takes web links (Downloader, a browser) opens
                // it there, after asking "Open with" when it has several; OK takes what is offered, and either of them downloads.
                a.launch("https://$link")
                for (i in 0 until 30) { if (stop || a.app == dl || a.app == "android") break; Thread.sleep(200) }
                if (a.app == "android" && !stop) {
                    say("TV đang hỏi “Mở bằng”: app tự chọn giúp...", "The TV asks “Open with”: choosing for you...")
                    for (press in 0 until 3) {
                        Thread.sleep(1500)
                        if (a.app != "android" || stop) break
                        a.pulse(23, 110)
                    }
                    Thread.sleep(1500)
                }
                val direct = !stop && a.app != "android" && a.app != "com.fptplay.launcher" && a.app.isNotEmpty() && (a.app == dl || a.app.contains("chrome") || a.app.contains("browser"))
                var there = direct || openOnTv(a, dl) { stop }
                if (!there && !stop && basic === a) {                     // its Play Store page was told to install it
                    say("TV chưa có Downloader: đang cài từ CH Play...\n\nNếu TV không tự cài, bấm “Cài đặt” trên màn hình TV. Khi TV báo cài xong, bấm “Đã cài xong” bên dưới.",
                        "The TV has no Downloader yet: installing it from the Play Store...\n\nIf the TV does not start by itself, choose “Install” on its screen. When the TV says it is installed, tap “Installed” below.")
                    while (!stop && basic === a && !there) {
                        for (i in 0 until 600) { if (stop || installed || a.app == dl) break; Thread.sleep(200) }
                        if (stop || basic !== a) break
                        checking(true)
                        say("Đang mở Downloader trên TV để kiểm tra...", "Opening Downloader on the TV to check...")
                        there = a.app == dl || openOnTv(a, dl) { stop }
                        installed = false
                        checking(false)
                        if (!there) say("TV vẫn chưa có Downloader. Chọn “Cài đặt” trên màn hình TV; khi TV báo cài xong, bấm “Đã cài xong”.", "The TV still has no Downloader. Choose “Install” on its screen; when it says installed, tap “Installed”.")
                    }
                }
                if (!there) {
                    ui.post { if (box.isShowing) box.dismiss(); if (!stop && basic === a) installSetupByHand() }
                    return@Thread
                }
                if (!direct) {                                            // Downloader is there now, so the TV has an app for the link
                    say("Đang đưa đường dẫn tải cho Downloader...", "Handing the download link to Downloader...")
                    Thread.sleep(3000)
                    a.launch("https://$link")
                    for (press in 0 until 3) {                            // "Open with", when a browser is installed too
                        Thread.sleep(1500)
                        if (a.app != "android" || stop) break
                        a.pulse(23, 110)
                    }
                    Thread.sleep(2500)
                }
                val from = a.app
                say("Đã nhập đường dẫn. Đang chờ TV tải xong...", "Link entered. Waiting for the TV to finish the download...")
                for (i in 0 until 240) {                                  // up to 2 minutes, until the TV's installer (or its permission screen) comes up
                    if (stop || basic !== a || a.app != from) break
                    Thread.sleep(500)
                }
                ui.post {
                    if (box.isShowing) box.dismiss()
                    if (stop || basic !== a || isFinishing) return@post
                    AlertDialog.Builder(this).setTitle(t("Nhìn lên TV", "Look at the TV"))
                        .setMessage(t("App đã nhập đường dẫn và cho Downloader tải.\n\n1. TV hỏi cài đặt: chọn Cài đặt (Install). Nếu TV hỏi quyền cài từ nguồn này: bật cho Downloader, bấm Quay lại, rồi chọn Install.\n2. Cài xong chọn Mở (hoặc bấm “Mở TV Setup” bên dưới).\n3. TV Setup tự mở Tùy chọn nhà phát triển: bật “Gỡ lỗi USB”.\n\nNếu TV vẫn đứng ở trang chủ Downloader: bấm “Làm từng bước”.",
                            "The app has entered the link and Downloader is fetching it.\n\n1. The TV asks to install: choose Install. If it asks to allow installs from this source: allow Downloader, press Back, then choose Install.\n2. Then choose Open (or tap “Open TV Setup” below).\n3. TV Setup opens Developer options by itself: turn on “USB debugging”.\n\nIf the TV is still on Downloader’s home page: tap “Step by step”."))
                        .setPositiveButton(t("Mở TV Setup", "Open TV Setup")) { _, _ -> tvJob { openOnTv(a, "dev.tvremote.setup") { false } } }
                        .setNeutralButton(t("Làm từng bước", "Step by step")) { _, _ -> installSetupByHand() }
                        .setNegativeButton(t("Đóng", "Close"), null).show()
                }
            } catch (ignored: Exception) {
            } finally {
                tvBusy.set(false)
            }
        }.start()
    }

    /**
     * Brings the app [pkg] to the front on the TV, on a worker thread; true once the TV says it is there. Asking for the app by
     * name works on some TVs and not on others (and not every time), so the sure way follows: its Play Store page, where OK
     * presses the main button. That is "Open" when the app is installed; when it is not, the same press starts installing it,
     * and this returns false.
     */
    private fun openOnTv(a: Atv, pkg: String, stop: () -> Boolean): Boolean {
        val front = { want: String, seconds: Int ->
            var ok = false
            for (i in 0 until seconds * 5) { if (stop() || basic !== a) break; if (a.app == want) { ok = true; break }; Thread.sleep(200) }
            ok
        }
        if (a.app == pkg) return true
        a.launch(pkg)
        if (front(pkg, 4)) return true
        a.launch("https://play.google.com/store/apps/details?id=$pkg")
        if (!front("com.android.vending", 10)) return false
        Thread.sleep(3000)                                            // the page draws, with its main button in focus
        a.pulse(23, 110)
        return front(pkg, 15)
    }

    /**
     * Drives Downloader's home page to load [link], on a worker thread. The page has a pointer and no field in focus, so:
     * hold Up until the pointer leaves the page and the toolbar takes the focus; go to its right end and two back, which is
     * the address field wherever the focus first landed; OK opens the field with the TV's keyboard; type; Enter loads.
     */
    private fun downloaderGo(a: Atv, link: String) {
        for (i in 0 until 40) a.pulse(19, 70)                          // many short holds: over this protocol a key cannot be kept down, each one nudges the pointer
        for (i in 0 until 4) a.pulse(22, 110)
        for (i in 0 until 2) a.pulse(21, 110)
        Thread.sleep(40 * 90L + 6 * 350L)                             // the keys above are queued; let them play out
        a.pulse(23, 110)
        Thread.sleep(2500)                                            // the field and the keyboard open
        a.keys(link)
        Thread.sleep(link.length * 60L + 1000)
        a.key(66, Atv.SHORT)
    }

    /** The same, one step at a time, for when the automatic run loses its way. */
    private fun installSetupByHand() {
        val link = "github.com/hoangtran0410/tv-remote/releases/latest/download/tv-setup.apk"   // lower case, no "https://": typed as key presses, and Downloader adds the rest
        menu(t("Cài TV Setup lên TV", "Install TV Setup on the TV"), -1, listOf(
            Triple(t("1. Mở Downloader trên TV", "1. Open Downloader on the TV"), t("Chưa cài thì TV mở trang CH Play của nó: bấm Cài đặt, rồi bấm lại mục này", "Not installed: the TV opens its Play Store page. Install it, then tap this again")) {
                basic?.let { b -> tvJob { openOnTv(b, "com.esaba.downloader") { false } } }
                ui.postDelayed({ if (basic != null && !isFinishing) installSetupByHand() }, 600)
            },
            Triple(t("2. Nhập đường dẫn tải", "2. Enter the download link"), t("Khi TV đang ở trang chủ Downloader: app tự tới ô địa chỉ, gõ đường dẫn và cho tải. Mất khoảng 10 giây", "With Downloader’s home page on the TV: the app goes to the address field, types the link and starts the download. About 10 seconds")) {
                basic?.let { b -> tvJob { downloaderGo(b, link) } }
                Toast.makeText(this, t("Đang nhập đường dẫn trên TV...", "Entering the link on the TV..."), Toast.LENGTH_LONG).show()
                ui.postDelayed({ if (basic != null && !isFinishing) installSetupByHand() }, 600)
            },
            Triple(t("3. Mở TV Setup trên TV", "3. Open TV Setup on the TV"), t("Trong đó bấm “Tùy chọn nhà phát triển” rồi bật Gỡ lỗi USB", "There choose “Developer options” and turn on USB debugging")) { basic?.let { b -> tvJob { openOnTv(b, "dev.tvremote.setup") { false } } }; Unit },
            Triple(t("Chép đường dẫn", "Copy the link"), link) {
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("tv-setup", link))
                Toast.makeText(this, t("Đã chép", "Copied"), Toast.LENGTH_SHORT).show()
            }))
    }

    private fun isSettings(pkg: String) = pkg.contains("settings", true)

    /** Tries the Settings key, then the links that open Settings on other TVs, watching which app the TV says is in front. */
    private fun findSettings() {
        val a = basic ?: return
        if (!tvFree()) return
        Toast.makeText(this, t("Đang thử mở Cài đặt trên TV...", "Trying to open Settings on the TV..."), Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val tries = listOf<() -> Unit>({ a.key(176, Atv.SHORT) }, { a.launch("android-app://com.android.tv.settings") }, { a.launch("intent:#Intent;action=android.settings.SETTINGS;end") })
                var ok = isSettings(a.app)
                for (attempt in tries) {
                    if (ok) break
                    for (i in 0 until 30) { if (a.ready()) break; Thread.sleep(200) }   // the TV may have dropped us after the last try
                    attempt()
                    for (i in 0 until 12) { Thread.sleep(200); if (isSettings(a.app)) { ok = true; break } }
                }
                val blind = a.app.isEmpty()                                // this TV does not say what is on its screen
                ui.post {
                    if (isFinishing || basic !== a) return@post
                    if (ok) adbSteps()
                    else if (blind) AlertDialog.Builder(this).setMessage(t("TV có mở màn hình Cài đặt không?", "Did the TV open its Settings?"))
                        .setPositiveButton(t("Có", "Yes")) { _, _ -> adbSteps() }.setNegativeButton(t("Không", "No")) { _, _ -> lookOnHome() }.show()
                    else lookOnHome()
                }
            } catch (ignored: Exception) {
            } finally {
                tvBusy.set(false)
            }
        }.start()
    }

    /** The keys and links did nothing: the TV's own home screen may still have Settings somewhere, before anything gets installed. */
    private fun lookOnHome() {
        AlertDialog.Builder(this).setTitle(t("TV không mở Cài đặt theo lệnh", "The TV did not open Settings when asked"))
            .setMessage(t("Tìm trên màn hình chính của TV, dùng các phím mũi tên ở đây: biểu tượng hồ sơ/tài khoản hoặc bánh răng (thường ở góc trên) → Cài đặt → “Cài đặt thiết bị”.\n\nBox FPT Play: mở bảng Quản lý (hồ sơ) → Cài đặt → Cài đặt thiết bị.",
                "Look on the TV’s home screen, using the arrows here: the profile/account or gear icon (usually in a top corner) → Settings → “Device settings”.\n\nFPT Play box: open the profile panel → Settings → Device settings."))
            .setPositiveButton(t("Thấy rồi", "Found it")) { _, _ -> adbSteps() }
            .setNegativeButton(t("Không có · Cài màn hình chính khác", "Not there · Install another home screen")) { _, _ -> otherHome() }.show()
    }

    private fun otherHome() {
        val a = basic ?: return
        menu(t("Mở Cài đặt qua màn hình chính khác", "Reach Settings through another home screen"), -1, HOMES.map { (name, pkg) ->
            Triple(name, if (a.app == pkg) t("Đang mở trên TV", "Open on the TV now") else t("Đã cài thì mở, chưa cài thì mở trang CH Play", "Opens it, or its Play Store page")) {
                tvJob { openOnTv(a, pkg) { false } }
                AlertDialog.Builder(this).setTitle(name).setMessage(t("Nhìn lên TV.\n\n• Thấy trang CH Play: bấm Cài đặt, xong bấm “Mở lại” bên dưới.\n• CH Play báo không tương thích hoặc không tìm thấy: bấm “Chọn cái khác”.\n• Thấy màn hình chính mới: tìm biểu tượng bánh răng → Cài đặt Android, rồi bấm “Các bước bật ADB”.\n\nCH Play cần TV đã đăng nhập tài khoản Google.",
                        "Look at the TV.\n\n• A Play Store page: choose Install, then tap “Open again” below.\n• The store says it is incompatible or not found: tap “Pick another”.\n• The new home screen: find the gear icon → Android settings, then tap “ADB steps”.\n\nThe Play Store needs a Google account on the TV."))
                    .setPositiveButton(t("Các bước bật ADB", "ADB steps")) { _, _ -> adbSteps() }
                    .setNeutralButton(t("Mở lại", "Open again")) { _, _ -> tvJob { openOnTv(a, pkg) { false } }; ui.postDelayed({ if (basic === a && !isFinishing) adbSteps() }, 800) }
                    .setNegativeButton(t("Chọn cái khác", "Pick another")) { _, _ -> otherHome() }.show()
                Unit
            }
        })
    }

    private fun adbSteps() {
        AlertDialog.Builder(this).setTitle(t("Bật Gỡ lỗi ADB trên TV", "Turn on ADB debugging on the TV"))
            .setMessage(t("Dùng các phím mũi tên và OK của remote này:\n\n1. Trong Cài đặt: Tùy chọn thiết bị (hoặc Hệ thống) → Giới thiệu → bấm OK 7 lần vào “Bản dựng”.\n2. Quay lại, vào Tùy chọn nhà phát triển → bật “Gỡ lỗi USB” (hoặc “Gỡ lỗi ADB”).\n3. Bấm “Kết nối đầy đủ”, rồi trên TV tích “Luôn cho phép” và chọn OK.\n\nNếu TV đòi “mật khẩu ADB”: nhà mạng đã khóa chức năng này (vd. box FPT Play). Chỉ nhà mạng có mật khẩu; khi đó TV này chỉ dùng được chế độ bấm phím.",
                "Use this remote’s arrows and OK:\n\n1. In Settings: Device Preferences (or System) → About → press OK 7 times on “Build”.\n2. Go back, open Developer options → turn on “USB debugging” (or “ADB debugging”).\n3. Tap “Full connection”, then on the TV tick “Always allow” and choose OK.\n\nIf the TV asks for an “ADB password”: the operator has locked this (some operator boxes do). Only they have the password; this TV then works in keys-only mode only."))
            .setPositiveButton(t("Kết nối đầy đủ", "Full connection")) { _, _ ->
                val ip = host
                val e = entry
                disconnect(null)
                if (ip != null) connect(ip, e)
            }
            .setNegativeButton(t("Đóng", "Close"), null).show()
    }

    private fun explain(err: String?): String {
        val e = err?.lowercase(Locale.ROOT) ?: ""
        if (refused(err))
            return t("TV từ chối kết nối: chưa bật “Gỡ lỗi ADB”.\n\nTrên TV: Cài đặt → Tùy chọn thiết bị → Giới thiệu → bấm OK 7 lần vào “Bản dựng”. Rồi Tùy chọn nhà phát triển → bật “Gỡ lỗi ADB” (hoặc “Gỡ lỗi USB”).",
                "The TV refused the connection: “ADB debugging” is off.\n\nOn the TV: Settings → Device Preferences → About → press OK 7 times on “Build”. Then Developer options → turn on “ADB debugging” (or “USB debugging”).")
        if (e.contains("timed out") || e.contains("timeout") || e.contains("unreachable") || e.contains("no route") || e.contains("failed to connect"))
            return t("Không tới được TV ở địa chỉ này. TV có đang bật không? Điện thoại phải cùng Wi-Fi với TV, hoặc cả hai cùng bật Tailscale (khi đó dùng IP 100.x của TV).",
                "Cannot reach the TV at this address. Is it on? The phone must be on the TV’s Wi-Fi, or both must have Tailscale on (then use the TV’s 100.x IP).") + "\n\n" +
                t("Nếu TV đang tắt, bấm nút nguồn cạnh tên TV để đánh thức.", "If the TV is off, tap the power button next to it to wake it.") + "\n\n(" + err + ")"
        return t("Lỗi: ", "Error: ") + err
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

    // ---------------------------------------------------------------- updates (GitHub releases)
    /** "2.5.4 · 13/10/2026": this app's version and the day it was built. */
    private fun versionLine(): String = version() + released().let { if (it.isEmpty()) "" else " · $it" }

    /** The day this build was made, as dd/mm/yyyy ("" when unknown). */
    private fun released(): String = try {
        getString(resources.getIdentifier("released", "string", packageName)).split("-").let { it[2] + "/" + it[1] + "/" + it[0] }
    } catch (e: Exception) { "" }

    private fun version(): String =
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "0" } catch (e: Exception) { "0" }

    private fun checkUpdate(manual: Boolean) {
        if (manual) updateNote.text = t("Đang kiểm tra...", "Checking...")
        Thread {
            var found: Release? = null
            var error: String? = null
            try { found = Release.newer(version(), "android", "tv-remote.apk") } catch (e: Exception) { error = e.message ?: e.javaClass.simpleName }
            val r = found
            val err = error
            ui.post {
                if (isFinishing) return@post
                if (err == null) {
                    update = r
                    showUpdate()
                }
                if (manual) updateNote.text = when {
                    err != null -> t("Không kiểm tra được: ", "Could not check: ") + err
                    r == null -> t("Đang dùng bản mới nhất (", "You have the latest version (") + version() + ")."
                    else -> t("Có bản mới ", "New version ") + r.version
                }
                if (manual && r != null) askUpdate()                  // asked for it: offer the update right here
            }
        }.start()
    }

    private fun showUpdate() {
        val u = update
        updateBtn.visibility = if (u == null) View.GONE else View.VISIBLE
        if (u != null && !updating) updateBtn.text = "🆕  " + t("Có bản mới ", "New version ") + u.version + t(" · bấm để cập nhật", " · tap to update")
    }

    /** The download shows both on the TV list and in Settings, wherever the update was started from. */
    private fun progress(s: String) {
        updateBtn.text = s
        updateNote.text = s
    }

    private fun askUpdate() {
        val r = update
        if (r == null || updating) return
        val all = r.notes.replace("**", "").replace("\r", "")         // release notes are Markdown
        val notes = if (all.length > 900) all.substring(0, 900) + "..." else all

        AlertDialog.Builder(this).setTitle(t("Bản mới ", "Version ") + r.version + t(" (đang dùng ", " (you have ") + version() + ")")
            .setMessage(notes.ifEmpty { t("Tải và cài bản mới?", "Download and install the new version?") })
            .setPositiveButton(t("Cập nhật", "Update")) { _, _ -> startUpdate(r) }.setNegativeButton(t("Để sau", "Later"), null).show()
    }

    /** Downloads the APK from the release, then hands it to Android, which asks the person to confirm the install. */
    private fun startUpdate(r: Release) {
        updating = true
        progress(t("Đang tải bản ", "Downloading ") + r.version + "...")
        Thread {
            var error: String? = null
            try {
                val apk = File(cacheDir, "update.apk")
                Release.download(r.url, apk) { pct -> ui.post { progress(t("Đang tải bản ", "Downloading ") + r.version + "... " + pct + "%") } }
                val pi = packageManager.packageInstaller
                val id = pi.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
                pi.openSession(id).use { session ->
                    session.openWrite("apk", 0, apk.length()).use { out ->
                        FileInputStream(apk).use { input ->
                            val b = ByteArray(65536)
                            while (true) {
                                val n = input.read(b)
                                if (n <= 0) break
                                out.write(b, 0, n)
                            }
                            session.fsync(out)
                        }
                    }
                    val back = Intent(this, MainActivity::class.java).setAction(INSTALL_RESULT)
                    session.commit(PendingIntent.getActivity(this, id, back,
                        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) 0x02000000 /* FLAG_MUTABLE */ else 0)).intentSender)
                }
                apk.delete()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
            val err = error
            ui.post {
                if (err == null) {
                    progress(t("Đang chờ Android xác nhận cài đặt...", "Waiting for Android to confirm the install..."))
                    return@post
                }
                updating = false
                showUpdate()
                message(t("Không cập nhật được: ", "Could not update: ") + err, true)
            }
        }.start()
    }

    /** Android reports back here: first "the person must confirm" (with the screen to show), then the result. */
    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        if (takeShare(i)) return
        if (INSTALL_RESULT != i.action) return
        val status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirm != null) startActivity(confirm)
            return
        }
        if (status == PackageInstaller.STATUS_SUCCESS) return         // the app is restarted by the install anyway
        updating = false
        showUpdate()
        message(if (status == PackageInstaller.STATUS_FAILURE_ABORTED) t("Đã hủy cập nhật.", "Update cancelled.")
        else t("Không cài được bản mới: ", "Could not install the new version: ") + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE), true)
    }

    // ---------------------------------------------------------------- waking a TV that is off
    /**
     * Standby with the network still on: the TV answers, so connect and press "wake". Network off too: only a
     * Wake-on-LAN packet helps, and that reaches the TV from its own Wi-Fi only (never through Tailscale).
     */
    private fun wake(ip: String) {
        if (waking) return
        waking = true
        val mac = pref("mac:$ip")
        val lan = pref("lan:$ip")
        message(t("Đang đánh thức ", "Waking ") + title(ip) + "...", false)
        Thread {
            var up = open(ip, 5555, 1500)
            var sent = 0
            var i = 0
            val hopeless = mac.isEmpty() || !Wol.sameNetwork(lan)     // no packet can reach the TV from here: say so at once instead of waiting
            while (i < 22 && !up && !hopeless) {
                if (i % 6 == 0 && mac.isNotEmpty()) sent += Wol.send(mac, lan)
                try { Thread.sleep(1000) } catch (e: InterruptedException) { break }
                up = open(ip, 5555, 600) || lan.isNotEmpty() && lan != ip && Wol.sameNetwork(lan) && open(lan, 5555, 400)
                i++
            }
            val ok = up
            val packets = sent > 0
            val home = Wol.sameNetwork(lan)
            ui.post {
                waking = false
                if (isFinishing || host != null) return@post
                if (ok) {
                    wakeNext = true
                    connect(ip)
                    return@post
                }
                val why = if (mac.isEmpty()) t("App chưa biết địa chỉ MAC của TV này (nó tự lưu sau lần kết nối đầu tiên bằng bản app này), nên chưa gửi được gói đánh thức.",
                    "The app does not know this TV’s MAC address yet (it is saved after the first connection with this version), so no wake packet was sent.")
                else if (!packets || !home) t("TV đã tắt cả mạng nên không nghe qua Tailscale. Gói đánh thức chỉ tới được TV từ đúng Wi-Fi nhà TV, mà điện thoại đang ở mạng khác. Cách mở: nối điện thoại vào Wi-Fi đó rồi bấm lại, hoặc dùng nút Đánh thức trên web ở máy tính (nó nhờ một máy ở nhà gửi giúp).",
                    "The TV’s network is off too, so it cannot hear anything over Tailscale. A wake packet only reaches the TV from its own Wi-Fi, and the phone is on another network. Join that Wi-Fi and try again, or use Wake in the web UI on a computer (it relays through a machine at home).")
                else t("Đã gửi gói đánh thức trong Wi-Fi nhưng TV không lên. Kiểm tra trên TV: Cài đặt → Mạng và Internet → “Khởi động từ xa” phải bật.",
                    "Wake packets were sent on the Wi-Fi but the TV did not come up. On the TV check: Settings → Network & Internet → “Remote start” must be on.")
                message(t("Chưa đánh thức được TV.\n\n", "Could not wake the TV.\n\n") + why, true)
            }
        }.start()
    }

    // ---------------------------------------------------------------- saved TVs
    private fun name(ip: String): String = pref("name:$ip")

    private fun savedIps(): MutableList<String> = pref("ips").split(",").filter { it.isNotEmpty() }.toMutableList()

    private fun save(ip: String, model: String) {
        val ips = savedIps()
        ips.remove(ip)
        ips.add(0, ip)
        val e = prefs.edit().putString("ips", TextUtils.join(",", ips))
        if (model.isNotEmpty()) e.putString("name:$ip", model)
        e.apply()
    }

    // Each saved TV says whether it answers right now, so nobody has to tap it to find out; the wake button appears only when it does not.
    private val savedRows = HashMap<String, Pair<Button, View>>()
    private val reach = HashMap<String, Boolean>()                    // absent: not checked yet
    private val awayFrom = HashMap<String, Boolean>()                 // a TV that is off can only be woken from its own Wi-Fi
    private val probe = object : Runnable {
        override fun run() {
            ui.removeCallbacks(this)
            if (host == null && !waking) for (ip in savedIps()) Thread {
                // the saved address first, then the TV's other one: its Tailscale IP when away, its home IP when at home
                val via = listOf(ip, pref("ts:$ip"), pref("lan:$ip")).filter { it.isNotEmpty() }.distinct()
                    .firstOrNull { open(it, 5555, 2500) || prefs.getBoolean("atv:$ip", false) && open(it, 6466, 1500) }
                val up = via != null
                val lan = pref("lan:$ip")
                val away = lan.isNotEmpty() && !Wol.sameNetwork(lan)   // the phone is not on the TV's own Wi-Fi
                ui.post { if (via != null) route[ip] = via else route.remove(ip); reach[ip] = up; awayFrom[ip] = away; showReach(ip, up) }
            }.start()
            ui.postDelayed(this, 8000)
        }
    }

    private fun showReach(ip: String, up: Boolean?) {
        val (b, wk) = savedRows[ip] ?: return
        val status = when (up) {
            null -> t("đang kiểm tra...", "checking...")
            true -> route[ip].let { r -> if (r == null || r == ip) t("● kết nối được", "● reachable")
                else if (r.startsWith("100.")) t("● kết nối được qua Tailscale", "● reachable through Tailscale") else t("● kết nối được trong nhà", "● reachable at home") }
            false -> if (awayFrom[ip] == true) t("○ không thấy · bạn đang ngoài Wi-Fi của TV nên không đánh thức từ đây được", "○ no answer · you are not on the TV’s Wi-Fi, so it cannot be woken from here")
                else t("○ không thấy · TV đang tắt? bấm nút nguồn để đánh thức", "○ no answer · is the TV off? tap power to wake it")
        }
        val text = android.text.SpannableString((if (name(ip).isEmpty()) ip else name(ip) + "\n" + ip) + "\n" + status)
        val from = text.length - status.length
        text.setSpan(android.text.style.ForegroundColorSpan(if (up == true) 0xff4ade80.toInt() else MUTED), from, text.length, 0)
        text.setSpan(android.text.style.RelativeSizeSpan(0.82f), from, text.length, 0)
        b.text = text
        wk.visibility = if (up == false) View.VISIBLE else View.GONE
    }

    override fun onResume() { super.onResume(); probe.run() }

    override fun onPause() { ui.removeCallbacks(probe); super.onPause() }

    private fun refreshSaved() {
        savedList.removeAllViews()
        savedRows.clear()
        for (ip in savedIps()) {
            val b = row("") { connectSaved(ip) }
            val wk = icon("@power", t("Đánh thức", "Wake"), BTN)        // only offered while the TV does not answer
            wk.setOnClickListener { wake(ip) }
            wk.visibility = View.GONE
            savedRows[ip] = Pair(b, wk)
            showReach(ip, reach[ip])
            val line = LinearLayout(this)
            line.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
            val wp = LinearLayout.LayoutParams(dp(60), -1)
            wp.leftMargin = dp(6)
            line.addView(wk, wp)
            line.layoutParams = gap(6)
            val ask = {
                AlertDialog.Builder(this).setMessage(t("Xóa TV này khỏi danh sách?", "Remove this TV from the list?") + "\n" + title(ip) + (if (name(ip).isEmpty()) "" else " · $ip") + "\n\n" +
                        t("Không đổi gì trên TV. Muốn dùng lại thì quét hoặc nhập IP như lần đầu.", "Nothing changes on the TV. To use it again, scan or type its IP as the first time."))
                    .setPositiveButton(t("Xóa", "Remove")) { _, _ ->
                        val ips = savedIps()
                        ips.remove(ip)
                        prefs.edit().putString("ips", TextUtils.join(",", ips)).remove("name:$ip").remove("mac:$ip").remove("lan:$ip").remove("ts:$ip").remove("tvs:$ip").remove("atv:$ip").apply()
                        refreshSaved()
                    }.setNegativeButton(android.R.string.cancel, null).show()
            }
            val del = icon("@close", t("Xóa TV này khỏi danh sách", "Remove this TV from the list"), BTN)
            del.setOnClickListener { ask() }
            val dp2 = LinearLayout.LayoutParams(dp(48), -1)
            dp2.leftMargin = dp(6)
            line.addView(del, dp2)
            b.setOnLongClickListener { ask(); true }
            savedList.addView(line)
        }
        if (savedList.childCount == 0) savedList.addView(note(t("Chưa có TV nào. Quét Wi-Fi hoặc nhập IP bên dưới.", "No TVs yet. Scan the Wi-Fi or type an IP below.")))
    }

    // ---------------------------------------------------------------- finding TVs on the Wi-Fi
    private fun scan() {
        scanBtn.isEnabled = false
        scanBtn.text = t("Đang quét...", "Scanning...")
        foundList.removeAllViews()
        Thread {
            val nets = ArrayList<String>()
            val found = Collections.synchronizedList(ArrayList<String>())
            val names = java.util.concurrent.ConcurrentHashMap<String, String>()
            val adbOn = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
            try {
                val add = { a: java.net.InetAddress? ->
                    val ip = a?.hostAddress
                    if (ip != null && a is Inet4Address && a.isSiteLocalAddress) ip.substring(0, ip.lastIndexOf('.') + 1).let { if (it !in nets) nets.add(it) }
                }
                try {
                    for (ni in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                        if (!ni.isUp || ni.isLoopback) continue
                        for (a in Collections.list(ni.inetAddresses)) add(a)
                    }
                } catch (ignored: Exception) {
                }
                try {                                                 // a second source: with a VPN (Tailscale) on, the list above can miss the Wi-Fi
                    val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                    @Suppress("DEPRECATION")
                    for (n in cm.allNetworks) cm.getLinkProperties(n)?.linkAddresses?.forEach { add(it.address) }
                } catch (ignored: Exception) {
                }
                val seen = { ip: String ->
                    if (!found.contains(ip)) {
                        found.add(ip)
                        adbOn[ip] = open(ip, 5555, 900)
                        (name(ip).ifEmpty { castName(ip) }).let { if (it.isNotEmpty()) names[ip] = it }
                    }
                }
                val stopMdns = announced { ip -> Thread { seen(ip) }.start() }   // TVs also announce themselves; this finds the slow ones
                val pool = Executors.newFixedThreadPool(96)
                for (net in nets) for (i in 1 until 255) {
                    val ip = net + i
                    pool.execute { if (open(ip, 6466, 900) || open(ip, 5555, 900)) seen(ip) }
                }
                pool.shutdown()
                pool.awaitTermination(25, TimeUnit.SECONDS)
                stopMdns()
            } catch (ignored: Exception) {
            }
            ui.post {
                scanBtn.isEnabled = true
                scanBtn.text = "🔍  " + t("Quét Wi-Fi", "Scan the Wi-Fi")
                val ips = synchronized(found) { found.sorted() }
                for (ip in ips) foundList.addView(row((names[ip]?.let { it + "\n" } ?: "") + ip + " · " +
                    (if (adbOn[ip] == true) t("ADB đã bật", "ADB on") else t("ADB chưa bật: chỉ bấm phím, app sẽ giúp bật", "ADB off: keys only, the app helps turn it on"))) { connect(ip) })
                if (ips.isEmpty()) foundList.addView(note(
                    if (nets.isEmpty()) t("Điện thoại chưa nối Wi-Fi. Khác mạng thì nhập IP Tailscale của TV bên dưới.", "The phone is not on Wi-Fi. On another network, type the TV’s Tailscale IP below.")
                    else t("Đã quét ", "Scanned ") + nets.joinToString(", ") { it + "x" } + t(": không thấy TV nào. TV có đang bật và cùng Wi-Fi này không?\n\nTV ở mạng khác (qua Tailscale) thì không quét được: nhập IP Tailscale của TV (100.x.x.x) bên dưới.",
                        ": no TV found. Is it on, and on this Wi-Fi?\n\nA TV on another network (through Tailscale) cannot be scanned for: type its Tailscale IP (100.x.x.x) below.")))
            }
        }.start()
    }

    /** Listens for Android TVs announcing their remote service on the Wi-Fi (mDNS); [onTv] gets each one's address. Returns how to stop. */
    private fun announced(onTv: (String) -> Unit): () -> Unit {
        try {
            val nsd = getSystemService(NSD_SERVICE) as android.net.nsd.NsdManager
            val queue = java.util.concurrent.LinkedBlockingQueue<android.net.nsd.NsdServiceInfo>()
            var busy = false
            lateinit var next: () -> Unit
            next = {                                                  // old Androids resolve one at a time
                val s = queue.poll()
                if (s == null) busy = false
                else {
                    busy = true
                    try {
                        @Suppress("DEPRECATION")
                        nsd.resolveService(s, object : android.net.nsd.NsdManager.ResolveListener {
                            override fun onResolveFailed(i: android.net.nsd.NsdServiceInfo, code: Int) { next() }
                            override fun onServiceResolved(i: android.net.nsd.NsdServiceInfo) {
                                @Suppress("DEPRECATION")
                                (i.host as? Inet4Address)?.hostAddress?.let(onTv)
                                next()
                            }
                        })
                    } catch (e: Exception) { busy = false }
                }
            }
            val listener = object : android.net.nsd.NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(type: String, code: Int) {}
                override fun onStopDiscoveryFailed(type: String, code: Int) {}
                override fun onDiscoveryStarted(type: String) {}
                override fun onDiscoveryStopped(type: String) {}
                override fun onServiceLost(s: android.net.nsd.NsdServiceInfo) {}
                override fun onServiceFound(s: android.net.nsd.NsdServiceInfo) { synchronized(queue) { queue.add(s); if (!busy) next() } }
            }
            nsd.discoverServices("_androidtvremote2._tcp.", android.net.nsd.NsdManager.PROTOCOL_DNS_SD, listener)
            return { try { nsd.stopServiceDiscovery(listener) } catch (ignored: Exception) { } }
        } catch (e: Exception) {
            return {}
        }
    }

    /** The name an Android TV gives itself, asked from its Chromecast service (every Android TV has one). "" when it does not answer. */
    private fun castName(ip: String): String = try {
        java.net.Socket().use { so ->
            so.connect(java.net.InetSocketAddress(ip, 8008), 800)
            so.soTimeout = 1500
            so.getOutputStream().write(("GET /setup/eureka_info HTTP/1.1\r\nHost: $ip:8008\r\nConnection: close\r\n\r\n").toByteArray())
            val got = java.io.ByteArrayOutputStream()
            val buf = ByteArray(2048)
            var found = ""
            try {
                while (found.isEmpty() && got.size() < 20000) {       // the TV may keep the connection open: stop at the name
                    val n = so.getInputStream().read(buf)
                    if (n < 0) break
                    got.write(buf, 0, n)
                    found = Regex("\"name\"\\s*:\\s*\"([^\"]{1,60})\"").find(got.toString("UTF-8"))?.groupValues?.get(1) ?: ""
                }
            } catch (timeout: Exception) {
            }
            found
        }
    } catch (e: Exception) { "" }

    // ---------------------------------------------------------------- picker screen
    private fun buildPicker() {
        picker = col()
        picker.setPadding(dp(16), dp(20), dp(16), dp(24))
        val title = label("TV Remote", 24, TEXT)
        title.typeface = Typeface.DEFAULT_BOLD
        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        val names = col()
        names.addView(title)
        names.addView(label(t("Bản ", "Version ") + versionLine(), 12, MUTED))
        top.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
        val set = icon("@settings", t("Cài đặt", "Settings"), BTN)
        set.setOnClickListener {
            hideKeyboard()
            settings.visibility = View.VISIBLE
        }
        top.addView(set, LinearLayout.LayoutParams(dp(52), dp(44)))
        picker.addView(top)
        pickerMsg = label("", 14, ERR_TEXT)
        pickerMsg.background = box(ERR_BG, 12)
        pickerMsg.setPadding(dp(12), dp(10), dp(12), dp(10))
        pickerMsg.visibility = View.GONE
        picker.addView(pickerMsg, gap(12))
        updateBtn = button("", GREEN)
        updateBtn.setOnClickListener { askUpdate() }
        updateBtn.visibility = View.GONE
        val up = LinearLayout.LayoutParams(-1, dp(48))
        up.topMargin = dp(12)
        picker.addView(updateBtn, up)

        picker.addView(head(t("TV ĐÃ DÙNG", "YOUR TVS")), gap(18))
        savedList = col()
        picker.addView(savedList)

        picker.addView(head(t("TÌM TRONG WI-FI", "FIND ON THE WI-FI")), gap(18))
        scanBtn = button("🔍  " + t("Quét Wi-Fi", "Scan the Wi-Fi"), ACCENT)
        scanBtn.setOnClickListener { scan() }
        val sp = LinearLayout.LayoutParams(-1, dp(48))
        sp.topMargin = dp(6)
        picker.addView(scanBtn, sp)
        foundList = col()
        picker.addView(foundList)

        picker.addView(head(t("NHẬP IP CỦA TV", "TYPE THE TV’S IP")), gap(18))
        val line = LinearLayout(this)
        ipBox = input(t("vd 192.168.1.88 hoặc 100.x.x.x", "e.g. 192.168.1.88 or 100.x.x.x"))
        ipBox.inputType = InputType.TYPE_CLASS_PHONE
        ipBox.imeOptions = EditorInfo.IME_ACTION_GO
        ipBox.setOnEditorActionListener { _, _, _ ->
            connect(ipBox.text.toString())
            true
        }
        val go = icon("@right", t("Kết nối", "Connect"), ACCENT)
        go.setOnClickListener { connect(ipBox.text.toString()) }
        line.addView(ipBox, LinearLayout.LayoutParams(0, dp(48), 1f))
        val lp = LinearLayout.LayoutParams(-2, dp(48))
        lp.leftMargin = dp(8)
        lp.width = dp(60)
        line.addView(go, lp)
        picker.addView(line, gap(6))
        val help = button(t("Chưa biết IP? Xem cách kết nối", "No IP yet? How to connect"), 0)
        help.setTextColor(ACCENT)
        help.setOnClickListener {
            AlertDialog.Builder(this).setTitle(t("Cách kết nối", "How to connect")).setMessage(t(
                "1. BẬT ADB TRÊN TV (một lần)\nCài đặt → Tùy chọn thiết bị → Giới thiệu → bấm OK 7 lần vào “Bản dựng”. Rồi Tùy chọn nhà phát triển → bật “Gỡ lỗi ADB”.\n\n" +
                    "2A. CÙNG WI-FI VỚI TV\nBấm “Quét Wi-Fi” rồi chọn TV. Hoặc xem IP trên TV: Cài đặt → Mạng và Internet → tên Wi-Fi.\n\n" +
                    "2B. KHÁC MẠNG\nBật Tailscale trên cả điện thoại và TV. TV đã kết nối ở nhà một lần thì app nhớ IP Tailscale của nó, ra ngoài chỉ cần bấm TV trong danh sách. Chưa thì nhập IP 100.x của TV (xem trong app Tailscale).\n\n" +
                    "TV chưa có Tailscale? Lúc đang ở nhà, kết nối TV rồi bấm nút ô lưới → Thiết lập → “Cài và đăng nhập Tailscale”: app tự cài lên TV và mở trang đăng nhập trên điện thoại, bạn chỉ bấm Connect.\n\n" +
                    "3. LẦN ĐẦU KẾT NỐI\nTV hỏi cho phép: tích “Luôn cho phép” rồi OK.\n\n" +
                    "MẸO\nNút nguồn cạnh tên TV: đánh thức TV đang tắt. Nút ✕ cạnh tên TV: xóa khỏi danh sách.",
                "1. TURN ON ADB ON THE TV (once)\nSettings → Device Preferences → About → press OK 7 times on “Build”. Then Developer options → turn on “ADB debugging”.\n\n" +
                    "2A. SAME WI-FI AS THE TV\nTap “Scan the Wi-Fi” and pick the TV. Or read the IP on the TV: Settings → Network & Internet → the Wi-Fi name.\n\n" +
                    "2B. ANOTHER NETWORK\nTurn on Tailscale on both the phone and the TV. Once the TV has been connected at home, the app remembers its Tailscale IP: away, just tap the TV in the list. Otherwise type the TV’s 100.x IP (see the Tailscale app).\n\n" +
                    "No Tailscale on the TV yet? While at home, connect to the TV, then tap the grid button → Setup → “Install and sign in to Tailscale”: the app installs it on the TV and opens the sign-in page on the phone; you only press Connect.\n\n" +
                    "3. THE FIRST CONNECTION\nThe TV asks for permission: tick “Always allow”, then OK.\n\n" +
                    "TIPS\nThe power button next to a TV wakes it when it is off. The ✕ next to a TV removes it from the list."))
                .setPositiveButton(t("Đã hiểu", "Got it"), null).show()
        }
        picker.addView(help, LinearLayout.LayoutParams(-1, dp(44)))
    }

    // ---------------------------------------------------------------- settings screen
    private fun buildSettings(): View {
        val box = col()
        box.setPadding(dp(16), dp(20), dp(16), dp(24))
        val title = label(t("Cài đặt", "Settings"), 24, TEXT)
        title.typeface = Typeface.DEFAULT_BOLD
        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val done = icon("@check", t("Xong", "Done"), ACCENT)
        done.setOnClickListener { settings.visibility = View.GONE }
        top.addView(done, LinearLayout.LayoutParams(dp(52), dp(44)))
        box.addView(top)

        box.addView(head(t("NGÔN NGỮ", "LANGUAGE")), gap(18))
        val now = pref("lang")
        val langs = listOf("" to "📱  " + t("Theo điện thoại", "Same as the phone"), "vi" to "🇻🇳  Tiếng Việt", "en" to "🇬🇧  English")
        for ((code, words) in langs) {
            val b = row(words + (if (code == now) "   ✓" else "")) {
                if (code == now) return@row
                prefs.edit().putString("lang", code).apply()
                recreate()                                            // every label is built in onCreate
            }
            if (code == now) b.setTextColor(ACCENT)
            box.addView(b)
        }

        box.addView(head(t("CẬP NHẬT", "UPDATES")), gap(18))
        lateinit var auto: Button
        val autoLabel = { auto.text = (if (prefs.getBoolean("autoUpdate", true)) "✅  " else "⬜  ") + t("Tự kiểm tra bản mới", "Check for updates automatically") }
        auto = row("") {
            prefs.edit().putBoolean("autoUpdate", !prefs.getBoolean("autoUpdate", true)).apply()
            autoLabel()
        }
        autoLabel()
        box.addView(auto)
        box.addView(row("🔄  " + t("Kiểm tra ngay", "Check now")) { checkUpdate(true) })
        updateNote = note("")
        box.addView(updateNote)

        box.addView(head(t("GIỚI THIỆU", "ABOUT")), gap(18))
        box.addView(row("ℹ️  " + t("Giới thiệu", "About")) { about.visibility = View.VISIBLE })

        val sv = ScrollView(this)
        sv.setBackgroundColor(BG)
        sv.isClickable = true                                         // do not let touches fall through to the screen below
        sv.addView(box)
        sv.visibility = View.GONE
        return sv
    }

    // ---------------------------------------------------------------- managing the TV: apps, files, installs, health, terminal
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == PICK_FILE) managing.picked(result, data)
        if (request == PICK_CAST && result == RESULT_OK) data?.data?.let { castFile(it) }
        if (request == SPEAK && result == RESULT_OK && data != null) {
            val heard = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (heard != null && heard.isNotEmpty()) ask(heard[0])
        }
    }

    // ---------------------------------------------------------------- speaking to the TV
    /** The phone turns speech into text (its own Google speech service) and the TV's search / Assistant gets the sentence. */
    private fun speak() {
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

    private fun ask(sentence: String) {
        if (needsAdb()) return
        Toast.makeText(this, t("Đã gửi cho TV: ", "Sent to the TV: ") + sentence, Toast.LENGTH_SHORT).show()
        val tv = tv
        if (tv != null && tv.ready()) tv.search(sentence) else pendingAsk = sentence
    }

    /**
     * The same page the TV app serves, run inside this app: a server on 127.0.0.1 that only this app's web view can use
     * (it must present a random cookie), talking to the TV over its own ADB connection.
     */
    private inner class Managing(root: FrameLayout) : HttpServer.Handler {
        private val view: LinearLayout = col()
        private val web: WebView
        private val token = UUID.randomUUID().toString().replace("-", "")
        private var http: HttpServer? = null
        private var port = 0
        private var link: AdbLink? = null
        private var manage: Manage? = null
        private var labels: RemoteLabels? = null
        private var forHost: String? = null
        private var picking: ValueCallback<Array<Uri>>? = null
        private val webPage by lazy { dev.tvremote.core.WebPage(assets, "phone", version() + "-" + (try { packageManager.getPackageInfo(packageName, 0).lastUpdateTime / 1000 } catch (e: Exception) { 0L })) }

        init {
            view.setBackgroundColor(BG)
            view.isClickable = true
            val title = label(t("Quản lý TV", "Manage the TV"), 20, TEXT)
            title.typeface = Typeface.DEFAULT_BOLD
            val top = LinearLayout(this@MainActivity)
            top.gravity = Gravity.CENTER_VERTICAL
            top.setPadding(dp(16), dp(10), dp(12), dp(6))
            top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            val done = icon("@close", t("Đóng", "Close"), BTN)
            done.setOnClickListener { view.visibility = View.GONE }
            top.addView(done, LinearLayout.LayoutParams(dp(48), dp(40)))
            view.addView(top)
            web = WebView(this@MainActivity)
            web.setBackgroundColor(BG)
            @Suppress("SetJavaScriptEnabled")
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            web.webViewClient = object : WebViewClient() {            // the page's own links stay inside; the rest (Tailscale sign-in) go to the browser
                override fun shouldOverrideUrlLoading(w: WebView?, req: android.webkit.WebResourceRequest?): Boolean {
                    val u = req?.url ?: return false
                    if (u.host == "127.0.0.1") return false
                    web(u.toString())
                    return true
                }
            }
            web.webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(w: WebView?, cb: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                    picking?.onReceiveValue(null)
                    picking = cb
                    try {
                        @Suppress("DEPRECATION")
                        startActivityForResult(params!!.createIntent(), PICK_FILE)
                    } catch (e: Exception) {
                        picking = null
                        return false
                    }
                    return true
                }
            }
            web.setDownloadListener { url, _, disposition, _, _ -> saveToPhone(url, disposition) }
            view.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
            view.visibility = View.GONE
            root.addView(view, FrameLayout.LayoutParams(-1, -1))
        }

        fun open(tvHost: String?) {
            if (tvHost == null) return
            try {
                if (http == null) {
                    val server = HttpServer(this)
                    http = server
                    port = server.start(0, true)
                }
                if (tvHost != forHost) {
                    link?.close()
                    val l = AdbLink(filesDir, tvHost)
                    link = l
                    val names = RemoteLabels(l, File(applicationInfo.sourceDir))   // app names and pictures, asked from the TV itself
                    labels = names
                    manage = Manage(l, names, cacheDir)
                    forHost = tvHost
                    val origin = "http://127.0.0.1:$port"
                    CookieManager.getInstance().setCookie(origin, "tvr=$token")
                    web.loadUrl(origin + "/?embed=1&lang=" + (if (VI) "vi" else "en"))
                }
                view.visibility = View.VISIBLE
            } catch (e: Exception) {
                message(t("Không mở được trang quản lý: ", "Could not open the manage page: ") + e.message, true)
            }
        }

        fun back(): Boolean {
            if (view.visibility != View.VISIBLE) return false
            view.visibility = View.GONE
            return true
        }

        /** Leaving this TV: drop its ADB connection; the next TV gets a fresh page. */
        fun close() {
            view.visibility = View.GONE
            link?.close()
            link = null
            labels = null
            manage = null
            forHost = null
        }

        fun destroy() {
            close()
            http?.stop()
            web.destroy()
        }

        fun picked(result: Int, data: Intent?) {
            val cb = picking ?: return
            cb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data))
            picking = null
        }

        override fun handle(r: HttpServer.Request): HttpServer.Response {
            if (token != r.cookie("tvr")) return HttpServer.Response.error(403, "this page belongs to the TV Remote app")
            webPage.serve(r.path)?.let { return it }
            if (r.path == "/api/state" || r.path == "/api/about")      // the page only needs to know it may show itself, and for which TV
                return HttpServer.Response.json(JSONObject().put("core", "phone").put("host", forHost ?: "").put("name", name(forHost ?: "")).put("connected", true).put("is_on", true).put("paired", true)
                    .put("adb", JSONObject().put("state", manage?.adbState() ?: "closed")).put("app_name", "TV Remote").put("version", version()).put("released", released()))
            if (r.path == "/api/app/icon") {
                val png = labels?.icon(r.query["pkg"].toString()) ?: return HttpServer.Response.error(404, "no icon")
                val icon = HttpServer.Response.of("image/png", png)
                icon.headers["Cache-Control"] = "max-age=86400"
                return icon
            }
            return manage?.handle(r) ?: HttpServer.Response.error(404, "not found")
        }

        /** "Download to this device": copy the file from our own server into the phone's Download folder. */
        private fun saveToPhone(url: String, disposition: String?) {
            var fileName = "file"
            try {
                val m = Pattern.compile("filename\\*=UTF-8''([^;]+)").matcher(disposition.toString())
                if (m.find()) fileName = URLDecoder.decode(m.group(1), "UTF-8")
            } catch (ignored: Exception) {
            }
            Toast.makeText(this@MainActivity, t("Đang tải ", "Downloading ") + fileName + "...", Toast.LENGTH_SHORT).show()
            Thread {
                var result: String
                try {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.setRequestProperty("Cookie", "tvr=$token")
                    val out: OutputStream
                    if (Build.VERSION.SDK_INT >= 29) {
                        val v = ContentValues()
                        v.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        out = contentResolver.openOutputStream(contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!)!!
                        result = t("Đã lưu vào thư mục Download: ", "Saved to Download: ") + fileName
                    } else {
                        val f = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                        out = FileOutputStream(f)
                        result = t("Đã lưu: ", "Saved: ") + f.absolutePath
                    }
                    out.use { o -> c.inputStream.use { it.copyTo(o, 65536) } }
                } catch (e: Exception) {
                    result = t("Không tải được: ", "Download failed: ") + e.message
                }
                val msg = result
                ui.post { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show() }
            }.start()
        }
    }

    // ---------------------------------------------------------------- about screen
    private fun web(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {                                      // no browser on this device
            message(url, false)
        }
    }

    private fun buildAbout(): View {
        val box = col()
        box.setPadding(dp(16), dp(20), dp(16), dp(24))
        val title = label(t("Giới thiệu", "About"), 24, TEXT)
        title.typeface = Typeface.DEFAULT_BOLD
        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val done = icon("@check", t("Xong", "Done"), ACCENT)
        done.setOnClickListener { about.visibility = View.GONE }
        top.addView(done, LinearLayout.LayoutParams(dp(52), dp(44)))
        box.addView(top)

        val logo = ImageView(this)
        logo.setImageResource(R.drawable.icon)
        val lp = LinearLayout.LayoutParams(dp(84), dp(84))
        lp.gravity = Gravity.CENTER_HORIZONTAL
        lp.topMargin = dp(22)
        box.addView(logo, lp)
        val name = label("TV Remote " + versionLine(), 20, TEXT)
        name.typeface = Typeface.DEFAULT_BOLD
        name.gravity = Gravity.CENTER
        box.addView(name, gap(10))
        // The app's own download link as a code: someone next to you scans it and has the app
        val appLink = "https://github.com/HoangTran0410/tv-remote/releases/latest/download/tv-remote.apk"
        dev.tvremote.core.Qr.bitmap(appLink, 6)?.let { bmp ->
            val qr = ImageView(this)
            qr.setImageBitmap(bmp)
            qr.contentDescription = t("Mã QR tải app", "QR code to download the app")
            val qp = LinearLayout.LayoutParams(dp(170), dp(170))
            qp.gravity = Gravity.CENTER_HORIZONTAL
            qp.topMargin = dp(14)
            box.addView(qr, qp)
            val hint = note(t("Quét để tải app này cho điện thoại khác", "Scan to get this app on another phone"))
            hint.gravity = Gravity.CENTER
            box.addView(hint, gap(6))
            val share = button(t("Chia sẻ đường dẫn tải app", "Share the app’s download link"), BTN)
            share.setOnClickListener { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, appLink), null)) }
            box.addView(share, gap(8))
        }
        val what = note(t("Remote cho Android TV / Google TV: hình trực tiếp, chạm, nút điều khiển, gõ chữ, mở app.",
            "A remote for Android TV / Google TV: live picture, touch, remote buttons, typing, opening apps."))
        what.gravity = Gravity.CENTER
        box.addView(what)

        box.addView(head(t("TÁC GIẢ", "AUTHOR")), gap(22))
        box.addView(row("👤  Hoang Tran\n@HoangTran0410") { web("https://github.com/HoangTran0410") })

        box.addView(head(t("MÃ NGUỒN", "SOURCE CODE")), gap(18))
        box.addView(row("🔗  github.com/HoangTran0410/tv-remote") { web("https://github.com/" + Release.REPO) })
        box.addView(row("📦  " + t("Các bản phát hành và nhật ký thay đổi", "Releases and change log")) { web("https://github.com/" + Release.REPO + "/releases") })
        box.addView(row("🐞  " + t("Báo lỗi, góp ý", "Report a problem")) { web("https://github.com/" + Release.REPO + "/issues") })

        box.addView(head(t("GHI CÔNG", "CREDITS")), gap(18))
        box.addView(row("scrcpy · Genymobile · Apache-2.0\n" + t("hình trực tiếp và điều khiển", "live picture and input")) { web("https://github.com/Genymobile/scrcpy") })
        box.addView(note(t("App nói chuyện thẳng với TV qua ADB, không qua máy chủ nào. Khóa ADB của app nằm trong điện thoại này; gỡ app là mất khóa và TV sẽ hỏi cho phép lại.",
            "The app talks straight to the TV over ADB, with no server in between. Its ADB key lives on this phone; uninstalling the app loses it and the TV will ask for permission again.")), gap(12))

        val sv = ScrollView(this)
        sv.setBackgroundColor(BG)
        sv.isClickable = true
        sv.addView(box)
        sv.visibility = View.GONE
        return sv
    }

    // ---------------------------------------------------------------- remote screen
    private fun buildRemote() {
        remote = col()
        status = label("", 13, MUTED)
        status.setPadding(dp(14), dp(8), dp(8), dp(8))
        videoBtn = icon("@eye_off", "", BTN)
        videoBtn.setOnClickListener { toggleVideo() }
        basicBar = button(t("Chỉ bấm phím · Bật ADB để dùng đủ chức năng  ›", "Keys only · Turn on ADB for everything  ›"), ACCENT)
        basicBar.textSize = 13f
        basicBar.setOnClickListener { adbHelp() }
        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        qualBtn = button("", BTN)                                     // picture quality: a menu of the levels, lightest first
        qualBtn.textSize = 13f
        qualBtn.typeface = Typeface.DEFAULT_BOLD
        qualBtn.setPadding(0, 0, 0, 0)
        qualBtn.minHeight = 0
        qualBtn.minimumHeight = 0
        qualBtn.minWidth = 0
        qualBtn.minimumWidth = 0
        qualBtn.contentDescription = t("Chất lượng hình", "Picture quality")
        qualBtn.setOnClickListener {
            val menu = android.widget.PopupMenu(this, qualBtn)
            for (level in Tv.LEVELS) menu.menu.add(0, level, 0, Tv.QUALITY[level][0].toString() + "p" + (if (level == prefs.getInt("quality", 1)) "  ✓" else ""))
            menu.setOnMenuItemClickListener { item ->
                if (item.itemId != prefs.getInt("quality", 1)) {
                    prefs.edit().putInt("quality", item.itemId).apply()
                    stopSession()                                     // the size is fixed when the session starts
                    layoutRemote()
                    startSession()
                }
                true
            }
            menu.show()
        }
        val qp = LinearLayout.LayoutParams(dp(60), dp(40))
        qp.rightMargin = dp(8)
        bar.addView(qualBtn, qp)
        fullBtn = icon("@expand", t("Toàn màn hình", "Full screen"), BTN)
        fullBtn.setOnClickListener { setFullscreen(true) }
        val fb = LinearLayout.LayoutParams(dp(52), dp(40))
        fb.rightMargin = dp(8)
        bar.addView(fullBtn, fb)
        val vb = LinearLayout.LayoutParams(dp(52), dp(40))
        vb.rightMargin = dp(10)
        bar.addView(videoBtn, vb)
        bar.setPadding(0, dp(8), 0, dp(10))                               // room between the buttons and the picture
        remote.addView(bar)

        frame = VideoFrame(this)
        frame.setBackgroundColor(Color.BLACK)
        surface = SurfaceView(this)
        surface.holder.addCallback(this)
        frame.addView(surface, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        surface.setOnTouchListener { v, e ->
            val tv = tv
            if (tv == null || !tv.ready()) return@setOnTouchListener true
            val action = when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> 0
                MotionEvent.ACTION_MOVE -> 2
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 1
                else -> -1
            }
            if (action >= 0) tv.touch(action, e.x / v.width, e.y / v.height)
            true
        }
        stage = FrameLayout(this)
        stage.setBackgroundColor(Color.BLACK)
        stage.addView(frame, FrameLayout.LayoutParams(-1, -2))
        exitBtn = icon("@close", t("Thoát toàn màn hình", "Leave full screen"), 0x66000000)   // the only thing on the picture, and only in full screen
        exitBtn.alpha = 0.5f
        exitBtn.setOnClickListener { setFullscreen(false) }
        val fp = FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP or Gravity.END)
        fp.setMargins(dp(8), dp(8), dp(8), dp(8))
        stage.addView(exitBtn, fp)
        remote.addView(stage, LinearLayout.LayoutParams(-1, -2))

        controls = col()
        controls.setPadding(dp(14), dp(12), dp(14), dp(20))
        val bb = LinearLayout.LayoutParams(-1, dp(44))
        bb.bottomMargin = dp(12)
        controls.addView(basicBar, bb)
        val first = LinearLayout(this)
        val power = icon("@power", t("Nguồn", "Power"), BTN)
        power.setOnClickListener {
            if (basic != null) { press(26); return@setOnClickListener }
            val tv = tv
            if (tv == null || !tv.ready()) return@setOnClickListener
            Thread {
                val awake = try { !tv.shell("dumpsys power | grep -m1 mWakefulness=").contains("Asleep") } catch (e: Exception) { true }
                ui.post {
                    if (isFinishing || this.tv !== tv) return@post
                    if (!awake) {
                        tv.press(224)
                        Toast.makeText(this, t("Đang bật TV...", "Waking the TV..."), Toast.LENGTH_SHORT).show()
                    } else AlertDialog.Builder(this).setMessage(t("Đưa TV về chế độ chờ?", "Put the TV in standby?"))
                        .setPositiveButton(t("Chế độ chờ", "Standby")) { _, _ -> if (tv.ready()) tv.press(223) }
                        .setNegativeButton(t("Hủy", "Cancel"), null).show()
                }
            }.start()
        }
        first.addView(power, cell(52, false))
        val apps = icon("@apps", t("Ứng dụng", "Apps"), BTN)
        apps.setOnClickListener { if (!needsAdb()) managing.open(host) }
        val mic = icon("@mic", t("Nói với TV", "Speak to the TV"), BTN)
        mic.setOnClickListener { if (!needsAdb()) speak() }
        mic.setOnLongClickListener {                                  // the TV's own Assistant screen
            tv?.let { if (it.ready()) it.press(219) }
            true
        }
        first.addView(mic, cell(52, true))
        first.addView(apps, cell(52, true))
        // Everything used less often sits behind this button: one tap shows the extra keys, another hides them.
        val moreBtn = icon("@more", t("Thêm phím", "More keys"), BTN)
        first.addView(moreBtn, cell(52, true))
        controls.addView(first)
        val more = col()
        more.addView(captioned(keys(58, "@input|" + t("Ngõ vào", "Input"), 178, "@settings|" + t("Cài đặt", "Settings"), 176, "@info|" + t("Thông tin", "Info"), 165, "@guide|" + t("Lịch phát", "Guide"), 172)), gap(8))
        more.addView(captioned(keys(58, "@tv|" + t("TV", "Live TV"), 170, "@subtitle|" + t("Phụ đề", "Subtitles"), 175, "@stop|" + t("Dừng", "Stop"), 86, "@camera|" + t("Chụp màn hình", "Screenshot"), SHOT)), gap(8))
        more.addView(captioned(keys(58, "@send|" + t("Mở trên TV", "Open on the TV"), OPEN, "", 0, "", 0, "", 0)), gap(8))
        val showMore = { on: Boolean ->
            more.visibility = if (on) View.VISIBLE else View.GONE
            moreBtn.background = box(if (on) ACCENT else BTN, 16)
        }
        showMore(prefs.getBoolean("more", false))
        moreBtn.setOnClickListener {
            val on = more.visibility != View.VISIBLE
            prefs.edit().putBoolean("more", on).apply()
            showMore(on)
        }
        controls.addView(more)
        // The arrows, or a touchpad in their place: swipes become arrow keys, a tap is OK. Handy where the TV shows a pointer
        // that follows the arrows (a browser, Downloader), and for long lists.
        val arrows = dpad()
        val tpad = touchpad()
        val swap = button("", BTN)
        swap.textSize = 13f
        swap.setPadding(dp(12), 0, dp(12), 0)
        swap.minHeight = 0
        swap.minimumHeight = 0
        val usePad = { on: Boolean ->
            arrows.visibility = if (on) View.GONE else View.VISIBLE
            tpad.visibility = if (on) View.VISIBLE else View.GONE
            swap.text = if (on) t("Dùng phím mũi tên", "Use the arrows") else t("Dùng touchpad", "Use the touchpad")
        }
        usePad(prefs.getBoolean("pad", false))
        swap.setOnClickListener {
            val on = tpad.visibility != View.VISIBLE
            prefs.edit().putBoolean("pad", on).apply()
            usePad(on)
        }
        controls.addView(arrows, gap(16))
        controls.addView(tpad, LinearLayout.LayoutParams(-1, dp(250)).also { it.topMargin = dp(16) })
        val sp2 = LinearLayout.LayoutParams(-2, dp(36))
        sp2.gravity = Gravity.CENTER_HORIZONTAL
        sp2.topMargin = dp(8)
        controls.addView(swap, sp2)
        controls.addView(captioned(keys(58, "@back|" + t("Quay lại", "Back"), 4, "@home|Home", 3, "@menu|Menu", 82)), gap(16))
        // the same rows, in the same order, as the two web remotes
        // Volume on the left, channels on the right, each one tall bar for the thumb; the media keys between them.
        // The same block, in the same order, as the two web remotes.
        val mid = col()
        mid.addView(captioned(keys(58, "@mute|" + t("Tắt tiếng", "Mute"), 164, "@play_pause|" + t("Phát / Dừng", "Play / Pause"), 85)))
        mid.addView(captioned(keys(58, "@prev|" + t("Bài trước", "Previous"), 88, "@next|" + t("Bài sau", "Next"), 87)), gap(8))
        mid.addView(captioned(keys(58, "@rewind|" + t("Tua lại", "Rewind"), 89, "@ffwd|" + t("Tua tới", "Forward"), 90)), gap(8))
        val cluster = LinearLayout(this)
        cluster.addView(pill("Vol", "@plus", 24, t("Tăng âm lượng", "Volume up"), "@minus", 25, t("Giảm âm lượng", "Volume down")), LinearLayout.LayoutParams(0, -1, 1f))
        val mp = LinearLayout.LayoutParams(0, -2, 2.4f)
        mp.leftMargin = dp(10)
        mp.rightMargin = dp(10)
        cluster.addView(mid, mp)
        cluster.addView(pill(t("Kênh", "CH"), "@up", 166, t("Kênh +", "Channel up"), "@down", 167, t("Kênh −", "Channel down")), LinearLayout.LayoutParams(0, -1, 1f))
        controls.addView(cluster, gap(16))

        val line = LinearLayout(this)
        liveBtn = button("⚡", BTN)
        liveBtn.setPadding(0, 0, 0, 0)
        liveBtn.setOnClickListener { setLive(!live) }
        val lb = LinearLayout.LayoutParams(dp(48), dp(48))
        lb.rightMargin = dp(6)
        line.addView(liveBtn, lb)
        textBox = input("")
        textBox.imeOptions = EditorInfo.IME_ACTION_SEND
        textBox.setSingleLine(true)
        textBox.setOnEditorActionListener { _, _, _ ->
            sendText()
            true
        }
        textBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = typedLive(s.toString())
        })
        line.addView(textBox, LinearLayout.LayoutParams(0, dp(48), 1f))
        val send = icon("@send", t("Gửi", "Send"), ACCENT)
        send.setOnClickListener { sendText() }
        val lp = LinearLayout.LayoutParams(-2, dp(48))
        lp.leftMargin = dp(6)
        lp.width = dp(60)
        line.addView(send, lp)
        controls.addView(line, gap(12))
        val edit = keys(48, "@backspace|" + t("Xóa", "Delete"), 67, "@enter|Enter", 66)
        val digits = button("123", BTN)                               // shows / hides the number keys (channels, PIN codes)
        digits.contentDescription = t("Bàn phím số", "Number keys")
        val dl = LinearLayout.LayoutParams(dp(64), dp(48))
        dl.rightMargin = dp(8)
        edit.addView(digits, 0, dl)
        controls.addView(edit, gap(8))
        val pad = col()
        pad.addView(keys(48, "1", 8, "2", 9, "3", 10), gap(8))
        pad.addView(keys(48, "4", 11, "5", 12, "6", 13), gap(8))
        pad.addView(keys(48, "7", 14, "8", 15, "9", 16), gap(8))
        pad.addView(keys(48, "", 0, "0", 7, "", 0), gap(8))
        pad.visibility = if (prefs.getBoolean("numpad", false)) View.VISIBLE else View.GONE
        digits.setOnClickListener {
            val show = pad.visibility != View.VISIBLE
            pad.visibility = if (show) View.VISIBLE else View.GONE
            prefs.edit().putBoolean("numpad", show).apply()
        }
        controls.addView(pad)
        setLive(prefs.getBoolean("live", false))

        val bye = button(t("Ngắt kết nối", "Disconnect"), 0)
        bye.setTextColor(RED)
        bye.setOnClickListener { disconnect(null) }
        val bp = LinearLayout.LayoutParams(-1, dp(48))
        bp.topMargin = dp(10)
        controls.addView(bye, bp)

        val sv = ScrollView(this)
        sv.addView(controls)
        remote.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        layoutRemote()
    }

    private fun sendText() {
        val s = textBox.text.toString()
        basic?.let {                                                  // keys only: the text goes into the field that has the focus on the TV
            if (live) { it.key(66, Atv.SHORT); clearBox() }           // everything is on the TV already: this is Enter
            else if (s.isEmpty()) it.key(66, Atv.SHORT)
            else { if (it.hasField && !Atv.typable(s)) it.text(s) else it.keys(s); textBox.setText("") }
            return
        }
        val tv = tv
        if (tv == null || !tv.ready()) return
        if (live) {                                                   // everything is on the TV already: this is Enter
            tv.press(66)
            clearBox()
        } else if (s.isNotEmpty()) {
            tv.text(s)
            textBox.setText("")
        }
    }

    // Two ways to type. Send: write here, then send it in one go. Live (⚡): the TV follows every letter and every
    // deletion as it happens, so one wrong letter is fixed by fixing it here.
    private lateinit var liveBtn: Button
    private var live = false
    private var onTv = ""                                             // what the TV has received from the box so far
    private var quiet = false                                         // the box is being changed by the app, not typed in

    private fun setLive(on: Boolean) {
        live = on
        prefs.edit().putBoolean("live", on).apply()
        liveBtn.background = box(if (on) ACCENT else BTN, 14)
        liveBtn.contentDescription = if (on) t("Đang gõ trực tiếp. Bấm để chuyển sang gõ xong rồi gửi", "Live typing is on. Tap to type first, then send")
            else t("Đang gõ xong rồi gửi. Bấm để chuyển sang gõ trực tiếp", "Type-then-send is on. Tap for live typing")
        textBox.hint = if (on) t("Gõ trực tiếp: TV nhận từng chữ", "Live: the TV gets every letter") else t("Gõ chữ rồi bấm gửi", "Type, then send")
        clearBox()
    }

    private fun clearBox() {
        quiet = true
        textBox.setText("")
        quiet = false
        onTv = ""
    }

    /** Live typing: make the TV's text follow the box by deleting what changed at the end and typing the new end. */
    private fun typedLive(now: String) {
        if (!live || quiet) return
        basic?.let {                                                  // keys only: through the TV's keyboard service when the app uses it, as key presses otherwise
            if (it.hasField && !Atv.typable(now)) { if (now.isNotEmpty()) it.text(now) }
            else {
                var keep = 0
                while (keep < onTv.length && keep < now.length && onTv[keep] == now[keep]) keep++
                repeat(onTv.length - keep) { _ -> it.key(67, Atv.SHORT) }
                it.keys(now.substring(keep))
            }
            onTv = now
            return
        }
        val tv = tv
        if (tv == null || !tv.ready()) return
        var same = 0
        while (same < onTv.length && same < now.length && onTv[same] == now[same]) same++
        repeat(onTv.length - same) { tv.press(67) }
        if (now.length > same) tv.text(now.substring(same))
        onTv = now
    }

    /** A row of equal-width remote keys: label, Android key code, label, key code... "@icon|words" shows an icon. An empty label leaves a gap. */
    /** One tall bar with a key at each end and its name in the middle: volume, channels. */
    private fun pill(name: String, upIcon: String, up: Int, sayUp: String, downIcon: String, down: Int, sayDown: String): LinearLayout {
        val p = col()
        p.background = box(BTN, 26)
        val a = icon(upIcon, sayUp, 0)
        bindKey(a, up, box(HELD, 26))
        val b = icon(downIcon, sayDown, 0)
        bindKey(b, down, box(HELD, 26))
        val l = label(name, 13, MUTED)
        l.gravity = Gravity.CENTER
        l.typeface = Typeface.DEFAULT_BOLD
        p.addView(a, LinearLayout.LayoutParams(-1, 0, 1f))
        p.addView(l, LinearLayout.LayoutParams(-1, -2))
        p.addView(b, LinearLayout.LayoutParams(-1, 0, 1f))
        return p
    }

    /** Shows each icon button's name under its icon. */
    private fun captioned(row: LinearLayout): LinearLayout {
        for (i in 0 until row.childCount) (row.getChildAt(i) as? IconButton)?.let { it.caption = it.contentDescription?.toString() }
        return row
    }

    /** Saves what is on the TV's screen as a picture in this phone's Download folder. */
    private fun shot() {
        if (needsAdb()) return
        val tv = tv
        if (tv == null || !tv.ready()) return
        Toast.makeText(this, t("Đang chụp màn hình TV...", "Taking a screenshot of the TV..."), Toast.LENGTH_SHORT).show()
        Thread {
            var result: String
            try {
                val png = tv.screenshot() ?: throw java.io.IOException(t("TV không trả ảnh", "the TV sent no picture"))
                val fileName = "tv-" + System.currentTimeMillis() / 1000 + ".png"
                val out: OutputStream
                if (Build.VERSION.SDK_INT >= 29) {
                    val v = ContentValues()
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    v.put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    out = contentResolver.openOutputStream(contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!)!!
                    result = t("Đã lưu vào thư mục Download: ", "Saved to Download: ") + fileName
                } else {
                    val f = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                    out = FileOutputStream(f)
                    result = t("Đã lưu: ", "Saved: ") + f.absolutePath
                }
                out.use { it.write(png) }
            } catch (e: Exception) {
                result = t("Không chụp được: ", "Screenshot failed: ") + e.message
            }
            val msg = result
            ui.post { if (!isFinishing) Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }.start()
    }

    /** Menu, Input, TV settings, Guide, Live TV: keys that should open something. When the TV's screen ignores one, say so, so it does not look like a dead button. */
    private fun opens(code: Int) {
        if (basic != null) { if (code == 176) findSettings() else press(code); return }
        val tv = tv
        if (tv == null || !tv.ready()) return
        Thread {
            if (!tv.pressSeen(code)) ui.post {
                if (!isFinishing && this.tv === tv) Toast.makeText(this, t("TV đã nhận nút này, nhưng màn hình hiện tại không phản hồi", "The TV got the key, but the current screen did not react"), Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun keys(height: Int, vararg labelAndCode: Any): LinearLayout {
        val row = LinearLayout(this)
        for (i in labelAndCode.indices step 2) {
            var label = labelAndCode[i] as String
            var say = label
            val bar = label.indexOf('|')
            if (bar > 0) {
                say = label.substring(bar + 1)
                label = label.substring(0, bar)
            }
            val code = labelAndCode[i + 1] as Int
            val v: View
            if (label.isEmpty()) v = View(this)
            else {
                val b = if (bar > 0) icon(label, say, BTN) else button(label, BTN)
                if (bar < 0) b.textSize = 18f
                if (code == SHOT) b.setOnClickListener { shot() }
                else if (code == OPEN) b.setOnClickListener { quickOpen() }
                else if (code in Tv.OPENERS) {
                    val held = box(HELD, 16)
                    b.setOnClickListener { flash(b, held); opens(code) }
                } else bindKey(b, code, box(HELD, 16))
                v = b
            }
            row.addView(v, cell(height, i > 0))
        }
        return row
    }

    /**
     * A tap counts when the finger lifts on the button: touching it sends nothing, and sliding off it (or starting to scroll)
     * cancels. Then the button flashes amber. Keeping the finger on it for 0.4 s is a long press on the TV: the key goes
     * down, repeats, and comes up when the finger lifts or leaves; the button stays amber meanwhile.
     */
    private fun bindKey(b: Button, code: Int, held: Drawable) {
        val normal = b.background
        var holding = false
        var count = 0
        var start: Runnable? = null
        val again = object : Runnable {
            override fun run() {
                if (!holding) return
                keyDown(code, ++count)
                ui.postDelayed(this, 80)
            }
        }
        val stop = {
            start?.let { ui.removeCallbacks(it) }
            start = null
            if (holding) {
                holding = false
                ui.removeCallbacks(again)
                keyUp(code)
                b.background = normal
            }
        }
        b.setOnTouchListener { v, e ->
            val inside = e.x >= 0 && e.y >= 0 && e.x <= v.width && e.y <= v.height
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val go = Runnable {
                        if (!live()) return@Runnable
                        start = null
                        holding = true
                        count = 0
                        b.background = held
                        keyDown(code, 0)
                        ui.postDelayed(again, 80)
                    }
                    start = go
                    ui.postDelayed(go, 400)
                }
                MotionEvent.ACTION_MOVE -> if (!inside) stop()
                MotionEvent.ACTION_CANCEL -> stop()
                MotionEvent.ACTION_UP -> {
                    val tap = start != null && inside                 // lifted on the button before it became a hold
                    stop()
                    if (tap && live()) {
                        press(code)
                        flash(b, held)
                    }
                }
            }
            true
        }
    }

    /** Amber for a moment: the press was taken. */
    private fun flash(b: Button, held: Drawable) {
        val normal = b.background
        if (normal === held) return
        b.background = held
        ui.postDelayed({ b.background = normal }, 160)
    }

    private fun oval(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(color)
        return g
    }

    /** The round direction pad: four arrows on a disc, OK in the middle. */
    /**
     * A touchpad made of keys: every bit of a swipe is one arrow key in that direction, a tap is OK, a tap with two fingers
     * is Back. It is not a mouse: it moves whatever the arrows move, which on a TV browser is the pointer.
     */
    private fun touchpad(): View {
        val pad = FrameLayout(this)
        pad.background = box(CARD, 28)
        val hint = label(t("Vuốt: di chuyển  ·  Chạm: OK  ·  Chạm 2 ngón: Quay lại", "Swipe: move  ·  Tap: OK  ·  Two-finger tap: Back"), 13, MUTED)
        hint.gravity = Gravity.CENTER
        pad.addView(hint, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        val step = dp(20).toFloat()
        var ax = 0f
        var ay = 0f
        var moved = false
        var fingers = 0
        var downAt = 0L
        pad.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent.requestDisallowInterceptTouchEvent(true)  // the page must not scroll under the finger
                    ax = e.x; ay = e.y; moved = false; fingers = 1; downAt = e.eventTime
                }
                MotionEvent.ACTION_POINTER_DOWN -> fingers = maxOf(fingers, e.pointerCount)
                MotionEvent.ACTION_MOVE -> if (fingers == 1 && live()) {
                    while (Math.abs(e.x - ax) >= step) { val right = e.x > ax; press(if (right) 22 else 21); ax += if (right) step else -step; moved = true }
                    while (Math.abs(e.y - ay) >= step) { val down = e.y > ay; press(if (down) 20 else 19); ay += if (down) step else -step; moved = true }
                }
                MotionEvent.ACTION_UP -> if (!moved && live() && e.eventTime - downAt < 400) {
                    press(if (fingers >= 2) 4 else 23)
                    val normal = pad.background
                    pad.background = box(HELD, 28)
                    ui.postDelayed({ pad.background = normal }, 120)
                }
            }
            true
        }
        return pad
    }

    private fun dpad(): View {
        val pad = FrameLayout(this)
        pad.background = oval(CARD)
        val arrows = arrayOf(intArrayOf(19, Gravity.TOP or Gravity.CENTER_HORIZONTAL), intArrayOf(20, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL),
            intArrayOf(21, Gravity.START or Gravity.CENTER_VERTICAL), intArrayOf(22, Gravity.END or Gravity.CENTER_VERTICAL))
        val names = arrayOf("@up", "@down", "@left", "@right")
        val says = arrayOf(t("Lên", "Up"), t("Xuống", "Down"), t("Trái", "Left"), t("Phải", "Right"))
        for (i in 0 until 4) {
            val b = icon(names[i], says[i], 0)
            b.setIcon(names[i], MUTED)
            b.size = 30f
            b.background = oval(0)
            bindKey(b, arrows[i][0], oval(HELD))
            val lp = FrameLayout.LayoutParams(dp(80), dp(80), arrows[i][1])
            lp.setMargins(dp(4), dp(4), dp(4), dp(4))
            pad.addView(b, lp)
        }
        val ok = button("OK", ACCENT)
        ok.typeface = Typeface.DEFAULT_BOLD
        ok.textSize = 18f
        ok.setPadding(0, 0, 0, 0)
        ok.background = oval(ACCENT)
        ok.elevation = dp(6).toFloat()
        bindKey(ok, 23, oval(HELD))
        pad.addView(ok, FrameLayout.LayoutParams(dp(92), dp(92), Gravity.CENTER))
        val wrap = LinearLayout(this)
        wrap.gravity = Gravity.CENTER
        wrap.addView(pad, LinearLayout.LayoutParams(dp(248), dp(248)))
        return wrap
    }

    // ---------------------------------------------------------------- small view helpers
    private fun col(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        return l
    }

    private fun gap(top: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(-1, -2)
        p.topMargin = dp(top)
        return p
    }

    private fun cell(height: Int, leftGap: Boolean): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(0, dp(height), 1f)
        if (leftGap) p.leftMargin = dp(6)
        return p
    }

    private fun box(color: Int, radius: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radius).toFloat()
        return g
    }

    private fun label(s: String, sp: Int, color: Int): TextView {
        val v = TextView(this)
        v.text = s
        v.textSize = sp.toFloat()
        v.setTextColor(color)
        return v
    }

    private fun head(s: String): TextView {
        val v = label(s, 12, MUTED)
        v.typeface = Typeface.DEFAULT_BOLD
        v.letterSpacing = 0.08f
        return v
    }

    private fun note(s: String): TextView {
        val v = label(s, 13, MUTED)
        v.setPadding(0, dp(6), 0, 0)
        v.setLineSpacing(dp(3).toFloat(), 1f)
        return v
    }

    private fun button(s: String, color: Int): Button {
        val b = Button(this)
        b.text = s
        b.setAllCaps(false)
        b.setTextColor(TEXT)
        b.textSize = 15f
        b.background = box(color, 14)
        b.stateListAnimator = null
        b.setPadding(dp(14), 0, dp(14), 0)
        b.minHeight = 0
        b.minimumHeight = 0
        return b
    }

    /** A button that shows only an icon; the words go to screen readers. "@name" = res/drawable/ic_name (dev/gen_icons.py), anything else is text. */
    private fun icon(symbol: String, says: String, color: Int): IconButton {
        val b = IconButton(this)
        b.setIcon(symbol, if (symbol == "@power") RED else TEXT)
        b.setAllCaps(false)
        b.setTextColor(TEXT)
        b.textSize = 20f
        b.contentDescription = says
        b.background = box(color, 16)
        b.stateListAnimator = null
        b.setPadding(0, 0, 0, 0)
        b.minHeight = 0
        b.minimumHeight = 0
        b.minWidth = 0
        b.minimumWidth = 0
        return b
    }

    private fun row(s: String, onClick: () -> Unit): Button {
        val b = button(s, CARD)
        b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        b.setPadding(dp(14), dp(12), dp(14), dp(12))
        b.setOnClickListener { onClick() }
        b.layoutParams = gap(6)
        return b
    }

    private fun input(hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setHintTextColor(MUTED)
        e.setTextColor(TEXT)
        e.textSize = 15f
        e.setSingleLine(true)
        e.background = box(CARD, 12)
        e.setPadding(dp(12), 0, dp(12), 0)
        return e
    }

    private fun hideKeyboard() {
        val f = currentFocus
        if (f != null) (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(f.windowToken, 0)
    }

    companion object {
        private const val BG = 0xff111418.toInt()
        private const val CARD = 0xff1c2026.toInt()
        private const val BTN = 0xff2a3038.toInt()
        private const val ACCENT = 0xff5b8cff.toInt()
        private const val HELD = 0xffe0a030.toInt()
        private const val TEXT = 0xffe8eaed.toInt()
        private const val MUTED = 0xff9aa0a6.toInt()
        private const val RED = 0xffff8a80.toInt()                    // the power icon, "Disconnect"
        private const val GREEN = 0xff2e7d4f.toInt()                  // the update banner
        private const val ERR_TEXT = 0xffffb4a8.toInt()
        private const val ERR_BG = 0xff3a2020.toInt()
        private const val INSTALL_RESULT = "dev.tvremote.phone.INSTALL_RESULT"
        private const val PICK_FILE = 77
        private const val SPEAK = 78
        private var VI = "vi" == Locale.getDefault().language         // set again in onCreate from the Settings choice

        fun t(vi: String, en: String): String = if (VI) vi else en

        private fun open(ip: String, port: Int, timeoutMs: Int): Boolean =
            try { Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs) }; true } catch (e: Exception) { false }
    }
}
