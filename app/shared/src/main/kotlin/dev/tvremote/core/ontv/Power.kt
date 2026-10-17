package dev.tvremote.core.ontv
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Health tab's Sleep view: how the TV sleeps and wakes, and whether what keeps it reachable (Tailscale, TV Setup) survives.
 * Every TV says some of it its own way, so like [Sensors] each part is left out when the shell printed nothing for it:
 *  - since boot: how long, how much of it in deep sleep (the chip suspended: nothing answers), why it booted;
 *  - the last sleep and wake and their reasons, the timers that put it to sleep, who holds a wake lock now;
 *  - a maker's own count of deep sleeps ([DEEP]: TCL counts its "STR" ones);
 *  - Tailscale and TV Setup: running or not, and when the TV killed them (Android's log, TCL's TGuard);
 *  - TV Setup's power.jsonl: the screen going on and off, TV Setup starting, Tailscale's VPN coming and going.
 */
object Power {
    private const val SEP = "printf '\\043~\\043\\n'"
    private const val STATS = "/sdcard/Android/data/dev.tvremote.setup/files/stats"
    private const val WATCHED = "com.tailscale.ipn|dev.tvremote.setup"

    /** Settings that decide when the TV sleeps, and what it keeps up meanwhile: "table name". */
    private val SETTINGS = listOf("secure sleep_timeout", "secure attentive_timeout", "system screen_off_timeout", "global stay_on_while_plugged_in",
        "global hdmi_control_auto_device_off_enabled", "global wifi_sleep_policy", "global boot_count", "secure always_on_vpn_app")

    /** A maker's own count of the chip's deep sleeps: the property, and whose it is. */
    private val DEEP = listOf("persist.tcl.totalstrtimes" to "TCL")

    val CMD = "cat /proc/uptime; echo \"now=\$(date +%s)\"; dumpsys alarm 2>/dev/null | grep -m2 'Runtime uptime'; $SEP; " +
        "p=\$(dumpsys power 2>/dev/null); echo \"\$p\" | grep -E 'mWakefulness=|mLast(Global)?(Wake|Sleep)(Time|Reason)|mIsEnabled=|Low Power Standby|mLowPowerStandby'; " +
        "echo \"\$p\" | sed -n '/^Wake Locks: size/,/^\$/p'; $SEP; " +
        "for k in ${SETTINGS.joinToString(" ") { "'$it'" }}; do echo \"\$k=\$(settings get \$k)\"; done; " +
        "for p in sys.boot.reason ${DEEP.joinToString(" ") { it.first }}; do echo \"\$p=\$(getprop \$p)\"; done; $SEP; " +
        "pm path ${Tailscale.PKG} 2>/dev/null | head -1; echo \"ts=\$(pidof ${Tailscale.PKG})\"; echo \"setup=\$(pidof dev.tvremote.setup)\"; " +
        "ip -o -4 addr show 2>/dev/null | grep -E '(tailscale|tun)[0-9]*[[:space:]]+inet 100\\.'; $SEP; " +
        "logcat -d -v epoch 2>/dev/null | grep -E 'has died|tguard-kill|lowmemorykiller|lmkd' | grep -E '$WATCHED' | tail -n 40; " +
        "logcat -d -b events -v epoch 2>/dev/null | grep -E 'am_kill' | grep -E '$WATCHED' | tail -n 40; $SEP; " +
        "tail -n 3000 $STATS/power.jsonl 2>/dev/null"

    /** [out]: what [CMD] printed; [log]: power.jsonl another way, asked only when [out] has none. Times are the TV's clock, in seconds
     *  ("now" too, so the page compares like with like); "ago" ones are seconds before the reading. */
    fun parse(out: String, log: () -> String = { "" }): JSONObject {
        val part = (out + "#~#".repeat(6)).split("#~#").toMutableList()
        if (part[5].isBlank()) part[5] = log()                         // the shell could not read TV Setup's folder: TV Setup tells it
        val o = JSONObject()
        boot(part[0], o)
        find("^now=(\\d+)", part[0])?.toLongOrNull()?.let { o.put("now", it) }
        state(part[1], o)
        val set = HashMap<String, String>()
        for (line in part[2].lines()) {
            val i = line.indexOf('=')
            if (i > 0) set[line.substring(0, i).trim().substringAfterLast(' ')] = line.substring(i + 1).trim()
        }
        val ms = { k: String -> set[k]?.toLongOrNull() }
        o.put("timeouts", JSONObject().putOpt("sleep", ms("sleep_timeout")).putOpt("attentive", ms("attentive_timeout"))
            .putOpt("screen_off", ms("screen_off_timeout")).putOpt("stay_on", ms("stay_on_while_plugged_in")))
        ms("hdmi_control_auto_device_off_enabled")?.let { o.put("hdmi_off", it != 0L) }
        ms("wifi_sleep_policy")?.let { o.put("wifi_sleep", it) }      // 2: never (Wi-Fi kept up with the screen off)
        set["sys.boot.reason"]?.takeIf { it.isNotEmpty() }?.let { o.getJSONObject("boot").put("reason", it) }
        ms("boot_count")?.let { o.getJSONObject("boot").put("count", it) }
        for ((prop, who) in DEEP) set[prop]?.toLongOrNull()?.let { o.put("deep", JSONObject().put("count", it).put("by", who)) }
        val ts = JSONObject().put("installed", part[3].contains("package:")).put("running", find("ts=(\\d+)", part[3]) != null)
            .put("always_on", set["always_on_vpn_app"] == Tailscale.PKG)
        find(" inet (100\\.[\\d.]+)", part[3])?.let { ts.put("ip", it) }
        o.put("tailscale", ts).put("setup_running", find("setup=(\\d+)", part[3]) != null)
        o.put("deaths", deaths(part[4]))
        val ev = JSONArray()
        for (line in part[5].lines()) try {
            val a = JSONArray(line.trim())
            if (a.length() >= 2) ev.put(JSONArray().put(a.getLong(0)).put(a.getString(1)))
        } catch (ignored: Exception) { }
        o.put("events", ev).put("logged", part[5].isNotBlank())
        return o
    }

