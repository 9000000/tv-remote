package dev.tvremote.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * The focus as one pane of glass that glides from tile to tile (the idea of cleanyourtv's launcher, drawn our own way): a bright
 * rim, a faint glow round it and a sheen on the top part only, no fill, so a banner keeps its colours under it. It is drawn ONCE
 * into a texture the size of the largest tile, and a move only changes its translation and scale: no redraw while it glides (a
 * pane redrawn every frame crawled on a MediaTek MT5895). All tiles are 16:9, so a uniform scale fits it to any of them.
 * In a FrameLayout over the tiles inside the scrolled content, so a scroll carries it along with its tile.
 */
class Glass(c: Context, private val dp: (Number) -> Int) : View(c) {
    private val pad = dp(8)                                          // room for the glow round the pane
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); color = 0xE6FFFFFF.toInt() }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val at = Rect()
    private var baseW = 0                                            // the pane's width at scale 1 (the largest tile seen)
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
        sheen.shader = LinearGradient(0f, pad.toFloat(), 0f, pad + (h - 2 * pad) * 0.5f, 0x47FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        box.set(pad.toFloat(), pad.toFloat(), (width - pad).toFloat(), (height - pad).toFloat())
        for (i in 1..3) {                                             // the glow: three widening, fading rings
            val o = i * dp(1.5f).toFloat()
            glow.strokeWidth = dp(2).toFloat() * i
            glow.color = ((0x2E / i) shl 24) or 0xFFFFFF
            canvas.drawRoundRect(box.left - o, box.top - o, box.right + o, box.bottom + o, radius + o, radius + o, glow)
        }
        canvas.save()
        canvas.clipRect(box.left, box.top, box.right, box.top + box.height() * 0.5f)   // clipRect stays on the GPU; clipPath did not
        canvas.drawRoundRect(box, radius, radius, sheen)
        canvas.restore()
        canvas.drawRoundRect(box, radius, radius, rim)
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
        if (face.width > baseW || r != radius) {                      // a larger tile than any so far: a new texture, drawn once
            baseW = maxOf(baseW, face.width); radius = r
            layoutParams = FrameLayout.LayoutParams(baseW + 2 * pad, baseW * 9 / 16 + 2 * pad)
            invalidate()
        }
        val k = tw / baseW
        val x = at.exactCenterX() - (baseW + 2 * pad) * k / 2f
        val y = at.exactCenterY() - (baseW * 9 / 16 + 2 * pad) * k / 2f
        atX = x; atY = y
        animate().cancel()
        if (!glide || !shown) {
            translationX = x; translationY = y; scaleX = k; scaleY = k
            if (!shown) { shown = true; if (glide) animate().alpha(1f).setDuration(180).start() else alpha = 1f }
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
}
