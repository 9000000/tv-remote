package dev.tvremote.home

import org.json.JSONArray
import org.json.JSONObject

/**
 * TV Home's settings, the one place their shape and limits are written: the launcher (app/home) keeps them, the phone and
 * the computer change them over ADB (core/ontv/Home.kt → HomeReceiver). Compiled into both sides, so they never disagree.
 *   dock    favourite apps in their order, at most [DOCK_MAX]
 *   hidden  apps left off the grid
 *   order   the grid's own order, set by moving apps on the TV; an app it does not list goes after those that it does (A-Z), an app
 *           that is gone is ignored
 *   wall    the background on now: a gradient ([PLAIN]), a drawn scene ([ART]), "bing" (the day's Bing picture, kept by TV Home), or one
 *           of the person's photos ([PHOTOS]: "photo" is wall.jpg, "photo2" wall2.jpg ... in TV Home's external files folder, sent by
 *           the client; at most [PHOTO_MAX])
 *   wurl    the page the "web" wall shows (an Immich Kiosk, a dashboard): only http:// or https:// (LAN pages are mostly plain http),
 *           at most [WURL_MAX]; anything else is dropped. "web" with no good wurl is an unknown wall: the default gradient. "web" is
 *           never turned through: choosing it makes [walls] just that one, and a set sent with it elsewhere loses it
 *   photo   when a photo last changed, so the launcher reads it again
 *   shade   how dark the veil over a photo, a drawn scene or the web page is, 0..100 % in steps of [SHADE_STEP]: 0 none,
 *           [SHADE_DEFAULT] the usual one (darker at the top and the bottom, where the clock and the names are), 100 an even veil
 *           nearly black. A busy page or photo fights the words of the home screen at the usual one. A TV Home that has this
 *           reports "shadeOk": true
 *   walls   the set the background turns through (any of the above, [WALLS_MAX]); never empty, [wall] is what shows now
 *   wevery  how often it moves on to the next of [walls] ([EVERY]): "off", "30m", "1h", "6h", "day" (the first time Home is seen on a
 *           new day), "show" (each time Home comes back on screen). Only while Home is on screen or when it comes back: nothing
 *           runs in between. A TV Home that does this reports "wrotate": true (the page keeps its one-choice list for one that
 *           does not)
 *   clock24 24-hour clock; greet: the greeting under the clock
 *   power   the round power button in the top bar (screen off / standby / power off, for a remote whose own button is broken); on by
 *           default. A TV Home that has this reports "power" in what it sends back, so the page offers the switch only to one that does
 *   lang    TV Home's words: "" the TV's own language, or one of [LANGS] whatever the TV is set to
 *   anim    motion effects (a focused app lifts, rows glide, the screen fades in); off = instant, with a ring. TV Home also
 *           treats the system's "animator duration scale 0" as off
 *   motion  how much moves while [anim] is on: "" this TV's choice (calm on one with little memory, 1.5 GB or less, or that Android
 *           calls low on RAM), "calm" (no overshoot, shorter, only the card grows: fewer frames on a weak chip), "full"
 *   focus   how the focused app shows: "glass" (a pane gliding from tile to tile) or "ring" (a blue ring on the tile)
 *   cols    how many app tiles in a row of the grid, [COLS_MIN]..[COLS_MAX] (default [COLS_DEFAULT]). A TV Home that has this reports
 *           "colsOk": true (the page offers the choice only to one that does)
 *   rows    how many rows of apps the first screen shows under the clock or the cards, tenths allowed (1.2: row 1 and a fifth of
 *           row 2), 1..[ROWS_MAX]; 0 = by itself (1.2 without cards, right under them with). More than fit stops where the grid
 *           meets the cards or the clock (TV Home reports that as "rowsMax", and "rowsOk": true)
 *   widgets the cards between the clock and the favourites, in their order ([WIDGETS]): "system" (memory, storage, network,
 *           ADB), "usage" (the most used apps of the week and the day's app limits, asked of TV Setup: [USAGE]),
 *           "calendar" (the month, the lunar day under every solar day),
 *           "lunar" (the lunar date, the next full moon and Tết), "clock" (an analog clock face, round, sweeping second hand),
 *           "timer" (the sleep timer or bedtime, from TV Setup too;
 *           shown only while one is set), "weather" (the sky now and the next days, Open-Meteo: [place] or, by itself, the device's
 *           location or the internet address's)
 *           Their order in the list is the order on the screen (the page and the TV's panel move them).
 *   qrs     QR cards: {"qr1": {tpl, f, cap, show}, ...} up to [HomeQr.CARDS_MAX], each a template's fields (Wi-Fi, link, text, phone, SMS,
 *           e-mail, contact: [HomeQr]) and a caption; a card is on the screen when its key is also in "widgets" (the order, the size).
 *           A key with no card here is dropped from "widgets". The Wi-Fi password lives here, on the TV, and nowhere else. A TV Home
 *           that has this reports "qrOk": true
 *   wsize   how big each card is: {"system": "big", "lunar": "small", ...}, "big" or "small", the default of a card when it is
 *           missing ([defaultSize]). [pack] turns the sizes and the order into columns: a big card is a column, two small cards
 *           in a row stack in one, a small card alone is a narrow column; what does not fit waits. A TV Home that has this reports
 *           "wsizes": true (the page offers the old layout chips to one that does not). Replaces "wlayout" (row / big / big2 /
 *           flip / quad), which [clean] still reads once, as sizes giving the same picture, when there are no "wsize" yet
 *   wstyle  how a card looks, for the cards that have looks ([STYLES]): {"clock": "light", ...}; missing = the card's first style.
 *           A TV Home that has this reports "wstyles": {kind: [styles]} (the page reads the choices from it; one without it has none)
 *   place   a city for the weather card, as typed ([PLACE_MAX] characters); empty = automatic
 *   dream   TV Home is the TV's screensaver (the big clock and the wallpaper, [DREAM]). Read from the system, not kept: the
 *           launcher sets `screensaver_components` itself (it holds WRITE_SECURE_SETTINGS once a client has granted it over
 *           ADB) and puts the previous screensaver back when this is switched off
 *   note    a line from the family, pinned on the home screen until it is emptied ([NOTE_MAX] characters, one line)
 *   tiles   live tiles on the app grid: keys "tile:<provider package>/<id>" ([isTile]), at most [TILES_MAX]. A provider (TV Setup's RAM tile:
 *           tile:dev.tvremote.setup/ram) draws a tile's value itself; the key then sits in dock / hidden / order like an app's package name.
 *           A TV Home that has this reports "tilesOk": true, "tilesAvail" (what the provider offers: [{key, title, kind}]) and
 *           "tilesState": "ok" | "missing" (no TV Setup or one without tiles) | "untrusted" (signed by another key)
 *   keyon   remote keys that open an app (default off); keyapps: {"183": "package"} the key code to its app, only keys [keyFree]
 *           allows. TV Home answers them on its own screen; TV Setup's key filter answers them everywhere (HomeInfo "keys").
 *           A TV Home that has this reports "keysOk": true, "keysSetup" (TV Setup took the map) and "keysDead" (the keys it found dead)
 *   autoon  open [autoapp] once each time the TV is turned on (boot or wake from standby); default off. TV Setup does it
 *           (TV Home is not running in standby), so it needs TV Setup, said in the setting ("keysSetup")
 */
