package dev.tvremote.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * The photo wallpaper, read at the size it is drawn and no bigger: a phone photo is 4000 px wide, and on a 2 GB box a bitmap
 * of the TV's 1080p x 4 bytes is 8 MB the launcher keeps for as long as it lives. Never over 1920x1080 (a 4K panel draws the
 * same photo scaled), 2 bytes a pixel (a JPEG has no alpha): 4 MB.
 */
object Wall {
    const val MAX_W = 1920
    const val MAX_H = 1080

    /** The size the photo is wanted at for a screen of [w] x [h]: the screen's own, shrunk (same shape) to fit [MAX_W] x [MAX_H]. */
    fun want(w: Int, h: Int): Pair<Int, Int> {
        val k = minOf(1f, MAX_W.toFloat() / w, MAX_H.toFloat() / h)
        return maxOf(1, (w * k).toInt()) to maxOf(1, (h * k).toInt())
    }

    /** The biggest power of two to shrink a [w] x [h] picture by while it still covers [dw] x [dh]. */
    fun sample(w: Int, h: Int, dw: Int, dh: Int): Int {
        var s = 1
        while (w / (s * 2) >= dw && h / (s * 2) >= dh) s *= 2
        return s
    }

    /** The size to scale a [w] x [h] picture to so it covers [dw] x [dh] with nothing to spare (never bigger than it is). */
    fun cover(w: Int, h: Int, dw: Int, dh: Int): Pair<Int, Int> {
        val k = maxOf(dw.toFloat() / w, dh.toFloat() / h)
        return if (k >= 1f) w to h else maxOf(dw, (w * k + 0.5f).toInt()) to maxOf(dh, (h * k + 0.5f).toInt())
    }

    /** [f] ready for a screen of [sw] x [sh]; null when it is not there or cannot be read. Off the main thread. */
    fun load(f: File, sw: Int, sh: Int): Bitmap? = try {
        val (dw, dh) = want(sw, sh)
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        val read = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply {
            inSampleSize = sample(o.outWidth, o.outHeight, dw, dh); inPreferredConfig = Bitmap.Config.RGB_565
        })
        if (read == null) null else {
            val (w, h) = cover(read.width, read.height, dw, dh)
            if (w == read.width && h == read.height) read
            else Bitmap.createScaledBitmap(read, w, h, true).also { if (it !== read) read.recycle() }
        }
    } catch (e: Throwable) { null }

    /** The photo shrunk twice to about 1/16 for the blurred band: the view scales it back up soft. */
    fun soft(b: Bitmap): Bitmap {
        val a = Bitmap.createScaledBitmap(b, maxOf(1, b.width / 4), maxOf(1, b.height / 4), true)
        val c = Bitmap.createScaledBitmap(a, maxOf(1, b.width / 16), maxOf(1, b.height / 16), true)
        if (a !== c && a !== b) a.recycle()
        return c
    }

    fun file(c: android.content.Context) = File(c.getExternalFilesDir(null), "wall.jpg")
}
