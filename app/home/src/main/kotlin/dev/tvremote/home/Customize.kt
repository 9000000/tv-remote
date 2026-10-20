package dev.tvremote.home

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

    fun show(a: Activity): Dialog {
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
        fun tile(res: Int, text: String, sub: String?, key: String) {
            if (pair == null || inRow == 2) {
                pair = LinearLayout(a).apply { clipChildren = false; clipToPadding = false }
                col.addView(pair, LinearLayout.LayoutParams(-1, -2).apply { if (inRow == 2) topMargin = gap }); inRow = 0
            }
            var on = conf.optBoolean(key, true)
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
                on = !on
                try { Store.change(a, JSONObject().put(key, on)) } catch (e: SecurityException) { on = !on; systemDream(a) }
                paint()
            }
            pair!!.addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { if (inRow == 1) leftMargin = gap })
            inRow++
        }
        section(a.getString(R.string.c_look))
        tile(R.drawable.ic_timer, a.getString(R.string.clock24), null, "clock24")
        tile(R.drawable.ic_message, a.getString(R.string.greet), null, "greet")
        tile(R.drawable.ic_sparkle, a.getString(R.string.motion), null, "anim")
        tile(R.drawable.ic_moon, a.getString(R.string.dream), a.getString(R.string.dream_sub), "dream")
        if (inRow == 1) pair!!.addView(View(a), LinearLayout.LayoutParams(0, 0, 1f).apply { leftMargin = gap })   // a lone tile keeps half the width
        pair = null
        section(a.getString(R.string.w_widgets))
        widgetRows(a, col, gap, ::glass, ::label)
        section(a.getString(R.string.w_layout))
        layoutTiles(a, col, gap, ::glass, ::label)
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
        return dialog
    }

    /** The screensaver switch cannot write the setting (no WRITE_SECURE_SETTINGS yet; a phone or computer grants it): the system's own screen for it instead. */
    private fun systemDream(a: Activity) {
        android.widget.Toast.makeText(a, R.string.dream_system, android.widget.Toast.LENGTH_LONG).show()
        try { a.startActivity(android.content.Intent(android.provider.Settings.ACTION_DREAM_SETTINGS)) }
        catch (e: Exception) { try { a.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)) } catch (e2: Exception) { } }
    }

    private val WIDGET_ROWS = listOf(                                  // key, icon, name, line under it
        arrayOf<Any>("system", R.drawable.ic_gauge, R.string.w_system, R.string.c_system_sub),
        arrayOf<Any>("usage", R.drawable.ic_chart, R.string.w_usage, R.string.c_usage_sub),
        arrayOf<Any>("calendar", R.drawable.ic_calendar, R.string.w_calendar, R.string.c_calendar_sub),
        arrayOf<Any>("lunar", R.drawable.ic_moon, R.string.w_lunar, R.string.c_lunar_sub),
        arrayOf<Any>("timer", R.drawable.ic_timer, R.string.w_timer, R.string.c_timer_sub),
        arrayOf<Any>("weather", R.drawable.ic_weather, R.string.w_weather, R.string.c_weather_sub),
    )

    /**
     * The widgets in the order they have on the screen, the chosen ones first. OK on a card switches it; the button at its right
     * is the move mode: OK turns it on, Up / Down move the card, OK or Back (or leaving it) turn it off. One column, so Up / Down
     * mean the same as on the screen's list, and a card switched on goes to the end.
     */
    private fun widgetRows(a: Activity, col: LinearLayout, gap: Int, glass: (Int, Int, Int) -> GradientDrawable, label: (String, Float, Int) -> TextView) {
        val d = a.resources.displayMetrics.density
        fun dp(v: Number) = (v.toFloat() * d).toInt()
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        col.addView(box, LinearLayout.LayoutParams(-1, -2))
        col.addView(label(a.getString(R.string.w_order_hint), 12f, 0x8CFFFFFF.toInt()).apply { maxLines = 2 }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        var moving: String? = null; var building = false
        fun fill(focus: String? = null, focusMove: Boolean = false) {
            building = true                                            // taking the rows down loses their focus: not a wander
            box.removeAllViews()
            val on = Store.list(a, "widgets")
            val order = on + HomeConfig.WIDGETS.filter { it !in on }
            var want: View? = null
            order.forEachIndexed { i, k ->
                val r = WIDGET_ROWS.first { it[0] == k }
                val isOn = k in on
                val line = LinearLayout(a).apply { clipChildren = false; clipToPadding = false }
                val ic = ImageView(a).apply { setImageResource(r[1] as Int); imageTintList = ColorStateList.valueOf(if (isOn) ACCENT else DIM) }
                val state = label("", 12f, if (isOn) ACCENT else DIM).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
                val sub = a.getString(r[3] as Int)
                val word = a.getString(if (isOn) R.string.w_on else R.string.w_off).replaceFirstChar { it.uppercase() }
                state.text = if (moving == k) a.getString(R.string.c_moving) else "$word · $sub"
                val t = LinearLayout(a).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_focused), glass(16, if (isOn) 0x408AB4F8 else 0x2EFFFFFF, Color.WHITE))
                        addState(intArrayOf(), glass(16, if (isOn) 0x268AB4F8 else 0x12FFFFFF, if (isOn) ACCENT else 0x1FFFFFFF))
                    }
                    addView(ic, LinearLayout.LayoutParams(dp(26), dp(26)))
                    addView(LinearLayout(a).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(label(a.getString(r[2] as Int), 15f, Color.WHITE).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                        addView(state)
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
                    onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
                    setOnClickListener {
                        moving = null
                        Store.putList(a, "widgets", if (isOn) on - k else on + k)
                        fill(k)
                    }
                }
                line.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
                if (isOn && on.size > 1) {
                    val m = LinearLayout(a).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isFocusable = true; isClickable = true
                        contentDescription = a.getString(R.string.c_move)
                        val live = moving == k
                        background = StateListDrawable().apply {
                            addState(intArrayOf(android.R.attr.state_focused), glass(16, if (live) 0x668AB4F8 else 0x2EFFFFFF, Color.WHITE))
                            addState(intArrayOf(), glass(16, if (live) 0x408AB4F8 else 0x12FFFFFF, if (live) ACCENT else 0x1FFFFFFF))
                        }
                        for (res in intArrayOf(R.drawable.ic_up, R.drawable.ic_down)) addView(ImageView(a).apply { setImageResource(res); imageTintList = ColorStateList.valueOf(if (live) ACCENT else DIM) }, LinearLayout.LayoutParams(dp(18), dp(18)))
                        onFocusChangeListener = View.OnFocusChangeListener { v, f ->
                            Motion.pop(v, f)
                            if (!f && !building && moving == k) { moving = null; v.postDelayed({ if (v.isAttachedToWindow) fill() }, 0) }   // wandered off: back to plain
                        }
                        setOnClickListener { moving = if (moving == k) null else k; fill(k, true) }
                        setOnKeyListener { _, code, ev ->
                            if (moving != k) return@setOnKeyListener false
                            val j = when (code) { android.view.KeyEvent.KEYCODE_DPAD_UP -> on.indexOf(k) - 1; android.view.KeyEvent.KEYCODE_DPAD_DOWN -> on.indexOf(k) + 1; else -> -2 }
                            if (code == android.view.KeyEvent.KEYCODE_BACK) { if (ev.action == android.view.KeyEvent.ACTION_UP) { moving = null; fill(k, true) }; return@setOnKeyListener true }
                            if (j == -2) return@setOnKeyListener false
                            if (ev.action == android.view.KeyEvent.ACTION_DOWN && j in on.indices) {
                                val n = on.toMutableList(); n[on.indexOf(k)] = n[j]; n[j] = k
                                Store.putList(a, "widgets", n); fill(k, true)
                            }
                            true
                        }
                    }
                    line.addView(m, LinearLayout.LayoutParams(dp(56), -1).apply { leftMargin = dp(8) })
                    if (focus == k && focusMove) want = m
                }
                if (focus == k && !focusMove) want = t
                box.addView(line, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = gap })
            }
            want?.requestFocus()
            building = false
        }
        fill()
    }

    /** The layouts, two tiles to a row, one chosen; each shows its places in miniature. */
    private fun layoutTiles(a: Activity, col: LinearLayout, gap: Int, glass: (Int, Int, Int) -> GradientDrawable, label: (String, Float, Int) -> TextView) {
        val d = a.resources.displayMetrics.density
        fun dp(v: Number) = (v.toFloat() * d).toInt()
        val tiles = ArrayList<Triple<String, LinearLayout, LayoutGlyph>>()
        fun paint() {
            val now = Store.get(a).optString("wlayout", "row")
            for ((k, t, ic) in tiles) {
                val on = k == now
                t.background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), glass(16, if (on) 0x408AB4F8 else 0x2EFFFFFF, Color.WHITE))
                    addState(intArrayOf(), glass(16, if (on) 0x268AB4F8 else 0x12FFFFFF, if (on) ACCENT else 0x1FFFFFFF))
                }
                ic.paint.color = if (on) ACCENT else DIM
                ic.invalidate()
            }
        }
        val all = listOf(Triple("row", R.string.c_layout_row, R.string.c_layout_row_sub), Triple("big", R.string.c_layout_big, 0),
            Triple("big2", R.string.c_layout_big2, 0), Triple("flip", R.string.c_layout_flip, 0), Triple("quad", R.string.c_layout_quad, 0))
        var pair: LinearLayout? = null
        all.forEachIndexed { i, (k, name, sub) ->
            if (i % 2 == 0) pair = LinearLayout(a).apply { clipChildren = false; clipToPadding = false }.also { col.addView(it, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = gap }) }
            val ic = LayoutGlyph(a, k)
            val t = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
                setPadding(dp(14), dp(12), dp(14), dp(12))
                addView(ic, LinearLayout.LayoutParams(dp(40), dp(26)))
                addView(LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label(a.getString(name), 15f, Color.WHITE).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                    val n = HomeConfig.slots(k)
                    addView(label(if (sub != 0) a.getString(sub) else a.getString(R.string.c_layout_first, n), 12f, DIM).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
                onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
                setOnClickListener { Store.change(a, JSONObject().put("wlayout", k)); paint() }
            }
            tiles += Triple(k, t, ic)
            pair!!.addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i % 2 == 1) leftMargin = gap })
        }
        if (all.size % 2 == 1) pair!!.addView(View(a), LinearLayout.LayoutParams(0, 0, 1f).apply { leftMargin = gap })
        paint()
    }

    /** A layout in miniature: its columns, the cards stacked in each. Drawn when painted, never animated. */
    private class LayoutGlyph(c: Context, val layout: String) : View(c) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(cv: Canvas) {
            val g = resources.displayMetrics.density * 2f
            val cols = if (layout == "row") listOf(HomeConfig.Col(33, 1), HomeConfig.Col(33, 1), HomeConfig.Col(34, 2)) else HomeConfig.COLS.getValue(layout)
            val room = width - g * (cols.size - 1)
            var x = 0f
            for (c in cols) {
                val w = room * c.pct / 100f
                val h = (height - g * (c.cells - 1)) / c.cells
                for (j in 0 until c.cells) cv.drawRoundRect(x, j * (h + g), x + w, j * (h + g) + h, g, g, paint)
                x += w + g
            }
        }
    }

    private fun ring(d: Float, w: Int, color: Int) = GradientDrawable().apply { cornerRadius = 12 * d; setStroke((w * d).toInt(), color); setColor(Color.TRANSPARENT) }

    private fun mark(tiles: List<Pair<String, View>>, wall: String) {
        for ((k, v) in tiles) { v.isSelected = k == wall; v.findViewWithTag<View>("check")?.visibility = if (k == wall) View.VISIBLE else View.GONE }
    }
}
