package dev.tvremote.setup

import android.content.Context
import dev.tvremote.core.Scrub
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What support reads without a field named for it beforehand (the user, 2026-10-26: a Chromecast's keys and auto-open had none,
 * and every new feature would need one more). [prefs]: every setting this app keeps, secrets out; [logcat]: this app's own log
 * (KeyFix, AutoOpen ... say what they did there); [shell]: while support looks, any command that only reads, over this app's ADB.
 */
object SupportDump {
    // a name that holds a secret or a person's words, at any depth (the PIN's hash and salt, the TOTP seed, tokens, chats)
    private val SECRET = Regex("(?i)^(?:.*[_-])?(token|tokens|pass|password|pin|code|secret|totp|hash|h|salt|key|cookie|chats|history|paired|recovery|backup|note)$")

    fun prefs(c: Context): String = buildString {
        val dir = File(c.applicationInfo.dataDir, "shared_prefs")
        for (f in (dir.listFiles() ?: emptyArray()).sortedBy { it.name }) {
            val name = f.name.removeSuffix(".xml")
            if (name.startsWith("WebView") || name.startsWith("Aw")) continue   // the WebView's own, not this app's
            val all = try { c.getSharedPreferences(name, Context.MODE_PRIVATE).all } catch (e: Exception) { continue }
            if (all.isEmpty()) continue
            append("[prefs ").append(name).append("]\n")
            for ((k, v) in all.toSortedMap()) append(k).append('=').append(if (SECRET.matches(k)) "…" else value(v)).append('\n')
        }
    }.let { Scrub.blank(it) }

    private fun value(v: Any?): String = when (v) {
        is String -> (try { if (v.startsWith("{")) clean(JSONObject(v)).toString() else if (v.startsWith("[")) clean(JSONArray(v)).toString() else null } catch (e: Exception) { null }) ?: v.take(2000)
        is Set<*> -> v.joinToString(",", "{", "}")
        else -> v.toString()
    }

    private fun clean(o: Any?): Any? = when (o) {
        is JSONObject -> JSONObject().also { out -> o.keys().forEach { k -> out.put(k, if (SECRET.matches(k)) "…" else clean(o.opt(k))) } }
        is JSONArray -> JSONArray().also { out -> for (i in 0 until o.length()) out.put(clean(o.opt(i))) }
        else -> o
    }

    /** This app's own log: no permission needed for its own lines. */
    fun logcat(): String = try {
        val p = ProcessBuilder("logcat", "-d", "-t", "3000", "-v", "time", "--pid=" + android.os.Process.myPid()).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        "[logcat]\n" + Scrub.blank(out)
    } catch (e: Exception) { "[logcat] $e\n" }

    // one command, no shell syntax (no ; | & > < ` $ quotes or line breaks): the only way to chain or write is ruled out first
    private val SYNTAX = Regex("[;|&><`$\\\\'\"\\n\\r(){}]")
    // and what may run: commands that print, never set. dumpsys / cmd forms that act are named below.
    private val READ = listOf(
        Regex("getprop( [\\w.-]+)?"),
        Regex("settings (get (global|secure|system) [\\w.-]+|list (global|secure|system))"),
        Regex("dumpsys( [\\w./:-]+)*"),
        Regex("pm (list (packages|permissions|features|users|libraries)( -[a-zA-Z]+)*( [\\w.-]+)?|path [\\w.]+|dump [\\w.]+)"),
        Regex("appops get [\\w.]+( [\\w_:]+)?"),
        Regex("cmd package (resolve-activity|query-activities|query-services|query-receivers|list packages)( [\\w.:/=-]+)*"),
        Regex("logcat -d( -[tsv] [\\w:*.]+| -b \\w+| [\\w]+:[VDIWEFS])*"),
        Regex("(uptime|df( -h)?|ps -A( -o [\\w,]+)?|top -b -n 1( -m \\d+)?|wm (size|density)|id|date)"),
        // a remote's keys (a Chromecast with a TIMvision remote, 2026-10-26): the system's key maps, which keys each remote declares
        // (-p prints and ends), and the next presses while the person tries a button (timeout ends it if they press nothing)
        Regex("(cat|ls( -l)?) /(system|vendor|product|odm)/usr/(keylayout|keychars|idc)(/[\\w-][\\w.-]*)?"),
        Regex("getevent -(?=[lip]*p)[lip]+"),
        Regex("timeout ([1-9]|1\\d|20) getevent -lt? -c \\d{1,3}")
    )
    private val ACTS = Regex("\\b(set|reset|unplug|enable|disable|force-idle|force-inactive|step|clear|kill|run|put|delete|write|unforce|--reset|start|stop|add|remove)\\b")

    // services whose dump carries a person's own words or accounts: never asked from afar
    private val PRIVATE = Regex("^dumpsys (clipboard|notification|account|telecom|content|sms|contacts)\\b")

    fun allowed(cmd: String): Boolean = cmd.length <= 200 && !SYNTAX.containsMatchIn(cmd) && READ.any { it.matches(cmd) } &&
        !(cmd.startsWith("dumpsys") && (ACTS.containsMatchIn(cmd) || PRIVATE.containsMatchIn(cmd)))

    /** [cmd] when [allowed], over this app's own ADB link; (status, output). */
    fun shell(cmd: String): Pair<Int, String> {
        val c = cmd.trim().replace(Regex("\\s+"), " ")
        if (!allowed(c)) return 403 to "not a read-only command: $c"
        val adb = RemoteService.adb() ?: return 503 to "no ADB on this TV (state ${RemoteService.adbState()})"
        // Scrub.text: emails and addresses out too, a dump may name the person's account
        // cut to what the worker takes (a whole logcat is megabytes: refused, the owner got "answer not delivered"); a log keeps its end
        return try { 200 to Support.fit(Scrub.text(adb.shell("$c 2>&1", 25000)), Support.MAX_ANSWER, tail = c.startsWith("logcat")) } catch (e: Exception) { 0 to (e.message ?: e.javaClass.simpleName) }
    }
}
