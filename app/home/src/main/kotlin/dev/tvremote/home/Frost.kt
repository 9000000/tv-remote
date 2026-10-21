package dev.tvremote.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import java.util.WeakHashMap

/**
 * The glass of a card (a light fill, a hairline edge) over the wallpaper's blurred copy at the card's place on the screen, as the
 * dock band has it: over a busy photo a widget's words were hard to read. The copy is [Walls.soft] (1/16 of the screen, stretched:
 * no real blur, nothing for the GPU to do); no copy (a gradient), the glass alone.
 */
class Frost(private val radius: Float, stroke: Int) : Drawable() {
    private val glass = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x2EFFFFFF, 0x14FFFFFF)).apply {
        cornerRadius = radius; setStroke(stroke, 0x33FFFFFF)
    }
    private val clip = Path()
    private val at = IntArray(2)
    private val to = RectF()

    override fun onBoundsChange(b: android.graphics.Rect) {
        glass.bounds = b
        clip.reset(); clip.addRoundRect(RectF(b), radius, radius, Path.Direction.CW)
    }

    override fun draw(c: Canvas) {
        val soft = Frost.soft
        val v = callback as? View
        if (soft != null && v != null) {
            v.getLocationInWindow(at)
            c.save(); c.clipPath(clip)
            to.set(-at[0].toFloat(), -at[1].toFloat(), -at[0] + screenW, -at[1] + screenH)
            c.drawBitmap(soft, null, to, PAINT)
            c.drawColor(0x70000000)                                   // as dark as the band's copy: white words stay white
            c.restore()
        }
        glass.draw(c)
    }

    override fun setAlpha(alpha: Int) { glass.alpha = alpha }
    override fun setColorFilter(f: ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        private val PAINT = Paint(Paint.FILTER_BITMAP_FLAG)
        private val views = WeakHashMap<View, Boolean>()
        @JvmStatic var soft: Bitmap? = null; private set
        private var screenW = 0f
        private var screenH = 0f

        /** A card drawn with it, so a new copy or a scroll redraws it. */
        fun on(v: View, radius: Float, stroke: Int) { v.background = Frost(radius, stroke); views[v] = true }

        /** The wallpaper's blurred copy (null: none), for a screen of [w] x [h]. */
        fun show(b: Bitmap?, w: Int, h: Int) { soft = b; screenW = w.toFloat(); screenH = h.toFloat(); moved() }

        /** The cards moved on the screen (a scroll): each draws its part of the copy again. */
        fun moved() { if (soft != null) for (v in views.keys.toList()) v.invalidate() }
    }
}
