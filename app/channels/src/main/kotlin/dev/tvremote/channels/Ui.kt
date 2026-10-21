package dev.tvremote.channels

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView

/** The few looks the two screens share. Plain Views: the app must stay small and quick on a 1 GB TV box. */
object Ui {
    const val TEXT = 0xFFE8EAED.toInt()
    const val DIM = 0xFF9AA0A6.toInt()
    const val ACCENT = 0xFF5EEAD4.toInt()
    const val CARD = 0xFF1B2028.toInt()
    const val TILE = 0xFFF2F4F7.toInt()                                // logos are drawn for light backgrounds
    const val BAD = 0xFFF28B82.toInt()

    fun dp(c: Context, v: Number) = (v.toFloat() * c.resources.displayMetrics.density)

    fun round(color: Int, r: Float, stroke: Int = 0, strokeColor: Int = 0) =
        GradientDrawable().apply { setColor(color); cornerRadius = r; if (stroke > 0) setStroke(stroke, strokeColor) }

    /** A background that is [normal] at rest and a light fill when focused or pressed (D-pad and touch). */
    fun states(c: Context, normal: Int, r: Float, on: Int = 0x33FFFFFF, edge: Boolean = true) = StateListDrawable().apply {
        val sel = round(on, r, if (edge) dp(c, 2).toInt() else 0, ACCENT)
        addState(intArrayOf(android.R.attr.state_focused), sel)
        addState(intArrayOf(android.R.attr.state_pressed), sel)
        addState(intArrayOf(android.R.attr.state_activated), round(0x2214B8A6, r))
        addState(intArrayOf(), round(normal, r))
    }

    fun text(c: Context, s: CharSequence, sp: Float, color: Int = TEXT, bold: Boolean = false) = TextView(c).apply {
        text = s; setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        includeFontPadding = false
    }

    /** A row of the side list or a dialog: icon, words, optional count; focusable, with [go] on OK / tap. */
    fun row(c: Context, icon: Int, label: CharSequence, count: String = "", go: () -> Unit) = android.widget.LinearLayout(c).apply {
        gravity = Gravity.CENTER_VERTICAL
        val p = dp(c, 12).toInt()
        setPadding(p, dp(c, 10).toInt(), p, dp(c, 10).toInt())
        background = states(c, Color.TRANSPARENT, dp(c, 12), edge = false)
        isFocusable = true; isClickable = true
        setOnClickListener { go() }
        if (icon != 0) addView(android.widget.ImageView(c).apply {
            setImageResource(icon); imageTintList = ColorStateList.valueOf(TEXT)
        }, android.widget.LinearLayout.LayoutParams(dp(c, 20).toInt(), dp(c, 20).toInt()).apply { rightMargin = dp(c, 12).toInt() })
        addView(text(c, label, 15f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
            android.widget.LinearLayout.LayoutParams(0, -2, 1f))
        if (count.isNotEmpty()) addView(text(c, count, 13f, DIM))
    }

    fun show(v: View, on: Boolean) { v.visibility = if (on) View.VISIBLE else View.GONE }

    /** The first letters of a channel with no logo: "VTV3 HD" -> "VT". */
    fun initials(name: String) = name.filter { it.isLetterOrDigit() }.take(2).uppercase()
}
