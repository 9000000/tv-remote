package dev.tvremote.tvui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * The focus as one pane of glass that glides from tile to tile (the idea of cleanyourtv's launcher, drawn our own way): a bright
 * rim, a faint glow round it and a sheen on the top part only, no fill, so a banner keeps its colours under it. It is drawn ONCE
 * into a texture the size of the largest tile, and a move only changes its translation and scale: no redraw while it glides (a
 * pane redrawn every frame crawled on a MediaTek MT5895). All tiles are 16:9, so a uniform scale fits it to any of them; another
 * shape (a widget card, the header, a round button) is drawn once for that shape (its glow kept, [HALOS]) and the pane is there at
 * once, faded in, instead of gliding out of shape. In a FrameLayout over the tiles inside the scrolled content, so a scroll carries
 * it along with its tile.
 */
class Glass(c: Context, private val dp: (Number) -> Int) : View(c) {
    // the pane is white light only; what changes is how much of it shows
    private fun white(a: Int) = (a shl 24) or 0xFFFFFF
    private fun whites(vararg a: Int) = IntArray(a.size) { white(a[it]) }
    private val pad = dp(14)                                         // room for the glow round the pane
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat() }
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat() }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(3).toFloat(); color = white(0x38)
        maskFilter = BlurMaskFilter(dp(6).toFloat(), BlurMaskFilter.Blur.NORMAL)
    }
    private val pic = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private var halo: Bitmap? = null                                 // the glow, blurred once in software for this size
    private val halos = LinkedHashMap<Long, Bitmap>()                // the last few shapes' glows: tiles, a card, the header and back costs no blur
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val at = Rect()
    private var baseW = 0                                            // the pane's size at scale 1 (the largest of this shape seen)
    private var baseH = 0
    private var radius = 0f
    private var shown = false
    private var atX = 0f; private var atY = 0f                      // where it rests, for [nudge]

    init {
        pivotX = 0f; pivotY = 0f; alpha = 0f
        isFocusable = false; isClickable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        // the rim lit from the top left as iOS's glass is: bright where the light meets the corners (top left, bottom right),
        // dim along the sides; the hairline inside it, the other way round, is the far edge of the glass seen through it
        val cx = w / 2f; val cy = h / 2f
        val a = Math.toDegrees(Math.atan2((h - 2.0 * pad), (w - 2.0 * pad))).toFloat() / 360f   // a corner's angle on the sweep
        rim.shader = SweepGradient(cx, cy, whites(0x8C, 0xFF, 0x59, 0x59, 0xFF, 0xA6, 0x8C),
            floatArrayOf(0f, a, 0.25f + a / 2, 0.5f, 0.5f + a, 0.75f, 1f))
        inner.shader = SweepGradient(cx, cy, whites(0x14, 0x0A, 0x40, 0x14, 0x0A, 0x40, 0x14),
            floatArrayOf(0f, a, 0.25f, 0.5f, 0.5f + a, 0.75f, 1f))
        sheen.shader = LinearGradient(0f, pad.toFloat(), 0f, pad + (h - 2 * pad) * 0.5f, white(0x47), white(0), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return                         // drawn once before its first layout: no bitmap of 0 px
        box.set(pad.toFloat(), pad.toFloat(), (width - pad).toFloat(), (height - pad).toFloat())
        // the glow: one ring blurred (a real Gaussian), not three fading rings, whose steps showed as separate bands. Blurred
        // on a software canvas once per size (BlurMaskFilter is not drawn by the GPU before Android 9), then only pasted
        val key = (width.toLong() shl 32) or (height.toLong() shl 12) or radius.toLong().coerceIn(0, 4095)
        val h = halo?.takeIf { it.width == width && it.height == height } ?: (halos[key] ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawRoundRect(box, radius, radius, glow); drawRoundRect(box, radius, radius, CUT) }   // outward only: inside, it hazed the banner's edge
            halos[key] = it
            if (halos.size > HALOS) halos.remove(halos.keys.first())?.recycle()
        }).also { halo = it }
        canvas.drawBitmap(h, 0f, 0f, pic)
        canvas.save()
        canvas.clipRect(box.left, box.top, box.right, box.top + box.height() * 0.5f)   // clipRect stays on the GPU; clipPath did not
        canvas.drawRoundRect(box, radius, radius, sheen)
        canvas.restore()
        canvas.drawRoundRect(box, radius, radius, rim)
        val i = dp(2.5f).toFloat()
        canvas.drawRoundRect(box.left + i, box.top + i, box.right - i, box.bottom - i, radius - i, radius - i, inner)
    }

    /**
     * Onto [face] (a tile's picture) grown by [grow] round its centre, as the tile is when focused; [r] its corner radius.
     * [glide] false: there at once (the first showing, motion off, a layout that moved the tiles).
     */
    fun to(content: ViewGroup, face: View, grow: Float, r: Float, glide: Boolean) {
        if (face.width == 0) { face.post { if (face.isAttachedToWindow) to(content, face, grow, r, false) }; return }
        at.set(0, 0, face.width, face.height)
        content.offsetDescendantRectToMyCoords(face, at)
        val tw = face.width * grow
        val same = baseH > 0 && Math.abs(face.width.toFloat() / face.height - baseW.toFloat() / baseH) < 0.02f && r == radius
        var jump = false
        if (!same || face.width > baseW) {                            // a larger tile than any so far, or another shape: a new texture, drawn once
            jump = !same && shown                                     // scaled out of shape on the way the rim and corners smeared: there at once
            baseW = face.width; baseH = Math.round(baseW.toFloat() * face.height / face.width); radius = r; halo = null
            layoutParams = FrameLayout.LayoutParams(baseW + 2 * pad, baseH + 2 * pad)
            invalidate()
        }
        val k = tw / baseW
        val x = at.exactCenterX() - (baseW + 2 * pad) * k / 2f
        val y = at.exactCenterY() - (baseH + 2 * pad) * k / 2f
        atX = x; atY = y
        animate().cancel()
        if (jump) { shown = false; alpha = 0f }
        if (!glide || !shown) {
            translationX = x; translationY = y; scaleX = k; scaleY = k
            if (!shown) { shown = true; if (glide) animate().alpha(1f).setDuration(180).start() else alpha = 1f }
            else alpha = 1f                                           // a re-place (a layout, a scroll) cancelled the fade in: it stayed see-through
            return
        }
        alpha = 1f
        animate().translationX(x).translationY(y).scaleX(k).scaleY(k).setDuration(Motion.ms(460)).setInterpolator(Motion.spring).start()
    }

    /** With its tile, leaning [px], [py] at an edge and wobbling back ([Tile.nudge]). */
    fun nudge(px: Float, py: Float) {
        if (!shown) return
        animate().cancel()
        animate().translationX(atX + px).translationY(atY + py).setDuration(80).setInterpolator(Motion.glide).withEndAction {
            animate().translationX(atX).translationY(atY).setDuration(460).setInterpolator(Motion.bounce).start()
        }.start()
    }

    /** Focus left the tiles (the buttons, a panel): the pane fades and the next tile it lands on gets it at once. */
    fun hide() {
        if (!shown) return
        shown = false
        animate().cancel()
        animate().alpha(0f).setDuration(140).start()
    }

    val placed get() = shown

    private companion object { const val HALOS = 4; val CUT = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) } }
}
