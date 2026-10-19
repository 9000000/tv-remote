package dev.tvremote.home

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The home screen: the clock, three buttons (inputs, network, settings), the favourites ("dock") and every other app below.
 * Plain views, built in code: it has to come up at once on the weakest TV chip. What it shows is set in [Store], from here
 * (hold OK on an app) or from the phone / computer (HomeReceiver).
 */
class HomeActivity : Activity() {
    private val bg = Executors.newSingleThreadExecutor()
    private lateinit var wallView: ImageView
    private lateinit var scrim: View
    private lateinit var scroll: ScrollView
    private lateinit var clock: TextClock
    private lateinit var dateLine: TextView
    private lateinit var netPill: Pill
    private lateinit var dock: LinearLayout
    private lateinit var dockEmpty: TextView
    private lateinit var appsTitle: TextView
    private lateinit var grid: LinearLayout
    private var apps: List<App> = emptyList()
    private var conf = JSONObject()
    private var wallShown = ""

    private fun dp(v: Number) = (v.toFloat() * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        conf = Store.get(this)
        val root = FrameLayout(this)
        wallView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
        scrim = View(this).apply {                                    // words stay readable over any photo
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xB0000000.toInt(), 0x40000000, 0x90000000.toInt()))
            visibility = View.GONE
        }
        root.addView(wallView, FrameLayout.LayoutParams(-1, -1))
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))

        scroll = Scroller(this).apply { isVerticalScrollBarEnabled = false; isFillViewport = true; clipChildren = false }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            setPadding(dp(48), dp(34), dp(48), dp(48))
        }
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2))
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        // the clock and the day on the left, the three buttons on the right
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clock = TextClock(this).apply {
            textSize = 60f; setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            includeFontPadding = false; shadow(this)
        }
        dateLine = text(18f, 0xD9FFFFFF.toInt()).apply { setPadding(dp(2), dp(4), 0, 0) }
        left.addView(clock); left.addView(dateLine)
        head.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        val pills = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        pills.addView(Pill(this, R.drawable.ic_input, getString(R.string.inputs)) { inputs() })
        netPill = Pill(this, R.drawable.ic_wifi, getString(R.string.wifi)) { open(Settings.ACTION_WIFI_SETTINGS) }
        pills.addView(netPill, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
        pills.addView(Pill(this, R.drawable.ic_settings, getString(R.string.settings)) { open(Settings.ACTION_SETTINGS) },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
        for (i in 0 until pills.childCount) pills.getChildAt(i).onFocusChangeListener = View.OnFocusChangeListener { _, f -> if (f) scroll.smoothScrollTo(0, 0) }
        head.addView(pills)
        col.addView(head)

        // the favourites, in a band of their own
        val band = FrameLayout(this).apply {
            background = GradientDrawable().apply { cornerRadius = dp(26).toFloat(); setColor(0x1FFFFFFF) }
            setPadding(dp(22), dp(20), dp(22), dp(6)); clipChildren = false; clipToPadding = false
        }
        dock = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        dockEmpty = text(17f, 0xB3FFFFFF.toInt()).apply { text = getString(R.string.dock_empty); gravity = Gravity.CENTER; setPadding(0, dp(24), 0, dp(36)) }
        band.addView(dock, FrameLayout.LayoutParams(-1, -2))
        band.addView(dockEmpty, FrameLayout.LayoutParams(-1, -2))
        col.addView(band, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(40) })

        appsTitle = text(18f, 0xCCFFFFFF.toInt()).apply { text = getString(R.string.apps); setPadding(dp(4), 0, 0, 0) }
        col.addView(appsTitle, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(34); bottomMargin = dp(14) })
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        col.addView(grid)

        setContentView(root)
        Store.onChange = { conf = Store.get(this); show() }
        val pkgs = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(changed, pkgs)
        registerReceiver(tick, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
            @Suppress("DEPRECATION") addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        })
        loadApps()
    }

    override fun onDestroy() {
        Store.onChange = null
        try { unregisterReceiver(changed); unregisterReceiver(tick) } catch (e: Exception) { }
        bg.shutdownNow()
        super.onDestroy()
    }

    override fun onResume() { super.onResume(); clockAndDay() }

    /** Home pressed while already home, or Back: to the top, on the first favourite. */
    override fun onNewIntent(i: Intent) { super.onNewIntent(i); toTop() }

    @Deprecated("Back on a home screen goes nowhere")
    override fun onBackPressed() { toTop() }

    private fun toTop() {
        scroll.smoothScrollTo(0, 0)
        (if (dock.childCount > 0) dock.getChildAt(0) else grid.findFocus() ?: firstGridTile())?.requestFocus()
    }

    private fun firstGridTile(): View? = (grid.getChildAt(0) as? ViewGroup)?.getChildAt(0)

    private val changed = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { loadApps() } }
    private val tick = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { clockAndDay() } }

    private fun loadApps() {
        bg.execute {
            val list = Apps.list(this)
            list.forEach { it.load(this) }                            // the pictures off the main thread: dozens of apps on a slow chip
            runOnUiThread { if (!isDestroyed) { apps = list; show() } }
        }
    }

    // ---- what is shown ----

    private fun show() {
        val keep = (currentFocus as? Tile)?.app?.pkg
        wall()
        clockAndDay()
        val byPkg = apps.associateBy { it.pkg }
        val dockPkgs = Store.list(this, "dock").filter { it in byPkg }
        val hidden = Store.list(this, "hidden").toSet()

        val width = resources.displayMetrics.widthPixels - dp(96)
        dock.removeAllViews()
        val n = dockPkgs.size
        if (n > 0) {
            val w = minOf(dp(176), (width - dp(44) - (n - 1) * dp(22)) / n)
            dockPkgs.forEachIndexed { i, p ->
                val t = tile(byPkg.getValue(p), w, true)
                dock.addView(t, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) leftMargin = dp(22) })
            }
        }
        dock.visibility = if (n > 0) View.VISIBLE else View.GONE
        dockEmpty.visibility = if (n > 0) View.GONE else View.VISIBLE

        grid.removeAllViews()
        val rest = apps.filter { it.pkg !in dockPkgs && it.pkg !in hidden }
        val cols = 6
        val gap = dp(16)
        val w = (width - (cols - 1) * gap) / cols
        val cells: MutableList<View> = rest.map { tile(it, w, false) }.toMutableList()
        val hiddenHere = apps.count { it.pkg in hidden }
        if (hiddenHere > 0) cells.add(Tile(this, null, w, getString(R.string.hidden, hiddenHere), ::dp).apply { setOnClickListener { unhide() } })
        var row: LinearLayout? = null
        cells.forEachIndexed { i, v ->
            if (i % cols == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
                grid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(8) })
            }
            row!!.addView(v, LinearLayout.LayoutParams(-2, -2).apply { if (i % cols > 0) leftMargin = gap })
        }
        appsTitle.visibility = if (cells.isEmpty()) View.GONE else View.VISIBLE

        val again = keep?.let { p -> tiles().firstOrNull { it.app?.pkg == p } }
        if (again != null) again.requestFocus() else if (currentFocus == null || currentFocus !is Tile && currentFocus !is Pill) toTop()
    }

    private fun tiles(): List<Tile> {
        val out = ArrayList<Tile>()
        for (i in 0 until dock.childCount) (dock.getChildAt(i) as? Tile)?.let { out.add(it) }
        for (r in 0 until grid.childCount) (grid.getChildAt(r) as ViewGroup).let { row -> for (i in 0 until row.childCount) (row.getChildAt(i) as? Tile)?.let { out.add(it) } }
        return out
    }

    private fun tile(a: App, w: Int, inDock: Boolean) = Tile(this, a, w, a.label, ::dp).apply {
        setOnClickListener { if (!Apps.launch(this@HomeActivity, a.pkg)) toast(R.string.no_open) }
        setOnLongClickListener { menu(a); true }
        setOnKeyListener { _, code, e -> if (code == KeyEvent.KEYCODE_MENU && e.action == KeyEvent.ACTION_UP) { menu(a); true } else false }
        onFocusChangeListener = View.OnFocusChangeListener { v, f ->
            (v as Tile).focus(f)
            if (f) {
                if (inDock) scroll.smoothScrollTo(0, 0)
                else {                                                // a row that is cut off comes to the upper third; one in full view stays put
                    var y = 0; var p: View? = v
                    while (p != null && p !== scroll) { y += p.top; p = p.parent as? View }
                    val margin = dp(40)
                    if (y - margin < scroll.scrollY || y + v.height + margin > scroll.scrollY + scroll.height)
                        scroll.smoothScrollTo(0, maxOf(0, y - scroll.height / 3))
                }
            }
        }
    }

    private fun wall() {
        val wall = conf.optString("wall")
        val key = wall + conf.optLong("photo")
        if (key == wallShown) return
        wallShown = key
        val colors = when (wall) {
            "ocean" -> intArrayOf(0xFF03192B.toInt(), 0xFF0A4A66.toInt(), 0xFF05233A.toInt())
            "dusk" -> intArrayOf(0xFF1A0F2E.toInt(), 0xFF4A2150.toInt(), 0xFF7A3B3B.toInt())
            "forest" -> intArrayOf(0xFF06140F.toInt(), 0xFF133B2D.toInt(), 0xFF0A1F18.toInt())
            "aurora" -> intArrayOf(0xFF071A1F.toInt(), 0xFF0F4C4A.toInt(), 0xFF2B2A6B.toInt())
            "black" -> intArrayOf(Color.BLACK, Color.BLACK)
            else -> intArrayOf(0xFF0B0F1A.toInt(), 0xFF18223F.toInt(), 0xFF0B0F1A.toInt())
        }
        window.decorView.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
        wallView.visibility = View.GONE; scrim.visibility = View.GONE
        if (wall != "photo") return
        val f = java.io.File(getExternalFilesDir(null), "wall.jpg")
        val dm = resources.displayMetrics
        bg.execute {
            val bmp = try {                                           // read at about the screen's size: a phone photo is 4000 px wide
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, o)
                var s = 1
                while (o.outWidth / (s * 2) >= dm.widthPixels && o.outHeight / (s * 2) >= dm.heightPixels) s *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
            } catch (e: Throwable) { null }
            runOnUiThread {
                if (bmp != null && !isDestroyed) { wallView.setImageBitmap(bmp); wallView.visibility = View.VISIBLE; scrim.visibility = View.VISIBLE }
            }
        }
    }

    private fun clockAndDay() {
        val h24 = conf.optBoolean("clock24", true)
        clock.format24Hour = if (h24) "HH:mm" else "h:mm"
        clock.format12Hour = clock.format24Hour
        val now = Calendar.getInstance()
        val day = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), Date()).toString()
            .replaceFirstChar { it.uppercase() }
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val greet = getString(if (hour in 4..11) R.string.morning else if (hour in 12..17) R.string.afternoon else R.string.evening)
        dateLine.text = if (conf.optBoolean("greet", true)) "$greet  ·  $day" else day
        @Suppress("DEPRECATION") val net = (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo
        val (label, off) = @Suppress("DEPRECATION") when {
            net == null || !net.isConnected -> getString(R.string.offline) to true
            net.type == ConnectivityManager.TYPE_ETHERNET -> getString(R.string.cable) to false
            else -> getString(R.string.wifi) to false
        }
        netPill.set(label, if (off) 0.5f else 1f, if (net?.type == ConnectivityManager.TYPE_ETHERNET) R.drawable.ic_globe else R.drawable.ic_wifi)
    }

    // ---- what the buttons and the menu do ----

    private fun menu(a: App) {
        val dockList = Store.list(this, "dock")
        val at = dockList.indexOf(a.pkg)
        val acts = ArrayList<Pair<Int, () -> Unit>>()
        if (at >= 0) {
            if (at > 0) acts.add(R.string.left to { dockList.add(at - 1, dockList.removeAt(at)); Store.putList(this, "dock", dockList) })
            if (at < dockList.size - 1) acts.add(R.string.right to { dockList.add(at + 1, dockList.removeAt(at)); Store.putList(this, "dock", dockList) })
            acts.add(R.string.unpin to { dockList.removeAt(at); Store.putList(this, "dock", dockList) })
        } else {
            acts.add(R.string.pin to {
                if (dockList.size >= HomeConfig.DOCK_MAX) Toast.makeText(this, getString(R.string.dock_full, HomeConfig.DOCK_MAX), Toast.LENGTH_LONG).show()
                else { dockList.add(a.pkg); Store.putList(this, "dock", dockList) }
            })
            acts.add(R.string.hide to { Store.putList(this, "hidden", Store.list(this, "hidden") + a.pkg) })
        }
        acts.add(R.string.info to {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))) } catch (e: Exception) { toast(R.string.no_open) }
        })
        AlertDialog.Builder(this, R.style.Menu).setTitle(a.label)
            .setItems(acts.map { getString(it.first) }.toTypedArray()) { _, i -> acts[i].second() }.show()
    }

    private fun unhide() {
        val hidden = Store.list(this, "hidden")
        val here = apps.filter { it.pkg in hidden }
        AlertDialog.Builder(this, R.style.Menu).setTitle(getString(R.string.show))
            .setItems(here.map { it.label }.toTypedArray()) { _, i -> Store.putList(this, "hidden", hidden - here[i].pkg) }.show()
    }

    /** The TV's inputs (HDMI and the tuner) as the TV knows them; its own input settings when it lists none. */
    private fun inputs() {
        val tim = try { getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager } catch (e: Exception) { null }
        val list: List<TvInputInfo> = try { tim?.tvInputList.orEmpty() } catch (e: Exception) { emptyList() }
        if (list.isEmpty()) { toast(R.string.no_inputs); open(Settings.ACTION_SETTINGS); return }
        AlertDialog.Builder(this, R.style.Menu).setTitle(getString(R.string.inputs))
            .setItems(list.map { it.loadLabel(this).toString() }.toTypedArray()) { _, i ->
                val info = list[i]
                val uri = if (info.isPassthroughInput) TvContract.buildChannelUriForPassthroughInput(info.id) else TvContract.buildChannelsUriForInput(info.id)
                try { startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { toast(R.string.no_open) }
            }.show()
    }

    private fun open(action: String) {
        try { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e2: Exception) { toast(R.string.no_open) } }
    }

    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_SHORT).show()

    private fun text(size: Float, color: Int) = TextView(this).apply { textSize = size; setTextColor(color); shadow(this) }

    private fun shadow(t: TextView) = t.setShadowLayer(dp(6).toFloat(), 0f, dp(1).toFloat(), 0x80000000.toInt())

    /** Never scrolls by itself to the focused view: [tile] and the buttons say where to, so a row is not pressed to the edge. */
    private class Scroller(c: Context) : ScrollView(c) {
        override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?) = 0
    }

    /** A round-ended button with an icon and a word; white when focused. */
    private class Pill(c: Activity, icon: Int, label: String, onClick: () -> Unit) : LinearLayout(c) {
        private val word: TextView
        init {
            val d = c.resources.displayMetrics.density
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isClickable = true
            setPadding((16 * d).toInt(), 0, (20 * d).toInt(), 0)
            minimumHeight = (44 * d).toInt()
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { cornerRadius = 22 * d; setColor(Color.WHITE) })
                addState(intArrayOf(), GradientDrawable().apply { cornerRadius = 22 * d; setColor(0x29FFFFFF) })
            }
            val tint = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(0xFF111318.toInt(), Color.WHITE))
            addView(ImageView(c).apply { setImageResource(icon); imageTintList = tint; isDuplicateParentStateEnabled = true }, LayoutParams((20 * d).toInt(), (20 * d).toInt()))
            word = TextView(c).apply { text = label; textSize = 15f; setTextColor(tint); isDuplicateParentStateEnabled = true; setPadding((8 * d).toInt(), 0, 0, 0) }
            addView(word)
            setOnClickListener { onClick() }
        }
        fun set(label: String, a: Float, icon: Int) { word.text = label; (getChildAt(0) as ImageView).apply { setImageResource(icon); alpha = a } }
    }
}

