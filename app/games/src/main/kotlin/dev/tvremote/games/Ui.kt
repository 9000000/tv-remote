package dev.tvremote.games

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import dev.tvremote.core.web.Qr
import dev.tvremote.tvui.dp
import dev.tvremote.tvui.label
import dev.tvremote.tvui.menuButton

val PLAYER_COLORS = intArrayOf(0xFF3B82F6.toInt(), 0xFFEF4444.toInt(), 0xFF22C55E.toInt(), 0xFFEAB308.toInt())

fun Context.dot(slot: Int, size: Int) = View(this).apply {
    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(PLAYER_COLORS[slot - 1]) }
    layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { marginEnd = dp(size / 2); gravity = Gravity.CENTER_VERTICAL }
}

/** The join code, [scale] px per module: made once per address (the cards and the lobby ask for the same one), and drawn without smoothing so
 *  an enlarged module stays a sharp square (a smoothed QR blurs at the edges and reads worse from the sofa). */
private val qrCache = HashMap<String, android.graphics.Bitmap>()
fun Context.qrView(url: String, sizeDp: Int, scale: Int = 8) = ImageView(this).apply {
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    scaleType = ImageView.ScaleType.FIT_XY
    val b = synchronized(qrCache) { qrCache[url + "|" + scale] ?: Qr.bitmap(url, scale)?.also { qrCache[url + "|" + scale] = it } }
    if (b != null) setImageDrawable(android.graphics.drawable.BitmapDrawable(resources, b).apply { isFilterBitmap = false })
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
