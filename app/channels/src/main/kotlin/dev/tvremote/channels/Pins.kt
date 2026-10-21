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
 * The app's public face: a channel is the link tvchannels://play/<iptv-org id>, a group of the person's tvchannels://group/<id>
 * ([Store.FAV] the favourites). A pinned shortcut is that link put on the
 * home screen through Android's own request (ShortcutManager), so any launcher that takes pins shows it, and no launcher has
 * to know this app. Not every launcher takes them (Google TV's does not): [can] says so, and the menu entry is left out.
 */
object Pins {
    fun link(id: String): Uri = Uri.parse("tvchannels://play/" + Uri.encode(id))

    /** The id in a link made by [link] (or typed by a phone: tvchannels://play/HTV7.vn@SD). */
    fun id(u: Uri?): String? = u?.takeIf { it.scheme == "tvchannels" && it.host == "play" }?.encodedPath?.trim('/')?.let { Uri.decode(it) }?.ifEmpty { null }

    fun groupLink(id: String): Uri = Uri.parse("tvchannels://group/" + Uri.encode(id))
    fun groupId(u: Uri?): String? = u?.takeIf { it.scheme == "tvchannels" && it.host == "group" }?.encodedPath?.trim('/')?.let { Uri.decode(it) }?.ifEmpty { null }

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
        return try { sm.updateShortcuts(listOf(s)); sm.requestPinShortcut(s, null) } catch (e: Exception) { false }
    }

    /** A group on the home screen: its name, and up to four of its logos on the card. Opens the list on that group. */
    fun pinGroup(c: Context, id: String, name: String, logos: List<Bitmap>): Boolean {
        if (!can(c)) return false
        val sm = c.getSystemService(ShortcutManager::class.java) ?: return false
        val icon = if (logos.isNotEmpty()) Icon.createWithBitmap(mosaic(logos)) else Icon.createWithResource(c, R.drawable.banner)
        val s = ShortcutInfo.Builder(c, "grp:$id")
            .setShortLabel(name.take(24)).setLongLabel(name).setIcon(icon)
            .setIntent(Intent(Intent.ACTION_VIEW, groupLink(id)).setClass(c, BrowseActivity::class.java))
            .build()
        return try { sm.updateShortcuts(listOf(s)); sm.requestPinShortcut(s, null) } catch (e: Exception) { false }
    }

    /** A group renamed: its pin (if any) takes the new name. Deleted: its pin is turned off, the home screen drops it. */
    fun renamed(c: Context, id: String, name: String) {
        if (Build.VERSION.SDK_INT < 26) return
        val sm = c.getSystemService(ShortcutManager::class.java) ?: return
        val old = try { sm.pinnedShortcuts.firstOrNull { it.id == "grp:$id" } } catch (e: Exception) { null } ?: return
        try { sm.updateShortcuts(listOf(ShortcutInfo.Builder(c, old.id).setShortLabel(name.take(24)).setLongLabel(name).build())) } catch (e: Exception) {}
    }
    fun deleted(c: Context, id: String) {
        if (Build.VERSION.SDK_INT < 26) return
        try { c.getSystemService(ShortcutManager::class.java)?.disableShortcuts(listOf("grp:$id")) } catch (e: Exception) {}
    }

    /** Up to four logos, two by two, on the light card. */
    private fun mosaic(logos: List<Bitmap>): Bitmap {
        if (logos.size == 1) return tile(logos[0])
        val w = 320; val h = 180
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.drawColor(Color.rgb(242, 244, 247))                         // square: the launcher rounds it to its own corners, ours showed its backing
        val cw = w / 2f; val chh = h / 2f
        logos.take(4).forEachIndexed { i, logo ->
            val x = (i % 2) * cw; val y = (i / 2) * chh + if (logos.size == 2) chh / 2 else 0f
            val k = minOf((cw - 28f) / logo.width, (chh - 22f) / logo.height)
            val lw = logo.width * k; val lh = logo.height * k
            cv.drawBitmap(logo, null, RectF(x + (cw - lw) / 2, y + (chh - lh) / 2, x + (cw + lw) / 2, y + (chh + lh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return b
    }

    /** The logo on a light 16:9 card, as the list shows it: logos are made for light backgrounds. */
    private fun tile(logo: Bitmap): Bitmap {
        val w = 320; val h = 180
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.drawColor(Color.rgb(242, 244, 247))                         // square: the launcher rounds it to its own corners, ours showed its backing
        val k = minOf((w - 48f) / logo.width, (h - 40f) / logo.height)
        val lw = logo.width * k; val lh = logo.height * k
        cv.drawBitmap(logo, null, RectF((w - lw) / 2, (h - lh) / 2, (w + lw) / 2, (h + lh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        return b
    }
}