object HomeConfig {
    const val PKG = "dev.tvremote.home"
    const val RECEIVER = "$PKG/.HomeReceiver"
    const val PHOTO_QR = "$PKG.PHOTO"                                  // HomeActivity's action: its QR screen for a photo from a phone (TV Setup's page on the TV has no photos)
    const val DREAM = "$PKG/.HomeDream"                               // the screensaver's component, as `screensaver_components` writes it
    const val WRITE_SECURE = "android.permission.WRITE_SECURE_SETTINGS"
    /** The Wi-Fi's name is location to Android (8.1+): granted over ADB when TV Home is installed or set up, never asked on the TV. */
    const val GRANT_WIFI = "pm grant $PKG android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1; "
    const val DOCK_MAX = 6
    const val COLS_MIN = 4; const val COLS_MAX = 8; const val COLS_DEFAULT = 5
    const val ROWS_MAX = 10.0
    private const val HIDDEN_MAX = 300
    private const val ORDER_MAX = 800
    /** Plain gradients (HomeActivity.wallColors). */
    val PLAIN = listOf("night", "ocean", "dusk", "forest", "aurora", "black", "sunset", "ember", "plum", "midnight", "teal", "slate", "graphite", "navy", "wine", "moss")
    /** Scenes TV Home draws itself, no picture file (WallSpec; app/home WallArt.kt). */
    val ART = listOf("dawn", "peaks", "dunes", "waves", "candy", "peach", "meadow", "mist", "bokeh", "canyon")
    /** Bing's pictures, downloaded by TV Home: the last 8 days kept, newest first, turned through by the rotation before the next of the set. */
    const val BING = "bing"
    /** A web page as the background and the screensaver (TV Home shows it in a WebView; [wurl]). */
    const val WEB = "web"
    const val WURL_MAX = 500
    val WMODES = listOf("live", "snap")
    const val SHADE_DEFAULT = 50
    const val SHADE_STEP = 5
    val WSNAPS = listOf(1, 5, 15, 30, 60)
    const val WSNAP_DEFAULT = 5
    private val WURL = Regex("https?://[^\\s/?#]+\\S*", RegexOption.IGNORE_CASE)
    /** [s] trimmed when it is an http(s) address with a host and fits [WURL_MAX]; else "". */
    fun wurl(s: String): String = s.trim().takeIf { it.length <= WURL_MAX && WURL.matches(it) } ?: ""
    const val PHOTO_MAX = 10
    /** The person's photos, one file each: "photo" is the first and the only one older TV Homes know. */
    val PHOTOS = listOf("photo") + (2..PHOTO_MAX).map { "photo$it" }
    /** In the order the pickers list them: Bing, the web page and the person's photos first (the ones people come to change), then the scenes, the plain colours last. */
    val WALLS = listOf(BING, WEB) + PHOTOS + ART + PLAIN
    const val WALLS_MAX = 40
    val EVERY = listOf("off", "30m", "1h", "6h", "day", "show")
    /** Where a client puts the first photo (the shell may write there; the app reads it with no permission). */
    const val PHOTO = "/sdcard/Android/data/$PKG/files/wall.jpg"
    /** The file of photo [id] ("photo" → wall.jpg, "photo3" → wall3.jpg), in TV Home's external files folder. */
    fun photoName(id: String) = "wall" + id.removePrefix("photo") + ".jpg"
    fun photoPath(id: String) = PHOTO.substringBeforeLast('/') + "/" + photoName(id)
    fun isPhoto(id: String) = id in PHOTOS
    /** The length of an interval in ms ("day" and "show" are not lengths: 0). */
    fun everyMs(e: String) = when (e) { "30m" -> 30 * 60_000L; "1h" -> 3_600_000L; "6h" -> 6 * 3_600_000L; else -> 0L }
    private const val DAY = 86_400_000L

