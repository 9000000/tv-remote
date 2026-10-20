package dev.tvremote.home

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.view.animation.PathInterpolator
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
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
    private lateinit var head: LinearLayout
    private lateinit var bandWrap: FrameLayout
    private lateinit var blurClip: FrameLayout
    private lateinit var blurView: ImageView
    private var scrollAnim: ObjectAnimator? = null
    private var gridOn = true                                         // focus is in the grid: it is lit and in place; else it waits a little lower, dimmer
    private var away = false                                        // really left (stopped), not just paused: only then does the screen come in again
    private var trimmed = false                                     // the pictures were let go for memory while away ([trim])
    private var shownKey = ""                                       // what the tiles were built from: the same again builds nothing
    private var tileCache = HashMap<String, Tile>()                 // a tile is made once per app and size, then moved
    private var picture: Bitmap? = null                             // the photo wallpaper and its blurred copy; let go in [trim], the small one stays as a stand-in
    private var soft: Bitmap? = null
    private var badges = Badges.NONE
    private var wantCol = -1                                        // the grid column Up and Down try to keep ([Nav])
    private var stepping = false
    private var open: android.app.Dialog? = null                    // the panel or menu on top, closed by a Home press
    private lateinit var widgets: Widgets
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private val widgetTick = object : Runnable { override fun run() { widgetsRead(); ui.postDelayed(this, 60_000) } }
    private val netTick = object : Runnable { override fun run() { widgets.sample(); ui.postDelayed(this, Widgets.SAMPLE_MS) } }

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

        Motion.read(this, conf)
        scroll = Scroller(this) { blurAlign() }.apply { isVerticalScrollBarEnabled = false; isFillViewport = true; clipChildren = false }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            setPadding(dp(48), dp(34), dp(48), dp(48))
        }
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2))
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        // the clock and the day on the left, the three buttons on the right
        head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP; clipChildren = false; clipToPadding = false }  // a focused button grows past its row
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        clock = TextClock(this).apply {
            textSize = 60f; setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            includeFontPadding = false; shadow(this)
        }
        dateLine = text(18f, 0xD9FFFFFF.toInt()).apply { setPadding(dp(2), dp(4), 0, 0) }
        left.addView(clock); left.addView(dateLine)
        head.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        val pills = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0); clipChildren = false; clipToPadding = false }
        pills.addView(Pill(this, R.drawable.ic_input, getString(R.string.inputs)) { inputs() })
        netPill = Pill(this, R.drawable.ic_wifi, getString(R.string.wifi)) { open(Settings.ACTION_WIFI_SETTINGS) }
        pills.addView(netPill, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
        pills.addView(Pill(this, R.drawable.ic_settings, getString(R.string.settings)) { open(Settings.ACTION_SETTINGS) },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
        pills.addView(Pill(this, R.drawable.ic_sliders, getString(R.string.customize), iconOnly = true) { customize() },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
        for (i in 0 until pills.childCount) pills.getChildAt(i).onFocusChangeListener = View.OnFocusChangeListener { v, f ->
            Motion.pop(v, f); if (f) { scrollToY(0); gridFocus(false) }
        }
        // a narrow screen (dp): the words go and only the icons stay, so the clock keeps its line
        if (resources.displayMetrics.widthPixels < dp(820)) for (i in 0 until pills.childCount) (pills.getChildAt(i) as Pill).compact()
        head.addView(pills)
        col.addView(head)
        widgets = Widgets(this, ::dp).apply { visibility = View.GONE; onWeather = { runOnUiThread { if (!isDestroyed) widgetsRead() } } }
        col.addView(widgets, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })

        // the favourites, in a band of their own
        // frosted: a light fill with a hairline edge; over a photo the same photo, blurred, sits behind it (see [blurAlign])
        val band = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x2EFFFFFF, 0x14FFFFFF)).apply {
                cornerRadius = dp(26).toFloat(); setStroke(dp(1), 0x33FFFFFF)
            }
            setPadding(dp(22), dp(20), dp(22), dp(6)); clipChildren = false; clipToPadding = false
        }
        blurView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setColorFilter(0x70000000, PorterDuff.Mode.SRC_ATOP) }
        blurClip = FrameLayout(this).apply {
            val r = dp(26).toFloat()
            outlineProvider = object : android.view.ViewOutlineProvider() {            // a fully transparent GradientDrawable gives no outline to clip by
                override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, r) }
            }
            clipToOutline = true; visibility = View.GONE
            addView(blurView, FrameLayout.LayoutParams(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels))
        }
        bandWrap = Wrap(this).apply {
            clipChildren = false; clipToPadding = false
            addView(blurClip, FrameLayout.LayoutParams(-1, -1)); addView(band, FrameLayout.LayoutParams(-1, -2))
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> blurAlign() }
        }
        dock = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }
        dockEmpty = text(17f, 0xB3FFFFFF.toInt()).apply { text = getString(R.string.dock_empty); gravity = Gravity.CENTER; setPadding(0, dp(24), 0, dp(36)) }
        band.addView(dock, FrameLayout.LayoutParams(-1, -2))
        band.addView(dockEmpty, FrameLayout.LayoutParams(-1, -2))
        col.addView(bandWrap, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(40) })

        appsTitle = text(18f, 0xCCFFFFFF.toInt()).apply { text = getString(R.string.apps); setPadding(dp(4), 0, 0, 0) }
        col.addView(appsTitle, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(34); bottomMargin = dp(14) })
        grid = Flat(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
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
        if (Motion.on) enter(true)
    }

    override fun onDestroy() {
        Store.onChange = null
        ui.removeCallbacks(widgetTick); ui.removeCallbacks(netTick)
        try { unregisterReceiver(changed); unregisterReceiver(tick) } catch (e: Exception) { }
        bg.shutdownNow()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        Motion.read(this, conf)                                       // the system's "remove animations" may have changed while away
        clockAndDay()
        if (away && Motion.on) enter(false)                           // Home pressed on the home screen pauses and resumes it without stopping: nothing may flicker then
        away = false
        badgesRead()
        ui.removeCallbacks(widgetTick); ui.post(widgetTick)
        ui.removeCallbacks(netTick); ui.postDelayed(netTick, Widgets.SAMPLE_MS)
    }

    override fun onStart() {
        super.onStart()
        if (trimmed) untrim()
    }

    override fun onStop() { away = true; super.onStop() }

    /** Memory is short and the screen is hidden: the wallpaper's big bitmap and every banner go, the next [onStart] brings them back. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && away) trim()
    }

    private fun trim() {
        if (trimmed) return
        trimmed = true
        picture = null
        if (soft != null && wallView.drawable != null) wallView.setImageBitmap(soft)    // the blurred copy stands in, so the return is not a blank
        else wallView.setImageDrawable(null)
        for (t in tileCache.values) t.release()
        for (a in apps) a.release()
    }

    private fun untrim() {
        trimmed = false
        val dm = resources.displayMetrics
        if (conf.optString("wall") == "photo") loadPhoto()
        for (t in tiles().filter { dock.indexOfChild(it) >= 0 }) { t.app?.load(this); t.restore() }   // the favourites at once, the rest from the side
        bg.execute {
            for (a in apps) if (a.banner == null && a.icon == null) a.load(this)
            runOnUiThread { if (!isDestroyed && !trimmed) for (t in tileCache.values) t.restore() }
        }
    }

    override fun onPause() { ui.removeCallbacks(widgetTick); ui.removeCallbacks(netTick); super.onPause() }

    private var cpuAgain = false

    /** The cards' numbers, read off the main thread; none at all while they are off. */
    private fun widgetsRead() {
        val want = Store.list(this, "widgets")
        val conf = Store.get(this); val note = conf.optString("note"); val layout = conf.optString("wlayout", "row")
        val none = want.isEmpty() && note.isEmpty()
        (bandWrap.layoutParams as LinearLayout.LayoutParams).topMargin = dp(if (none) 40 else 24)
        if (none) { widgets.visibility = View.GONE; return }
        bg.execute {
            val f = widgets.read(want)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                widgets.show(want, f, note, { p -> apps.firstOrNull { it.pkg == p }?.label ?: p.substringAfterLast('.') }, layout)
                // TV Setup answers the CPU with its last measure and starts the next: the first time there is none, so ask again soon, once
                if ("system" in want && f.cpu == null && !cpuAgain) { cpuAgain = true; ui.postDelayed({ if (!isDestroyed) widgetsRead() }, 2500) }
            }
        }
    }

    /** The screen comes in: the clock and the buttons ease down, the dock band rises (only a fade when coming back from an app). */
    private fun enter(slide: Boolean) {
        val d = if (slide) dp(14).toFloat() else 0f
        head.alpha = 0f; head.translationY = -d
        bandWrap.alpha = 0f; bandWrap.translationY = d
        head.animate().alpha(1f).translationY(0f).setDuration(if (slide) 420 else 240).setInterpolator(Motion.glide).withLayer().start()
        bandWrap.animate().alpha(1f).translationY(0f).setStartDelay(if (slide) 70 else 0).setDuration(if (slide) 420 else 240).setInterpolator(Motion.glide).withLayer().start()
    }

    /** Scrolls to [y] gliding (one animation at a time, the last target wins); at once when motion is off. */
    private fun scrollToY(y: Int) {
        scrollAnim?.cancel()
        if (!Motion.on) { scroll.scrollTo(0, y); return }
        if (scroll.scrollY == y) return
        scrollAnim = ObjectAnimator.ofInt(scroll, "scrollY", scroll.scrollY, y).apply { duration = 420; interpolator = Motion.glide; start() }
    }

    /** The grid is lit and in place while focus is in it; from the dock or the buttons it waits a little lower and dimmer, then rises as focus comes down. */
    private fun gridFocus(on: Boolean) {
        if (on == gridOn) return
        gridOn = on
        val a = if (on) 1f else 0.6f
        val ty = if (on) 0f else dp(12).toFloat()
        for (v in arrayOf(appsTitle, grid)) {
            v.animate().cancel()
            if (!Motion.on) { v.alpha = a; v.translationY = ty }
            else v.animate().alpha(a).translationY(ty).setDuration(if (on) 280 else 200).setInterpolator(Motion.glide).start()
        }
    }

    /** The blurred photo behind the dock band is the wallpaper itself: it keeps the screen's place while the band scrolls. */
    private fun blurAlign() {
        if (blurClip.visibility != View.VISIBLE) return
        blurView.translationX = -bandWrap.left.toFloat()
        blurView.translationY = (scroll.scrollY - bandWrap.top).toFloat()
    }

    /** Home pressed while already home, or Back: to the top, on the first favourite. */
    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        if (open?.isShowing == true) { open?.dismiss(); open = null; return }                          // a panel or menu is open: this press closes it
        toTop()
    }

    @Deprecated("Back on a home screen goes nowhere")
    override fun onBackPressed() { toTop() }

    private fun toTop() {
        val first = if (dock.childCount > 0) dock.getChildAt(0) else grid.findFocus() ?: firstGridTile()
        if (first != null && first.isFocused && scroll.scrollY == 0) return    // already there: nothing to move, nothing to redraw
        scrollToY(0)
        first?.requestFocus()
    }

    /** The marks on the tiles: asked of TV Setup off the main thread, on coming back and then every minute; the answer is applied only where it differs. */
    private fun badgesRead() {
        bg.execute {
            val info = Badges.read(this) ?: Badges.NONE
            runOnUiThread { if (!isDestroyed && !(info.bed == badges.bed && info.by == badges.by)) { badges = info; badgeTiles() } }
        }
    }

    private fun badgeTiles() {
        val m = getString(R.string.b_m); val h = getString(R.string.b_h)
        for (t in tileCache.values) t.app?.let { a -> t.mark(badges.of(a.pkg)) { Badges.label(it, m, h) } }
    }

    private fun firstGridTile(): View? = (grid.getChildAt(0) as? ViewGroup)?.getChildAt(0)

    private val changed = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val pkg = i.data?.schemeSpecificPart
            if (pkg == null) { loadApps(); return }
            if (i.action == Intent.ACTION_PACKAGE_REMOVED && i.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return   // the new version's ADDED follows
            packageChanged(pkg, i.action == Intent.ACTION_PACKAGE_REMOVED)
        }
    }
    private val tick = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { clockAndDay() } }

    private fun loadApps() {
        bg.execute {
            val list = Apps.list(this)
            list.forEach { it.load(this) }                            // the pictures off the main thread: dozens of apps on a slow chip
            runOnUiThread { if (!isDestroyed) { apps = list; shownKey = ""; show() } }
        }
    }

    /** One app came, went or changed: only its picture is read again, the other tiles are kept and moved. */
    private fun packageChanged(pkg: String, removed: Boolean) {
        if (pkg == packageName) return
        bg.execute {
            val one = if (removed) null else Apps.one(this, pkg)?.also { it.load(this) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val was = apps.firstOrNull { it.pkg == pkg }
                if (one == null && was == null) return@runOnUiThread    // not an app this screen lists (a service, a library)
                apps = (apps.filter { it.pkg != pkg } + listOfNotNull(one)).sortedBy { it.label.lowercase() }
                shownKey = ""; show()
            }
        }
    }

    // ---- what is shown ----

    private fun show() {
        val keep = (currentFocus as? Tile)?.app?.pkg
        Motion.read(this, conf)
        wall()
        clockAndDay()
        widgetsRead()
        val byPkg = apps.associateBy { it.pkg }
        val dockPkgs = Store.list(this, "dock").filter { it in byPkg }
        val hidden = Store.list(this, "hidden").toSet()
        val key = dockPkgs.joinToString(",") + "|" + apps.filter { it.pkg !in dockPkgs && it.pkg !in hidden }.joinToString(",") { it.pkg } + "|" + apps.count { it.pkg in hidden }
        if (key == shownKey) return                                   // nothing that decides the tiles changed: not one view is rebuilt
        shownKey = key
        val made = HashMap<String, Tile>()                            // the tiles kept for the next build: those of apps still here
        val width = resources.displayMetrics.widthPixels - dp(96)
        dock.removeAllViews()
        val n = dockPkgs.size
        if (n > 0) {
            val w = minOf(dp(176), (width - dp(44) - (n - 1) * dp(22)) / n)
            dockPkgs.forEachIndexed { i, p ->
                val t = tile(byPkg.getValue(p), w, true, made)
                dock.addView(t, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) leftMargin = dp(22) })
            }
        }
        dock.visibility = if (n > 0) View.VISIBLE else View.GONE
        dockEmpty.visibility = if (n > 0) View.GONE else View.VISIBLE

        for (r in 0 until grid.childCount) (grid.getChildAt(r) as ViewGroup).removeAllViews()   // the tiles leave their rows alive, to be put in new ones
        grid.removeAllViews()
        val rest = apps.filter { it.pkg !in dockPkgs && it.pkg !in hidden }
        val cols = 6
        val gap = dp(16)
        val w = (width - (cols - 1) * gap) / cols
        val cells: MutableList<View> = rest.map { tile(it, w, false, made) }.toMutableList()
        val hiddenHere = apps.count { it.pkg in hidden }
        if (hiddenHere > 0) cells.add(moves(Tile(this, null, w, getString(R.string.hidden, hiddenHere), ::dp).apply { setOnClickListener { unhide() } }, false, null))
        var row: LinearLayout? = null
        cells.forEachIndexed { i, v ->
            if (i % cols == 0) {
                row = Flat(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
                grid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(8) })
            }
            row!!.addView(v, LinearLayout.LayoutParams(-2, -2).apply { if (i % cols > 0) leftMargin = gap })
        }
        appsTitle.visibility = if (cells.isEmpty()) View.GONE else View.VISIBLE
        tileCache = made
        badgeTiles()

        val again = keep?.let { p -> tiles().firstOrNull { it.app?.pkg == p } }
        if (again != null) again.requestFocus() else if (currentFocus == null || currentFocus !is Tile && currentFocus !is Pill) toTop()
    }

    private fun tiles(): List<Tile> {
        val out = ArrayList<Tile>()
        for (i in 0 until dock.childCount) (dock.getChildAt(i) as? Tile)?.let { out.add(it) }
        for (r in 0 until grid.childCount) (grid.getChildAt(r) as ViewGroup).let { row -> for (i in 0 until row.childCount) (row.getChildAt(i) as? Tile)?.let { out.add(it) } }
        return out
    }

    /**
     * The D-pad on a tile, decided here, not by the nearest view ([Nav]): Right on a row's last tile goes to the next row's first,
     * Left the other way, Up and Down keep the column they started in as far as the next row has it (else its last tile), the dock
     * and the grid hand over to the tile nearest across; at the ends focus stays (the nearest view out there would be a header
     * button). True = handled. Up from the dock goes to the buttons, Android's choice.
     */
    private fun step(v: Tile, code: Int, inDock: Boolean): Boolean {
        val dir = when (code) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> Nav.Dir.RIGHT
            KeyEvent.KEYCODE_DPAD_LEFT -> Nav.Dir.LEFT
            KeyEvent.KEYCODE_DPAD_DOWN -> Nav.Dir.DOWN
            KeyEvent.KEYCODE_DPAD_UP -> Nav.Dir.UP
            else -> return false
        }
        if (inDock) {
            val at = dock.indexOfChild(v)
            if (at < 0) return false
            when (dir) {
                Nav.Dir.RIGHT -> dock.getChildAt(at + 1)?.let { go(it) } ?: v.nudge(1, 0)
                Nav.Dir.LEFT -> dock.getChildAt(at - 1)?.let { go(it) } ?: v.nudge(-1, 0)
                Nav.Dir.UP -> return false
                Nav.Dir.DOWN -> { val row = grid.getChildAt(0) as? ViewGroup ?: return false; wantCol = -1; go(row.getChildAt(Nav.nearest(centres(row), centre(v)))) }
            }
            return true
        }
        val rows = IntArray(grid.childCount) { (grid.getChildAt(it) as ViewGroup).childCount }
        val row = (0 until grid.childCount).firstOrNull { (grid.getChildAt(it) as ViewGroup).indexOfChild(v) >= 0 } ?: return false
        val col = (grid.getChildAt(row) as ViewGroup).indexOfChild(v)
        val to = Nav.grid(rows, row, col, wantCol, dir)
        if (to == null) {
            if (dir == Nav.Dir.UP && dock.childCount > 0) go(dock.getChildAt(Nav.nearest(centres(dock), centre(v))))
            else if (dir == Nav.Dir.UP) return false
            else v.nudge(if (dir == Nav.Dir.RIGHT) 1 else if (dir == Nav.Dir.LEFT) -1 else 0, if (dir == Nav.Dir.DOWN) 1 else 0)
            return true
        }
        val want = Nav.wantAfter(dir, col, wantCol)
        go((grid.getChildAt(to.row) as ViewGroup).getChildAt(to.col))
        wantCol = want                                                // only a move made here sets the column to keep
        return true
    }

    private fun go(t: View) { stepping = true; try { t.requestFocus() } finally { stepping = false } }

    private fun centre(v: View): Int { val xy = IntArray(2); v.getLocationInWindow(xy); return xy[0] + v.width / 2 }
    private fun centres(p: ViewGroup) = IntArray(p.childCount) { centre(p.getChildAt(it)) }

    private fun tile(a: App, w: Int, inDock: Boolean, made: HashMap<String, Tile>): Tile {
        val k = "${a.pkg}|$w|$inDock"
        val t = tileCache[k]?.takeIf { it.app === a } ?: makeTile(a, w, inDock)
        made[k] = t
        return t
    }

    private fun makeTile(a: App, w: Int, inDock: Boolean) = Tile(this, a, w, a.label, ::dp).apply {
        setOnClickListener { if (!Apps.launch(this@HomeActivity, a.pkg)) toast(R.string.no_open) }
        setOnLongClickListener { menu(a); true }
        moves(this, inDock) { menu(a) }
    }

    /** The D-pad, the menu key and the focus ring of a tile; the hidden apps' tile too, or focus on it showed nowhere. */
    private fun moves(t: Tile, inDock: Boolean, menu: (() -> Unit)?) = t.apply {
        setOnKeyListener { v, code, e ->
            if (code == KeyEvent.KEYCODE_MENU && menu != null) { if (e.action == KeyEvent.ACTION_UP) menu(); e.action == KeyEvent.ACTION_UP }
            else if (e.action == KeyEvent.ACTION_DOWN) step(v as Tile, code, inDock) else false
        }
        onFocusChangeListener = View.OnFocusChangeListener { v, f ->
            (v as Tile).focus(f)
            if (f) {
                if (!stepping) wantCol = -1                           // focus came another way: the column to keep starts over here
                gridFocus(!inDock)
                if (inDock) scrollToY(0)
                else {                                                // a row that is cut off comes to the upper third; one in full view stays put
                    var y = 0; var p: View? = v
                    while (p != null && p !== scroll) { y += p.top; p = p.parent as? View }
                    val margin = dp(40)
                    if (y - margin < scroll.scrollY || y + v.height + margin > scroll.scrollY + scroll.height)
                        scrollToY(maxOf(0, y - scroll.height / 3))
                }
            }
        }
    }

    private fun wall() {
        val wall = conf.optString("wall")
        val key = wall + conf.optLong("photo")
        if (key == wallShown) return
        wallShown = key
        val colors = wallColors(wall)
        // set once per wall, never per frame: a solid is a ColorDrawable (free), a gradient one GradientDrawable (no bitmap); only a photo costs a bitmap
        window.decorView.background = wallDrawable(colors)
        wallView.visibility = View.GONE; scrim.visibility = View.GONE; blurClip.visibility = View.GONE
        picture = null; soft = null
        if (wall == "photo") loadPhoto()
    }

    /** The photo, at the screen's size and no more ([Wall]), off the main thread; shown over whatever stands in for it. */
    private fun loadPhoto() {
        val dm = resources.displayMetrics
        bg.execute {
            val bmp = Wall.load(Wall.file(this), dm.widthPixels, dm.heightPixels)
            val small = bmp?.let { Wall.soft(it) }
            runOnUiThread {
                if (bmp != null && !isDestroyed && !trimmed) {
                    picture = bmp; soft = small
                    wallView.setImageBitmap(bmp); wallView.visibility = View.VISIBLE; scrim.visibility = View.VISIBLE
                    blurView.setImageBitmap(small); blurClip.visibility = View.VISIBLE; blurAlign()
                }
            }
        }
    }

    /** The Wi-Fi's name; null without the location grant (a client gives it over ADB) or with location off: then just "Wi-Fi". */
    private fun ssid(): String? = try {
        @Suppress("DEPRECATION") (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo?.ssid
            ?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    } catch (e: Exception) { null }

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
            else -> (ssid() ?: getString(R.string.wifi)) to false
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
        acts.add(0, R.string.customize to { customize() })
        acts.add((if (Motion.on) R.string.anim_off else R.string.anim_on) to { Store.change(this, JSONObject().put("anim", !conf.optBoolean("anim", true))) })
        acts.add(R.string.info to {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))) } catch (e: Exception) { toast(R.string.no_open) }
        })
        AlertDialog.Builder(this, R.style.Menu).setTitle(a.label)
            .setItems(acts.map { getString(it.first) }.toTypedArray()) { _, i -> acts[i].second() }.show().also { open = it }
    }

    private fun customize() { open = Customize.show(this) }

    /** The remote's Menu key anywhere but on an app (an app's own Menu opens its menu) opens the settings. */
    override fun onKeyUp(code: Int, e: KeyEvent): Boolean {
        if (code == KeyEvent.KEYCODE_MENU) { customize(); return true }
        // The Input key, where it reaches the home (cleanyourtv #342: some Realtek remotes send TV_INPUT, others TV). A box whose
        // system sends it to Google's GlobalKeyReceiver (the FPT Play box) never gives it to any app
        if (code == KeyEvent.KEYCODE_TV_INPUT || code == KeyEvent.KEYCODE_TV) { inputs(); return true }
        return super.onKeyUp(code, e)
    }

    private fun unhide() {
        val hidden = Store.list(this, "hidden")
        val here = apps.filter { it.pkg in hidden }
        AlertDialog.Builder(this, R.style.Menu).setTitle(getString(R.string.show))
            .setItems(here.map { it.label }.toTypedArray()) { _, i -> Store.putList(this, "hidden", hidden - here[i].pkg) }.show().also { open = it }
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
            }.show().also { open = it }
    }

    private fun open(action: String) {
        try { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e2: Exception) { toast(R.string.no_open) } }
    }

    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_SHORT).show()

    private fun text(size: Float, color: Int) = TextView(this).apply { textSize = size; setTextColor(color); shadow(this) }

    private fun shadow(t: TextView) = t.setShadowLayer(dp(6).toFloat(), 0f, dp(1).toFloat(), 0x80000000.toInt())

    /** Never scrolls by itself to the focused view: [tile] and the buttons say where to, so a row is not pressed to the edge. */
    private class Scroller(c: Context, private val moved: () -> Unit) : ScrollView(c) {
        override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?) = 0
        override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) { super.onScrollChanged(l, t, oldl, oldt); moved() }
    }

    /** The band's size alone: the screen-sized blurred photo inside its clip must not stretch it. */
    private class Wrap(c: Context) : FrameLayout(c) {
        override fun onMeasure(ws: Int, hs: Int) {
            val w = MeasureSpec.getSize(ws)
            val band = getChildAt(1)
            band.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            getChildAt(0).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(band.measuredHeight, MeasureSpec.EXACTLY))
            setMeasuredDimension(w, band.measuredHeight)
        }
    }

    /** A column whose alpha is applied to each child, not through a layer the size of the whole grid. */
    private class Flat(c: Context) : LinearLayout(c) { override fun hasOverlappingRendering() = false }

    /** A round-ended button with an icon and a word; white when focused. */
    private class Pill(c: Activity, icon: Int, label: String, iconOnly: Boolean = false, onClick: () -> Unit) : LinearLayout(c) {
        private val word: TextView
        init {
            val d = c.resources.displayMetrics.density
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            isFocusable = true; isClickable = true
            setPadding(((if (iconOnly) 12 else 16) * d).toInt(), 0, ((if (iconOnly) 12 else 20) * d).toInt(), 0)
            minimumHeight = (44 * d).toInt()
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { cornerRadius = 22 * d; setColor(Color.WHITE) })
                addState(intArrayOf(), GradientDrawable().apply { cornerRadius = 22 * d; setColor(0x29FFFFFF) })
            }
            val tint = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(0xFF111318.toInt(), Color.WHITE))
            addView(ImageView(c).apply { setImageResource(icon); imageTintList = tint; isDuplicateParentStateEnabled = true }, LayoutParams((20 * d).toInt(), (20 * d).toInt()))
            word = TextView(c).apply { text = label; textSize = 15f; maxLines = 1; maxWidth = (200 * d).toInt(); ellipsize = TextUtils.TruncateAt.END; setTextColor(tint); isDuplicateParentStateEnabled = true; setPadding((8 * d).toInt(), 0, 0, 0) }
            if (iconOnly) { word.visibility = View.GONE; contentDescription = label } else addView(word)
            setOnClickListener { onClick() }
        }
        fun compact() {
            word.visibility = View.GONE; contentDescription = word.text
            setPadding(paddingRight, 0, paddingRight, 0)
        }
        fun set(label: String, a: Float, icon: Int) { word.text = label; if (word.visibility == View.GONE) contentDescription = label; (getChildAt(0) as ImageView).apply { setImageResource(icon); alpha = a } }
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
    private val shine = View(c)
    private val content: View
    private var pic: ImageView? = null                              // the picture, so it can be let go and put back ([release])
    private val badge = BadgeView(c, dp)
    private var shownMark: Badges.Mark? = null
    private val lift: Float
    private val across: Float

    init {
        orientation = VERTICAL; isFocusable = true; isClickable = true; isLongClickable = true
        clipChildren = false; clipToPadding = false
        val h = w * 9 / 16
        val r = dp(10).toFloat()
        lift = dp(14).toFloat(); across = w.toFloat()
        val banner = app?.banner
        card.background = GradientDrawable().apply { cornerRadius = r; setColor(if (banner != null) 0xFF1E2230.toInt() else shade(app?.pkg ?: "")) }
        card.clipToOutline = true
        if (banner != null) {
            content = ImageView(c).apply { setImageDrawable(banner); scaleType = ImageView.ScaleType.CENTER_CROP }
            pic = content as ImageView
            card.addView(content, FrameLayout.LayoutParams(-1, -1))
        } else {
            val inner = LinearLayout(c).apply { orientation = VERTICAL; gravity = Gravity.CENTER; setPadding(dp(6), dp(4), dp(6), dp(4)) }
            inner.addView(ImageView(c).apply {
                if (app?.icon != null) { setImageDrawable(app.icon); pic = this } else { setImageResource(R.drawable.ic_eye_off); imageTintList = ColorStateList.valueOf(Color.WHITE) }
            }, LayoutParams(h * 45 / 100, h * 45 / 100))
            inner.addView(TextView(c).apply {
                text = label; textSize = 12f; setTextColor(Color.WHITE); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            }, LayoutParams(-1, -2))
            card.addView(inner, FrameLayout.LayoutParams(-1, -1))
            content = inner
        }
        shine.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x30FFFFFF, 0x0AFFFFFF, 0x00FFFFFF))
        shine.alpha = 0f; shine.translationY = -lift
        card.addView(shine, FrameLayout.LayoutParams(-1, h * 2 / 3))
        ring.background = ringDrawable(r, dp)
        ring.alpha = 0f
        card.addView(ring, FrameLayout.LayoutParams(-1, -1))
        card.addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(6), dp(6), 0) })
        addView(card, LayoutParams(w, h))
        name.apply {
            text = if (banner != null) label else ""                  // a tile without a banner already shows its name inside
            textSize = 13f; setTextColor(Color.WHITE); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
            alpha = 0f; setPadding(0, dp(9), 0, dp(4)); setShadowLayer(dp(4).toFloat(), 0f, 0f, 0x99000000.toInt())
        }
        addView(name, LayoutParams(w, -2))
        background = ColorDrawable(Color.TRANSPARENT)
    }

    /**
     * Focus lifts the card (a springy rise, a soft shadow, the picture a touch larger inside it, a shine passing over) and rings it;
     * with motion off only the ring and the name change, at once. Hardware layers only while it moves.
     */
    fun focus(on: Boolean) {
        card.animate().cancel(); ring.animate().cancel(); name.animate().cancel(); content.animate().cancel(); shine.animate().cancel()
        if (!Motion.on) {
            card.scaleX = 1f; card.scaleY = 1f; card.translationZ = 0f; content.scaleX = 1f; content.scaleY = 1f
            ring.alpha = if (on) 1f else 0f; name.alpha = if (on) 1f else 0f; name.translationY = 0f; shine.alpha = 0f
            return
        }
        // in on a spring, out on a plain glide a little slower: the next tile's rise leads, the last one sinks behind it
        val s = if (on) 1.12f else 1f
        val t = if (on) 520L else 300L
        val ip = if (on) Motion.spring else Motion.glide
        card.translationX = 0f; card.translationY = 0f
        card.animate().scaleX(s).scaleY(s).translationZ(if (on) lift else 0f).setDuration(t).setInterpolator(ip).withLayer().start()
        content.animate().scaleX(if (on) 1.05f else 1f).scaleY(if (on) 1.05f else 1f).setDuration(t).setInterpolator(ip).start()
        ring.animate().alpha(if (on) 1f else 0f).setDuration(if (on) 200L else 160L).setInterpolator(Motion.glide).start()
        name.animate().alpha(if (on) 1f else 0f).translationY(if (on) 0f else -lift / 3).setDuration(if (on) 360L else 200L).setInterpolator(Motion.glide).start()
        // the gloss: light from above that settles on the card as it rises, rather than a sweep on every move (busy when stepping fast)
        shine.animate().alpha(if (on) 1f else 0f).translationY(if (on) 0f else -lift).setDuration(if (on) 420L else 200L).setInterpolator(Motion.glide).start()
    }

    /** Nothing that way: the card leans toward it and wobbles back, so the press was felt (Apple TV does the same). */
    fun nudge(dx: Int, dy: Int) {
        if (!Motion.on) return
        val px = across / 40f
        card.animate().cancel()
        card.animate().translationX(dx * px).translationY(dy * px).setDuration(80).setInterpolator(Motion.glide).withEndAction {
            card.animate().translationX(0f).translationY(0f).setDuration(460).setInterpolator(Motion.bounce).start()
        }.start()
    }

    /** The picture let go (memory is short while the screen is hidden) and put back; the tile keeps its place and its colour meanwhile. */
    fun release() { pic?.setImageDrawable(null) }
    fun restore() { pic?.setImageDrawable(app?.banner ?: app?.icon) }

    /** The lock or the time left on the corner; nothing when [m] is null. Set only when it differs, so nothing is laid out for the same mark. */
    fun mark(m: Badges.Mark?, label: (Int) -> String) {
        if (m == shownMark) return
        shownMark = m
        badge.set(m, m?.let { if (it.locked) "" else label(it.minutes) } ?: "")
    }

    /** A dark colour of its own per app, for those without a TV banner. */
    private fun shade(pkg: String): Int {
        val hues = intArrayOf(0xFF27324A.toInt(), 0xFF2E3B2F.toInt(), 0xFF43293A.toInt(), 0xFF2B3A45.toInt(), 0xFF3D3424.toInt(), 0xFF2F2D4A.toInt())
        return hues[(pkg.hashCode() and 0x7fffffff) % hues.size]
    }
}

