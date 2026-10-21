package dev.tvremote.games

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.io.File

/**
 * The launcher screen, Apple TV style: clean pictures in rows (favourites, the person's folders, all games, suggestions), the focused one
 * rises with TV Home's ring and shows its name and a quiet line (players, genre, system) under it. A search field and filter pills sit on
 * top; OK held opens a menu (favourite, folders, pin to TV Home). A "+" tile shows the "scan to add" QR.
 */
class LibraryActivity : ComponentActivity() {
    private lateinit var server: GameServer
    private lateinit var root: FrameLayout
    private var overlay: View? = null
    private val art = java.util.WeakHashMap<View, Bitmap>()           // a tile's picture, for the backdrop
    private val artById = HashMap<String, Bitmap>()                   // the pictures by game id, for the pins

    /** One game as the library sees it: what it is, what it takes, and what OK does. */
    private class Item(
        val id: String, val name: String, val sysLabel: String, val sysKey: String, val color: Int, val src: Thumbs.Src?,
        val max: Int, val min: Int,                                   // players; max 0 = not known
        val genre: String,
        val shelf: Boolean,                                           // can be a favourite / in a folder (a game here to play, not a download)
        val play: (() -> Unit)? = null,                               // null: a suggestion to download
        val rom: Pair<org.json.JSONObject, Systems.Sys>? = null,
        val web: String? = null,                                      // a link the person added: the menu can remove it
        val hideId: String? = null,                                   // a suggestion: the menu can hide it
    )