    /** Whether it is time for the next background: [last] is when it last changed, [off] the time zone's offset (ms) for "day". */
    fun due(every: String, last: Long, now: Long, off: Long): Boolean = when (every) {
        "30m", "1h", "6h" -> now - last >= everyMs(every) || now < last          // a clock set back must not freeze it
        "day" -> Math.floorDiv(now + off, DAY) != Math.floorDiv(last + off, DAY)
        else -> false
    }
    /** How long until [due] turns true (for the one timer set while Home is on screen); null when time does not decide it. */
    fun wait(every: String, last: Long, now: Long, off: Long): Long? = when (every) {
        "30m", "1h", "6h" -> (last + everyMs(every) - now).coerceIn(0L, everyMs(every))
        "day" -> (Math.floorDiv(now + off, DAY) + 1) * DAY - off - now
        else -> null
    }
    /** The one after [cur] in [walls] that [usable] allows (a photo gone, Bing not yet fetched: skipped); null when there is none. */
    fun next(walls: List<String>, cur: String, usable: (String) -> Boolean): String? {
        if (walls.isEmpty()) return null
        val at = walls.indexOf(cur)
        for (i in 1..walls.size) {
            val w = walls[Math.floorMod(at + i, walls.size)]
            if (w != cur && usable(w)) return w
        }
        return null
    }
    val WIDGETS = listOf("system", "usage", "calendar", "lunar", "timer", "weather", "clock") + HomeQr.KEYS
    /** The looks of the cards that have more than one, the first is the default ("wstyle"). */
    val STYLES: Map<String, List<String>> = mapOf("clock" to listOf("classic", "light", "minimal", "roman"))
    fun styles(): JSONObject = JSONObject().also { o -> STYLES.forEach { (k, v) -> o.put(k, JSONArray(v)) } }
    /** A card's size when nobody chose: the four large cards are big, the small pills (lunar, timer, clock, and the note) small. */
    fun defaultSize(k: String) = if (k in BIG_KINDS) "big" else "small"
    private val BIG_KINDS = setOf("system", "usage", "calendar", "weather") + HomeQr.KEYS     // a code is read from the sofa: big until chosen otherwise
    /**
     * Widths as weights (a column's share of the row): a big card, the calendar (seven day columns) a little more, a small column
     * (stacked pills, or one standing) a little less. The row holds [BUDGET] at most: three big cards and a small column fit; a
     * fourth big card is 200 dp wide at 1920x1080 and the weather there cuts its words to "…" (measured on a TV), so it waits. [FLOOR]: a few
     * cards do not stretch across the whole row.
     */
    const val W_BIG = 100; const val W_CAL = 120; const val W_SMALL = 90; const val BUDGET = 415; const val FLOOR = 190
    fun weight(k: String, big: Boolean) = if (!big) W_SMALL else if (k == "calendar") W_CAL else W_BIG
    /** The cards of one column, top to bottom, and its weight. */
    class Col(val keys: List<String>, val weight: Int)
    /** The columns that fit, left to right, and the cards that wait (the pinned note, "note", never does). */
    class Packed(val cols: List<Col>, val waiting: List<String>)
    /**
     * The one rule of the layout (js/home.js `homePack` does the same, tests/home_pack_cases.json holds the cases both read): walk
     * the cards in order, a [big] card is a column, two small cards in a row stack in one column, a small card alone is a narrow
     * column of its own; stop at the first column that would pass [BUDGET], the rest wait. The pinned note ([note]) is the last
     * small card and keeps its place: the cards before it give way, from the end.
     */
    fun pack(keys: List<String>, big: Set<String>, note: Boolean = false): Packed {
        fun run(ks: List<String>): Packed {
            val cols = ArrayList<Col>(); var sum = 0; var i = 0
            while (i < ks.size) {
                val k = ks[i]; val isBig = k in big
                val group = if (!isBig && ks.getOrNull(i + 1)?.let { it !in big } == true) listOf(k, ks[i + 1]) else listOf(k)
                val w = weight(k, isBig)
                if (sum + w > BUDGET) break
                cols += Col(group, w); sum += w; i += group.size
            }
            return Packed(cols, ks.drop(i))
        }
        if (!note) return run(keys)
        for (n in keys.size downTo 0) {
            val p = run(keys.take(n) + "note")
            if (p.waiting.isEmpty()) return Packed(p.cols, keys.drop(n))
        }
        return Packed(emptyList(), keys)                                // not reached: the note alone always fits
    }
    /** The sizes in [conf] as the set of big cards. */
    fun bigs(conf: JSONObject): Set<String> {
        val s = conf.optJSONObject("wsize")
        return WIDGETS.filter { (s?.optString(it)?.takeIf { v -> v == "big" || v == "small" } ?: defaultSize(it)) == "big" }.toSet()
    }
    /** Remote keys a person may give an app. Colour keys: no app answers them in general. Numbers: only on TV Home's own screen. */
    val KEY_COLOURS = listOf(183, 184, 185, 186)                        // PROG_RED, GREEN, YELLOW, BLUE
    val KEY_NUMBERS = (7..16).toList()                                  // KEYCODE_0 .. KEYCODE_9
    /** The keys the stock launcher answered (app/tv KeyFix.RULES; a test there holds the two lists equal): free only once their handler is gone. */
    val KEY_DEAD = listOf(170, 178, 190, 191, 195, 192, 284, 176)
    fun keyAllowed(code: Int) = code in KEY_COLOURS || code in KEY_NUMBERS || code in KEY_DEAD
    /** "keyapps" as code to package, the codes allowed and the packages real names. */
    fun keyApps(o: JSONObject?): Map<Int, String> {
        val out = LinkedHashMap<Int, String>()
        if (o != null) for (k in o.keys()) {
            val code = k.toIntOrNull() ?: continue
            val p = o.optString(k)
            if (keyAllowed(code) && p.length <= 200 && PKG_NAME.matches(p)) out[code] = p
        }
        return out
    }
    const val NOTE_MAX = 140
    const val PLACE_MAX = 60
    /** TV Setup's answer to TV Home about app use (its HomeInfo provider): `call(USAGE, "usage")` → Bundle "json". */
    const val USAGE = "content://dev.tvremote.setup.home"
    /** The languages TV Home has words in (res/values-<lang>; English is res/values), "" = the TV's. */
    val MOTIONS = listOf("calm", "full")
    val LANGS = listOf("", "vi", "en")
    private val PKG_NAME = Regex("[A-Za-z][\\w]*(\\.[\\w]+)+")
    private val TILE = Regex("tile:[A-Za-z][\\w]*(\\.[\\w]+)+/[a-z][a-z0-9_]{0,15}")
    const val TILES_MAX = 8
    /** [k] is a live tile's key ("tile:dev.tvremote.setup/ram"), not an app's package name. */
    fun isTile(k: String) = k.length <= 100 && TILE.matches(k)
    fun tileKey(provider: String, id: String) = "tile:$provider/$id"
    /** The provider's package of a tile key, and its id there. */
    fun tileProvider(k: String) = k.removePrefix("tile:").substringBefore('/')
    fun tileId(k: String) = k.substringAfter('/')

