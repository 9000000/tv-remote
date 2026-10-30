package dev.tvremote.home.wall

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * The photo wallpaper, read at the size it is drawn and no bigger: a phone photo is 4000 px wide, and on a 2 GB box a bitmap
 * of the TV's 1080p x 4 bytes is 8 MB the launcher keeps for as long as it lives. Never over 1920x1080 (a 4K panel draws the
 * same photo scaled): 8 MB, let go while the screen is hidden and memory is short. Not 2 bytes a pixel: that banded skies.
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
            inSampleSize = sample(o.outWidth, o.outHeight, dw, dh); inPreferredConfig = Bitmap.Config.ARGB_8888   // 565 banded every sky and dusk (Backdrop paints it on an 8-bit surface)
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
        return smooth(if (c.isMutable) c else c.copy(Bitmap.Config.ARGB_8888, true).also { if (it !== c && c !== b) c.recycle() })
    }

    /** Three box blurs over the small copy (~120 x 68): shrinking alone left blocks and bright streaks behind a widget's words. */
    private fun smooth(b: Bitmap, r: Int = 2): Bitmap {
        val w = b.width; val h = b.height
        if (w < 2 || h < 2) return b
        var px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        var tmp = IntArray(w * h)
        repeat(3) { box(px, tmp, w, h, r, true); box(tmp, px, w, h, r, false) }
        b.setPixels(px, 0, w, 0, 0, w, h)
        return b
    }

    private fun box(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, across: Boolean) {
        val n = if (across) w else h; val lines = if (across) h else w
        val k = 2 * r + 1
        for (l in 0 until lines) {
            var sr = 0; var sg = 0; var sb = 0
            fun at(i: Int) = src[if (across) l * w + i.coerceIn(0, n - 1) else i.coerceIn(0, n - 1) * w + l]
            for (i in -r..r) { val c = at(i); sr += c shr 16 and 255; sg += c shr 8 and 255; sb += c and 255 }
            for (i in 0 until n) {
                dst[if (across) l * w + i else i * w + l] = (0xFF shl 24) or (sr / k shl 16) or (sg / k shl 8) or (sb / k)
                val o = at(i - r); val e = at(i + r + 1)
                sr += (e shr 16 and 255) - (o shr 16 and 255); sg += (e shr 8 and 255) - (o shr 8 and 255); sb += (e and 255) - (o and 255)
            }
        }
    }

    fun file(c: android.content.Context) = File(c.getExternalFilesDir(null), "wall.jpg")
}
