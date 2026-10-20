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
 * 42 ms a frame. Here the window draws only what moves.
 */
class Backdrop(c: Context) : SurfaceView(c), SurfaceHolder.Callback {
    private var colors = intArrayOf(0xFF000000.toInt())
    private var photo: Bitmap? = null
    private var ready = false
    private val pic = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val src = Rect()
    private val dst = Rect()

    init {
        holder.setFormat(PixelFormat.RGBX_8888)                      // the default is RGB_565: every gradient would band
        holder.addCallback(this)
    }

    /** [photo] over [colors] when there is one (any size: it is cropped to cover, like CENTER_CROP). Painted at once if the surface is up. */
    fun show(colors: IntArray, photo: Bitmap?) {
        this.colors = colors; this.photo = photo
        paint()
    }

    private fun paint() {
        if (!ready) return
        val c: Canvas = try { holder.lockCanvas() } catch (e: Exception) { null } ?: return
        try {
            val w = c.width; val h = c.height
            val p = photo
            if (p == null) {
                if (colors.all { it == colors[0] }) c.drawColor(colors[0])
                else gradient(GradientDrawable.Orientation.TL_BR, colors, w, h).draw(c)
            } else {
                val k = maxOf(w.toFloat() / p.width, h.toFloat() / p.height)
                val sw = (w / k).toInt(); val sh = (h / k).toInt()
                src.set((p.width - sw) / 2, (p.height - sh) / 2, (p.width + sw) / 2, (p.height + sh) / 2)
                dst.set(0, 0, w, h)
                c.drawBitmap(p, src, dst, pic)
                gradient(GradientDrawable.Orientation.TOP_BOTTOM, SHADE, w, h).draw(c)   // words stay readable over any photo
            }
        } finally {
            try { holder.unlockCanvasAndPost(c) } catch (e: Exception) { }
        }
    }

    private fun gradient(o: GradientDrawable.Orientation, cs: IntArray, w: Int, h: Int) =
        GradientDrawable(o, cs).apply { setBounds(0, 0, w, h); setDither(true) }

    override fun surfaceCreated(h: SurfaceHolder) { ready = true }
    override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, hh: Int) { paint() }
    override fun surfaceDestroyed(h: SurfaceHolder) { ready = false }

    companion object {
        val SHADE = intArrayOf(0xB0000000.toInt(), 0x40000000, 0x90000000.toInt())
    }
}