    /** [o] made safe: unknown fields dropped, lists of real package names without repeats, every value in range. */
    fun clean(o: JSONObject): JSONObject {
        val qrs = HomeQr.cards(o.optJSONObject("qrs"))
        val widgets = o.optJSONArray("widgets")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty().filter { it in WIDGETS && (it !in HomeQr.KEYS || qrs.has(it)) }.distinct()
        val dock = pkgs(o.optJSONArray("dock"), DOCK_MAX)
        val wurl = wurl(o.optString("wurl"))
        val wall = o.optString("wall").takeIf { it in WALLS && (it != WEB || wurl.isNotEmpty()) } ?: "night"
        // the page is not a stop of the rotation (it never ends, and a turn away from it would not come back): it is the whole set or not in it
        var walls = if (wall == WEB) listOf(WEB)
            else o.optJSONArray("walls")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty().filter { it in WALLS && it != WEB }.distinct().take(WALLS_MAX)
        if (walls.isEmpty()) walls = listOf(wall)
        return JSONObject()
            .put("dock", JSONArray(dock))
            .put("hidden", JSONArray(pkgs(o.optJSONArray("hidden"), HIDDEN_MAX).filter { it !in dock }))
            .put("order", JSONArray(pkgs(o.optJSONArray("order"), ORDER_MAX)))
            .put("wall", if (wall in walls) wall else walls[0])           // what shows is always one of the set
            .put("walls", JSONArray(walls))
            .put("wurl", wurl)
            .put("wmode", o.optString("wmode").takeIf { it in WMODES } ?: "live")
            .put("wsnap", o.optInt("wsnap", WSNAP_DEFAULT).takeIf { it in WSNAPS } ?: WSNAP_DEFAULT)
            .put("wkeep", o.optBoolean("wkeep", false))
            .put("wevery", o.optString("wevery").takeIf { it in EVERY } ?: "off")
            .put("photo", o.optLong("photo", 0).coerceAtLeast(0))
            .put("shade", o.optInt("shade", SHADE_DEFAULT).coerceIn(0, 100) / SHADE_STEP * SHADE_STEP)
            .put("clock24", o.optBoolean("clock24", true))
            .put("lang", o.optString("lang").takeIf { it in LANGS } ?: "")
            .put("greet", o.optBoolean("greet", true))
            .put("power", o.optBoolean("power", true))
            .put("anim", o.optBoolean("anim", true))
            .put("motion", o.optString("motion").takeIf { it in MOTIONS } ?: "")
            .put("focus", o.optString("focus").takeIf { it == "ring" } ?: "glass")
            .put("cols", o.optInt("cols", COLS_DEFAULT).coerceIn(COLS_MIN, COLS_MAX))
            .put("rows", rows(o.optDouble("rows", 0.0)))
            .put("dream", o.optBoolean("dream", false))
            .put("note", o.optString("note").replace(Regex("\\s+"), " ").trim().take(NOTE_MAX))
            .put("place", o.optString("place").replace(Regex("\\s+"), " ").trim().take(PLACE_MAX))
            .put("keyon", o.optBoolean("keyon", false))
            .put("keyapps", JSONObject().also { m -> keyApps(o.optJSONObject("keyapps")).toSortedMap().forEach { (k, v) -> m.put(k.toString(), v) } })
            .put("autoon", o.optBoolean("autoon", false))
            .put("autoapp", o.optString("autoapp").takeIf { it.length <= 200 && PKG_NAME.matches(it) } ?: "")
            .put("tiles", JSONArray(strs(o.optJSONArray("tiles")).filter { isTile(it) }.distinct().take(TILES_MAX)))
            .put("qrs", qrs)
            .put("widgets", JSONArray(widgets))
            .put("wsize", sizes(o, widgets))
            .put("wstyle", JSONObject().also { out ->
                val g = o.optJSONObject("wstyle")
                if (g != null) for ((k, v) in STYLES) g.optString(k).takeIf { it in v }?.let { out.put(k, it) }
            })
    }

