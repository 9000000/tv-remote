package dev.tvremote.phone

import dev.tvremote.core.T
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
    val title = label(T("page.settings"), 24, TEXT)
    title.typeface = Typeface.DEFAULT_BOLD
    val top = LinearLayout(this)
    top.gravity = Gravity.CENTER_VERTICAL
    top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
    val done = icon("@check", T("drawing.done"), ACCENT)
    done.setOnClickListener { settings.visibility = View.GONE }
    top.addView(done, LinearLayout.LayoutParams(dp(52), dp(44)))
    box.addView(top)

    box.addView(head(T("info_screens.language")), gap(18))
    val now = pref("lang")
    val langs = listOf("" to "@phone|" + T("info_screens.same_as_the_phone"), "vi" to "@globe|Tiếng Việt", "en" to "@globe|English")
    for ((code, words) in langs) {
        val b = row(words + (if (code == now) "   ✓" else "")) {
            if (code == now) return@row
            prefs.edit().putString("lang", code).apply()
            recreate()                                            // every label is built in onCreate
        }
        if (code == now) b.setInk(ACCENT_TEXT)
        box.addView(b)
    }

    box.addView(head(T("info_screens.appearance")), gap(18))
    val theme = pref("theme").ifEmpty { "auto" }
    val modes = LinearLayout(this)
    for ((i, m) in listOf("auto" to T("info_screens.auto"), "light" to T("info_screens.light"), "dark" to T("info_screens.dark")).withIndex()) {
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

    box.addView(head(T("info_screens.the_phone_s_volume")), gap(18))
    lateinit var vol: Button
    val volLabel = { vol.setLabel((if (prefs.getBoolean("volKeysTv", true)) "@check|" else "@close|") + T("info_screens.set_the_tv_s")) }
    vol = row("") {
        prefs.edit().putBoolean("volKeysTv", !prefs.getBoolean("volKeysTv", true)).apply()
        volLabel()
    }
    volLabel()
    box.addView(vol)

    box.addView(head(T("info_screens.when_pressing_a_key")), gap(18))
    lateinit var buzzRow: Button
    val buzzLabel = { buzzRow.setLabel((if (prefs.getBoolean("buzz", true)) "@check|" else "@close|") + T("about.vibrate")) }
    buzzRow = row("") {
        prefs.edit().putBoolean("buzz", !prefs.getBoolean("buzz", true)).apply()
        buzzLabel()
        buzz()
    }
    buzzLabel()
    box.addView(buzzRow)

    box.addView(head(T("info_screens.updates")), gap(18))
    lateinit var auto: Button
    val autoLabel = { auto.setLabel((if (prefs.getBoolean("autoUpdate", true)) "@check|" else "@close|") + T("info_screens.check_for_updates_automatically")) }
    auto = row("") {
        prefs.edit().putBoolean("autoUpdate", !prefs.getBoolean("autoUpdate", true)).apply()
        autoLabel()
    }
    autoLabel()
    box.addView(auto)
    box.addView(row("@refresh|" + T("info_screens.check_now")) { checkUpdate(true) })
    updateNote = note("")
    box.addView(updateNote)

    box.addView(head(T("info_screens.about")), gap(18))
    box.addView(row("@info|" + T("about.about")) { about.visibility = View.VISIBLE })

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
    val title = label(T("about.about"), 24, TEXT)
    title.typeface = Typeface.DEFAULT_BOLD
    val top = LinearLayout(this)
    top.gravity = Gravity.CENTER_VERTICAL
    top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
    val done = icon("@check", T("drawing.done"), ACCENT)
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
        qr.contentDescription = T("info_screens.qr_code_to_download")
        val qp = LinearLayout.LayoutParams(dp(170), dp(170))
        qp.gravity = Gravity.CENTER_HORIZONTAL
        qp.topMargin = dp(14)
        box.addView(qr, qp)
        val share = button(T("info_screens.share_the_download_link"), BTN)
        share.setOnClickListener { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, appLink), null)) }
        box.addView(share, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
    }

    box.addView(head(T("info_screens.author")), gap(22))
    box.addView(row("@user|Hoang Tran\n@HoangTran0410") { web("https://github.com/HoangTran0410") })

    box.addView(head(T("info_screens.source_code")), gap(18))
    box.addView(row("@link|github.com/HoangTran0410/tv-remote") { web("https://github.com/" + Release.REPO) })
    box.addView(row("@package|" + T("info_screens.releases")) { web("https://github.com/" + Release.REPO + "/releases") })
    box.addView(row("@bug|" + T("about.report_a_problem")) { web("https://github.com/" + Release.REPO + "/issues") })
    // what the TVs said and the errors shown (WireLog): what a real TV did is what tells a bug apart
    box.addView(row("@clipboard|" + T("info_screens.send_the_log")) {
        val log = dev.tvremote.core.WireLog.all.report(basic?.takeIf { it.kind != dev.tvremote.core.remote.Remote.Kind.ANDROID })
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, T("info_screens.tv_remote_log")).putExtra(Intent.EXTRA_TEXT, log), null))
    })

    box.addView(head(T("info_screens.credits")), gap(18))
    box.addView(row("scrcpy · Apache-2.0") { web("https://github.com/Genymobile/scrcpy") })

    val sv = ScrollView(this)
    sv.setBackgroundColor(BG)
    sv.isClickable = true
    sv.addView(box)
    sv.visibility = View.GONE
    return sv
}
