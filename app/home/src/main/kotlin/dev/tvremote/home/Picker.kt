package dev.tvremote.home

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvremote.tvui.Glass
import dev.tvremote.tvui.Motion
import dev.tvremote.tvui.TvGlass
import dev.tvremote.tvui.TvTile
import dev.tvremote.tvui.TvUi
import org.json.JSONArray
import org.json.JSONObject

internal const val PK_DIM = 0xA6FFFFFF.toInt()                      // a section name, a hint: white at about two thirds
internal const val PK_SOFT = 0xB3FFFFFF.toInt()
internal const val PK_FAINT = 0x99FFFFFF.toInt()
internal const val PK_SCRIM = 0xB30E1116.toInt()                    // the screen behind a full-screen picker
internal const val PK_EMPTY = 0xFF232838.toInt()                     // a quick key with nothing on it
private const val PK_CHECK = 0xFF2F7DF6.toInt()
private const val PK_TAG = 0xCC101420.toInt()
private const val PK_BANNER_BACK = 0xFF1E2230.toInt()
private val PK_HUES = intArrayOf(0xFF27324A.toInt(), 0xFF2E3B2F.toInt(), 0xFF43293A.toInt(), 0xFF2B3A45.toInt(), 0xFF3D3424.toInt(), 0xFF2F2D4A.toInt())   // a tile without a picture: a colour from its key

/**
 * One tile of the picker and of the quick keys: the logo of a [Target] (an app's banner or icon, a shortcut's picture, a live tile's
 * mark) on a colour of its own, its name inside when it has no wide picture, and the focus TV Home's grid has ([TvTile]: the ring, or
 * the glass [TileFocus] moves, by the user's setting). [tag] is a small pill on a corner, [mark] a check, [dead] a tile that cannot
 * be used (greyed, no focus).
 */
class PickTile(c: Context, w: Int, private val dpx: (Number) -> Int, val key: String, label: String, art: App?, hue: String,
               tag: String = "", icon: Int = 0, plus: Boolean = false, color: Int = 0) : TvTile(c, w, dpx) {
    private val check = ImageView(c).apply { setImageResource(R.drawable.ic_check); imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(PK_CHECK) }; setPadding(dpx(3), dpx(3), dpx(3), dpx(3)); visibility = View.GONE }
    private val tagView = Font.on(TextView(c)).apply { textSize = 11f; setTextColor(Color.WHITE); maxLines = 1; setPadding(dpx(7), dpx(2), dpx(7), dpx(2))
        background = GradientDrawable().apply { cornerRadius = dpx(9).toFloat(); setColor(PK_TAG) }; visibility = View.GONE }
    private val h = w * 9 / 16
    var dead = false
        set(v) { field = v; isFocusable = !v; isClickable = !v; alpha = if (v) 0.38f else 1f }

    init {
        Font.on(name)
        val banner = art?.banner
        faceColor(if (banner != null) PK_BANNER_BACK else if (color != 0) color else shade(hue))
        if (banner != null) {
            fill(ImageView(c).apply { setImageDrawable(banner); scaleType = ImageView.ScaleType.CENTER_CROP })
            name.text = label
        } else {
            val inner = LinearLayout(c).apply { orientation = VERTICAL; gravity = Gravity.CENTER; setPadding(dpx(6), dpx(if (tag.isEmpty()) 4 else 22), dpx(6), dpx(4)) }   // a tag sits on the top edge: the picture starts under it
            val pic = art?.icon
            inner.addView(ImageView(c).apply {
                if (pic != null) setImageDrawable(pic)
                else { setImageResource(if (plus) R.drawable.ic_plus else icon.takeIf { it != 0 } ?: R.drawable.ic_apps); imageTintList = android.content.res.ColorStateList.valueOf(if (plus) PK_FAINT else Color.WHITE) }
            }, LayoutParams(h * 42 / 100, h * 42 / 100))
            if (label.isNotEmpty()) inner.addView(Font.on(TextView(c)).apply {
                text = label; textSize = 12f; setTextColor(if (plus) PK_SOFT else Color.WHITE); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER
                setPadding(0, dpx(4), 0, 0)
            }, LayoutParams(-1, -2))
            fill(inner)
            name.text = ""
        }
        face.addView(check, FrameLayout.LayoutParams(dpx(22), dpx(22), Gravity.TOP or Gravity.END).apply { setMargins(0, dpx(6), dpx(6), 0) })
        face.addView(tagView, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dpx(6), dpx(6), 0, 0) })
        if (tag.isNotEmpty()) { tagView.text = tag; tagView.visibility = View.VISIBLE }
    }

    fun mark(on: Boolean) { check.visibility = if (on) View.VISIBLE else View.GONE }
    fun tag(t: String) { tagView.text = t; tagView.visibility = if (t.isEmpty()) View.GONE else View.VISIBLE }
    val wide get() = h

    private fun shade(k: String): Int {
        return PK_HUES[(k.hashCode() and Int.MAX_VALUE) % PK_HUES.size]
    }
}