    /** [r] to tenths, 1..[ROWS_MAX]; anything under 1 (or not a number) is 0, by itself. */
    fun rows(r: Double): Double = if (r.isNaN() || r < 1) 0.0 else Math.round(minOf(r, ROWS_MAX) * 10) / 10.0

    private val CARD = Regex("[a-z][a-z0-9_]{0,15}")

    /**
     * What a client sends: [clean], but the cards it does not know (a newer TV Home's, read back from it) kept in "widgets" and
     * "wsize": an older phone or computer that turned one card on or resized one dropped the newer ones. TV Home's own [clean] decides.
     */
    fun forward(o: JSONObject): JSONObject {
        val c = clean(o)
        o.optJSONArray("widgets")?.let { a -> c.put("widgets", JSONArray((0 until a.length()).map { a.optString(it) }.filter { CARD.matches(it) }.distinct().take(16))) }
        o.optJSONObject("wsize")?.let { g ->
            val out = JSONObject()
            for (k in g.keys()) if (CARD.matches(k)) g.optString(k).takeIf { it == "big" || it == "small" }?.let { out.put(k, it) }
            c.put("wsize", out)
        }
        o.optJSONObject("wstyle")?.let { g ->                           // the looks of cards this client does not know
            val out = c.optJSONObject("wstyle") ?: JSONObject()
            for (k in g.keys()) if (k !in STYLES && CARD.matches(k)) g.optString(k).takeIf { CARD.matches(it) }?.let { out.put(k, it) }
            c.put("wstyle", out)
        }
        return c
    }

