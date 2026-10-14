package dev.tvremote.setup

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import dev.tvremote.core.Adb
import dev.tvremote.core.Adb.Companion.q
import java.util.Locale

/**
 * Enforces [Rules] on the TV, from RemoteService. A check runs every few seconds to a minute (sooner when a deadline is close):
 * standby when the sleep timer ends or during bedtime (each after a minute's warning on screen, [WarnActivity]), and an app
 * closed once its minutes for the day are used up. Standby, closing apps and putting the warning over another app all need
 * the TV's own ADB, which this app holds (as for everything else it does).
 */
internal class Guard(private val c: Context, private val adb: () -> Adb?) {
    companion object {
        @Volatile private var running: Guard? = null
        /** The rules changed: check now rather than at the next tick. */
        fun poke() { running?.let { g -> g.ui.removeCallbacks(g.tick); g.ui.post(g.tick) } }
        /** "Turn off now" on the warning screen. */
        fun standbyNow() { running?.standby() }
    }

    private val ui = Handler(Looper.getMainLooper())
    private val vi = Locale.getDefault().language == "vi"
    private val stats by lazy { Stats(c) }
    @Volatile private var stopped = false
    private var warnedSleep = 0L                                      // the sleep_at already warned about
    private var bedWarnedAt = 0L                                      // bedtime: when this turn-on was warned about, 0 when not yet
    private val nearlyWarned = HashSet<String>()                      // "pkg day": the five-minute notice was shown
    private var lastStop = HashMap<String, Long>()

    private val tick = object : Runnable {
        override fun run() {
            if (stopped) return
            Thread({
                val next = try { check() } catch (e: Exception) { 30_000L }
                ui.removeCallbacks(this)                              // a poke meanwhile: still one chain of checks
                ui.postDelayed(this, next.coerceIn(3_000L, 60_000L))
            }, "guard").apply { isDaemon = true }.start()
        }
    }

    fun start() { running = this; ui.postDelayed(tick, 5_000) }

    fun stop() { stopped = true; if (running === this) running = null; ui.removeCallbacks(tick) }

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
            else -> t("Hết giờ dùng ", "Time is up for ") + label(pkg) + t(" hôm nay", " today")
        })
    }

    private fun say(text: String) { ui.post { Toast.makeText(c, text, Toast.LENGTH_LONG).show() } }
}