    private fun boot(s: String, o: JSONObject) {
        val b = JSONObject()
        find("^\\s*([\\d.]+)", s)?.toDoubleOrNull()?.let { b.put("up", Math.round(it)) }
        val elapsed = find("Runtime uptime \\(elapsed\\): (\\S+)", s)?.let { duration(it) }
        val uptime = find("Runtime uptime \\(uptime\\): (\\S+)", s)?.let { duration(it) }
        if (elapsed != null && uptime != null && elapsed >= uptime) b.put("deep", (elapsed - uptime) / 1000)   // the clock that stops in suspend
        o.put("boot", b)
    }

    private fun state(s: String, o: JSONObject) {
        val suspends = o.getJSONObject("boot").optLong("deep") > 60      // mLastSleepTime counts uptime, which stops in deep sleep
        find("mWakefulness=(\\w+)", s)?.let { o.put("state", it) }
        // the realtime ones (Android 12+) are what the whole TV did; mLastSleepTime and friends are the default display's
        val ago = { k: String ->                                       // "=0 (…ms ago)": it never did, since boot
            Regex("(?m)^\\s*$k=(\\S+) \\((-?\\d+) ms ago\\)").find(s)?.takeIf { it.groupValues[1] != "0" }?.groupValues?.get(2)?.toLongOrNull()?.let { it / 1000 }
        }
        val why = { k: String -> find("(?m)^\\s*$k=(\\S+)", s) }
        val old = { k: String -> if (suspends) null else ago(k) }
        val sleep = JSONObject().putOpt("ago", ago("mLastGlobalSleepTimeRealtime") ?: old("mLastSleepTime")).putOpt("why", why("mLastGlobalSleepReason") ?: why("mLastSleepReason"))
        val wake = JSONObject().putOpt("ago", ago("mLastGlobalWakeTimeRealtime") ?: old("mLastWakeTime")).putOpt("why", why("mLastGlobalWakeReason") ?: why("mLastWakeReason"))
        if (sleep.length() > 0) o.put("last_sleep", sleep)
        if (wake.length() > 0) o.put("last_wake", wake)
        find("Low Power Standby[^\\n]*\\n(?:[^\\n]*\\n){0,3}?\\s*mIsEnabled=(true|false)", s)?.let { o.put("low_power_standby", it == "true") }
        val locks = JSONArray()
        val m = Regex("(?m)^\\s*(PARTIAL_WAKE_LOCK|FULL_WAKE_LOCK|SCREEN_\\w+_WAKE_LOCK|DOZE_WAKE_LOCK|DRAW_WAKE_LOCK)\\s+'([^']*)'.*?(?:ws=WorkSource\\{(\\d+)[^}]*\\}|uid=(\\d+))?").findAll(s)
        for (x in m) locks.put(JSONObject().put("kind", x.groupValues[1].removeSuffix("_WAKE_LOCK").lowercase()).put("tag", x.groupValues[2]))
        o.put("locks", locks)
    }

    /** "+1d2h3m4s5ms" (dumpsys's way) in milliseconds. */
    internal fun duration(s: String): Long? {
        var total = 0L
        var any = false
        for (m in Regex("(\\d+)(ms|d|h|m|s)").findAll(s)) {
            any = true
            val n = m.groupValues[1].toLong()
            total += n * when (m.groupValues[2]) { "d" -> 86_400_000L; "h" -> 3_600_000L; "m" -> 60_000L; "s" -> 1000L; else -> 1L }
        }
        return if (any) total else null
    }

    /** When Tailscale or TV Setup died, and why, newest last; one entry per death (Android's log says it two or three ways). */
    internal fun deaths(log: String): JSONArray {
        class D(val t: Double, val pkg: String, var why: String)
        val list = ArrayList<D>()
        for (line in log.lines()) {
            val t = find("^\\s*(\\d{9,}\\.\\d+)", line)?.toDoubleOrNull() ?: continue
            val pkg = find("($WATCHED)", line) ?: continue
            val why = when {
                line.contains("tguard-kill") -> "tguard:" + (find("tguard-kill-(\\w+)", line) ?: "")
                Regex("installPackage|force.?stop|stop .* due to|deletePackage", RegexOption.IGNORE_CASE).containsMatchIn(line) -> continue   // updated or stopped on purpose, not killed
                line.contains("am_kill") -> find("am_kill\\s*:\\s*\\[[^,]*,[^,]*,[^,]*,[^,]*,([^\\]]*)\\]", line)?.trim() ?: ""
                line.contains("lowmemorykiller") || line.contains("lmkd") -> "lmk"
                line.contains("has died") -> "died"
                else -> continue
            }
            val same = list.lastOrNull { it.pkg == pkg && Math.abs(it.t - t) < 3 }
            if (same == null) list.add(D(t, pkg, why))
            else if (same.why == "died" || (why.startsWith("tguard") && !same.why.startsWith("tguard"))) same.why = why   // the line that says why wins
        }
        list.sortBy { it.t }
        val a = JSONArray()
        for (d in list.takeLast(20)) a.put(JSONObject().put("t", Math.round(d.t)).put("pkg", d.pkg).put("why", d.why))
        return a
    }

    private fun find(re: String, s: String): String? = Regex(re, RegexOption.MULTILINE).find(s)?.groupValues?.get(1)
}