/**
 * The ring round the focused tile, seen on a white banner and on a black one alike, with or without the grow: a dark edge, the
 * accent in the middle (almost no banner is that blue), a thin white line inside for a banner that is blue itself.
 */
private fun ringDrawable(r: Float, dp: (Number) -> Int): Drawable {
    fun band(w: Int, color: Int, inset: Int) = GradientDrawable().apply { cornerRadius = maxOf(0f, r - inset); setStroke(w, color); setColor(Color.TRANSPARENT) }
    return android.graphics.drawable.LayerDrawable(arrayOf(band(dp(2), RING_EDGE, 0), band(dp(4), RING_ACCENT, dp(2)), band(dp(1), Color.WHITE, dp(6)))).apply {
        setLayerInset(1, dp(2), dp(2), dp(2), dp(2)); setLayerInset(2, dp(6), dp(6), dp(6), dp(6))
    }
}
const val RING_ACCENT = 0xFF2F7DF6.toInt()                          // 3.7:1 on white; with the dark edge it shows on any banner
const val RING_EDGE = 0xE6000000.toInt()

/**
 * A pill on a tile's corner: a lock, or an hourglass and the time left. Drawn by hand with the paints and icons made once, and
 * measured only when the mark changes: nothing is allocated while it is drawn (or while the tile grows).
 */
