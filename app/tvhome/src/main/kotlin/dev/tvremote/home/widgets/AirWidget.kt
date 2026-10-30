package dev.tvremote.home.widgets

import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.LayoutParams
import android.widget.LinearLayout.VERTICAL
import dev.tvremote.home.Font
import dev.tvremote.home.R
import dev.tvremote.home.widgets.Widgets.Companion.AMBER
import dev.tvremote.home.widgets.Widgets.Companion.DIM
import dev.tvremote.home.widgets.Widgets.Companion.GREEN
import dev.tvremote.home.widgets.Widgets.Companion.RED
import dev.tvremote.home.widgets.Widgets.Facts

/** The air widget in the glass look: a coloured square (green good .. red bad), the index, the level and PM2.5. [full]: with its title. */
internal fun Widgets.airGlass(widget: LinearLayout, f: Facts, full: Boolean) {
    val x = f.air
    widget.gravity = Gravity.CENTER_VERTICAL
    if (full) widget.addView(head(a.getString(R.string.w_air), ""))
    if (x == null) { widget.addView(note(a.getString(R.string.aq_none))); return }
    val level = Weather.airLevel(x.aqi)
    val row = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    row.addView(tile(if (level == 0) GREEN else if (level <= 2) AMBER else RED, R.drawable.ic_wind), LayoutParams(dp(if (full) 56 else 44), dp(if (full) 56 else 44)))
    val words = LinearLayout(a).apply { orientation = VERTICAL }
    words.addView(text(x.aqi.toString(), if (full) 30f else 24f, Color.WHITE).apply { typeface = Font.medium })
    words.addView(text(a.getString(airWords(level)), 13f, Color.WHITE))
    words.addView(text("PM2.5 ${x.pm25}", 12f, DIM))
    row.addView(words, LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
    widget.addView(row, if (full) LayoutParams(-1, 0, 1f) else LayoutParams(-1, -2))
}
