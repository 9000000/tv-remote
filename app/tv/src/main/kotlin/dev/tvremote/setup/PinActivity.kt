package dev.tvremote.setup

import dev.tvremote.core.T
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Over a locked app ([Rules.locked]) the moment it comes to the front ([Guard]): its PIN on the remote's number keys or the keypad
 * here, checked as soon as it has its length. Back, or ✕, goes to the home screen. Opaque, so the app under it is stopped (a
 * video pauses) and nothing of it shows. Five wrong PINs in a row: a minute's wait before the next.
 */
class PinActivity : Activity() {
    companion object {
        /** The app this screen is over now, null when it is not shown: the Guard does not ask twice. */
        @Volatile var showing: String? = null
        private var wrong = 0
        private var waitUntil = 0L
    }

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var dots: TextView
    private lateinit var said: TextView
    private var pkg = ""
    private var typed = ""
    private var len = 4

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        read(intent)
        val page = LinearLayout(this)
        page.gravity = Gravity.CENTER
        page.setBackgroundColor(Color.rgb(12, 14, 18))
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.background = GradientDrawable().apply { setColor(Color.rgb(25, 29, 35)); cornerRadius = dp(20).toFloat() }
        card.setPadding(dp(36), dp(20), dp(36), dp(20))
        card.addView(text("🔒", 36, false))
        card.addView(text(T("pin.locked", "name" to name()), 24, true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
        said = text(T("pin.enter"), 16, false).apply { setTextColor(Color.rgb(170, 178, 189)) }
        card.addView(said, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        dots = text("", 30, true).apply { letterSpacing = 0.3f }
        card.addView(dots, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        val pad = GridLayout(this).apply { columnCount = 3 }
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✕")
        val buttons = keys.map { k -> button(k) { press(k) } }
        buttons.forEach { pad.addView(it, GridLayout.LayoutParams().apply { width = dp(72); height = dp(48); setMargins(dp(4), dp(4), dp(4), dp(4)) }) }
        card.addView(pad, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        page.addView(card, LinearLayout.LayoutParams(-2, -2))
        setContentView(page)
        buttons[0].isFocusableInTouchMode = true                    // opened from the background, the TV may be in touch mode: OK must land somewhere
        buttons[0].requestFocus()
        draw()
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); read(i); typed = ""; draw() }

    private fun read(i: Intent) {
        pkg = i.getStringExtra("pkg") ?: ""
        showing = pkg
        len = Rules.load(this).optJSONObject("lock")?.optInt("len", 4) ?: 4
    }

    override fun onDestroy() { if (showing == pkg) showing = null; ui.removeCallbacksAndMessages(null); super.onDestroy() }

    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) { press((code - KeyEvent.KEYCODE_0).toString()); return true }
        if (code == KeyEvent.KEYCODE_DEL) { press("⌫"); return true }
        return super.onKeyDown(code, e)
    }

    @Suppress("OVERRIDE_DEPRECATION") override fun onBackPressed() { home() }

    private fun press(k: String) {
        when (k) {
            "✕" -> { home(); return }
            "⌫" -> typed = typed.dropLast(1)
            else -> {
                val left = waitUntil - System.currentTimeMillis()
                if (left > 0) { said.text = T("pin.wait", "s" to (left + 999) / 1000); return }
                if (typed.isEmpty()) { said.text = T("pin.enter"); said.setTextColor(Color.rgb(170, 178, 189)) }
                if (typed.length < len) typed += k
            }
        }
        draw()
        if (typed.length < len) return
        if (Rules.pinOk(Rules.load(this), typed)) {
            wrong = 0
            Guard.unlock(pkg)
            showing = null
            finish()
            return
        }
        typed = ""
        if (++wrong >= 5) { wrong = 0; waitUntil = System.currentTimeMillis() + 60_000 }
        said.text = T("pin.wrong")
        said.setTextColor(Color.rgb(255, 120, 110))
        ui.postDelayed({ draw() }, 150)
    }

    private fun draw() { dots.text = "●".repeat(typed.length) + "○".repeat(maxOf(0, len - typed.length)) }

    private fun home() {
        showing = null
        try { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
        finish()
    }

    private fun name(): String = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun text(s: String, sp: Int, bold: Boolean): TextView = TextView(this).apply {
        text = s; textSize = sp.toFloat(); setTextColor(Color.rgb(233, 236, 241)); gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(label: String, run: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(0, 0, 0, 0)
        stateListAnimator = null
        val idle = GradientDrawable().apply { setColor(Color.rgb(34, 39, 47)); cornerRadius = dp(14).toFloat() }
        val focus = GradientDrawable().apply { setColor(Color.rgb(59, 115, 246)); cornerRadius = dp(14).toFloat() }
        background = idle
        setOnFocusChangeListener { v, f -> v.background = if (f) focus else idle }
        setOnClickListener { run() }
    }
}
