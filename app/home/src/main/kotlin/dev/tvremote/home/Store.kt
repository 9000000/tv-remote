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
        return conf.put("dream", Dream.isOn(c)).put("wlayouts", org.json.JSONArray(HomeConfig.LAYOUTS))   // wlayouts: an older TV Home has none, so the page offers it the old ones
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
        e.apply()
        val out = effective(c, JSONObject(n.toString()))
        onChange?.invoke()
        return out
    }

    fun list(c: Context, key: String): MutableList<String> {
        val a = get(c).getJSONArray(key)
        return MutableList(a.length()) { a.getString(it) }
    }

    fun putList(c: Context, key: String, l: List<String>) { change(c, JSONObject().put(key, org.json.JSONArray(l))) }
}
