package dev.tvremote.core.ontv
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Giving a TV another home screen, and taking it back. Any launcher, not FLauncher only (Optimize TV and the launcher gallery
 * use this one code). What it does, in the order that kept TVs working (dev/bloat-sources.md):
 *  1. the new launcher is opened once, so Home is never empty;
 *  2. the TV's own home screens that are on go off, the setup wizard first (with launcherx off alone Home lands on the wizard's
 *     RecoveryActivity and Google TV restarts about every minute);
 *  3. it is made the home LAST (Android forgets that choice whenever the set of home screens changes);
 *  4. Home is asked where it goes: anywhere else and everything is put back at once.
 * What was turned off, and the home before, are kept ON THE TV in [REC] before anything is touched, so a way back exists even if the
 * phone or computer that started it is gone: [rollback] puts back exactly that. After the swap the record waits for the person's
 * Keep ("pending", [CONFIRM_SEC] seconds, like a display-resolution change), and [LauncherWatch] keeps looking for a launcher that
 * crashes or a Home that lands nowhere.
 */
class LauncherSwap(private val sh: (String) -> String, private val log: (String) -> Unit = { }) {
    companion object {
        const val REC = "/data/local/tmp/tvr-launcher.json"
        const val CONFIRM_SEC = 30
        private const val HOMES = "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null"
        private const val RESOLVE_HOME = "cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN"
        private val PKG = Regex("[A-Za-z][\\w]*(\\.[\\w]+)+")

        fun last(out: String): String = out.split("\n").dropLastWhile { it.isBlank() }.lastOrNull()?.trim() ?: ""

        /** Home landing on something that is not a launcher a person can use: Android's picker, the setup wizard, the fallback. */
        fun badHome(comp: String): Boolean {
            val pkg = comp.substringBefore('/')
            return pkg.isEmpty() || pkg == "android" || pkg in Setup.WIZARD || "FallbackHome" in comp
        }
    }

    /** What is kept on the TV: [state] swapping / pending (waiting for Keep) / kept / rolledback; times are the TV's own clock (seconds). */
    class Rec(val pkg: String, val comp: String, val prev: String, val off: List<String>, val at: Long, val state: String, val until: Long, val why: String) {
        fun json(): JSONObject = JSONObject().put("pkg", pkg).put("comp", comp).put("prev", prev).put("off", JSONArray(off))
            .put("at", at).put("state", state).put("until", until).put("why", why)
        fun with(state: String, until: Long = this.until, why: String = this.why) = Rec(pkg, comp, prev, off, at, state, until, why)
        val active get() = state == "swapping" || state == "pending" || state == "kept"
    }

    fun tvNow(): Long = sh("date +%s").trim().toLongOrNull() ?: (System.currentTimeMillis() / 1000)

    fun state(): Rec? {
        val raw = sh("cat $REC 2>/dev/null").trim()
        if (raw.isEmpty()) return null
        return try {
            val o = JSONObject(raw)
            Rec(o.getString("pkg"), o.optString("comp"), o.optString("prev"), o.optJSONArray("off")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                o.optLong("at"), o.optString("state"), o.optLong("until"), o.optString("why"))
        } catch (e: org.json.JSONException) { null }
    }

    private fun save(r: Rec) { sh("echo '" + r.json().toString().replace("'", "") + "' > $REC") }

    private fun enabled(): Set<String> =
        sh("pm list packages -e").split("\n").map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toSet()

    private fun homes(): List<String> = sh(HOMES).split("\n").map { it.trim() }.filter { it.contains('/') && !it.startsWith("priority") }

    fun homeNow(): String = last(sh(RESOLVE_HOME))

    /** Turned off one command per shell call (chained, some TVs' pm stopped after the first), and kept for Setup's Undo. */
    private fun turnOff(p: String) {
        sh("pm disable-user --user 0 $p")
        sh("am force-stop $p")
        sh("grep -qx '$p' ${Setup.OFF_LIST} 2>/dev/null || echo '$p' >> ${Setup.OFF_LIST}")
    }

    /**
     * Makes [pkg] (installed) the home screen. True when Home now lands on it, with the record [state] "pending" for [confirmSec] seconds
     * (0: "kept" at once). False when it did not: everything is back as it was, the record says rolledback.
     */
    @Throws(IOException::class)
    fun take(pkg: String, confirmSec: Int = 0): Boolean {
        if (!PKG.matches(pkg)) throw IOException("bad package name")
        val ours = homes().firstOrNull { it.startsWith("$pkg/") } ?: run { log("no home activity"); return false }   // not a launcher
        val prev = homeNow()
        sh("am start -n $ours")                                      // opened once before the old one goes, so Home is never empty
        val on = enabled()
        val gone = (Setup.WIZARD + Setup.LAUNCHER).filter { it in on && it != pkg }   // only what is on: Google TV's tvlauncher is off from the factory
        val now = tvNow()
        save(Rec(pkg, ours, prev, gone, now, "swapping", 0, ""))      // before the first thing goes off
        for (p in gone) turnOff(p)
        sh("cmd package set-home-activity $ours 2>/dev/null")
        if (homeNow().substringBefore('/') == pkg) {
            val r = state()?.with(if (confirmSec > 0) "pending" else "kept", if (confirmSec > 0) tvNow() + confirmSec else 0) ?: return true
            save(r)
            return true
        }
        rollback("home")
        return false
    }

