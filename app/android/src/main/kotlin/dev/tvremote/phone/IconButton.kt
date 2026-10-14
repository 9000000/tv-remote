package dev.tvremote.phone

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.widget.Button
import kotlin.math.roundToInt

/** A button that draws an icon in its middle. "@name" = res/drawable/ic_name (dev/gen_icons.py), anything else is shown as text. */
internal class IconButton(c: Context) : Button(c) {
    private var art: Drawable? = null
    var size = 24f                                                    // dp
    /** A small label under the icon. */
    var caption: String? = null
    private val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
        color = 0xFF98A2B3.toInt()
    }
    /** White on a lit (accent) button, where the usual grey is hard to read. */
    var lit = false
        set(v) { field = v; ink.color = if (v) TEXT else 0xFF98A2B3.toInt(); invalidate() }

    fun setIcon(symbol: String, tint: Int) {
        if (!symbol.startsWith("@")) {
            art = null
            text = symbol
            return
        }
        val id = resources.getIdentifier("ic_" + symbol.substring(1), "drawable", context.packageName)
        art = if (id == 0) null else resources.getDrawable(id, null).mutate()
        art?.setTint(tint)
        text = ""
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val art = art ?: return
        val h = (size * resources.displayMetrics.density / 2).roundToInt()
        val cx = width / 2
        var cy = height / 2
        caption?.let {
            val d = resources.displayMetrics.density
            cy -= (7 * d).roundToInt()
            ink.textSize = 10.5f * resources.displayMetrics.scaledDensity
            c.drawText(it, cx.toFloat(), cy + h + 15 * d, ink)
        }
        art.setBounds(cx - h, cy - h, cx + h, cy + h)
        art.draw(c)
    }
}
