package dev.tvremote.phone

import android.app.AlertDialog
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import dev.tvremote.core.Invite
import dev.tvremote.core.Qr
import dev.tvremote.core.SetupNeed
import org.json.JSONObject

// ⋯ More keys → Chia sẻ, as shareDialog() on the page: a QR that opens this TV's web page already paired (a one-time invite from
// TV Setup, Invite.kt), and one that downloads this app. Without TV Setup, or with an older one, only the app's.

private const val APP_APK = "https://github.com/HoangTran0410/tv-remote/releases/latest/download/tv-remote.apk"

internal fun MainActivity.shareDialog() {
    if (needsAdb()) return
    val tv = tv
    if (tv == null || !tv.ready()) { say(t("Đang kết nối TV, thử lại sau giây lát.", "Still connecting to the TV, try again in a moment."), false); return }
    val at = host ?: return
    Thread {
        val r = try { Invite.share({ tv.shell(it) }, at) } catch (e: Exception) { JSONObject().put("error", e.message ?: "") }
        ui.post { if (!isFinishing) showShare(r) }
    }.start()
}

private fun MainActivity.showShare(r: JSONObject) {
    val body = col()
    body.setPadding(dp(24), dp(8), dp(24), dp(20))
    val warn = when (r.optString("warn")) {
        "tailscale" -> t("Máy này đang nối TV qua Tailscale: điện thoại quét phải ở cùng Wi-Fi với TV.", "This device reaches the TV through Tailscale: the phone that scans must be on the TV's Wi-Fi.")
        "tailscale_only" -> t("TV không có địa chỉ Wi-Fi: điện thoại quét cần bật Tailscale.", "The TV has no Wi-Fi address: the phone that scans needs Tailscale on.")
        else -> null
    }
    if (warn != null) body.addView(label(warn, 14, ERR_TEXT), gap(4))
    val url = r.optString("url")
    if (url.isNotEmpty()) qr(body, url, t("Mở remote", "Open the remote"), t("Quét để điều khiển TV này, không cần mã. Dùng 1 lần, trong 10 phút.", "Scan to control this TV, no code needed. Once, within 10 minutes."))
    else when (r.optString("setup")) {
        "missing" -> body.addView(label(t("TV chưa có TV Setup: chỉ chia sẻ được app.", "The TV has no TV Setup: only the app can be shared."), 14, MUTED), gap(4))
        "old" -> {
            val up = button("@upload|" + t("Cập nhật TV Setup để chia sẻ remote", "Update TV Setup to share the remote"), BTN)
            up.setOnClickListener { offerSetup(SetupNeed.Missing("invite", r.optString("have"))) }
            body.addView(up, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(4) })
        }
        else -> body.addView(label(t("Không lấy được mã từ TV Setup: ", "TV Setup gave no code: ") + r.optString("error"), 14, ERR_TEXT), gap(4))
    }
    qr(body, APP_APK, t("App Android", "Android app"), t("Quét để tải app.", "Scan to get the app."))
    val scroll = android.widget.ScrollView(this)
    scroll.addView(body)
    AlertDialog.Builder(this).setTitle(t("Chia sẻ", "Share")).setView(scroll).setPositiveButton(t("Xong", "Done"), null).show()
}

private fun MainActivity.qr(box: LinearLayout, link: String, title: String, note: String) {
    val bmp = Qr.bitmap(link, 6) ?: return
    val img = ImageView(this)
    img.setImageBitmap(bmp)
    img.contentDescription = title
    box.addView(img, LinearLayout.LayoutParams(dp(190), dp(190)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(14) })
    val name = label(title, 15, TEXT)
    name.typeface = android.graphics.Typeface.DEFAULT_BOLD
    name.gravity = Gravity.CENTER
    box.addView(name, gap(8))
    val line = label(note, 13, MUTED)
    line.gravity = Gravity.CENTER
    box.addView(line, gap(2))
}
