package dev.tvremote.home.apps

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvremote.home.Font
import dev.tvremote.home.R
import dev.tvremote.tvui.Motion
import dev.tvremote.tvui.TvGlass
import dev.tvremote.tvui.TvUi
import org.json.JSONArray

/**
 * The views of the app menu's own pages (inside [TvGlass.Panel.view]): the parent's PIN keypad, a plain message, the 7-day use chart.
 * The keypad mirrors TV Setup's PIN screen (`PinActivity`): dots, a line of words, a 3 x 4 pad with the digits in a new order each time
 * and the remote's number keys; it submits at the PIN's length. What happened (wrong code and tries left, a wait, no PIN) is always the
 * line over the dots, never a toast alone.
 */
object AppPages {
    private val BAD = Color.rgb(255, 120, 110)                          // wrong code, waiting (TV Setup's PIN screen)
    private const val BAR_EMPTY = 0x1FFFFFFF                            // a day with no use: a stub on the track
    private val BAR_PAST = 0x80FFFFFF.toInt()                           // the days before today

    private fun dp(a: Activity, v: Number) = TvUi.dp(a, v).toInt()
    private fun text(a: Activity, s: String, sp: Float, color: Int, bold: Boolean = false) =
        TvUi.text(a, s, sp, color, bold).apply { Font.on(this); typeface = if (bold) Font.bold else Font.regular; gravity = Gravity.CENTER }

    /** A focusable row-button of the panel (the keys, "See details"). */
    private fun key(a: Activity, label: String, sp: Float, go: () -> Unit) = text(a, label, sp, Color.WHITE, true).apply {
        isFocusable = true; isClickable = true; background = TvGlass.rowBg(a, 14)
        onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
        setOnClickListener { go() }
    }

    /** The PIN keypad's page: [view] to show, [focus] to focus first, [onKey] for the dialog's key listener (number keys, delete). */
    class Pad(val view: View, val focus: View, val onKey: (Int, KeyEvent) -> Boolean)

