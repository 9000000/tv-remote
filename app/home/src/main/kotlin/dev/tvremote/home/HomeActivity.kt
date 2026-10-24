package dev.tvremote.home

import android.app.Activity
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
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
import dev.tvremote.tvui.Glass
import dev.tvremote.tvui.Motion
import dev.tvremote.tvui.TvGlass
import dev.tvremote.tvui.TvTile
import dev.tvremote.tvui.TvUi
import dev.tvremote.update.Release
import dev.tvremote.update.SelfUpdate
import dev.tvremote.home.apps.App
import dev.tvremote.home.apps.Apps
import dev.tvremote.home.wall.Backdrop
import dev.tvremote.home.apps.Badges
import dev.tvremote.home.ui.Drop
import dev.tvremote.home.ui.Frost
import dev.tvremote.home.apps.InputOpen
import dev.tvremote.home.keys.Keys
import dev.tvremote.home.manage.ManageServer
import dev.tvremote.home.apps.Nav
import dev.tvremote.home.wall.Online
import dev.tvremote.home.manage.PhotoScreen
import dev.tvremote.home.apps.Pins
import dev.tvremote.home.widgets.sample
import dev.tvremote.home.apps.Tiles
import dev.tvremote.home.apps.LiveFace
import dev.tvremote.home.ui.BarBtn
import dev.tvremote.home.ui.TopBar
import dev.tvremote.home.wall.Wall
import dev.tvremote.home.wall.WallArt
import dev.tvremote.home.wall.Walls
import dev.tvremote.home.wall.WebWall
import dev.tvremote.home.wall.VideoWall
import dev.tvremote.home.wall.Videos
import dev.tvremote.home.wall.WebSnap
import dev.tvremote.home.wall.WebSnapper
import dev.tvremote.home.widgets.Widgets
import dev.tvremote.home.ui.Zoom

/**
 * The home screen: the clock, three round buttons in one capsule (inputs, network, settings), and ONE grid of apps: the favourites
 * first, then every other app.
 * Plain views, built in code: it has to come up at once on the weakest TV chip. What it shows is set in [Store], from here
 * (hold OK on an app) or from the phone / computer (HomeReceiver).
 */
class HomeActivity : Activity() {
    private val bg = Executors.newSingleThreadExecutor()
    private lateinit var backdrop: Backdrop
    private lateinit var scroll: ScrollView
    private lateinit var content: FrameLayout
    private lateinit var glass: Glass
    private lateinit var clock: TextClock
    private lateinit var dateLine: TextView
    private lateinit var greetLine: TextView
    private lateinit var netPill: BarBtn
    private lateinit var inputsPill: BarBtn
    private lateinit var settingsPill: BarBtn
    private lateinit var powerPill: BarBtn
    private lateinit var bar: TopBar
    private lateinit var grid: LinearLayout                         // the favourites first, then every other app, then the hidden apps' tile: one grid
    private var apps: List<App> = emptyList()
    /** The apps with their pictures as the grid has them: the picker shows these instead of loading every banner again (~230 ms). */
    internal fun loaded(): List<App> = apps
    @Volatile private var pins: List<App> = emptyList()                // shortcuts other apps pinned here ([Pins])
    private var conf = JSONObject()
    private var wallShown = ""
    private var lastWall = ""
    private var web: android.webkit.WebView? = null                 // the "web" wall's page, behind the grid and over the window's gradient; null for every other wall
    private var webDim: View? = null
    private var webOf = ""                                          // the address [web] shows
    private var video: VideoWall? = null                           // a video or GIF wall: the player behind the grid (the poster is the window's background until it moves)
    private var snapper: WebSnapper? = null                         // "web" in snap mode: takes the pictures that are the background (no page on screen)
    private lateinit var head: LinearLayout
    private var scrollAnim: ObjectAnimator? = null
    private val zoom by lazy { Zoom(this) }
    private var away = false                                        // really left (stopped), not just paused: only then does the screen come in again
    private var trimmed = false                                     // the pictures were let go for memory while away ([trim])
    private var shownKey = ""                                       // what the tiles were built from: the same again builds nothing
    private var tileCache = HashMap<String, Tile>()                 // a tile is made once per app and size, then moved
    private var picture: Bitmap? = null                             // the photo wallpaper and its blurred copy; let go in [trim], the small one stays as a stand-in
    private var soft: Bitmap? = null
    private var badges = Badges.NONE
    private var wantCol = -1                                        // the grid column Up and Down try to keep ([Nav])
    private var lastOpened: String?                                 // kept in prefs: the home may be made again while the app is open
        get() = getSharedPreferences("nav", MODE_PRIVATE).getString("last", null)
        set(v) { getSharedPreferences("nav", MODE_PRIVATE).edit().putString("last", v).apply() }
    private var topAt: View? = null                                 // the tile left by Up: Down from the buttons lands on it
    private var stepping = false
    internal companion object {
        var reopen = false                                            // across recreate(): the panel was open when the language changed
        const val SETUP_CODE = "2397346"                              // TV Setup's Downloader code (README, the site)
    }
    private var open: android.app.Dialog? = null                    // the panel or menu on top, closed by a Home press
    private lateinit var widgets: Widgets
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private val widgetTick = object : Runnable { override fun run() { widgetsRead(); ui.postDelayed(this, 60_000) } }
    private val netTick = object : Runnable { override fun run() { widgets.sample(); ui.postDelayed(this, Widgets.SAMPLE_MS) } }

    private fun dp(v: Number) = (v.toFloat() * resources.displayMetrics.density).toInt()

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    /**
     * Started before the TV's first unlock since it booted (direct boot): none of TV Home's own storage can be read yet, so a clock on
     * black stands in and the real screen is built once Android unlocks. Home must open then: a TV with no FallbackHome never finished
     * booting with a Home that could not start before the unlock (cleanyourtv, a Vision on Android 13: black screen, remote dead).
     */
    private var locked = false
    private val unlocked = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { if (locked) recreate() } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (CrashLoop.tripped(this)) {                               // crashing on start again and again: let the person change the home app
            try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (ignored: Exception) { }
            finish(); return
        }
        wantInputs = intent?.action == InputOpen.INPUTS
        Font.load(this)
        InputOpen.sync(this)
        if (intent?.action == HomeConfig.PHOTO_QR) window.decorView.post { if (!locked) PhotoScreen.show(this) {} }
        if (android.os.Build.VERSION.SDK_INT >= 24 && getSystemService(android.os.UserManager::class.java)?.isUserUnlocked == false) {
            locked = true
            registerReceiver(unlocked, IntentFilter(Intent.ACTION_USER_UNLOCKED))
            setContentView(FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(TextClock(this@HomeActivity).apply { textSize = 64f; setTextColor(Color.WHITE) },
                    FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            })
            return
        }
        Store.advance(this, true)                                     // a rotation on "each time Home is shown" (or one that fell due while away): before the first paint, so nothing is drawn twice
        conf = Store.get(this); readNames()
        val root = FrameLayout(this)
        backdrop = Backdrop(window.decorView)

