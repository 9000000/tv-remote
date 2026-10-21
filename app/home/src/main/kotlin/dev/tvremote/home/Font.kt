package dev.tvremote.home

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView

/**
 * TV Home's own letters: Inter (assets/fonts, cut to Latin + Vietnamese, SIL OFL), the same on every TV. "sans-serif" was the
 * TV's: Roboto on Google TV, another face on brands that swap it, with other widths, so a layout checked on one TV moved on the
 * next. A glyph outside the cut (emoji, a Chinese app name) still falls back to the TV's fonts. [load] before the first view.
 */
object Font {
    private var faces: Array<Typeface>? = null

    fun load(c: Context) {
        if (faces != null) return
        faces = try {
            arrayOf("Light", "Regular", "Medium", "Bold").map { Typeface.createFromAsset(c.applicationContext.assets, "fonts/Inter-$it.ttf") }.toTypedArray()
        } catch (e: RuntimeException) { null }                         // a broken APK copy: the TV's own, not a crash
    }

    private fun at(i: Int, sys: Typeface) = faces?.get(i) ?: sys
    val light get() = at(0, Typeface.create("sans-serif-light", Typeface.NORMAL))
    val regular get() = at(1, Typeface.DEFAULT)
    val medium get() = at(2, Typeface.create("sans-serif-medium", Typeface.NORMAL))
    val bold get() = at(3, Typeface.DEFAULT_BOLD)

    /** A TextView in Inter Regular (set a weight after it). */
    fun <T : TextView> on(v: T): T = v.apply { typeface = regular }
}