    /**
     * [said] is the line over the dots ([bad]: in red); [len] the PIN's length, [totp] a 6-digit Authenticator code also opens it (then the
     * OK key sends what is typed; at 6 it goes by itself). [submit] gets the digits.
     */
    fun pin(a: Activity, said: String, bad: Boolean, len: Int, totp: Boolean, submit: (String) -> Unit): Pad {
        var typed = ""
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        col.addView(text(a, said, 14f, if (bad) BAD else TvGlass.HINT).apply { maxLines = 4 }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(a, 8) })
        val dots = text(a, "", 28f, Color.WHITE, true).apply { letterSpacing = 0.3f }
        col.addView(dots, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(a, 10) })
        val most = if (totp) maxOf(len, 6) else len
        fun draw() { dots.text = "●".repeat(typed.length) + "○".repeat(maxOf(0, most - typed.length)) }
        fun send() { if (typed.isNotEmpty()) { val t = typed; typed = ""; draw(); submit(t) } }
        fun press(k: String) {
            when (k) {
                "⌫" -> typed = typed.dropLast(1)
                "OK" -> { send(); return }
                else -> if (typed.length < most) typed += k
            }
            draw()
            if (k != "⌫" && (typed.length == len && !totp || typed.length == 6 && totp || typed.length == most)) send()
        }
        // The digits in a new order each time (as PinActivity does): whoever watches which key is lit learns nothing of the code
        val d = ('0'..'9').map { it.toString() }.shuffled(java.util.Random(java.security.SecureRandom().nextLong()))
        val keys = d.subList(0, 9) + "⌫" + d[9] + (if (totp) a.getString(R.string.am_pin_ok) else "")
        var first: View? = null
        val grid = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        for (r in 0 until 4) {
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
            for (c in 0 until 3) {
                val k = keys[r * 3 + c]
                if (k.isEmpty()) { row.addView(View(a), LinearLayout.LayoutParams(dp(a, 84), dp(a, 52)).apply { setMargins(dp(a, 4), dp(a, 4), dp(a, 4), dp(a, 4)) }); continue }
                val v = key(a, k, 20f) { press(if (k == a.getString(R.string.am_pin_ok)) "OK" else k) }
                if (first == null) first = v
                row.addView(v, LinearLayout.LayoutParams(dp(a, 84), dp(a, 52)).apply { setMargins(dp(a, 4), dp(a, 4), dp(a, 4), dp(a, 4)) })
            }
            grid.addView(row)
        }
        col.addView(grid, LinearLayout.LayoutParams(-2, -2))
        draw()
        return Pad(col, first ?: col) { code, e ->
            if (e.action != KeyEvent.ACTION_DOWN) code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 || code == KeyEvent.KEYCODE_DEL
            else if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) { press((code - KeyEvent.KEYCODE_0).toString()); true }
            else if (code == KeyEvent.KEYCODE_DEL) { press("⌫"); true } else false
        }
    }

    /** A page that is words (and one optional button): the focus goes to the button, or [fallback] row of the panel when there is none. */
    fun message(a: Activity, said: String, bad: Boolean = false, button: String? = null, go: () -> Unit = {}): Pair<View, View?> {
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        col.addView(text(a, said, 15f, if (bad) BAD else Color.WHITE).apply { maxLines = 8; setLineSpacing(0f, 1.1f) }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(a, 6) })
        val b = button?.let { key(a, it, 16f, go).apply { setPadding(dp(a, 20), dp(a, 12), dp(a, 20), dp(a, 12)) } }
        b?.let { col.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(a, 8) }) }
        return col to b
    }

    private fun dur(m: Long) = if (m >= 60) "%dh%02d".format(m / 60, m % 60) else "$m"

    /**
     * The use chart: a bar per day of [days] (seconds, today FIRST; drawn oldest to today), the minutes over each bar, the day's name under
     * it, today in white and the others dimmer. [names] are the 7 short day names in the same order as [days]. [more] is the
     * "See details" button, [note] the line under it.
     */
    fun week(a: Activity, days: JSONArray, names: List<String>, more: String, note: String, go: () -> Unit): Pair<View, View> {
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        val secs = (0 until 7).map { days.optLong(it) }
        val total = (secs.sum() + 30) / 60
        col.addView(text(a, if (total > 0) a.getString(R.string.am_week_total, if (total >= 60) dur(total) else a.getString(R.string.am_limit_min, total.toInt())) else a.getString(R.string.am_week_none), 14f, TvGlass.HINT),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(a, 10) })
        val top = maxOf(1L, secs.max())
        val bars = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; clipChildren = false }
        val chart = dp(a, 110)
        for (i in 6 downTo 0) {                                          // the oldest first, today at the right
            val m = (secs[i] + 30) / 60
            val cell = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            cell.addView(text(a, if (secs[i] == 0L) "" else if (m == 0L) "<1" else dur(m), 12f, if (i == 0) Color.WHITE else TvGlass.HINT).apply { maxLines = 1 }, LinearLayout.LayoutParams(-2, dp(a, 18)))
            val slot = LinearLayout(a).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL }
            val h = if (secs[i] == 0L) dp(a, 3) else maxOf(dp(a, 6), (chart * secs[i] / top).toInt())
            slot.addView(View(a).apply {
                background = GradientDrawable().apply { cornerRadius = dp(a, 6).toFloat(); setColor(if (i == 0) Color.WHITE else if (secs[i] == 0L) BAR_EMPTY else BAR_PAST) }
            }, LinearLayout.LayoutParams(dp(a, 26), h))
            cell.addView(slot, LinearLayout.LayoutParams(-2, chart))
            cell.addView(text(a, names[i], 12f, if (i == 0) Color.WHITE else TvGlass.HINT).apply { maxLines = 1 }, LinearLayout.LayoutParams(-2, dp(a, 20)))
            bars.addView(cell, LinearLayout.LayoutParams(dp(a, 56), -2))
        }
        col.addView(bars, LinearLayout.LayoutParams(-2, -2))
        val b = key(a, more, 16f, go).apply { setPadding(dp(a, 20), dp(a, 12), dp(a, 20), dp(a, 12)) }
        col.addView(b, LinearLayout.LayoutParams(dp(a, 280), -2).apply { topMargin = dp(a, 16) })
        col.addView(text(a, note, 13f, TvGlass.HINT).apply { maxLines = 4 }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(a, 8) })
        return col to b
    }
}
