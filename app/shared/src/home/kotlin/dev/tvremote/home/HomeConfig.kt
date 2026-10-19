package dev.tvremote.home

import org.json.JSONArray
import org.json.JSONObject

/**
 * TV Home's settings, the one place their shape and limits are written: the launcher (app/home) keeps them, the phone and
 * the computer change them over ADB (core/ontv/Home.kt → HomeReceiver). Compiled into both sides, so they never disagree.
 *   dock    favourite apps in their order, at most [DOCK_MAX]
 *   hidden  apps left off the grid
 *   wall    a built-in background ([WALLS]) or "photo" (wall.jpg in TV Home's external files folder, sent by the client)
 *   photo   when that photo last changed, so the launcher reads it again
 *   clock24 24-hour clock; greet: the greeting under the clock
 */
object HomeConfig {
    const val PKG = "dev.tvremote.home"
    const val RECEIVER = "$PKG/.HomeReceiver"
    const val DOCK_MAX = 6
    private const val HIDDEN_MAX = 300
    val WALLS = listOf("night", "ocean", "dusk", "forest", "aurora", "black", "photo")
    /** Where a client puts the photo (the shell may write there; the app reads it with no permission). */
    const val PHOTO = "/sdcard/Android/data/$PKG/files/wall.jpg"
    private val PKG_NAME = Regex("[A-Za-z][\\w]*(\\.[\\w]+)+")

    /** [o] made safe: unknown fields dropped, lists of real package names without repeats, every value in range. */
    fun clean(o: JSONObject): JSONObject {
        val dock = pkgs(o.optJSONArray("dock"), DOCK_MAX)
        return JSONObject()
            .put("dock", JSONArray(dock))
            .put("hidden", JSONArray(pkgs(o.optJSONArray("hidden"), HIDDEN_MAX).filter { it !in dock }))
            .put("wall", o.optString("wall").takeIf { it in WALLS } ?: "night")
            .put("photo", o.optLong("photo", 0).coerceAtLeast(0))
            .put("clock24", o.optBoolean("clock24", true))
            .put("greet", o.optBoolean("greet", true))
    }

    /** [old] with the fields [change] has (any of them), cleaned. */
    fun merge(old: JSONObject, change: JSONObject): JSONObject {
        val n = JSONObject(clean(old).toString())
        for (k in change.keys()) n.put(k, change.get(k))
        return clean(n)
    }

    private fun pkgs(a: JSONArray?, max: Int): List<String> {
        val out = LinkedHashSet<String>()
        if (a != null) for (i in 0 until a.length()) {
            val p = a.optString(i)
            if (p.length <= 200 && PKG_NAME.matches(p)) out.add(p)
        }
        return out.take(max)
    }
}
