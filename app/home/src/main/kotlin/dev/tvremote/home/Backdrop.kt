package dev.tvremote.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * The wallpaper on a surface of its own behind the window, painted once (at the screen's full size, dithered) and then only
 * composed by the system. As the window's background it was filled again on every frame focus moved: drawn live a gradient
 * cost an FPT Play box 60 → 40 frames a second, a small stretched picture of it showed bands ~20 px wide, a photo with its shade
 * 42 ms a frame. Here the window draws only what moves. Changing it (a new choice, the rotation) switches at once: a cross-fade
 * (two full-size bitmaps blended on a thread) stalled the FPT box's main thread for up to a second, so there is none.
 */
class Backdrop(c: Context) : SurfaceView(c), SurfaceHolder.Callback {
    /** What is painted: a gradient, a drawn scene ([art], WallArt) or a photo over [colors] (any size: cropped to cover, like CENTER_CROP). */
    class Look(val colors: IntArray, val photo: Bitmap? = null, val art: String? = null)

    private var look = Look(intArrayOf(0xFF000000.toInt()))
    private var ready = false
    private val pic = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val lock = Any()

    init {
        holder.setFormat(PixelFormat.RGBX_8888)                      // the default is RGB_565: every gradient would band
        holder.addCallback(this)
    }

    fun show(colors: IntArray, photo: Bitmap?, art: String? = null) = show(Look(colors, photo, art))

    /** [l] painted at once if the surface is up. */
    fun show(l: Look) {
        synchronized(lock) { look = l }
        paint()
    }

    private fun paint() {
        if (!ready) return
        synchronized(lock) {
            val c: Canvas = try { holder.lockCanvas() } catch (e: Exception) { null } ?: return
            try { draw(c, c.width, c.height, look) }
            finally { try { holder.unlockCanvasAndPost(c) } catch (e: Exception) { } }
        }
    }

    private fun draw(c: Canvas, w: Int, h: Int, l: Look) {
        val p = l.photo
        val art = l.art
        if (art != null && WallArt.has(art)) {
            WallArt.paint(c, w, h, art)
            gradient(GradientDrawable.Orientation.TOP_BOTTOM, shade(WallArt.shade(art)), w, h).draw(c)
        } else if (p == null) {
            if (l.colors.all { it == l.colors[0] }) c.drawColor(l.colors[0])
            else gradient(GradientDrawable.Orientation.TL_BR, l.colors, w, h).draw(c)
        } else {
            val k = maxOf(w.toFloat() / p.width, h.toFloat() / p.height)
            val sw = (w / k).toInt(); val sh = (h / k).toInt()
            c.drawBitmap(p, Rect((p.width - sw) / 2, (p.height - sh) / 2, (p.width + sw) / 2, (p.height + sh) / 2), Rect(0, 0, w, h), pic)
            gradient(GradientDrawable.Orientation.TOP_BOTTOM, SHADE, w, h).draw(c)   // words stay readable over any photo
        }
    }

    private fun gradient(o: GradientDrawable.Orientation, cs: IntArray, w: Int, h: Int) =
        GradientDrawable(o, cs).apply { setBounds(0, 0, w, h); setDither(true) }

    override fun surfaceCreated(h: SurfaceHolder) { ready = true }
    override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, hh: Int) { paint() }
    override fun surfaceDestroyed(h: SurfaceHolder) { ready = false }

    companion object {
        val SHADE = intArrayOf(0xB0000000.toInt(), 0x40000000, 0x90000000.toInt())
        /** [SHADE] at [k] of its strength (a drawn scene is made dark enough already, a photo is not). */
        fun shade(k: Float) = IntArray(SHADE.size) { (((SHADE[it] ushr 24) * k).toInt() shl 24) }
    }
}