/**
 * The focus of the tiles in [area] (a FrameLayout round the grid, inside the scroller): each tile's own lift and ring or shine, and
 * the pane of glass gliding between them when the setting is "glass" ([Motion.glass]), exactly as on the home screen.
 */
class TileFocus(c: Context, private val area: FrameLayout, dp: (Number) -> Int) {
    private val glass = Glass(c, dp).also { area.addView(it, FrameLayout.LayoutParams(0, 0)) }

    fun wire(t: PickTile) {
        t.nudged = { x, y -> if (Motion.glass) glass.nudge(x, y) }
        t.setOnFocusChangeListener { v, on ->
            t.focus(on)
            if (on) { if (Motion.glass) glass.to(area, t.pane, TvTile.GROW, t.corner, Motion.on) else glass.hide() }
            else v.post { if (area.findFocus() == null) glass.hide() }
        }
    }

    fun hide() = glass.hide()
}

/**
 * "Add to home screen" and the choice of what a key or the power-on opens: one screen listing the apps (the hidden ones too), the
 * launcher shortcuts each app offers ([Pins]) and the live tiles ([Tiles]), made of [PickTile]s with a search over their names. In
 * [Mode.GRID] a press switches the tile on the app grid or off; in [Mode.TARGET] it returns the chosen [Target] (or "" for none).
 */
object Picker {
    enum class Mode { GRID, TARGET }

