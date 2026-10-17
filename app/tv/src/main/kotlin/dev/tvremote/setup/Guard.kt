package dev.tvremote.setup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import dev.tvremote.core.adb.Adb
import dev.tvremote.core.adb.Adb.Companion.q
import java.util.Locale

/**
 * Enforces [Rules] on the TV, from RemoteService. A check runs every few seconds to a minute (sooner when a deadline is close):
 * standby when the sleep timer ends or during bedtime (each after a minute's warning on screen, [WarnActivity]), and an app
 * closed once its minutes for the day are used up, and the volume turned down to the highest the hour allows, the moment it
 * goes higher (with a word on screen saying why, so nobody thinks the volume keys broke), and a notice offering to free the memory
 * when it stays fuller than the share set (once per half hour at most, so "Skip" means it). Standby, closing apps and putting the warning over another app all need
 * the TV's own ADB, which this app holds (as for everything else it does).
 */
internal class Guard(private val c: Context, private val adb: () -> Adb?) {
    companion object {
        @Volatile private var running: Guard? = null
        /** The rules changed: check now rather than at the next tick. */
        fun poke() { running?.let { g -> g.ui.removeCallbacks(g.tick); g.ui.post(g.tick) } }
        /** "Turn off now" on the warning screen. */
        fun standbyNow() { running?.standby() }
        /** The TV's volume is not its own to set (sent on to a sound bar or the TV set over HDMI): no limit can hold it. */
        fun volumeFixed(c: Context): Boolean = (c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isVolumeFixed
        /** "Free RAM" on the notice: the apps waiting in the background closed; the MB that came back, null without ADB. */
        fun freeNow(): Long? = running?.free()

        /** (total, available) memory in MB, from /proc/meminfo, which any app may read. */
        fun memory(): Pair<Long, Long> {
            val m = try { java.io.File("/proc/meminfo").readText() } catch (e: Exception) { "" }
            val kb = { k: String -> dev.tvremote.core.ontv.Meminfo.kb(m, k) ?: 0L }
            return kb("MemTotal") / 1024 to kb("MemAvailable") / 1024
        }
    }

    private val ui = Handler(Looper.getMainLooper())
    private val vi = Locale.getDefault().language == "vi"
    private val stats by lazy { Stats(c) }
    @Volatile private var stopped = false
    private var warnedSleep = 0L                                      // the sleep_at already warned about
    private var bedWarnedAt = 0L                                      // bedtime: when this turn-on was warned about, 0 when not yet
    private val nearlyWarned = HashSet<String>()                      // "pkg day": the five-minute notice was shown
    private var lastStop = HashMap<String, Long>()
    private val audio = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var volSaidAt = 0L
    private var ramHigh = 0                                           // checks in a row with the memory past the line
    private var ramQuietUntil = 0L                                    // the notice was shown: not again before this
    private val volume = object : BroadcastReceiver() {                // every change, from any remote: turned down at once
        override fun onReceive(ctx: Context, i: Intent) { capVolume() }
    }

    private val checking = Any()                                     // a poke during a slow check waits for it, then looks at the new rules
    private val tick = object : Runnable {
        override fun run() {
            if (stopped) return
            Thread({
                val next = try { synchronized(checking) { check() } } catch (e: Exception) { 30_000L }
                ui.removeCallbacks(this)                              // a poke meanwhile: still one chain of checks
                ui.postDelayed(this, next.coerceIn(3_000L, 60_000L))
            }, "guard").apply { isDaemon = true }.start()
        }
    }

    fun start() {
        running = this
        ui.postDelayed(tick, 5_000)
        val f = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        if (android.os.Build.VERSION.SDK_INT >= 33) c.registerReceiver(volume, f, Context.RECEIVER_NOT_EXPORTED) else c.registerReceiver(volume, f)
    }

    fun stop() {
        stopped = true
        if (running === this) running = null
        ui.removeCallbacks(tick)
        try { c.unregisterReceiver(volume) } catch (e: Exception) { }
    }

    /** Louder than the range allows: down to its highest, and said on screen each time (at most every 3 s, keys repeat). */
    private fun capVolume() {
        val cap = Rules.volumeCap(Rules.load(c), System.currentTimeMillis()) ?: return
        if (audio.isVolumeFixed) return
        val top = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val most = cap.optInt("max") * top / 100
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) <= most) return
        try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, most, 0) } catch (e: Exception) { return }
        val now = System.currentTimeMillis()
        if (now - volSaidAt < 3_000) return
        volSaidAt = now
        say(t("🔊 Âm lượng tối đa ", "🔊 Volume at most ") + cap.optInt("max") + "% (" + cap.optString("from") + "–" + cap.optString("to") + t(", giới hạn đặt trong TV Remote)", ", a limit set in TV Remote)"))
    }

    /** One look at the rules; returns how long until the next one is worth doing. */
    private fun check(): Long {
        val o = Rules.load(c)
        val now = System.currentTimeMillis()
        val on = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        var next = 60_000L

        // ---- sleep timer
        val at = o.optLong("sleep_at")
        if (at > 0) {
            val left = at - now
            if (left <= 0) {
                Rules.clearSleep(c, at)
                if (on) standby()
            } else {
                if (left <= 61_000 && on && warnedSleep != at) { warnedSleep = at; warn("sleep", at, "") }
                next = minOf(next, if (left > 61_000) left - 60_000 else left)
            }
        }

        // ---- bedtime: a minute after the TV is turned on in the window, standby again
        if (on && Rules.inBedtime(o, now) && now >= o.optLong("snooze_until")) {
            if (bedWarnedAt == 0L) { bedWarnedAt = now; warn("bed", now + 60_000, "") }
            else if (now - bedWarnedAt >= 60_000) { bedWarnedAt = 0; standby() }
            next = minOf(next, 5_000)
        } else bedWarnedAt = 0

        // ---- volume: a range may just have begun with the sound up
        if ((o.optJSONArray("volume")?.length() ?: 0) > 0) {
            ui.post { capVolume() }
            next = minOf(next, 30_000)
        }

        // ---- memory: full at two checks in a row (half a minute apart), not a moment's peak while an app starts
        val ram = Rules.ram(o)
        if (ram.optBoolean("on")) {
            val (total, avail) = memory()
            if (on && total > 0 && (total - avail) * 100 >= total * ram.optInt("pct", 90)) {
                if (++ramHigh >= 2 && now >= ramQuietUntil) { ramQuietUntil = now + 30 * 60_000L; ramHigh = 0; warn("ram", 0, "") }
            } else ramHigh = 0
            next = minOf(next, 30_000)
        }

        // ---- daily app limits, for the app on screen
        val limits = o.optJSONObject("limits")
        if (on && limits != null && limits.length() > 0) {
            val (used, front) = stats.today(now, true)
            val limit = limits.optInt(front)
            if (front.isNotEmpty() && limit > 0) {
                val allowed = (limit + Rules.extra(o, front, now)) * 60L
                val left = allowed - used.optLong(front)
                val key = front + " " + Rules.day(now)
                if (left <= 0) {
                    if (now - (lastStop[front] ?: 0L) > 15_000) { lastStop[front] = now; close(front) }
                } else {
                    if (left <= 300 && nearlyWarned.add(key)) say(t("Còn ${(left + 59) / 60} phút dùng ", "${(left + 59) / 60} min left today for ") + label(front) + t(" hôm nay", ""))
                    next = minOf(next, maxOf(5_000L, left * 1000))
                }
            }
            next = minOf(next, 20_000)                                // another app may come to the front at any time
        }
        return next
    }

    private fun t(v: String, e: String) = if (vi) v else e

    private fun label(pkg: String): String = try {
        c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun sh(cmd: String): String? = try { adb()?.shell(cmd) } catch (e: Exception) { null }

    private fun standby() { sh("input keyevent 223") }

    /** The same as the pages' "Free up RAM" (Manage.free): what waits in the background goes, what is on screen stays. */
    private fun free(): Long? {
        val before = memory().second
        if (sh("am kill-all") == null) return null
        Thread.sleep(1500)
        return maxOf(0L, memory().second - before)
    }

    /** The app's minutes are used up: back to the home screen, the app closed, and a word on screen saying why. */
    private fun close(pkg: String) {
        sh("input keyevent 3; am force-stop " + q(pkg))
        warn("limit", 0, pkg)
    }

    /** The warning screen over whatever is on the TV. Since Android 10 a service may not open a screen by itself; ADB's shell may. */
    private fun warn(kind: String, until: Long, pkg: String) {
        val out = sh("am start -n ${c.packageName}/.WarnActivity --es kind $kind --el until $until --es pkg " + q(pkg) + " 2>&1")
        if (out == null || out.contains("Error")) say(when (kind) {
            "sleep" -> t("TV sẽ tắt sau 1 phút (hẹn giờ)", "The TV turns off in 1 minute (sleep timer)")
            "bed" -> t("Đã tới giờ đi ngủ: TV sẽ tắt sau 1 phút", "Bedtime: the TV turns off in 1 minute")
            "ram" -> t("RAM của TV gần đầy: mở TV Setup → Quản lý trên TV → Sức khỏe để dọn", "The TV's memory is nearly full: TV Setup → Manage on this TV → Health frees it")
            else -> t("Hết giờ dùng ", "Time is up for ") + label(pkg) + t(" hôm nay", " today")
        })
    }

    private fun say(text: String) { ui.post { Toast.makeText(c, text, Toast.LENGTH_LONG).show() } }
}
