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
        val cy = height / 2
        art.setBounds(cx - h, cy - h, cx + h, cy + h)
        art.draw(c)
    }
}
