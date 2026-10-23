package dev.tvremote.games

import android.app.Activity
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import dev.tvremote.tvui.dp
import dev.tvremote.tvui.label
import dev.tvremote.tvui.panel
import dev.tvremote.tvui.scrim

/**
 * The console buttons a TV remote lacks (Start, Select, B), given to keys it has. Remotes differ (numbers, colours, ⏯, none of
 * them), so the keys are learnt: the pause card's "Map remote keys" asks for each in turn. Kept for every game.
 * ⏯ is Start until something else is.
 */
class RemoteMap(private val act: Activity, private val root: FrameLayout, private val hold: (Boolean) -> Unit) {   // [hold]: the game stands still meanwhile
    private val prefs = act.getSharedPreferences("remote_map", 0)
    private val steps = listOf(KeyEvent.KEYCODE_BUTTON_START to R.string.map_start, KeyEvent.KEYCODE_BUTTON_SELECT to R.string.map_select,
        KeyEvent.KEYCODE_BUTTON_B to R.string.map_b)
    private var step = -1
    private var card: FrameLayout? = null
    private val ask = act.label("", 30f, act.getColor(R.color.tv_ink))
    private val now = act.label("", 19f, act.getColor(R.color.tv_dim))
    val learning get() = step >= 0

    /** The console button for a remote key, or null. */
    fun button(code: Int): Int? = prefs.getInt(code.toString(), 0).takeIf { it != 0 }
        ?: if ((code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_MEDIA_PLAY) && keyOf(KeyEvent.KEYCODE_BUTTON_START) == null) KeyEvent.KEYCODE_BUTTON_START else null

    private fun keyOf(button: Int): Int? = prefs.all.entries.firstOrNull { it.value == button }?.key?.toIntOrNull()

    fun learn() {
        if (card == null) card = act.scrim(act.panel("🎮  " + act.getString(R.string.map_title)).apply {
            minimumWidth = act.dp(520)
            addView(ask.apply { setPadding(0, act.dp(6), 0, act.dp(8)) })
            addView(now)
            addView(act.label(act.getString(R.string.map_skip), 17f, act.getColor(R.color.tv_dim)).apply { setPadding(0, act.dp(18), 0, 0) })
        }).also { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        step = 0; hold(true); show()
    }

    private fun show() {
        if (step >= steps.size) { step = -1; card?.visibility = View.GONE; hold(false); return }
        val (button, words) = steps[step]
        val w = act.getString(words); val t = act.getString(R.string.map_press, w); val at = t.indexOf(w)
        ask.text = android.text.SpannableString(t).apply { if (at >= 0) setSpan(android.text.style.ForegroundColorSpan(act.getColor(R.color.tv_ring_accent)), at, at + w.length, 0)
            if (at >= 0) setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), at, at + w.length, 0) }
        val k = keyOf(button) ?: if (button == KeyEvent.KEYCODE_BUTTON_START) KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE else null
        now.text = if (k == null) "" else act.getString(R.string.map_now, name(k))
        card?.visibility = View.VISIBLE
    }

    /** While learning every key ends here. True = used up. */
    fun key(ev: KeyEvent): Boolean {
        if (!learning) return false
        if (ev.action != KeyEvent.ACTION_DOWN || ev.repeatCount > 0) return true
        val c = ev.keyCode
        if (c == KeyEvent.KEYCODE_BACK) { step++; show(); return true }
        if (c in TAKEN) return true                                   // the D-pad, OK and the system's keys stay theirs
        val button = steps[step].first
        prefs.edit().apply { prefs.all.forEach { (k, v) -> if (v == button || k == c.toString()) remove(k) }; putInt(c.toString(), button) }.apply()
        step++; show(); return true
    }

    companion object {
        private val TAKEN = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER)
        fun name(code: Int) = when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "⏯"
            KeyEvent.KEYCODE_PROG_RED -> "🔴"; KeyEvent.KEYCODE_PROG_GREEN -> "🟢"
            KeyEvent.KEYCODE_PROG_YELLOW -> "🟡"; KeyEvent.KEYCODE_PROG_BLUE -> "🔵"
            else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ')
        }
    }
}