class BadgeView(c: Context, private val dp: (Number) -> Int) : View(c) {
    private val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6101420.toInt() }
    private val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = dp(13).toFloat(); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val lock = c.getDrawable(R.drawable.ic_lock)!!.mutate().also { it.setTint(0xFFFF8A80.toInt()) }
    private val glass = c.getDrawable(R.drawable.ic_hourglass)!!.mutate().also { it.setTint(0xFFFFD27A.toInt()) }
    private val box = android.graphics.RectF()
    private var icon: Drawable = lock
    private var label = ""
    private var textW = 0f

    init { visibility = GONE }

    fun set(m: Badges.Mark?, label: String) {
        if (m == null) { visibility = GONE; return }
        icon = if (m.locked) lock else glass
        this.label = label
        textW = if (label.isEmpty()) 0f else text.measureText(label)
        visibility = VISIBLE
        contentDescription = if (m.locked) context.getString(R.string.b_locked) else label
        requestLayout(); invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension((dp(8) + dp(15) + (if (label.isEmpty()) 0f else dp(4) + textW) + dp(8)).toInt(), dp(24))
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(box, height / 2f, height / 2f, bgPaint)
        val s = dp(15); val x = dp(8); val y = (height - s) / 2
        icon.setBounds(x, y, x + s, y + s); icon.draw(canvas)
        if (label.isNotEmpty()) canvas.drawText(label, (x + s + dp(4)).toFloat(), height / 2f - (text.ascent() + text.descent()) / 2f, text)
    }
}

