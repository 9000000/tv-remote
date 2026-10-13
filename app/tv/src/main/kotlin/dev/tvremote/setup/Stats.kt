package dev.tvremote.setup

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The TV's own diary, for the History view of the remotes: every few minutes, how long each app has been on screen today
 * and how much memory, CPU and storage is in use. It is kept on the TV, so nothing else has to stay switched on, in a folder
 * the shell user can read, so every remote gets it over ADB:
 *
 *   stats/usage.json      { "2026-10-13": { "com.example.app": seconds, ... }, ... }      the last 90 days
 *   stats/samples.jsonl   [time, screen on, CPU %, memory used MB, memory MB, storage used MB, storage MB, "app on screen"]   30 days
 *
 * Reading which app is on screen needs the "usage access" permission, which the TV grants to our ADB session.
 */
internal class Stats(private val ctx: Context) {
    private val dir = File(ctx.getExternalFilesDir(null), "stats")
    private val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** [cpu] is the CPU load in percent, or null when it could not be read. */
    @Synchronized
    fun tick(cpu: Int?) {
        try {
            dir.mkdirs()
            val now = System.currentTimeMillis()
            val on = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
            val (usage, front) = today(now, on)
            if (usage.length() > 0) {
                val f = File(dir, "usage.json")
                val all = try { JSONObject(f.readText()) } catch (e: Exception) { JSONObject() }
                all.put(day.format(Date(now)), usage)
                val oldest = day.format(Date(now - 90L * 86400000))
                for (k in all.keys().asSequence().toList()) if (k < oldest) all.remove(k)
                write(f, all.toString())
            }
            val mem = ActivityManager.MemoryInfo()
            (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mem)
            val disk = StatFs(Environment.getDataDirectory().path)
            val mb = 1024L * 1024
            val line = JSONArray().put(now / 1000).put(if (on) 1 else 0).put(cpu ?: JSONObject.NULL)
                .put((mem.totalMem - mem.availMem) / mb).put(mem.totalMem / mb)
                .put((disk.totalBytes - disk.availableBytes) / mb).put(disk.totalBytes / mb).put(if (on) front else "")
            val samples = File(dir, "samples.jsonl")
            samples.appendText(line.toString() + "\n")
            if (samples.length() > 900_000) {                         // about 30 days at one line every 5 minutes
                val keep = samples.readLines().let { it.subList(it.size / 4, it.size) }
                write(samples, keep.joinToString("\n") + "\n")
            }
        } catch (ignored: Exception) {                                // a missed sample is not worth a crash
        }
    }

    /** Seconds on screen per app since midnight, and the app on screen now ("" when unknown). */
    private fun today(now: Long, on: Boolean): Pair<JSONObject, String> {
        val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val events = (ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager).queryEvents(start, now)
        val total = HashMap<String, Long>()
        val e = UsageEvents.Event()
        var cur: String? = null
        var since = 0L
        val close = { until: Long -> cur?.let { total[it] = (total[it] ?: 0L) + maxOf(0L, until - since) }; cur = null }
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> if (cur != e.packageName) { close(e.timeStamp); cur = e.packageName; since = e.timeStamp }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (cur == e.packageName) close(e.timeStamp)
                16 -> close(e.timeStamp)                              // SCREEN_NON_INTERACTIVE: the TV went to standby with the app still open
            }
        }
        val front = cur ?: ""
        if (on) close(now)
        val out = JSONObject()
        for ((pkg, ms) in total) if (ms >= 1000) out.put(pkg, ms / 1000)
        return Pair(out, front)
    }

    private fun write(f: File, text: String) {
        val tmp = File(f.path + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(f)
    }
}