        Motion.read(this, conf)
        scroll = Scroller(this) { Frost.moved() }.apply { isVerticalScrollBarEnabled = false; isFillViewport = true; clipChildren = false }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
            setPadding(dp(48), dp(34), dp(48), dp(48))
        }
        content = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        content.addView(col, FrameLayout.LayoutParams(-1, -2))
        glass = Glass(this, ::dp)                                     // over the tiles, in what scrolls: a scroll carries it with its tile
        content.addView(glass, FrameLayout.LayoutParams(0, 0))
        content.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> (currentFocus as? Tile)?.let { if (glass.placed) it.glassOn(false) } }
        scroll.addView(content, FrameLayout.LayoutParams(-1, -2))
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        // the clock, then the greeting (small, above: it is read once) and the day beside it (one row: under the clock they took
        // the room of the cards), the three buttons on the right
        head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false }  // a focused button grows past its row
        val left = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false }
        clock = TextClock(this).apply {
            textSize = 60f; setTextColor(Color.WHITE); typeface = Font.light
            includeFontPadding = false; shadow(this)
        }
        greetLine = text(16f, HEAD_DIM).apply { setPadding(0, 0, 0, dp(2)) }
        dateLine = text(22f, Color.WHITE).apply { typeface = Font.medium }
        val words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(greetLine); addView(dateLine) }
        left.addView(clock)
        left.addView(View(this).apply { setBackgroundColor(HEAD_RULE) }, LinearLayout.LayoutParams(dp(1), dp(56)).apply { leftMargin = dp(28); rightMargin = dp(28) })
        left.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        bar = TopBar(this, ::dp)
        inputsPill = BarBtn(this, R.drawable.ic_input, getString(R.string.inputs), ::dp) { inputs() }
        netPill = BarBtn(this, R.drawable.ic_wifi, getString(R.string.wifi), ::dp) { open(Settings.ACTION_WIFI_SETTINGS) }
        // one button for both settings, the TV's and TV Home's: two buttons side by side read as the same thing
        settingsPill = BarBtn(this, R.drawable.ic_settings, getString(R.string.settings), ::dp) {
            open = Drop.show(this, bar.capsule, listOf(
                Drop.Row(R.drawable.ic_settings, getString(R.string.tv_settings)) { open(Settings.ACTION_SETTINGS) }) +
                listOf(Settings.ACTION_DISPLAY_SETTINGS to (R.drawable.ic_monitor to R.string.picture), Settings.ACTION_SOUND_SETTINGS to (R.drawable.ic_vol_up to R.string.sound))
                    .filter { Intent(it.first).resolveActivity(packageManager) != null }          // a TV without that screen shows no dead row
                    .map { (act, v) -> Drop.Row(v.first, getString(v.second)) { open(act) } } + listOf(
                Drop.Row(R.drawable.ic_sliders, getString(R.string.customize)) { customize() }))
        }
        // for a remote whose own power button is broken: three named actions TV Setup runs (Power.kt), the hard one asks again
        powerPill = BarBtn(this, R.drawable.ic_power, getString(R.string.power), ::dp) {
            if (!setupThere()) { open = Drop.show(this, bar.capsule, needSetup()); return@BarBtn }
            open = Drop.show(this, bar.capsule, listOf(
                Drop.Row(R.drawable.ic_eye_off, getString(R.string.power_screen_off)) { Power.run(this, Power.SCREEN_OFF) },
                Drop.Row(R.drawable.ic_moon, getString(R.string.power_standby)) { Power.run(this, Power.STANDBY) },
                Drop.Row(R.drawable.ic_power, getString(R.string.power_off)) {
                    open = Drop.show(this, bar.capsule, listOf(                // Cancel first: the focus lands on the safe choice
                        Drop.Row(R.drawable.ic_close, getString(R.string.power_keep)) { },
                        Drop.Row(R.drawable.ic_power, getString(R.string.power_off_now)) { Power.run(this, Power.POWER_OFF) }))
                }))
        }
        for (b in listOf(inputsPill, netPill, settingsPill, powerPill)) bar.add(b)
        for (b in bar.buttons) b.setOnKeyListener { _, code, e ->
            code == KeyEvent.KEYCODE_DPAD_DOWN && e.action == KeyEvent.ACTION_DOWN && (topAt?.takeIf { it.isAttachedToWindow } ?: firstGridTile())?.let { go(it); true } == true
        }
        // the row's two ends stay put: Android's own search went from Inputs down-left onto a tile, and OK then opened its app
        inputsPill.nextFocusLeftId = View.generateViewId().also { inputsPill.id = it }
        settingsPill.id = View.generateViewId(); powerPill.id = View.generateViewId()
        powerPill.nextFocusRightId = powerPill.id
        bar.focusIn = { scrollToY(0) }
        head.addView(bar)
        col.addView(head)
        widgets = Widgets(this, ::dp).apply { visibility = View.GONE; onWeather = { runOnUiThread { if (!isDestroyed) widgetsRead() } } }
        col.addView(widgets, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })

        // ONE grid: the favourites are its first tiles, then every other app; no panel, the wallpaper stays whole
        grid = Flat(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        col.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(40) })
        // no widgets: the grid starts low, so the first row is whole and the second is cut by the screen's edge; the wallpaper above
        grid.viewTreeObserver.addOnGlobalLayoutListener { peek() }  // after every layout: rows came or went, a card opened
        scroll.viewTreeObserver.addOnGlobalFocusChangeListener { _, _ -> veil() }
        scroll.addOnLayoutChangeListener { _, _, t, _, b, _, ot, _, ob -> if (b - t != ob - ot) peek() }

        setContentView(root)
        Store.onChange = { conf = Store.get(this); readNames(); if (conf.optString("lang") != Lang.shown) relang() else { show(); wallTimer(); bingCheck(); peek() } }
        val pkgs = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(changed, pkgs)
        registerReceiver(tick, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED)
            @Suppress("DEPRECATION") addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(Intent.ACTION_DREAMING_STARTED); addAction(Intent.ACTION_DREAMING_STOPPED)
        })
        loadApps()
        if (Motion.on) enter(true)
        if (reopen) { reopen = false; ui.post { customize("lang") } }
    }

    /** A new language: the screen built again in it, the panel reopened if the change came from it (else it is closed). */
    private fun relang() {
        reopen = open?.isShowing == true && open === panel
        open?.dismiss(); open = null
        recreate()
    }

    override fun onDestroy() {
        Customize.forget(this)
        if (locked) { try { unregisterReceiver(unlocked) } catch (e: Exception) { }; super.onDestroy(); return }
        Store.onChange = null
        open?.dismiss()
        ui.removeCallbacks(widgetTick); ui.removeCallbacks(netTick); ui.removeCallbacks(wallTick); ui.removeCallbacks(webRetry); ui.removeCallbacks(tileTick)
        detachWeb(); detachVideo()
        try { unregisterReceiver(changed); unregisterReceiver(tick) } catch (e: Exception) { }
        bg.shutdownNow()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (locked) return
        if (wantInputs) { wantInputs = false; bar.post { inputs() } }   // started by the Input key: the menu once the home is up
        Motion.read(this, conf)                                       // the system's "remove animations" may have changed while away
        zoom.back()                                                   // the plate that grew over the opened app shrinks into its tile
        clockAndDay()
        if (away && Motion.on) enter(false)                           // Home pressed on the home screen pauses and resumes it without stopping: nothing may flicker then
        away = false
        if (trimmed && !locked) untrim()                              // a trim queued while away landed between onStart and here (away still true): blurred wallpaper, no banners, until Home was left again (a Coocaa)
        appsSince()
        Keys.push(this)                                               // TV Setup keeps the keys and the power-on app: told again, so a TV Setup installed later has them
        badgesRead()
        pinsRead()                                                    // a pin is confirmed in its own screen, then the home comes back here
        bg.execute { if (Tiles.refresh(this)) runOnUiThread { if (!isDestroyed) { shownKey = ""; show() } } }   // what TV Setup offers (it may have been installed or updated while away)
        ui.removeCallbacks(tileTick); ui.post(tileTick)
        ui.removeCallbacks(widgetTick); ui.post(widgetTick)
        ui.removeCallbacks(netTick); ui.postDelayed(netTick, Widgets.SAMPLE_MS)
        if (!Store.advance(this, false)) wallTimer()                  // on a change the new look sets the timer itself ([Store.onChange])
        bingCheck()
        ui.postDelayed({ if (!isDestroyed) bg.execute { Customize.warm(this) } }, 3000)   // the panel's pictures made while nothing else goes on
        ui.postDelayed({ Customize.prepare(this) }, 3500)                  // and the panel itself, on the main thread once it is idle
        upd.check({ r -> ui.postDelayed({ if (!isDestroyed && !away && open == null) askUpdate(r) }, 2500) }, { updating(it) })   // asked once the home has settled
    }

    private val upd by lazy { SelfUpdate(this, "home", "tv-home.apk") }

    /** A newer TV Home in the latest release (SelfUpdate): Update now / Later / Skip this version; Back is a later too. */
    private fun askUpdate(r: Release) {
        // Back closes the menu: a later, as before (dismiss does nothing to the update)
        val acts = listOf(TvUi.Item(R.drawable.ic_download, getString(R.string.upd_now)) { updating(r) },
            TvUi.Item(R.drawable.ic_clock, getString(R.string.upd_later)) { upd.later() },
            TvUi.Item(R.drawable.ic_close, getString(R.string.upd_skip)) { upd.skip(r) })
        open = TvUi.menu(this, R.style.TvSheet, getString(R.string.upd_title, r.version) + "\n" + getString(R.string.upd_note, upd.version()), acts, R.drawable.ic_check)
    }

    /** Its download, with Cancel (and Back, Home); at 100 % Android's installer takes over with its own question. */
    private fun updating(r: Release) {
        val (d, box) = TvUi.sheet(this, R.style.TvSheet)
        TvUi.sheetTitle(box, getString(R.string.upd_title, r.version))
        val note = TvUi.sheetNote(box, getString(R.string.upd_loading, 0))
        box.addView(TvUi.button(this, getString(R.string.upd_cancel)) { d.dismiss() }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END })
        d.setOnDismissListener { upd.cancel() }
        d.show()
        open = d
        val started = upd.install(r, { pct ->
            note.text = getString(R.string.upd_loading, pct)
            if (pct >= 100) { d.setOnDismissListener(null); d.dismiss() }
        }, { m ->
            d.setOnDismissListener(null); d.dismiss()
            val (f, fb) = TvUi.sheet(this, R.style.TvSheet)
            TvUi.sheetTitle(fb, getString(R.string.upd_title, r.version))
            TvUi.sheetNote(fb, getString(R.string.upd_failed, m))
            fb.addView(TvUi.button(this, getString(android.R.string.ok)) { f.dismiss() }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END })
            f.show(); open = f
            fb.getChildAt(fb.childCount - 1).requestFocus()
        })
        if (!started) { d.setOnDismissListener(null); d.dismiss() }      // Android's "install unknown apps" first: back from it, onResume goes on
        else box.getChildAt(box.childCount - 1).requestFocus()
    }

    override fun onStart() {
        super.onStart()
        if (locked) return
        if (away) Store.advance(this, true)                           // Home is shown again: "each time" moves on here, and a timed one that fell due while away
        if (trimmed) untrim()
        WebWall.wake(web); snapper?.resume(); video?.start()                          // a page keeps running only while Home is on screen: it is a slideshow, not a service
    }

    override fun onStop() { away = true; zoom.hidden(); ManageServer.release(); WebWall.sleep(web); snapper?.pause(); video?.stop(); super.onStop() }   // the QR stops opening the page; a phone already on it goes on until idle

    /** Memory is short and the screen is hidden: the wallpaper's big bitmap and every banner go, the next [onStart] brings them back. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (locked) return
        val low = level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
        if (away && low) trim()                                       // RUNNING_LOW..CRITICAL also count: a game in front can starve the system before UI_HIDDEN's later levels
        else if (low) Customize.forget()                              // shown: the tiles' logos and the wallpaper stay (a Coocaa lost both), only the panel's thumbnails go, made again when it opens
    }

    private fun trim() {
        if (trimmed) return
        trimmed = true
        picture = null
        val id = conf.optString("wall")
        detachWeb()                                                   // a page costs the most memory of any wall: gone while away, made again by [untrim]
        if (WallArt.has(id)) backdrop.show(wallColors(id), null, id)      // a drawn scene costs nothing to keep: only the picture is repainted
        else backdrop.show(wallColors(id), soft)                          // the blurred copy stands in, so the return is not a blank
        val keep = firstRows()                                        // the logos seen first on return stay (~10 x 230 KB): a home without them is worse than the memory
        for (t in tileCache.values) if (t.app !in keep) t.release()
        for (a in apps) if (a !in keep) a.release()
        Customize.forget()
    }

    private fun firstRows() = tiles().take(2 * conf.optInt("cols", HomeConfig.COLS_DEFAULT)).mapNotNull { it.app }.toSet()

    private fun untrim() {
        trimmed = false
        val dm = resources.displayMetrics
        if (Walls.isPicture(conf.optString("wall")) || conf.optString("wall") == HomeConfig.WEB) loadWall(conf.optString("wall"))
        for (t in tiles().take(2 * conf.optInt("cols", HomeConfig.COLS_DEFAULT))) { t.app?.let { if (it.banner == null && it.icon == null) it.load(this) }; t.restore() }   // the favourites at once (kept by [trim] unless the list moved), the rest from the side
        bg.execute {
            for (a in apps) if (a.banner == null && a.icon == null) a.load(this)
            runOnUiThread { if (!isDestroyed && !trimmed) for (t in tileCache.values) t.restore() }
        }
    }

    override fun onPause() { if (moving != null) endMove(false); zoom.paused(); ui.removeCallbacks(widgetTick); ui.removeCallbacks(netTick); ui.removeCallbacks(wallTick); ui.removeCallbacks(tileTick); super.onPause() }

    private var cpuAgain = false

    private var noWidgets = false

    /**
     * The grid's top margin, so the first screen shows "rows" rows of apps (1.2: row 1 whole and a fifth of row 2), at most up to the
     * clock or the cards. By itself: without widgets 1.2 (a clean screen that still says there is more), with them right under them.
     * From the real screen and tile heights.
     */
    private fun peek() {
        val lp = grid.layoutParams as LinearLayout.LayoutParams
        val rows = grid.childCount
        val rowH = if (rows > 0) grid.getChildAt(0).height else 0
        val want = conf.optDouble("rows", 0.0).let { if (it >= 1) it else if (noWidgets) 1.2 else 0.0 }
        if (want > 0 && rows > 0 && rowH == 0) return                   // new rows, not laid out yet: the next layout asks again
        val above = if (noWidgets) head.bottom else widgets.bottom
        val least = dp(if (noWidgets) 48 else 36); val gap = dp(GRID_GAP)
        if (rowH > 0 && scroll.height > 0) {
            val room = scroll.height - above - least
            val n = maxOf(0, (room + gap) / (rowH + gap)); val f = (room - n * (rowH + gap)).toDouble() / rowH
            rowsFit = maxOf(1.0, if (f >= 1) n + 1.0 else n + Math.floor(f * 10) / 10)
        }
        // one row sits where row 1 of many would (more columns put few apps in one row: it rose to the clock)
        val m = if (want == 0.0 || rows == 0 || rowH == 0 || scroll.height == 0) least else {
            val n = Math.floor(want + 1e-6).toInt(); val f = want - n
            // whole rows keep half a row clear under them (the rows below, hidden there until the focus goes down: [veil])
            val tall = if (f < 0.05) n * (rowH + gap) + rowH / 2 else n * (rowH + gap) + (f * rowH).toInt()
            maxOf(least, scroll.height - tall - above)
        }
        if (Math.abs(m - lp.topMargin) > 1) {
            // a new number of rows glides there: the grid moved at once and drawn from where it was (no layout per frame)
            val from = lp.topMargin
            lp.topMargin = m; grid.requestLayout()
            if (Motion.on && grid.isLaidOut && grid.alpha == 1f && grid.animation == null) {
                grid.animate().cancel()
                grid.translationY = grid.translationY + from - m
                grid.animate().translationY(0f).setDuration(320).setInterpolator(Motion.glide).withLayer().start()
            }
        }
        veil()
    }

    /** "rows" a whole number: the rows under them stay hidden in the half row left clear until the focus goes down into them. */
    private fun veil(glide: Boolean = true) {
        val r = conf.optDouble("rows", 0.0)
        val n = if (r >= 1 && r - Math.floor(r) < 0.05) Math.floor(r).toInt() else Int.MAX_VALUE
        var v: View? = currentFocus
        while (v != null && v.parent !== grid) v = v.parent as? View
        val down = v != null && grid.indexOfChild(v) >= n
        for (i in 0 until grid.childCount) {
            val row = grid.getChildAt(i); val want = if (i < n || down) 1f else 0f
            if (row.alpha != want) { row.animate().cancel(); if (glide && Motion.on) row.animate().alpha(want).setDuration(220).start() else row.alpha = want }
        }
    }

    /** The cards' numbers, read off the main thread; none at all while they are off. */
    private fun widgetsRead() {
        val want = Store.list(this, "widgets")
        val conf = Store.get(this); val note = conf.optString("note"); val sizes = conf.optJSONObject("wsize") ?: JSONObject()
        val none = want.isEmpty() && note.isEmpty()
        noWidgets = none
        peek()                                                        // the grid's margin is peek()'s alone: set here too, every minute's read made it jump
        if (none) { widgets.visibility = View.GONE; return }
        bg.execute {
            val f = widgets.read(want)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                widgets.show(want, f, note, { p -> apps.firstOrNull { it.pkg == p }?.label ?: p.substringAfterLast('.') }, sizes, conf.optJSONObject("qrs") ?: JSONObject())
                // TV Setup answers the CPU with its last measure and starts the next: the first time there is none, so ask again soon, once
                if ("system" in want && f.cpu == null && !cpuAgain) { cpuAgain = true; ui.postDelayed({ if (!isDestroyed) widgetsRead() }, 2500) }
            }
        }
    }

    /** The screen comes in: the clock and the buttons ease down, the grid rises (only a fade when coming back from an app). */
    private fun enter(slide: Boolean) {
        val d = if (slide) dp(14).toFloat() else 0f
        head.alpha = 0f; head.translationY = -d
        grid.alpha = 0f; grid.translationY = d
        head.animate().alpha(1f).translationY(0f).setDuration(if (slide) 420 else 240).setInterpolator(Motion.glide).withLayer().start()
        grid.animate().alpha(1f).translationY(0f).setStartDelay(if (slide) 70 else 0).setDuration(if (slide) 420 else 240).setInterpolator(Motion.glide).withLayer().start()
    }

    /** Scrolls to [y] gliding (one animation at a time, the last target wins); at once when motion is off. */
    /** The glass onto this tile, when the focus is glass ([Motion.glass]); [glide] false: there at once. A tile being moved has the ring alone. */
    private fun Tile.glassOn(glide: Boolean) { if (Motion.glass && moving == null) glass.to(content, pane, TvTile.GROW, corner, glide && Motion.on) else glass.hide() }

    private fun scrollToY(y: Int) {
        scrollAnim?.cancel()
        if (!Motion.on) { scroll.scrollTo(0, y); return }
        if (scroll.scrollY == y) return
        scrollAnim = ObjectAnimator.ofInt(scroll, "scrollY", scroll.scrollY, y).apply { duration = 420; interpolator = Motion.glide; start() }
    }

    /** Home pressed while already home, or Back: to the top, on the first tile. */
    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        if (locked) return
        if (i.action == InputOpen.INPUTS) { inputs(); return }
        if (i.action == HomeConfig.PHOTO_QR) { open?.dismiss(); PhotoScreen.show(this) {}; return }                  // the remote's Input key while another app is in front
        if (open?.isShowing == true) { open?.dismiss(); open = null; return }                          // a panel or menu is open: this press closes it
        val last = if (away) lastOpened?.let { p -> tiles().firstOrNull { it.app?.pkg == p } } else null
        if (last != null) last.requestFocus() else toTop()            // back from an app: on its tile; Home on the home screen: to the top
    }

    @Deprecated("Back on a home screen goes nowhere")
    override fun onBackPressed() { if (!locked && SystemClock.uptimeMillis() - back > 500) toTop() }

    // Android 13+ hands the Back that closed a menu or the panel to the home too: it must not also move the focus to the dock
    private var back = 0L
    private var wantInputs = false
    override fun onWindowFocusChanged(has: Boolean) { super.onWindowFocusChanged(has); if (has) back = SystemClock.uptimeMillis() }

    private fun toTop() {
        val first = firstGridTile()
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
        val conf = Store.get(this)
        val power = if (conf.optBoolean("autoon")) conf.optString("autoapp") else ""
        for (t in tileCache.values) t.app?.let { a -> t.mark(badges.of(a.pkg) ?: Badges.POWER.takeIf { a.pkg == power }) { Badges.label(it, m, h) } }
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
    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            // a screensaver over Home: Android 11+ stops the activity, older TVs only lay a window over it and the video went on decoding
            when (i.action) { Intent.ACTION_DREAMING_STARTED -> return video?.stop() ?: Unit; Intent.ACTION_DREAMING_STOPPED -> return if (!away) video?.start() ?: Unit else Unit }
            clockAndDay(); bingCheck()                                // Bing: a new day's picture seen while Home stays on
        }
    }

    private var seq = 0                                               // PackageManager's change counter at the last full list; 0 = everything since boot

    /** Back on screen: the list is read again only if the system says a package changed meanwhile (API 26+); a missed broadcast shows up here. */
    private fun appsSince() {
        if (Build.VERSION.SDK_INT < 26) return                        // no counter: the broadcasts are all there is, as before
        val ch = try { packageManager.getChangedPackages(seq) } catch (e: Exception) { null } ?: return   // null = nothing changed
        val names = ch.packageNames.filter { it != packageName }
        if (names.isEmpty()) { seq = ch.sequenceNumber; return }
        if (names.size > 3) { loadApps(); return }                    // many at once (an update of everything): one pass beats a tile each
        seq = ch.sequenceNumber
        for (n in names) packageChanged(n, false)                     // skips by itself a part that turned on or off with the same name and entry
    }

    private fun loadApps() {
        if (Build.VERSION.SDK_INT >= 26) try { packageManager.getChangedPackages(seq)?.let { seq = it.sequenceNumber } } catch (e: Exception) { }   // before the list, so a change during it is seen on the next return
        bg.execute {
            val list = Apps.list(this)
            list.forEach { it.load(this) }                            // the pictures off the main thread: dozens of apps on a slow chip
            runOnUiThread { if (!isDestroyed) { apps = (list + apps.filter { it.grid }).sortedBy { it.label.lowercase() }; shownKey = ""; show() } }
        }
    }

    /** The shortcuts other apps pinned here ([Pins]): the grid is built again only when they changed. */
    private fun pinsRead() {
        bg.execute {
            val now = Pins.list(this)
            if (now.map { it.pkg } == pins.map { it.pkg }) return@execute
            now.forEach { it.load(this) }
            runOnUiThread { if (!isDestroyed) { pins = now; shownKey = ""; show() } }
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
                // a part of it turned on or off with its name and entry the same: some boxes' apps do it every 15 s, and each rebuilt the grid
                if (one != null && was != null && one.label == was.label && one.info?.name == was.info?.name
                    && one.info?.iconResource == was.info?.iconResource && one.info?.bannerResource == was.info?.bannerResource) return@runOnUiThread
                apps = (apps.filter { it.pkg != pkg } + listOfNotNull(one)).sortedBy { it.label.lowercase() }
                shownKey = ""; show()
                pinsRead()                                            // an app's pins leave with it
            }
        }
    }

    // ---- what is shown ----

    private fun show() {
        moving?.let { it.carry(false); hint(false); for (o in tiles()) { o.animate().cancel(); o.alpha = 1f } }; moving = null                           // anything that rebuilds the tiles ends a move that was not confirmed: the saved order wins
        val keep = (currentFocus as? Tile)?.app?.pkg
        val keepAt = tiles().indexOf(currentFocus)                    // its place: an app hidden or a live tile taken off leaves no tile to go back to
        Motion.read(this, conf)
        if (!Motion.glass) glass.hide()
        wall()
        clockAndDay()
        powerBar()
        widgetsRead()
        syncTiles()
        val byPkg = apps.associateBy { it.pkg }
        val dockPkgs = Store.list(this, "dock").filter { it in byPkg }
        val hidden = Store.list(this, "hidden").toSet()
        val shownPins = pins.filter { it.pkg !in byPkg }                // a shortcut put on the grid by the picker shows as that tile, not twice
        val key = (dockPkgs + shownPins.map { it.pkg }).joinToString(",") + "|" + apps.filter { it.pkg !in dockPkgs && it.pkg !in hidden }.joinToString(",") { it.pkg } + "|" + apps.count { it.pkg in hidden } + "|" + conf.optInt("cols") + "|" + conf.optJSONArray("order") + "|" + conf.optJSONObject("names")
        if (key == shownKey) { badgeTiles(); return }                 // nothing that decides the tiles changed: not one view is rebuilt (the power mark may have)
        shownKey = key
        val made = HashMap<String, Tile>()                            // the tiles kept for the next build: those of apps still here
        val width = resources.displayMetrics.widthPixels - dp(96)
        val favs = dockPkgs.map { byPkg.getValue(it) } + shownPins     // the pins after the apps, under the same limit

        for (r in 0 until grid.childCount) (grid.getChildAt(r) as ViewGroup).removeAllViews()   // the tiles leave their rows alive, to be put in new ones
        grid.removeAllViews()
        val place = Store.list(this, "order").withIndex().associate { it.value to it.index }   // the grid's own order; an app it does not know goes last, A-Z
        val rest = apps.filter { it.pkg !in dockPkgs && it.pkg !in hidden }.sortedBy { place[it.pkg] ?: Int.MAX_VALUE }
        val cols = conf.optInt("cols", HomeConfig.COLS_DEFAULT)
        val gap = dp(GRID_GAP)
        val w = (width - (cols - 1) * gap) / cols                     // one size for every tile: the favourites are only the first ones
        val cells: MutableList<View> = (favs + rest).map { tile(it, w, made) }.toMutableList()
        val hiddenHere = apps.count { it.pkg in hidden }
        if (hiddenHere > 0) cells.add(moves(Tile(this, null, w, getString(R.string.hidden, hiddenHere), ::dp).apply { setOnClickListener { unhide() } }, null))
        fill(cells, cols, gap)
        tileCache = made
        badgeTiles()
        ui.removeCallbacks(tileTick); if (!away && liveViews().isNotEmpty()) ui.post(tileTick)   // a new grid asks at once

        val again = (keep ?: lastOpened.takeIf { currentFocus == null })?.let { p -> tiles().firstOrNull { it.app?.pkg == p } }
        val near = if (again == null && keepAt >= 0) tiles().let { t -> t.getOrNull(minOf(keepAt, t.size - 1)) } else null   // the one now in its place (the last, if it was last): not the top of Home, a scroll away
        if (again != null) again.requestFocus() else if (near != null) near.requestFocus() else if (currentFocus == null || currentFocus !is Tile && currentFocus !is BarBtn) toTop()
    }

    private fun fill(cells: List<View>, cols: Int, gap: Int) {
        var row: LinearLayout? = null
        cells.forEachIndexed { i, v ->
            if (i % cols == 0) {
                row = Flat(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
                grid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = gap })   // as far apart as the tiles are side by side
            }
            row!!.addView(v, LinearLayout.LayoutParams(-2, -2).apply { if (i % cols > 0) leftMargin = gap })
        }
        veil(false)                                                   // new rows come shown: the hidden ones at once, not seen fading out
    }

    // ---- move mode: an app is carried through every row with the D-pad, OK saves, Back / Home cancels ----

    private var moving: Tile? = null
    private val hintView: TextView by lazy {
        Font.on(TextView(this)).apply {                                // a pill at the top centre, over no tile (the clock is left, the buttons right)
            text = getString(R.string.move_hint); textSize = 17f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(22), dp(10), dp(22), dp(10)); visibility = View.GONE; alpha = 0f
            background = GradientDrawable().apply { cornerRadius = dp(30).toFloat(); setColor(0xE6101420.toInt()); setStroke(dp(1), 0x33FFFFFF) }
            (window.decorView as ViewGroup).let { (it.findViewById<ViewGroup>(android.R.id.content)).addView(this, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(40) }) }
        }
    }

    private fun startMove(pkg: String) {
        val t = tiles().firstOrNull { it.app?.pkg == pkg && (it.app.pin == null || it.app.grid) } ?: return
        moving = t; movingFavs = dockCount()
        t.requestFocus(); t.carry(true); glass.hide()                 // the glass's edge inside the ring read as two borders
        for (o in tiles()) if (o !== t) o.animate().cancel().also { o.animate().alpha(DIM).setDuration(if (Motion.on) 150L else 0L).start() }   // the rest steps back
        hint(true)
    }

    private fun hint(on: Boolean) {
        hintView.animate().cancel()
        if (on) { hintView.visibility = View.VISIBLE; hintView.animate().alpha(1f).setDuration(if (Motion.on) 150L else 0L).start() }
        else hintView.animate().alpha(0f).setDuration(if (Motion.on) 150L else 0L).withEndAction { hintView.visibility = View.GONE }.start()
    }

    /** The tiles an app can be carried through: every app's, across the rows (the favourites are only the first [movingFavs]); a pinned shortcut keeps its place after them. */
    private fun movable(): List<Tile> = tiles().filter { it.app != null && (it.app.pin == null || it.app.grid) }
    private var movingFavs = 0

    /** The favourites are the first tiles, as many as the saved dock holds (pins and the rest follow). */
    private fun dockCount(): Int { val by = apps.map { it.pkg }.toSet(); return Store.list(this, "dock").count { it in by } }

    private fun moveKey(t: Tile, code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { endMove(true); return true }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { endMove(false); return true }
        }
        val cols = conf.optInt("cols", HomeConfig.COLS_DEFAULT)
        val d = when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1; KeyEvent.KEYCODE_DPAD_RIGHT -> 1
            KeyEvent.KEYCODE_DPAD_UP -> -cols; KeyEvent.KEYCODE_DPAD_DOWN -> cols
            else -> return false
        }
        val shown = tiles()
        val m = movable().toMutableList()
        val at = m.indexOf(t); val to = at + d
        if (to !in m.indices) { t.nudge(if (d == 1) 1 else if (d == -1) -1 else 0, if (d == cols) 1 else 0); return true }
        val before = shown.associateWith { val xy = IntArray(2); it.getLocationOnScreen(xy); xy }
        m.add(to, m.removeAt(at))
        val k = movingFavs.coerceAtMost(m.size)
        val all: List<View> = m.take(k) + shown.filter { it.app?.pin != null } + m.drop(k) + shown.filter { it.app == null }
        for (r in 0 until grid.childCount) (grid.getChildAt(r) as ViewGroup).removeAllViews()
        grid.removeAllViews()
        fill(all, cols, dp(GRID_GAP))
        stepping = true; try { t.requestFocus() } finally { stepping = false }
        t.carry(true)
        grid.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                grid.viewTreeObserver.removeOnPreDrawListener(this)
                val now = IntArray(2)
                for ((v, o) in before) {                              // the others slide from where they stood
                    v.getLocationOnScreen(now)
                    val dx = (o[0] - now[0]).toFloat(); val dy = (o[1] - now[1]).toFloat()
                    if (dx == 0f && dy == 0f) continue
                    v.animate().cancel()
                    if (!Motion.on) { v.translationX = 0f; v.translationY = 0f; continue }
                    v.translationX = dx; v.translationY = dy
                    v.animate().translationX(0f).translationY(0f).setDuration(150).setInterpolator(Motion.glide).start()
                }
                return true
            }
        })
        return true
    }

    private fun endMove(save: Boolean) {
        val t = moving ?: return
        moving = null
        t.carry(false); t.glassOn(false)
        hint(false)
        for (o in tiles()) o.animate().cancel().also { o.animate().alpha(1f).setDuration(if (Motion.on) 150L else 0L).start() }
        if (save) {                                                   // the first places are the favourites, as many as before: an app carried in pushes the last one down
            val m = movable().mapNotNull { it.app?.pkg }; val k = movingFavs.coerceAtMost(m.size)
            val here = apps.map { it.pkg }.toSet()
            Store.change(this, JSONObject()                           // one write: one rebuild
                .put("dock", org.json.JSONArray(m.take(k) + Store.list(this, "dock").filter { it !in here }))   // an app no longer installed keeps its place at the end
                .put("order", org.json.JSONArray(m.drop(k))))           // the grid's order; apps that leave drop out of it by not being listed
        } else { shownKey = ""; show() }                              // the saved order, tile by tile as it was
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val t = moving
        if (t != null && !locked) {
            if (e.action == KeyEvent.ACTION_DOWN) { if (e.repeatCount == 0 || e.keyCode != KeyEvent.KEYCODE_DPAD_CENTER) moveKey(t, e.keyCode).let { if (it) return true } }
            else if (moveKeys.contains(e.keyCode)) return true        // the key-up of what was taken
        }
        return super.dispatchKeyEvent(e)
    }
    /** Colour keys and 0-9 given an app in the settings (Keys): answered here on TV Home's own screen; elsewhere TV Setup's key filter does. */
    override fun onKeyDown(keyCode: Int, e: KeyEvent): Boolean = (!locked && moving == null && Keys.onKey(this, e)) || super.onKeyDown(keyCode, e)

    private val moveKeys = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)

    private fun tiles(): List<Tile> {
        val out = ArrayList<Tile>()
        for (r in 0 until grid.childCount) (grid.getChildAt(r) as ViewGroup).let { row -> for (i in 0 until row.childCount) (row.getChildAt(i) as? Tile)?.let { out.add(it) } }
        return out
    }

    /**
     * The D-pad on a tile, decided here, not by the nearest view ([Nav]): Right on a row's last tile goes to the next row's first,
     * Left the other way, Up and Down keep the column they started in as far as the next row has it (else its last tile); at the ends
     * focus stays (the nearest view out there would be a header button). True = handled. Up from the first row goes to the buttons.
     */
    /** Up from the top row of tiles: the button on the head nearest above it. */
    private fun up(v: View) {
        val at = IntArray(2); v.getLocationOnScreen(at)
        val x = at[0] + v.width / 2
        bar.buttons.filter { it.isShown }.minByOrNull { p ->
            p.getLocationOnScreen(at); Math.abs(at[0] + p.width / 2 - x)
        }?.requestFocus()
    }

    private fun step(v: Tile, code: Int): Boolean {
        val dir = when (code) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> Nav.Dir.RIGHT
            KeyEvent.KEYCODE_DPAD_LEFT -> Nav.Dir.LEFT
            KeyEvent.KEYCODE_DPAD_DOWN -> Nav.Dir.DOWN
            KeyEvent.KEYCODE_DPAD_UP -> Nav.Dir.UP
            else -> return false
        }
        val rows = IntArray(grid.childCount) { (grid.getChildAt(it) as ViewGroup).childCount }
        val row = (0 until grid.childCount).firstOrNull { (grid.getChildAt(it) as ViewGroup).indexOfChild(v) >= 0 } ?: return false
        val col = (grid.getChildAt(row) as ViewGroup).indexOfChild(v)
        val to = Nav.grid(rows, row, col, wantCol, dir)
        if (to == null) {
            if (dir == Nav.Dir.UP) { topAt = v; up(v) }               // Android 9 (a BRAVIA) found nothing above the first row by itself
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

    // ---- live tiles ([Tiles]): read every few seconds while Home is on screen, never while it is not ----

    private fun tileSays(mode: String) = getString(if (mode == Tiles.UNTRUSTED) R.string.tile_untrusted else R.string.tile_need_setup)
    private fun liveViews(): List<Tile> = tiles().filter { it.app?.live == true }
    private fun animateTiles() = Motion.on && !Motion.calm            // a weak box (calm by itself under 1.5 GB) just changes the numbers

    private val tileTick = object : Runnable { override fun run() { tilesRead(); if (liveViews().isNotEmpty()) ui.postDelayed(this, Tiles.EVERY_MS) } }

    /** Every live tile on the grid asks its provider for its state, off the main thread. */
    private fun tilesRead() {
        val keys = liveViews().mapNotNull { it.app?.pkg }.distinct()
        if (keys.isEmpty() || isDestroyed) return
        bg.execute { for (k in keys) { val s = Tiles.fetch(this, k); runOnUiThread { tileShow(k, s) } } }
    }

    private fun tileShow(key: String, s: JSONObject) { if (!isDestroyed) for (t in liveViews()) if (t.app?.pkg == key) t.live(s, animateTiles()) }

    /** The press: the provider does its job and the tile follows it until it is done (the count-down is the tile's). */
    private fun pressTile(a: App) {
        Thread {                                                  // its own thread: the follow-up polls for up to 14 s, and on [bg] the wallpaper, widgets and apps waited behind it
            val ok = Tiles.press(this, a.pkg) { s -> runOnUiThread { tileShow(a.pkg, s) } }
            if (!ok) runOnUiThread { if (!isDestroyed) toast(if (Tiles.status(this) == Tiles.UNTRUSTED) R.string.power_untrusted else R.string.tile_need_setup_long) }
        }.start()
    }

    /** The tiles in the config ([HomeConfig] "tiles") are among the apps, so move, hide and the order work on them as on any app. */
    private fun syncTiles() {
        val live = Tiles.apps(this, conf)
        val now = apps.filter { it.grid }
        if (live.map { it.pkg to it.label } == now.map { it.pkg to it.label }) return
        apps = (apps.filter { !it.grid } + live).sortedBy { it.label.lowercase() }
    }

    private fun tile(a: App, w: Int, made: HashMap<String, Tile>): Tile {
        val k = "${a.pkg}|$w|${a.label}"
        val t = tileCache[k]?.takeIf { it.app === a } ?: makeTile(a, w)
        made[k] = t
        return t
    }

    /** The person's names for apps into [Apps.names]; a tile is cached by its name too, so a renamed one is made again. */
    private fun readNames() {
        val g = conf.optJSONObject("names")
        Apps.names = if (g == null) emptyMap() else g.keys().asSequence().associateWith { g.optString(it) }
        apps = apps.sortedBy { it.label.lowercase() }
    }

    private fun makeTile(a: App, w: Int) = Tile(this, a, w, a.label, ::dp, ::tileSays).apply {
        setOnClickListener {
            if (a.live) { pressTile(a); return@setOnClickListener }   // a live tile does its job in place: no app opens, no zoom
            lastOpened = a.pkg
            fun go(o: Bundle?) { if (!(if (a.pin != null) Pins.open(this@HomeActivity, a, o) else Apps.launch(this@HomeActivity, a.pkg, o))) { zoom.end(); toast(R.string.no_open) } }
            if (!zoom.open(this, ::go)) go(null)
        }
        setOnLongClickListener { menu(a); true }
        moves(this) { menu(a) }
    }

    /** The D-pad, the menu key and the focus ring of a tile; the hidden apps' tile too, or focus on it showed nowhere. */
    private fun moves(t: Tile, menu: (() -> Unit)?) = t.apply {
        nudged = { x, y -> if (Motion.glass) glass.nudge(x, y) }
        setOnKeyListener { v, code, e ->
            if (code == KeyEvent.KEYCODE_MENU && menu != null) { if (e.action == KeyEvent.ACTION_UP) menu(); e.action == KeyEvent.ACTION_UP }
            else if (e.action == KeyEvent.ACTION_DOWN) step(v as Tile, code) else false
        }
        onFocusChangeListener = View.OnFocusChangeListener { v, f ->
            (v as Tile).focus(f)
            if (f) v.glassOn(true) else v.post { if (currentFocus !is Tile) glass.hide() }   // to the buttons or a panel: the pane goes
            if (f) {
                if (!stepping) wantCol = -1                           // focus came another way: the column to keep starts over here
                if (grid.indexOfChild(v.parent as? View) <= 0) scrollToY(0)   // the first row: the page up, the clock and the widgets with it
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

    private fun shadeK() = conf.optInt("shade", HomeConfig.SHADE_DEFAULT).toFloat() / HomeConfig.SHADE_DEFAULT   // Backdrop.shade's: 1 the usual veil

    /** The background of [conf], when it is not the one on screen: a new choice, the rotation, a new photo, a new Bing picture. */
    private fun wall() {
        val id = conf.optString("wall")
        val key = id + (Walls.file(this, id)?.let { it.name + it.lastModified() } ?: "") + conf.optLong("photo") + conf.optInt("shade") + (if (HomeConfig.isVideo(id)) "${conf.optBoolean("vsound")}${conf.optInt("vvol")}" else "") + (if (id == HomeConfig.WEB) conf.optString("wurl") + conf.optString("wmode") + conf.optInt("wsnap") + conf.optBoolean("wkeep") else "")   // a photo put over the old file, another day's Bing picture: a new one
        if (key == wallShown) return
        wallShown = key
        if (trimmed) { picture = null; soft = null; return }          // away and short of memory: [untrim] brings it back
        loadWall(id)
    }

    /**
     * Puts background [id] on the [backdrop]: a gradient at once; a scene or a picture read / drawn off the main thread (only the
     * next one, the one before is let go) and then painted, faded in over the old one when [fade]. A photo that cannot be read
     * (deleted, Bing not fetched yet) leaves what stood in for it.
     */
    private fun loadWall(id: String) {
        val dm = resources.displayMetrics
        val colors = wallColors(id)
        val key = wallShown
        val url = conf.optString("wurl")
        val snap = id == HomeConfig.WEB && conf.optString("wmode") == "snap"   // the page as a picture: read and painted like a photo
        val again = snap && lastWall == HomeConfig.WEB                // the next picture of the page: the one on screen stays until it is read (no flash of the gradient every few minutes)
        lastWall = id
        backdrop.strength = shadeK()
        if (HomeConfig.isVideo(id)) attachVideo(id) else detachVideo()
        if (id == HomeConfig.WEB && !snap) {                          // a live page: no bitmap, so nothing to blur for the cards either (as a gradient)
            picture = null; soft = null
            Frost.show(null, dm.widthPixels, dm.heightPixels, live = true)
            backdrop.show(Backdrop.Look(colors))                      // the gradient is what shows while the page loads, and if it never does
            attachWeb()
            webDim?.background = WebWall.veil(shadeK())               // the same page, a new strength: attachWeb kept the page as it was
            return
        }
        if (snap) attachSnap() else {
            detachWeb()
            bg.execute { WebSnap.clear(this) }                        // not the web wall any more: its last picture is only a file taking room
        }
        if (!snap && !Walls.isPicture(id) && !WallArt.has(id)) {
            picture = null; soft = null
            Frost.show(null, dm.widthPixels, dm.heightPixels)
            val look = Backdrop.Look(colors)
            backdrop.show(look)
            return
        }
        if (!again) backdrop.show(Backdrop.Look(colors))              // its colours stand in while it is made
        bg.execute {
            val big = if (snap) Wall.load(WebSnap.file(this, url), dm.widthPixels, dm.heightPixels) else Walls.bitmap(this, id, dm.widthPixels, dm.heightPixels)
            val small = if (snap) big?.let { Wall.soft(it) } else Walls.soft(this, id, big, dm.widthPixels, dm.heightPixels)
            runOnUiThread {
                if (isDestroyed || trimmed || key != wallShown) return@runOnUiThread   // another choice came meanwhile: its own load shows
                if ((snap || Walls.isPicture(id)) && big == null) return@runOnUiThread   // no picture yet: the gradient stays
                picture = big; soft = small
                val look = if (big != null) Backdrop.Look(colors, big) else Backdrop.Look(colors, null, id)
                backdrop.show(look)
                val moving = HomeConfig.isVideo(id)                      // the cards' blurred copy is still, the video is not: translucent cards like over the live page
                Frost.show(if (moving) null else small, dm.widthPixels, dm.heightPixels, live = moving)
            }
        }
    }

    private val videoRetry = Runnable { if (!isDestroyed && !trimmed && !away && HomeConfig.isVideo(conf.optString("wall"))) { detachVideo(); attachVideo(conf.optString("wall")) } }

    /** The player of a video wall behind the grid. A file the codec cannot play leaves the poster (a picture), tried again in 2 minutes. */
    private fun attachVideo(id: String) {
        val f = Videos.file(this, id).takeIf { it.isFile } ?: return detachVideo()   // deleted: the poster (or the gradient) stays
        if (Videos.off(this, id)) return detachVideo()                // too slow for this TV twice: the poster stays until the video is chosen again
        val root = scroll.parent as? ViewGroup ?: return
        val v = video ?: VideoWall(this, root, ui, { m -> videoMeasured(conf.optString("wall"), m) }) { detachVideo(); ui.postDelayed(videoRetry, 120_000) }.also { video = it }
        v.show(f, shadeK(), conf.optBoolean("vsound", false), conf.optInt("vvol", HomeConfig.VOL_DEFAULT))
        if (away) v.stop()                                            // made while away (a rotation, a phone): [onStart] plays it
    }

    /** 20 s of the video are measured: kept for the clients; slow twice in a row and the poster takes over (a still picture beats a stutter). */
    private fun videoMeasured(id: String, m: VideoWall.Measure) {
        val r = Videos.measured(this, id, m.fps, m.want, m.cpu, System.currentTimeMillis())
        if (r.optBoolean("off")) detachVideo()
    }

    private fun detachVideo() {
        ui.removeCallbacks(videoRetry)
        video?.detach(); video = null
    }

    private val webRetry = Runnable { if (!isDestroyed && !trimmed && web == null && conf.optString("wall") == HomeConfig.WEB && conf.optString("wmode") != "snap") attachWeb() }

    /** The page of the "web" wall behind the grid; a page that cannot load is taken away (the gradient shows, no dialog) and tried again in 2 minutes. */
    private fun attachWeb() {
        val url = conf.optString("wurl")
        if (web != null && webOf == url) return
        detachWeb()
        val root = scroll.parent as? ViewGroup ?: return
        val v = WebWall.make(this, url, { detachWeb(); ui.postDelayed(webRetry, 120_000) }) ?: return
        val dim = WebWall.dim(this, shadeK())
        root.addView(v, 0, FrameLayout.LayoutParams(-1, -1))
        root.addView(dim, 1, FrameLayout.LayoutParams(-1, -1))
        web = v; webDim = dim; webOf = url
    }

    /** The snapshots of the page every `wsnap` minutes; kept as it is while the choice is the same (a change of the wall does not start the wait again). */
    private fun attachSnap() {
        val url = conf.optString("wurl")
        val every = conf.optInt("wsnap", HomeConfig.WSNAP_DEFAULT) * 60_000L
        val keep = conf.optBoolean("wkeep", false)
        detachWeb(snap = false)                                       // a live page gives way to the pictures
        val now = snapper
        if (now != null && now.sig == "$url|$every|$keep") return
        now?.stop()
        snapper = WebSnapper(this, url, every, keep, { scroll.parent as? ViewGroup }, ui, bg) {
            if (!isDestroyed && !trimmed && conf.optString("wall") == HomeConfig.WEB) { wallShown = ""; wall() }   // a new picture: read and painted
        }.also { if (!away) it.resume() }                             // away: [onStart] starts it
    }

    /** Everything of the web wall let go: the page (and its dim), the picture-taker and its page; [snap] false leaves the picture-taker. */
    private fun detachWeb(snap: Boolean = true) {
        webDim?.let { (it.parent as? ViewGroup)?.removeView(it) }
        ui.removeCallbacks(webRetry)
        WebWall.stop(web)
        web = null; webDim = null; webOf = ""
        if (snap) { snapper?.stop(); snapper = null }
    }

    // ---- turning through the set ----

    private val wallTick = Runnable { Store.advance(this, false); wallTimer() }

    /** The one timer of the rotation, set while Home is on screen only (nothing runs when it is not); a change sets it again. */
    private fun wallTimer() {
        ui.removeCallbacks(wallTick)
        val every = conf.optString("wevery")
        val walls = conf.optJSONArray("walls")?.let { a -> List(a.length()) { a.optString(it) } } ?: return
        if (every == "off" || every == "show" || !Store.turns(this, walls)) return
        val now = System.currentTimeMillis()
        val wait = HomeConfig.wait(every, Store.last(this), now, java.util.TimeZone.getDefault().getOffset(now).toLong()) ?: return
        ui.postDelayed(wallTick, if (wait <= 0L) 600_000L else wait)  // due but nothing else to show (a photo gone, Bing not here yet): look again in 10 minutes
    }

    /** Bing's pictures when they are shown or turned to ([Online.wanted] keeps it to every few hours, never for anything else). */
    private fun bingCheck() {
        val a = conf.optJSONArray("walls") ?: return
        if ((0 until a.length()).none { a.optString(it) == HomeConfig.BING } || !Online.wanted(this)) return
        Thread { if (Online.refresh(this)) runOnUiThread { if (!isDestroyed) wall() } }.start()
    }

    /** The Wi-Fi's name; null without the location grant (a client gives it over ADB) or with location off: then just "Wi-Fi". */
    private fun ssid(): String? = try {
        @Suppress("DEPRECATION") (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo?.ssid
            ?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    } catch (e: Exception) { null }

    private fun setupThere() = try { packageManager.getPackageInfo("dev.tvremote.setup", 0); true } catch (e: Exception) { false }

    /**
     * Without TV Setup (it runs the power actions: TV Home has no ADB) the button stays and says how to get it: a button that
     * vanished read as a bug. Downloader opens when the TV has it; the code is the one the site gives for TV Setup.
     */
    private fun needSetup(): List<Drop.Row> {
        val dl = packageManager.getLaunchIntentForPackage("com.esaba.downloader")
        return listOf(
            Drop.Row(R.drawable.ic_info, getString(R.string.power_needs_setup)) { },
            Drop.Row(R.drawable.ic_download, getString(R.string.power_setup_code, SETUP_CODE)) { if (dl != null) try { startActivity(dl) } catch (e: Exception) { } },
            Drop.Row(R.drawable.ic_phone, getString(R.string.power_setup_phone)) { })
    }

    /** The power button shows unless Customize switched it off; the settings button is then the row's right end again. */
    private fun powerBar() {
        val on = conf.optBoolean("power", true)
        powerPill.visibility = if (on) View.VISIBLE else View.GONE
        settingsPill.nextFocusRightId = if (on) powerPill.id else settingsPill.id
        bar.requestLayout()
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
        dateLine.text = day
        greetLine.text = greet
        greetLine.visibility = if (conf.optBoolean("greet", true)) View.VISIBLE else View.GONE
        @Suppress("DEPRECATION") val net = (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo
        val (label, off) = @Suppress("DEPRECATION") when {
            net == null || !net.isConnected -> getString(R.string.offline) to true
            net.type == ConnectivityManager.TYPE_ETHERNET -> getString(R.string.cable) to false
            else -> (ssid() ?: getString(R.string.wifi)) to false
        }
        netPill.label = label; netPill.contentDescription = label; netPill.alpha = if (off) 0.5f else 1f
        netPill.setImageResource(if (net?.type == ConnectivityManager.TYPE_ETHERNET) R.drawable.ic_globe else R.drawable.ic_wifi)
        bar.relabel(netPill)
    }

    // ---- what the buttons and the menu do ----

    /** The long-press / Menu menu of an app: the glass panel of Customize ([TvGlass.menu]), one row per action with its line icon. */
    private fun menu(a: App) {
        val acts = ArrayList<Triple<Int, Int, () -> Unit>>()          // icon, words, what it does
        if (a.pin != null && !a.grid) {                               // a pinned shortcut: only taken away here; its app decides the rest
            acts.add(Triple(R.drawable.ic_close, R.string.unpin) { Pins.remove(this, a); pins = pins.filter { it !== a }; shownKey = ""; show() })
            acts.add(Triple(R.drawable.ic_sliders, R.string.customize) { customize() })
            if (!a.live) acts.add(Triple(R.drawable.ic_pen, R.string.rename) { ui.post { rename(a) } })
        } else {                                                      // no "add to / take from the favourites": they are only the first places, and Move puts an app there
            acts.add(Triple(R.drawable.ic_sliders, R.string.customize) { customize() })
            acts.add(Triple(R.drawable.ic_expand, R.string.move) { ui.post { startMove(a.pkg) } })
            if (!a.live) acts.add(Triple(R.drawable.ic_pen, R.string.rename) { ui.post { rename(a) } })
            acts.add(Triple(R.drawable.ic_eye_off, R.string.hide) { Store.change(this, JSONObject().put("hidden", org.json.JSONArray(Store.list(this, "hidden") + a.pkg))
                .put("dock", org.json.JSONArray(Store.list(this, "dock") - a.pkg))) })
            val powerApp = conf.optBoolean("autoon") && conf.optString("autoapp") == a.pkg
            acts.add(Triple(R.drawable.ic_power, if (powerApp) R.string.auto_off else R.string.auto_on) {   // the app that opens when the TV turns on: TV Setup does it (Keys.push)
                Store.change(this, if (powerApp) JSONObject().put("autoon", false) else JSONObject().put("autoapp", a.pkg).put("autoon", true))
                if (!Keys.setup(this)) toast(R.string.auto_needs_setup)   // saved anyway: it works the day TV Setup is there
            })
            acts.add(Triple(R.drawable.ic_sparkle, if (Motion.on) R.string.anim_off else R.string.anim_on) { Store.change(this, JSONObject().put("anim", !conf.optBoolean("anim", true))) })
            if (a.grid) acts.add(Triple(R.drawable.ic_trash, R.string.tile_remove) {   // off the grid: it leaves "tiles" and its places in the dock, the order and the hidden list
                fun without(k: String) = org.json.JSONArray(Store.list(this, k) - a.pkg)
                Store.change(this, JSONObject().put("tiles", org.json.JSONArray(Store.list(this, "tiles") - a.pkg)).put("dock", without("dock")).put("hidden", without("hidden")).put("order", without("order")))
            }) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))
                .takeIf { packageManager.resolveActivity(it, 0) != null }   // a CVTE TV has no App info page: no entry rather than one that fails (cleanyourtv #1202)
                ?.let { i -> acts.add(Triple(R.drawable.ic_info, R.string.info) { try { startActivity(i) } catch (e: Exception) { toast(R.string.no_open) } }) }
            val removable = try { packageManager.getApplicationInfo(a.pkg, 0).let { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 || (it.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 } } catch (e: Exception) { false }
            if (a.pkg != packageName && removable) Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg))
                .takeIf { packageManager.resolveActivity(it, 0) != null }   // the same CVTE gap: no installer page, no entry
                ?.let { i -> acts.add(Triple(if (a.grid) R.drawable.ic_close else R.drawable.ic_trash, R.string.uninstall) { try { startActivity(i) } catch (e: Exception) { toast(R.string.no_open) } }) }   // the trash is "remove from grid" there; the list follows PACKAGE_REMOVED
        }
        open = TvGlass.menu(this, R.style.Menu, a.label, acts.map { TvGlass.Item(it.first, getString(it.second), it.third) }) { it.typeface = if (it.typeface?.isBold == true) Font.bold else Font.regular }
    }

    /** Rename [a]: a field with the TV's keyboard, prefilled with the name shown; "Original name" takes the person's name away. */
    private fun rename(a: App) {
        val box = dev.tvremote.tvui.TvField(this).apply {
            Font.on(this); textSize = 16f; isSingleLine = true; maxLines = 1
            filters = arrayOf(android.text.InputFilter.LengthFilter(HomeConfig.NAME_MAX))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setText(a.label); setSelection(text.length)
        }
        fun save(name: String) {
            val g = JSONObject(conf.optJSONObject("names")?.toString() ?: "{}")
            if (name.isEmpty() || name == a.orig) g.remove(a.pkg) else g.put(a.pkg, name)
            Store.change(this, JSONObject().put("names", g))
        }
        val b = android.app.AlertDialog.Builder(this, R.style.Menu).setTitle(R.string.rename_q)
            .setView(FrameLayout(this).apply { setPadding(dp(24), dp(4), dp(24), 0); addView(box) })
            .setPositiveButton(R.string.ok) { _, _ -> save(box.text.toString().trim()) }.setNegativeButton(android.R.string.cancel, null)
        if (a.renamed) b.setNeutralButton(R.string.rename_orig) { _, _ -> save("") }
        val dlg = b.create()
        box.setOnEditorActionListener { _, _, _ -> save(box.text.toString().trim()); dlg.dismiss(); true }
        open = dlg
        dlg.show()
    }

    private var panel: android.app.Dialog? = null
    private fun customize(at: String? = null) { open = Customize.show(this, at).also { panel = it } }

    /** The remote's Menu key anywhere but on an app (an app's own Menu opens its menu) opens the settings. */
    override fun onKeyUp(code: Int, e: KeyEvent): Boolean {
        if (locked) return super.onKeyUp(code, e)
        if (code == KeyEvent.KEYCODE_MENU) { customize(); return true }
        // The Input key, where it reaches the home (cleanyourtv #342: some Realtek remotes send TV_INPUT, others TV). A box whose
        // system sends it to Google's GlobalKeyReceiver (the FPT Play box) never gives it to any app
        if (code == KeyEvent.KEYCODE_TV_INPUT || code == KeyEvent.KEYCODE_TV) { inputs(); return true }
        return super.onKeyUp(code, e)
    }

    private fun unhide() {
        val hidden = Store.list(this, "hidden")
        val here = apps.filter { it.pkg in hidden }
        open = TvGlass.menu(this, R.style.Menu, getString(R.string.show), here.map { h -> TvGlass.Item(0, h.label) { Store.putList(this, "hidden", hidden - h.pkg) } }) { it.typeface = if (it.typeface?.isBold == true) Font.bold else Font.regular }
    }

    /**
     * The TV's inputs as the TV shows them (cleanyourtv's launcher reads them the same way): the names the owner gave them, the
     * hidden ones left out, a device on HDMI-CEC folded into its port, "Connected" where the TV knows, HDMI first, then AV and
     * the like, the tuner last. The TV's own settings when it lists none.
     */
    private fun inputs() {
        val tim = try { getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager } catch (e: Exception) { null }
        val list: List<TvInputInfo> = try { tim?.tvInputList.orEmpty() } catch (e: Exception) { emptyList() }
        fun hidden(i: TvInputInfo) = try { i.isHidden(this) } catch (e: Exception) { false }
        fun custom(i: TvInputInfo) = try { i.loadCustomLabel(this)?.toString()?.trim().orEmpty() } catch (e: Exception) { "" }
        fun state(id: String) = try { tim?.getInputState(id) ?: -1 } catch (e: Exception) { -1 }
        val ids = list.map { it.id }.toSet()
        val top = list.filter { (it.parentId == null || it.parentId !in ids) && !hidden(it) }
        val devices = list.filter { it.isPassthroughInput && it.parentId in ids && !hidden(it) }.groupBy { it.parentId }
        val ports = top.filter { it.isPassthroughInput && it.type == TvInputInfo.TYPE_HDMI }
        // some TVs say every HDMI port is connected: then it means nothing
        val bogus = ports.size > 1 && ports.all { state(it.id) == TvInputManager.INPUT_STATE_CONNECTED }
        val rows = top.map { i ->
            val hdmi = i.isPassthroughInput && i.type == TvInputInfo.TYPE_HDMI
            val base = custom(i).ifEmpty { i.loadLabel(this).toString() }
            val dev = devices[i.id]?.firstNotNullOfOrNull { d -> custom(d).ifEmpty { d.loadLabel(this).toString() }.takeIf { it.isNotBlank() && it != base } }
            val connected = !(hdmi && bogus) && (state(i.id) == TvInputManager.INPUT_STATE_CONNECTED || devices[i.id].orEmpty().any { state(it.id) == TvInputManager.INPUT_STATE_CONNECTED })
            val group = if (hdmi) 0 else if (i.isPassthroughInput) 1 else 2
            Drop.Row(if (group == 2) R.drawable.ic_tv else if (hdmi) R.drawable.ic_input else R.drawable.ic_plug,
                if (dev != null) "$base · $dev" else base, if (connected && group < 2) getString(R.string.connected) else null, group) {
                val uri = if (i.isPassthroughInput) TvContract.buildChannelUriForPassthroughInput(i.id) else TvContract.buildChannelsUriForInput(i.id)
                try { startActivity(InputOpen.intent(this, uri)) } catch (e: Exception) { toast(R.string.no_open) }
            }
        }.sortedWith(compareBy<Drop.Row> { it.group }.thenBy(Nat) { it.label })
        if (rows.isEmpty()) { toast(R.string.no_inputs); open(Settings.ACTION_SETTINGS); return }
        open?.dismiss()
        open = Drop.show(this, bar.capsule, rows)
    }

    /** "HDMI 2" before "HDMI 10". */
    private object Nat : Comparator<String> {
        override fun compare(a: String, b: String): Int {
            val n = Regex("\\d+|\\D+")
            val x = n.findAll(a.lowercase()).map { it.value }.toList(); val y = n.findAll(b.lowercase()).map { it.value }.toList()
            for (k in 0 until minOf(x.size, y.size)) {
                val c = if (x[k][0].isDigit() && y[k][0].isDigit()) (x[k].toBigInteger()).compareTo(y[k].toBigInteger()) else x[k].compareTo(y[k])
                if (c != 0) return c
            }
            return x.size - y.size
        }
    }

    private fun open(action: String) {
        try { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e2: Exception) { toast(R.string.no_open) } }
    }

    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_SHORT).show()

    private fun text(size: Float, color: Int) = Font.on(TextView(this)).apply { textSize = size; setTextColor(color); shadow(this) }

    private fun shadow(t: TextView) = t.setShadowLayer(dp(6).toFloat(), 0f, dp(1).toFloat(), 0x80000000.toInt())

    /** Never scrolls by itself to the focused view: [tile] and the buttons say where to, so a row is not pressed to the edge. */
    private class Scroller(c: Context, private val moved: () -> Unit) : ScrollView(c) {
        override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?) = 0
        override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) { super.onScrollChanged(l, t, oldl, oldt); moved() }
    }

    /** A column whose alpha is applied to each child, not through a layer the size of the whole grid. */
    private class Flat(c: Context) : LinearLayout(c) { override fun hasOverlappingRendering() = false }

}

