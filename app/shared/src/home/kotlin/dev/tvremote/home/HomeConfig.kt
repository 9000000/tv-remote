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
 *   anim    motion effects (a focused app lifts, rows glide, the screen fades in); off = instant, with a ring. TV Home also
 *           treats the system's "animator duration scale 0" as off
 *   widgets the cards between the clock and the favourites, in their order ([WIDGETS]): "system" (memory, storage, network,
 *           ADB), "usage" (the most used apps of the week and the day's app limits, asked of TV Setup: [USAGE]),
 *           "calendar" (the month, the lunar day under every solar day),
 *           "lunar" (the lunar date, the next full moon and Tết), "timer" (the sleep timer or bedtime, from TV Setup too;
 *           shown only while one is set), "weather" (the sky now and the next days, Open-Meteo: [place] or, by itself, the device's
 *           location or the internet address's)
 *   place   a city for the weather card, as typed ([PLACE_MAX] characters); empty = automatic
 *   dream   TV Home is the TV's screensaver (the big clock and the wallpaper, [DREAM]). Read from the system, not kept: the
 *           launcher sets `screensaver_components` itself (it holds WRITE_SECURE_SETTINGS once a client has granted it over
 *           ADB) and puts the previous screensaver back when this is switched off
 *   note    a line from the family, pinned on the home screen until it is emptied ([NOTE_MAX] characters, one line)
 */
object HomeConfig {
    const val PKG = "dev.tvremote.home"
    const val RECEIVER = "$PKG/.HomeReceiver"
    const val DREAM = "$PKG/.HomeDream"                               // the screensaver's component, as `screensaver_components` writes it
    const val WRITE_SECURE = "android.permission.WRITE_SECURE_SETTINGS"
    /** The Wi-Fi's name is location to Android (8.1+): granted over ADB when TV Home is installed or set up, never asked on the TV. */
    const val GRANT_WIFI = "pm grant $PKG android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1; "
    const val DOCK_MAX = 6
    private const val HIDDEN_MAX = 300
    val WALLS = listOf("night", "ocean", "dusk", "forest", "aurora", "black", "sunset", "ember", "plum", "midnight", "teal", "slate", "graphite", "navy", "wine", "moss", "photo")
    /** Where a client puts the photo (the shell may write there; the app reads it with no permission). */
    const val PHOTO = "/sdcard/Android/data/$PKG/files/wall.jpg"
    val WIDGETS = listOf("system", "usage", "calendar", "lunar", "timer", "weather")
    const val NOTE_MAX = 140
    const val PLACE_MAX = 60
    /** TV Setup's answer to TV Home about app use (its HomeInfo provider): `call(USAGE, "usage")` → Bundle "json". */
    const val USAGE = "content://dev.tvremote.setup.home"
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
            .put("anim", o.optBoolean("anim", true))
            .put("dream", o.optBoolean("dream", false))
            .put("note", o.optString("note").replace(Regex("\\s+"), " ").trim().take(NOTE_MAX))
            .put("place", o.optString("place").replace(Regex("\\s+"), " ").trim().take(PLACE_MAX))
            .put("widgets", JSONArray(o.optJSONArray("widgets")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty().filter { it in WIDGETS }.distinct()))
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
