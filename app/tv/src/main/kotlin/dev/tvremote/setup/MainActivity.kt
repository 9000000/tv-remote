package dev.tvremote.setup

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import dev.tvremote.core.Adb
import dev.tvremote.core.Setup
import dev.tvremote.core.SetupUpdate
import dev.tvremote.core.Tailscale
import dev.tvremote.update.Release
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One screen, driven with the TV remote. Left: where things stand (ADB, the TV's addresses) and six actions as tiles.
 * Right: how a phone or computer takes over (the QR code of the web remote this TV serves, the phone app). Below: what happened.
 * Words follow the TV's language (Vietnamese, otherwise English).
 */
class MainActivity : Activity() {
    companion object {
        @Volatile var shown = false                                   // on screen: no update in the background meanwhile (AutoUpdate)
    }

    private val vi = Locale.getDefault().language == "vi"
    private fun t(v: String, e: String) = if (vi) v else e

    private lateinit var dot: View
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var spinner: ProgressBar
    private lateinit var tiles: List<Tile>
    private lateinit var tsTile: Tile
    private lateinit var updateTile: Tile
    private lateinit var webCol: LinearLayout
    private lateinit var webBtn: Button
    private var lanIp = ""
    private var tsIp = ""
    private var adb: Adb? = null
    private var setup: Setup? = null
    private var busy = false
    private var focusedOnce = false

    private val BG = Color.rgb(13, 16, 20)
    private val CARD = Color.rgb(25, 29, 35)
    private val TILE = Color.rgb(34, 39, 47)
    private val INK = Color.rgb(233, 236, 241)
    private val MUTED = Color.rgb(140, 150, 163)
    private val ACCENT = Color.rgb(59, 115, 246)
    private val OK = Color.rgb(70, 210, 131)
    private val WARN = Color.rgb(240, 179, 74)
    private val BAD = Color.rgb(255, 112, 112)

    /** An action: an icon, a title, and one line beside it saying what it does or where it stands. */
    private inner class Tile(icon: Int, title: String, sub: String, run: () -> Unit) {
        val view = LinearLayout(this@MainActivity)
        private val subView: TextView
        private val badge = ImageView(this@MainActivity)
        private val wheel = ProgressBar(this@MainActivity)              // in place of the icon while this tile's work runs
        init {
            view.gravity = Gravity.CENTER_VERTICAL
            view.setPadding(dp(14), dp(10), dp(14), dp(10))
            view.isFocusable = true
            view.isClickable = true
            view.background = box(TILE, 14, 0)
            badge.setImageResource(icon)
            badge.setColorFilter(INK)
            badge.background = box(CARD, 10, 0)
            badge.setPadding(dp(8), dp(8), dp(8), dp(8))
            val slot = android.widget.FrameLayout(this@MainActivity)
            slot.addView(badge, android.widget.FrameLayout.LayoutParams(dp(40), dp(40)))
            wheel.isIndeterminate = true
            wheel.indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            wheel.visibility = View.GONE
            slot.addView(wheel, android.widget.FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
            view.addView(slot, LinearLayout.LayoutParams(dp(40), dp(40)))
            val words = LinearLayout(this@MainActivity)
            words.orientation = LinearLayout.VERTICAL
            words.setPadding(dp(12), 0, 0, 0)
            val head = text(title, 16, INK, true)
            head.isSingleLine = true
            head.ellipsize = TextUtils.TruncateAt.END
            words.addView(head)
            subView = text(sub, 12, MUTED, false)
            subView.maxLines = 2
            subView.ellipsize = TextUtils.TruncateAt.END
            words.addView(subView)
            view.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
            view.setOnClickListener { if (!busy) run() }
            view.setOnFocusChangeListener { v, f ->
                v.background = if (f) box(ACCENT, 14, 2) else box(TILE, 14, 0)
                badge.background = box(if (f) Color.argb(60, 255, 255, 255) else CARD, 10, 0)
                subView.setTextColor(if (f) Color.WHITE else MUTED)
                v.animate().scaleX(if (f) 1.03f else 1f).scaleY(if (f) 1.03f else 1f).setDuration(120).start()
            }
        }
        private var idle = sub
        private var working = false
        fun say(s: String) { idle = s; if (!working) subView.text = s }
        fun progress(s: String) { if (working) subView.text = s }
        fun work(on: Boolean) {
            working = on
            subView.text = if (on) t("Đang làm...", "Working...") else idle
            wheel.visibility = if (on) View.VISIBLE else View.GONE
            badge.imageAlpha = if (on) 0 else 255
        }
        fun enable(on: Boolean) { view.isEnabled = on; view.alpha = if (on) 1f else 0.4f }
    }

    /**
     * A phone (TV Setup runs there too, see README): one scrolling column instead of the TV's two, whichever way it is held.
     * Not the screen size alone: a 1080p TV is only 540dp on its short side.
     */
    private val phone by lazy {
        !packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) && resources.configuration.smallestScreenWidthDp < 600
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val page = LinearLayout(this)
        page.setBackgroundColor(BG)
        if (phone) {
            page.orientation = LinearLayout.VERTICAL
            page.setPadding(dp(16), dp(20), dp(16), dp(24))
        } else page.setPadding(dp(36), dp(28), dp(36), dp(24))

        // ---- left: header, status, actions, activity
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.clipChildren = false                                     // a focused tile grows 3%: drawn into the page's margin, not cut
        page.clipChildren = false; page.clipToPadding = false

        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.icon)
        logo.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(10).toFloat()) }
        }
        logo.clipToOutline = true
        head.addView(logo, LinearLayout.LayoutParams(dp(44), dp(44)))
        val names = LinearLayout(this)
        names.orientation = LinearLayout.VERTICAL
        names.setPadding(dp(14), 0, 0, 0)
        names.addView(text("TV Setup", 24, INK, true))
        names.addView(text(t("Phiên bản ", "Version ") + version() + released().let { if (it.isEmpty()) "" else " · $it" } +
            "   ·   Hoang Tran   ·   github.com/HoangTran0410/tv-remote", 12, MUTED, false))
        head.addView(names)
        left.addView(head)

        val status = LinearLayout(this)
        status.gravity = Gravity.CENTER_VERTICAL
        status.background = box(CARD, 16, 0)
        status.setPadding(dp(18), dp(14), dp(18), dp(14))
        dot = View(this)
        status.addView(dot, LinearLayout.LayoutParams(dp(12), dp(12)))
        val st = LinearLayout(this)
        st.orientation = LinearLayout.VERTICAL
        st.setPadding(dp(14), 0, 0, 0)
        statusTitle = text("", 17, INK, true)
        statusDetail = text("", 13, MUTED, false)
        statusDetail.setLineSpacing(dp(2).toFloat(), 1f)
        st.addView(statusTitle)
        st.addView(statusDetail)
        status.addView(st, LinearLayout.LayoutParams(0, -2, 1f))
        left.addView(status, margins(top = 18))

        left.addView(section(t("THAO TÁC", "ACTIONS")), margins(top = 18))
        tsTile = Tile(R.drawable.ic_globe, "Tailscale", t("Dùng khi xa nhà", "Away from home")) { tailscale() }
        updateTile = Tile(R.drawable.ic_download, t("Cập nhật", "Update"), t("Bản ", "Version ") + version()) { updateMenu() }
        tiles = listOf(
            Tile(R.drawable.ic_zap, t("Tối ưu TV", "Optimize TV"), t("Bớt app thừa", "Fewer extra apps")) { chooseOptimize() },
            tsTile,
            Tile(R.drawable.ic_undo, t("Hoàn tác", "Undo"), t("Như trước khi tối ưu", "As before Optimize")) { job { it.undo() } },
            updateTile,
            Tile(R.drawable.ic_code, t("Gỡ lỗi ADB", "Debugging"), t("Tùy chọn nhà phát triển", "Developer options")) { debugMenu() },
            Tile(R.drawable.ic_refresh, t("Kết nối lại", "Reconnect"), t("ADB của TV", "The TV's ADB")) { connect() })
        for (row in tiles.chunked(if (phone) 2 else 3)) {
            val r = LinearLayout(this)
            r.clipChildren = false
            row.forEachIndexed { i, tile -> r.addView(tile.view, LinearLayout.LayoutParams(0, dp(74), 1f).apply { if (i > 0) leftMargin = dp(12) }) }
            left.addView(r, margins(top = 10))
        }

        val actHead = LinearLayout(this)
        actHead.gravity = Gravity.CENTER_VERTICAL
        actHead.addView(section(t("HOẠT ĐỘNG", "ACTIVITY")))
        spinner = ProgressBar(this)
        spinner.isIndeterminate = true
        spinner.visibility = View.INVISIBLE
        actHead.addView(spinner, LinearLayout.LayoutParams(dp(16), dp(16)).apply { leftMargin = dp(10) })
        left.addView(actHead, margins(top = 18))
        logView = text("", 14, INK, false)
        logView.setLineSpacing(dp(3).toFloat(), 1f)
        scroll = ScrollView(this)
        scroll.background = box(CARD, 14, 0)
        scroll.setPadding(dp(16), dp(10), dp(16), dp(10))
        scroll.isFocusable = false
        scroll.addView(logView)
        // ---- right: taking over from a phone or a computer
        webCol = LinearLayout(this)
        webCol.orientation = LinearLayout.VERTICAL
        webCol.background = box(CARD, 18, 0)
        webCol.setPadding(dp(20), dp(18), dp(20), dp(18))
        webBtn = smallButton("") { RemoteService.setEnabled(this, !RemoteService.enabled(this)); showWeb(); webBtn.requestFocus() }
        if (phone) {                                                  // header, status, then the card (what a phone is mostly here for), the tiles, the log
            left.addView(webCol, 2, margins(top = 16))
            scroll.isNestedScrollingEnabled = true
            left.addView(scroll, LinearLayout.LayoutParams(-1, dp(240)).apply { topMargin = dp(8) })
            page.addView(left, LinearLayout.LayoutParams(-1, -2))
            val outer = ScrollView(this)
            outer.setBackgroundColor(BG)
            outer.isFillViewport = true
            outer.addView(page)
            setContentView(outer)
        } else {
            left.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
            page.addView(left, LinearLayout.LayoutParams(0, -1, 1f))
            page.addView(webCol, LinearLayout.LayoutParams(dp(290), -1).apply { leftMargin = dp(24) })
            setContentView(page)
        }

        if (RemoteService.enabled(this)) RemoteService.start(this)
        showWeb()
        connect()
        Thread {                                                      // asks GitHub for the latest release, nothing else is sent
            try {
                val r = Release.newer(version(), "tv", "tv-setup.apk")
                if (r != null) ui { updateTile.say(t("Có bản mới ", "New version ") + r.version + t(" · chọn để cài", " · select to install")) }
            } catch (ignored: Exception) { }                          // offline: stay quiet, the tile still checks on demand
        }.start()
    }

    /** Opened by touch or by another app, nothing has the focus yet: the first arrow key lands on the first tile that works. */
    override fun dispatchKeyEvent(e: android.view.KeyEvent): Boolean {
        val arrow = e.keyCode in intArrayOf(android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT, android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        if (arrow && e.action == android.view.KeyEvent.ACTION_DOWN && (currentFocus == null || window.decorView.isInTouchMode)) {
            (tiles.firstOrNull { it.view.isEnabled } ?: tiles[4]).view.let { it.isFocusableInTouchMode = true; it.requestFocus(); it.isFocusableInTouchMode = false }
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    /** The day this build was made, as dd/mm/yyyy ("" when unknown). */
    private fun released(): String = try {
        getString(resources.getIdentifier("released", "string", packageName)).split("-").let { it[2] + "/" + it[1] + "/" + it[0] }
    } catch (e: Exception) { "" }

    private fun version(): String =
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "0" } catch (e: Exception) { "0" }

    private fun autoText() = if (AutoUpdate.on(this)) t("Tự cập nhật: Bật", "Automatic updates: On") else t("Tự cập nhật: Tắt", "Automatic updates: Off")

    /**
     * Downloads this app's APK from the latest GitHub release and installs it through the TV's own ADB (no "unknown sources"
     * needed), with SetupUpdate's script: it opens this screen again once the new version runs, or the old one if it does not.
     */
    /** The Update tile: update now, automatic updates on or off, and removing this app, kept out of the way here. */
    private fun updateMenu() {
        if (busy) return
        val items = arrayOf(t("Cập nhật ngay", "Update now"), autoText(), t("Gỡ TV Setup", "Remove TV Setup"))
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("TV Setup " + version())
            .setItems(items) { _, i ->
                when (i) {
                    0 -> if (adb == null) log(t("✗ Cần ADB: bật Gỡ lỗi ADB trước.", "✗ Needs ADB: turn on Debugging first.")) else update()
                    1 -> { AutoUpdate.set(this, !AutoUpdate.on(this)); log(autoText()) }
                    2 -> askRemove()
                }
            }.show()
    }

    private fun update() {
        job { _ ->
            log(t("Đang kiểm tra bản mới...", "Checking for a new version..."))
            val r = Release.newer(version(), "tv", "tv-setup.apk")
            if (r == null) { log(t("✓ Đang dùng bản mới nhất (", "✓ This is the newest version (") + version() + ")."); return@job }
            val a = adb ?: throw java.io.IOException("ADB")
            if (SetupUpdate.bad(SetupUpdate.state(a), r.version)) {
                log(t("Bản ", "Version ") + r.version + t(" đã chạy lỗi trên TV này nên TV Setup đã quay về bản đang dùng. Chờ bản sau nhé.",
                    " did not work on this TV, so TV Setup went back to this one. Wait for the next version."))
                return@job
            }
            log(t("Đang tải bản ", "Downloading version ") + r.version + "...")
            val apk = File(getExternalFilesDir("update"), "tv-setup-" + r.version + ".apk")
            var last = 0
            Release.download(r.url, apk, Release.Progress { pct -> if (pct >= last + 25) { last = pct; log("  $pct%") } })
            log(t("Đang cài. TV Setup tự mở lại sau khoảng nửa phút.", "Installing. TV Setup opens again in about half a minute."))
            SetupUpdate.start(a, apk.absolutePath, r.version, true)
        }
    }

    /** How the last update went, said once on this screen when it is recent: a version that was undone is worth knowing about. */
    private fun lastUpdate(a: Adb) {
        val s = SetupUpdate.state(a)
        if (System.currentTimeMillis() - s.optLong("at") > 24 * 3600_000L) return
        val seen = getSharedPreferences("remote", MODE_PRIVATE)
        val key = s.optString("state") + s.optLong("at")
        if (seen.getString("update_seen", "") == key) return
        val line = when (s.optString("state")) {
            "ok" -> if (s.optString("version") == version()) t("✓ Đã cập nhật lên bản ", "✓ Updated to version ") + version() + "." else null
            "rolledback" -> "⚠️ " + t("Bản ", "Version ") + s.optString("version") + t(" chạy lỗi trên TV này nên TV Setup đã tự quay về bản ", " did not work on this TV, so TV Setup went back to version ") +
                s.optString("from") + t(". Hẹn giờ, ghép nối và cài đặt vẫn còn nguyên.", ". Timers, pairings and settings are all still here.")
            "broken" -> "⚠️ " + t("Bản ", "Version ") + s.optString("version") + t(" chạy chưa ổn trên TV này. Nếu có lỗi, cài lại TV Setup từ app TV Remote.", " may not work well on this TV. If something is wrong, reinstall TV Setup from the TV Remote app.")
            "failed" -> t("Chưa cài được bản ", "Could not install version ") + s.optString("version") + ": " + s.optString("detail")
            else -> null
        } ?: return
        seen.edit().putString("update_seen", key).apply()
        log(line)
    }

    override fun onPause() { shown = false; super.onPause() }

    override fun onResume() {
        super.onResume()
        shown = true
        showWeb()
        val s = setup
        if (s != null && !busy) Thread { try { showStatus(s) } catch (ignored: Exception) { } }.start()   // back from Tailscale / Play Store: refresh the IPs
        else if (setup == null && retryOnResume && !connecting) { retryOnResume = false; connect() }   // back from Developer options: ADB may be on now
    }

    override fun onDestroy() {
        val a = adb                                                   // this screen's own connection; RemoteService has its own
        adb = null; setup = null
        Thread { try { a?.close() } catch (ignored: Exception) { } }.start()
        super.onDestroy()
    }

    private val APP_LINK = "https://github.com/HoangTran0410/tv-remote/releases/latest/download/tv-remote.apk"

    /** This TV's address on the home network, read from the network interfaces (ADB not needed). */
    private fun wifiIp(): String = try {
        java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces()).filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") }
            .flatMap { java.util.Collections.list(it.inetAddresses) }.firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress }?.hostAddress ?: ""
    } catch (e: Exception) { "" }

    /** The right-hand card: the web remote's QR code and address, pairing, and the phone app. */
    private fun showWeb() {
        val on = RemoteService.enabled(this)
        val ip = lanIp.ifEmpty { wifiIp() }
        val focused = currentFocus
        webCol.removeAllViews()
        webCol.addView(text(t("Điều khiển từ điện thoại", "Control from a phone"), 18, INK, true))
        if (on && ip.isNotEmpty()) {
            val link = "http://" + ip + ":" + RemoteService.PORT
            qr(link)?.let { webCol.addView(it, LinearLayout.LayoutParams(dp(164), dp(164)).apply { topMargin = dp(14); gravity = Gravity.CENTER_HORIZONTAL }) }
            val addr = text(link.removePrefix("http://"), 16, INK, true)
            addr.gravity = Gravity.CENTER
            webCol.addView(addr, margins(top = 10))
            if (tsIp.isNotEmpty()) {
                val ts = text(t("Ngoài nhà: ", "Away from home: ") + tsIp + ":" + RemoteService.PORT, 13, MUTED, false)
                ts.gravity = Gravity.CENTER
                webCol.addView(ts)
            }
            val n = RemoteService.pairedCount(this)
            webCol.addView(text(t("Đã ghép: $n thiết bị", "Paired: $n " + (if (n == 1) "device" else "devices")), 13, MUTED, false), margins(top = 10))
        } else {
            webCol.addView(text(if (on) t("TV chưa có mạng: nối TV vào Wi-Fi.", "The TV has no network: connect it to Wi-Fi.")
                else t("Đang tắt.", "Off."), 14, MUTED, false), margins(top = 8))
        }
        webBtn.text = if (on) t("Remote qua web: Bật", "Web remote: On") else t("Remote qua web: Tắt", "Web remote: Off")
        (webBtn.parent as? LinearLayout)?.removeView(webBtn)
        val btns = LinearLayout(this)
        btns.addView(webBtn, LinearLayout.LayoutParams(0, -2, 1f))
        if (on) btns.addView(smallButton(t("Hủy ghép", "Unpair all")) {
            RemoteService.forgetAll(this); log(t("✓ Đã hủy ghép. Thiết bị nào muốn điều khiển phải nhập mã mới.", "✓ Unpaired. Any device that wants control must enter a new code.")); showWeb()
        }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        webCol.addView(btns, margins(top = 12))
        rulesLine()?.let { (line, sleeping) ->                       // timers and limits set from a phone or computer (Rules.kt)
            webCol.addView(text(line, 13, INK, false).apply { setLineSpacing(dp(2).toFloat(), 1f) }, margins(top = 14))
            if (sleeping) webCol.addView(smallButton(t("Hủy hẹn giờ tắt", "Cancel the sleep timer")) {
                Rules.change(this, org.json.JSONObject().put("do", "sleep").put("minutes", 0)); showWeb()
            }, margins(top = 8))
        }

        webCol.addView(View(this), if (phone) LinearLayout.LayoutParams(1, dp(16)) else LinearLayout.LayoutParams(1, 0, 1f))
        val app = LinearLayout(this)
        app.gravity = Gravity.CENTER_VERTICAL
        qr(APP_LINK)?.let { app.addView(it, LinearLayout.LayoutParams(dp(72), dp(72))) }
        val words = LinearLayout(this)
        words.orientation = LinearLayout.VERTICAL
        words.setPadding(dp(12), 0, 0, 0)
        words.addView(text(t("App Android TV Remote", "TV Remote Android app"), 14, INK, true))
        words.addView(text(t("Quét để tải", "Scan to download"), 12, MUTED, false))
        app.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        webCol.addView(app)
        if (focused != null && focused.parent == null) webBtn.requestFocus()   // the focused button was rebuilt: keep the focus on this card
    }

    /** One line per timer or limit that is set, and whether the sleep timer is running; null when nothing is set. */
    private fun rulesLine(): Pair<String, Boolean>? {
        val o = Rules.load(this)
        val lines = ArrayList<String>()
        val at = o.optLong("sleep_at")
        val sleeping = at > System.currentTimeMillis()
        if (sleeping) lines.add("😴 " + t("TV tự tắt lúc ", "The TV turns off at ") + java.text.SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(at)))
        o.optJSONObject("bedtime")?.let { if (it.optBoolean("on")) lines.add("🌙 " + t("Giờ đi ngủ ", "Bedtime ") + it.optString("from") + " – " + it.optString("to")) }
        val n = o.optJSONObject("limits")?.length() ?: 0
        if (n > 0) lines.add("⏳ " + t("Giới hạn giờ dùng: $n app", "Daily limits: $n " + (if (n == 1) "app" else "apps")))
        return if (lines.isEmpty()) null else Pair(lines.joinToString("\n"), sleeping)
    }

    private fun qr(link: String): View? {
        val bmp = dev.tvremote.core.Qr.bitmap(link, 6) ?: return null
        val img = ImageView(this)
        img.setImageBitmap(bmp)
        img.background = box(Color.WHITE, 10, 0)
        img.setPadding(dp(6), dp(6), dp(6), dp(6))
        return img
    }

    @Volatile private var connecting = false

    private fun connect() {
        setEnabled(false)
        connecting = true
        setStatus(WARN, t("Đang kết nối ADB...", "Connecting ADB..."), "")
        Thread {
            try {
                adb?.close()
                val a = Adb(filesDir)
                a.connect("127.0.0.1", 5555, Adb.Prompt {
                    ui { setStatus(WARN, t("TV đang hỏi “Cho phép gỡ lỗi USB?”", "The TV is asking “Allow USB debugging?”"),
                        t("Tích “Luôn cho phép”, rồi chọn OK.", "Tick “Always allow”, then choose OK.")) }
                })
                adb = a
                val s = Setup(a, getExternalFilesDir(null), vi, Setup.Log { log(it) })
                setup = s
                try { lastUpdate(a) } catch (ignored: Exception) { }
                showStatus(s)
                connecting = false
                ui { setEnabled(true) }
            } catch (e: Exception) {
                adb = null; setup = null
                connecting = false
                ui {
                    setStatus(BAD, t("Chưa kết nối được ADB", "No ADB connection"), t("Bật “Gỡ lỗi ADB” rồi chọn Kết nối lại.", "Turn on “ADB debugging”, then choose Reconnect."))
                    log("✗ " + reason(e))
                    log(t("Cần bật “Gỡ lỗi ADB”: Cài đặt → Tùy chọn thiết bị → Giới thiệu → bấm OK 7 lần vào “Bản dựng”, rồi Tùy chọn nhà phát triển → bật “Gỡ lỗi ADB” (hoặc “Gỡ lỗi USB”).",
                        "Turn on “ADB debugging”: Settings → Device Preferences → About → press OK 7 times on “Build”, then Developer options → “ADB debugging” (or “USB debugging”)."))
                    log(t("Bật xong quay lại đây, chọn Kết nối lại. TV hỏi thì chọn “Luôn cho phép”.", "Then come back here and choose Reconnect. If the TV asks, choose “Always allow”."))
                    tiles[5].enable(true)
                    tiles[5].view.requestFocus()
                    val off = (e.message ?: "").lowercase().let { it.contains("refused") }   // ADB is off; not: the person said no to the prompt
                    if (!openedDev && off) {                          // once: straight to where ADB is turned on, no hunting through menus
                        openedDev = true
                        log(t("Đang mở Tùy chọn nhà phát triển để bật Gỡ lỗi USB...", "Opening Developer options to turn on USB debugging..."))
                        devOptions()
                    }
                }
            }
        }.start()
    }

    @Throws(Exception::class)
    private fun showStatus(setup: Setup) {
        val name = setup.describe()
        val ts = setup.tailscaleIp()
        val lan = setup.lanIp()
        val tsInstalled = setup.has(Setup.TS_PKG)
        lanIp = lan; tsIp = ts
        ui {
            showWeb()
            setStatus(OK, t("Đã kết nối ADB", "ADB connected"), name + "\n" + t("Trong nhà ", "Home network ") + lan.ifEmpty { "?" } + "    ·    Tailscale " +
                (if (ts.isNotEmpty()) ts else if (tsInstalled) t("chưa đăng nhập", "signed out") else t("chưa cài", "not installed")))
            tsTile.say(if (ts.isNotEmpty()) t("Đã vào · ", "Connected · ") + ts else if (tsInstalled) t("Chưa đăng nhập · chọn để vào", "Signed out · select to sign in")
                else t("Chưa cài · chọn để cài", "Not installed · select to install"))
        }
    }

    private fun setStatus(color: Int, title: String, detail: String) {
        dot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        statusTitle.text = title
        statusTitle.setTextColor(if (color == OK) INK else color)
        statusDetail.text = detail
        statusDetail.visibility = if (detail.isEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * Opens the TV's Developer options straight away. Some operator boxes hide Settings on their home screen, or ask for a
     * password of their own when "Build" is pressed seven times; the screen itself often opens when asked for by name.
     */
    private var openedDev = false
    private var retryOnResume = false                                // one more try when the person comes back from the screen we sent them to

    private fun keepText() = if (KeepAdb.on(this)) t("✓ Tự bật lại khi bị tắt", "✓ Turn back on when switched off")
        else t("Tự bật lại khi bị tắt: đang tắt", "Turn back on when switched off: off")

    /** Developer options, and whether TV Setup turns ADB debugging back on (KeepAdb) when someone switches it off. */
    private fun debugMenu() {
        val items = arrayOf(t("Mở Tùy chọn nhà phát triển", "Open Developer options"), keepText())
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(t("Gỡ lỗi ADB", "Debugging"))
            .setItems(items) { _, i ->
                when (i) {
                    0 -> devOptions()
                    1 -> { KeepAdb.set(this, !KeepAdb.on(this)); log(keepText()) }
                }
            }.show()
    }

    private fun devOptions() {
        retryOnResume = true
        val dev = t("Tùy chọn nhà phát triển", "Developer options")
        val tries = listOf(
            dev to android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            dev to android.content.Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.system.development.DevelopmentActivity"),
            dev to android.content.Intent().setClassName("com.android.settings", "com.android.settings.DevelopmentSettings"),
            t("Giới thiệu (bấm OK 7 lần vào “Bản dựng”)", "About (press OK 7 times on “Build”)") to android.content.Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS),
            t("Cài đặt", "Settings") to android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
        fun attempt(i: Int) {
            if (i >= tries.size) { log(t("✗ TV này không cho mở Cài đặt từ app.", "✗ This TV does not let apps open its Settings.")); return }
            val (name, intent) = tries[i]
            try {
                startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {                                  // not on this TV, or not allowed: the next one
                attempt(i + 1)
                return
            }
            // a screen that is not allowed to open closes itself at once and leaves us in front: then try the next one
            window.decorView.postDelayed({
                if (hasWindowFocus()) attempt(i + 1)
                else log(t("Đã mở: ", "Opened: ") + name + t(". Bật “Gỡ lỗi USB” (hoặc “Gỡ lỗi ADB”), quay lại đây rồi chọn Kết nối lại.", ". Turn on “USB debugging” (or “ADB debugging”), come back here and choose Reconnect."))
            }, 1200)
        }
        attempt(0)
    }

    private fun interface Job { @Throws(Exception::class) fun run(setup: Setup) }

    /** Runs one piece of work on the ADB connection in the background, tiles greyed out and a spinner meanwhile. */
    /** "Optimize TV": first what this TV has, then the person ticks what to do (suggested apps ticked, other apps not). */
    private fun chooseOptimize() {
        val s = setup ?: return
        if (busy) return
        log(t("Đang xem TV có gì để tối ưu...", "Looking at what this TV has..."))
        Thread {
            val plan = try {
                s.plan { pkg -> try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { null } }
            } catch (e: Exception) { ui { log("✗ " + reason(e)) }; return@Thread }
            ui { showOptimize(plan) }
        }.start()
    }

    /** The choices, in this app's own look: rows to tick with the remote's OK, sections, and the two buttons at the bottom. */
    private fun showOptimize(plan: org.json.JSONObject) {
        val d = android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        val root = android.widget.FrameLayout(this)
        root.setBackgroundColor(Color.argb(215, 5, 7, 10))
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = box(CARD, 22, 0)
        card.setPadding(dp(32), dp(26), dp(32), dp(22))

        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val badge = ImageView(this)
        badge.setImageResource(R.drawable.ic_zap)
        badge.setColorFilter(Color.WHITE)
        badge.background = box(ACCENT, 12, 0)
        badge.setPadding(dp(9), dp(9), dp(9), dp(9))
        head.addView(badge, LinearLayout.LayoutParams(dp(44), dp(44)))
        val words = LinearLayout(this)
        words.orientation = LinearLayout.VERTICAL
        words.setPadding(dp(14), 0, 0, 0)
        words.addView(text(t("Tối ưu TV", "Optimize TV"), 22, INK, true))
        words.addView(text(t("Tắt app chỉ là tạm ngưng; Hoàn tác bật lại hết.", "Turning an app off only pauses it; Undo turns all back on."), 13, MUTED, false))
        head.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(head)

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val rows = ArrayList<Pair<String?, BooleanArray>>()             // (package or null, ticked)
        val count = text("", 13, MUTED, false)
        fun recount() {
            val n = rows.count { it.first != null && it.second[0] }
            count.text = if (n == 0) t("Không tắt app nào", "No apps turned off") else t("Sẽ tắt $n app", "$n apps will be turned off")
        }
        fun row(title: String, sub: String, on: Boolean, pkg: String?): View {
            val ticked = booleanArrayOf(on)
            rows.add(pkg to ticked)
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(14), dp(10), dp(14), dp(10))
            r.isFocusable = true
            r.isClickable = true
            val mark = TextView(this)
            mark.gravity = Gravity.CENTER
            mark.textSize = 16f
            mark.typeface = Typeface.DEFAULT_BOLD
            r.addView(mark, LinearLayout.LayoutParams(dp(26), dp(26)))
            val w = LinearLayout(this)
            w.orientation = LinearLayout.VERTICAL
            w.setPadding(dp(14), 0, 0, 0)
            val tt = text(title, 16, INK, true)
            tt.isSingleLine = true
            tt.ellipsize = TextUtils.TruncateAt.END
            w.addView(tt)
            val st = text(sub, 12, MUTED, false)
            st.isSingleLine = true
            st.ellipsize = TextUtils.TruncateAt.END
            if (sub.isNotEmpty()) w.addView(st)
            r.addView(w, LinearLayout.LayoutParams(0, -2, 1f))
            fun paint() {
                val f = r.isFocused
                r.background = if (f) box(ACCENT, 12, 2) else box(TILE, 12, 0)
                mark.text = if (ticked[0]) "✓" else ""
                mark.setTextColor(if (f) ACCENT else Color.WHITE)
                mark.background = if (ticked[0]) box(if (f) Color.WHITE else ACCENT, 7, 0) else GradientDrawable().apply { cornerRadius = dp(7).toFloat(); setStroke(dp(2), if (f) Color.WHITE else MUTED) }
                st.setTextColor(if (f) Color.WHITE else MUTED)
            }
            r.setOnClickListener { ticked[0] = !ticked[0]; paint(); recount() }
            r.setOnFocusChangeListener { _, _ -> paint() }
            paint()
            return r
        }
        fun add(v: View) = list.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        fun header(s: String) = list.addView(section(s), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        val usingFl = plan.optString("home") == Setup.FL_PKG
        header(t("CHUNG", "GENERAL"))
        add(row(t("Tắt hiệu ứng chuyển cảnh", "Turn off transition animations"),
            if (plan.optBoolean("anim", true)) t("Nhanh hơn", "Faster") else t("Đang tắt sẵn", "Already off"), true, null))
        add(row(if (usingFl) t("Màn hình chính FLauncher (đang dùng)", "FLauncher home screen (in use)") else t("Dùng FLauncher làm màn hình chính", "Use FLauncher as the home screen"),
            t("Nhẹ, không quảng cáo", "Light, no ads") + (if (plan.optBoolean("flauncher")) "" else t(" · tải 10 MB", " · 10 MB download")), true, null))
        val sug = plan.optJSONArray("suggest") ?: org.json.JSONArray()
        if (sug.length() > 0) header(t("TẮT APP ĐỀ XUẤT", "SUGGESTED APPS TO TURN OFF"))
        for (i in 0 until sug.length()) { val a = sug.getJSONObject(i); add(row(a.optString("label"), a.optString("pkg"), true, a.optString("pkg"))) }
        val more = plan.optJSONArray("apps") ?: org.json.JSONArray()
        if (more.length() > 0) header(t("TẮT THÊM APP KHÁC", "TURN OFF OTHER APPS"))
        for (i in 0 until more.length()) { val a = more.getJSONObject(i); add(row(a.optString("label"), a.optString("pkg"), false, a.optString("pkg"))) }
        recount()

        val most = (resources.displayMetrics.heightPixels * 0.62).toInt()  // the list grows with its rows, then scrolls
        val scroll = object : ScrollView(this) {
            override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(most, View.MeasureSpec.AT_MOST))
        }
        scroll.addView(list)
        card.addView(scroll, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        val foot = LinearLayout(this)
        foot.gravity = Gravity.CENTER_VERTICAL
        foot.setPadding(0, dp(18), 0, 0)
        val go = smallButton(t("⚡  Chạy tối ưu", "⚡  Optimize")) {
            val ticks = rows.map { it.second[0] }
            d.dismiss()
            job { it.apply(Setup.Choice(ticks[0], ticks[1], rows.filter { r -> r.first != null && r.second[0] }.map { r -> r.first!! })) }
        }
        go.textSize = 16f
        go.setPadding(dp(22), dp(12), dp(22), dp(12))
        go.id = View.generateViewId()
        go.background = box(Color.rgb(36, 58, 108), 10, 0)                   // dim until focused: only one button ever looks "on"
        go.setOnFocusChangeListener { v, f -> v.background = if (f) box(ACCENT, 10, 2) else box(Color.rgb(36, 58, 108), 10, 0) }
        list.getChildAt(list.childCount - 1)?.nextFocusDownId = go.id
        val cancel = smallButton(t("Hủy", "Cancel")) { d.dismiss() }
        cancel.textSize = 16f
        cancel.setPadding(dp(22), dp(12), dp(22), dp(12))
        foot.addView(go)
        foot.addView(cancel, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
        foot.addView(count, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(18) })
        card.addView(foot)

        root.addView(card, android.widget.FrameLayout.LayoutParams(dp(820), -2, Gravity.CENTER))
        d.setContentView(root)
        d.show()
        list.getChildAt(1)?.requestFocus()                                // the first choice, under its heading
    }

    /** Removing this app leaves the TV as it was before it: apps Optimize TV turned off come back on first (Undo). */
    private fun askRemove() {
        if (busy) return
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(t("Gỡ TV Setup?", "Remove TV Setup?"))
            .setMessage(t("App đã tắt bằng Tối ưu TV sẽ được bật lại.", "Apps turned off by Optimize TV come back on."))
            .setPositiveButton(t("Gỡ", "Remove")) { _, _ ->
                val s = setup
                val a = adb
                if (s == null || a == null) {                         // no ADB here: Android's own way
                    try { startActivity(android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:$packageName"))) } catch (e: Exception) { log("✗ " + reason(e)) }
                    return@setPositiveButton
                }
                busy = true
                spinner.visibility = View.VISIBLE
                log(t("Đang gỡ TV Setup...", "Removing TV Setup..."))
                Thread {
                    try {
                        if (s.optimized()) s.undo()
                        a.shell("pm uninstall $packageName 2>&1")              // the end of this app: nothing after this runs
                    } catch (e: Exception) { ui { log("✗ " + reason(e)) } }
                    busy = false
                    ui { spinner.visibility = View.INVISIBLE }
                }.start()
            }
            .setNegativeButton(t("Hủy", "Cancel"), null).show()
    }

    private fun job(job: Job) {
        val s = setup
        if (s == null || busy) return
        busy = true
        val running = currentFocus
        tiles.forEach { it.view.alpha = if (it.view === running) 1f else 0.4f; if (it.view === running) it.work(true) }   // focus stays where it is; the others wait
        spinner.visibility = View.VISIBLE
        Thread {
            try {
                job.run(s)
                showStatus(s)
            } catch (e: Exception) {
                log("✗ " + reason(e))
            }
            busy = false
            ui { tiles.forEach { it.work(false) }; setEnabled(true); spinner.visibility = View.INVISIBLE }
        }.start()
    }

    /**
     * One press: installs the official Tailscale when missing (through this TV's own ADB, no Play Store), lets it make its VPN
     * without asking, switches it on, and when it still has to be signed in, leaves its sign-in screen up: a QR code that a phone
     * signed in to Tailscale scans to add the TV. Nobody types an account on the TV.
     */
    private fun tailscale() {
        job { _ ->
            val ts = Tailscale(adb ?: return@job, getExternalFilesDir(null))
            if (!ts.installed()) log(t("Đang tải và cài Tailscale bản chính thức (khoảng 100 MB)...", "Downloading and installing the official Tailscale (about 100 MB)..."))
            val url = ts.start { what, pct ->
                ui { tsTile.progress(when (what) {
                    "download" -> t("Đang tải ", "Downloading ") + ((pct - 5) * 100 / 55).coerceIn(0, 100) + "%"
                    "install" -> t("Đang cài...", "Installing...")
                    else -> t("Đang bật...", "Switching on...") }) }
                if (what == "start") log(t("Đang bật Tailscale...", "Switching Tailscale on..."))
            }
            if (url.isEmpty()) log(t("✓ TV đã vào Tailscale: ", "✓ The TV is in Tailscale: ") + ts.ip() + t(". Ở ngoài nhà dùng IP này (máy điều khiển cũng bật Tailscale, cùng tài khoản).", ". Away from home use this IP (the controlling device needs Tailscale too, same account)."))
            else log(t("Tailscale đang hiện mã QR: quét bằng điện thoại đã đăng nhập Tailscale rồi bấm Connect (hoặc mở ", "Tailscale shows a QR code: scan it with a phone signed in to Tailscale and press Connect (or open ") + url +
                t(" trên máy tính). Xong thì nên vào login.tailscale.com/admin/machines, menu ⋯ của TV → Disable key expiry.", " on a computer). Afterwards, at login.tailscale.com/admin/machines, open the TV's ⋯ menu → Disable key expiry."))
        }
    }

    private fun log(line: String) {
        ui {
            logView.append(line + "\n")
            if (logView.lineCount > 300) logView.text = logView.text.toString().lines().takeLast(200).joinToString("\n")
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun reason(e: Exception): String = dev.tvremote.core.Errors.say(e, vi)

    private fun ui(r: Runnable) { runOnUiThread(r) }

    /** Tiles that need ADB follow it; "Developer options" always works. Focus stays where it was, or goes to a tile that works. */
    private fun setEnabled(on: Boolean) {
        val had = currentFocus
        tiles.forEachIndexed { i, tile -> tile.enable(on || i == 3 || i == 4) }   // without ADB: Debugging to turn it on; Update for its menu (removing works without ADB)
        if (on && !focusedOnce) { focusedOnce = true; tiles[0].view.requestFocus() }   // ready for the first time: start on the first action
        else if (had == null || !had.isEnabled) (if (on) tiles[0] else tiles[4]).view.requestFocus()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun margins(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }

    private fun box(color: Int, radius: Int, stroke: Int): GradientDrawable {
        val d = GradientDrawable()
        d.cornerRadius = dp(radius).toFloat()
        d.setColor(color)
        if (stroke > 0) d.setStroke(dp(stroke), Color.WHITE)
        return d
    }

    private fun section(s: String): TextView {
        val v = text(s, 12, MUTED, true)
        v.letterSpacing = 0.08f
        return v
    }

    private fun text(s: String, sp: Int, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = sp.toFloat()
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    /** The smaller buttons of the right-hand card; the focused one is unmistakable on a TV. */
    private fun smallButton(label: String, l: View.OnClickListener): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 14f
        b.isAllCaps = false
        b.setOnClickListener(l)
        b.isFocusable = true
        b.setTextColor(Color.WHITE)
        b.background = box(TILE, 10, 0)
        b.setOnFocusChangeListener { v, f -> v.background = if (f) box(ACCENT, 10, 2) else box(TILE, 10, 0) }
        b.minHeight = 0
        b.minimumHeight = 0
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(dp(12), dp(10), dp(12), dp(10))
        return b
    }
}