/**
 * One app: its banner (or icon on a colour, with the name; or, for a live tile, its own render: [LiveFace]), the name under it shown while focused. Focus grows it and rings it
 * ([TvTile]: the look is tvui's, TV Home's own moved there). [app] null: the "hidden apps" tile.
 */
class Tile(c: Context, val app: App?, w: Int, label: String, dp: (Number) -> Int, say: (String) -> String = { it }) : TvTile(c, w, dp) {
    private val content: View
    private var pic: ImageView? = null                              // the picture, so it can be let go and put back ([release])
    private val badge = BadgeView(c, dp)
    private var shownMark: Badges.Mark? = null

    init {
        Font.on(name)
        val h = w * 9 / 16
        val banner = app?.banner
        val liveFace = if (app?.live == true) LiveFace(c, w, h, label, say).also { it.set(Tiles.cached(app.pkg), animate = false) } else null
        faceColor(if (banner != null) 0xFF1E2230.toInt() else shade(app?.pkg ?: ""))
        if (liveFace != null) {                                       // a live tile draws itself ([Tiles]); its state comes from [live]
            content = liveFace
            fill(content)
        } else if (banner != null) {
            content = ImageView(c).apply { setImageDrawable(banner); scaleType = ImageView.ScaleType.CENTER_CROP }
            pic = content as ImageView
            fill(content)
        } else {
            val inner = LinearLayout(c).apply { orientation = VERTICAL; gravity = Gravity.CENTER; setPadding(dp(6), dp(4), dp(6), dp(4)) }
            inner.addView(ImageView(c).apply {
                if (app?.icon != null) { setImageDrawable(app.icon); pic = this } else { setImageResource(R.drawable.ic_eye_off); imageTintList = ColorStateList.valueOf(Color.WHITE) }
            }, LayoutParams(h * 45 / 100, h * 45 / 100))
            inner.addView(Font.on(TextView(c)).apply {
                text = label; textSize = 12f; setTextColor(Color.WHITE); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            }, LayoutParams(-1, -2))
            fill(inner)
            content = inner
        }
        face.addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(6), dp(6), 0) })
        name.text = if (banner != null && app?.renamed != true) label else ""                 // a tile without a banner already shows its name inside
        if (banner != null && app?.renamed == true) face.addView(Font.on(TextView(c)).apply {   // the person's own name stays on a banner (two YouTubes look the same), not only while focused
            text = label; textSize = 11f; setTextColor(Color.WHITE); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(7), dp(2), dp(7), dp(3)); typeface = Font.medium
            background = GradientDrawable().apply { setColor(Customize.NAME_SCRIM); cornerRadius = dp(10).toFloat() }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(6), 0, dp(6), dp(6)) })
    }

    /** A live tile's newest state from its provider (null: no answer); [animate] when motion is on and not calm. */
    fun live(s: org.json.JSONObject?, animate: Boolean) { (content as? LiveFace)?.set(s, animate) }

    /** The picture let go (memory is short while the screen is hidden) and put back; the tile keeps its place and its colour meanwhile. */
    fun release() { pic?.setImageDrawable(null) }
    fun restore() { pic?.setImageDrawable(app?.banner ?: app?.icon) }

    /** The lock or the time left on the corner; nothing when [m] is null. Set only when it differs, so nothing is laid out for the same mark. */
    fun mark(m: Badges.Mark?, label: (Int) -> String) {
        if (m == shownMark) return
        shownMark = m
        badge.set(m, m?.let { if (it.locked || it.power) "" else label(it.minutes) } ?: "")
    }

    /** A dark colour of its own per app, for those without a TV banner. */
    private fun shade(pkg: String): Int {
        val hues = intArrayOf(0xFF27324A.toInt(), 0xFF2E3B2F.toInt(), 0xFF43293A.toInt(), 0xFF2B3A45.toInt(), 0xFF3D3424.toInt(), 0xFF2F2D4A.toInt())
        return hues[(pkg.hashCode() and 0x7fffffff) % hues.size]
    }
}