/**
 * A damped spring as an interpolator, [damping] < 1: x(t) = 1 - e^(-ζωt)(cos ωd·t + ζω/ωd · sin ωd·t), with ω chosen so it has
 * settled (within 0.2 %) at t = 1. A PathInterpolator can overshoot once; a spring eases into its rest, which is what reads as smooth.
 */
class Spring(private val damping: Float) : android.animation.TimeInterpolator {
    private val w = 6.2f / damping
    private val wd = w * Math.sqrt(1.0 - damping * damping).toFloat()
    override fun getInterpolation(t: Float): Float {
        if (t >= 1f) return 1f
        val e = Math.exp((-damping * w * t).toDouble()).toFloat()
        return 1f - e * (Math.cos((wd * t).toDouble()).toFloat() + damping * w / wd * Math.sin((wd * t).toDouble()).toFloat())
    }
}

/** The motion TV Home uses: on from its setting, off when the system's animator duration scale is 0 (Settings, "remove animations"). */
object Motion {
    @JvmField var on = true
    val lift = PathInterpolator(0.34f, 1.35f, 0.64f, 1f)              // overshoots a little then settles: a spring
    val spring = Spring(0.62f)                                        // a real damped spring: rises fast, passes ~6 %, settles (Apple TV's focus)
    val bounce = Spring(0.35f)                                        // looser: the nudge at an edge wobbles back
    val glide = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)                // fast out, long soft end

    fun read(c: Context, conf: JSONObject) {
        val scale = try { Settings.Global.getFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) } catch (e: Exception) { 1f }
        on = conf.optBoolean("anim", true) && scale > 0f
    }

    /** A button a little larger while focused. */
    fun pop(v: View, focused: Boolean) {
        v.animate().cancel()
        val s = if (focused) 1.06f else 1f
        if (!on) { v.scaleX = 1f; v.scaleY = 1f } else v.animate().scaleX(s).scaleY(s).setDuration(if (focused) 420 else 220).setInterpolator(if (focused) spring else glide).start()
    }
}

