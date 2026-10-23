package dev.tvremote.core

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.InputStream

/**
 * A phone's photo is often stored lying down with an EXIF tag saying how to turn it (an iPhone's portrait shots: Orientation 6,
 * turn 90°). BitmapFactory ignores the tag, so a welcome picture sent from an iPhone showed on its side; this reads the tag and
 * turns the picture. Pictures that went through a page's canvas (wallpapers, drawing) are upright already: the browser turned
 * them. Android only (the computer app's build leaves this file out).
 */
object Upright {
    /** The EXIF orientation of the file, 1 (as stored) when there is none or it cannot be read. */
    fun read(path: String): Int = try { ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } catch (e: Exception) { 1 }

    /** The same from a stream (Android 7+; older ones keep the picture as stored). The stream is not closed. */
    fun read(input: InputStream): Int = if (android.os.Build.VERSION.SDK_INT < 24) 1
        else try { ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } catch (e: Exception) { 1 }

    /** Width and height swap once turned (5 to 8: a quarter turn): what a sample size is worked out on. */
    fun sideways(o: Int) = o in 5..8

    /** [b] turned (and mirrored) as [o] says; [b] itself when nothing is to be done, else a new bitmap and [b] recycled. */
    fun apply(b: Bitmap, o: Int): Bitmap {
        val m = Matrix()
        when (o) {
            2 -> m.setScale(-1f, 1f)
            3 -> m.setRotate(180f)
            4 -> { m.setRotate(180f); m.postScale(-1f, 1f) }
            5 -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            6 -> m.setRotate(90f)
            7 -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            8 -> m.setRotate(-90f)
            else -> return b
        }
        val out = try { Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true) } catch (e: OutOfMemoryError) { return b }
        if (out !== b) b.recycle()
        return out
    }
}
