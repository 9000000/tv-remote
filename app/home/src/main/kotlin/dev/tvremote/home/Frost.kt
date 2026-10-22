package dev.tvremote.home

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
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
    private val at = IntArray(2)
    private val box = RectF()
    private val place = Matrix()
    private val copy = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private var shaded: Bitmap? = null

    override fun onBoundsChange(b: android.graphics.Rect) {
        glass.bounds = b
        box.set(b)
    }

    override fun draw(c: Canvas) {
        val soft = Frost.soft
        val v = callback as? View
        if (soft != null && v != null) {
            // the copy as a shader filling the rounded box, not drawn through clipPath: on a BRAVIA (MT5891, Android 9) the path's
            // stencil took each widget's redraw while the home scrolled to 73 ms frames (42 ms without the copy)
            if (shaded !== soft) { copy.shader = BitmapShader(soft, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP); shaded = soft }
            v.getLocationInWindow(at)
            place.setScale(screenW / soft.width, screenH / soft.height); place.postTranslate(-at[0].toFloat(), -at[1].toFloat())
            copy.shader.setLocalMatrix(place)
            c.drawRoundRect(box, radius, radius, copy)
            c.drawRoundRect(box, radius, radius, DARK)                // as dark as the band's copy: white words stay white
        }
        glass.draw(c)
    }

    override fun setAlpha(alpha: Int) { glass.alpha = alpha }
    override fun setColorFilter(f: ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        private val DARK = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x70000000 }
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
