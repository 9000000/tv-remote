package dev.tvremote.tvui

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import java.io.File
import java.security.MessageDigest

/**
 * A pinned shortcut's picture, sharp. Android shrinks every shortcut icon to its max icon size (127 px on the FPT box, ~192 elsewhere),
 * so a game's 16:9 card reached TV Home as 127x71 and was stretched to a ~370 px focused tile: the blur. The app that pins also keeps the
 * card at the largest tile's size here (4 columns on a 1080p TV, focused; TV Home draws it at its own tile's size), and TV Home reads it through this provider (`<package>.pinart`, read only, TV Home only);
 * no file (an older app, a launcher that is not ours): the system's icon, as before.
 */
object PinArt {
    const val W = 640; const val H = 360

    private fun dir(c: Context) = File(c.filesDir, "pinart")
    private fun name(sid: String) = MessageDigest.getInstance("SHA-1").digest(sid.toByteArray()).joinToString("") { "%02x".format(it) }.take(20) + ".png"

    /** Kept next to the pin request; [card] at [W]x[H]. */
    fun save(c: Context, sid: String, card: Bitmap) {
        try { dir(c).mkdirs(); File(dir(c), name(sid)).outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (e: Exception) { }
    }

    fun drop(c: Context, sid: String) { File(dir(c), name(sid)).delete() }

    /** TV Home's side: [pkg]'s picture for its shortcut [sid], at least [width] px wide (halved while that holds), or null. Blocking (a binder call and a decode): off the main thread. */
    fun read(c: Context, pkg: String, sid: String, width: Int = W): Bitmap? = try {
        var n = 1; while (W / (n * 2) >= width) n *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = n }
        c.contentResolver.openInputStream(Uri.parse("content://$pkg.pinart/" + Uri.encode(sid)))?.use { BitmapFactory.decodeStream(it, null, o) }
    } catch (e: Exception) { null }

    /** Declared by each app that pins (authority `${applicationId}.pinart`, exported): opens one card, read only, for TV Home. */
    class Provider : ContentProvider() {
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
            val c = context ?: return null
            if (mode != "r") return null
            val caller = c.packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
            if (HOME !in caller && c.packageName !in caller) return null      // the games and channels a person keeps are theirs: not for any app
            val sid = uri.lastPathSegment ?: return null
            val f = File(dir(c), name(sid)).takeIf { it.isFile } ?: return null
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun onCreate() = true
        override fun getType(uri: Uri) = "image/png"
        override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
        override fun insert(u: Uri, v: ContentValues?): Uri? = null
        override fun delete(u: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    }

    private const val HOME = "dev.tvremote.home"
}
