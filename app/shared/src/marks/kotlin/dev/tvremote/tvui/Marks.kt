package dev.tvremote.tvui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan

/**
 * Plain text marks (✓ ✗ ⚠ ▶ ...) drawn as the app's line icons. A TV font may have no glyph for them, or draw them at its own
 * size and baseline (app/DESIGN-TV.md §5), so a mark inside words becomes an icon span of the text's own height and colour.
 * Words keep their mark in the i18n files (the web page turns them into icons too); the native screens turn them here.
 */
object Marks {
    /** The marks that have a line icon; a mark followed by U+FE0F is a colour emoji and stays. */
    const val SET = "✓✔✗✕✖⏻⏹⏸▶♪↻↩★☆⚠⚙⬇⌫"

    fun isMark(s: CharSequence, i: Int) = SET.indexOf(s[i]) >= 0 && !(i + 1 < s.length && s[i + 1] == '️')

    /** The first mark of [s] when it leads the words (after spaces), else null: "which kind of line is this". */
    fun lead(s: CharSequence): Char? = s.indexOfFirst { it != ' ' }.let { if (it >= 0 && isMark(s, it)) s[it] else null }

    /** [s] without its marks, for a toast or a notification where no icon can go: the words say it. */
    fun plain(s: String): String = buildString { s.forEachIndexed { i, ch -> if (!isMark(s, i)) append(ch) } }.trim().replace("  ", " ")

    /** [s] with each mark an icon span; [icon] gives the drawable id of a mark (0 = leave the character). The icon takes the colour the text is drawn in, whenever it is drawn (focus changes it). */
    fun text(c: Context, s: CharSequence, icon: (Char) -> Int): SpannableStringBuilder {
        val out = SpannableStringBuilder(s)
        for (i in s.indices) if (isMark(s, i)) {
            val id = icon(s[i]); if (id == 0) continue
            val d = c.getDrawable(id)!!.mutate()
            out.setSpan(IconSpan(d), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    /** One square icon as wide as the line's text height, centred on it. */
    private class IconSpan(val d: android.graphics.drawable.Drawable) : ReplacementSpan() {
        override fun getSize(p: Paint, t: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int = side(p)
        override fun draw(cv: Canvas, t: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, p: Paint) {
            val s = side(p); val mid = (y + p.fontMetrics.ascent / 2 + p.fontMetrics.descent / 2).toInt()   // the middle of the letters
            d.setBounds(0, 0, s, s); d.setTint(p.color)
            cv.save(); cv.translate(x, (mid - s / 2).toFloat()); d.draw(cv); cv.restore()
        }
        private fun side(p: Paint) = (p.textSize * 1.05f).toInt()
    }
}
