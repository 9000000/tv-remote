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
 *   bedtime       between two times of day the TV goes to standby a minute after it is turned on
 *   app limits    minutes per day for an app; when they are used up the app is closed (more can be granted for today)
 *
 * Stored as one JSON object: { sleep_at, bedtime: {on, from, to}, snooze_until, limits: {pkg: minutes}, extra: {day: {pkg: minutes}} }.
 * The remotes reach it through [RulesReceiver] (phone, computer: over ADB) or RemoteService (the TV's own page).
 */
internal object Rules {
    private const val PREFS = "rules"
    private val PKG = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
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
    }

    fun day(t: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))

    /**
     * One action from a remote, then the state they show. Actions ("do"): sleep {minutes} (0 cancels), sleep_add {minutes},
     * bedtime {on, from, to} ("22:00"), snooze {minutes} (bedtime paused, 0 resumes), limit {pkg, minutes} (0 removes),
     * extra {pkg, minutes} (more for today only). An empty "do" only reads.
     */
    @Synchronized
    fun change(c: Context, d: JSONObject): JSONObject {
        val o = load(c)
        val now = System.currentTimeMillis()
        val minutes = d.optInt("minutes").coerceIn(0, 24 * 60)
        when (d.optString("do")) {
            "", "get" -> return state(c)
            "sleep" -> o.put("sleep_at", if (minutes > 0) now + minutes * 60_000L else 0L)
            "sleep_add" -> o.put("sleep_at", maxOf(o.optLong("sleep_at"), now) + minutes * 60_000L)
            "bedtime" -> {
                val from = d.optString("from", "22:00")
                val to = d.optString("to", "06:30")
                if (!HHMM.matches(from) || !HHMM.matches(to)) throw IllegalArgumentException("bad time")
                o.put("bedtime", JSONObject().put("on", d.optBoolean("on")).put("from", pad(from)).put("to", pad(to)))
            }
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
        save(c, o)
        Guard.poke()
        return state(c)
    }

    /** The sleep timer has gone off (unless it was set again meanwhile). */
    @Synchronized
    fun clearSleep(c: Context, at: Long) {
        val o = load(c)
        if (o.optLong("sleep_at") == at) save(c, o.put("sleep_at", 0L))
    }

    private fun pad(hhmm: String): String = hhmm.split(":").let { it[0].padStart(2, '0') + ":" + it[1] }

    /** Minutes of today granted on top of [pkg]'s limit. */
    fun extra(o: JSONObject, pkg: String, now: Long): Int = o.optJSONObject("extra")?.optJSONObject(day(now))?.optInt(pkg) ?: 0

    /** Whether [now] falls in the bedtime window (which may run past midnight). */
    fun inBedtime(o: JSONObject, now: Long): Boolean {
        val b = o.optJSONObject("bedtime") ?: return false
        if (!b.optBoolean("on")) return false
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val mins = { s: String -> s.split(":").let { it[0].toInt() * 60 + it[1].toInt() } }
        val from = mins(b.optString("from", "22:00"))
        val to = mins(b.optString("to", "06:30"))
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
            .put("bedtime", o.optJSONObject("bedtime") ?: JSONObject().put("on", false).put("from", "22:00").put("to", "06:30"))
            .put("in_bedtime", inBedtime(o, now)).put("snooze_until", o.optLong("snooze_until").let { if (it > now) it else 0L })
            .put("limits", limits).put("running", RemoteService.isRunning()).put("wanted", active(c))
            .put("adb", RemoteService.adbState())
    }

    private fun label(c: Context, pkg: String): String = try {
        c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) { pkg.substringAfterLast('.') }
}
