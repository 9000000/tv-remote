package dev.tvremote.setup

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvremote.core.Adb
import dev.tvremote.update.Release
import java.io.File
import kotlin.math.roundToInt

/** One screen, driven with the TV remote: connect to the TV's own ADB, then optimise / undo / set up Tailscale. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var buttons: Array<Button>
    private lateinit var webBtn: Button
    private lateinit var webInfo: TextView
    private var lanIp = ""
    private var tsIp = ""
    private var adb: Adb? = null
    private var setup: Setup? = null
    private var busy = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val pad = dp(32)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)
        root.setBackgroundColor(Color.rgb(17, 20, 24))

        val title = text("TV Setup", 30, Color.WHITE)
        title.typeface = Typeface.DEFAULT_BOLD
        root.addView(title)
        root.addView(text("Bản " + version() + "  ·  Tác giả: Hoang Tran (@HoangTran0410)  ·  github.com/HoangTran0410/tv-remote", 14, Color.rgb(120, 130, 143)))
        status = text("Đang kết nối ADB...", 18, Color.rgb(153, 163, 176))
        status.setPadding(0, dp(6), 0, dp(18))
        root.addView(status)

        val row = FlowRow(this, dp(12))
        buttons = arrayOf(
            button("⚡ Tối ưu TV") { job { it.apply() } },
            button("Hoàn tác") { job { it.undo() } },
            button("Tailscale") { tailscale() },
            button("Kết nối lại") { connect() },
            button("Cập nhật") { update() })
        for (b in buttons) row.addView(b)
        root.addView(row)

        // the web remote served by this TV: any phone or computer opens http://<TV's IP>:8765 and pairs with a code
        val web = FlowRow(this, dp(12))
        web.setPadding(0, dp(12), 0, 0)
        webBtn = button("") { RemoteService.setEnabled(this, !RemoteService.enabled(this)); showWeb() }
        web.addView(webBtn)
        web.addView(button("Hủy ghép mọi thiết bị") { RemoteService.forgetAll(this); log("✓ Đã hủy ghép. Thiết bị nào muốn điều khiển phải nhập mã mới."); showWeb() })
        root.addView(web)
        webInfo = text("", 17, Color.rgb(233, 236, 241))
        webInfo.setPadding(0, dp(10), 0, 0)
        root.addView(webInfo)
        if (RemoteService.enabled(this)) RemoteService.start(this)
        showWeb()

        logView = text("", 17, Color.rgb(233, 236, 241))
        logView.setLineSpacing(dp(4).toFloat(), 1f)
        scroll = ScrollView(this)
        scroll.setPadding(0, dp(18), 0, 0)
        scroll.addView(logView)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        log("Cần bật trước: Cài đặt → Tùy chọn thiết bị → Giới thiệu → bấm OK 7 lần vào \"Bản dựng\",")
        log("rồi Tùy chọn nhà phát triển → bật \"Gỡ lỗi ADB\" (hoặc \"Gỡ lỗi USB\").")
        connect()
        Thread {                                                      // asks GitHub for the latest release, nothing else is sent
            try {
                val r = Release.newer(version(), "tv", "tv-setup.apk")
                if (r != null) { log("🆕 Có bản TV Setup mới " + r.version + " (đang dùng " + version() + "). Chọn \"Cập nhật\" để cài."); ui { buttons[4].text = "🆕 Cập nhật " + r.version } }
            } catch (ignored: Exception) { }                          // offline: stay quiet, the button still checks on demand
        }.start()
    }

    private fun version(): String =
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "0" } catch (e: Exception) { "0" }

    /** Downloads this app's APK from the latest GitHub release and installs it through the TV's own ADB (no "unknown sources" needed). */
    private fun update() {
        job { setup ->
            log("Đang kiểm tra bản mới...")
            val r = Release.newer(version(), "tv", "tv-setup.apk")
            if (r == null) { log("✓ Đang dùng bản mới nhất (" + version() + ")."); return@job }
            log("Đang tải bản " + r.version + "...")
            val apk = File(getExternalFilesDir(null), "tv-setup-update.apk")
            var last = 0
            Release.download(r.url, apk, Release.Progress { pct -> if (pct >= last + 25) { last = pct; log("  $pct%") } })
            log("Đang cài. App sẽ tự đóng; vài giây sau mở lại TV Setup là bản " + r.version + ".")
            log(setup.install(apk))
        }
    }

    override fun onResume() {
        super.onResume()
        showWeb()
        if (setup != null && !busy) job { showStatus(it) }            // back from Tailscale / Play Store: refresh the IPs
    }

    private fun showWeb() {
        val on = RemoteService.enabled(this)
        webBtn.text = if (on) "Remote qua web: ĐANG BẬT" else "Remote qua web: đang tắt"
        if (!on) { webInfo.text = "Bật \"Remote qua web\" để điều khiển TV này từ trình duyệt của điện thoại hay máy tính, không cần cài gì."; return }
        val where = if (lanIp.isEmpty() && tsIp.isEmpty()) "http://<IP của TV>:" + RemoteService.PORT
        else (if (lanIp.isEmpty()) "" else "http://" + lanIp + ":" + RemoteService.PORT + "  (cùng Wi-Fi)") + (if (lanIp.isEmpty() || tsIp.isEmpty()) "" else "     hoặc     ") +
            (if (tsIp.isEmpty()) "" else "http://" + tsIp + ":" + RemoteService.PORT + "  (qua Tailscale)")
        val n = RemoteService.pairedCount(this)
        webInfo.text = "Mở trên điện thoại / máy tính:   $where\nLần đầu TV sẽ hiện mã 6 số để ghép.   Đã ghép: $n thiết bị.   Tự chạy lại khi TV khởi động."
    }

    private fun connect() {
        setEnabled(false)
        Thread {
            try {
                adb?.close()
                val a = Adb(filesDir)
                a.connect("127.0.0.1", 5555, Adb.Prompt {
                    ui {
                        status.text = "TV đang hỏi \"Cho phép gỡ lỗi USB?\" → tích \"Luôn cho phép\" rồi chọn OK"
                        status.setTextColor(Color.rgb(240, 179, 74))
                    }
                })
                adb = a
                val s = Setup(a, getExternalFilesDir(null), Setup.Log { log(it) })
                setup = s
                showStatus(s)
                ui { setEnabled(true) }
            } catch (e: Exception) {
                adb = null; setup = null
                ui {
                    status.text = "Chưa kết nối được ADB"
                    status.setTextColor(Color.rgb(255, 112, 112))
                    log("✗ " + reason(e))
                    log("Kiểm tra đã bật \"Gỡ lỗi ADB\" chưa, rồi chọn \"Kết nối lại\". Nếu TV có hỏi thì phải chọn \"Luôn cho phép\".")
                    buttons[3].isEnabled = true
                    buttons[3].requestFocus()
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
            status.setTextColor(Color.rgb(70, 210, 131))
            status.text = name + "\nIP trong nhà: " + (if (lan.isEmpty()) "?" else lan) + "   ·   IP Tailscale: " +
                (if (ts.isNotEmpty()) ts else if (tsInstalled) "chưa vào mạng" else "chưa cài")
        }
    }

    private fun interface Job { @Throws(Exception::class) fun run(setup: Setup) }

    /** Runs one piece of work on the ADB connection in the background, buttons greyed out meanwhile. */
    private fun job(job: Job) {
        val s = setup
        if (s == null || busy) return
        busy = true
        setEnabled(false)
        Thread {
            try {
                job.run(s)
                showStatus(s)
            } catch (e: Exception) {
                log("✗ " + reason(e))
            }
            busy = false
            ui { setEnabled(true) }
        }.start()
    }

    /** Installed: open it so it shows its code, which the person enters in the Tailscale admin console. Not installed: its Play Store page. */
    private fun tailscale() {
        job { setup ->
            if (setup.has(Setup.TS_PKG)) {
                val i = packageManager.getLeanbackLaunchIntentForPackage(Setup.TS_PKG) ?: packageManager.getLaunchIntentForPackage(Setup.TS_PKG)
                if (i == null) { log("✗ Không mở được Tailscale"); return@job }
                log("Mở Tailscale: TV hiện một mã. Không cần gõ tài khoản trên TV: trên máy tính/điện thoại mở login.tailscale.com/admin/machines, chọn Add device rồi nhập mã đó (hoặc quét mã QR). Xong thì vào menu của TV trong trang đó, chọn Disable key expiry.")
                startActivity(i)
            } else {
                log("Mở Google Play: chọn Cài đặt (Install), xong quay lại đây bấm \"Tailscale\" lần nữa để lấy mã thêm TV.")
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Setup.TS_PKG)))
                } catch (e: Exception) {
                    log("✗ TV này không có Google Play. Cài Tailscale từ máy tính bằng deploy/tv-new.")
                }
            }
        }
    }

    private fun log(line: String) {
        ui {
            logView.append(line + "\n")
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun reason(e: Exception): String {
        val m = e.message
        return e.javaClass.simpleName + (if (m == null) "" else ": $m")
    }

    private fun ui(r: Runnable) { runOnUiThread(r) }

    private fun setEnabled(on: Boolean) {
        val had = currentFocus
        for (b in buttons) { b.isEnabled = on; b.alpha = if (on) 1f else 0.45f }
        if (on && had !is Button) buttons[0].requestFocus()
        else if (on && had != null) had.requestFocus()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun text(s: String, sp: Int, color: Int): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = sp.toFloat()
        t.setTextColor(color)
        return t
    }

    /** The stock button barely shows focus on a TV; make the focused one unmistakable. */
    private fun background(focused: Boolean): GradientDrawable {
        val d = GradientDrawable()
        d.cornerRadius = dp(10).toFloat()
        d.setColor(if (focused) Color.rgb(47, 109, 246) else Color.rgb(39, 45, 53))
        if (focused) d.setStroke(dp(3), Color.WHITE)
        return d
    }

    private fun button(label: String, l: View.OnClickListener): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 18f
        b.isAllCaps = false
        b.setOnClickListener(l)
        b.isFocusable = true
        b.isFocusableInTouchMode = true                               // keep D-pad focus even after a mouse / touch event
        b.setTextColor(Color.WHITE)
        b.background = background(false)
        b.setOnFocusChangeListener { v, focused -> v.background = background(focused) }
        b.gravity = Gravity.CENTER
        b.layoutParams = ViewGroup.LayoutParams(-2, -2)             // spacing comes from the FlowRow it sits in
        b.setPadding(dp(22), dp(12), dp(22), dp(12))
        return b
    }
}