    private var lib: List<Item> = emptyList()
    private var sugs: List<Item> = emptyList()
    private var query = ""
    private var fPlayers: Int? = null
    private var fSystem: String? = null
    private var fGenre: String? = null
    private var sections: LinearLayout? = null
    private var searchBox: EditText? = null
    private val pills = ArrayList<Pair<() -> Boolean, TextView>>()
    private val tileKeys = HashMap<String, View>()                    // "<section>|<id>" -> its tile
    private var focusedKey: String? = null
    private var lastFocus: View? = null
    private var wantFolder: String? = null                            // a tvgames://folder/<id> link: its row takes the focus once drawn
    private var scroller: ScrollView? = null
    private var scrollAnim: android.animation.ValueAnimator? = null
    private val en get() = java.util.Locale.getDefault().language != "vi"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.tv_bg)) }
        setContentView(root)
        server = GameServer.acquire(this)
        intent.getStringExtra("cat_base")?.let { Catalog.setDebug(this, it, intent.getStringExtra("cat_key")) }   // debug builds only
        firstRun()
        val prefs = getSharedPreferences("games", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT in 23..32 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            && !prefs.getBoolean("asked", false)) {
            prefs.edit().putBoolean("asked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
        reload()
        link(intent)
        refreshCatalog()
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); setIntent(i); link(i) }

    /** A pinned shortcut: tvgames://play/<id> starts that game (through its lobby, as from the library), tvgames://folder/<id> shows that folder. */
    private fun link(i: Intent?) {
        val u = i?.data ?: return
        i.data = null
        Pins.playId(u)?.let { id ->
            val it = (lib + sugs).firstOrNull { x -> x.id == id && x.play != null }
            if (it != null) it.play!!() else android.widget.Toast.makeText(this, getString(R.string.pin_gone), android.widget.Toast.LENGTH_LONG).show()
        }
        Pins.folderId(u)?.let { id -> wantFolder = id; clearFilters(false); renderSections() }
    }

    /** The site's list (a day old at most) in the background; the library is drawn again when it changed. */
    private fun refreshCatalog() {
        Thread { if (Catalog.refresh(applicationContext, intent.hasExtra("cat_base"))) runOnUiThread { if (!isDestroyed && overlay == null && dl == null && searchBox?.hasFocus() != true) reload() } }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(code: Int, p: Array<String>, r: IntArray) { super.onRequestPermissionsResult(code, p, r); reload() }

    /** The bundled homebrew goes to files/roms once, so the library is never empty; deleting it sticks. */
    private fun firstRun() {
        val prefs = getSharedPreferences("games", MODE_PRIVATE)
        if (prefs.getBoolean("seeded", false)) return
        try {
            assets.open("homebrew/roborun.nes").use { i -> File(Systems.romsDir(this), "Robo Run (homebrew).nes").outputStream().use { i.copyTo(it) } }
            prefs.edit().putBoolean("seeded", true).apply()
        } catch (e: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        server.inGame = false; server.webMode = false; server.clearLayouts(); server.setSystem("nes")
        server.onKey = { _, _, _ -> }
        server.onPlayers = { }
        server.onAdded = { runOnUiThread { if (dl == null && overlay == null) reload() } }
        if (overlay == null && searchBox?.hasFocus() != true) reload()
    }

    override fun onDestroy() { GameServer.release(); super.onDestroy() }

    private fun filtering() = query.isNotBlank() || fPlayers != null || fSystem != null || fGenre != null

    /** Back: first a closed card, then a cleared search / filter, then the launcher. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (overlay != null) closeOverlay() else if (filtering()) clearFilters(true) else super.onBackPressed() }

    private fun clearFilters(render: Boolean) {
        query = ""; fPlayers = null; fSystem = null; fGenre = null
        searchBox?.setText(""); pills.forEach { it.second.isSelected = it.first() }
        if (render) renderSections()
    }

    /** The Add-game card answers Back / Escape / a pad's B itself (down and up both eaten), so none of them reaches the launcher. */
    override fun dispatchKeyEvent(ev: android.view.KeyEvent): Boolean {
        if (dl != null && ev.keyCode == android.view.KeyEvent.KEYCODE_BACK) { if (ev.action == android.view.KeyEvent.ACTION_UP) cancelDl(); return true }
        if (overlay != null && (ev.keyCode == android.view.KeyEvent.KEYCODE_BACK || ev.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE || ev.keyCode == android.view.KeyEvent.KEYCODE_BUTTON_B)) {
            if (ev.action == android.view.KeyEvent.ACTION_UP) closeOverlay()
            return true
        }
        return super.dispatchKeyEvent(ev)
    }

    private fun closeOverlay() {
        overlay?.let { root.removeView(it) }; overlay = null
        scroller?.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        lastFocus?.takeIf { it.isAttachedToWindow }?.requestFocus()
    }

    // ---- the data

    private fun genreOf(e: org.json.JSONObject?, fallback: String) = Browse.genre(e?.opt("genre"), en) ?: fallback

    private fun buildItems() {
        val games = Systems.scan(this)
        val webs = Systems.webGames(this)
        val cat = Catalog.peek(this)
        val prefs = getSharedPreferences("games", MODE_PRIVATE)
        val hidden = prefs.getStringSet("hidden", emptySet()) ?: emptySet()
        val other = getString(R.string.genre_other)
        val have = games.map { it.name.lowercase() }.toSet()
        val stems = games.map { it.file.nameWithoutExtension.removeSuffix(" (homebrew)").lowercase() }.toSet()
        val shas = games.mapNotNull { shaOf(it.file) }.toSet()      // the same file under any name
        fun installed(h: org.json.JSONObject): Boolean {            // by sha, name, the file name in its address, or the file this app saved for its id
            val nm = h.optString("name").replace(Regex("[^A-Za-z0-9 ._-]"), "").lowercase()
            val fromUrl = h.optString("url").substringAfterLast('/').substringBeforeLast('.').lowercase()
            val saved = prefs.getString("got:" + h.optString("id"), null)
            val sha = h.optString("sha256").lowercase()
            return (sha.isNotEmpty() && sha in shas) || nm in stems || h.optString("name").lowercase() in have || (fromUrl.isNotEmpty() && fromUrl in stems) || (saved != null && File(Systems.romsDir(this), saved).exists())
        }
        val l = ArrayList<Item>()
        for (g in games) {
            val pc = Catalog.players(this, g.file.name, null, Systems.maxPlayers(g.sys.id))
            l += Item("g:" + g.file.name, g.name, g.sys.label, g.sys.id, g.sys.color, Thumbs.catalog(this, g.file.name) ?: Thumbs.src(this, g.sys, g.file.name),
                pc.first, pc.second, genreOf(Catalog.entryOf(this, g.file.name, null), other), true,
                play = { try { startActivity(Intent(this, GameActivity::class.java).putExtra("path", g.file.path)) } catch (e: Exception) { } })
        }
        for (w in webs) {
            val e = if (w.url.startsWith("builtin:")) null else Catalog.entryOf(this, null, w.url)
            val pc = if (e != null) Catalog.players(this, null, w.url, 4) else 0 to 1
            l += Item("w:" + w.url, w.name, "Web", "web", 0xFF0891B2.toInt(), webSrc(w.url, null), pc.first, pc.second, genreOf(e, other), true,
                play = { try { startActivity(Intent(this, WebGameActivity::class.java).putExtra("name", w.name).putExtra("url", w.url)) } catch (e: Exception) { } },
                web = w.url.takeIf { !it.startsWith("builtin:") })
        }
        l.sortBy { it.name.lowercase() }
        lib = l
        val s = ArrayList<Item>()
        val feat = (0 until (cat.optJSONArray("web")?.length() ?: 0)).mapNotNull { cat.optJSONArray("web")?.optJSONObject(it) }
            .filter { it.optBoolean("on", true) && it.optString("url").matches(Regex("https?://.+")) && "w:" + it.optString("id") !in hidden }
        for (w in feat) {
            val nm = w.optJSONObject("name")?.let { it.optString(if (en) "en" else "vi").ifEmpty { it.optString("en") } } ?: w.optString("id")
            val url = w.optString("url")
            s += Item("w:$url", nm, "Web", "web", 0xFF0891B2.toInt(), webSrc(url, w.optString("img")), w.optInt("players", 4).coerceIn(1, 4), w.optInt("min", 1),
                genreOf(w, other), true,
                play = { try { startActivity(Intent(this, WebGameActivity::class.java).putExtra("name", nm).putExtra("url", url).putExtra("pad", w.optString("pad", "web"))) } catch (e: Exception) { } },
                hideId = "w:" + w.optString("id"))
        }
        val brew = (0 until (cat.optJSONArray("homebrew")?.length() ?: 0)).mapNotNull { cat.optJSONArray("homebrew")?.optJSONObject(it) }
            .filter { it.optBoolean("on", true) && "h:" + it.optString("id") !in hidden && !installed(it) && Systems.list(this).any { s -> s.id == it.optString("system") } }
        for (h in brew) {
            val sy = Systems.list(this).first { it.id == h.optString("system") }
            s += Item("h:" + h.optString("id"), h.optString("name"), sy.label, sy.id, sy.color, Thumbs.Src("h:" + h.optString("id"), img = h.optString("img").takeIf { it.isNotEmpty() }),
                h.optInt("players", Systems.maxPlayers(sy.id)).coerceIn(1, 4), h.optInt("min", 1), genreOf(h, other), false, rom = h to sy, hideId = "h:" + h.optString("id"))
        }
        s.sortBy { it.name.lowercase() }
        sugs = s
    }

    // ---- the screen

    private fun reload() {
        if (!::root.isInitialized) return
        overlay = null
        root.removeAllViews(); pills.clear(); tileKeys.clear()
        buildItems()
        if (lib.isEmpty() && sugs.isEmpty()) {
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                addView(addPanel(server))
                addView(rotateButton(server) { reload() })
                addView(label(getString(R.string.empty_hint), 16f, 0xFFAAB0C0.toInt()).apply { setPadding(dp(80), dp(18), dp(80), 0) })
            }, FrameLayout.LayoutParams(-1, -1))
            return
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        sections = box
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(resources.getDimensionPixelSize(R.dimen.tv_margin_x), resources.getDimensionPixelSize(R.dimen.tv_margin_y), resources.getDimensionPixelSize(R.dimen.tv_margin_x), dp(32)); clipChildren = false; clipToPadding = false
            addView(header())
            addView(filterRow())
            addView(box)
            addView(label(getString(R.string.menu_hint), 14f, 0xFF6B7385.toInt()).apply { gravity = Gravity.START; setPadding(dp(8), dp(2), 0, 0) })
        }
        scroller = object : ScrollView(this) {
            // the default "scroll just enough to show it" would fight centreFocused(), which does the scrolling
            override fun requestChildRectangleOnScreen(child: View, rectangle: android.graphics.Rect, immediate: Boolean) = false
        }.apply { addView(col); clipChildren = false; clipToPadding = false; isSmoothScrollingEnabled = false }
        root.addView(scroller, FrameLayout.LayoutParams(-1, -1))
        renderSections()
    }

    /** The app's mark and name, the search field on the right. */
    private fun header(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), 0, dp(8), dp(12))
        addView(android.widget.ImageView(context).apply {
            setImageResource(R.drawable.icon); scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            outlineProvider = object : android.view.ViewOutlineProvider() { override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, resources.getDimension(R.dimen.tv_head_radius)) } }
            clipToOutline = true
        }, LinearLayout.LayoutParams(resources.getDimensionPixelSize(R.dimen.tv_head_icon), resources.getDimensionPixelSize(R.dimen.tv_head_icon)).apply { marginEnd = resources.getDimensionPixelSize(R.dimen.tv_head_gap) })
        addView(label(getString(R.string.app_name), 24f, getColor(R.color.tv_ink)).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL; typeface = android.graphics.Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, -2, 1f))
        val f = EditText(context).apply {
            hint = getString(R.string.search_hint); setHintTextColor(0x99FFFFFF.toInt()); setTextColor(Color.WHITE); textSize = 18f
            setText(query); setSingleLine(true); imeOptions = EditorInfo.IME_ACTION_SEARCH; inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(dp(20), 0, dp(20), 0); gravity = Gravity.CENTER_VERTICAL
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { setColor(0x40FFFFFF); cornerRadius = dp(30).toFloat(); setStroke(dp(2), 0xCCFFFFFF.toInt()) })
                addState(intArrayOf(), GradientDrawable().apply { setColor(0x24FFFFFF); cornerRadius = dp(30).toFloat() })
            }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(e: Editable?) { val q = e?.toString() ?: ""; if (q != query) { query = q; renderSections() } }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            setOnEditorActionListener { v, _, _ ->
                (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
                firstTile()?.requestFocus(); true
            }
        }
        searchBox = f
        addView(f, LinearLayout.LayoutParams(dp(300), dp(46)))
    }

    private fun firstTile(): View? { val b = sections ?: return null; return findTile(b) }
    private fun findTile(v: View): View? {
        if (itemOf.containsKey(v)) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findTile(v.getChildAt(i))?.let { return it }
        return null
    }

    /** A pill: a choice you can switch on and off; selected = white, focused = lighter, like the segmented controls on Apple TV. */
    private fun pill(text: String, sp: Float = 16f, selected: (() -> Boolean)? = null, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text; textSize = sp; gravity = Gravity.CENTER; isFocusable = true; isClickable = true; maxLines = 1
        setPadding(dp(16), dp(7), dp(16), dp(7))
        minWidth = Math.ceil(paint.measureText(text).toDouble()).toInt() + dp(36)     // wraps its words; the measure alone came a pixel short in a scroller
        fun d(c: Int) = GradientDrawable().apply { setColor(c); cornerRadius = dp(30).toFloat() }
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected), d(0xF2FFFFFF.toInt()))
            addState(intArrayOf(android.R.attr.state_focused), d(0x73FFFFFF))
            addState(intArrayOf(), d(0x24FFFFFF))
        }
        setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()), intArrayOf(0xFF0B0D12.toInt(), 0xFFE5E9F2.toInt())))
        setOnFocusChangeListener { v, on -> v.animate().scaleX(if (on) 1.08f else 1f).scaleY(if (on) 1.08f else 1f).setDuration(if (animOn) 200 else 0).withLayer().start(); if (on) centreFocused(v) }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }
        if (selected != null) { isSelected = selected(); pills += selected to this }
    }

    /** Players, system, genre: one row of pills, each group single-choice (the same pill again clears it); a folder button at the end. */
    private fun filterRow(): View {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false; setPadding(dp(8), dp(6), dp(40), dp(6)) }
        fun gap() = row.addView(View(this), LinearLayout.LayoutParams(dp(12), 1))
        fun refresh() { pills.forEach { it.second.isSelected = it.first() }; renderSections() }
        val all = lib + sugs
        listOf(1 to R.string.f_players_1, 2 to R.string.f_players_2, 3 to R.string.f_players_34).forEach { (code, id) ->
            if (all.any { Browse.playersOk(code, it.max, it.min) }) row.addView(pill(getString(id), selected = { fPlayers == code }) { fPlayers = if (fPlayers == code) null else code; refresh() })
        }
        gap()
        all.map { it.sysKey to it.sysLabel }.distinct().sortedBy { it.second }.forEach { (k, l) ->
            row.addView(pill(l, selected = { fSystem == k }) { fSystem = if (fSystem == k) null else k; refresh() })
        }
        gap()
        all.groupingBy { it.genre }.eachCount().entries.sortedWith(compareBy({ it.key == getString(R.string.genre_other) }, { -it.value }, { it.key })).forEach { (g, _) ->
            row.addView(pill(g, selected = { fGenre == g }) { fGenre = if (fGenre == g) null else g; refresh() })
        }
        gap()
        row.addView(pill(getString(R.string.grp_new_pill)) { askName(getString(R.string.grp_new), Shelf.get(this).freeName(getString(R.string.grp_default))) { n -> val g = Shelf.newGroup(this, n); wantFolder = g.id; renderSections() } })
        return HorizontalScrollView(this).apply { addView(row); isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; overScrollMode = View.OVER_SCROLL_NEVER }
    }

    private fun matches(i: Item) = Browse.nameMatches(i.name, query) && Browse.playersOk(fPlayers, i.max, i.min) && (fSystem == null || i.sysKey == fSystem) && (fGenre == null || i.genre == fGenre)

    /** The rows under the pills: favourites, each folder, all games, suggestions; while searching or filtering one row of what matches. */
    private fun renderSections() {
        val box = sections ?: return
        val keepKey = focusedKey; val keepId = keepKey?.substringAfter('|')
        val hadFocus = box.hasFocus()
        val typing = searchBox?.hasFocus() == true                      // the keyboard is open on the search field: it keeps the focus
        box.removeAllViews(); tileKeys.clear()
        if (filtering()) {
            val res = (lib + sugs).filter(::matches)
            if (res.isEmpty()) box.addView(label(getString(R.string.no_match), 20f, 0xFFAAB0C0.toInt()).apply { gravity = Gravity.START; setPadding(dp(8), dp(24), 0, 0) })
            else box.addView(section("r", getString(R.string.sec_results, res.size), res))
        } else {
            val shelf = Shelf.get(this)
            val byId = (lib + sugs).filter { it.shelf }.associateBy { it.id }
            val fav = shelf.fav.mapNotNull { byId[it] }
            if (fav.isNotEmpty()) box.addView(section("fav", getString(R.string.sec_fav), fav))
            for (g in shelf.groups) box.addView(section("g" + g.id, g.name, g.items.mapNotNull { byId[it] }, group = g))
            box.addView(section("all", getString(R.string.sec_all), lib, add = true))
            if (sugs.isNotEmpty()) box.addView(section("sug", getString(R.string.suggest), sugs))
        }
        val pend = pendingFocus; pendingFocus = null
        val f = wantFolder; wantFolder = null
        val target = pend?.let { p -> tileKeys.entries.firstOrNull { it.key.endsWith("|$p") }?.value }
            ?: f?.let { id -> tileKeys.entries.firstOrNull { it.key.startsWith("g$id|") }?.value ?: groupHeads["g$id"] }
            ?: if (hadFocus || keepKey != null) (keepKey?.let { tileKeys[it] } ?: keepId?.let { id -> tileKeys.entries.firstOrNull { it.key.endsWith("|$id") }?.value }) else null
        if (!typing) (target ?: if (!hadFocus && lastFocus == null) firstTile() else null)?.requestFocus()
    }

    private val groupHeads = HashMap<String, View>()

    /** A row: its title (a folder's with Rename / Delete / Pin beside it) and its tiles. */
    private fun section(key: String, title: String, items: List<Item>, group: Shelf.Group? = null, add: Boolean = false): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(14), 0, dp(8)); clipChildren = false
            addView(label(title, 22f, 0xFFAAB0C0.toInt()).apply { gravity = Gravity.START })
            if (group != null) {
                val sp = { View(context).also { v -> v.layoutParams = LinearLayout.LayoutParams(dp(16), 1) } }
                addView(sp())
                val rn = pill(getString(R.string.grp_rename), 14f) { askName(getString(R.string.grp_rename), group.name) { n -> Shelf.change(context) { it.rename(group.id, n) }; Pins.renamed(context, group.id, n); renderSections() } }
                addView(rn)
                addView(pill(getString(R.string.grp_delete), 14f) { confirm(getString(R.string.grp_delete_q, group.name)) { Shelf.change(context) { it.delete(group.id) }; Pins.deleted(context, group.id); renderSections() } })
                if (Pins.can(context)) addView(pill(getString(R.string.m_pin), 14f) { pinGroup(group, items) })
                groupHeads[key] = rn
            }
        })
        if (items.isEmpty() && !add) addView(label(getString(R.string.grp_empty), 16f, 0xFF6B7385.toInt()).apply { gravity = Gravity.START; setPadding(dp(8), 0, 0, dp(18)) })
        else addView(GridLayout(context).apply {
            columnCount = 4; clipChildren = false; clipToPadding = false
            items.forEach { i -> addView(gameTile(i, key)) }
            if (add) addView(addTile())
        })
    }

    private fun webSrc(url: String, img: String?): Thumbs.Src = when {
        url == "builtin:padtest" -> Thumbs.Src("w:$url", asset = "thumbs/padtest.jpg")
        url.startsWith("builtin:") -> Thumbs.Src("w:$url")
        !img.isNullOrEmpty() -> Thumbs.Src("w:$url", img = img)
        else -> Thumbs.Src("w:$url", page = url)
    }

    private fun hide(id: String) {
        val p = getSharedPreferences("games", MODE_PRIVATE)
        p.edit().putStringSet("hidden", HashSet(p.getStringSet("hidden", emptySet()) ?: emptySet()).apply { add(id) }).apply(); reload()
    }

    // ---- the menu on OK held

    private fun menu(i: Item) {
        val e = ArrayList<Pair<String, () -> Unit>>()
        if (i.shelf) {
            val fav = Shelf.get(this).fav.contains(i.id)
            e += getString(if (fav) R.string.m_fav_del else R.string.m_fav_add) to { Shelf.change(this) { it.toggleFav(i.id) }; closeOverlay(); renderSections() }
            e += getString(R.string.m_group) to { groupPicker(i) }
            if (Pins.can(this)) e += getString(R.string.m_pin) to { closeOverlay(); pinGame(i) }
        }
        i.web?.let { u -> e += getString(R.string.m_remove) to { closeOverlay(); Systems.removeWeb(this, u); reload() } }
        i.hideId?.let { h -> e += getString(R.string.m_hide) to { closeOverlay(); hide(h) } }
        if (e.isNotEmpty()) popup(i.name, e)
    }

    private fun groupPicker(i: Item) {
        val sh = Shelf.get(this)
        val e = ArrayList<Pair<String, () -> Unit>>()
        sh.groups.forEach { g -> e += ((if (sh.inGroup(g.id, i.id)) "✓  " else "     ") + g.name) to { Shelf.change(this) { it.toggleIn(g.id, i.id) }; closeOverlay(); renderSections() } }
        e += getString(R.string.grp_new_item) to { closeOverlay(); askName(getString(R.string.grp_new), sh.freeName(getString(R.string.grp_default))) { n -> val g = Shelf.newGroup(this, n); Shelf.change(this) { it.toggleIn(g.id, i.id) }; renderSections() } }
        popup(getString(R.string.m_group), e, replace = true)
    }

    private fun pinGame(i: Item) {
        val ok = Pins.pinGame(this, i.id, i.name, artById[i.id], i.color)
        pinSaid(ok) { Pins.gameRefused(this, i.id) }
    }

    private fun pinGroup(g: Shelf.Group, items: List<Item>) {
        val ok = Pins.pinFolder(this, g.id, g.name, items.mapNotNull { artById[it.id] })
        pinSaid(ok) { Pins.folderRefused(this, g.id) }
    }

    /** The launcher cannot pin: say so. It can: look again after a moment, and when it took none (TV Home's dock is full) say that. */
    private fun pinSaid(ok: Boolean, refused: () -> Boolean) {
        if (!ok) { android.widget.Toast.makeText(this, getString(R.string.pin_no), android.widget.Toast.LENGTH_LONG).show(); return }
        root.postDelayed({ if (!isDestroyed && refused()) android.widget.Toast.makeText(this, getString(R.string.pin_full), android.widget.Toast.LENGTH_LONG).show() }, 1800)
    }

    /** A card over the library: a title and buttons; Back / Close leaves it ([dispatchKeyEvent]). */
    private fun popup(title: String, entries: List<Pair<String, () -> Unit>>, replace: Boolean = false, extra: ((LinearLayout) -> Unit)? = null, focusIndex: Int = 0) {
        if (replace) { overlay?.let { root.removeView(it) }; overlay = null } else lastFocus = currentFocus
        var first: View? = null
        val v = FrameLayout(this).apply {
            setBackgroundColor(0xF20B0D12.toInt()); isClickable = true
            addView(LinearLayout(this@LibraryActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(label(title, 28f).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD; maxLines = 2; setPadding(dp(20), 0, dp(20), dp(10)) })
                extra?.invoke(this)
                entries.forEachIndexed { n, (t, f) -> addView(menuButton(t, 420, 52, 20f) { f() }.also { if (n == focusIndex) first = it }) }
                addView(menuButton(getString(R.string.close), 420, 52, 20f) { closeOverlay() }.also { if (first == null) first = it })
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        overlay = v
        scroller?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        root.addView(v, ViewGroup.LayoutParams(-1, -1))
        first?.requestFocus()
    }

    private fun confirm(q: String, yes: () -> Unit) = popup(q, listOf(getString(R.string.yes_delete) to { closeOverlay(); yes() }), focusIndex = 1)

    /** A name to type (the TV keyboard opens on OK); OK saves a non-empty one. */
    private fun askName(title: String, initial: String, ok: (String) -> Unit) {
        lateinit var f: EditText
        val save = { val n = f.text.toString().trim(); if (n.isNotEmpty()) { closeOverlay(); ok(n.take(40)) } }
        popup(title, listOf(getString(R.string.ok) to { save() }), extra = { col ->
            f = EditText(this).apply {
                setText(initial); setSelection(initial.length); setSingleLine(true); textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                imeOptions = EditorInfo.IME_ACTION_DONE; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                background = focusBg(10, 0xFF2A3042.toInt(), 0xFF3B4460.toInt())
                setOnEditorActionListener { v, _, _ -> (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0); true }
            }
            col.addView(f, LinearLayout.LayoutParams(dp(420), dp(56)).apply { topMargin = dp(8) })
        })
        f.requestFocus()
    }

    /** SHA-256 of a ROM (under 4 MB), kept by name + size + date so each file is read once. */
    private fun shaOf(f: File): String? {
        if (f.length() > (4 shl 20)) return null
        val p = getSharedPreferences("shas", MODE_PRIVATE); val k = "${f.name}|${f.length()}|${f.lastModified()}"
        p.getString(k, null)?.let { return it }
        return try {
            val v = java.security.MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
            p.edit().putString(k, v).apply(); v
        } catch (e: Exception) { null }
    }

    private class Dl(val tile: View) { @Volatile var cancelled = false }
    private class Cover(val box: View, val text: TextView, val bar: android.widget.ProgressBar, val hint: TextView)
    private var dl: Dl? = null
    private val covers = java.util.WeakHashMap<View, Cover>()
    private var pendingFocus: String? = null                          // the file just downloaded: the library focuses its tile once drawn

    private fun coverOf(tile: View): Cover = covers.getOrPut(tile) {
        val t = label("", 18f).apply { setTypeface(null, android.graphics.Typeface.BOLD); maxLines = 2; gravity = Gravity.CENTER }
        val bar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = android.content.res.ColorStateList.valueOf(0xFF4ADE80.toInt()); progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x40FFFFFF)
        }
        val hint = label("", 14f, 0xFFD5DAE6.toInt())
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(0xFA0B0D12.toInt()); setPadding(dp(14), 0, dp(14), 0)
            addView(t); addView(bar, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(8); bottomMargin = dp(6) }); addView(hint)
        }
        (tile as FrameLayout).addView(box, tile.childCount - 1, FrameLayout.LayoutParams(-1, -1))   // under the focus ring
        Cover(box, t, bar, hint)
    }

    private fun uncover(tile: View) { covers.remove(tile)?.let { (tile as FrameLayout).removeView(it.box) } }

    /** One OK on a suggestion: the ROM into files/roms, its sha256 checked first (a mismatch is thrown away). The card shows the percent
     *  (bytes of Content-Length; a bar that just runs when the size is unknown); OK or Back on it cancels; a failure says so and OK retries. */
    private fun getRom(h: org.json.JSONObject, s: Systems.Sys, tile: View) {
        dl?.let { if (it.tile === tile) cancelDl(); return }          // a second OK on the same card cancels; another card waits its turn
        val d = Dl(tile); dl = d
        val cv = coverOf(tile)
        cv.bar.visibility = View.VISIBLE; cv.bar.isIndeterminate = true; cv.text.text = getString(R.string.getting); cv.hint.text = getString(R.string.dl_cancel)
        Thread {
            var msg: String? = null; var saved: String? = null
            try {
                val c = java.net.URL(h.getString("url")).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 15000
                val total = c.contentLengthLong
                val out = java.io.ByteArrayOutputStream(if (total in 1..(64L shl 20)) total.toInt() else 1 shl 16)
                var last = -1; var lastAt = 0L
                c.inputStream.use { i ->
                    val buf = ByteArray(16384)
                    while (!d.cancelled) {
                        val n = i.read(buf); if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > (64 shl 20)) throw java.io.IOException("too big")
                        val pc = if (total > 0) (out.size() * 100L / total).toInt() else -1
                        val now = System.currentTimeMillis()
                        if (pc != last && now - lastAt > 120) { last = pc; lastAt = now; runOnUiThread { if (dl === d) progress(cv, pc) } }
                    }
                }
                if (!d.cancelled) {
                    val bytes = out.toByteArray()
                    val sum = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    val ext = h.getString("url").substringAfterLast('.').takeIf { it in s.exts } ?: s.exts.first()
                    if (sum != h.optString("sha256").lowercase()) msg = getString(R.string.rom_bad)
                    else { saved = h.optString("name").replace(Regex("[^A-Za-z0-9 ._-]"), "") + ".$ext"; File(Systems.romsDir(this), saved).writeBytes(bytes) }
                }
            } catch (e: Exception) { msg = getString(R.string.core_net) }
            runOnUiThread {
                if (isDestroyed || dl !== d) return@runOnUiThread
                dl = null
                if (d.cancelled) { uncover(tile); return@runOnUiThread }
                if (msg != null) { cv.bar.visibility = View.GONE; cv.text.text = msg; cv.hint.text = getString(R.string.dl_retry); return@runOnUiThread }
                getSharedPreferences("games", MODE_PRIVATE).edit().putString("got:" + h.optString("id"), saved).apply()
                pendingFocus = "g:" + saved
                android.widget.Toast.makeText(this, getString(R.string.added, h.optString("name")), android.widget.Toast.LENGTH_LONG).show()
                reload()
            }
        }.start()
    }

    private fun progress(cv: Cover, pc: Int) {
        if (pc < 0) { cv.bar.isIndeterminate = true; return }
        cv.bar.isIndeterminate = false; cv.bar.progress = pc; cv.text.text = getString(R.string.dl_progress, pc)
    }

    private fun cancelDl() { dl?.let { it.cancelled = true; uncover(it.tile); dl = null } }



    // ---- a tile: TV Home's, one for one (same ring, rise, spring and shine; its code is app/home HomeActivity.Tile)

    private val tileW get() = dp(196)
    private val tileH get() = dp(123)
    private val ringOut get() = dp(3)
    private val animOn: Boolean get() = android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    private val calm: Boolean by lazy {                               // little memory (TCL's 1 GB boxes): only the card grows, shorter
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        am.isLowRamDevice || android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem <= 1536L * 1024 * 1024
    }
    private val spring = GSpring(0.62f)
    private val glide = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)

    private class Ref(val card: FrameLayout, val face: FrameLayout, val ring: View, val shine: View, val content: View, val below: LinearLayout, val nameV: TextView)
    private val refs = java.util.WeakHashMap<View, Ref>()
    private val itemOf = java.util.WeakHashMap<View, Item>()

    /** The ring round a focused tile: TV Home's accent from [ringOut] dp outside the face to 1 dp over it, then a thin white line. */
    private fun ringDrawable(r: Float): Drawable {
        val o = ringOut
        fun band(w: Int, color: Int, radius: Float) = GradientDrawable().apply { cornerRadius = maxOf(0f, radius); setStroke(w, color); setColor(Color.TRANSPARENT) }
        return android.graphics.drawable.LayerDrawable(arrayOf(band(o + dp(1), getColor(R.color.tv_ring_accent), r + o), band(dp(1), Color.WHITE, r - dp(1)))).apply { setLayerInset(1, o + dp(1), o + dp(1), o + dp(1), o + dp(1)) }
    }

    /** The shell every tile shares: the card (face + ring) that grows, and the lines that show under it while focused. [fill] builds the face; returns its content. */
    private fun shell(color: Int, fill: (FrameLayout) -> View): View {
        val o = ringOut; val w = tileW; val h = tileH; val r = resources.getDimension(R.dimen.tv_radius_tile)
        val outer = FrameLayout(this).apply { isFocusable = true; isClickable = true; clipChildren = false; clipToPadding = false }
        val face = FrameLayout(this).apply { background = GradientDrawable().apply { cornerRadius = r; setColor(color) }; clipToOutline = true }
        val content = fill(face)
        val lift = dp(14).toFloat()
        val shine = View(this).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x30FFFFFF, 0x0AFFFFFF, 0x00FFFFFF)); alpha = 0f; translationY = -lift }
        face.addView(shine, FrameLayout.LayoutParams(-1, h * 2 / 3))
        val ring = View(this).apply { background = ringDrawable(r); alpha = 0f }
        val card = FrameLayout(this).apply {
            clipChildren = false
            addView(face, FrameLayout.LayoutParams(w, h).apply { setMargins(o, o, o, o) })
            addView(ring, FrameLayout.LayoutParams(-1, -1))
            outlineProvider = object : android.view.ViewOutlineProvider() { override fun getOutline(v: View, out: android.graphics.Outline) { out.setRoundRect(o, o, o + w, o + h, r) } }
        }
        outer.addView(card, FrameLayout.LayoutParams(w + 2 * o, h + 2 * o).apply { setMargins(-o, -o, -o, -o) })
        val nameV = TextView(this).apply { textSize = 18f; setTextColor(Color.WHITE); typeface = android.graphics.Typeface.DEFAULT_BOLD; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER; setShadowLayer(dp(5).toFloat(), 0f, dp(1).toFloat(), 0xCC000000.toInt()) }
        val below = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; alpha = 0f; addView(nameV, LinearLayout.LayoutParams(-1, -2)) }
        // under the scaled card, inside the row gap: it adds no height, so rows keep their rhythm
        outer.addView(below, FrameLayout.LayoutParams(w + dp(24), dp(60)).apply { leftMargin = -dp(12); topMargin = h + (h * (GROW - 1f) / 2f).toInt() + o + dp(3) })
        outer.layoutParams = GridLayout.LayoutParams().apply { width = w; height = h; setMargins(dp(12), dp(8), dp(12), dp(58)) }
        refs[outer] = Ref(card, face, ring, shine, content, below, nameV)
        outer.setOnFocusChangeListener { v, on -> focusTile(v, on) }
        return outer
    }

    private fun gameTile(i: Item, section: String): View {
        val dl = i.rom != null
        val outer = shell(0xFF1E2230.toInt()) { face ->
            val dark = Color.rgb(Color.red(i.color) * 45 / 100, Color.green(i.color) * 45 / 100, Color.blue(i.color) * 45 / 100)
            val fb = FrameLayout(this).apply { background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(i.color, dark)) }
            fb.addView(TextView(this).apply {                                // the designed picture: initials, and the name, until (or without) the real one
                text = ThumbNames.initials(i.name); textSize = 36f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(0xE6FFFFFF.toInt()); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(16))
            }, FrameLayout.LayoutParams(-1, -1))
            fb.addView(TextView(this).apply {
                text = if (dl) getString(R.string.get_rom, i.name) else i.name; textSize = 14f; setTextColor(Color.WHITE); maxLines = 2; gravity = Gravity.CENTER; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(8), 0, dp(8), dp(8)); setShadowLayer(4f, 0f, 1f, Color.BLACK)
            }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            face.addView(fb, FrameLayout.LayoutParams(-1, -1))
            fb
        }
        itemOf[outer] = i
        val ref = refs[outer]!!
        ref.below.addView(TextView(this).apply { text = meta(i); textSize = 14f; setTextColor(0xFFC5CCDB.toInt()); maxLines = 1; gravity = Gravity.CENTER; ellipsize = android.text.TextUtils.TruncateAt.END; setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(), 0xCC000000.toInt()) }, LinearLayout.LayoutParams(-1, -2))
        ref.nameV.text = if (dl) getString(R.string.get_rom, i.name) else i.name
        ref.nameV.visibility = View.GONE                                     // shown once a picture covers the face: the face names itself until then
        i.src?.let { src ->
            Thumbs.load(this, src) { bmp, _ ->
                val inner = trimSides(bmp)                                       // a 4:3 box art comes with dark side bars: its own blur fills them
                if (inner != null) {
                    ref.face.addView(android.widget.ImageView(this).apply { scaleType = android.widget.ImageView.ScaleType.FIT_XY; setImageBitmap(Bitmap.createScaledBitmap(inner, 24, 14, true)); setColorFilter(0xFF707070.toInt(), android.graphics.PorterDuff.Mode.MULTIPLY) }, 1, FrameLayout.LayoutParams(-1, -1))
                    ref.face.addView(android.widget.ImageView(this).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER; setImageBitmap(inner) }, 2, FrameLayout.LayoutParams(-1, -1))
                } else ref.face.addView(android.widget.ImageView(this).apply { scaleType = android.widget.ImageView.ScaleType.FIT_XY; setImageBitmap(bmp) }, 1, FrameLayout.LayoutParams(-1, -1))     // over the designed face, under the shine
                (ref.content as View).visibility = View.GONE
                ref.nameV.visibility = View.VISIBLE
                art[outer] = bmp; artById[i.id] = bmp
            }
        }
        val key = "$section|${i.id}"
        tileKeys.putIfAbsent(key, outer)
        outer.setTag(R.id.tile_key, key)
        if (i.rom != null) outer.setOnClickListener { getRom(i.rom.first, i.rom.second, outer) }
        else outer.setOnClickListener { i.play?.invoke() }
        outer.setOnLongClickListener { menu(i); true }
        return outer
    }

    /** The picture without its dark side bars (Thumbs' backdrop colour), or null when it has none. */
    private fun trimSides(b: Bitmap): Bitmap? {
        fun bar(x: Int): Boolean = (1..15).all { val p = b.getPixel(x, b.height * it / 16); Math.abs(Color.red(p) - 0x10) < 8 && Math.abs(Color.green(p) - 0x13) < 8 && Math.abs(Color.blue(p) - 0x1A) < 8 }
        var l = 0; while (l < b.width / 3 && bar(l)) l++
        var r = 0; while (r < b.width / 3 && bar(b.width - 1 - r)) r++
        if (l < b.width / 25 && r < b.width / 25) return null
        val m = minOf(l, r)
        return Bitmap.createBitmap(b, m, 0, b.width - 2 * m, b.height)
    }

    /** "2 players · Sports · NES". */
    private fun meta(i: Item): String {
        val p = when { i.max <= 0 -> null; i.max == 1 -> getString(R.string.need_one); i.min >= i.max -> getString(R.string.meta_n, i.max); else -> getString(R.string.need_range, i.min, i.max) }
        return listOfNotNull(p, i.genre.takeIf { it != getString(R.string.genre_other) || p == null }, i.sysLabel).joinToString("  ·  ")
    }

    private fun addTile(): View {
        val outer = shell(0xFF1A1F2B.toInt()) { face ->
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                addView(TextView(context).apply { text = "+"; textSize = 52f; setTextColor(Color.WHITE); includeFontPadding = false; gravity = Gravity.CENTER; typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL) })
                addView(label(getString(R.string.add_game), 15f).apply { setTextColor(0xFFC9CFDD.toInt()) })
            }
            face.addView(c, FrameLayout.LayoutParams(-1, -1)); c
        }
        outer.setOnClickListener { showOverlay() }
        return outer
    }

    /** TV Home's focus, copied: a spring rise (1.12x, a soft shadow, the picture a touch larger inside), the ring fading in, the lines under it, a shine settling. */
    private fun focusTile(v: View, on: Boolean) {
        val r = refs[v] ?: return
        val lift = dp(14).toFloat()
        r.card.animate().cancel(); r.ring.animate().cancel(); r.below.animate().cancel(); r.content.animate().cancel(); r.shine.animate().cancel()
        if (on) { lastFocus = v; focusedKey = v.getTag(R.id.tile_key) as? String; centreFocused(v) }
        if (!animOn) {
            r.card.scaleX = 1f; r.card.scaleY = 1f; r.card.translationZ = 0f; r.content.scaleX = 1f; r.content.scaleY = 1f
            r.ring.alpha = if (on) 1f else 0f; r.below.alpha = if (on) 1f else 0f; r.shine.alpha = 0f
            return
        }
        val s = if (on) GROW else 1f
        val t = ms(if (on) 520L else 300L)
        val ip = if (on) spring else glide
        r.card.translationX = 0f; r.card.translationY = 0f
        if (calm) {
            r.card.translationZ = 0f; r.content.scaleX = 1f; r.content.scaleY = 1f; r.shine.alpha = 0f; r.below.translationY = 0f
            r.ring.alpha = if (on) 1f else 0f; r.below.alpha = if (on) 1f else 0f
            r.card.animate().scaleX(s).scaleY(s).setDuration(t).setInterpolator(ip).withLayer().start()
            return
        }
        r.card.animate().scaleX(s).scaleY(s).translationZ(if (on) lift else 0f).setDuration(t).setInterpolator(ip).withLayer().start()
        r.content.animate().scaleX(if (on) 1.05f else 1f).scaleY(if (on) 1.05f else 1f).setDuration(t).setInterpolator(ip).start()
        r.ring.animate().alpha(if (on) 1f else 0f).setDuration(if (on) 200L else 160L).setInterpolator(glide).start()
        r.below.animate().alpha(if (on) 1f else 0f).translationY(if (on) 0f else -lift / 3).setDuration(if (on) 360L else 200L).setInterpolator(glide).start()
        r.shine.animate().alpha(if (on) 1f else 0f).translationY(if (on) 0f else -lift).setDuration(if (on) 420L else 200L).setInterpolator(glide).start()
    }

    private fun ms(t: Long) = if (calm) t * 11 / 20 else t

    /** Scrolls so the focused tile's row sits in the middle of the screen (clamped: the header stays on top for the first row, the
     *  last row stops at the content's end, so no empty band is added below it). A new key retargets the running animation. */
    private fun centreFocused(v: View) {
        val sv = scroller ?: return
        val content = sv.getChildAt(0) ?: return
        sv.post {
            val r = android.graphics.Rect(0, 0, v.width, v.height)
            try { sv.offsetDescendantRectToMyCoords(v, r) } catch (e: Exception) { return@post }
            val inContent = r.centerY()      // already in the content's coordinates; its zoom is ignored
            val max = (content.height - sv.height).coerceAtLeast(0)
            val head = (content as? ViewGroup)?.let { c -> (0..1).sumOf { c.getChildAt(it)?.height ?: 0 } } ?: 0     // the header and the pills stay in view for the first row
            val want = (inContent - sv.height / 2).coerceIn(0, max)
            val to = if (want < head) 0 else want                       // never half-hide the header: the first rows stay as they are
            scrollAnim?.cancel()
            val from = sv.scrollY
            if (from == to) return@post
            if (!animOn) { sv.scrollTo(0, to); return@post }
            scrollAnim = android.animation.ValueAnimator.ofInt(from, to).apply {
                duration = 200; interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { sv.scrollTo(0, it.animatedValue as Int) }
                start()
            }
        }
    }

    private fun showOverlay() {
        var close: View? = null
        lastFocus = currentFocus
        val v = FrameLayout(this).apply {
            setBackgroundColor(0xFA0B0D12.toInt()); isClickable = true
            addView(LinearLayout(this@LibraryActivity).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(addPanel(server)); addView(rotateButton(server) { closeOverlay(); showOverlay() })
                addView(menuButton(getString(R.string.close), 360, 52, 20f) { closeOverlay() }.also { close = it })
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        overlay = v
        scroller?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        root.addView(v, ViewGroup.LayoutParams(-1, -1))
        close?.requestFocus()                                          // the D-pad starts on Close, not on the tile behind
    }

    companion object { const val GROW = 1.12f }                      // a focused card's size (TV Home's)
}

/** A real damped spring: rises fast, passes ~6 %, settles (TV Home's `Spring`, Apple TV's focus). */
class GSpring(private val damping: Float) : android.animation.TimeInterpolator {
    private val w = 6.2f / damping
    private val wd = w * Math.sqrt(1.0 - damping * damping).toFloat()
    override fun getInterpolation(t: Float): Float {
        if (t >= 1f) return 1f
        val e = Math.exp((-damping * w * t).toDouble()).toFloat()
        return 1f - e * (Math.cos((wd * t).toDouble()).toFloat() + damping * w / wd * Math.sin((wd * t).toDouble()).toFloat())
    }
}
