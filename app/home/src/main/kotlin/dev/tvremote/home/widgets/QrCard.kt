package dev.tvremote.home.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import dev.tvremote.home.Font

/**
 * A QR card: the code, dark modules on a white square whatever the wallpaper or theme (a scanner needs the contrast and a white
 * margin), as big as the card lets it be. Only the square is white: the card is the other cards' glass (Widgets puts it in
 * card()), the words white on it; a white slab hid the wallpaper (2026-10-23). The code comes with the caption under it (or
 * beside it in a wide, low card) and, when the person asked, the Wi-Fi password as text. Beside the code the words span its height:
 * the caption at its top, the password (else a line on how to scan) at its bottom; a lone caption at the top looked unfinished,
 * in the middle it floated. Modules are drawn as whole pixels so the edges stay sharp; no bitmap is kept. Only to look at, like
 * the other cards. The text is built by [HomeQr.payload] from the card's fields; nothing here is logged.
 */
class QrCard(c: Context, private val grid: Array<BooleanArray>, private val caption: String, private val secretLabel: String, private val secret: String, private val hint: String = "") : View(c) {
    private val d = resources.displayMetrics.density
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER }
    private val ink = Paint().apply { color = INK }
    private val cap = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = WORDS; setShadowLayer(4 * d, 0f, d, SHADOW); textSize = 15f * d; typeface = Font.bold }
    private val lab = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = LABEL; setShadowLayer(4 * d, 0f, d, SHADOW); textSize = 11f * d; typeface = Font.medium }
    private val sec = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = WORDS; setShadowLayer(4 * d, 0f, d, SHADOW); textSize = 14f * d; typeface = Typeface.MONOSPACE }
    private var text: List<Pair<StaticLayout, Float>> = emptyList()   // each line and its top
    private var box = RectF()                                          // where the code's modules go (a square), set with the text
    private var module = 0f

    /** The module size in px at the card's current size (tests and the report read it; 0 before the first layout). */
    fun modulePx() = module

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val side = w >= h * 1.5f                                       // wide and low: the words go beside the code, spanning its height
        val n = grid.size + 2 * QUIET                                  // the code and the least white margin; what the whole pixels leave over widens the margin
        val align = if (side) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER
        fun line(t: String, p: TextPaint, width: Int, lines: Int, where: TextUtils.TruncateAt = TextUtils.TruncateAt.END): StaticLayout =
            if (Build.VERSION.SDK_INT >= 23) StaticLayout.Builder.obtain(t, 0, t.length, p, width).setAlignment(align).setMaxLines(lines).setEllipsize(where).build()
            else StaticLayout(TextUtils.ellipsize(t, p, width * lines.toFloat() - 3 * p.textSize, where), p, width, align, 1f, 0f, false)
        val avail = if (side) minOf(h, (w * 0.6f).toInt()) else w     // beside: the code takes 60 % at most, the words keep the rest
        val textW = if (side) (w - avail - 14 * d).toInt().coerceAtLeast(1) else w
        val top = ArrayList<StaticLayout>()
        val bottom = ArrayList<StaticLayout>()
        if (caption.isNotEmpty()) {
            cap.textSize = 15f * d                                     // a long word ("Homestay") shrinks before it is cut
            while (caption.split(' ').maxOf { cap.measureText(it) } > textW && cap.textSize > 12f * d) cap.textSize -= 0.5f * d
            top += line(caption, cap, textW, 2)
        }
        if (secret.isNotEmpty()) {
            // one line, whatever its length: shrink down to a floor, then cut out of the middle (the ends are what a person checks), never wrap
            val low = side && h < 120 * d                              // a small card: no room for the label line, the password is what matters
            if (secretLabel.isNotEmpty() && !low) bottom += line(secretLabel, lab, textW, 1)
            sec.textSize = 14f * d
            while (sec.measureText(secret) > textW && sec.textSize > 10f * d) sec.textSize -= 0.5f * d
            bottom += line(secret, sec, textW, 1, TextUtils.TruncateAt.MIDDLE)
        } else if (side && hint.isNotEmpty()) bottom += line(hint, lab, textW, 2)   // under the code there is no room to spare for it
        val gap = 6 * d
        fun height(l: List<StaticLayout>) = l.sumOf { it.height } + gap * (l.size - 1).coerceAtLeast(0)
        val textH = height(top + bottom)
        val room = if (side) avail.toFloat() else minOf(avail.toFloat(), h - textH - (if (top.isEmpty() && bottom.isEmpty()) 0f else gap))
        module = Math.floor((room / n).toDouble()).toFloat().coerceAtLeast(1f)
        val size = Math.floor(room.toDouble()).toFloat()               // the white square takes the whole room, not module * n: a 136 px card lost 31 px to the rounding
        val quiet = (size - module * grid.size) / 2
        val x0 = if (side) (avail - size) / 2 else (w - size) / 2
        val y0 = if (side) (h - size) / 2 else ((h - textH - (if (top.isEmpty() && bottom.isEmpty()) 0f else gap)) - size).coerceAtLeast(0f) / 2
        paperAt = RectF(x0, y0, x0 + size, y0 + size)
        box = RectF(Math.round(x0 + quiet).toFloat(), Math.round(y0 + quiet).toFloat(), 0f, 0f)   // only the top left is read: whole pixels, sharp edges
        val placed = ArrayList<Pair<StaticLayout, Float>>()
        var y = if (side) y0 else y0 + size + gap
        for (t in top) { placed += t to y; y += t.height + gap }
        if (side) y = maxOf(y, y0 + size - height(bottom))            // the bottom lines sit on the code's bottom edge, unless the caption needs the room
        for (t in bottom) { placed += t to y; y += t.height + gap }
        text = placed
        textX = if (side) avail + 14 * d else 0f
    }
    private var textX = 0f
    private var paperAt = RectF()

    override fun onDraw(cv: Canvas) {
        cv.drawRoundRect(paperAt, module * 1.5f, module * 1.5f, paper)        // corners round within the margin, never into the code
        for (y in grid.indices) for (x in grid.indices) if (grid[y][x]) {
            val left = Math.round(box.left + x * module).toFloat(); val top = Math.round(box.top + y * module).toFloat()
            cv.drawRect(left, top, left + module, top + module, ink)
        }
        for ((t, y) in text) { cv.save(); cv.translate(textX, y); t.draw(cv); cv.restore() }
    }

    companion object {
        private const val PAPER = 0xFFFFFFFF.toInt()
        private const val INK = 0xFF111111.toInt()
        private const val WORDS = 0xFFFFFFFF.toInt()                     // the caption and the password, on the glass
        private const val LABEL = 0xA6FFFFFF.toInt()                     // the small label over the password: Widgets' DIM
        private const val SHADOW = 0x80000000.toInt()                    // as Widgets.text(): readable on a bright wallpaper
        private const val QUIET = 2                                      // the least modules of white around the code (scanners read 2+); usually more is left over
    }
}
