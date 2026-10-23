package dev.tvremote.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View

/**
 * A QR card: the code, dark modules on a white card whatever the wallpaper or theme (a scanner needs the contrast and a white
 * margin), as big as the card lets it be, with the caption under it (or beside it in a wide, low card) and, when the person asked,
 * the Wi-Fi password as text. Modules are drawn as whole pixels so the edges stay sharp; no bitmap is kept. Only to look at, like
 * the other cards. The text is built by [HomeQr.payload] from the card's fields; nothing here is logged.
 */
class QrCard(c: Context, private val grid: Array<BooleanArray>, private val caption: String, private val secret: String) : View(c) {
    private val d = resources.displayMetrics.density
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER }
    private val ink = Paint().apply { color = INK }
    private val cap = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 14f * d; typeface = Font.medium }
    private val sec = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = PASS; textSize = 13f * d; typeface = Font.regular }
    private val r = RectF()
    private var text: List<StaticLayout> = emptyList()
    private var box = RectF()                                          // where the code's modules go (a square), set with the text
    private var module = 0f

    /** The module size in px at the card's current size (tests and the report read it; 0 before the first layout). */
    fun modulePx() = module

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val pad = 10 * d
        val side = w >= h * 1.5f                                       // wide and low: the words go beside the code
        val n = grid.size + 4                                          // the code and two modules of white around it (the card's padding is the rest of the margin)
        val words = ArrayList<StaticLayout>()
        fun line(t: String, p: TextPaint, width: Int, lines: Int): StaticLayout =
            if (Build.VERSION.SDK_INT >= 23) StaticLayout.Builder.obtain(t, 0, t.length, p, width).setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
            else StaticLayout(TextUtils.ellipsize(t, p, width * lines.toFloat() - 3 * p.textSize, TextUtils.TruncateAt.END), p, width, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false)
        val avail = if (side) (h - 2 * pad).toInt() else (w - 2 * pad).toInt()
        val textW = if (side) (w - 3 * pad - avail).toInt().coerceAtLeast(1) else (w - 2 * pad).toInt()
        if (caption.isNotEmpty()) words += line(caption, cap, textW, 2)
        if (secret.isNotEmpty()) words += line(secret, sec, textW, 3)                   // a long password wraps rather than hides its tail
        val textH = words.sumOf { it.height } + if (words.size > 1) (4 * d).toInt() else 0
        val room = if (side) avail.toFloat() else minOf(avail.toFloat(), h - 2 * pad - textH - (if (words.isEmpty()) 0f else 6 * d))
        module = Math.floor((room / n).toDouble()).toFloat().coerceAtLeast(1f)
        val size = module * n
        val x0 = if (side) pad + (avail - size) / 2 else (w - size) / 2
        val y0 = if (side) (h - size) / 2 else pad + ((h - 2 * pad - textH - (if (words.isEmpty()) 0f else 6 * d)) - size).coerceAtLeast(0f) / 2
        box = RectF(x0 + module * 2, y0 + module * 2, x0 + size - module * 2, y0 + size - module * 2)
        text = words
        textAt = if (side) floatArrayOf(2 * pad + avail, (h - textH) / 2f) else floatArrayOf(pad, y0 + size + 6 * d)
    }
    private var textAt = floatArrayOf(0f, 0f)

    override fun onDraw(cv: Canvas) {
        r.set(0f, 0f, width.toFloat(), height.toFloat())
        cv.drawRoundRect(r, 22 * d, 22 * d, paper)
        for (y in grid.indices) for (x in grid.indices) if (grid[y][x]) {
            val left = Math.round(box.left + x * module).toFloat(); val top = Math.round(box.top + y * module).toFloat()
            cv.drawRect(left, top, left + module, top + module, ink)
        }
        var y = textAt[1]
        for (t in text) {
            cv.save(); cv.translate(textAt[0], y); t.draw(cv); cv.restore()
            y += t.height + 4 * d
        }
    }

    companion object {
        private const val PAPER = 0xFFFFFFFF.toInt()
        private const val INK = 0xFF111111.toInt()
        private const val PASS = 0xFF3A3A3C.toInt()
    }
}
