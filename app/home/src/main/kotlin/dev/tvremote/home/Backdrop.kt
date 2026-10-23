package dev.tvremote.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.view.View

/**
 * The wallpaper, painted once at the screen's full size (dithered) into one picture that is [target]'s background (the window's).
 * Drawn live there, a gradient cost an FPT Play box 60 → 40 frames a second and a photo with its shade 42 ms a frame; a small
 * stretched picture showed bands ~20 px wide. On a SurfaceView of its own behind the window instead, a BRAVIA (MT5891, Android 9)
 * blended both full-screen layers on its GPU every frame: 45 ms a frame while focus moved, 9.5 ms with this one picture (the FPT
 * box: 15.8 ms either way). Changing it (a new choice, the rotation) switches at once: a cross-fade (two full-size bitmaps
 * blended on a thread) stalled the FPT box's main thread for up to a second, so there is none.
 */
class Backdrop(private val target: View) {
    /** What is painted: a gradient, a drawn scene ([art], WallArt) or a photo over [colors] (any size: cropped to cover, like CENTER_CROP). */
    class Look(val colors: IntArray, val photo: Bitmap? = null, val art: String? = null)

    /** The person's veil ([HomeConfig] "shade" / 100), for the next [show]. */
    var strength = 1f
    private val pic = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private var made: Bitmap? = null

    fun show(colors: IntArray, photo: Bitmap?, art: String? = null) = show(Look(colors, photo, art))

    /** [l] painted at once, over the one picture kept (8 MB at 1080p: not a new one per change). */
    fun show(l: Look) {
        val dm = target.resources.displayMetrics
        val w = dm.widthPixels; val h = dm.heightPixels
        val b = made?.takeIf { it.width == w && it.height == h } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { made = it }   // 565 would band every gradient
        draw(Canvas(b), w, h, l)
        target.background = BitmapDrawable(target.resources, b).apply { setDither(true) }
        target.invalidate()
    }

    private fun draw(c: Canvas, w: Int, h: Int, l: Look) {
        val p = l.photo
        val art = l.art
        if (art != null && WallArt.has(art)) {
            WallArt.paint(c, w, h, art)
            gradient(GradientDrawable.Orientation.TOP_BOTTOM, shade(WallArt.shade(art), strength), w, h).draw(c)
        } else if (p == null) {
            if (l.colors.all { it == l.colors[0] }) c.drawColor(l.colors[0])
            else gradient(GradientDrawable.Orientation.TL_BR, l.colors, w, h).draw(c)
        } else {
            val k = maxOf(w.toFloat() / p.width, h.toFloat() / p.height)
            val sw = (w / k).toInt(); val sh = (h / k).toInt()
            c.drawBitmap(p, Rect((p.width - sw) / 2, (p.height - sh) / 2, (p.width + sw) / 2, (p.height + sh) / 2), Rect(0, 0, w, h), pic)
            gradient(GradientDrawable.Orientation.TOP_BOTTOM, shade(1f, strength), w, h).draw(c)   // words stay readable over any photo
        }
    }

    private fun gradient(o: GradientDrawable.Orientation, cs: IntArray, w: Int, h: Int) =
        GradientDrawable(o, cs).apply { setBounds(0, 0, w, h); setDither(true) }

    companion object {
        val SHADE = intArrayOf(0xB0000000.toInt(), 0x40000000, 0x90000000.toInt())
        /**
         * [SHADE] at [k] of its strength (a drawn scene is made dark enough already, a photo is not), then the person's [user]
         * ("shade" / 100): under 1 lighter, over 1 each stop goes towards [DARKEST], so the middle (light at 1, where a busy page
         * shows most) darkens most and 2 is an even veil.
         */
        fun shade(k: Float, user: Float = 1f) = IntArray(SHADE.size) {
            val a = (SHADE[it] ushr 24) * k
            val u = if (user <= 1f) a * user else a + (DARKEST - a).coerceAtLeast(0f) * minOf(user - 1f, 1f)
            u.toInt().coerceIn(0, 255) shl 24
        }
        private const val DARKEST = 0xE0.toFloat()
    }
}
