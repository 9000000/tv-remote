package dev.tvremote.setup

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.PowerManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import dev.tvremote.core.WireLog
import dev.tvremote.core.adb.Adb
import dev.tvremote.core.adb.Adb.Companion.q
import dev.tvremote.core.ontv.Foreground
import dev.tvremote.core.ontv.Setup
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The TV's side of [Heavy]: fed the app on screen by Guard's app-lock loop (the usage events it already reads; no poller of its
 * own), and, when a heavy app has been left for [Heavy.WAIT_MS], looks once more at what the TV says (the app in front, sound
 * playing, a media foreground service, the home screen / keyboards / accessibility services) before `am force-stop`.
 * Each close is written to the rules' "heavy_log" ([Rules.heavyLog]) for the page's list.
 */
internal class HeavyMode(private val c: Context, private val adb: () -> Adb?) {
    private val tracker = Heavy.Tracker()
    private val busy = AtomicBoolean()
    private val pm = c.packageManager

    /** One reading of the app on screen ("" unknown), [on] the screen. Cheap: no shell unless a wait is over. */
    fun look(front: String, now: Long, on: Boolean, rules: org.json.JSONObject) {
        if (!Heavy.on(rules)) { tracker.reset(); return }
        val keep = Heavy.keep(rules)
        val homes = homes()
        val ime = ime()
        val due = synchronized(tracker) {
            tracker.look(front, now, on, { Heavy.isHeavy(it, keep) }, { Heavy.transient(it, homes, ime) })
        }
        if (due.isEmpty() || !busy.compareAndSet(false, true)) return
        Thread({
            try { for (p in due) act(p) } catch (e: Exception) { WireLog.all.add("·", "heavy: " + e.message) } finally { busy.set(false) }
        }, "heavy").apply { isDaemon = true }.start()
    }

    private fun act(pkg: String) {
        val now = System.currentTimeMillis()
        val rules = Rules.load(c)
        val keep = Heavy.keep(rules)
        val a = adb()
        val awake = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        if (!Heavy.on(rules) || !awake || a == null) { synchronized(tracker) { tracker.later(pkg, now) }; return }   // off, TV in standby or no ADB: not now
        fun sh(cmd: String) = try { a.shell(cmd) } catch (e: Exception) { "" }
        val front = Foreground.parse(sh(Foreground.CMD))               // the usage events can be a few seconds late
        val v = if (front == pkg) Heavy.Verdict.NEVER else Heavy.verdict(pkg, front, keep, { Setup.mustKeep(it) }, safe(),
            playing(::sh), if (Heavy.foregroundMedia(sh("dumpsys activity services " + q(pkg)))) setOf(pkg) else emptySet())
        synchronized(tracker) {
            when (v) {
                Heavy.Verdict.STOP -> tracker.done(pkg)
                Heavy.Verdict.NEVER -> if (front == pkg) tracker.cancel(pkg) else tracker.done(pkg)
                Heavy.Verdict.LATER -> tracker.later(pkg, now)
            }
        }
        if (v != Heavy.Verdict.STOP) return
        sh("am force-stop " + q(pkg))
        Rules.heavyLog(c, pkg, label(pkg), now)
        WireLog.all.add("·", "heavy: closed $pkg to free memory")
    }

    /** Packages with sound going now: players started (by app id) and media sessions playing. */
    private fun playing(sh: (String) -> String): Set<String> {
        val out = HashSet<String>()
        for (uid in Heavy.startedUids(sh("dumpsys audio | grep AudioPlaybackConfiguration"))) pm.getPackagesForUid(uid)?.let { out.addAll(it) }
        out.addAll(Heavy.playingSessions(sh("dumpsys media_session")))
        return out
    }

    /** Never closed whatever the list says: launchers, keyboards, accessibility services, ourselves, persistent apps. */
    private fun safe(): Set<String> {
        val out = HashSet<String>(homes() + ime())
        out.add(c.packageName)
        val acc = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        for (s in acc.split(':')) s.substringBefore('/').takeIf { it.isNotEmpty() }?.let { out.add(it) }
        for (p in Heavy.LIST.keys) try {
            val i = pm.getApplicationInfo(p, 0)
            if (i.flags and ApplicationInfo.FLAG_PERSISTENT != 0 || i.uid < 10000) out.add(p)   // not FLAG_SYSTEM: YouTube is a system app on many TVs
        } catch (e: Exception) { }
        return out
    }

    private var cachedAt = 0L                                         // the launchers and keyboards hardly change: asked once a minute
    private var homesCache = emptySet<String>()
    private var imeCache = emptySet<String>()

    private fun homes(): Set<String> { refresh(); return homesCache }
    private fun ime(): Set<String> { refresh(); return imeCache }

    private fun refresh() {
        val now = System.currentTimeMillis()
        if (now - cachedAt < 60_000L) return
        cachedAt = now
        homesCache = queryHomes()
        imeCache = queryIme()
    }

    private fun queryHomes(): Set<String> = try {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }.toSet()
    } catch (e: Exception) { emptySet() }

    private fun queryIme(): Set<String> = try {
        (c.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.map { it.packageName }.toSet()
    } catch (e: Exception) { emptySet() }

    private fun label(pkg: String): String = Heavy.label(pkg) ?: try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }
}
