package dev.tvremote.core.ontv
import org.json.JSONObject
import java.io.IOException
import dev.tvremote.core.B64

/**
 * The welcome screen: a video or a picture TV Setup shows over the whole TV when it is turned on (a homestay's greeting).
 * TV Setup does the showing (WelcomeActivity there); a phone or computer with ADB sets it up with a broadcast only the shell
 * can send, the way [RulesClient] does. TV Setup reads the settings with [apply] and [due] too, so what the pages offer and
 * what the TV does agree. The settings, as the TV keeps and the pages show them:
 *   {"on": true, "when": "every" | "boot", "seconds": 10 (a picture), "kind": "video" | "image", "name": "hello.mp4", "size": n, "has": true}
 */
object Welcome {
    const val RECEIVER = RulesClient.SETUP + "/.WelcomeReceiver"
    /** Where the file goes: TV Setup's own folder on shared storage, which it reads with no permission and the shell may write. */
    const val DIR = "/sdcard/Android/data/" + RulesClient.SETUP + "/files/welcome"
    const val MAX_BYTES = 200L shl 20
    const val SECONDS = 10
    const val MIN_SECONDS = 3
    const val MAX_SECONDS = 120
    /** A welcome shown this recently (HDMI-CEC waking the TV twice, a screen that flickers) is not shown again. */
    const val GUARD_MS = 120_000L
    const val EVERY = "every"
    const val BOOT = "boot"
    val VIDEO = setOf("mp4", "m4v", "webm", "mkv")
    val IMAGE = setOf("jpg", "jpeg", "png", "webp")

    fun ext(name: String?): String = (name ?: "").substringAfterLast('.', "").lowercase()

    /** "video", "image", or null for a file the TV cannot show. */
    fun kind(name: String?): String? = ext(name).let { if (it in VIDEO) "video" else if (it in IMAGE) "image" else null }

    /** Whole seconds in [MIN_SECONDS]..[MAX_SECONDS]; anything that is not a number is the default. */
    fun seconds(v: Any?): Int {
        val d = when (v) { is Number -> v.toDouble(); is String -> v.trim().toDoubleOrNull(); else -> null }
        if (d == null || d.isNaN()) return SECONDS
        return Math.round(d.coerceIn(MIN_SECONDS.toDouble(), MAX_SECONDS.toDouble())).toInt()
    }

    fun whenOf(v: Any?): String = if ((v as? String)?.trim()?.lowercase() == BOOT) BOOT else EVERY

    /**
     * Whether a welcome is shown now. [event]: "boot" (the TV started) or "on" (it was turned on from standby). [lastAgo]: ms since
     * the last welcome in this same run of the TV, null when none (a restart forgets it: the clock of a TV that has just started cannot be trusted).
     */
    fun due(event: String, o: JSONObject, lastAgo: Long?): Boolean {
        if (!o.optBoolean("on") || !o.optBoolean("has")) return false
        if (event != "boot" && (event != "on" || whenOf(o.opt("when")) != EVERY)) return false
        return lastAgo == null || lastAgo !in 0 until GUARD_MS
    }

    /**
     * One change from a page ("do"): config {on, when, seconds} (only the fields given), remove (the file goes; the choices stay).
     * An empty "do" only reads. False when nothing here changed. The file itself is TV Setup's business (adopt, show, thumb).
     */
    fun apply(o: JSONObject, d: JSONObject): Boolean {
        when (d.optString("do")) {
            "", "get" -> return false
            "config" -> {
                if (d.has("on")) o.put("on", d.optBoolean("on"))
                if (d.has("when")) o.put("when", whenOf(d.opt("when")))
                if (d.has("seconds")) o.put("seconds", seconds(d.opt("seconds")))
            }
            "remove" -> for (k in listOf("kind", "name", "size", "at", "duration")) o.remove(k)
            else -> return false
        }
        return true
    }

    /**
     * Asks TV Setup on the TV: [change] is one change ({"do": "config", "on": true}), or null to read. The answer is the settings
     * above, or {"setup": "missing" | "old" | "busy"} when the TV has no TV Setup or one too old (see [RulesClient.call]).
     * A change may also be "adopt" (the file now in [DIR] as incoming.<ext> becomes the welcome), "show" (try it now) or
     * "thumb" (a small picture of it, "thumb": base64 JPEG). Anything that changes something first lets TV Setup draw over
     * other apps (the welcome comes up over whatever the TV is on, from the background).
     */
    @Throws(IOException::class)
    fun call(sh: (String) -> String, change: JSONObject?): JSONObject {
        val arg = if (change == null) "" else " --es set " + B64.encode(change.toString().toByteArray(Charsets.UTF_8))
        val allow = if (change != null && change.optString("do") != "thumb") "appops set ${RulesClient.SETUP} SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1; " else ""
        val out = sh(allow + "am broadcast --include-stopped-packages -n $RECEIVER$arg 2>&1")
        val m = Regex("data=\"([A-Za-z0-9+/=]+)\"").find(out)
        if (m != null) {
            val state = JSONObject(String(B64.decode(m.groupValues[1]), Charsets.UTF_8))
            if (state.has("error")) throw IllegalArgumentException(state.optString("error"))
            if (state.optBoolean("wanted") && !state.optBoolean("running")) sh(RulesClient.START)   // the service holds the screen-on listener
            return state
        }
        val have = SetupNeed.have(sh)
        val kind = if (have.isEmpty()) "missing" else if (SetupNeed.ok(have, "welcome")) "busy" else "old"
        if (change != null && kind != "busy") throw SetupNeed.Missing("welcome", have)
        return JSONObject().put("setup", kind).put("have", have).put("min", SetupNeed.MIN["welcome"])
    }
}