    private val bg = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "home-picker").apply { isDaemon = true } }

    private class Item(val key: String, val label: String, val app: App?, val tag: String, val section: Int, val icon: Int = 0)

    private fun fold(t: String) = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('đ', 'd').replace('Đ', 'D').lowercase()

    fun show(a: Activity, mode: Mode, title: String, now: String = "", onPick: (String) -> Unit = {}): Dialog {
        val d = a.resources.displayMetrics.density
        fun dp(v: Number) = (v.toFloat() * d).toInt()
        val dm = a.resources.displayMetrics
        val dialog = Dialog(a, R.style.Menu)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val panelW = minOf(dm.widthPixels - dp(64), dp(1180))
        val cols = if (panelW >= dp(1000)) 5 else 4
        val gap = dp(26)
        val tileW = (panelW - dp(2 * 34) - (cols - 1) * gap) / cols

        val area = FrameLayout(a).apply { clipChildren = false; clipToPadding = false }
        val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(dp(34), dp(24), dp(34), dp(70)) }   // room for a focused tile to grow past the first and last row
        area.addView(list, FrameLayout.LayoutParams(-1, -2))
        val focus = TileFocus(a, area, ::dp)
        val scroll = ScrollView(a).apply { isVerticalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(area) }
        val search = TvUi.field(a, a.getString(R.string.pk_search), 16f, EditorInfo.IME_ACTION_SEARCH, TvUi.FIELD_REST, TvUi.R_BUTTON, 18, 10, R.drawable.ic_search)
        val head = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(34), dp(26), dp(34), dp(10))
            addView(Font.on(TextView(a)).apply { text = title; textSize = 26f; setTextColor(Color.WHITE); typeface = Font.bold; maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(search, LinearLayout.LayoutParams(dp(300), dp(46)))
        }
        val note = Font.on(TextView(a)).apply { textSize = 13f; setTextColor(PK_DIM); setPadding(dp(34), 0, dp(34), dp(4)); text = a.getString(if (mode == Mode.GRID) R.string.pk_grid_hint else R.string.pk_target_hint) }
        val panel = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; background = TvGlass.panelBg(a)
            addView(head); addView(note); addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        dialog.setContentView(FrameLayout(a).apply { addView(panel, FrameLayout.LayoutParams(panelW, dm.heightPixels - dp(64), Gravity.CENTER)) })   // centred: a bare content view sat at the left
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(PK_SCRIM))
            setLayout(-1, -1); setGravity(Gravity.CENTER); setWindowAnimations(0)
        }

        val items = ArrayList<Item>()
        val tiles = HashMap<String, PickTile>()
        val sections = listOf(R.string.pk_apps, R.string.pk_pins, R.string.pk_tiles)

        fun on(k: String): Boolean {
            return when {
                mode == Mode.TARGET -> k == now
                Target.kind(k) == Target.Kind.APP -> k !in Store.list(a, "hidden")
                else -> k in Store.list(a, "tiles")
            }
        }
        fun press(it: Item) {
            if (mode == Mode.TARGET) { dialog.dismiss(); onPick(it.key); return }
            if (Target.kind(it.key) == Target.Kind.APP) {          // an app: off the grid (hidden) or back
                val hidden = Store.list(a, "hidden")
                if (it.key in hidden) Store.change(a, JSONObject().put("hidden", JSONArray(hidden - it.key)))
                else Store.change(a, JSONObject().put("hidden", JSONArray(hidden + it.key)).put("dock", JSONArray(Store.list(a, "dock") - it.key)))
            } else {
                val t = Store.list(a, "tiles")
                if (it.key in t) Store.change(a, JSONObject().put("tiles", JSONArray(t - it.key)).put("dock", JSONArray(Store.list(a, "dock") - it.key)).put("order", JSONArray(Store.list(a, "order") - it.key)))
                else if (t.size < HomeConfig.TILES_MAX) Store.change(a, JSONObject().put("tiles", JSONArray(t + it.key)))
            }
            tiles[it.key]?.mark(on(it.key))
        }

        fun tileFor(it: Item): PickTile = tiles.getOrPut(it.key) {
            PickTile(a, tileW, ::dp, it.key, it.label, it.app, it.key, it.tag, it.icon, plus = it.key.isEmpty()).also { t ->
                t.setOnClickListener { _ -> press(it) }
                focus.wire(t)
                t.mark(it.key.isNotEmpty() && on(it.key))
            }
        }

        fun heading(t: String) = Font.on(TextView(a)).apply { text = t.uppercase(); textSize = 12f; setTextColor(PK_DIM); letterSpacing = 0.12f; setPadding(0, dp(18), 0, dp(14)) }
        fun redraw() {
            val q = fold(search.text.toString().trim())
            list.removeAllViews(); focus.hide()
            for (s in sections.indices) {
                val here = items.filter { it.section == s && (q.isEmpty() || fold(it.label).contains(q) || fold(it.tag).contains(q)) }
                val empty = when {
                    s == 1 && !Pins.can(a) -> a.getString(R.string.pk_pins_need_home)
                    s == 2 && Tiles.available(a).isEmpty() -> a.getString(if (Tiles.status(a) == Tiles.UNTRUSTED) R.string.power_untrusted else R.string.c_tiles_none)
                    else -> ""
                }
                if (here.isEmpty() && (q.isNotEmpty() || empty.isEmpty())) continue
                list.addView(heading(a.getString(sections[s])))
                if (here.isEmpty()) { list.addView(Font.on(TextView(a)).apply { text = empty; textSize = 14f; setTextColor(PK_DIM) }); continue }
                var row: LinearLayout? = null
                here.forEachIndexed { i, it ->
                    if (i % cols == 0) { row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
                        list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = gap }) }
                    val t = tileFor(it)
                    (t.parent as? ViewGroup)?.removeView(t)
                    row!!.addView(t, LinearLayout.LayoutParams(-2, -2).apply { if (i % cols > 0) leftMargin = gap })
                }
            }
            if (list.childCount == 0) list.addView(Font.on(TextView(a)).apply { text = a.getString(R.string.pk_none_found); textSize = 15f; setTextColor(PK_DIM); setPadding(0, dp(24), 0, 0) })
        }
        search.done = { redraw() }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { redraw() }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        search.after = { (0 until list.childCount).firstNotNullOfOrNull { (list.getChildAt(it) as? LinearLayout)?.getChildAt(0) as? PickTile } }   // Done / Enter: onto the first tile left

        // what there is, read off the main thread: every app's picture, then each app's shortcuts (a binder call per app)
        bg.execute {
            val apps = Apps.list(a).onEach { it.load(a) }
            val hidden = Store.list(a, "hidden").toSet()
            val got = ArrayList<Item>()
            if (mode == Mode.TARGET) got.add(Item("", a.getString(R.string.pk_none), null, "", 0))
            for (p in apps) got.add(Item(p.pkg, p.label, p, if (p.pkg in hidden && mode == Mode.GRID) a.getString(R.string.pk_hidden) else "", 0))
            Pins.refresh(a)
            val avail = try { JSONArray(a.getSharedPreferences("pins", Context.MODE_PRIVATE).getString("avail", "[]")) } catch (e: Exception) { JSONArray() }
            val names = apps.associate { it.pkg to it.label }
            for (i in 0 until avail.length()) {
                val o = avail.getJSONObject(i)
                val app = Pins.app(a, o.getString("key")) ?: continue
                got.add(Item(o.getString("key"), o.getString("title"), app, names[o.getString("app")] ?: "", 1))
            }
            for (t in Tiles.available(a)) got.add(Item(t.key, t.title, null, a.getString(R.string.pk_live), 2, R.drawable.ic_gauge))
            a.runOnUiThread {
                if (!dialog.isShowing) return@runOnUiThread
                items.addAll(got); redraw()
                val inOrder = (0 until list.childCount).mapNotNull { (list.getChildAt(it) as? LinearLayout)?.getChildAt(0) as? PickTile }.firstOrNull { it.isFocusable }   // the first on the screen, not a map's first
                val first = (if (mode == Mode.TARGET) tiles[now]?.takeIf { it.isFocusable && it.parent != null } else null) ?: inOrder
                (first ?: search).requestFocus()
            }
        }
        dialog.setOnDismissListener { focus.hide() }
        dialog.show()
        return dialog
    }
}
