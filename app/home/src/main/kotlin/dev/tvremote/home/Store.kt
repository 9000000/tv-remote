package dev.tvremote.home

import android.content.Context
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
        val saved = prefs(c).getString("conf", null) ?: return first(c)
        return effective(c, HomeConfig.clean(try { JSONObject(saved) } catch (e: Exception) { JSONObject() }))
    }

    private fun effective(c: Context, conf: JSONObject): JSONObject {
        // on until someone turns it off: older builds saved "off" for 2 GB boxes nobody had asked (it costs no memory, only frames)
        if (!prefs(c).getBoolean("anim_chosen", false)) conf.put("anim", true)
        return conf.put("dream", Dream.isOn(c)).put("wsizes", true).put("colsOk", true).put("rowsOk", true).put("rowsMax", rowsFit)   // an older TV Home has none: the page offers it the old layout chips
            .put("wstyles", HomeConfig.styles())                  // the looks of the cards that have some (an older one has none)
            .put("wweb", true)                                    // the background can be a web page (an older one would clean it away)
            .put("wrotate", true)                                 // the background can turn through a set (an older one has one choice)
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
        val n = HomeConfig.merge(get(c), change)
        val e = prefs(c).edit().putString("conf", n.toString())
        if (change.has("anim")) e.putBoolean("anim_chosen", true)  // from now on the choice is theirs
        if (change.has("wall") || change.has("walls") || change.has("wevery")) e.putLong("wlast", System.currentTimeMillis())   // a new choice gets its whole turn
        e.apply()
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

    fun list(c: Context, key: String): MutableList<String> {
        val a = get(c).getJSONArray(key)
        return MutableList(a.length()) { a.getString(it) }
    }

    fun putList(c: Context, key: String, l: List<String>) { change(c, JSONObject().put(key, org.json.JSONArray(l))) }
}