/** The colours of a built-in background (the page's small tiles copy them: js/home.js HOME_WALLS). */
fun wallColors(wall: String): IntArray = when (wall) {
    "ocean" -> intArrayOf(0xFF03192B.toInt(), 0xFF0A4A66.toInt(), 0xFF05233A.toInt())
    "dusk" -> intArrayOf(0xFF1A0F2E.toInt(), 0xFF4A2150.toInt(), 0xFF7A3B3B.toInt())
    "forest" -> intArrayOf(0xFF06140F.toInt(), 0xFF133B2D.toInt(), 0xFF0A1F18.toInt())
    "aurora" -> intArrayOf(0xFF071A1F.toInt(), 0xFF0F4C4A.toInt(), 0xFF2B2A6B.toInt())
    "black" -> intArrayOf(Color.BLACK, Color.BLACK)
    "sunset" -> intArrayOf(0xFF1F0D1C.toInt(), 0xFF6B2D4A.toInt(), 0xFFA14A4A.toInt())
    "ember" -> intArrayOf(0xFF190B08.toInt(), 0xFF4A1E10.toInt(), 0xFF7A2E12.toInt())
    "plum" -> intArrayOf(0xFF150B22.toInt(), 0xFF3A1F56.toInt(), 0xFF5A2F7A.toInt())
    "midnight" -> intArrayOf(0xFF050A1A.toInt(), 0xFF0B1B45.toInt(), 0xFF14307A.toInt())
    "teal" -> intArrayOf(0xFF041A1A.toInt(), 0xFF08413F.toInt(), 0xFF0E5F5F.toInt())
    "slate" -> intArrayOf(0xFF0F1218.toInt(), 0xFF252C3A.toInt(), 0xFF3A4558.toInt())
    "graphite" -> intArrayOf(0xFF1C1C1E.toInt(), 0xFF1C1C1E.toInt())
    "navy" -> intArrayOf(0xFF0B1D3A.toInt(), 0xFF0B1D3A.toInt())
    "wine" -> intArrayOf(0xFF3A0F1F.toInt(), 0xFF3A0F1F.toInt())
    "moss" -> intArrayOf(0xFF14261B.toInt(), 0xFF14261B.toInt())
    else -> intArrayOf(0xFF0B0F1A.toInt(), 0xFF18223F.toInt(), 0xFF0B0F1A.toInt())
}

/** A solid is a ColorDrawable (free), a gradient a GradientDrawable (no bitmap). */
fun wallDrawable(colors: IntArray): Drawable =
    if (colors.all { it == colors[0] }) ColorDrawable(colors[0]) else GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