const val DIM = 0.35f                                                 // the tiles that are not being moved
/** The most rows of apps the first screen can show (to tenths), as the last layout measured; 0 = not measured yet. */
@Volatile var rowsFit = 0.0

const val GRID_GAP = 32                                               // dp between tiles, across and down: room under a focused tile for its name
private const val HEAD_DIM = 0xB3FFFFFF.toInt()                       // the greeting over the day
private const val HEAD_RULE = 0x40FFFFFF                             // the line between the clock and the day
private const val POWER_INK = 0xFF7EE0A1.toInt()                    // the power mark: the green of "on", apart from the lock's red and the limit's amber

/**
 * A pill on a tile's corner: a lock, an hourglass and the time left, or the power mark of the app opened at power-on. Drawn by hand with the paints and icons made once, and
 * measured only when the mark changes: nothing is allocated while it is drawn (or while the tile grows).
 */
class BadgeView(c: Context, private val dp: (Number) -> Int) : View(c) {
    private val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6101420.toInt() }
    private val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = dp(13).toFloat(); typeface = Font.medium
    }
    private val lock = c.getDrawable(R.drawable.ic_lock)!!.mutate().also { it.setTint(0xFFFF8A80.toInt()) }
    private val glass = c.getDrawable(R.drawable.ic_hourglass)!!.mutate().also { it.setTint(0xFFFFD27A.toInt()) }
    private val power = c.getDrawable(R.drawable.ic_power)!!.mutate().also { it.setTint(POWER_INK) }
    private val box = android.graphics.RectF()
    private var icon: Drawable = lock
    private var label = ""
    private var textW = 0f

    init { visibility = GONE }

    fun set(m: Badges.Mark?, label: String) {
        if (m == null) { visibility = GONE; return }
        icon = if (m.locked) lock else if (m.power) power else glass
        this.label = label
        textW = if (label.isEmpty()) 0f else text.measureText(label)
        visibility = VISIBLE
        contentDescription = if (m.locked) context.getString(R.string.b_locked) else if (m.power) context.getString(R.string.b_power) else label
        requestLayout(); invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(if (label.isEmpty()) dp(24) else (dp(8) + dp(15) + dp(4) + textW + dp(8)).toInt(), dp(24))   // an icon alone: a circle
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(box, height / 2f, height / 2f, bgPaint)
        val s = dp(15); val x = if (label.isEmpty()) (width - s) / 2 else dp(8); val y = (height - s) / 2
        icon.setBounds(x, y, x + s, y + s); icon.draw(canvas)
        if (label.isNotEmpty()) canvas.drawText(label, (x + s + dp(4)).toFloat(), height / 2f - (text.ascent() + text.descent()) / 2f, text)
    }
}

/** TV Home's settings into tvui's [Motion] (the object lives there, shared): "anim", "motion" and "focus" of the config. */
fun Motion.read(c: Context, conf: JSONObject) = read(c, conf.optBoolean("anim", true), conf.optString("motion"), conf.optString("focus", "glass"))

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
    else -> if (WallArt.has(wall)) WallArt.colors(wall) else intArrayOf(0xFF0B0F1A.toInt(), 0xFF18223F.toInt(), 0xFF0B0F1A.toInt())
}

/** For the screensaver, whose screen rarely changes; the home screen paints its wallpaper once on a [Backdrop] instead. */
fun wallDrawable(colors: IntArray): Drawable =
    if (colors.all { it == colors[0] }) ColorDrawable(colors[0]) else GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { setDither(true) }
