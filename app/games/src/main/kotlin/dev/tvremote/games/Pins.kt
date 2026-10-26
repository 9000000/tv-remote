package dev.tvremote.games

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build

/**
 * The app's public face: a game is the link tvgames://play/<id> (the library's id: "g:<file>" or "w:<url>"), a folder of the person's
 * tvgames://folder/<id>. A pinned shortcut is that link put on the home screen through Android's own request (ShortcutManager), so any
 * launcher that takes pins shows it (TV Home does) and none has to know this app. Not every launcher takes them (Google TV's does
 * not): [can] says so and the menu entry is left out. Same shape as TV Channels' Pins.
 */
object Pins {
    fun playLink(id: String): Uri = Uri.parse("tvgames://play/" + Uri.encode(id))
    fun folderLink(id: String): Uri = Uri.parse("tvgames://folder/" + Uri.encode(id))
    fun playId(u: Uri?): String? = idOf(u, "play")
    fun folderId(u: Uri?): String? = idOf(u, "folder")
    private fun idOf(u: Uri?, host: String): String? = u?.takeIf { it.scheme == "tvgames" && it.host == host }?.encodedPath?.trim('/')?.let { Uri.decode(it) }?.ifEmpty { null }

    fun can(c: Context): Boolean =
        Build.VERSION.SDK_INT >= 26 && c.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    /** [art] the game's picture as the library shows it (null: a card in the system's colour with its initials). */
    fun pinGame(c: Context, id: String, name: String, art: Bitmap?, color: Int): Boolean =
        pin(c, "g:$id", name, Intent(Intent.ACTION_VIEW, playLink(id)).setClass(c, LibraryActivity::class.java)) { w, h -> card(c, name, color, listOfNotNull(art), w, h) }

    /** A folder: its name, and up to four of its pictures on the card. Opens the library on that folder. */
    fun pinFolder(c: Context, id: String, name: String, arts: List<Bitmap>): Boolean =
        pin(c, "f:$id", name, Intent(Intent.ACTION_VIEW, folderLink(id)).setClass(c, LibraryActivity::class.java)) { w, h -> card(c, name, 0xFF334155.toInt(), arts.take(4), w, h) }

    /** TV Home takes a pin at once or refuses it (its dock is full); the system keeps only the accepted ones. True when [sid] is not pinned a moment after asking. */
    fun gameRefused(c: Context, id: String) = !pinned(c, "g:$id")
    fun folderRefused(c: Context, id: String) = !pinned(c, "f:$id")
    private fun pinned(c: Context, sid: String): Boolean =
        try { c.getSystemService(ShortcutManager::class.java)?.pinnedShortcuts?.any { it.id == sid } == true } catch (e: Exception) { true }

    /** [card] drawn twice: at the system's icon size for any launcher, at the tile's size for TV Home ([PinArt]). */
    private fun pin(c: Context, sid: String, name: String, intent: Intent, card: (Int, Int) -> Bitmap): Boolean {
        if (!can(c)) return false
        val sm = c.getSystemService(ShortcutManager::class.java) ?: return false
        dev.tvremote.tvui.PinArt.save(c, sid, card(dev.tvremote.tvui.PinArt.W, dev.tvremote.tvui.PinArt.H))
        val iw = (try { sm.iconMaxWidth } catch (e: Exception) { 0 }).takeIf { it > 0 }?.coerceIn(128, 320) ?: 320
        val s = ShortcutInfo.Builder(c, sid).setShortLabel(name.take(24)).setLongLabel(name).setIcon(Icon.createWithBitmap(card(iw, iw * 9 / 16))).setIntent(intent).build()
        return try { sm.updateShortcuts(listOf(s)); sm.requestPinShortcut(s, null) } catch (e: Exception) { false }
    }

    /** A folder renamed: its pin (if any) takes the new name. Deleted: its pin is turned off and the home screen drops it. */
    fun renamed(c: Context, id: String, name: String) {
        if (Build.VERSION.SDK_INT < 26) return
        val sm = c.getSystemService(ShortcutManager::class.java) ?: return
        val old = try { sm.pinnedShortcuts.firstOrNull { it.id == "f:$id" } } catch (e: Exception) { null } ?: return
        try { sm.updateShortcuts(listOf(ShortcutInfo.Builder(c, old.id).setShortLabel(name.take(24)).setLongLabel(name).build())) } catch (e: Exception) { }
    }
    fun gameDeleted(c: Context, id: String) {
        if (Build.VERSION.SDK_INT < 26) return
        try { c.getSystemService(ShortcutManager::class.java)?.disableShortcuts(listOf("g:$id")) } catch (e: Exception) { }; dev.tvremote.tvui.PinArt.drop(c, "g:$id")
    }

    fun deleted(c: Context, id: String) {
        if (Build.VERSION.SDK_INT < 26) return
        try { c.getSystemService(ShortcutManager::class.java)?.disableShortcuts(listOf("f:$id")) } catch (e: Exception) { }; dev.tvremote.tvui.PinArt.drop(c, "f:$id")
    }

    /** One picture fills the 16:9 card; two to four go in a grid; none: the colour with the initials. Square corners: the launcher rounds them. */
    private fun card(c: Context, name: String, color: Int, arts: List<Bitmap>, w: Int, h: Int): Bitmap {
        val u = w / 320f                                               // the sizes below were made for 320 px
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        fun fill(bm: Bitmap, r: RectF) {                                // cropped to fill, centred
            val k = maxOf(r.width() / bm.width, r.height() / bm.height)
            val sw = r.width() / k; val sh = r.height() / k
            val sx = (bm.width - sw) / 2; val sy = (bm.height - sh) / 2
            cv.drawBitmap(bm, android.graphics.Rect(sx.toInt(), sy.toInt(), (sx + sw).toInt(), (sy + sh).toInt()), r, p)
        }
        if (arts.isEmpty()) {
            val dark = Color.rgb(Color.red(color) * 45 / 100, Color.green(color) * 45 / 100, Color.blue(color) * 45 / 100)
            p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), color, dark, Shader.TileMode.CLAMP); cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p); p.shader = null
            p.color = Color.WHITE; p.textSize = 72f * u; p.typeface = Typeface.DEFAULT_BOLD; p.textAlign = Paint.Align.CENTER
            cv.drawText(ThumbNames.initials(name), w / 2f, h / 2f + 26f * u, p)
        } else if (arts.size == 1) fill(arts[0], RectF(0f, 0f, w.toFloat(), h.toFloat()))
        else {
            cv.drawColor(0xFF10131A.toInt())
            val cw = w / 2f; val ch = h / 2f
            arts.forEachIndexed { i, bm -> val x = (i % 2) * cw; val y = (i / 2) * ch; fill(bm, RectF(x + 2 * u, y + 2 * u, x + cw - 2 * u, y + ch - 2 * u)) }
        }
        return b
    }
}
