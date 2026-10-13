package dev.tvremote.phone

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
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
    private var tv: Tv? = null
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

    private fun layoutRemote() {
        val full = video && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE   // landscape: the picture fills the screen
        (controls.parent as View).visibility = if (full) View.GONE else View.VISIBLE
        (status.parent as View).visibility = if (full) View.GONE else View.VISIBLE
        frame.layoutParams = LinearLayout.LayoutParams(-1, if (full) -1 else -2)
        frame.visibility = if (video) View.VISIBLE else View.GONE
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
        if (about.visibility == View.VISIBLE) about.visibility = View.GONE
        else if (settings.visibility == View.VISIBLE) settings.visibility = View.GONE
        else if (host != null) disconnect(null)
        else super.onBackPressed()
    }

    /** The phone's volume buttons change the TV's volume while connected. */
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val c = e.keyCode
        val tv = tv
        if (tv != null && tv.ready() && (c == KeyEvent.KEYCODE_VOLUME_UP || c == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (e.action == KeyEvent.ACTION_DOWN) tv.press(c)
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
        if (h == null || tv != null || video && !surfaceReady) return
        val dm = resources.displayMetrics
        tv = Tv(this, h, if (video) surface.holder.surface else null, prefs.getInt("quality", 1), maxOf(dm.widthPixels, dm.heightPixels), wakeNext, this)
        wakeNext = false
    }

    private fun stopSession() {
        tv?.stop()
        tv = null
    }

    private fun disconnect(message: String?) {
        managing.close()
        stopSession()
        host = null
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
                    else t("chỉ điều khiển, không xem hình (bấm nút con mắt để xem)", "buttons only, no picture (tap the eye to watch)"))
                }
                "closed" -> {
                    tv = null
                    disconnect(t("TV đã ngắt kết nối.", "The TV closed the connection."))
                }
                else -> {
                    tv = null
                    disconnect(explain(detail))
                }
            }
        }
    }

    private fun explain(err: String?): String {
        val e = err?.lowercase(Locale.ROOT) ?: ""
        if (e.contains("refused") && !e.contains("refused:") || e.contains("econnrefused"))
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
                    else -> t("Có bản mới ", "New version ") + r.version + t(". Bấm nút ở màn chọn TV để cập nhật.", ". Tap the button on the TV list to update.")
                }
            }
        }.start()
    }

    private fun showUpdate() {
        val u = update
        updateBtn.visibility = if (u == null) View.GONE else View.VISIBLE
        if (u != null && !updating) updateBtn.text = "🆕  " + t("Có bản mới ", "New version ") + u.version + t(" · bấm để cập nhật", " · tap to update")
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
        updateBtn.text = t("Đang tải bản ", "Downloading ") + r.version + "..."
        Thread {
            var error: String? = null
            try {
                val apk = File(cacheDir, "update.apk")
                Release.download(r.url, apk) { pct -> ui.post { updateBtn.text = t("Đang tải bản ", "Downloading ") + r.version + "... " + pct + "%" } }
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
                    updateBtn.text = t("Đang chờ Android xác nhận cài đặt...", "Waiting for Android to confirm the install...")
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
        message(t("Đang đánh thức ", "Waking ") + title(ip) + t("... (tối đa 45 giây)", "... (up to 45 seconds)"), false)
        Thread {
            var up = open(ip, 5555, 1500)
            var sent = 0
            var i = 0
            while (i < 22 && !up) {
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
    private val probe = object : Runnable {
        override fun run() {
            ui.removeCallbacks(this)
            if (host == null && !waking) for (ip in savedIps()) Thread {
                val up = open(ip, 5555, 2500)
                ui.post { reach[ip] = up; showReach(ip, up) }
            }.start()
            ui.postDelayed(this, 8000)
        }
    }

    private fun showReach(ip: String, up: Boolean?) {
        val (b, wk) = savedRows[ip] ?: return
        val status = when (up) {
            null -> t("đang kiểm tra...", "checking...")
            true -> t("● kết nối được", "● reachable")
            false -> t("○ không thấy: TV tắt hoặc khác mạng", "○ no answer: off or on another network")
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
                    pool.execute { if (open(ip, 5555, 500) || open(ip, 6466, 500)) found.add(ip) }
                }
                pool.shutdown()
                pool.awaitTermination(20, TimeUnit.SECONDS)
            } catch (ignored: Exception) {
            }
            ui.post {
                scanBtn.isEnabled = true
                scanBtn.text = "🔍  " + t("Quét Wi-Fi", "Scan the Wi-Fi")
                val ips = synchronized(found) { found.sorted() }
                for (ip in ips) foundList.addView(row(ip) { connect(ip) })
                if (ips.isEmpty()) foundList.addView(note(
                    if (nets.isEmpty()) t("Điện thoại chưa nối Wi-Fi. Khác mạng thì nhập IP Tailscale của TV bên dưới.", "The phone is not on Wi-Fi. On another network, type the TV’s Tailscale IP below.")
                    else t("Không thấy TV nào trong Wi-Fi này. TV có đang bật và đã bật “Gỡ lỗi ADB” chưa?", "No TV found on this Wi-Fi. Is it on, with “ADB debugging” enabled?")))
            }
        }.start()
    }

    // ---------------------------------------------------------------- picker screen
    private fun buildPicker() {
        picker = col()
        picker.setPadding(dp(16), dp(20), dp(16), dp(24))
        val title = label("TV Remote", 24, TEXT)
        title.typeface = Typeface.DEFAULT_BOLD
        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
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

        box.addView(head(t("CHẤT LƯỢNG HÌNH", "PICTURE QUALITY")), gap(18))
        val levels = listOf("🪶  " + t("Tiết kiệm · 480 px · tối đa khoảng 40 KB/s", "Data saver · 480 px · up to about 40 KB/s"),
            "⚖️  " + t("Vừa · 720 px · tối đa khoảng 100 KB/s", "Balanced · 720 px · up to about 100 KB/s"),
            "🔍  " + t("Nét · 960 px · tối đa khoảng 250 KB/s", "Sharp · 960 px · up to about 250 KB/s"))
        val qb = ArrayList<Button>()
        val marks = {
            for ((i, b) in qb.withIndex()) {
                val on = prefs.getInt("quality", 1) == i
                b.text = levels[i] + (if (on) "   ✓" else "")
                b.setTextColor(if (on) ACCENT else TEXT)
            }
        }
        for (level in levels.indices) {
            val b = row("") {
                prefs.edit().putInt("quality", level).apply()
                marks()
            }
            qb.add(b)
            box.addView(b)
        }
        marks()
        box.addView(note(t("Áp dụng từ lần kết nối sau. Hình đứng yên thì gần như không tốn dữ liệu; con số trên là khi hình chuyển động liên tục (đang phát video). Mức thấp cũng nhẹ cho TV hơn. Không xem hình thì bấm nút con mắt ở màn remote: không tốn gì.",
            "Applies from the next connection. A still picture uses almost no data; the figures are for a constantly moving picture (video playing). Lower levels are also lighter on the TV. To not watch at all, tap the eye on the remote screen: that costs nothing.")))

        box.addView(head(t("CẬP NHẬT", "UPDATES")), gap(18))
        lateinit var auto: Button
        val autoLabel = { auto.text = (if (prefs.getBoolean("autoUpdate", true)) "✅  " else "⬜  ") + t("Tự kiểm tra bản mới khi mở app", "Check for a new version when the app opens") }
        auto = row("") {
            prefs.edit().putBoolean("autoUpdate", !prefs.getBoolean("autoUpdate", true)).apply()
            autoLabel()
        }
        autoLabel()
        box.addView(auto)
        box.addView(row("🔄  " + t("Kiểm tra bản mới ngay", "Check for a new version now")) { checkUpdate(true) })
        updateNote = note(t("App hỏi GitHub xem có bản phát hành mới hơn không; ngoài ra không gửi gì đi.", "The app asks GitHub whether a newer release exists; nothing else is sent."))
        box.addView(updateNote)

        box.addView(head(t("GIỚI THIỆU", "ABOUT")), gap(18))
        box.addView(row("ℹ️  " + t("Giới thiệu, tác giả, mã nguồn", "About, author, source code")) { about.visibility = View.VISIBLE })

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
                    manage = Manage(l, null)
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
                return HttpServer.Response.json(JSONObject().put("name", name(forHost ?: "")).put("on", true).put("volume", 0).put("volume_max", 0).put("muted", false).put("adb", "ready"))
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
        val name = label("TV Remote " + version(), 20, TEXT)
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
        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        val vb = LinearLayout.LayoutParams(dp(52), dp(40))
        vb.rightMargin = dp(10)
        vb.topMargin = dp(6)
        bar.addView(videoBtn, vb)
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
        remote.addView(frame, LinearLayout.LayoutParams(-1, -2))

        controls = col()
        controls.setPadding(dp(14), dp(12), dp(14), dp(20))
        val first = keys(52, "@menu|Menu", 82)
        first.getChildAt(0).layoutParams = cell(52, true)
        // power is a tap with a question, never a hold: a held power key opens the TV's power menu and picks "power off" there
        val power = icon("@power", t("Nguồn", "Power"), BTN)
        power.setOnClickListener {
            AlertDialog.Builder(this).setMessage(t("Tắt TV?", "Turn the TV off?"))
                .setPositiveButton(t("Tắt", "Turn off")) { _, _ -> tv?.let { if (it.ready()) it.press(26) } }
                .setNegativeButton(t("Hủy", "Cancel"), null).show()
        }
        first.addView(power, 0, cell(52, false))
        val tvSettings = icon("@settings", t("Cài đặt của TV", "TV settings"), BTN)
        tvSettings.setOnClickListener { tv?.let { if (it.ready()) it.settings() } }
        first.addView(tvSettings, cell(52, true))
        val apps = icon("@apps", t("Ứng dụng", "Apps"), BTN)
        apps.setOnClickListener { managing.open(host) }
        val mic = icon("@mic", t("Nói với TV", "Speak to the TV"), BTN)
        mic.setOnClickListener { speak() }
        mic.setOnLongClickListener {                                  // the TV's own Assistant screen
            tv?.let { if (it.ready()) it.press(219) }
            true
        }
        first.addView(mic, cell(52, true))
        first.addView(apps, cell(52, true))
        controls.addView(first)
        controls.addView(dpad(), gap(16))
        controls.addView(keys(56, "@back|" + t("Quay lại", "Back"), 4, "@home|Home", 3, "@mute|" + t("Tắt tiếng", "Mute"), 164), gap(16))
        controls.addView(keys(52, "@vol_down|" + t("Giảm âm lượng", "Volume down"), 25, "@prev|" + t("Bài trước", "Previous"), 88, "@play_pause|" + t("Phát / Dừng", "Play / Pause"), 85,
            "@next|" + t("Bài sau", "Next"), 87, "@vol_up|" + t("Tăng âm lượng", "Volume up"), 24), gap(8))

        val line = LinearLayout(this)
        textBox = input(t("Gõ chữ gửi lên TV", "Type text for the TV"))
        textBox.imeOptions = EditorInfo.IME_ACTION_SEND
        textBox.setSingleLine(true)
        textBox.setOnEditorActionListener { _, _, _ ->
            sendText()
            true
        }
        line.addView(textBox, LinearLayout.LayoutParams(0, dp(48), 1f))
        val send = icon("@send", t("Gửi", "Send"), ACCENT)
        send.setOnClickListener { sendText() }
        val lp = LinearLayout.LayoutParams(-2, dp(48))
        lp.leftMargin = dp(6)
        lp.width = dp(60)
        line.addView(send, lp)
        controls.addView(line, gap(12))
        controls.addView(keys(48, "@backspace|" + t("Xóa", "Delete"), 67, "@enter|Enter", 66), gap(8))

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
        val tv = tv
        if (tv != null && tv.ready() && s.isNotEmpty()) {
            tv.text(s)
            textBox.setText("")
        }
    }

    /** A row of equal-width remote keys: label, Android key code, label, key code... "@icon|words" shows an icon. An empty label leaves a gap. */
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
                bindKey(b, code, box(HELD, 16))
                v = b
            }
            row.addView(v, cell(height, i > 0))
        }
        return row
    }

    /** Down and up are sent separately, so holding the button = a long press on the TV; it turns amber while held. */
    private fun bindKey(b: Button, code: Int, held: Drawable) {
        val normal = b.background
        var repeat: Runnable? = null
        var count = 0
        b.setOnTouchListener { _, e ->
            val a = e.actionMasked
            if (a == MotionEvent.ACTION_DOWN) {
                val tv = tv
                if (tv == null || !tv.ready()) return@setOnTouchListener true
                b.background = held
                count = 0
                tv.key(0, code, 0, 0)
                val again = object : Runnable {
                    override fun run() {
                        this@MainActivity.tv?.key(0, code, ++count, 0)
                        ui.postDelayed(this, 80)
                    }
                }
                repeat = again
                ui.postDelayed(again, 450)
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                b.background = normal
                repeat?.let {
                    ui.removeCallbacks(it)
                    repeat = null
                    tv?.key(1, code, 0, 0)
                }
            }
            true
        }
    }

    private fun oval(color: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(color)
        return g
    }

    /** The round direction pad: four arrows on a disc, OK in the middle. */
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
