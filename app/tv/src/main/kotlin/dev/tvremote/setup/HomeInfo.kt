package dev.tvremote.setup

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What TV Home's "usage" card shows (HomeConfig.USAGE), counted here so it is the same number the app limits hold to:
 *   {week: [{pkg, sec}] the most used apps of the last 7 days with today, days: [sec] each day's total (today first), limits: [{pkg, min (today's, extra included), used (sec)}]}
 * the marks on the tiles ("badges"): {limits: [{pkg, min, used}] as above, locked: [pkg] behind a PIN, bed: bedtime is on now}, TV Home needs none of it,
 * its "timer" card: {sleep_at, bedtime: {on, ranges}, in_bedtime, snooze_until} (Rules.state's),
 * and the system card's CPU ring, "cpu": {busy (0..1 over one second), cores}; Android 8+ keeps /proc/stat from apps, the shell reads it.
 * and "power" (HomePower: screen off, standby, power off; `arg` the action, "result" ok / noadb / failed).
 * Which apps a family watches is nobody else's business: only TV Home, signed by this project's key (or by this app's own,
 * a build signed with one key for both), gets an answer.
 */
class HomeInfo : ContentProvider() {
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val c = context ?: return null
        if (method !in setOf("usage", "timer", "cpu", "badges", "power", "keys")) return null
        // TV Home's package name signed by another key (built elsewhere, a fork): said as such, nothing else given, so the
        // button tells the person to install both from one place instead of "update TV Setup", which would not help
        if (!trusted(c, Binder.getCallingUid())) return if (fromHome(c, Binder.getCallingUid())) Bundle().apply { putString("result", "untrusted") } else null
        if (method == "power") {                                       // the power button: `arg` is one of HomePower's three words, nothing else is run
            val token = Binder.clearCallingIdentity()
            try { return Bundle().apply { putString("result", HomePower.run(c, arg.orEmpty())) } } finally { Binder.restoreCallingIdentity(token) }
        }
        if (method == "keys") return keys(c, arg)
        val now = System.currentTimeMillis()
        if (method == "cpu") {                                         // the last measure at once, the next one started: a binder call never waits on ADB
            measure()
            return cpu?.let { Bundle().apply { putString("json", it) } }
        }
        if (method == "timer") {
            val o = Rules.load(c)
            return Bundle().apply { putString("json", JSONObject().put("sleep_at", o.optLong("sleep_at").let { if (it > now) it else 0L })
                .put("bedtime", Rules.bedtime(o)).put("in_bedtime", Rules.inBedtime(o, now))
                .put("snooze_until", o.optLong("snooze_until").let { if (it > now) it else 0L }).toString()) }
        }
        if (method == "badges") {                                      // the tiles' marks: little to ask, answered each minute
            val rules = Rules.load(c)
            val token = Binder.clearCallingIdentity()
            try {
                val on = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
                val today = try { Stats(c).today(now, on).first } catch (e: Exception) { JSONObject() }
                val limits = JSONArray()
                rules.optJSONObject("limits")?.let { l ->
                    for (pkg in l.keys()) limits.put(JSONObject().put("pkg", pkg).put("min", l.optInt(pkg) + Rules.extra(rules, pkg, now)).put("used", today.optLong(pkg)))
                }
                return Bundle().apply { putString("json", JSONObject().put("limits", limits).put("locked", JSONArray(Rules.locked(rules)))
                    .put("bed", Rules.inBedtime(rules, now)).toString()) }
            } finally { Binder.restoreCallingIdentity(token) }
        }
        val token = Binder.clearCallingIdentity()                      // our own permissions from here (usage access)
        try {
            val on = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
            val today = try { Stats(c).today(now, on).first } catch (e: Exception) { JSONObject() }
            val all = try { JSONObject(File(c.getExternalFilesDir(null), "stats/usage.json").readText()) } catch (e: Exception) { JSONObject() }
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val perDay = (0..6).map { d -> if (d == 0) today else all.optJSONObject(day.format(Date(now - d * 86_400_000L))) ?: JSONObject() }
            val week = HashMap<String, Long>()
            for (o in perDay) for (k in o.keys()) week[k] = (week[k] ?: 0L) + o.optLong(k)
            val homes = try {                                          // a home screen (Google TV's, TV Home) is where people pass, not what they watch
                c.packageManager.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME), 0)
                    .map { it.activityInfo.packageName }.toSet()
            } catch (e: Exception) { emptySet() }
            val pm = c.packageManager
            val opens = { p: String -> (if (Build.VERSION.SDK_INT >= 21) pm.getLeanbackLaunchIntentForPackage(p) else null) ?: pm.getLaunchIntentForPackage(p) }
            val top = JSONArray()                                      // apps one opens: not the system's screens (systemui) nor a home screen
            for ((pkg, s) in week.entries.filter { it.key != c.packageName && it.key !in homes && it.value >= 60 && opens(it.key) != null }.sortedByDescending { it.value }.take(5).map { it.key to it.value })
                top.put(JSONObject().put("pkg", pkg).put("sec", s))
            val counted = { k: String -> k != c.packageName && k !in homes && opens(k) != null }
            val days = JSONArray()                                     // each day's total, today first: the card's columns
            for (o in perDay) days.put(o.keys().asSequence().filter(counted).sumOf { o.optLong(it) })
            val rules = Rules.load(c)
            val limits = JSONArray()
            rules.optJSONObject("limits")?.let { l ->
                for (pkg in l.keys()) limits.put(JSONObject().put("pkg", pkg).put("min", l.optInt(pkg) + Rules.extra(rules, pkg, now)).put("used", today.optLong(pkg)))
            }
            return Bundle().apply { putString("json", JSONObject().put("week", top).put("days", days).put("limits", limits).toString()) }
        } finally { Binder.restoreCallingIdentity(token) }
    }

    /**
     * TV Home's keys and power-on app ([HomeKeys]): `arg` is its config (HomeConfig, cleaned again here); empty only asks. The answer
     * {free: the dead keys TV Home may offer, can: this app may run its key filter, wanted: it is not switched off here}.
     * The work runs on a thread: switching the filter on writes secure settings and a binder call must not wait on that.
     */
    private fun keys(c: Context, arg: String?): Bundle {
        if (!arg.isNullOrEmpty() && HomeKeys.save(c, arg)) {
            Thread({
                KeyFix.sync(c); KeyFixService.poke()
                if (HomeKeys.active(c) && !RemoteService.isRunning()) try { RemoteService.start(c, true) } catch (e: Exception) { }   // the wake-up hearing lives there
            }, "home-keys").apply { isDaemon = true }.start()
        }
        val free = KeyFix.free(KeyFix.systemMap()) { KeyFix.alive(c, it) }
        return Bundle().apply { putString("json", JSONObject().put("free", JSONArray(free.sorted())).put("can", KeyFix.can(c)).put("wanted", KeyFix.wanted(c)).toString()) }
    }

    /** One second of /proc/stat through the shell, on its own thread, never two at once (a dial may wait minutes for the TV's question). */
    private fun measure() {
        if (!measuring.compareAndSet(false, true)) return
        Thread({
            try {
                val a = RemoteService.shell("head -1 /proc/stat") ?: return@Thread
                Thread.sleep(1000)
                val b = RemoteService.shell("head -1 /proc/stat") ?: return@Thread
                val busy = busy(a, b) ?: return@Thread
                cpu = JSONObject().put("busy", busy.toDouble()).put("cores", Runtime.getRuntime().availableProcessors()).toString()
            } finally { measuring.set(false) }
        }, "home-cpu").apply { isDaemon = true }.start()
    }

    private fun fromHome(c: Context, uid: Int) = c.packageManager.getPackagesForUid(uid)?.contains(HOME) == true

    private fun trusted(c: Context, uid: Int): Boolean {
        val pm = c.packageManager
        if (!fromHome(c, uid)) return false
        val mine = certs(pm, c.packageName)
        return certs(pm, HOME).any { it == HOME_CERT || it in mine }
    }

    @Suppress("DEPRECATION")
    private fun certs(pm: PackageManager, pkg: String): List<String> = try {
        val sigs = if (Build.VERSION.SDK_INT >= 28) pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            else pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        sigs.orEmpty().map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }
    } catch (e: Exception) { emptyList() }

    override fun onCreate() = true
    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    internal companion object {
        @Volatile var cpu: String? = null                              // {busy, cores}, the latest measure
        val measuring = java.util.concurrent.atomic.AtomicBoolean()

        /** The busy share between two "cpu " lines of /proc/stat (user nice system idle iowait ...): idle and waiting for the disk are not busy. */
        fun busy(a: String, b: String): Float? {
            fun nums(s: String) = s.lines().firstOrNull { it.startsWith("cpu ") }?.trim()?.split(Regex("\\s+"))?.drop(1)?.mapNotNull { it.toLongOrNull() }
            val x = nums(a) ?: return null; val y = nums(b) ?: return null
            if (x.size < 4 || y.size < 4) return null
            val idle = { v: List<Long> -> v[3] + v.getOrElse(4) { 0 } }
            val all = y.sum() - x.sum()
            if (all <= 0) return null
            return ((all - (idle(y) - idle(x))).toFloat() / all).coerceIn(0f, 1f)
        }
        const val HOME = "dev.tvremote.home"
        const val HOME_CERT = "657aabacfdccbc22ff1f61138df7efd79fae765df742f7d15d80dac84c7d81b3"   // app/home/signing.jks, alias tvhome
    }
}
