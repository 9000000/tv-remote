package dev.tvremote.home.widgets

import android.view.View
import dev.tvremote.home.R
import dev.tvremote.home.widgets.Widgets.Companion.VIOLET
import java.util.Calendar
import org.json.JSONObject

/** The sleep timer first (it acts sooner), else the bedtime; null when neither is set. Without an answer, TV Setup is too old. */
internal fun Widgets.timer(o: JSONObject?): View? {
    if (o == null) return pill(tile(VIOLET, R.drawable.ic_moon), a.getString(R.string.w_timer), a.getString(R.string.w_need_setup), 1)
    val now = System.currentTimeMillis()
    val at = o.optLong("sleep_at")
    if (at > now) return pill(tile(VIOLET, R.drawable.ic_timer), a.getString(R.string.t_sleep, hm(at)), a.getString(R.string.w_left, span(at - now + 59_999)), 1)
    val bed = o.optJSONObject("bedtime") ?: return null
    if (!bed.optBoolean("on")) return null
    val ranges = bed.optJSONArray("ranges") ?: return null
    val snooze = o.optLong("snooze_until")
    if (o.optBoolean("in_bedtime")) {
        if (snooze > now) return pill(tile(VIOLET, R.drawable.ic_moon), a.getString(R.string.t_snoozed), a.getString(R.string.t_until, hm(snooze)), 1)
        val to = (0 until ranges.length()).mapNotNull { ranges.optJSONObject(it) }.map { it.optString("to") }.firstOrNull().orEmpty()
        return pill(tile(VIOLET, R.drawable.ic_moon), a.getString(R.string.t_bed_now), if (to.isNotEmpty()) a.getString(R.string.t_until, hm(next(to, now))) else "", 1)
    }
    val start = (0 until ranges.length()).mapNotNull { ranges.optJSONObject(it)?.optString("from") }.filter { it.isNotEmpty() }.minOfOrNull { next(it, now) } ?: return null
    return pill(tile(VIOLET, R.drawable.ic_moon), a.getString(R.string.t_bed_at, hm(start)), a.getString(R.string.w_left, span(start - now + 59_999)), 1)
}

/** The next time the clock shows [hhmm], from [now]. */
internal fun Widgets.next(hhmm: String, now: Long): Long {
    val p = hhmm.split(":"); val c = Calendar.getInstance().apply { timeInMillis = now }
    c.set(Calendar.HOUR_OF_DAY, p[0].toIntOrNull() ?: 0); c.set(Calendar.MINUTE, p.getOrNull(1)?.toIntOrNull() ?: 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_MONTH, 1)
    return c.timeInMillis
}