    /** The person's Keep: the swap stays, and only the watch is left. */
    fun keep() { state()?.takeIf { it.state == "pending" }?.let { save(it.with("kept")) } }

    /**
     * Back to what the record says: the home screens turned off come back (the launchers before the wizard), the home before is chosen
     * again, Home is asked once more. [why]: crash / home / timeout / user / interrupted, for the notice. No record (a swap by an older
     * version or by hand): the TV's own launchers that are installed but off come back, as Undo does.
     */
    @Throws(IOException::class)
    fun rollback(why: String) {
        val r = state()
        val off = if (r != null) r.off else (Setup.LAUNCHER.toList() + Setup.WIZARD).filter { it in installedOff() }
        for (p in off.reversed()) sh("pm enable --user 0 $p")        // home screens before the wizard
        val back = backTo(r, off)
        if (back != null) sh("cmd package set-home-activity $back 2>/dev/null")
        sh("am start -a android.intent.action.MAIN -c android.intent.category.HOME")
        if (r != null) save(r.with("rolledback", 0, why))
        log("rolled back: $why")
    }

    private fun installedOff(): Set<String> =
        sh("pm list packages -d 2>/dev/null").split("\n").map { it.removePrefix("package:").trim() }.toSet()

    /** The home screen to choose again: the one before, else the first of the TV's own that was turned off (Android TV's tvlauncher). */
    private fun backTo(r: Rec?, off: List<String>): String? {
        val all = homes()
        val prev = r?.prev ?: ""
        if (prev.contains('/') && !badHome(prev) && prev.substringBefore('/') != r?.pkg) all.firstOrNull { it == prev }?.let { return it }
        return all.firstOrNull { it.substringBefore('/') in off && it.substringBefore('/') !in Setup.WIZARD }
    }

    /** What Undo of Optimize TV leaves behind: nothing kept about a home swap. */
    fun forget() { sh("rm -f $REC") }
}

/**
 * The watchdog over a swap ([LauncherSwap.REC]): the TV app runs [check] every few seconds while a record is active. A launcher that
 * crashes twice within five minutes, a Home that resolves to nothing usable, or a confirmation nobody answered ends in [LauncherSwap.rollback],
 * so a launcher that does not work can never be the last thing a person had on their TV. [sh] is the TV's own shell (TV Setup's ADB to itself).
 */
class LauncherWatch(private val sh: (String) -> String) {
    companion object {
        const val WINDOW_SEC = 300L
        const val CRASHES = 2
        private const val GRACE = 8L                                  // the confirmation screen's own timeout comes first
        private const val STALE = 90L                                 // a swap that was never finished

        /** The seconds at which [pkg] crashed, from `logcat -b crash -v epoch`: a Java crash names its process, a native one is ">>> pkg <<<". */
        fun crashes(logcat: String, pkg: String): List<Long> = logcat.lineSequence().mapNotNull { l ->
            val m = Regex("^\\s*(\\d{9,})\\.\\d+\\s.*?(?:Process: ([\\w.]+), PID|>>> ([\\w.]+) <<<)").find(l) ?: return@mapNotNull null
            if ((m.groupValues[2].ifEmpty { m.groupValues[3] }) == pkg) m.groupValues[1].toLong() else null
        }.toList()

        /** Whether [times] holds [CRASHES] crashes within [WINDOW_SEC] of each other, none before [since]. */
        fun looping(times: List<Long>, since: Long): Boolean {
            val t = times.filter { it >= since }.sorted()
            return t.size >= CRASHES && (0..t.size - CRASHES).any { t[it + CRASHES - 1] - t[it] <= WINDOW_SEC }
        }
    }

    private val swap = LauncherSwap(sh)

    /** Whether there is a record to watch (a swap that is not over). */
    fun active(): Boolean = swap.state()?.active == true

    /** One look. The reason it rolled back (crash / home / timeout / interrupted), or null: nothing to do or all well. */
    @Throws(IOException::class)
    fun check(): String? {
        val r = swap.state()?.takeIf { it.active } ?: return null
        val now = swap.tvNow()
        val why = when {
            r.state == "swapping" -> if (now - r.at > STALE) "interrupted" else null
            r.state == "pending" && now > r.until + GRACE -> "timeout"
            LauncherSwap.badHome(swap.homeNow()) -> "home"
            looping(crashes(sh("logcat -b crash -d -v epoch 2>/dev/null"), r.pkg), r.at) -> "crash"
            else -> null
        } ?: return null
        swap.rollback(why)
        return why
    }
}