    /** "wsize" kept for the known cards with a known size; with none yet, the old "wlayout" as the sizes that gave the same picture. */
    private fun sizes(o: JSONObject, widgets: List<String>): JSONObject {
        val out = JSONObject()
        val given = o.optJSONObject("wsize")
        if (given != null) {
            for (k in WIDGETS) given.optString(k).takeIf { it == "big" || it == "small" }?.let { out.put(k, it) }
            return out
        }
        val bigCount = when (o.optString("wlayout")) { "big" -> 1; "big2" -> 2; "flip" -> 1; "quad" -> 0; else -> -1 }   // "row" or none: the defaults
        if (bigCount < 0) return out
        val flip = o.optString("wlayout") == "flip"
        widgets.forEachIndexed { i, k -> out.put(k, if (if (flip) i == 2 else i < bigCount) "big" else "small") }
        return out
    }

    /** [old] with the fields [change] has (any of them), cleaned. */
    fun merge(old: JSONObject, change: JSONObject): JSONObject {
        val n = JSONObject(clean(old).toString())
        for (k in change.keys()) n.put(k, change.get(k))
        if (change.has("wall") && !change.has("walls")) n.put("walls", JSONArray().put(change.get("wall")))   // a client that picks one: the set is that one
        return clean(n)
    }

    private fun strs(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }

    private fun pkgs(a: JSONArray?, max: Int): List<String> {
        val out = LinkedHashSet<String>()
        if (a != null) for (i in 0 until a.length()) {
            val p = a.optString(i)
            if (p.length <= 200 && (PKG_NAME.matches(p) || isTile(p))) out.add(p)
        }
        return out.take(max)
    }
}
