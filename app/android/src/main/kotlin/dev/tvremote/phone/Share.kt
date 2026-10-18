package dev.tvremote.phone

import dev.tvremote.core.T
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import dev.tvremote.core.web.Invite
import dev.tvremote.core.web.Qr
import dev.tvremote.core.ontv.SetupNeed
import org.json.JSONObject

// ⋯ More keys → Chia sẻ, as shareDialog() on the page, in two tabs: the TV, a QR that opens its web page already paired (a
// one-time invite from TV Setup, Invite.kt); the app, a QR that downloads it. Without TV Setup, or with an older one, only the app's. The invite
// carries what the one who scans may do (Scope.kt in TV Setup); another choice makes a new QR, as one invite is out at a time.

private const val APP_APK = "https://github.com/HoangTran0410/tv-remote/releases/latest/download/tv-remote.apk"

internal fun MainActivity.shareDialog() {
    if (needsAdb()) return
    val tv = tv
    if (tv == null || !tv.ready()) { say(T("share.still_connecting_to_the"), false); return }
    val at = host ?: return
    val ask = { scope: String, then: (JSONObject) -> Unit ->
        Thread {
            val r = try { Invite.share({ tv.shell(it) }, at, scope) } catch (e: Exception) { JSONObject().put("error", e.message ?: "") }
            ui.post { if (!isFinishing) then(r) }
        }.start()
    }
    ask("remote") { showShare(it, ask) }                              // what a guest on the sofa needs; the owner can pick more
}

private fun MainActivity.showShare(r: JSONObject, ask: (String, (JSONObject) -> Unit) -> Unit) {
    val body = col()
    body.setPadding(dp(24), dp(8), dp(24), dp(20))
    // two tabs: the TV (a pass, with what the guest may do) and the app (only its download); the TV first, it is why one shares
    val tvPane = col()
    val appPane = col()
    val tabs = LinearLayout(this)
    val tabTv = button("@tv|" + T("share.share_the_tv"), BTN)
    val tabApp = button("@phone|" + T("share.share_the_app"), BTN)
    val pick = { onTv: Boolean ->
        tvPane.visibility = if (onTv) android.view.View.VISIBLE else android.view.View.GONE
        appPane.visibility = if (onTv) android.view.View.GONE else android.view.View.VISIBLE
        for ((b, on) in listOf(tabTv to onTv, tabApp to !onTv)) b.setOn(on)
    }
    tabTv.setOnClickListener { pick(true) }
    tabApp.setOnClickListener { pick(false) }
    tabs.addView(tabTv, cell(44, false))
    tabs.addView(tabApp, cell(44, true))
    body.addView(tabs, gap(4))
    body.addView(tvPane)
    body.addView(appPane)
    val warn = when (r.optString("warn")) {
        "tailscale" -> T("share.this_device_reaches_the")
        "tailscale_only" -> T("setup.warn_tailscale_only")
        else -> null
    }
    if (warn != null) tvPane.addView(label(warn, 14, ERR_TEXT), gap(12))
    val wifi = r.optString("warn") != "tailscale_only"                 // the link is the TV's Wi-Fi address: say so, unless only Tailscale reaches it (warned above)
    val url = r.optString("url")
    if (url.isNotEmpty()) {
        val qrBox = col()
        val scopes = listOf(Triple("full", "shield", T("share.full_access")), Triple("remote", "gamepad", T("core.remote_only")),
            Triple("view", "eye", T("core.view_only")))
        val row = LinearLayout(this)
        var scope = "remote"
        val draw = { link: String ->
            qrBox.removeAllViews()
            if (link.isNotEmpty()) qr(qrBox, link, if (scope == "view") T("share.watch_the_tv") else T("share.open_the_remote"),
                (if (wifi) T("share.the_phone_must_be") + "\n" else "") +
                T("share.once_within_10_minutes"))
        }
        val buttons = scopes.map { (k, icon, words) -> chip(icon, words, k == scope) }
        buttons.forEachIndexed { i, b ->
            b.setOnClickListener {
                val k = scopes[i].first
                if (k == scope) return@setOnClickListener
                scope = k
                buttons.forEachIndexed { j, o -> o.setOn(j == i) }
                ask(k) { n -> if (scope == k) draw(n.optString("url")) }   // on a failure no QR rather than the last one under the wrong choice
            }
            row.addView(b, cell(64, i > 0))
        }
        tvPane.addView(label(T("share.what_the_one_who"), 13, MUTED), gap(14))
        tvPane.addView(row, gap(6))
        tvPane.addView(qrBox)
        draw(url)
    }
    else when (r.optString("setup")) {
        "missing" -> tvPane.addView(label(T("share.the_tv_has_no"), 14, MUTED), gap(12))
        "old" -> {
            val up = button("@upload|" + T("share.update_tv_setup_to"), BTN)
            up.setOnClickListener { offerSetup(SetupNeed.Missing("invite", r.optString("have"))) }
            tvPane.addView(up, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        }
        else -> tvPane.addView(label(T("share.tv_setup_gave_no", "error" to r.optString("error")), 14, ERR_TEXT), gap(12))
    }
    qr(appPane, APP_APK, T("share.android_app"), T("setup.qr_app_short"))
    pick(true)
    val scroll = android.widget.ScrollView(this)
    scroll.addView(body)
    Popup.Builder(this).setTitle(T("share.share")).setView(scroll).setPositiveButton(T("drawing.done"), null).show()
}

/** A choice among the three scopes: its icon above the words, so three fit across a phone. */
private fun MainActivity.chip(icon: String, words: String, on: Boolean): android.widget.Button {
    val b = button("@$icon|$words", BTN).apply { setOn(on) }
    b.textSize = 13f
    b.setPadding(dp(4), dp(6), dp(4), dp(6))
    val d = b.compoundDrawablesRelative[0]
    b.setCompoundDrawablesRelative(null, d, null, null)
    b.compoundDrawablePadding = dp(4)
    return b
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
