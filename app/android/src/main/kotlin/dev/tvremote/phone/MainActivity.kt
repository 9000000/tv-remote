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
    private lateinit var stage: FrameLayout                           // the picture with the full screen button over it
    private var tv: Tv? = null
    private var basic: Atv? = null                                    // keys only, for a TV whose ADB is still off (see Atv)
    private lateinit var basicBar: Button
    private var host: String? = null                                  // the TV we want to be connected to, null on the picker
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
    private fun connect(ip: String) {
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

    private fun disconnect(message: String?) {
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
            when (key) {
                "connecting" -> status.text = t("Đang kết nối ", "Connecting to ") + detail + "..."
                "allow" -> status.text = t("Nhìn lên TV: tích “Luôn cho phép từ máy tính này” rồi chọn OK.", "Look at the TV: tick “Always allow from this computer”, then OK.")
                "starting" -> {
                    save(h, detail)
                    status.text = t("Đang mở hình từ ", "Opening the picture from ") + detail.ifEmpty { h } + "..."
                }
                "rate" -> if (video) status.text = title(h) + " · " + detail + " KB/s"
                "live" -> {
                    pendingAsk?.let {
                        from?.search(it)
                        pendingAsk = null
                    }
                    status.text = title(h) + " · " + (if (video) t("chạm vào hình để bấm trên TV", "touch the picture to tap on the TV")
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
        Toast.makeText(this, t("Cần bật Gỡ lỗi ADB trên TV cho chức năng này", "This needs ADB debugging turned on on the TV"), Toast.LENGTH_SHORT).show()
        return true
    }

    /**
     * ADB did not work. Before saying so, see whether the TV answers as an Android TV: then the keys-only remote takes over
     * (straight away when this phone is paired with it, after asking otherwise). [error] is shown only when that is not possible.
     */
    private fun offerBasic(ip: String, error: String) {
        Thread {
            val keys = !open(ip, 5555, 1500) && open(ip, 6466, 2500)   // not when ADB answers after all, or this is no Android TV
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
                    save(ip, a.model)
                    message(null, false)
                    show(true)
                    layoutRemote()
                    status.text = title(ip) + " · " + t("chỉ bấm phím", "keys only")
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

    /** Everything the keys-only mode can do towards turning ADB on, in the order one needs it. */
    private fun adbHelp() {
        menu(t("Bật ADB cho TV này", "Turn on ADB for this TV"), 0, listOf(
            Triple(t("1. Mở Cài đặt TV", "1. Open TV Settings"), t("App tự thử mọi cách và báo kết quả", "The app tries every way and tells you")) { findSettings() },
            Triple(t("2. Cài màn hình chính khác", "2. Install another home screen"), t("Khi TV giấu Cài đặt: mở qua một launcher từ CH Play", "When the TV hides Settings: go through a launcher from the Play Store")) { otherHome() },
            Triple(t("3. Các bước bật Gỡ lỗi ADB", "3. Steps to turn on ADB debugging"), t("Làm trong Cài đặt của TV", "Done in the TV’s Settings")) { adbSteps() },
            Triple(t("4. Kết nối đầy đủ", "4. Full connection"), t("Sau khi đã bật ADB: xem hình, ứng dụng, tệp...", "Once ADB is on: picture, apps, files...")) {
                val ip = host
                disconnect(null)
                if (ip != null) connect(ip)
            },
            Triple(t("Cài TV Setup lên TV", "Install TV Setup on the TV"), t("Khi TV đòi mật khẩu ADB hoặc giấu Tùy chọn nhà phát triển: app này mở thẳng màn hình đó", "When the TV asks for an ADB password or hides Developer options: this app opens that screen directly")) { installSetup() },
            Triple(t("Mở CH Play trên TV", "Open the Play Store on the TV"), "") { basic?.launch("market://search?q=launcher"); Unit },
            Triple(t("Mở app theo tên gói", "Open an app by package name"), t("Đã cài thì mở app, chưa cài thì mở trang CH Play của nó", "Opens the app, or its Play Store page when not installed")) {
                val box = input("com.example.app")
                val wrap = FrameLayout(this)
                wrap.setPadding(dp(20), dp(8), dp(20), 0)
                wrap.addView(box)
                AlertDialog.Builder(this).setTitle(t("Tên gói hoặc đường dẫn", "Package name or link")).setView(wrap)
                    .setPositiveButton(t("Mở", "Open")) { _, _ -> box.text.toString().trim().let { if (it.isNotEmpty()) basic?.launch(it) } }
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
        val link = "github.com/hoangtran0410/tv-remote/releases/latest/download/tv-setup.apk"
        val dl = "com.esaba.downloader"
        var stop = false
        val box = AlertDialog.Builder(this).setTitle(t("Cài TV Setup lên TV", "Install TV Setup on the TV")).setMessage(t("Đang mở Downloader trên TV...", "Opening Downloader on the TV..."))
            .setNegativeButton(t("Dừng", "Stop")) { _, _ -> stop = true }.setOnCancelListener { stop = true }.show()
        val say = { vi: String, en: String -> ui.post { if (box.isShowing) box.setMessage(t(vi, en)) } }
        // waits until the TV says [pkg] is in front; false when it does not within [seconds], or the person stopped it
        val front = { pkg: String, seconds: Int ->
            var ok = false
            for (i in 0 until seconds * 5) { if (stop || basic !== a) break; if (a.app == pkg) { ok = true; break }; Thread.sleep(200) }
            ok
        }
        Thread {
            a.launch(dl)
            var there = front(dl, 6)
            if (!there && !stop && a.app.isEmpty()) {                  // this TV does not say what is on its screen: no way to follow along
                ui.post { box.dismiss(); installSetupByHand() }
                return@Thread
            }
            if (!there && !stop) {                                     // not installed: the TV is on its Play Store page, with Install in focus
                say("TV chưa có Downloader: đang cài từ CH Play...\n\nNếu TV không tự cài, bấm “Cài đặt” trên màn hình TV. App sẽ tự đi tiếp khi cài xong.",
                    "The TV has no Downloader yet: installing it from the Play Store...\n\nIf the TV does not start by itself, choose “Install” on its screen. The app carries on when it is done.")
                Thread.sleep(2500)
                a.pulse(23, 110)
                for (round in 0 until 30) {                            // up to 3 minutes: ask for the app again until it opens
                    if (stop || basic !== a) break
                    Thread.sleep(4000)
                    a.launch(dl)
                    if (front(dl, 2)) { there = true; break }
                }
            }
            if (!there) {
                ui.post { if (box.isShowing) box.dismiss(); if (!stop && basic === a) installSetupByHand() }
                return@Thread
            }
            say("Đang nhập đường dẫn tải vào Downloader...", "Typing the download link into Downloader...")
            Thread.sleep(3500)                                        // its home screen loads with the address field in focus
            a.pulse(23, 110)                                          // OK: opens the field and its keyboard
            Thread.sleep(1800)
            a.keys(link)
            Thread.sleep(link.length * 60L + 800)
            a.key(85, Atv.SHORT)                                      // Play/Pause is "Go" on Downloader's keyboard
            ui.post {
                if (box.isShowing) box.dismiss()
                if (stop || basic !== a || isFinishing) return@post
                AlertDialog.Builder(this).setTitle(t("Nhìn lên TV", "Look at the TV"))
                    .setMessage(t("App đã nhập đường dẫn và bấm Go.\n\n1. Chờ tải xong, chọn Install. Nếu TV hỏi quyền cài từ nguồn này: bật cho Downloader rồi quay lại.\n2. Cài xong chọn Open (hoặc bấm “Mở TV Setup” bên dưới).\n3. TV Setup tự mở Tùy chọn nhà phát triển: bật “Gỡ lỗi USB”.\n\nNếu ô địa chỉ trên TV không có chữ: bấm “Làm từng bước”.",
                        "The app has typed the link and pressed Go.\n\n1. When the download ends, choose Install. If the TV asks to allow installs from this source: allow Downloader, then go back.\n2. Then choose Open (or tap “Open TV Setup” below).\n3. TV Setup opens Developer options by itself: turn on “USB debugging”.\n\nIf the address field on the TV is empty: tap “Step by step”."))
                    .setPositiveButton(t("Mở TV Setup", "Open TV Setup")) { _, _ -> a.launch("dev.tvremote.setup") }
                    .setNeutralButton(t("Làm từng bước", "Step by step")) { _, _ -> installSetupByHand() }
                    .setNegativeButton(t("Đóng", "Close"), null).show()
            }
        }.start()
    }

    /** The same, one step at a time, for when the automatic run loses its way. */
    private fun installSetupByHand() {
        val link = "github.com/hoangtran0410/tv-remote/releases/latest/download/tv-setup.apk"   // lower case, no "https://": typed as key presses, and Downloader adds the rest
        menu(t("Cài TV Setup lên TV", "Install TV Setup on the TV"), -1, listOf(
            Triple(t("1. Mở Downloader trên TV", "1. Open Downloader on the TV"), t("Chưa cài thì TV mở trang CH Play của nó: bấm Cài đặt, rồi bấm lại mục này", "Not installed: the TV opens its Play Store page. Install it, then tap this again")) {
                basic?.launch("com.esaba.downloader")
                ui.postDelayed({ if (basic != null && !isFinishing) installSetupByHand() }, 600)
            },
            Triple(t("2. Gửi đường dẫn tải vào TV", "2. Send the download link to the TV"), t("Trước đó, trên TV chọn ô nhập địa chỉ của Downloader (bàn phím hiện lên). Xong bấm Go, rồi Install", "First put the focus on Downloader’s address field on the TV (its keyboard shows). Then choose Go, then Install")) {
                basic?.let { if (it.hasField) it.text(link) else it.keys(link) }
                Toast.makeText(this, t("Đã gửi đường dẫn vào ô đang chọn trên TV", "Link sent to the field that has the focus on the TV"), Toast.LENGTH_LONG).show()
                ui.postDelayed({ if (basic != null && !isFinishing) installSetupByHand() }, 600)
            },
            Triple(t("3. Mở TV Setup trên TV", "3. Open TV Setup on the TV"), t("Trong đó bấm “Tùy chọn nhà phát triển” rồi bật Gỡ lỗi USB", "There choose “Tùy chọn nhà phát triển” (Developer options) and turn on USB debugging")) { basic?.launch("dev.tvremote.setup"); Unit },
            Triple(t("Chép đường dẫn", "Copy the link"), link) {
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("tv-setup", link))
                Toast.makeText(this, t("Đã chép", "Copied"), Toast.LENGTH_SHORT).show()
            }))
    }

    private fun isSettings(pkg: String) = pkg.contains("settings", true)

    /** Tries the Settings key, then the links that open Settings on other TVs, watching which app the TV says is in front. */
    private fun findSettings() {
        val a = basic ?: return
        Toast.makeText(this, t("Đang thử mở Cài đặt trên TV...", "Trying to open Settings on the TV..."), Toast.LENGTH_SHORT).show()
        Thread {
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
                a.launch(pkg)
                AlertDialog.Builder(this).setTitle(name).setMessage(t("Nhìn lên TV.\n\n• Thấy trang CH Play: bấm Cài đặt, xong bấm “Mở lại” bên dưới.\n• CH Play báo không tương thích hoặc không tìm thấy: bấm “Chọn cái khác”.\n• Thấy màn hình chính mới: tìm biểu tượng bánh răng → Cài đặt Android, rồi bấm “Các bước bật ADB”.\n\nCH Play cần TV đã đăng nhập tài khoản Google.",
                        "Look at the TV.\n\n• A Play Store page: choose Install, then tap “Open again” below.\n• The store says it is incompatible or not found: tap “Pick another”.\n• The new home screen: find the gear icon → Android settings, then tap “ADB steps”.\n\nThe Play Store needs a Google account on the TV."))
                    .setPositiveButton(t("Các bước bật ADB", "ADB steps")) { _, _ -> adbSteps() }
                    .setNeutralButton(t("Mở lại", "Open again")) { _, _ -> a.launch(pkg); ui.postDelayed({ if (basic === a && !isFinishing) adbSteps() }, 800) }
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
                disconnect(null)
                if (ip != null) connect(ip)
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

    override fun onNetwork(mac: String, lan: String) {
        val ip = host
        if (ip != null) prefs.edit().putString("mac:$ip", mac).putString("lan:$ip", lan).apply()
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
                val up = open(ip, 5555, 2500) || prefs.getBoolean("atv:$ip", false) && open(ip, 6466, 1500)
                val lan = pref("lan:$ip")
                val away = lan.isNotEmpty() && !Wol.sameNetwork(lan)   // the phone is not on the TV's own Wi-Fi
                ui.post { reach[ip] = up; awayFrom[ip] = away; showReach(ip, up) }
            }.start()
            ui.postDelayed(this, 8000)
        }
    }

    private fun showReach(ip: String, up: Boolean?) {
        val (b, wk) = savedRows[ip] ?: return
        val status = when (up) {
            null -> t("đang kiểm tra...", "checking...")
            true -> t("● kết nối được", "● reachable")
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
            val b = row("") { connect(ip) }
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
            b.setOnLongClickListener {
                AlertDialog.Builder(this).setMessage(t("Xóa TV này khỏi danh sách?", "Remove this TV from the list?") + "\n" + ip)
                    .setPositiveButton(t("Xóa", "Remove")) { _, _ ->
                        val ips = savedIps()
                        ips.remove(ip)
                        prefs.edit().putString("ips", TextUtils.join(",", ips)).remove("name:$ip").remove("mac:$ip").remove("lan:$ip").apply()
                        refreshSaved()
                    }.setNegativeButton(android.R.string.cancel, null).show()
                true
            }
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
            try {
                for (ni in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (a in Collections.list(ni.inetAddresses)) {
                        val ip = a.hostAddress ?: continue
                        if (a is Inet4Address && a.isSiteLocalAddress) nets.add(ip.substring(0, ip.lastIndexOf('.') + 1))
                    }
                }
                val pool = Executors.newFixedThreadPool(64)
                for (net in nets) for (i in 1 until 255) {
                    val ip = net + i
                    pool.execute {
                        if (open(ip, 5555, 500) || open(ip, 6466, 500)) {
                            found.add(ip)
                            (name(ip).ifEmpty { castName(ip) }).let { if (it.isNotEmpty()) names[ip] = it }
                        }
                    }
                }
                pool.shutdown()
                pool.awaitTermination(20, TimeUnit.SECONDS)
            } catch (ignored: Exception) {
            }
            ui.post {
                scanBtn.isEnabled = true
                scanBtn.text = "🔍  " + t("Quét Wi-Fi", "Scan the Wi-Fi")
                val ips = synchronized(found) { found.sorted() }
                for (ip in ips) foundList.addView(row(names[ip]?.let { it + "\n" + ip } ?: ip) { connect(ip) })
                if (ips.isEmpty()) foundList.addView(note(
                    if (nets.isEmpty()) t("Điện thoại chưa nối Wi-Fi. Khác mạng thì nhập IP Tailscale của TV bên dưới.", "The phone is not on Wi-Fi. On another network, type the TV’s Tailscale IP below.")
                    else t("Không thấy TV nào trong Wi-Fi này. TV có đang bật và đã bật “Gỡ lỗi ADB” chưa?", "No TV found on this Wi-Fi. Is it on, with “ADB debugging” enabled?")))
            }
        }.start()
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
                    "2B. KHÁC MẠNG\nBật Tailscale trên cả điện thoại và TV, rồi nhập IP 100.x của TV (xem trong app Tailscale).\n\n" +
                    "TV chưa có Tailscale? Mở Tailscale trên TV, nó hiện một mã. Nhập mã ở login.tailscale.com/admin/machines → Add device.\n\n" +
                    "3. LẦN ĐẦU KẾT NỐI\nTV hỏi cho phép: tích “Luôn cho phép” rồi OK.\n\n" +
                    "MẸO\nNút nguồn cạnh tên TV: đánh thức TV đang tắt. Giữ lâu vào tên TV: xóa khỏi danh sách.",
                "1. TURN ON ADB ON THE TV (once)\nSettings → Device Preferences → About → press OK 7 times on “Build”. Then Developer options → turn on “ADB debugging”.\n\n" +
                    "2A. SAME WI-FI AS THE TV\nTap “Scan the Wi-Fi” and pick the TV. Or read the IP on the TV: Settings → Network & Internet → the Wi-Fi name.\n\n" +
                    "2B. ANOTHER NETWORK\nTurn on Tailscale on both the phone and the TV, then type the TV’s 100.x IP (see the Tailscale app).\n\n" +
                    "No Tailscale on the TV yet? Open Tailscale on the TV, it shows a code. Enter it at login.tailscale.com/admin/machines → Add device.\n\n" +
                    "3. THE FIRST CONNECTION\nThe TV asks for permission: tick “Always allow”, then OK.\n\n" +
                    "TIPS\nThe power button next to a TV wakes it when it is off. Long-press a TV to remove it from the list."))
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
        Toast.makeText(this, t("Gửi cho TV: ", "Sent to the TV: ") + sentence, Toast.LENGTH_SHORT).show()
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
        private var page: ByteArray? = null

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
            web.webViewClient = WebViewClient()                       // links stay inside
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
            if (r.path == "/" || r.path == "/index.html") {
                val html = page ?: run {
                    val buf = ByteArrayOutputStream()
                    assets.open("index.html").use { it.copyTo(buf, 65536) }
                    buf.toByteArray().also { page = it }
                }
                return HttpServer.Response.of("text/html; charset=utf-8", html)
            }
            if (r.path == "/api/state")                               // the page only needs to know it may show itself
                return HttpServer.Response.json(JSONObject().put("name", name(forHost ?: "")).put("on", true).put("volume", 0).put("volume_max", 0).put("muted", false).put("adb", "ready")
                    .put("app", "TV Remote").put("version", version()).put("released", released()))
            if (r.path == "/stats.js") return HttpServer.Response.of("text/javascript; charset=utf-8", assets.open("stats.js").use { it.readBytes() })
            if (r.path == "/api/icon") {
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
            else { if (it.hasField) it.text(s) else it.keys(s); textBox.setText("") }
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
            if (it.hasField) { if (now.isNotEmpty()) it.text(now) }
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
