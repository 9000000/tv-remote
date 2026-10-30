package dev.tvremote.channels

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONArray

/**
 * Reminders for programmes still to come (OK on a future row of the guide). Kept in prefs by channel id + start + title; one
 * inexact alarm each ([fireAt]: a minute late is fine, so no exact-alarm permission); [ReminderBoot] arms them again after a
 * restart or an update. When one fires, [ReminderNotice] decides what to show.
 */
object Reminders {
    /** The channel's id and name, the programme's title and its start (ms). */
    class Item(val id: String, val name: String, val title: String, val start: Long) {
        fun key() = "$id@$start"
    }

    const val LEAD = 2 * 60_000L                                      // the notice comes this long before the start
    const val GRACE = 5 * 60_000L                                     // a reminder that could not fire (TV off) is still kept this long past the start

    /** When to remind about a programme starting at [start]: [lead] before it; with less time left, at the start itself; null when it began. */
    fun fireAt(start: Long, now: Long, lead: Long = LEAD): Long? = when {
        start <= now -> null
        start - lead > now -> start - lead
        else -> start
    }

    /** [list] without the ones long past, and without doubles. */
    fun clean(list: List<Item>, now: Long): List<Item> = list.filter { it.start + GRACE > now }.distinctBy { it.key() }

    /** Every kept reminder as stored, past ones too: only a save may drop them, and a save only runs by the person (see [rearm]). */
    private fun stored(c: Context): List<Item> {
        val a = try { JSONArray(Store.raw(c, "reminders") ?: "[]") } catch (e: Exception) { JSONArray() }
        return (0 until a.length()).mapNotNull { a.optJSONArray(it) }.mapNotNull {
            try { Item(it.getString(0), it.getString(1), it.getString(2), it.getLong(3)) } catch (e: Exception) { null }
        }
    }

    fun all(c: Context, now: Long = System.currentTimeMillis()): List<Item> = clean(stored(c), now).sortedBy { it.start }
    private fun save(c: Context, l: List<Item>) =
        Store.putRaw(c, "reminders", JSONArray().apply { l.forEach { put(JSONArray().put(it.id).put(it.name).put(it.title).put(it.start)) } }.toString())

    fun has(c: Context, id: String, start: Long) = all(c).any { it.id == id && it.start == start }
    /** "🔔 " before a title the person asked to be reminded about, else "". */
    fun mark(c: Context, id: String, start: Long) = if (has(c, id, start)) "🔔 " else ""

    /** Sets the reminder for [s] on [ch], or takes it away; true when it is set now, null when the programme began already. */
    fun toggle(c: Context, ch: Channel, s: Show): Boolean? {
        val item = Item(ch.id, ch.name, s.title, s.start)
        val now = all(c)
        if (now.any { it.key() == item.key() }) { save(c, now.filter { it.key() != item.key() }); cancel(c, item); return false }
        if (fireAt(s.start, System.currentTimeMillis()) == null) return null
        save(c, now + item); arm(c, item)
        return true
    }

    fun remove(c: Context, item: Item) { save(c, stored(c).filter { it.key() != item.key() }); cancel(c, item) }
    /** Used up: taken out when it fires. */
    fun done(c: Context, key: String) { save(c, stored(c).filter { it.key() != key }) }

    /**
     * Arms every kept reminder (after boot / update / the clock being set). It never saves: a box boots with its clock a year ahead,
     * and a save here would have deleted every reminder as "long past"; they are armed again when the clock is corrected (TIME_SET).
     */
    fun rearm(c: Context) { all(c).forEach { arm(c, it) } }

    private fun pending(c: Context, it: Item, flags: Int = 0): PendingIntent? =
        PendingIntent.getBroadcast(c, it.key().hashCode(), Intent(c, ReminderReceiver::class.java).setData(Uri.parse("tvremind://" + Uri.encode(it.key())))
            .putExtra("id", it.id).putExtra("name", it.name).putExtra("title", it.title).putExtra("start", it.start),
            flags or PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))

    private fun arm(c: Context, it: Item) {
        val at = fireAt(it.start, System.currentTimeMillis()) ?: System.currentTimeMillis()    // past but kept (GRACE): at once
        val am = (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        val p = pending(c, it) ?: return
        if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p) else am.set(AlarmManager.RTC_WAKEUP, at, p)
    }
    private fun cancel(c: Context, it: Item) { pending(c, it)?.let { p -> (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(p); p.cancel() } }
}
