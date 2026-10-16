package dev.tvremote.setup

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.tvremote.core.Adb
import dev.tvremote.core.SetupUpdate
import dev.tvremote.update.Release
import java.io.File

/**
 * TV Setup keeps itself up to date, from RemoteService: it looks for a new version every few hours, downloads it, and installs
 * it only at a moment nobody would notice ([busy] says why not yet): the install closes TV Setup for some seconds, which stops
 * the web remote, the picture and the timers' checks meanwhile. What is on the TV's screen is not touched.
 * The install is [SetupUpdate]'s: a new version that does not start well is replaced by the old one, data and all, and never
 * tried again. Android 9 and older cannot put the old one back: there a version that fails is only noted, and the phone or
 * computer app, which sees TV Setup missing or broken, offers to install the next one (SetupBanner).
 */
internal class AutoUpdate(private val c: Context, private val version: String, private val adb: () -> Adb?, private val busy: () -> String?) {
    companion object {
        private const val PREFS = "remote"
        fun on(c: Context): Boolean = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("auto_update", true)
        fun set(c: Context, on: Boolean) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("auto_update", on).apply() }
        private const val MIN = 60_000L
    }

    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var stopped = false
    private var found: Release? = null
    private var checkedAt = 0L
    private var apk: File? = null
    private val dir by lazy { c.getExternalFilesDir("update") }       // shared storage: the shell, which installs it, can read it

    private val tick = object : Runnable {
        override fun run() {
            if (stopped) return
            Thread({
                val next = try { step() } catch (e: Exception) { 30 * MIN }   // offline, GitHub busy: later
                if (!stopped) ui.postDelayed(this, next)
            }, "auto-update").apply { isDaemon = true }.start()
        }
    }

    fun start() {
        val old = System.currentTimeMillis() - 3600_000              // left from an update that is over by now
        dir?.listFiles()?.forEach { if (it.lastModified() < old) it.delete() }
        ui.postDelayed(tick, 10 * MIN)                                // not while the TV is just starting up
    }

    fun stop() { stopped = true; ui.removeCallbacks(tick) }

    /** One step towards an update; returns how long until the next is worth trying. */
    private fun step(): Long {
        if (!on(c)) return 60 * MIN
        val now = System.currentTimeMillis()
        if (found == null && now - checkedAt < 6 * 60 * MIN) return 60 * MIN
        if (now - checkedAt >= 6 * 60 * MIN) {
            checkedAt = now
            val r = Release.newer(version, "tv", "tv-setup.apk")
            if (r?.version != found?.version) apk = null
            found = r
        }
        val r = found ?: return 60 * MIN
        val a = adb() ?: return 10 * MIN
        val s = SetupUpdate.state(a)
        if (SetupUpdate.bad(s, r.version)) return 6 * 60 * MIN        // it broke here once: wait for the next version
        if (SetupUpdate.busy(s)) return 10 * MIN
        if (s.optString("state") == "failed" && s.optString("version") == r.version) {   // the TV refused it (storage full, a bad file...): not every half hour,
            apk?.delete(); apk = null                                 // and downloaded again next time
            return 6 * 60 * MIN
        }
        val file = apk ?: File(dir ?: return 60 * MIN, "tv-setup-" + r.version + ".apk").also {
            r.download(it, null)                                      // deletes what does not match its signed checksum
            apk = it
        }
        if (!file.isFile) { apk = null; return 10 * MIN }
        busy()?.let { return 10 * MIN }
        // a phone or computer watching or controlling the TV through its own scrcpy session (ours is closed by now: see busy)
        if (a.shell("ps -A -o ARGS 2>/dev/null | grep -c '[c]om.genymobile.scrcpy.Server'").trim().let { it != "" && it != "0" }) return 10 * MIN
        SetupUpdate.start(a, file.absolutePath, r.version)
        return 30 * MIN                                               // the install closes this app; this is for a failed one
    }
}
