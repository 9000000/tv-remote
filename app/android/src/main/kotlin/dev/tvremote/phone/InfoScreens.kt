package dev.tvremote.phone

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import dev.tvremote.update.Release

// The Settings and About screens: plain pages over the picker.

// ---------------------------------------------------------------- settings screen
internal fun MainActivity.buildSettings(): View {
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
    val langs = listOf("" to "@phone|" + t("Theo điện thoại", "Same as the phone"), "vi" to "@globe|Tiếng Việt", "en" to "@globe|English")
    for ((code, words) in langs) {
        val b = row(words + (if (code == now) "   ✓" else "")) {
            if (code == now) return@row
            prefs.edit().putString("lang", code).apply()
            recreate()                                            // every label is built in onCreate
        }
        if (code == now) b.setInk(ACCENT_TEXT)
        box.addView(b)
    }

    box.addView(head(t("GIAO DIỆN", "APPEARANCE")), gap(18))
    val theme = pref("theme").ifEmpty { "auto" }
    val modes = LinearLayout(this)
    for ((i, m) in listOf("auto" to t("Tự động", "Auto"), "light" to t("Sáng", "Light"), "dark" to t("Tối", "Dark")).withIndex()) {
        val on = m.first == theme
        val art = mapOf("auto" to "contrast", "light" to "wake", "dark" to "moon")[m.first]
        val b = button("@$art|" + m.second, BTN).apply { setOn(on) }
        b.setPadding(dp(6), 0, dp(6), 0)
        b.contentDescription = m.second
        b.setOnClickListener {
            if (on) return@setOnClickListener
            prefs.edit().putString("theme", m.first).apply()
            recreate()                                            // the colours are read while the views are built
        }
        modes.addView(b, cell(44, i > 0))
    }
    box.addView(modes, gap(6))

    box.addView(head(t("NÚT ÂM LƯỢNG CỦA ĐIỆN THOẠI", "THE PHONE'S VOLUME KEYS")), gap(18))
    lateinit var vol: Button
    val volLabel = { vol.setLabel((if (prefs.getBoolean("volKeysTv", true)) "@check|" else "@close|") + t("Chỉnh âm lượng TV", "Set the TV's volume")) }
    vol = row("") {
        prefs.edit().putBoolean("volKeysTv", !prefs.getBoolean("volKeysTv", true)).apply()
        volLabel()
    }
    volLabel()
    box.addView(vol)

    box.addView(head(t("KHI BẤM NÚT", "WHEN PRESSING A KEY")), gap(18))
    lateinit var buzzRow: Button
    val buzzLabel = { buzzRow.setLabel((if (prefs.getBoolean("buzz", true)) "@check|" else "@close|") + t("Rung", "Vibrate")) }
    buzzRow = row("") {
        prefs.edit().putBoolean("buzz", !prefs.getBoolean("buzz", true)).apply()
        buzzLabel()
        buzz()
    }
    buzzLabel()
    box.addView(buzzRow)

    box.addView(head(t("CẬP NHẬT", "UPDATES")), gap(18))
    lateinit var auto: Button
    val autoLabel = { auto.setLabel((if (prefs.getBoolean("autoUpdate", true)) "@check|" else "@close|") + t("Tự kiểm tra bản mới", "Check for updates automatically")) }
    auto = row("") {
        prefs.edit().putBoolean("autoUpdate", !prefs.getBoolean("autoUpdate", true)).apply()
        autoLabel()
    }
    autoLabel()
    box.addView(auto)
    box.addView(row("@refresh|" + t("Kiểm tra ngay", "Check now")) { checkUpdate(true) })
    updateNote = note("")
    box.addView(updateNote)

    box.addView(head(t("GIỚI THIỆU", "ABOUT")), gap(18))
    box.addView(row("@info|" + t("Giới thiệu", "About")) { about.visibility = View.VISIBLE })

    val sv = ScrollView(this)
    sv.setBackgroundColor(BG)
    sv.isClickable = true                                         // do not let touches fall through to the screen below
    sv.addView(box)
    sv.visibility = View.GONE
    return sv
}

// ---------------------------------------------------------------- about screen
internal fun MainActivity.web(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {                                      // no browser on this device
        message(url, false)
    }
}

internal fun MainActivity.buildAbout(): View {
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
    dev.tvremote.core.web.Qr.bitmap(appLink, 6)?.let { bmp ->
        val qr = ImageView(this)
        qr.setImageBitmap(bmp)
        qr.contentDescription = t("Mã QR tải app", "QR code to download the app")
        val qp = LinearLayout.LayoutParams(dp(170), dp(170))
        qp.gravity = Gravity.CENTER_HORIZONTAL
        qp.topMargin = dp(14)
        box.addView(qr, qp)
        val share = button(t("Chia sẻ link tải app", "Share the download link"), BTN)
        share.setOnClickListener { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, appLink), null)) }
        box.addView(share, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
    }

    box.addView(head(t("TÁC GIẢ", "AUTHOR")), gap(22))
    box.addView(row("@user|Hoang Tran\n@HoangTran0410") { web("https://github.com/HoangTran0410") })

    box.addView(head(t("MÃ NGUỒN", "SOURCE CODE")), gap(18))
    box.addView(row("@link|github.com/HoangTran0410/tv-remote") { web("https://github.com/" + Release.REPO) })
    box.addView(row("@package|" + t("Bản phát hành", "Releases")) { web("https://github.com/" + Release.REPO + "/releases") })
    box.addView(row("@bug|" + t("Báo lỗi", "Report a problem")) { web("https://github.com/" + Release.REPO + "/issues") })
    // what the TVs said and the errors shown (WireLog): what a real TV did is what tells a bug apart
    box.addView(row("@clipboard|" + t("Gửi nhật ký", "Send the log")) {
        val log = dev.tvremote.core.WireLog.all.report(basic?.takeIf { it.kind != dev.tvremote.core.remote.Remote.Kind.ANDROID })
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "TV Remote: " + t("nhật ký", "log")).putExtra(Intent.EXTRA_TEXT, log), null))
    })

    box.addView(head(t("GHI CÔNG", "CREDITS")), gap(18))
    box.addView(row("scrcpy · Apache-2.0") { web("https://github.com/Genymobile/scrcpy") })

    val sv = ScrollView(this)
    sv.setBackgroundColor(BG)
    sv.isClickable = true
    sv.addView(box)
    sv.visibility = View.GONE
    return sv
}
