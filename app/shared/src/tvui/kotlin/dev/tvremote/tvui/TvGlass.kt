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
import android.widget.FrameLayout
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
    const val DIM = 0x8C000000.toInt()                                 // the screen behind a panel (55% black), drawn by the panel itself
    const val ROW_FOCUS = 0x2EFFFFFF                                   // a row of the panel: focused, at rest, its edge
    const val ROW_FILL = 0x12FFFFFF
    const val ROW_EDGE = 0x1FFFFFFF
    const val HINT = 0xB3FFFFFF.toInt()                                // the dim words on a row: what it is set to, the ›
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

    /** [hint]: what it is set to now, on the right; [more]: a "›" after it, the row opens a page of its own; [stay]: a press keeps the panel open (the action swaps its page). */
    class Item(val icon: Int, val label: String, val hint: String = "", val more: Boolean = false, val stay: Boolean = false, val go: () -> Unit)

    /**
     * ONE dialog whose content is swapped page by page (the way Customize's pages are), so the dim behind it never fades out and in
     * between a menu and its sub-menus. [page] shows a list of rows; a page with a [back] starts with a "‹ title" row (the same look
     * as Customize's head) and the remote's Back runs it too; on the first page Back closes the panel. [font] puts the app's typeface
     * on every text; [backIcon] is the app's left arrow.
     */
    class Panel(private val a: Activity, theme: Int, private val backIcon: Int, private val font: (TextView) -> Unit = {}) {
        private var back: (() -> Unit)? = null
        val dialog: Dialog = object : Dialog(a, theme) {
            @Suppress("OVERRIDE_DEPRECATION") override fun onBackPressed() { val b = back; if (b != null) b() else cancel() }
        }
        private val dm = a.resources.displayMetrics
        private fun dp(v: Number) = TvUi.dp(a, v).toInt()
        private val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10)); background = panelBg(a) }

        init {
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            // The window is the whole screen and the box floats in it: a page of another size then resizes a view, not the window (a window
            // resize reaches the screen a frame after its content, which was the quick flicker inside the panel on a slow box).
            val root = FrameLayout(a).apply { setOnClickListener { dialog.cancel() }; isFocusable = false; setBackgroundColor(DIM) }   // the dim is drawn here, not by the system: a window's own dim fades in again when it comes back from another activity (the flicker, and a dim drawn over the panel)
            root.addView(box, FrameLayout.LayoutParams(dp(420), -2, Gravity.CENTER))
            dialog.setContentView(root)
            dialog.window?.apply { setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); setLayout(-1, -1); clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND) }
        }

        private fun width(w: Int) { box.layoutParams = FrameLayout.LayoutParams(minOf(dp(w), dm.widthPixels - dp(96)), -2, Gravity.CENTER) }

        /** The page's first line: the plain [title], or with a [back] the "‹ title" row that goes up a level. */
        private fun head(title: String, back: (() -> Unit)?): View {
            if (back == null) return TvUi.text(a, title, 20f, Color.WHITE, bold = true).apply { font(this); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            return LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
                setPadding(dp(10), dp(8), dp(16), dp(8))
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), glass(a, 16, ROW_FOCUS, Color.WHITE))
                    addState(intArrayOf(), GradientDrawable().apply { setColor(Color.TRANSPARENT) })
                }
                addView(ImageView(a).apply { setImageResource(backIcon); imageTintList = ColorStateList.valueOf(Color.WHITE) }, LinearLayout.LayoutParams(dp(24), dp(24)))
                addView(TvUi.text(a, title, 20f, Color.WHITE, bold = true).apply { font(this); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
                setOnClickListener { back() }
            }
        }

        private fun frame(title: String, back: (() -> Unit)?, body: View, padBottom: Int = 0, margin: Int = 140): LinearLayout {
            this.back = back
            val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(dp(12), dp(8), dp(12), dp(8) + padBottom) }   // room for a focused row's growth: the scroll clips at its edge
            col.addView(head(title, back), LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(4), dp(4), dp(4), dp(12)) })
            col.addView(body, LinearLayout.LayoutParams(-1, -2))
            val cap = dm.heightPixels - dp(margin)                                  // the panel + its padding stay on the screen; a longer list scrolls
            val scroll = object : ScrollView(a) {
                override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST))
            }.apply { isVerticalScrollBarEnabled = false; clipChildren = false; addView(col) }
            box.removeAllViews(); box.addView(scroll)
            return col
        }

        /** A page of rows; [focus] is the row that starts focused. */
        fun page(title: String, items: List<Item>, focus: Int = 0, back: (() -> Unit)? = null) {
            val ink = ColorStateList.valueOf(Color.WHITE)
            val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
            for ((i, item) in items.withIndex()) list.addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
                setPadding(dp(14), dp(12), dp(16), dp(12)); background = rowBg(a, 16)
                if (item.icon != 0) addView(ImageView(a).apply { setImageResource(item.icon); imageTintList = ink }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(14) })
                addView(TvUi.text(a, item.label, 16f, Color.WHITE).apply { font(this); maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
                if (item.hint.isNotEmpty()) addView(TvUi.text(a, item.hint, 14f, HINT).apply { font(this); maxLines = 1; gravity = Gravity.END }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(10) })
                if (item.more) addView(TvUi.text(a, "›", 20f, HINT).apply { font(this) }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(10) })
                onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
                setOnClickListener { if (!item.stay) dialog.dismiss(); item.go() }
            }, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(8) })
            width(420)
            frame(title, back, list)
            list.getChildAt(focus.coerceIn(0, maxOf(0, items.size - 1)))?.let { r -> first(r) }
        }

        /** A page of your own [content] (a keypad), [wide] dp across; [focus] is the view that starts focused. */
        fun view(title: String, content: View, wide: Int, focus: View?, back: (() -> Unit)?) {
            width(wide)
            frame(title, back, content, padBottom = dp(10), margin = 64)                      // a focused tile's name shows under it
            focus?.let { r -> first(r) }
        }

        /** [r] is focused when the page is first laid out (before its first draw), so no frame shows the ring on another row. */
        private fun first(r: View) { r.isFocusedByDefault = true; r.post { if (!r.hasFocus()) r.requestFocus() } }

        fun show() = dialog.show()
    }

    /**
     * A list of actions in the glass panel, centred over the screen, the screen dimmed behind it: [title] on top, a row each (line
     * icon [Item.icon], 0 for none, and the words), the first focused, Back closes it. Rows come up a touch larger under focus
     * ([Motion.pop]). Taller than the screen: the rows scroll. For a menu with sub-menus use a [Panel].
     */
    fun menu(a: Activity, theme: Int, title: String, items: List<Item>, focus: Int = 0, onBack: (() -> Unit)? = null, font: (TextView) -> Unit = {}): Dialog {
        val p = Panel(a, theme, 0, font)
        p.page(title, items, focus)
        if (onBack != null) p.dialog.setOnCancelListener { onBack() }          // Back (not an action's own dismiss): up a level
        p.show()
        return p.dialog
    }
}
