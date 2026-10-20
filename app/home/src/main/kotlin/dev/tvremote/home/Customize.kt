package dev.tvremote.home

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

/**
 * TV Home's own settings, on the TV with the D-pad: a glass panel from the right, the home screen still showing beside it so
 * each change is seen at once. The background, the clock, the greeting, motion, the widgets. Every change goes through
 * [Store.change] ([HomeConfig.clean]), the path the phone's broadcast takes, so the page's dialog reads the same config. The
 * rest (favourites, hidden apps, pictures from the gallery) stays on the phone or computer: the gallery's signature check lives there.
 */
object Customize {
    private const val ACCENT = 0xFF8AB4F8.toInt()
    private const val DIM = 0xA6FFFFFF.toInt()

    fun show(a: Activity) {
        val d = a.resources.displayMetrics.density
        fun dp(v: Number) = (v.toFloat() * d).toInt()
        val dm = a.resources.displayMetrics
        val edge = dp(24)
        val panelW = minOf(dm.widthPixels - dp(64), dp(600))
        val inner = panelW - dp(56)
        val dialog = Dialog(a, R.style.Menu)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(dp(28), dp(26), dp(28), dp(28)) }
        fun label(t: String, size: Float, color: Int) = TextView(a).apply { text = t; textSize = size; setTextColor(color) }
        fun icon(res: Int, color: Int, size: Int) = ImageView(a).apply { setImageResource(res); imageTintList = ColorStateList.valueOf(color) }.let { it to size }
        fun section(t: String) = col.addView(label(t.uppercase(), 12f, DIM).apply { letterSpacing = 0.12f }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(22); bottomMargin = dp(10) })
        fun glass(radius: Int, fill: Int, stroke: Int) = GradientDrawable().apply { cornerRadius = dp(radius).toFloat(); setColor(fill); setStroke(dp(1), stroke) }

        // the head: the sliders in a tinted tile, the title, what it is for
        col.addView(LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(FrameLayout(a).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF6F8DFF.toInt(), 0xFF9B6BFF.toInt())).apply { cornerRadius = dp(14).toFloat() }
                val (iv, s) = icon(R.drawable.ic_sliders, Color.WHITE, dp(24))
                addView(iv, FrameLayout.LayoutParams(s, s, Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(a.getString(R.string.customize), 22f, Color.WHITE).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
                addView(label(a.getString(R.string.c_sub), 13f, DIM))
            }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(14) })
        })

        // backgrounds, four to a row, a ✓ on the one in use
        section(a.getString(R.string.bg))
        val conf = Store.get(a)
        val photo = java.io.File(a.getExternalFilesDir(null), "wall.jpg").isFile
        val keys = HomeConfig.WALLS.filter { it != "photo" || photo }
        val tiles = ArrayList<Pair<String, View>>()
        val cols = 5; val gap = dp(10); val w = (inner - (cols - 1) * gap) / cols
        var row: LinearLayout? = null
        keys.forEachIndexed { i, k ->
            if (i % cols == 0) { row = LinearLayout(a).apply { clipChildren = false; clipToPadding = false }; col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = gap }) }
            val cell = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
            val t = FrameLayout(a).apply {
                isFocusable = true; isClickable = true
                val c = if (k == "photo") intArrayOf(0xFF2A2F3A.toInt()) else wallColors(k)
                background = (if (c.all { it == c[0] }) GradientDrawable().apply { setColor(c[0]) } else GradientDrawable(GradientDrawable.Orientation.TL_BR, c))
                    .apply { cornerRadius = dp(12).toFloat() }                // rounded, so not [wallDrawable]'s ColorDrawable
                foreground = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), ring(d, 3, Color.WHITE))
                    addState(intArrayOf(android.R.attr.state_selected), ring(d, 2, ACCENT))
                    addState(intArrayOf(), ring(d, 1, 0x33FFFFFF))
                }
                if (k == "photo") { val (iv, s) = icon(R.drawable.ic_image, DIM, dp(22)); addView(iv, FrameLayout.LayoutParams(s, s, Gravity.CENTER)) }
                addView(FrameLayout(a).apply {                            // the ✓, shown on the chosen one (mark)
                    tag = "check"
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ACCENT) }
                    val (iv, s) = icon(R.drawable.ic_check, 0xFF0B1020.toInt(), dp(14)); addView(iv, FrameLayout.LayoutParams(s, s, Gravity.CENTER))
                }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(6), dp(6), 0) })
                onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
                setOnClickListener { Store.change(a, JSONObject().put("wall", k)); mark(tiles, k) }
            }
            tiles.add(k to t)
            cell.addView(t, LinearLayout.LayoutParams(w, w * 9 / 16))
            cell.addView(label(a.getString(a.resources.getIdentifier("wall_$k", "string", a.packageName)), 12f, DIM).apply { gravity = Gravity.CENTER; maxLines = 1 },
                LinearLayout.LayoutParams(w, -2).apply { topMargin = dp(5) })
            row!!.addView(cell, LinearLayout.LayoutParams(w, -2).apply { if (i % cols > 0) leftMargin = gap })
        }
        mark(tiles, conf.optString("wall"))

        // switches as tiles, two to a row: an icon, a name, a line under it; on = a blue tint with a blue edge, as everywhere in the app
        var pair: LinearLayout? = null
        var inRow = 0
        fun tile(res: Int, text: String, sub: String?, key: String, widget: Boolean = false) {
            if (pair == null || inRow == 2) {
                pair = LinearLayout(a).apply { clipChildren = false; clipToPadding = false }
                col.addView(pair, LinearLayout.LayoutParams(-1, -2).apply { if (inRow == 2) topMargin = gap }); inRow = 0
            }
            var on = if (widget) Store.list(a, "widgets").contains(key) else conf.optBoolean(key, true)
            val ic = ImageView(a).apply { setImageResource(res) }
            val state = label("", 12f, DIM)
            val t = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
                setPadding(dp(14), dp(12), dp(14), dp(12))
                addView(ic, LinearLayout.LayoutParams(dp(26), dp(26)))
                addView(LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label(text, 15f, Color.WHITE).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    addView(state.apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
            }
            fun paint() {
                t.background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), glass(16, if (on) 0x408AB4F8 else 0x2EFFFFFF, Color.WHITE))
                    addState(intArrayOf(), glass(16, if (on) 0x268AB4F8 else 0x12FFFFFF, if (on) ACCENT else 0x1FFFFFFF))
                }
                ic.imageTintList = ColorStateList.valueOf(if (on) ACCENT else DIM)
                val word = a.getString(if (on) R.string.w_on else R.string.w_off).replaceFirstChar { it.uppercase() }
                state.text = if (sub != null) "$word · $sub" else word
                state.setTextColor(if (on) ACCENT else DIM)
            }
            paint()
            t.onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
            t.setOnClickListener {
                on = !on; paint()
                if (!widget) Store.change(a, JSONObject().put(key, on))
                else Store.putList(a, "widgets", HomeConfig.WIDGETS.filter { w -> if (w == key) on else Store.list(a, "widgets").contains(w) })
            }
            pair!!.addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { if (inRow == 1) leftMargin = gap })
            inRow++
        }
        section(a.getString(R.string.c_look))
        tile(R.drawable.ic_timer, a.getString(R.string.clock24), null, "clock24")
        tile(R.drawable.ic_message, a.getString(R.string.greet), null, "greet")
        tile(R.drawable.ic_sparkle, a.getString(R.string.motion), null, "anim")
        if (inRow == 1) pair!!.addView(View(a), LinearLayout.LayoutParams(0, 0, 1f).apply { leftMargin = gap })   // a lone tile keeps half the width
        pair = null
        section(a.getString(R.string.w_widgets))
        tile(R.drawable.ic_gauge, a.getString(R.string.w_system), a.getString(R.string.c_system_sub), "system", true)
        tile(R.drawable.ic_chart, a.getString(R.string.w_usage), a.getString(R.string.c_usage_sub), "usage", true)
        tile(R.drawable.ic_calendar, a.getString(R.string.w_calendar), a.getString(R.string.c_calendar_sub), "calendar", true)
        tile(R.drawable.ic_moon, a.getString(R.string.w_lunar), a.getString(R.string.c_lunar_sub), "lunar", true)
        tile(R.drawable.ic_timer, a.getString(R.string.w_timer), a.getString(R.string.c_timer_sub), "timer", true)
        val note = conf.optString("note")
        if (note.isNotEmpty()) {                                       // written on the phone; here it can only be taken down
            val sub = label(a.getString(R.string.c_note_clear), 12f, DIM).apply { maxLines = 1 }
            col.addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), glass(16, 0x2EFFFFFF, Color.WHITE))
                    addState(intArrayOf(), glass(16, 0x12FFFFFF, 0x1FFFFFFF))
                }
                addView(ImageView(a).apply { setImageResource(R.drawable.ic_message); imageTintList = ColorStateList.valueOf(0xFFFFD27A.toInt()) }, LinearLayout.LayoutParams(dp(26), dp(26)))
                addView(LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label(note, 15f, Color.WHITE).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    addView(sub)
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
                onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
                setOnClickListener { Store.change(a, JSONObject().put("note", "")); sub.text = a.getString(R.string.c_note_gone); alpha = 0.6f }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = gap })
        }

        col.addView(label(a.getString(R.string.more), 12f, 0x8CFFFFFF.toInt()), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })
        val done = TextView(a).apply {
            text = a.getString(R.string.done); textSize = 16f; setTextColor(0xFF111318.toInt()); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isFocusable = true; isClickable = true; setPadding(dp(28), dp(11), dp(28), dp(11))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(Color.WHITE) })
                addState(intArrayOf(), GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(0xFFB8C7E6.toInt()) })
            }
            onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
            setOnClickListener { dialog.dismiss() }
        }
        col.addView(done, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        val scroll = ScrollView(a).apply { isVerticalScrollBarEnabled = false; isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(dp(24)); clipChildren = false; addView(col) }
        val panel = FrameLayout(a).apply {
            // solid: the home screen's text must not show through
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF161B2B.toInt(), 0xFF0C1020.toInt())).apply {
                cornerRadius = dp(28).toFloat(); setStroke(dp(1), 0x33FFFFFF)
            }
            clipToOutline = true
            addView(scroll)
        }
        val height = dm.heightPixels - 2 * edge
        dialog.setContentView(panel, ViewGroup.LayoutParams(panelW, height))
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout(panelW, height)
            setGravity(Gravity.END or Gravity.CENTER_VERTICAL)
            attributes = attributes.apply { x = edge }
            setDimAmount(0f)
            setWindowAnimations(0)                                     // its own slide below, or none when motion is off
        }
        dialog.setOnShowListener {
            (tiles.firstOrNull { it.second.isSelected } ?: tiles.firstOrNull())?.second?.requestFocus()
            if (Motion.on) { panel.translationX = panelW / 3f; panel.alpha = 0f; panel.animate().translationX(0f).alpha(1f).setDuration(260).setInterpolator(Motion.glide).start() }
        }
        dialog.show()
    }

    private fun ring(d: Float, w: Int, color: Int) = GradientDrawable().apply { cornerRadius = 12 * d; setStroke((w * d).toInt(), color); setColor(Color.TRANSPARENT) }

    private fun mark(tiles: List<Pair<String, View>>, wall: String) {
        for ((k, v) in tiles) { v.isSelected = k == wall; v.findViewWithTag<View>("check")?.visibility = if (k == wall) View.VISIBLE else View.GONE }
    }
}
