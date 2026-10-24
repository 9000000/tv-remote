package dev.tvremote.home.widgets

import android.view.View
import dev.tvremote.home.R
import dev.tvremote.home.widgets.Widgets.Companion.VIOLET
import java.util.Calendar
import org.json.JSONObject

/** The sleep timer first (it acts sooner), else the bedtime; null when neither is set. Without an answer, TV Setup is too old. */
internal fun Widgets.timer(o: JSONObject?): View? {
    val (title, sub) = timerLine(o) ?: return null
    return pill(tile(VIOLET, if (o != null && o.optLong("sleep_at") > System.currentTimeMillis()) R.drawable.ic_timer else R.drawable.ic_moon), title, sub, 1)
}

/** What the timer card says: (title, the line under it); both looks draw this, null when there is nothing to show. */
internal fun Widgets.timerLine(o: JSONObject?): Pair<String, String>? {
    if (o == null) return a.getString(R.string.w_timer) to a.getString(R.string.w_need_setup)
    val now = System.currentTimeMillis()
    val at = o.optLong("sleep_at")
    if (at > now) return a.getString(R.string.t_sleep, hm(at)) to a.getString(R.string.w_left, span(at - now + 59_999))
    val bed = o.optJSONObject("bedtime") ?: return null
    if (!bed.optBoolean("on")) return null
    val ranges = bed.optJSONArray("ranges") ?: return null
    val snooze = o.optLong("snooze_until")
    if (o.optBoolean("in_bedtime")) {
        if (snooze > now) return a.getString(R.string.t_snoozed) to a.getString(R.string.t_until, hm(snooze))
        val to = (0 until ranges.length()).mapNotNull { ranges.optJSONObject(it) }.map { it.optString("to") }.firstOrNull().orEmpty()
        return a.getString(R.string.t_bed_now) to (if (to.isNotEmpty()) a.getString(R.string.t_until, hm(next(to, now))) else "")
    }
    val start = (0 until ranges.length()).mapNotNull { ranges.optJSONObject(it)?.optString("from") }.filter { it.isNotEmpty() }.minOfOrNull { next(it, now) } ?: return null
    return a.getString(R.string.t_bed_at, hm(start)) to a.getString(R.string.w_left, span(start - now + 59_999))
}

/** The next time the clock shows [hhmm], from [now]. */
internal fun Widgets.next(hhmm: String, now: Long): Long {
    val p = hhmm.split(":"); val c = Calendar.getInstance().apply { timeInMillis = now }
    c.set(Calendar.HOUR_OF_DAY, p[0].toIntOrNull() ?: 0); c.set(Calendar.MINUTE, p.getOrNull(1)?.toIntOrNull() ?: 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_MONTH, 1)
    return c.timeInMillis
}
