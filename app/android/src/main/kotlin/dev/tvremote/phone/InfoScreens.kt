package dev.tvremote.phone

import android.content.Intent
import android.os.Build
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

    box.addView(head(t("NÚT ÂM LƯỢNG CỦA ĐIỆN THOẠI", "THE PHONE'S VOLUME KEYS")), gap(18))
    lateinit var vol: Button
    val volLabel = { vol.text = (if (prefs.getBoolean("volKeysTv", true)) "✅  " else "⬜  ") + t("Chỉnh âm lượng TV", "Set the TV's volume") }
    vol = row("") {
        prefs.edit().putBoolean("volKeysTv", !prefs.getBoolean("volKeysTv", true)).apply()
        volLabel()
    }
    volLabel()
    box.addView(vol)

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
    dev.tvremote.core.Qr.bitmap(appLink, 6)?.let { bmp ->
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
    box.addView(row("👤  Hoang Tran\n@HoangTran0410") { web("https://github.com/HoangTran0410") })

    box.addView(head(t("MÃ NGUỒN", "SOURCE CODE")), gap(18))
    box.addView(row("🔗  github.com/HoangTran0410/tv-remote") { web("https://github.com/" + Release.REPO) })
    box.addView(row("📦  " + t("Bản phát hành", "Releases")) { web("https://github.com/" + Release.REPO + "/releases") })
    box.addView(row("🐞  " + t("Báo lỗi", "Report a problem")) { web("https://github.com/" + Release.REPO + "/issues") })
    // LG / Samsung are a beta written against libraries and fake TVs: what a real set answered is what tells the bug
    box.addView(row("📋  " + t("Gửi nhật ký LG / Samsung", "Send the LG / Samsung log")) {
        val log = dev.tvremote.core.WireLog.all.report("TV Remote " + version() + " (phone, Android " + Build.VERSION.RELEASE + ")",
            basic?.takeIf { it.kind != dev.tvremote.core.Remote.Kind.ANDROID })
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "TV Remote: LG / Samsung").putExtra(Intent.EXTRA_TEXT, log), null))
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
