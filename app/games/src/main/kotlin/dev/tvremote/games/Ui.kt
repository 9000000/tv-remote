package dev.tvremote.games

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
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

/** The TV apps' panel (app/DESIGN-TV.md): `tv_card`, radius 16 dp; every card, popup and menu of TV Games is one. */
fun Context.panelBg() = GradientDrawable().apply { setColor(getColor(R.color.tv_card)); cornerRadius = resources.getDimension(R.dimen.tv_radius_card) }

/** A panel's padding and column; [title] on top when given. */
fun Context.panel(title: String? = null) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; background = panelBg()
    setPadding(dp(36), dp(26), dp(36), dp(28))
    if (title != null) addView(heading(title))
}

fun Context.heading(text: String) = label(text, 28f, getColor(R.color.tv_ink)).apply {
    typeface = android.graphics.Typeface.DEFAULT_BOLD; maxLines = 2; setPadding(dp(20), 0, dp(20), dp(10))
}

/** The screen under a panel: dimmed, the game or the library still seen behind (a dark game hid a dark card on no scrim). */
fun Context.scrim(child: View) = FrameLayout(this).apply {
    setBackgroundColor(0xD90E1116.toInt()); isClickable = true
    addView(child, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
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

/** A menu row: its line icon and words together on the left, the icon in the words' colour (dark under focus); a blank slot
 *  keeps rows without an icon in line. */
fun TextView.withIcon(r: Int?): TextView = apply {
    val d = if (r != null) context.getDrawable(r)!!.mutate().also { it.setTintList(textColors) } else android.graphics.drawable.ColorDrawable(0)
    val s = context.dp(22); d.setBounds(0, 0, s, s); setCompoundDrawablesRelative(d, null, null, null)
    compoundDrawablePadding = context.dp(14); gravity = Gravity.START or Gravity.CENTER_VERTICAL; setPadding(context.dp(24), 0, context.dp(20), 0)
}

/** The "scan to add games" block: a big QR, the link under it and where else to put files. */
fun Context.addPanel(server: GameServer): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
    addView(label(getString(R.string.scan_add), 28f))
    addView(qrView(server.addUrl(), 150).apply { (layoutParams as LinearLayout.LayoutParams).setMargins(0, dp(14), 0, dp(10)) })
    addView(label(server.addUrl().substringBefore("/add"), 18f, getColor(R.color.tv_dim)))
    overlayButton()?.let { addView(it) }
}

/**
 * "Let phones reopen TV Games": the pad's "open again" after Home works only for an app allowed to draw over others (Android 10+ blocks
 * every other start from the background; measured on Android 14). The TV's own settings page for it, offered while it is off.
 */
fun Context.overlayButton(): View? {
    if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) return null
    val it = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (packageManager.resolveActivity(it, 0) == null) return null
    return menuButton(getString(R.string.allow_reopen), 360, 52, 20f) { try { startActivity(it) } catch (e: Exception) { } }
}

/** "Reset connection code": a new code, every phone that had the old one must scan again. [again] redraws the QR. */
fun Context.rotateButton(server: GameServer, again: () -> Unit) = menuButton(getString(R.string.new_code), 360, 52, 20f) {
    server.rotateToken(); android.widget.Toast.makeText(this, getString(R.string.code_changed), android.widget.Toast.LENGTH_LONG).show(); again()
}
