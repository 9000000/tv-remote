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
    /** Each mark's line icon, the one map for every app (dev/gen/gen_icons.py writes the ic_* files; tests/marks.test.js checks each
     *  app has them). Full names on purpose: the resource shrinker keeps what getIdentifier is asked for by a literal name. */
    private val ICONS = mapOf(
        '✓' to "ic_check", '✔' to "ic_check", '✗' to "ic_close", '✕' to "ic_close", '✖' to "ic_close", '⚠' to "ic_alert",
        '▶' to "ic_play", '⏻' to "ic_power", '⏹' to "ic_stop", '⏸' to "ic_pause", '♪' to "ic_music", '↻' to "ic_refresh",
        '↩' to "ic_undo", '★' to "ic_star", '☆' to "ic_star", '⚙' to "ic_settings", '⬇' to "ic_download", '⌫' to "ic_backspace")

    /** The marks that have a line icon; a mark followed by U+FE0F is a colour emoji and stays. */
    val SET = ICONS.keys.joinToString("")

    private val ids = HashMap<Char, Int>()

    /** The drawable id of [ch]'s line icon in this app, 0 when it has none (looked up once per mark). */
    fun icon(c: Context, ch: Char): Int = synchronized(ids) {
        ids.getOrPut(ch) { ICONS[ch]?.let { c.resources.getIdentifier(it, "drawable", c.packageName) } ?: 0 }
    }

    fun isMark(s: CharSequence, i: Int) = SET.indexOf(s[i]) >= 0 && !(i + 1 < s.length && s[i + 1] == '️')

    /** The first mark of [s] when it leads the words (after spaces), else null: "which kind of line is this". */
    fun lead(s: CharSequence): Char? = s.indexOfFirst { it != ' ' }.let { if (it >= 0 && isMark(s, it)) s[it] else null }

    /** [s] without its marks, for a toast or a notification where no icon can go: the words say it. */
    fun plain(s: String): String = buildString { s.forEachIndexed { i, ch -> if (!isMark(s, i)) append(ch) } }.trim().replace("  ", " ")

    /** [s] with each mark an icon span (a mark this app has no icon for stays a character). The icon takes the colour the text is drawn in, whenever it is drawn (focus changes it). */
    fun text(c: Context, s: CharSequence): SpannableStringBuilder {
        val out = SpannableStringBuilder(s)
        for (i in s.indices) if (isMark(s, i)) {
            val id = icon(c, s[i]); if (id == 0) continue
            val d = try { c.getDrawable(id)!!.mutate() } catch (e: Exception) { continue }   // its file gone from under a running app (seen once, census ec35e370): the character stays
            out.setSpan(IconSpan(d), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    /** One square icon as wide as the line's text height, centred on it. */
    private class IconSpan(val d: android.graphics.drawable.Drawable) : ReplacementSpan() {
        override fun getSize(p: Paint, t: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            // the font's own line height: left empty, a label that is ONLY a mark (the PIN pad's ⌫ ✕) had a line 0 high, never drawn
            if (fm != null) p.fontMetricsInt.let { m -> fm.ascent = m.ascent; fm.descent = m.descent; fm.top = m.top; fm.bottom = m.bottom; fm.leading = m.leading }
            return side(p)
        }
        override fun draw(cv: Canvas, t: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, p: Paint) {
            val s = side(p); val mid = (y + p.fontMetrics.ascent / 2 + p.fontMetrics.descent / 2).toInt()   // the middle of the letters
            d.setBounds(0, 0, s, s); d.setTint(p.color)
            cv.save(); cv.translate(x, (mid - s / 2).toFloat()); d.draw(cv); cv.restore()
        }
        private fun side(p: Paint) = (p.textSize * 1.05f).toInt()
    }
}
