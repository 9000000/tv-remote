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
import android.graphics.Bitmap
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

        // backgrounds, five to a row, a ✓ on the one in use (rotation off) or on each of the set it turns through (rotation on)
        section(a.getString(R.string.bg))
        val conf = Store.get(a)
        val have = Walls.photos(a)
        val keys = HomeConfig.WALLS.filter { !HomeConfig.isPhoto(it) || it in have }
        val tiles = ArrayList<Pair<String, View>>()
        val cols = 5; val gap = dp(10); val w = tileW(a)
        val shots = java.util.concurrent.Executors.newSingleThreadExecutor()    // the scenes drawn and the photos read small, off the main thread
        dialog.setOnDismissListener { shots.shutdownNow() }
        // the pictures are put in together once the panel has slid in: one by one during the slide each drew the whole panel again
        val waiting = ArrayList<Pair<ImageView, Bitmap>>()
        var slid = !Motion.on
        fun put(v: ImageView, b: Bitmap) { if (slid) v.setImageBitmap(b) else waiting.add(v to b) }
        val rotNote = label("", 12f, DIM)
        fun wallOn(c: JSONObject, k: String) =
            if (c.optString("wevery") == "off") c.optString("wall") == k
            else c.getJSONArray("walls").let { s -> (0 until s.length()).any { s.getString(it) == k } }
        fun markAll() {
            val c = Store.get(a)                                         // once: reading it per tile cost 18 ms on the FPT box
            for ((k, v) in tiles) { val on = wallOn(c, k); v.isSelected = on; v.findViewWithTag<View>("check")?.visibility = if (on) View.VISIBLE else View.GONE }
            rotNote.visibility = if (c.optString("wevery") != "off") View.VISIBLE else View.GONE
            rotNote.text = a.getString(if (c.getJSONArray("walls").length() < 2) R.string.rot_pick else R.string.rot_set)
        }
        // how often it moves on: two rows of three chips, the one in use tinted like the switches below
        val everyWords = mapOf("off" to R.string.rot_off, "30m" to R.string.rot_30m, "1h" to R.string.rot_1h, "6h" to R.string.rot_6h, "day" to R.string.rot_day, "show" to R.string.rot_show)
        val chips = ArrayList<Pair<String, TextView>>()
        fun markEvery() { val e = Store.get(a).optString("wevery"); for ((k, v) in chips) v.isSelected = k == e }
        col.addView(label(a.getString(R.string.rot), 13f, Color.WHITE), LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(8) })
        var chipRow: LinearLayout? = null
        HomeConfig.EVERY.forEachIndexed { i, k ->
            if (i % 3 == 0) { chipRow = LinearLayout(a).apply { clipChildren = false; clipToPadding = false }; col.addView(chipRow, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(8) }) }
            val chip = TextView(a).apply {
                text = a.getString(everyWords.getValue(k)); textSize = 14f; gravity = Gravity.CENTER; maxLines = 1
                setTextColor(Color.WHITE); isFocusable = true; isClickable = true; setPadding(dp(8), dp(11), dp(8), dp(11))
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), glass(14, 0x2EFFFFFF, Color.WHITE))
                    addState(intArrayOf(android.R.attr.state_selected), glass(14, 0x268AB4F8, ACCENT))
                    addState(intArrayOf(), glass(14, 0x12FFFFFF, 0x1FFFFFFF))
                }
                onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
                setOnClickListener { Store.change(a, JSONObject().put("wevery", k)); markEvery(); markAll() }
            }
            chips.add(k to chip)
            chipRow!!.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i % 3 > 0) leftMargin = dp(8) })
        }
        col.addView(rotNote, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        col.addView(View(a), LinearLayout.LayoutParams(1, dp(14)))
        var row: LinearLayout? = null
        keys.forEachIndexed { i, k ->
            if (i % cols == 0) { row = LinearLayout(a).apply { clipChildren = false; clipToPadding = false }; col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = gap }) }
            val cell = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
            val t = FrameLayout(a).apply {
                isFocusable = true; isClickable = true
                val c = if (HomeConfig.isPhoto(k) || k == HomeConfig.BING) intArrayOf(0xFF2A2F3A.toInt()) else wallColors(k)
                background = (if (c.all { it == c[0] }) GradientDrawable().apply { setColor(c[0]) } else GradientDrawable(GradientDrawable.Orientation.TL_BR, c))
                    .apply { cornerRadius = dp(12).toFloat() }                // rounded, so not [wallDrawable]'s ColorDrawable
                if (WallArt.has(k) || Walls.isPicture(k)) {                   // the picture itself, in the tile's rounded corners: the same painter as the screen
                    val pv = ImageView(a).apply { scaleType = ImageView.ScaleType.FIT_XY }   // the picture is made at the tile's size with its corners: nothing scaled or clipped per frame
                    addView(pv, FrameLayout.LayoutParams(-1, -1))
                    val drawn = thumbs[thumbKey(a, k, w)]                    // made beforehand ([warm]) or on an earlier opening: there as it slides in
                    if (drawn != null) pv.setImageBitmap(drawn)
                    else shots.execute { val b = thumb(a, k, w); a.runOnUiThread { if (b != null) put(pv, b) } }
                }
                foreground = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), ring(d, 3, Color.WHITE))
                    addState(intArrayOf(android.R.attr.state_selected), ring(d, 2, ACCENT))
                    addState(intArrayOf(), ring(d, 1, 0x33FFFFFF))
                }
                if (k == HomeConfig.BING && !Online.file(a).isFile) { val (iv, s) = icon(R.drawable.ic_globe, DIM, dp(22)); addView(iv, FrameLayout.LayoutParams(s, s, Gravity.CENTER)) }   // not fetched yet
                addView(FrameLayout(a).apply {                            // the ✓, shown on the chosen one (mark)
                    tag = "check"
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ACCENT) }
                    val (iv, s) = icon(R.drawable.ic_check, 0xFF0B1020.toInt(), dp(14)); addView(iv, FrameLayout.LayoutParams(s, s, Gravity.CENTER))
                }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(6), dp(6), 0) })
                onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
                setOnClickListener {
                    val c = Store.get(a)
                    if (k == HomeConfig.BING && c.optString("wall") == k && c.optString("wevery") == "off" && Online.count(a) > 1) {   // Bing on already: its next (older) day, round to the newest
                        Online.step(a)
                        Store.change(a, JSONObject().put("wall", k))
                        val pv = getChildAt(0) as? ImageView
                        shots.execute { val b = thumb(a, k, w); a.runOnUiThread { if (b != null) pv?.setImageBitmap(b) } }
                    }
                    else if (c.optString("wevery") == "off") Store.change(a, JSONObject().put("wall", k))       // one choice
                    else {                                                                                      // a set: a tile in or out, never the last one out
                        val s = c.getJSONArray("walls"); val now = List(s.length()) { s.getString(it) }
                        val next = if (k in now) now - k else now + k
                        if (next.isNotEmpty()) Store.change(a, JSONObject().put("walls", org.json.JSONArray(next)))
                    }
                    markAll()
                }
            }
            tiles.add(k to t)
            cell.addView(t, LinearLayout.LayoutParams(w, w * 9 / 16))
            cell.addView(label(wallName(a, k), 12f, DIM).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(w, -2).apply { topMargin = dp(5) })
            row!!.addView(cell, LinearLayout.LayoutParams(w, -2).apply { if (i % cols > 0) leftMargin = gap })
        }
        markAll(); markEvery()

        // switches as tiles, two to a row: an icon, a name, a line under it; on = a blue tint with a blue edge, as everywhere in the app
        var pair: LinearLayout? = null
        var inRow = 0
        // with [choices] (value to its words) the tile steps through them on each press instead of switching on / off; on = not the first
        fun tile(res: Int, text: String, sub: String?, key: String, choices: List<Pair<String, String>>? = null) {
            if (pair == null || inRow == 2) {
                pair = LinearLayout(a).apply { clipChildren = false; clipToPadding = false }
                col.addView(pair, LinearLayout.LayoutParams(-1, -2).apply { if (inRow == 2) topMargin = gap }); inRow = 0
            }
            var pick = choices?.indexOfFirst { it.first == conf.optString(key) }?.coerceAtLeast(0) ?: 0
            var on = if (choices != null) pick > 0 else conf.optBoolean(key, true)
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
                state.text = choices?.get(pick)?.second ?: if (sub != null) "$word · $sub" else word
                state.setTextColor(if (on) ACCENT else DIM)
            }
            paint()
            t.onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
            t.setOnClickListener {
                if (choices != null) {                                 // a new language builds the screen again (HomeActivity), this panel with it
                    pick = (pick + 1) % choices.size; on = pick > 0; paint()
                    Store.change(a, JSONObject().put(key, choices[pick].first)); return@setOnClickListener
                }
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
        tile(R.drawable.ic_globe, a.getString(R.string.c_lang), null, "lang",
            listOf("" to a.getString(R.string.c_lang_tv), "vi" to "Tiếng Việt", "en" to "English"))
        if (inRow == 1) pair!!.addView(View(a), LinearLayout.LayoutParams(0, 0, 1f).apply { leftMargin = gap })   // a lone tile keeps half the width
        pair = null
        section(a.getString(R.string.w_widgets))
        val preview = PackGlyph(a)
        widgetRows(a, col, gap, ::glass, ::label, preview)
        section(a.getString(R.string.w_layout))
        col.addView(preview, LinearLayout.LayoutParams(-1, (64 * a.resources.displayMetrics.density).toInt()))
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

        val scroll = ScrollView(a).apply { isVerticalScrollBarEnabled = false; clipChildren = false; addView(col) }
        val panel = FrameLayout(a).apply {
            // solid: the home screen's text must not show through
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF161B2B.toInt(), 0xFF0C1020.toInt())).apply {
                cornerRadius = dp(28).toFloat(); setStroke(dp(1), 0x33FFFFFF)
            }
            // no clip and no fading edge: clipped to its corners it cost the FPT box ~10 ms a frame, the ScrollView's fading edge drew
            // the whole list into a layer every frame (55 ms a frame while a tile grew); the list stops 1 dp inside the border instead
            addView(scroll, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
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
            (tiles.firstOrNull { it.second.isSelected } ?: tiles.firstOrNull())?.second?.let {
                it.requestFocus()
                it.animate().cancel(); if (Motion.on) { it.scaleX = 1.06f; it.scaleY = 1.06f }   // already large as it slides in: its spring re-drew the whole panel for 400 ms
            }
            if (Motion.on) {                                           // on a layer: drawn once, then only moved and faded (drawn each frame it took 40-57 ms on the FPT box)
                panel.animate().translationX(0f).alpha(1f).setDuration(260).setInterpolator(Motion.glide).withLayer().withEndAction {
                    slid = true; for ((v, b) in waiting) v.setImageBitmap(b); waiting.clear()
                }.start()
            }
        }
        if (Motion.on) { panel.translationX = panelW / 3f; panel.alpha = 0f }   // before show: set in the show listener, the first frame had shown it in place
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
    private fun widgetRows(a: Activity, col: LinearLayout, gap: Int, glass: (Int, Int, Int) -> GradientDrawable, label: (String, Float, Int) -> TextView, preview: View) {
        val d = a.resources.displayMetrics.density
        fun dp(v: Number) = (v.toFloat() * d).toInt()
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        col.addView(box, LinearLayout.LayoutParams(-1, -2))
        col.addView(label(a.getString(R.string.w_order_hint) + " " + a.getString(R.string.c_size_hint), 12f, 0x8CFFFFFF.toInt()).apply { maxLines = 4 }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        var moving: String? = null; var building = false
        fun fill(focus: String? = null, focusMove: Boolean = false, focusSize: Boolean = false) {
            building = true                                            // taking the rows down loses their focus: not a wander
            box.removeAllViews()
            val on = Store.list(a, "widgets")
            val order = on + HomeConfig.WIDGETS.filter { it !in on }
            val conf = Store.get(a)
            val big = HomeConfig.bigs(conf)
            val waiting = HomeConfig.pack(on, big, conf.optString("note").isNotEmpty()).waiting.toSet()
            var want: View? = null
            order.forEachIndexed { i, k ->
                val r = WIDGET_ROWS.first { it[0] == k }
                val isOn = k in on
                val line = LinearLayout(a).apply { clipChildren = false; clipToPadding = false }
                val ic = ImageView(a).apply { setImageResource(r[1] as Int); imageTintList = ColorStateList.valueOf(if (isOn) ACCENT else DIM) }
                val state = label("", 12f, if (isOn) ACCENT else DIM).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
                val sub = a.getString(r[3] as Int)
                val word = a.getString(if (isOn) R.string.w_on else R.string.w_off).replaceFirstChar { it.uppercase() }
                state.text = if (moving == k) a.getString(R.string.c_moving) else if (k in waiting) "$word · ${a.getString(R.string.c_waits)}" else "$word · $sub"
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
                if (isOn) {                                            // the size: OK flips Big / Small
                    val isBig = k in big
                    val sz = label(a.getString(if (isBig) R.string.c_size_big else R.string.c_size_small), 14f, Color.WHITE).apply { gravity = Gravity.CENTER; maxLines = 1 }
                    val s = LinearLayout(a).apply {
                        gravity = Gravity.CENTER; isFocusable = true; isClickable = true
                        contentDescription = a.getString(R.string.c_size)
                        background = StateListDrawable().apply {
                            addState(intArrayOf(android.R.attr.state_focused), glass(16, if (isBig) 0x408AB4F8 else 0x2EFFFFFF, Color.WHITE))
                            addState(intArrayOf(), glass(16, if (isBig) 0x268AB4F8 else 0x12FFFFFF, if (isBig) ACCENT else 0x1FFFFFFF))
                        }
                        addView(sz)
                        onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
                        setOnClickListener {
                            moving = null
                            val now = (conf.optJSONObject("wsize") ?: JSONObject()).let { JSONObject(it.toString()) }
                            Store.change(a, JSONObject().put("wsize", now.put(k, if (isBig) "small" else "big")))
                            fill(k, focusSize = true)
                        }
                    }
                    line.addView(s, LinearLayout.LayoutParams(dp(72), -1).apply { leftMargin = dp(8) })
                    if (focus == k && focusSize) want = s
                }
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
                if (focus == k && !focusMove && !focusSize) want = t
                box.addView(line, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = gap })
            }
            want?.requestFocus()
            preview.invalidate()
            building = false
        }
        fill()
    }

    /**
     * The result in miniature: the columns [HomeConfig.pack] makes of the cards on, a big card in the accent, small ones pale; what
     * waits is not drawn. Reads the settings when painted (the rows call invalidate after every change), never animated.
     */
    private class PackGlyph(c: Context) : View(c) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(cv: Canvas) {
            val conf = Store.get(context)
            val big = HomeConfig.bigs(conf)
            val p = HomeConfig.pack(Store.list(context, "widgets"), big, conf.optString("note").isNotEmpty())
            val g = resources.displayMetrics.density * 4f
            val sum = maxOf(HomeConfig.FLOOR, p.cols.sumOf { it.weight })
            val room = width - g * (p.cols.size - 1)
            var x = 0f
            for (col in p.cols) {
                val w = room * col.weight / sum
                val h = (height - g * (col.keys.size - 1)) / col.keys.size
                col.keys.forEachIndexed { j, k ->
                    paint.color = if (k in big) 0x668AB4F8 else 0x30FFFFFF
                    cv.drawRoundRect(x, j * (h + g), x + w, j * (h + g) + h, g * 2, g * 2, paint)
                }
                x += w + g
            }
        }
    }

    /** The backgrounds' small pictures, kept between openings of the panel (about 400 KB each); let go when memory is short ([forget]). */
    private val thumbs: MutableMap<String, Bitmap> = java.util.Collections.synchronizedMap(HashMap())
    fun forget() = thumbs.clear()

    /** A background tile's width in px, the same as [show] lays them out: five to a row in the panel. */
    private fun tileW(a: Activity): Int {
        val dm = a.resources.displayMetrics
        val dp = { v: Int -> (v * dm.density).toInt() }
        val inner = minOf(dm.widthPixels - dp(64), dp(600)) - dp(56)
        return (inner - 4 * dp(10)) / 5
    }

    /** A new photo or another day's Bing picture is another key: made again. */
    private fun thumbKey(a: Context, k: String, w: Int) = "$k:$w" + (Walls.file(a, k)?.let { ":" + it.name + it.lastModified() } ?: "")

    /** [k]'s tile picture, made once at the tile's size with its corners (off the main thread). */
    private fun thumb(a: Context, k: String, w: Int): Bitmap? {
        val key = thumbKey(a, k, w)
        thumbs[key]?.let { return it }
        val b = Walls.small(a, k, w, w * 9 / 16)?.let { round(it, w, w * 9 / 16, 12 * a.resources.displayMetrics.density) } ?: return null
        synchronized(thumbs) { thumbs.keys.removeAll { it.startsWith("$k:") }; thumbs[key] = b }
        return b
    }

    /** Makes the tiles' pictures beforehand (Home calls it on its own thread once it is shown), so the panel opens with them: ~100 KB each. */
    fun warm(a: Activity) {
        val w = tileW(a)
        val have = Walls.photos(a)
        for (k in HomeConfig.WALLS) if (WallArt.has(k) || k == HomeConfig.BING || k in have) thumb(a, k, w)
    }

    /** [b] cropped to [w] x [h] from its middle, with rounded corners, so its tile needs no clip or scaling of its own. */
    private fun round(b: Bitmap, w: Int, h: Int, r: Float): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val k = maxOf(w.toFloat() / b.width, h.toFloat() / b.height)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = android.graphics.BitmapShader(b, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP).apply {
                setLocalMatrix(android.graphics.Matrix().apply { setScale(k, k); postTranslate((w - b.width * k) / 2, (h - b.height * k) / 2) })
            }
        }
        Canvas(out).drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, p)
        b.recycle()
        return out
    }

    private fun ring(d: Float, w: Int, color: Int) = GradientDrawable().apply { cornerRadius = 12 * d; setStroke((w * d).toInt(), color); setColor(Color.TRANSPARENT) }

    /** The name under a background's tile: from the strings, "Photo 3" for the person's third photo. */
    private fun wallName(a: Context, k: String): String = when {
        k == "photo" -> a.getString(R.string.wall_photo)
        HomeConfig.isPhoto(k) -> a.getString(R.string.wall_photo_n, k.removePrefix("photo").toInt())
        else -> a.getString(a.resources.getIdentifier("wall_$k", "string", a.packageName))
    }
}
