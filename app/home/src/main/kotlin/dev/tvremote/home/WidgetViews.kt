package dev.tvremote.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

internal class Ring(private val host: Widgets, c: Context, val part: Float, val colors: IntArray) : View(c) {
    private fun dp(n: Number) = host.dp(n)
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(6).toFloat(); color = 0x26FFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(6).toFloat(); strokeCap = Paint.Cap.ROUND }
    private val r = RectF()
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {          // drawing makes nothing new: weak TVs redraw often
        val i = dp(4).toFloat(); r.set(i, i, w - i, h - i)
        fill.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), colors[0], colors[1], Shader.TileMode.CLAMP)
    }
    override fun onDraw(cv: Canvas) {
        cv.drawArc(r, 0f, 360f, false, track)
        cv.drawArc(r, -90f, 360f * part.coerceIn(0.01f, 1f), false, fill)
    }
}

internal class Bar(private val host: Widgets, c: Context, val part: Float, val colors: IntArray) : View(c) {
    private val back = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val all = RectF(); private val done = RectF()
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        all.set(0f, 0f, w.toFloat(), h.toFloat())
        done.set(0f, 0f, (w * part.coerceIn(0f, 1f)).coerceAtLeast(h.toFloat()), h.toFloat())
        p.shader = host.grad(colors, done.right)
    }
    override fun onDraw(cv: Canvas) {
        val rad = height / 2f
        cv.drawRoundRect(all, rad, rad, back)
        cv.drawRoundRect(done, rad, rad, p)
    }
}