/**
 * One app: its banner (or icon on a colour, with the name), the name under it shown while focused. Focus grows it and rings it.
 * [app] null: the "hidden apps" tile.
 */
class Tile(c: Context, val app: App?, w: Int, label: String, dp: (Number) -> Int) : LinearLayout(c) {
    private val card = FrameLayout(c)
    private val ring = View(c)
    private val name = TextView(c)

    init {
        orientation = VERTICAL; isFocusable = true; isClickable = true; isLongClickable = true
        clipChildren = false; clipToPadding = false
        val h = w * 9 / 16
        val r = dp(10).toFloat()
        val banner = app?.banner
        card.background = GradientDrawable().apply { cornerRadius = r; setColor(if (banner != null) 0xFF1E2230.toInt() else shade(app?.pkg ?: "")) }
        card.clipToOutline = true
        if (banner != null) {
            card.addView(ImageView(c).apply { setImageDrawable(banner); scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1))
        } else {
            val inner = LinearLayout(c).apply { orientation = VERTICAL; gravity = Gravity.CENTER; setPadding(dp(6), dp(4), dp(6), dp(4)) }
            inner.addView(ImageView(c).apply {
                if (app?.icon != null) setImageDrawable(app.icon) else { setImageResource(R.drawable.ic_eye_off); imageTintList = ColorStateList.valueOf(Color.WHITE) }
            }, LayoutParams(h * 45 / 100, h * 45 / 100))
            inner.addView(TextView(c).apply {
                text = label; textSize = 12f; setTextColor(Color.WHITE); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            }, LayoutParams(-1, -2))
            card.addView(inner, FrameLayout.LayoutParams(-1, -1))
        }
        ring.background = GradientDrawable().apply { cornerRadius = r; setStroke(dp(3), Color.WHITE); setColor(Color.TRANSPARENT) }
        ring.alpha = 0f
        card.addView(ring, FrameLayout.LayoutParams(-1, -1))
        addView(card, LayoutParams(w, h))
        name.apply {
            text = label; textSize = 13f; setTextColor(Color.WHITE); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
            alpha = 0f; setPadding(0, dp(9), 0, dp(4)); setShadowLayer(dp(4).toFloat(), 0f, 0f, 0x99000000.toInt())
        }
        addView(name, LayoutParams(w, -2))
        background = ColorDrawable(Color.TRANSPARENT)
    }

    fun focus(on: Boolean) {
        val s = if (on) 1.1f else 1f
        card.animate().scaleX(s).scaleY(s).translationZ(if (on) 12f else 0f).setDuration(140).start()
        ring.animate().alpha(if (on) 1f else 0f).setDuration(140).start()
        name.animate().alpha(if (on) 1f else 0f).translationY(if (on) 2f else 0f).setDuration(140).start()
    }

    /** A dark colour of its own per app, for those without a TV banner. */
    private fun shade(pkg: String): Int {
        val hues = intArrayOf(0xFF27324A.toInt(), 0xFF2E3B2F.toInt(), 0xFF43293A.toInt(), 0xFF2B3A45.toInt(), 0xFF3D3424.toInt(), 0xFF2F2D4A.toInt())
        return hues[(pkg.hashCode() and 0x7fffffff) % hues.size]
    }
}
