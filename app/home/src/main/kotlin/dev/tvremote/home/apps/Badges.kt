package dev.tvremote.home.apps

import android.content.Context
import android.net.Uri
import dev.tvremote.home.HomeConfig
import org.json.JSONObject

/**
 * The little marks on the tiles, from what TV Setup keeps: a lock on an app that cannot be opened now (a PIN on it, its day's
 * limit used up, bedtime) and an hourglass with the time left on one that has a limit today. TV Setup answers only this app
 * (its signing key); an older TV Setup does not know the question, and then there are no marks, nothing else changes.
 */
object Badges {
    /** [minutes] left today (>= 0) of a limit, or [locked], or [power]: the app TV Home's setting opens at power-on (its own
     *  config, no TV Setup needed to show it). At most one is shown: the lock, then the time left, then the power mark. */
    data class Mark(val locked: Boolean, val minutes: Int, val power: Boolean = false)

    /** The marks by app; [bed]: bedtime is on, which locks every app; [kids]: kids mode is on, which locks every app but [open]. */
    class Info(val by: Map<String, Mark>, val bed: Boolean, val kids: Boolean = false, val open: Set<String> = emptySet()) {
        fun of(pkg: String): Mark? {
            // a live tile does its job inside TV Home (Free RAM, the timer) and opens TV Setup's screens behind its own PIN: no
            // lock on TV Setup's tiles, which still worked under one; another app's tile opens that app, so it has that app's mark
            if (pkg.startsWith("tile:")) return HomeConfig.tileProvider(pkg).let { p -> if (p == SETUP) null else of(p) }
            return if (bed || (kids && pkg !in open)) LOCK else by[pkg]
        }
    }

    val LOCK = Mark(true, 0)
    private const val SETUP = "dev.tvremote.setup"
    val POWER = Mark(false, 0, power = true)
    val NONE = Info(emptyMap(), false)

    /** TV Setup's {limits: [{pkg, min, used (s)}], locked: [pkg], bed, kids, open: [pkg]}; a limit with nothing left is a lock. */
    fun parse(o: JSONObject): Info {
        val by = HashMap<String, Mark>()
        val limits = o.optJSONArray("limits")
        if (limits != null) for (i in 0 until limits.length()) {
            val l = limits.optJSONObject(i) ?: continue
            val pkg = l.optString("pkg"); if (pkg.isEmpty() || l.optInt("min") <= 0) continue
            val left = l.optInt("min") * 60L - l.optLong("used")
            by[pkg] = if (left <= 0) LOCK else Mark(false, ((left + 59) / 60).toInt())
        }
        val locked = o.optJSONArray("locked")
        if (locked != null) for (i in 0 until locked.length()) by[locked.optString(i)] = LOCK
        val open = o.optJSONArray("open")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() } ?: emptySet()
        return Info(by, o.optBoolean("bed"), o.optBoolean("kids"), open)
    }

    /** [info] with TV Home's own locked apps ([HomeLock]) marked as locked too. */
    fun withOwn(info: Info, own: Set<String>): Info = if (own.isEmpty()) info else Info(info.by + own.associateWith { LOCK }, info.bed, info.kids, info.open)

    /** "20m", "1h05": the time left in a few characters ([m], [h]: the units in the app's language). */
    fun label(minutes: Int, m: String, h: String) =
        if (minutes < 60) "$minutes$m" else "${minutes / 60}$h${(minutes % 60).toString().padStart(2, '0')}"

    /** Asks TV Setup; null when it does not answer (not installed, older, or not this app's turn to know). Off the main thread. */
    fun read(c: Context): Info? = try {
        c.contentResolver.call(Uri.parse(HomeConfig.USAGE), "badges", null, null)?.getString("json")?.let { parse(JSONObject(it)) }
    } catch (e: Exception) { null }
}
