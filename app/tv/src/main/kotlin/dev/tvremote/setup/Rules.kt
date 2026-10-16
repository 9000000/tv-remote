package dev.tvremote.setup

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The timers and limits the remotes set, kept and enforced here on the TV (see [Guard]), so nothing else has to stay on:
 *
 *   sleep timer   the TV goes to standby at a set time (a minute's warning on screen, with "+15 min")
 *   bedtime       in any of a few times of day (22:00–06:30, 13:00–14:00) the TV goes to standby a minute after it is turned on
 *   app limits    minutes per day for an app; when they are used up the app is closed (more can be granted for today)
 *   volume        times of day with a highest volume each (22:00–06:00 at most 30%): louder is turned down, with a word on screen
 *   memory        a notice over whatever is on when the TV's memory stays fuller than a set share, to free it or not
 *
 * Stored as one JSON object: { sleep_at, bedtime: {on, ranges: [{from, to}], from, to (the first range, for
 * remotes older than ranges)}, snooze_until, limits: {pkg: minutes}, extra: {day: {pkg: minutes}},
 * volume: [{from, to, max (percent)}], ram: {on, pct (used, percent)} }.
 * The remotes reach it through [RulesReceiver] (phone, computer: over ADB) or RemoteService (the TV's own page).
 */
internal object Rules {
    private const val PREFS = "rules"
    private val PKG = dev.tvremote.core.Pkg.RE
    private val HHMM = Regex("([01]?\\d|2[0-3]):([0-5]\\d)")

    @Synchronized
    fun load(c: Context): JSONObject = try {
        JSONObject(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("json", "{}") ?: "{}")
    } catch (e: Exception) { JSONObject() }

    @Synchronized
    private fun save(c: Context, o: JSONObject) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("json", o.toString()).commit() }

    /** Something here needs the TV Setup service to keep running, even with the web remote off. */
    fun active(c: Context): Boolean {
        val o = load(c)
        return o.optLong("sleep_at") > System.currentTimeMillis() || o.optJSONObject("bedtime")?.optBoolean("on") == true || (o.optJSONObject("limits")?.length() ?: 0) > 0
            || (o.optJSONArray("volume")?.length() ?: 0) > 0 || ram(o).optBoolean("on")
    }

    fun day(t: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))

    /**
     * One action from a remote, then the state they show. Actions ("do"): sleep {minutes} (0 cancels), sleep_add {minutes},
     * bedtime {on, ranges: [{from, to}] ("22:00"), or from, to for one}, snooze {minutes} (bedtime paused, 0 resumes), limit {pkg, minutes} (0 removes),
     * extra {pkg, minutes} (more for today only), volume {ranges: [{from, to, max}]} (the whole list),
     * ram {on, pct} (the memory notice, at pct% used). An empty "do" only reads.
     */
    @Synchronized
    fun change(c: Context, d: JSONObject): JSONObject {
        val o = load(c)
        if (!apply(o, d, System.currentTimeMillis())) return state(c)
        save(c, o)
        Guard.poke()
        return state(c)
    }

    /** [change] without the phone's storage: [o] becomes the new rules. False for a read ("" / "get"), which changes nothing. */
    internal fun apply(o: JSONObject, d: JSONObject, now: Long): Boolean {
        val minutes = d.optInt("minutes").coerceIn(0, 24 * 60)
        when (d.optString("do")) {
            "", "get" -> return false
            "sleep" -> o.put("sleep_at", if (minutes > 0) now + minutes * 60_000L else 0L)
            "sleep_add" -> o.put("sleep_at", maxOf(o.optLong("sleep_at"), now) + minutes * 60_000L)
            "bedtime" -> {
                val given = d.optJSONArray("ranges") ?: JSONArray().put(JSONObject().put("from", d.optString("from", "22:00")).put("to", d.optString("to", "06:30")))
                val list = ranges(given, false)
                o.put("bedtime", bedtime(d.optBoolean("on") && list.length() > 0, list))
            }
            "volume" -> o.put("volume", ranges(d.optJSONArray("ranges") ?: JSONArray(), true))
            "ram" -> o.put("ram", JSONObject().put("on", d.optBoolean("on")).put("pct", d.optInt("pct", ram(o).optInt("pct")).coerceIn(RAM_MIN, RAM_MAX)))
            "snooze" -> o.put("snooze_until", if (minutes > 0) now + minutes * 60_000L else 0L)
            "limit", "extra" -> {
                val pkg = d.optString("pkg")
                if (!PKG.matches(pkg)) throw IllegalArgumentException("bad package name")
                if (d.optString("do") == "limit") {
                    val limits = o.optJSONObject("limits") ?: JSONObject()
                    if (minutes > 0) limits.put(pkg, minutes) else limits.remove(pkg)
                    o.put("limits", limits)
                } else {
                    val extra = o.optJSONObject("extra") ?: JSONObject()
                    val today = extra.optJSONObject(day(now)) ?: JSONObject()
                    today.put(pkg, today.optInt(pkg) + minutes)
                    o.put("extra", JSONObject().put(day(now), today))      // only today's grants are worth keeping
                }
            }
            else -> throw IllegalArgumentException("unknown action")
        }
        return true
    }

    /** At most 8 {from, to} ranges ("H:MM" padded to "HH:MM"), each with a "max" percent when [withMax]; a bad time throws. */
    private fun ranges(given: JSONArray, withMax: Boolean): JSONArray {
        val list = JSONArray()
        for (i in 0 until minOf(given.length(), 8)) {
            val r = given.getJSONObject(i)
            val from = r.optString("from"); val to = r.optString("to")
            if (!HHMM.matches(from) || !HHMM.matches(to)) throw IllegalArgumentException("bad time")
            val one = JSONObject().put("from", pad(from)).put("to", pad(to))
            if (withMax) one.put("max", r.optInt("max", 30).coerceIn(0, 100))
            list.put(one)
        }
        return list
    }

    /** The sleep timer has gone off (unless it was set again meanwhile). */
    @Synchronized
    fun clearSleep(c: Context, at: Long) {
        val o = load(c)
        if (o.optLong("sleep_at") == at) save(c, o.put("sleep_at", 0L))
    }

    const val RAM_MIN = 75
    const val RAM_MAX = 97

    /** The memory notice: {on, pct}, off at 90% until a remote turns it on. */
    fun ram(o: JSONObject): JSONObject = o.optJSONObject("ram") ?: JSONObject().put("on", false).put("pct", 90)

    private fun pad(hhmm: String): String = hhmm.split(":").let { it[0].padStart(2, '0') + ":" + it[1] }

    /** Minutes of today granted on top of [pkg]'s limit. */
    fun extra(o: JSONObject, pkg: String, now: Long): Int = o.optJSONObject("extra")?.optJSONObject(day(now))?.optInt(pkg) ?: 0

    /** {on, ranges, from, to}: from and to are the first range, what remotes older than several ranges read. */
    private fun bedtime(on: Boolean, ranges: JSONArray): JSONObject {
        val first = ranges.optJSONObject(0)
        return JSONObject().put("on", on).put("ranges", ranges).put("from", first?.optString("from") ?: "22:00").put("to", first?.optString("to") ?: "06:30")
    }

    /** The bedtime as stored, a TV Setup older than several ranges having kept only from and to. */
    fun bedtime(o: JSONObject): JSONObject {
        val b = o.optJSONObject("bedtime") ?: return bedtime(false, JSONArray().put(JSONObject().put("from", "22:00").put("to", "06:30")))
        return bedtime(b.optBoolean("on"), b.optJSONArray("ranges")
            ?: JSONArray().put(JSONObject().put("from", b.optString("from", "22:00")).put("to", b.optString("to", "06:30"))))
    }

    /** The bedtime range [now] falls in ({from, to}, which may run past midnight), null outside them or with bedtime off. */
    fun bedNow(o: JSONObject, now: Long): JSONObject? {
        val b = bedtime(o)
        if (!b.optBoolean("on")) return null
        val list = b.getJSONArray("ranges")
        return (0 until list.length()).map { list.getJSONObject(it) }.firstOrNull { within(it.optString("from"), it.optString("to"), now) }
    }

    fun inBedtime(o: JSONObject, now: Long): Boolean = bedNow(o, now) != null

    /** The volume range [now] is in ({from, to, max}), the lowest when two overlap; null outside them all. */
    fun volumeCap(o: JSONObject, now: Long): JSONObject? {
        val list = o.optJSONArray("volume") ?: return null
        var cap: JSONObject? = null
        for (i in 0 until list.length()) {
            val r = list.optJSONObject(i) ?: continue
            if (within(r.optString("from"), r.optString("to"), now) && (cap == null || r.optInt("max") < cap.optInt("max"))) cap = r
        }
        return cap
    }

    /** Whether [now] is between two times of day, a window that may run past midnight; the same from and to is never. */
    private fun within(fromHm: String, toHm: String, now: Long): Boolean {
        if (!HHMM.matches(fromHm) || !HHMM.matches(toHm)) return false
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val mins = { s: String -> s.split(":").let { it[0].toInt() * 60 + it[1].toInt() } }
        val from = mins(fromHm)
        val to = mins(toHm)
        return if (from == to) false else if (from < to) m in from until to else m >= from || m < to
    }

    /** What the pages show. `now` is the TV's clock, so a remote with a wrong clock still counts down right. */
    fun state(c: Context): JSONObject {
        val o = load(c)
        val now = System.currentTimeMillis()
        val used = try { Stats(c).today(now, true).first } catch (e: Exception) { JSONObject() }
        val limits = JSONArray()
        o.optJSONObject("limits")?.let { l ->
            for (pkg in l.keys()) limits.put(JSONObject().put("pkg", pkg).put("label", label(c, pkg)).put("minutes", l.optInt(pkg))
                .put("extra", extra(o, pkg, now)).put("used", used.optLong(pkg) / 60))
        }
        val sleepAt = o.optLong("sleep_at").let { if (it > now) it else 0L }
        return JSONObject().put("ok", true).put("now", now).put("sleep_at", sleepAt)
            .put("bedtime", bedtime(o))
            .put("in_bedtime", inBedtime(o, now)).put("snooze_until", o.optLong("snooze_until").let { if (it > now) it else 0L })
            .put("limits", limits).put("volume", o.optJSONArray("volume") ?: JSONArray()).put("volume_now", volumeCap(o, now) ?: JSONObject.NULL)
            .put("ram", ram(o)).put("volume_fixed", Guard.volumeFixed(c)).put("running", RemoteService.isRunning()).put("wanted", active(c))
            .put("adb", RemoteService.adbState())
    }

    private fun label(c: Context, pkg: String): String = try {
        c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) { pkg.substringAfterLast('.') }
}
