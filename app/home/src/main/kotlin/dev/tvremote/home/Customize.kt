package dev.tvremote.home

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
    private const val STEP_FILL = 0xF0161A24.toInt()                   // the rows stepper: nearly opaque, its number read over any background
    private const val DIM = 0xA6FFFFFF.toInt()
    private const val GLASS_FOCUS = 0x2EFFFFFF                          // a row of the panel: focused, at rest, its edge
    private const val GLASS_FILL = 0x12FFFFFF
    private const val GLASS_EDGE = 0x1FFFFFFF
    private const val TINT_BG = 0xFF6F8DFF.toInt()                      // each group's chip hue on the first level: its icon in full, the chip a fifth of it
    private const val TINT_LOOK = 0xFFB48CFF.toInt()
    private const val TINT_LAYOUT = 0xFF5EEAD4.toInt()
    private const val TINT_WIDGETS = 0xFFFFB86B.toInt()
    private const val TINT_SYSTEM = 0xFFFF7A8A.toInt()
    private const val TINT_LANG = 0xFF7BE0A0.toInt()
    private const val TINT_KEYS = 0xFFFFD27A.toInt()
    private const val CHIP_ALPHA = 0x33000000                           // OR-ed into a tint

    /** One group of settings: a row on the first level and a page of controls behind it. [last] is where the focus was when it was left. */
    private class Group(c: Context, val key: String, val name: Int, val icon: Int, val tint: Int) {
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        val scroll = ScrollView(c).apply { isVerticalScrollBarEnabled = false; clipChildren = false; addView(col) }
        val page = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        var row: View? = null; var head: View? = null; var sum: TextView? = null; var last: View? = null
        var start: (() -> View?)? = null                                 // where a first visit lands (the background in use), else the first control
    }

    private val EVERY_WORDS = mapOf("off" to R.string.rot_off, "30m" to R.string.rot_30m, "1h" to R.string.rot_1h, "6h" to R.string.rot_6h, "day" to R.string.rot_day, "show" to R.string.rot_show)

    /** The line under a group's name on the first level, from the config as it is now. */
    private fun summary(a: Context, key: String): String {
        val c = Store.get(a)
        fun on(k: String) = a.getString(if (c.optBoolean(k, true)) R.string.w_on else R.string.w_off)
        return when (key) {
            "bg" -> {
                val set = c.optString("wevery") != "off"
                val what = if (set) a.getString(R.string.g_bg_set, c.getJSONArray("walls").length(), a.getString(EVERY_WORDS.getValue(c.optString("wevery"))).lowercase()) else wallName(a, c.optString("wall"))
                what + " · " + a.getString(R.string.g_shade_n, c.optInt("shade", HomeConfig.SHADE_DEFAULT))
            }
            "look" -> a.getString(R.string.g_look_sum, a.getString(if (c.optString("focus", "glass") == "ring") R.string.c_focus_ring else R.string.c_focus_glass),
                if (!c.optBoolean("anim", true)) a.getString(R.string.w_off) else a.getString(when (c.optString("motion")) { "calm" -> R.string.c_motion_calm; "full" -> R.string.c_motion_full; else -> R.string.c_motion_auto }).lowercase())
            "layout" -> a.getString(R.string.g_cols_n, c.optInt("cols", HomeConfig.COLS_DEFAULT)) + " · " +
                (c.optDouble("rows", 0.0).let { if (it < 1) a.getString(R.string.g_rows_auto) else a.getString(R.string.g_rows_n, rowsWord(a, it)) })
            "widgets" -> a.getString(R.string.g_widgets_n, Store.list(a, "widgets").size)
            "system" -> a.getString(R.string.g_power) + ": " + on("power") + " · " + a.getString(R.string.g_dream) + ": " + on("dream")
            "keys" -> a.getString(R.string.g_keys_sum, HomeConfig.keyApps(c.optJSONObject("keyapps")).size.takeIf { c.optBoolean("keyon") } ?: 0,
                a.getString(if (c.optBoolean("autoon")) R.string.w_on else R.string.w_off))
            else -> when (c.optString("lang")) { "vi" -> "Tiếng Việt"; "en" -> "English"; else -> a.getString(R.string.c_lang_tv) }
        }
    }

    /** The first view that can take the focus in [v], top to bottom (a hidden one is passed over). */
    private fun firstFocusable(v: View): View? {
        if (v.visibility != View.VISIBLE) return null
        if (v.isFocusable) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) firstFocusable(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun rowsWord(a: Context, v: Double) = if (v < 1) a.getString(R.string.c_rows_auto) else String.format(java.util.Locale.ROOT, "%.1f", v)

    /**
     * "rows" or "cols" with the remote: rows ◀ ▶ a tenth, ▲ ▼ a whole row; columns ◀ ▶ ▲ ▼ one. OK or Back done. The panel steps
     * aside ([panel] hidden) and the number sits in the clock's corner with no dim, so each step shows on the home screen at once
     * (saved a moment after the last press: holding a key does not rebuild it every repeat). Then the panel is back as it was.
     * Rows below 1 are "by itself"; the most is what fits under the clock or the cards ([rowsFit]). "shade" in percent (◀ ▶ 5,
     * ▲ ▼ 10) with a bar under it, as a slider: the veil changes behind it as it moves.
     */
    private fun stepper(a: Activity, panel: Dialog, key: String, done: () -> Unit) {
        val d = a.resources.displayMetrics.density
        fun dp(v: Number) = (v.toFloat() * d).toInt()
        val rows = key == "rows"
        val shade = key == "shade"
        var v = if (rows) Store.get(a).optDouble("rows", 0.0) else if (shade) Store.get(a).optInt("shade", HomeConfig.SHADE_DEFAULT).toDouble() else Store.get(a).optInt("cols", HomeConfig.COLS_DEFAULT).toDouble()
        val most = if (shade) 100.0 else if (!rows) HomeConfig.COLS_MAX.toDouble() else if (rowsFit >= 1) rowsFit else HomeConfig.ROWS_MAX
        val bar = android.widget.ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = ColorStateList.valueOf(ACCENT); progressBackgroundTintList = ColorStateList.valueOf(GLASS_FOCUS)
            visibility = if (shade) View.VISIBLE else View.GONE
        }
        val dialog = Dialog(a, R.style.Menu)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val num = Font.on(TextView(a)).apply { textSize = 44f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = Font.light }
        val note = Font.on(TextView(a)).apply { textSize = 13f; setTextColor(DIM); gravity = Gravity.CENTER }
        fun say() {
            num.text = if (rows) rowsWord(a, v) else if (shade) "${v.toInt()}%" else v.toInt().toString()
            bar.progress = v.toInt()
            note.text = a.getString(if (shade) R.string.c_shade_keys else if (rows && v >= 1 && v >= most - 0.05) R.string.c_rows_most else if (rows) R.string.c_rows_keys else R.string.c_cols_keys)
        }
        val save = Runnable { Store.change(a, JSONObject().put(key, if (rows) v else v.toInt())) }
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; isFocusable = true; isFocusableInTouchMode = true   // opened by a touch (a mouse, adb), it still takes the keys
            setPadding(dp(28), dp(18), dp(28), dp(18))
            background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(STEP_FILL); setStroke(dp(1), ACCENT) }
            addView(Font.on(TextView(a)).apply { text = a.getString(if (shade) R.string.c_shade else if (rows) R.string.c_rows else R.string.c_cols); textSize = 15f; setTextColor(DIM); gravity = Gravity.CENTER })
            addView(num, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            addView(bar, LinearLayout.LayoutParams(-1, dp(6)).apply { topMargin = dp(6) })
            addView(note, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        box.setOnKeyListener { _, code, e ->
            if (e.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val step = when (code) {
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> if (rows) -0.1 else if (shade) -5.0 else -1.0; android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> if (rows) 0.1 else if (shade) 5.0 else 1.0
                android.view.KeyEvent.KEYCODE_DPAD_UP -> if (shade) 10.0 else 1.0; android.view.KeyEvent.KEYCODE_DPAD_DOWN -> if (shade) -10.0 else -1.0
                android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> { dialog.dismiss(); return@setOnKeyListener true }
                else -> return@setOnKeyListener false
            }
            v = if (shade) (v + step).coerceIn(0.0, most) else if (!rows) (v + step).coerceIn(HomeConfig.COLS_MIN.toDouble(), most)
                else if (v < 1) (if (step > 0) 1.0 else 0.0) else (v + step).let { if (it < 0.95) 0.0 else HomeConfig.rows(minOf(it, most)) }
            say(); box.removeCallbacks(save); box.postDelayed(save, 200); true
        }
        say()
        dialog.setContentView(box)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); setDimAmount(0f)
            setLayout(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT)
            attributes = attributes.apply { gravity = Gravity.TOP or Gravity.START; x = dp(48); y = dp(40) }
        }
        dialog.setOnDismissListener { box.removeCallbacks(save); save.run(); panel.show(); done() }
        // the panel out of sight only once the number has the window focus: hidden first, the focus fell to the home screen behind
        dialog.setOnShowListener { box.requestFocus(); panel.hide(); box.post { box.requestFocus() } }
        dialog.show()
    }

    fun show(a: Activity): Dialog {
        val d = a.resources.displayMetrics.density
        fun dp(v: Number) = (v.toFloat() * d).toInt()
        val dm = a.resources.displayMetrics
        val edge = dp(24)
        val panelW = minOf(dm.widthPixels - dp(64), dp(600))
        val inner = panelW - dp(56)
        var stepBack: () -> Boolean = { false }                        // Back on a page goes up a level (set below); on the first level it closes the panel
        val dialog = object : Dialog(a, R.style.Menu) {
            @Suppress("OVERRIDE_DEPRECATION") override fun onBackPressed() { if (!stepBack()) super.onBackPressed() }
        }
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val groups = listOf(
            Group(a, "bg", R.string.bg, R.drawable.ic_image, TINT_BG), Group(a, "look", R.string.g_look, R.drawable.ic_sparkle, TINT_LOOK),
            Group(a, "layout", R.string.g_layout, R.drawable.ic_apps, TINT_LAYOUT), Group(a, "widgets", R.string.w_widgets, R.drawable.ic_gauge, TINT_WIDGETS),
            Group(a, "system", R.string.g_system, R.drawable.ic_power, TINT_SYSTEM), Group(a, "keys", R.string.g_keys, R.drawable.ic_keyboard, TINT_KEYS),
            Group(a, "lang", R.string.c_lang, R.drawable.ic_globe, TINT_LANG))
        for (g in groups) g.col.setPadding(dp(28), dp(8), dp(28), dp(28))
        val top = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(dp(28), dp(26), dp(28), dp(28)) }
        var col = groups[0].col                                        // the page being filled; each page's controls go into its own column
        fun label(t: String, size: Float, color: Int) = Font.on(TextView(a)).apply { text = t; textSize = size; setTextColor(color) }
        fun icon(res: Int, color: Int, size: Int) = ImageView(a).apply { setImageResource(res); imageTintList = ColorStateList.valueOf(color) }.let { it to size }
        fun section(t: String) = col.addView(label(t.uppercase(), 12f, DIM).apply { letterSpacing = 0.12f }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(22); bottomMargin = dp(10) })
        fun glass(radius: Int, fill: Int, stroke: Int) = GradientDrawable().apply { cornerRadius = dp(radius).toFloat(); setColor(fill); setStroke(dp(1), stroke) }

        // backgrounds, five to a row, a ✓ on the one in use (rotation off) or on each of the set it turns through (rotation on)
        val conf = Store.get(a)
        val have = Walls.photos(a)
        val keys = HomeConfig.WALLS.filter { !HomeConfig.isPhoto(it) || it in have }
        val tiles = ArrayList<Pair<String, View>>()
        val cols = 5; val gap = dp(10); val w = tileW(a)
        val shots = java.util.concurrent.Executors.newSingleThreadExecutor()    // the scenes drawn and the photos read small, off the main thread
        dialog.setOnDismissListener { shots.shutdownNow(); Keys.onState = null }
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
        val everyWords = EVERY_WORDS
        val chips = ArrayList<Pair<String, TextView>>()
        fun markEvery() { val e = Store.get(a).optString("wevery"); for ((k, v) in chips) v.isSelected = k == e }
        col.addView(label(a.getString(R.string.rot), 13f, Color.WHITE), LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(8) })
        var chipRow: LinearLayout? = null
        HomeConfig.EVERY.forEachIndexed { i, k ->
            if (i % 3 == 0) { chipRow = LinearLayout(a).apply { clipChildren = false; clipToPadding = false }; col.addView(chipRow, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(8) }) }
            val chip = Font.on(TextView(a)).apply {
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
                val c = if (HomeConfig.isPhoto(k) || k == HomeConfig.BING || k == HomeConfig.WEB) intArrayOf(0xFF2A2F3A.toInt()) else wallColors(k)
                background = (if (c.all { it == c[0] }) GradientDrawable().apply { setColor(c[0]) } else GradientDrawable(GradientDrawable.Orientation.TL_BR, c))
                    .apply { cornerRadius = dp(12).toFloat() }                // rounded, so not [wallDrawable]'s ColorDrawable
                if (WallArt.has(k) || Walls.isPicture(k)) {                   // the picture itself, in the tile's rounded corners: the same painter as the screen
                    val pv = ImageView(a).apply { scaleType = ImageView.ScaleType.FIT_XY }   // the picture is made at the tile's size with its corners: nothing scaled or clipped per frame
                    addView(pv, FrameLayout.LayoutParams(-1, -1))
                    val drawn = thumbs[thumbKey(a, k, w)]                    // made beforehand ([warm]) or on an earlier opening: there as it slides in
                    if (drawn != null) pv.setImageBitmap(drawn)
                    else shots.execute { val b = thumb(a, k, w); a.runOnUiThread {
                        if (b != null) put(pv, b)
                        else if (HomeConfig.isPhoto(k)) { val (iv, sz) = icon(R.drawable.ic_image, DIM, dp(22)); addView(iv, FrameLayout.LayoutParams(sz, sz, Gravity.CENTER)) }   // a photo that cannot be read: a mark, not a blank grey
                    } }
                }
                foreground = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), ring(d, 3, Color.WHITE))
                    addState(intArrayOf(android.R.attr.state_selected), ring(d, 2, ACCENT))
                    addState(intArrayOf(), ring(d, 1, 0x33FFFFFF))
                }
                if (k == HomeConfig.WEB) { val (iv, s) = icon(R.drawable.ic_globe, ACCENT, dp(26)); addView(iv, FrameLayout.LayoutParams(s, s, Gravity.CENTER)) }
                if (k == HomeConfig.BING && !Online.file(a).isFile) { val (iv, s) = icon(R.drawable.ic_image, DIM, dp(22)); addView(iv, FrameLayout.LayoutParams(s, s, Gravity.CENTER)) }   // not fetched yet
                addView(FrameLayout(a).apply {                            // the ✓, shown on the chosen one (mark)
                    tag = "check"
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ACCENT) }
                    val (iv, s) = icon(R.drawable.ic_check, 0xFF0B1020.toInt(), dp(14)); addView(iv, FrameLayout.LayoutParams(s, s, Gravity.CENTER))
                }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(6), dp(6), 0) })
                onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
                setOnClickListener {
                    val c = Store.get(a)
                    if (k == HomeConfig.WEB) {                                // the link is pasted on a phone (a QR), how it updates is picked there with the D-pad
                        PhotoScreen.show(a, web = true) { dialog.dismiss() }
                        return@setOnClickListener
                    }
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
                // a photo sent from a phone: hold OK to delete it, after a question (the same as the phone's ✕: HomeReceiver.drop)
                if (HomeConfig.isPhoto(k)) setOnLongClickListener {
                    android.app.AlertDialog.Builder(a, R.style.Menu).setTitle(R.string.photo_del_q).setMessage(R.string.photo_del_note)
                        .setPositiveButton(R.string.photo_del) { _, _ ->
                            HomeReceiver.drop(a, k)
                            thumbs.keys.removeAll { it.startsWith("$k:") }
                            // the focus to its neighbour: the hidden tile's focus fell to the panel's first control, far up
                            val at = tiles.indexOfFirst { it.first == k }
                            val near = tiles.getOrNull(at + 1)?.second ?: tiles.getOrNull(at - 1)?.second
                            (parent as? View)?.visibility = View.GONE
                            tiles.removeAll { it.first == k }
                            markAll()
                            near?.requestFocus()
                        }
                        .setNegativeButton(android.R.string.cancel, null).show()
                    true
                }
            }
            tiles.add(k to t)
            cell.addView(t, LinearLayout.LayoutParams(w, w * 9 / 16))
            cell.addView(label(wallName(a, k), 12f, DIM).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(w, -2).apply { topMargin = dp(5) })
            row!!.addView(cell, LinearLayout.LayoutParams(w, -2).apply { if (i % cols > 0) leftMargin = gap })
        }
        markAll(); markEvery()
        if (keys.any { HomeConfig.isPhoto(it) }) col.addView(label(a.getString(R.string.photo_del_hint), 12f, DIM), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })

        // a photo from a phone on the same Wi-Fi: a QR screen, no ADB and no TV Setup; when it was used the panel closes to show the new background
        col.addView(LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), glass(16, GLASS_FOCUS, Color.WHITE))
                addState(intArrayOf(), glass(16, GLASS_FILL, GLASS_EDGE))
            }
            addView(ImageView(a).apply { setImageResource(R.drawable.ic_cast); imageTintList = ColorStateList.valueOf(ACCENT) }, LinearLayout.LayoutParams(dp(26), dp(26)))
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(a.getString(R.string.wall_phone), 15f, Color.WHITE).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                addView(label(a.getString(R.string.wall_phone_sub), 12f, DIM).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
            onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
            setOnClickListener { PhotoScreen.show(a) { dialog.dismiss() } }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = gap })

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
            var on = if (choices != null || key == "rows" || key == "shade") pick > 0 || key == "cols" || key == "rows" || key == "shade" else conf.optBoolean(key, true)
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
                state.text = if (key == "rows") rowsWord(a, Store.get(a).optDouble("rows", 0.0)) else if (key == "shade") "${Store.get(a).optInt("shade", HomeConfig.SHADE_DEFAULT)}%" else choices?.get(pick)?.second ?: if (sub != null) "$word · $sub" else word
                state.setTextColor(if (on) ACCENT else DIM)
            }
            paint()
            t.onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
            t.setOnClickListener {
                if (key == "rows" || key == "cols" || key == "shade") {
                    stepper(a, dialog, key) { pick = choices?.indexOfFirst { it.first == Store.get(a).optString(key) }?.coerceAtLeast(0) ?: 0; paint() }
                    return@setOnClickListener
                }
                if (choices != null) {                                 // a new language builds the screen again (HomeActivity), this panel with it
                    pick = (pick + 1) % choices.size; on = pick > 0 || key == "cols"; paint()
                    Store.change(a, JSONObject().put(key, choices[pick].first)); return@setOnClickListener
                }
                on = !on
                try { Store.change(a, JSONObject().put(key, on)) } catch (e: SecurityException) { on = !on; systemDream(a) }
                paint()
            }
            pair!!.addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { if (inRow == 1) leftMargin = gap })
            inRow++
        }
        fun endRow() { if (inRow == 1) pair!!.addView(View(a), LinearLayout.LayoutParams(0, 0, 1f).apply { leftMargin = gap }); pair = null; inRow = 0 }   // a lone tile keeps half the width
        // the veil over a photo, a scene or the page: a busy one fights the clock and the names (HomeConfig "shade")
        tile(R.drawable.ic_contrast, a.getString(R.string.c_shade), null, "shade")
        endRow()
        col = groups[1].col
        tile(R.drawable.ic_timer, a.getString(R.string.clock24), null, "clock24")
        tile(R.drawable.ic_message, a.getString(R.string.greet), null, "greet")
        tile(R.drawable.ic_sparkle, a.getString(R.string.motion), null, "anim")
        tile(R.drawable.ic_sparkle, a.getString(R.string.c_motion), null, "motion",
            listOf("" to a.getString(R.string.c_motion_auto), "calm" to a.getString(R.string.c_motion_calm), "full" to a.getString(R.string.c_motion_full)))
        tile(R.drawable.ic_eye, a.getString(R.string.c_focus), null, "focus",
            listOf("ring" to a.getString(R.string.c_focus_ring), "glass" to a.getString(R.string.c_focus_glass)))
        endRow()
        col = groups[2].col
        tile(R.drawable.ic_apps, a.getString(R.string.c_cols), null, "cols", (HomeConfig.COLS_MIN..HomeConfig.COLS_MAX).map { "$it" to "$it" })
        tile(R.drawable.ic_updown, a.getString(R.string.c_rows), null, "rows")
        endRow()
        col = groups[3].col
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
        tilesPage(a, col, ::glass, ::label, gap)
        col = groups[4].col
        tile(R.drawable.ic_power, a.getString(R.string.c_power), null, "power")   // the bar's power button (HomeConfig "power")
        tile(R.drawable.ic_moon, a.getString(R.string.dream), a.getString(R.string.dream_sub), "dream")
        endRow()
        col = groups[5].col
        keysPage(a, col, { r, t, k -> tile(r, t, null, k) }, ::endRow, ::glass, ::label, gap)
        col = groups[6].col
        tile(R.drawable.ic_globe, a.getString(R.string.c_lang), null, "lang",
            listOf("" to a.getString(R.string.c_lang_tv), "vi" to "Tiếng Việt", "en" to "English"))
        endRow()
        groups[0].start = { tiles.firstOrNull { it.second.isSelected }?.second ?: tiles.firstOrNull()?.second }

        // the first level: a row per group (its icon in a tinted chip, the name, what it is set to now) and the way out
        top.addView(LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(FrameLayout(a).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(TINT_BG, 0xFF9B6BFF.toInt())).apply { cornerRadius = dp(14).toFloat() }
                val (iv, s) = icon(R.drawable.ic_sliders, Color.WHITE, dp(24))
                addView(iv, FrameLayout.LayoutParams(s, s, Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(a.getString(R.string.customize), 22f, Color.WHITE).apply { typeface = Font.medium })
                addView(label(a.getString(R.string.c_sub), 13f, DIM))
            }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(14) })
        })
        val topScroll = ScrollView(a).apply { isVerticalScrollBarEnabled = false; clipChildren = false; addView(top) }
        val stack = FrameLayout(a).apply { clipChildren = false }
        stack.addView(topScroll, FrameLayout.LayoutParams(-1, -1))
        var cur: Group? = null                                         // the page open, null on the first level
        var gen = 0                                                    // a slide that was cut short by the next one does not finish its hiding
        val shift = panelW / 6f
        fun slides() = Motion.on && !Motion.calm                       // calm and off: the page is simply there
        fun refresh() { for (g in groups) g.sum?.text = summary(a, g.key) }
        fun enter(g: Group) {
            cur = g; val n = ++gen
            topScroll.animate().cancel(); g.page.animate().cancel()
            topScroll.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS   // while it fades the keys already belong to the page
            g.page.visibility = View.VISIBLE
            (g.last?.takeIf { it.isShown } ?: g.start?.invoke() ?: firstFocusable(g.col))?.requestFocus()
            if (slides()) {
                g.page.translationX = shift; g.page.alpha = 0f
                g.page.animate().translationX(0f).alpha(1f).setDuration(Motion.ms(220)).setInterpolator(Motion.glide).withLayer().start()
                topScroll.animate().translationX(-shift / 2).alpha(0f).setDuration(Motion.ms(220)).setInterpolator(Motion.glide).withLayer()
                    .withEndAction { if (n == gen) topScroll.visibility = View.GONE }.start()
            } else { g.page.translationX = 0f; g.page.alpha = 1f; topScroll.visibility = View.GONE }
        }
        fun leave(): Boolean {
            val g = cur ?: return false
            cur = null; val n = ++gen
            g.last = g.page.findFocus()
            topScroll.animate().cancel(); g.page.animate().cancel()
            topScroll.visibility = View.VISIBLE; topScroll.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            refresh()
            g.row?.requestFocus()                                      // back on the group just left
            if (slides()) {
                topScroll.translationX = -shift / 2; topScroll.alpha = 0f
                topScroll.animate().translationX(0f).alpha(1f).setDuration(Motion.ms(220)).setInterpolator(Motion.glide).withLayer().start()
                g.page.animate().translationX(shift).alpha(0f).setDuration(Motion.ms(220)).setInterpolator(Motion.glide).withLayer()
                    .withEndAction { if (n == gen) g.page.visibility = View.GONE }.start()
            } else { topScroll.translationX = 0f; topScroll.alpha = 1f; g.page.visibility = View.GONE }
            return true
        }
        stepBack = ::leave
        for (g in groups) {
            val chip = FrameLayout(a).apply {
                background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor((g.tint and 0x00FFFFFF) or CHIP_ALPHA) }
                val (iv, s) = icon(g.icon, g.tint, dp(24))
                addView(iv, FrameLayout.LayoutParams(s, s, Gravity.CENTER))
            }
            val chevron = ImageView(a).apply { setImageResource(R.drawable.ic_right); imageTintList = ColorStateList.valueOf(DIM) }
            g.sum = label("", 13f, DIM).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
            val row = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
                setPadding(dp(10), dp(8), dp(14), dp(8))
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), glass(18, GLASS_FOCUS, Color.WHITE))
                    addState(intArrayOf(), glass(18, GLASS_FILL, GLASS_EDGE))
                }
                addView(chip, LinearLayout.LayoutParams(dp(42), dp(42)))
                addView(LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label(a.getString(g.name), 16f, Color.WHITE).apply { maxLines = 1; typeface = Font.medium })
                    addView(g.sum!!)
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
                addView(chevron, LinearLayout.LayoutParams(dp(22), dp(22)))
                onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f); chevron.imageTintList = ColorStateList.valueOf(if (f) Color.WHITE else DIM) }
                setOnClickListener { enter(g) }
                setOnKeyListener { _, code, e -> if (code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT && e.action == android.view.KeyEvent.ACTION_DOWN) { enter(g); true } else false }
            }
            g.row = row
            top.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = if (g === groups[0]) dp(16) else dp(8) })
            // the page: a way back as its head ("‹ name"), then the controls
            g.head = LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
                contentDescription = a.getString(R.string.g_back)
                setPadding(dp(10), dp(8), dp(16), dp(8))
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), glass(16, GLASS_FOCUS, Color.WHITE))
                    addState(intArrayOf(), GradientDrawable().apply { setColor(Color.TRANSPARENT) })
                }
                addView(ImageView(a).apply { setImageResource(R.drawable.ic_left); imageTintList = ColorStateList.valueOf(g.tint) }, LinearLayout.LayoutParams(dp(24), dp(24)))
                addView(label(a.getString(g.name), 20f, Color.WHITE).apply { typeface = Font.medium }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
                onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
                setOnClickListener { leave() }
            }
            g.page.addView(g.head, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(18), dp(20), 0, dp(4)) })
            g.page.addView(g.scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            stack.addView(g.page, FrameLayout.LayoutParams(-1, -1))
        }
        refresh()
        top.addView(label(a.getString(R.string.more), 12f, 0x8CFFFFFF.toInt()), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })
        val done = Font.on(TextView(a)).apply {
            text = a.getString(R.string.done); textSize = 16f; setTextColor(0xFF111318.toInt()); gravity = Gravity.CENTER
            typeface = Font.medium
            isFocusable = true; isClickable = true; setPadding(dp(28), dp(11), dp(28), dp(11))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(Color.WHITE) })
                addState(intArrayOf(), GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(0xFFB8C7E6.toInt()) })
            }
            onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
            setOnClickListener { dialog.dismiss() }
        }
        top.addView(done, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        val panel = object : FrameLayout(a) {
            // Left from a control with nothing to its left, on a page: back to the first level (a held key does it once)
            override fun dispatchKeyEvent(e: android.view.KeyEvent): Boolean {
                if (cur != null && e.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                    val f = findFocus()
                    if (f == null) { if (e.action == android.view.KeyEvent.ACTION_DOWN) cur?.let { g -> (firstFocusable(g.col) ?: g.page)?.requestFocus() }; return true }
                    val west = f.focusSearch(View.FOCUS_LEFT)
                    if (west == null || west === cur?.head) {                  // the head sits up-left of the first control: the way back, not a neighbour
                        if (e.action == android.view.KeyEvent.ACTION_DOWN && e.repeatCount == 0) leave(); return true
                    }
                }
                return super.dispatchKeyEvent(e)
            }
        }.apply {
            // solid: the home screen's text must not show through
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF161B2B.toInt(), 0xFF0C1020.toInt())).apply {
                cornerRadius = dp(28).toFloat(); setStroke(dp(1), 0x33FFFFFF)
            }
            // no clip and no fading edge: clipped to its corners it cost the FPT box ~10 ms a frame, the ScrollView's fading edge drew
            // the whole list into a layer every frame (55 ms a frame while a tile grew); the list stops 1 dp inside the border instead
            addView(stack, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
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
            groups[0].row?.let {
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

    /**
     * "Keys & startup": remote keys that open an app, and the app that opens when the TV is turned on (HomeConfig keyon / keyapps /
     * autoon / autoapp, both default off). A key row picks its app from a list; the lines under the switches say what needs TV Setup
     * (everywhere but TV Home's own screen, and the power-on app), kept current by [Keys.onState] when TV Setup answers.
     */
    private fun keysPage(a: Activity, col: LinearLayout, tile: (Int, String, String) -> Unit, endRow: () -> Unit,
                         glass: (Int, Int, Int) -> GradientDrawable, label: (String, Float, Int) -> TextView, gap: Int) {
        val d = a.resources.displayMetrics.density
        fun dp(v: Number) = (v.toFloat() * d).toInt()
        val apps = Apps.list(a)
        fun name(pkg: String) = apps.firstOrNull { it.pkg == pkg }?.label ?: if (pkg.isEmpty()) "—" else pkg
        val none = a.getString(R.string.k_none)
        val subs = ArrayList<() -> Unit>()                             // each row's second line, painted again after a choice
        fun pick(title: String, now: String, done: (String) -> Unit) {
            val list = apps.sortedBy { it.label.lowercase() }
            val items = arrayOf(none) + list.map { it.label }
            AlertDialog.Builder(a, R.style.Menu).setTitle(title).setItems(items) { _, i -> done(if (i == 0) "" else list[i - 1].pkg); subs.forEach { it() } }.show()
        }
        fun row(title: String, value: () -> String, onPick: () -> Unit) {
            val sub = label("", 12f, ACCENT).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
            subs.add { sub.text = value() }; sub.text = value()
            col.addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), glass(16, GLASS_FOCUS, Color.WHITE))
                    addState(intArrayOf(), glass(16, GLASS_FILL, GLASS_EDGE))
                }
                addView(label(title, 15f, Color.WHITE).apply { maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(sub, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
                onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
                setOnClickListener { onPick() }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = gap })
        }
        fun hint(text: () -> String) {
            val t = label("", 12f, DIM)
            subs.add { t.text = text() }; t.text = text()
            col.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = gap; leftMargin = dp(6) })
        }
        fun keys() = HomeConfig.keyApps(Store.get(a).optJSONObject("keyapps"))

        tile(R.drawable.ic_keyboard, a.getString(R.string.c_keyapps), "keyon")
        endRow()
        hint { a.getString(if (!Keys.setup(a)) R.string.k_need_setup else if (!Keys.can(a)) R.string.k_need_adb else R.string.k_everywhere) }
        val codes = HomeConfig.KEY_COLOURS + Keys.dead(a).filter { it in HomeConfig.KEY_DEAD } + HomeConfig.KEY_NUMBERS
        for (code in codes) row(Keys.name(a, code), { keys()[code]?.let { name(it) } ?: none }) {
            pick(Keys.name(a, code), keys()[code].orEmpty()) { pkg ->
                val m = JSONObject(); for ((k, v) in keys()) if (k != code) m.put(k.toString(), v)
                if (pkg.isNotEmpty()) m.put(code.toString(), pkg)
                Store.change(a, JSONObject().put("keyapps", m))
            }
        }
        col.addView(View(a), LinearLayout.LayoutParams(-1, dp(14)))
        tile(R.drawable.ic_power, a.getString(R.string.c_autoopen), "autoon")
        endRow()
        row(a.getString(R.string.c_autoapp), { name(Store.get(a).optString("autoapp")) }) {
            pick(a.getString(R.string.c_autoapp), Store.get(a).optString("autoapp")) { pkg -> Store.change(a, JSONObject().put("autoapp", pkg)) }
        }
        hint { a.getString(if (!Keys.setup(a)) R.string.k_auto_need_setup else R.string.k_auto_note) }
        Keys.onState = { subs.forEach { it() } }
        Keys.push(a)                                                  // asks TV Setup again, so the lines above are true when it answers
    }

    /**
     * Live tiles ([Tiles]) the TV Setup offers: a row each, pressed to put it on the app grid or take it off. A minimal entry; the
     * "Add to home screen" browser lists tiles too. Without tiles (no TV Setup, an old one, another key) one line says why.
     */
    private fun tilesPage(a: Activity, col: LinearLayout, glass: (Int, Int, Int) -> GradientDrawable, label: (String, Float, Int) -> TextView, gap: Int) {
        val d = a.resources.displayMetrics.density
        fun dp(v: Number) = (v.toFloat() * d).toInt()
        val offered = Tiles.available(a)
        col.addView(label(a.getString(R.string.c_tiles).uppercase(), 12f, DIM).apply { letterSpacing = 0.12f }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(18) })
        if (offered.isEmpty()) {
            col.addView(label(a.getString(if (Tiles.status(a) == Tiles.UNTRUSTED) R.string.power_untrusted else R.string.c_tiles_none), 12f, DIM),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = gap; leftMargin = dp(6) })
            return
        }
        for (t in offered) {
            val sub = label("", 12f, ACCENT).apply { maxLines = 1 }
            fun on() = Store.list(a, "tiles").contains(t.key)
            fun paint() { sub.text = if (on()) a.getString(R.string.c_tiles_on) else a.getString(R.string.w_off) }
            paint()
            col.addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), glass(16, GLASS_FOCUS, Color.WHITE))
                    addState(intArrayOf(), glass(16, GLASS_FILL, GLASS_EDGE))
                }
                addView(label(t.title, 15f, Color.WHITE).apply { maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(sub, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
                onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
                setOnClickListener {
                    val now = Store.list(a, "tiles")
                    Store.change(a, JSONObject().put("tiles", org.json.JSONArray(if (t.key in now) now - t.key else now + t.key)))
                    paint()
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = gap })
        }
    }

    /** The screensaver switch cannot write the setting (no WRITE_SECURE_SETTINGS yet; a phone or computer grants it): the system's own screen for it instead. */
    private fun systemDream(a: Activity) {
        val go = {
            try { a.startActivity(android.content.Intent(android.provider.Settings.ACTION_DREAM_SETTINGS)) }
            catch (e: Exception) { try { a.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)) } catch (e2: Exception) { } }
        }
        // a Toast was hidden by the settings screen opened at once; a question the person reads, then goes on
        android.app.AlertDialog.Builder(a, R.style.Menu).setMessage(R.string.dream_system).setPositiveButton(R.string.ok) { _, _ -> go() }.show()
    }

    private val WIDGET_ROWS = listOf(                                  // key, icon, name, line under it
        arrayOf<Any>("system", R.drawable.ic_gauge, R.string.w_system, R.string.c_system_sub),
        arrayOf<Any>("usage", R.drawable.ic_chart, R.string.w_usage, R.string.c_usage_sub),
        arrayOf<Any>("calendar", R.drawable.ic_calendar, R.string.w_calendar, R.string.c_calendar_sub),
        arrayOf<Any>("lunar", R.drawable.ic_moon, R.string.w_lunar, R.string.c_lunar_sub),
        arrayOf<Any>("timer", R.drawable.ic_timer, R.string.w_timer, R.string.c_timer_sub),
        arrayOf<Any>("weather", R.drawable.ic_weather, R.string.w_weather, R.string.c_weather_sub),
        arrayOf<Any>("clock", R.drawable.ic_clock, R.string.w_clock, R.string.c_clock_sub),
    )

    private val QR_ROW = arrayOf<Any>("qr", R.drawable.ic_qr, R.string.w_qr, R.string.c_qr_sub)

    /** What a QR card holds, in a word: "Wi-Fi", "Link" … (its fields are written on the phone or the web page). */
    private fun qrKind(a: Context, card: JSONObject) = a.getString(when (card.optString("tpl")) {
        "wifi" -> R.string.qr_t_wifi; "link" -> R.string.qr_t_link; "tel" -> R.string.qr_t_tel; "sms" -> R.string.qr_t_sms
        "mail" -> R.string.qr_t_mail; "contact" -> R.string.qr_t_contact; else -> R.string.qr_t_text })

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
            val conf = Store.get(a)
            val qrs = conf.optJSONObject("qrs") ?: JSONObject()
            val order = (on + HomeConfig.WIDGETS.filter { it !in on }).filter { it !in HomeQr.KEYS || qrs.has(it) }   // a QR card is written on the phone or the web page: only those that exist
            val big = HomeConfig.bigs(conf)
            val waiting = HomeConfig.pack(on, big, conf.optString("note").isNotEmpty()).waiting.toSet()
            var want: View? = null
            order.forEachIndexed { i, k ->
                val r = WIDGET_ROWS.firstOrNull { it[0] == k } ?: QR_ROW
                val qr = if (k in HomeQr.KEYS) qrs.getJSONObject(k) else null
                val isOn = k in on
                val line = LinearLayout(a).apply { clipChildren = false; clipToPadding = false }
                val ic = ImageView(a).apply { setImageResource(r[1] as Int); imageTintList = ColorStateList.valueOf(if (isOn) ACCENT else DIM) }
                val state = label("", 12f, if (isOn) ACCENT else DIM).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
                val sub = if (qr != null) qrKind(a, qr) else a.getString(r[3] as Int)
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
                        addView(label(if (qr != null) a.getString(R.string.w_qr_named, qr.optString("cap").ifEmpty { qrKind(a, qr) }) else a.getString(r[2] as Int), 15f, Color.WHITE).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
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
