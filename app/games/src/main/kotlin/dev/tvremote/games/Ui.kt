package dev.tvremote.games

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvremote.core.web.Qr

fun Context.dp(n: Int) = (n * resources.displayMetrics.density).toInt()

val PLAYER_COLORS = intArrayOf(0xFF3B82F6.toInt(), 0xFFEF4444.toInt(), 0xFF22C55E.toInt(), 0xFFEAB308.toInt())

/** A rounded fill that gets a thick white edge while the D-pad focus is on it (readable from a sofa). */
fun Context.focusBg(radius: Int, fill: Int, focusFill: Int = fill): StateListDrawable {
    fun d(f: Int, stroke: Boolean) = GradientDrawable().apply {
        setColor(f); cornerRadius = dp(radius).toFloat(); if (stroke) setStroke(dp(4), Color.WHITE)
    }
    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), d(focusFill, true))
        addState(intArrayOf(), d(fill, false))
    }
}

fun Context.dot(slot: Int, size: Int) = View(this).apply {
    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(PLAYER_COLORS[slot - 1]) }
    layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { marginEnd = dp(size / 2); gravity = Gravity.CENTER_VERTICAL }
}

fun Context.qrView(url: String, sizeDp: Int) = ImageView(this).apply {
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    scaleType = ImageView.ScaleType.FIT_XY
    setImageBitmap(Qr.bitmap(url, 8))
}

fun Context.label(text: String, sp: Float, color: Int = Color.WHITE) = TextView(this).apply {
    this.text = text; textSize = sp; setTextColor(color); gravity = Gravity.CENTER
    // wrap, never the vertical LinearLayout's default match_parent: inside a wrap_content panel that made every label as wide as the
    // QR beside it (150 dp), so "Scan to add games" and the link wrapped and were clipped
    layoutParams = LinearLayout.LayoutParams(-2, -2)
}

/** A big focusable menu button: dark, white edge + light fill under focus. */
fun Context.menuButton(text: String, w: Int, h: Int, sp: Float, onClick: () -> Unit) = TextView(this).apply {
    this.text = text; textSize = sp; gravity = Gravity.CENTER
    isFocusable = true; isClickable = true
    background = focusBg(10, 0xFF2A3042.toInt(), 0xFFFFFFFF.toInt())
    setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(0xFF0B0D12.toInt(), Color.WHITE)))
    layoutParams = LinearLayout.LayoutParams(dp(w), dp(h)).apply { topMargin = dp(8) }
    setOnClickListener { onClick() }
}

/** The "scan to add games" block: a big QR, the link under it and where else to put files. */
fun Context.addPanel(server: GameServer): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
    addView(label(getString(R.string.scan_add), 28f))
    addView(qrView(server.addUrl(), 150).apply { (layoutParams as LinearLayout.LayoutParams).setMargins(0, dp(14), 0, dp(10)) })
    addView(label(server.addUrl().substringBefore("/add"), 18f, 0xFFAAB0C0.toInt()))
}
