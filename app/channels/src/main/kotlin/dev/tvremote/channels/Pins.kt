package dev.tvremote.channels

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build

/**
 * The app's public face: a channel is the link tvchannels://play/<iptv-org id>. A pinned shortcut is that link put on the
 * home screen through Android's own request (ShortcutManager), so any launcher that takes pins shows it, and no launcher has
 * to know this app. Not every launcher takes them (Google TV's does not): [can] says so, and the menu entry is left out.
 */
object Pins {
    fun link(id: String): Uri = Uri.parse("tvchannels://play/" + Uri.encode(id))

    /** The id in a link made by [link] (or typed by a phone: tvchannels://play/HTV7.vn@SD). */
    fun id(u: Uri?): String? = u?.takeIf { it.scheme == "tvchannels" && it.host == "play" }?.encodedPath?.trim('/')?.let { Uri.decode(it) }?.ifEmpty { null }

    fun can(c: Context): Boolean =
        Build.VERSION.SDK_INT >= 26 && c.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    fun pin(c: Context, ch: Channel, logo: Bitmap?): Boolean {
        if (!can(c)) return false
        val sm = c.getSystemService(ShortcutManager::class.java) ?: return false
        val icon = if (logo != null) Icon.createWithBitmap(tile(logo)) else Icon.createWithResource(c, R.drawable.icon)
        val s = ShortcutInfo.Builder(c, "ch:" + ch.id)
            .setShortLabel(ch.name.take(24)).setLongLabel(ch.name).setIcon(icon)
            .setIntent(Intent(Intent.ACTION_VIEW, link(ch.id)).setClass(c, PlayActivity::class.java))
            .build()
        return try { sm.requestPinShortcut(s, null) } catch (e: Exception) { false }
    }

    /** The logo on a light 16:9 card, as the list shows it: logos are made for light backgrounds. */
    private fun tile(logo: Bitmap): Bitmap {
        val w = 320; val h = 180
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(242, 244, 247) })
        val k = minOf((w - 48f) / logo.width, (h - 40f) / logo.height)
        val lw = logo.width * k; val lh = logo.height * k
        cv.drawBitmap(logo, null, RectF((w - lw) / 2, (h - lh) / 2, (w + lw) / 2, (h + lh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        return b
    }
}
