package dev.tvremote.setup

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvremote.core.T
import kotlin.math.roundToInt

/**
 * Shown over the TV by whoever swapped its launcher: "Keep this launcher? Going back in 30 s" with Keep / Go back (like the display
 * change prompt), or, with `notice`, one line saying the TV went back to its own launcher and why ([LauncherGuard]). The answer is
 * kept in the swap's record on the TV, so the page that started it (waiting) sees it too; no answer means going back, by the watchdog.
 */
class LauncherConfirmActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var count: TextView
    private var until = 0L

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val notice = intent.getStringExtra("notice") ?: ""
        until = intent.getLongExtra("until", 0L) * 1000L
        val name = intent.getStringExtra("name") ?: ""
        val page = LinearLayout(this).apply { gravity = Gravity.CENTER; setBackgroundColor(Color.argb(200, 0, 0, 0)) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply { setColor(Color.rgb(25, 29, 35)); cornerRadius = dp(20).toFloat() }
            setPadding(dp(40), dp(32), dp(40), dp(28))
        }
        card.addView(text(if (notice.isEmpty()) "🏠" else "↩️", 48, false))
        card.addView(text(if (notice.isEmpty()) T("launcher.keep_title", "name" to name) else T("launcher.went_back"), 28, true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        card.addView(text(if (notice.isEmpty()) T("launcher.keep_note") else T(when (notice) { "crash" -> "launcher.why_crash"; "home" -> "launcher.why_home"; "timeout" -> "launcher.why_timeout"; "interrupted" -> "launcher.why_interrupted"; else -> "launcher.why_user" }), 16, false).apply { setTextColor(Color.rgb(170, 178, 189)); maxWidth = dp(560) },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        count = text("", 22, true)
        if (notice.isEmpty()) card.addView(count, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val keep = button(if (notice.isEmpty()) T("launcher.keep") else "OK") { if (notice.isEmpty()) answer(true) else finish() }
        row.addView(keep, LinearLayout.LayoutParams(-2, dp(52)))
        if (notice.isEmpty()) row.addView(button(T("launcher.go_back")) { answer(false) }, LinearLayout.LayoutParams(-2, dp(52)).apply { leftMargin = dp(12) })
        card.addView(row, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(22) })
        page.addView(card, LinearLayout.LayoutParams(-2, -2))
        setContentView(page)
        keep.isFocusableInTouchMode = true                          // opened by ADB, the TV is still in touch mode: OK must land somewhere
        keep.requestFocus()
        if (notice.isEmpty()) tick.run() else ui.postDelayed({ finish() }, 20_000L)
    }

    private val tick = object : Runnable {
        override fun run() {
            val s = (until - System.currentTimeMillis() + 999) / 1000
            if (s <= 0) { finish(); return }
            count.text = T("launcher.back_in", "s" to s)
            ui.postDelayed(this, 500)
        }
    }

    private fun answer(keep: Boolean) {
        Thread { LauncherGuard.answer(keep); runOnUiThread { finish() } }.start()
    }

    override fun onDestroy() { ui.removeCallbacksAndMessages(null); super.onDestroy() }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun text(s: String, sp: Int, bold: Boolean): TextView = TextView(this).apply {
        text = s; textSize = sp.toFloat(); setTextColor(Color.rgb(233, 236, 241)); gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(label: String, run: () -> Unit): Button = Button(this).apply {
        text = label; isAllCaps = false; textSize = 17f; setTextColor(Color.WHITE); setPadding(dp(22), 0, dp(22), 0); stateListAnimator = null
        val idle = GradientDrawable().apply { setColor(Color.rgb(34, 39, 47)); cornerRadius = dp(14).toFloat() }
        val focus = GradientDrawable().apply { setColor(Color.rgb(59, 115, 246)); cornerRadius = dp(14).toFloat() }
        background = idle
        setOnFocusChangeListener { v, f -> v.background = if (f) focus else idle }
        setOnClickListener { run() }
    }
}
