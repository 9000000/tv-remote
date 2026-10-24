package dev.tvremote.home

import android.content.Context
import dev.tvremote.home.apps.Apps
import dev.tvremote.home.apps.Pins
import dev.tvremote.home.apps.Tiles
import dev.tvremote.home.keys.Keys
import dev.tvremote.home.wall.Online
import dev.tvremote.home.wall.Videos
import dev.tvremote.home.wall.Walls
import org.json.JSONObject

/** The settings ([HomeConfig]) in prefs; the screen is told of every change, whether it came from the phone or from the TV. */
object Store {
    var onChange: (() -> Unit)? = null                                // the screen, while it exists; changes come on the main thread

    private fun prefs(c: Context) = c.getSharedPreferences("home", Context.MODE_PRIVATE)

    /**
     * The settings as they are now: motion starts off on a weak box (little RAM) until someone chooses, and "dream" is what the
     * system says (the screensaver can be changed in its own settings too), neither kept in the saved copy.
     */
    fun get(c: Context): JSONObject {
        val saved = saved(c) ?: return first(c)
        return effective(c, JSONObject(saved.toString()))           // a copy: callers change what they get
    }

    // the saved copy cleaned once, again only when the text in prefs changes: parsed and cleaned on every call it was ~7 ms on the
    // FPT box, and [list] (a dozen calls building one screen) paid that and [effective]'s ~6 ms for one array
    private var raw: String? = null
    private var clean = JSONObject()
    @Synchronized private fun saved(c: Context): JSONObject? {
        val now = prefs(c).getString("conf", null) ?: return null
        if (now != raw) { clean = HomeConfig.clean(try { JSONObject(now) } catch (e: Exception) { JSONObject() }); raw = now }
        return clean
    }

    private fun effective(c: Context, conf: JSONObject): JSONObject {
        // on until someone turns it off: older builds saved "off" for 2 GB boxes nobody had asked (it costs no memory, only frames)
        if (!prefs(c).getBoolean("anim_chosen", false)) conf.put("anim", true)
        return conf.put("dream", Dream.isOn(c)).put("wsizes", true).put("colsOk", true).put("rowsOk", true).put("rowsMax", rowsFit)   // an older TV Home has none: the page offers it the old layout chips
            .put("wstyles", HomeConfig.styles())                  // the looks of the cards that have some (an older one has none)
            .put("wweb", true)                                    // the background can be a web page (an older one would clean it away)
            .put("wrotate", true)                                 // the background can turn through a set (an older one has one choice)
            .put("shadeOk", true)                                 // the veil's strength can be chosen (an older one has one)
            .put("qrOk", true)                                    // QR cards (an older one would clean them away)
            .put("themesOk", true).put("theme", HomeConfig.themeNow(conf))   // themes (HomeConfig "theme"): the ready-made ones and which look is on now
            .put("themesBuiltin", org.json.JSONArray(HomeConfig.ready.map { r -> JSONObject().put("id", r.id).put("names", JSONObject(r.names)).put("conf", r.conf) }))
            .let { Keys.report(c, it) }                           // keys that open apps: "keysOk", and whether TV Setup took them
            .let { Tiles.report(c, it) }                          // live tiles: "tilesOk", what TV Setup offers, and whether it can be asked
            .let { Pins.report(c, it) }                           // shortcuts: "pinsOk", what the apps offer ([Target] "pin:...")
            .put("keysHave", Keys.have(c))                        // the keys this remote has (the quick keys screen greys out the others)
    }

    /** The first start: a dock of the well-known apps this TV has, so the screen is not empty before anyone sets it up. */
    private fun first(c: Context): JSONObject {
        val have = Apps.list(c).map { it.pkg }.toSet()
        val dock = FIRST_DOCK.filter { it in have }.take(HomeConfig.DOCK_MAX)
        val conf = HomeConfig.clean(JSONObject().put("dock", org.json.JSONArray(dock)))
        prefs(c).edit().putString("conf", conf.toString()).apply()
        return effective(c, conf)
    }
    private val FIRST_DOCK = listOf("com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus", "com.google.android.youtube.tvmusic", "com.spotify.tv.android", "com.android.vending", "com.android.tv.settings")

    /** @throws SecurityException "dream" without WRITE_SECURE_SETTINGS (a client grants it over ADB first; the TV's own switch falls back to the system's screen) */
    fun change(c: Context, change: JSONObject): JSONObject {
        if (change.has("dream")) Dream.set(c, change.optBoolean("dream"))
        val n = HomeConfig.merge(get(c), change) { Walls.usable(c, it) }
        if (change.has("wall")) Videos.forgetPerf(c, change.optString("wall"))   // a video that was too slow gets another try when it is chosen again
        val e = prefs(c).edit().putString("conf", n.toString())
        val applied = change.optJSONObject("theme")?.has("apply") == true      // a theme sets the motion and the background too
        if (change.has("anim") || applied) e.putBoolean("anim_chosen", true)  // from now on the choice is theirs
        if (change.has("wall") || change.has("walls") || change.has("wevery") || applied) e.putLong("wlast", System.currentTimeMillis())   // a new choice gets its whole turn
        e.apply()
        if (listOf("keyon", "keyapps", "autoon", "autoapp").any { change.has(it) }) Keys.push(c)   // TV Setup does the keys everywhere and the power-on app
        val out = effective(c, JSONObject(n.toString()))
        onChange?.invoke()
        return out
    }

    /** When the background last changed (ms), from a choice or from the rotation. */
    fun last(c: Context) = prefs(c).getLong("wlast", 0L)

    /**
     * Moves the background on to the next of its set when it is time (or, [show], when Home has just come back on screen and the
     * rotation is "show"); true when it changed. No timer anywhere keeps this running: Home asks when it is shown and sets one
     * timer while it is on screen, so a weak box does no work between changes.
     */
    fun advance(c: Context, show: Boolean): Boolean {
        val conf = get(c)
        val every = conf.optString("wevery")
        val a = conf.getJSONArray("walls")
        val walls = List(a.length()) { a.getString(it) }
        if (every == "off" || !turns(c, walls)) return false
        val now = System.currentTimeMillis()
        if (!(show && every == "show") && !HomeConfig.due(every, last(c), now, java.util.TimeZone.getDefault().getOffset(now).toLong())) return false
        if (conf.optString("wall") == HomeConfig.BING && Online.step(c)) { change(c, JSONObject().put("walls", a)); return true }   // Bing's older days first, then the next of the set
        val next = HomeConfig.next(walls, conf.optString("wall")) { Walls.usable(c, it) } ?: return false
        change(c, JSONObject().put("wall", next).put("walls", a))
        return true
    }

    /** Whether [walls] has anything to turn to: two or more, or Bing alone with several days kept. */
    fun turns(c: Context, walls: List<String>) = walls.size >= 2 || (walls.singleOrNull() == HomeConfig.BING && Online.count(c) > 1)

    /** One of the saved lists (dock, hidden, tiles, order, widgets): from the cleaned copy, none of [effective]'s reports needed. */
    fun list(c: Context, key: String): MutableList<String> = synchronized(this) {
        val a = (saved(c) ?: return get(c).getJSONArray(key).let { a -> MutableList(a.length()) { a.getString(it) } }).getJSONArray(key)
        MutableList(a.length()) { a.getString(it) }
    }

    fun putList(c: Context, key: String, l: List<String>) { change(c, JSONObject().put(key, org.json.JSONArray(l))) }
}
