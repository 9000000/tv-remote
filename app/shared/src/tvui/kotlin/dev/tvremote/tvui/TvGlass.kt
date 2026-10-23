package dev.tvremote.tvui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.res.ColorStateList
import android.text.TextUtils

/**
 * The glass panel of TV Home's Customize (app/DESIGN-TV.md §3), as components: the panel's fill and the rows in it, and [menu], a
 * list of actions in that panel (the long-press menu of an app). Solid enough that a busy wallpaper never reads through, no blur,
 * no clip (both cost the weak boxes frames): the look is a gradient, a hairline and white-alpha rows.
 */
object TvGlass {
    const val PANEL_TOP = 0xFF161B2B.toInt()
    const val PANEL_BOTTOM = 0xFF0C1020.toInt()
    const val PANEL_EDGE = 0x33FFFFFF
    const val ROW_FOCUS = 0x2EFFFFFF                                   // a row of the panel: focused, at rest, its edge
    const val ROW_FILL = 0x12FFFFFF
    const val ROW_EDGE = 0x1FFFFFFF
    const val R_PANEL = 28f

    /** The panel's background: [R_PANEL] corners, a 1 dp hairline. */
    fun panelBg(c: Context) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(PANEL_TOP, PANEL_BOTTOM)).apply {
        cornerRadius = TvUi.dp(c, R_PANEL); setStroke(TvUi.dp(c, 1).toInt(), PANEL_EDGE)
    }

    /** One glass fill with a 1 dp edge: a row's, a chip's. */
    fun glass(c: Context, radius: Int, fill: Int, stroke: Int) = GradientDrawable().apply {
        cornerRadius = TvUi.dp(c, radius); setColor(fill); setStroke(TvUi.dp(c, 1).toInt(), stroke)
    }

    /** A row of the panel: at rest a faint fill and edge, focused a brighter fill with a white edge. */
    fun rowBg(c: Context, radius: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), glass(c, radius, ROW_FOCUS, Color.WHITE))
        addState(intArrayOf(), glass(c, radius, ROW_FILL, ROW_EDGE))
    }

    class Item(val icon: Int, val label: String, val go: () -> Unit)

    /**
     * A list of actions in the glass panel, centred over the screen, the screen dimmed behind it: [title] on top, a row each (line
     * icon [Item.icon], 0 for none, and the words), the first focused, Back closes it. [font] lets the app put its typeface on
     * every text. Rows come up a touch larger under focus ([Motion.pop]). Taller than the screen: the rows scroll.
     */
    fun menu(a: Activity, theme: Int, title: String, items: List<Item>, font: (TextView) -> Unit = {}): Dialog {
        val dialog = Dialog(a, theme)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        fun dp(v: Number) = TvUi.dp(a, v).toInt()
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(dp(12), dp(8), dp(12), dp(8)) }   // room for a focused row's growth: the scroll clips at its edge
        col.addView(TvUi.text(a, title, 20f, Color.WHITE, bold = true).apply { font(this); maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(4), dp(4), dp(4), dp(12)) })
        val ink = ColorStateList.valueOf(Color.WHITE)
        for ((i, item) in items.withIndex()) col.addView(LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
            setPadding(dp(14), dp(12), dp(16), dp(12)); background = rowBg(a, 16)
            if (item.icon != 0) addView(ImageView(a).apply { setImageResource(item.icon); imageTintList = ink }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(14) })
            addView(TvUi.text(a, item.label, 16f, Color.WHITE).apply { font(this); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
            onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
            setOnClickListener { dialog.dismiss(); item.go() }
        }, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(8) })
        val scroll = ScrollView(a).apply { isVerticalScrollBarEnabled = false; clipChildren = false; addView(col) }
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10)); background = panelBg(a); addView(scroll)
        }
        dialog.setContentView(box)
        val dm = a.resources.displayMetrics
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(minOf(dp(420), dm.widthPixels - dp(96)), -2)
            setGravity(Gravity.CENTER)
            setDimAmount(0.55f)
        }
        dialog.setOnShowListener { col.getChildAt(1)?.requestFocus() }
        dialog.show()
        return dialog
    }
}
