package dev.tvremote.home

import android.content.Context
import org.json.JSONObject

/** The settings ([HomeConfig]) in prefs; the screen is told of every change, whether it came from the phone or from the TV. */
object Store {
    var onChange: (() -> Unit)? = null                                // the screen, while it exists; changes come on the main thread

    private fun prefs(c: Context) = c.getSharedPreferences("home", Context.MODE_PRIVATE)

    fun get(c: Context): JSONObject {
        val saved = prefs(c).getString("conf", null) ?: return first(c)
        return HomeConfig.clean(try { JSONObject(saved) } catch (e: Exception) { JSONObject() })
    }

    /** The first start: a dock of the well-known apps this TV has, so the screen is not empty before anyone sets it up. */
    private fun first(c: Context): JSONObject {
        val have = Apps.list(c).map { it.pkg }.toSet()
        val dock = FIRST_DOCK.filter { it in have }.take(HomeConfig.DOCK_MAX)
        val conf = HomeConfig.clean(JSONObject().put("dock", org.json.JSONArray(dock)))
        prefs(c).edit().putString("conf", conf.toString()).apply()
        return conf
    }
    private val FIRST_DOCK = listOf("com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus", "com.google.android.youtube.tvmusic", "com.spotify.tv.android", "com.android.vending", "com.android.tv.settings")

    fun change(c: Context, change: JSONObject): JSONObject {
        val n = HomeConfig.merge(get(c), change)
        prefs(c).edit().putString("conf", n.toString()).apply()
        onChange?.invoke()
        return n
    }

    fun list(c: Context, key: String): MutableList<String> {
        val a = get(c).getJSONArray(key)
        return MutableList(a.length()) { a.getString(it) }
    }

    fun putList(c: Context, key: String, l: List<String>) { change(c, JSONObject().put(key, org.json.JSONArray(l))) }
}
