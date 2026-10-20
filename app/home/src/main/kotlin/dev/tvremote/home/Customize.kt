package dev.tvremote.home

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import org.json.JSONObject

/**
 * TV Home's own settings, on the TV with the D-pad: the background, the clock, the greeting, motion. Every change goes through
 * [Store.change] ([HomeConfig.clean]), the path the phone's broadcast takes, so the page's dialog reads the same config. The
 * rest (favourites, hidden apps, pictures from the gallery) stays on the phone or computer: the gallery's signature check lives there.
 */
object Customize {
    private const val ACCENT = 0xFF8AB4F8.toInt()

    fun show(a: Activity) {
        val d = a.resources.displayMetrics.density
        fun dp(v: Number) = (v.toFloat() * d).toInt()
        val dm = a.resources.displayMetrics
        val panelW = minOf(dm.widthPixels - dp(64), dp(780))
        val inner = panelW - dp(48)
        val dialog = Dialog(a, R.style.Menu)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(dp(24), dp(20), dp(24), dp(28)) }   // room for "Done" grown by its focus
        fun label(t: String, size: Float, color: Int) = TextView(a).apply { text = t; textSize = size; setTextColor(color) }
        col.addView(label(a.getString(R.string.customize), 22f, Color.WHITE))
        col.addView(label(a.getString(R.string.bg), 15f, 0xCCFFFFFF.toInt()), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14); bottomMargin = dp(8) })

        val conf = Store.get(a)
        val photo = java.io.File(a.getExternalFilesDir(null), "wall.jpg").isFile
        val keys = HomeConfig.WALLS.filter { it != "photo" || photo }
        val tiles = ArrayList<Pair<String, View>>()
        val cols = 9; val gap = dp(8); val w = (inner - (cols - 1) * gap) / cols
        var row: LinearLayout? = null
        keys.forEachIndexed { i, k ->
            if (i % cols == 0) { row = LinearLayout(a).apply { clipChildren = false; clipToPadding = false }; col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = gap }) }
            val t = FrameLayout(a).apply {
                isFocusable = true; isClickable = true
                val c = if (k == "photo") intArrayOf(0xFF2A2F3A.toInt()) else wallColors(k)
                background = (if (c.all { it == c[0] }) GradientDrawable().apply { setColor(c[0]) } else GradientDrawable(GradientDrawable.Orientation.TL_BR, c))
                    .apply { cornerRadius = dp(10).toFloat() }                // rounded, so not [wallDrawable]'s ColorDrawable
                foreground = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), ring(d, 3, Color.WHITE))
                    addState(intArrayOf(android.R.attr.state_selected), ring(d, 3, ACCENT))
                    addState(intArrayOf(), ring(d, 1, 0x40FFFFFF))
                }
                addView(TextView(a).apply {
                    text = a.getString(a.resources.getIdentifier("wall_$k", "string", a.packageName)); textSize = 11f; setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER; maxLines = 1; setShadowLayer(dp(3).toFloat(), 0f, 0f, 0xCC000000.toInt())
                }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dp(3) })
                onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
                setOnClickListener { Store.change(a, JSONObject().put("wall", k)); mark(tiles, k) }
            }
            tiles.add(k to t)
            row!!.addView(t, LinearLayout.LayoutParams(w, w * 9 / 16).apply { if (i % cols > 0) leftMargin = gap })
        }
        mark(tiles, conf.optString("wall"))

        // a widget's switch adds it to "widgets" or takes it out; the others are one field each
        fun toggle(text: String, key: String, widget: Boolean = false) {
            val on = if (widget) Store.list(a, "widgets").contains(key) else conf.optBoolean(key, true)
            val sw = Switch(a).apply { isChecked = on; isFocusable = false; isClickable = false }
            val r = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
                setPadding(dp(14), 0, dp(14), 0); minimumHeight = dp(44)
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0x33FFFFFF) })
                    addState(intArrayOf(), GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0x0DFFFFFF) })
                }
                addView(label(text, 16f, Color.WHITE), LinearLayout.LayoutParams(0, -2, 1f)); addView(sw)
                setOnClickListener {
                    sw.isChecked = !sw.isChecked
                    if (!widget) Store.change(a, JSONObject().put(key, sw.isChecked))
                    else Store.putList(a, "widgets", HomeConfig.WIDGETS.filter { w -> if (w == key) sw.isChecked else Store.list(a, "widgets").contains(w) })
                }
            }
            col.addView(r, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(if (key == "clock24" || key == "system") 14 else 6) })
        }
        toggle(a.getString(R.string.clock24), "clock24")
        toggle(a.getString(R.string.greet), "greet")
        toggle(a.getString(R.string.motion), "anim")
        toggle(a.getString(R.string.w_widgets) + ": " + a.getString(R.string.w_system), "system", true)
        toggle(a.getString(R.string.w_widgets) + ": " + a.getString(R.string.w_week), "usage", true)

        col.addView(label(a.getString(R.string.more), 13f, 0xB3FFFFFF.toInt()), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        val done = TextView(a).apply {
            text = a.getString(R.string.done); textSize = 16f; setTextColor(0xFF111318.toInt()); gravity = Gravity.CENTER
            isFocusable = true; isClickable = true; setPadding(dp(28), dp(10), dp(28), dp(10))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(Color.WHITE) })
                addState(intArrayOf(), GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(0xFFB8C7E6.toInt()) })
            }
            onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
            setOnClickListener { dialog.dismiss() }
        }
        col.addView(done, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12); gravity = Gravity.END })

        val cap = dm.heightPixels - dp(32)                              // a short screen scrolls the panel instead of cutting it
        val scroll = object : ScrollView(a) {
            override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
        }.apply { isVerticalScrollBarEnabled = false; clipChildren = false; addView(col) }
        val panel = FrameLayout(a).apply {
            background = GradientDrawable().apply { cornerRadius = dp(26).toFloat(); setColor(0xFF101420.toInt()); setStroke(dp(1), 0x33FFFFFF) }
            addView(scroll)
        }
        dialog.setContentView(panel, ViewGroup.LayoutParams(panelW, ViewGroup.LayoutParams.WRAP_CONTENT))
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            setLayout(panelW, ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0f)
        }
        dialog.setOnShowListener { tiles.firstOrNull { it.second.isSelected }?.second?.requestFocus() ?: tiles.firstOrNull()?.second?.requestFocus() }
        dialog.show()
    }

    private fun ring(d: Float, w: Int, color: Int) = GradientDrawable().apply { cornerRadius = 10 * d; setStroke((w * d).toInt(), color); setColor(Color.TRANSPARENT) }

    private fun mark(tiles: List<Pair<String, View>>, wall: String) { for ((k, v) in tiles) v.isSelected = k == wall }
}
