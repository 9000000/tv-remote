package dev.tvremote.core

import org.json.JSONObject
import java.io.IOException

/**
 * A short message on the TV's screen, over whatever is playing: a small card (icon, title, text) in a corner that takes no focus
 * and goes away by itself. TV Setup draws it (NotifyLayer there); a phone or computer with ADB asks with a broadcast only the
 * shell can send, the way [RulesClient] does. The TV Setup app reads the message with [clean] too, so the clamps (corner,
 * seconds, lengths) are the same on both sides:
 *   {"title": "Dinner", "text": "Come down", "corner": "top-right", "seconds": 5}
 */
object Notify {
    const val RECEIVER = RulesClient.SETUP + "/.NotifyReceiver"
    val CORNERS = listOf("top-left", "top-right", "bottom-left", "bottom-right")
    const val CORNER = "top-right"
    const val SECONDS = 5
    const val MIN_SECONDS = 1
    const val MAX_SECONDS = 60
    const val MAX_TITLE = 60
    const val MAX_TEXT = 300

    fun corner(s: String?): String = s?.trim()?.lowercase()?.takeIf { it in CORNERS } ?: CORNER

    /** Whole seconds in [MIN_SECONDS]..[MAX_SECONDS]; anything that is not a number is the default. */
    fun seconds(v: Any?): Int {
        val d = when (v) { is Number -> v.toDouble(); is String -> v.trim().toDoubleOrNull(); else -> null }
        if (d == null || d.isNaN()) return SECONDS
        return Math.round(d.coerceIn(MIN_SECONDS.toDouble(), MAX_SECONDS.toDouble())).toInt()
    }

    /** The message as TV Setup takes it, or IllegalArgumentException("empty") when there is neither title nor text. */
    fun clean(o: JSONObject): JSONObject {
        val title = o.optString("title").trim().take(MAX_TITLE)
        val text = o.optString("text").trim().take(MAX_TEXT)
        if (title.isEmpty() && text.isEmpty()) throw IllegalArgumentException("empty")
        return JSONObject().put("title", title).put("text", text).put("corner", corner(o.opt("corner") as? String)).put("seconds", seconds(o.opt("seconds")))
    }

    /** Shows [msg] on the TV. Throws [SetupNeed.Missing] when TV Setup is absent or too old to have the receiver. */
    @Throws(IOException::class)
    fun send(sh: (String) -> String, msg: JSONObject) {
        val arg = B64.encode(clean(msg).toString().toByteArray(Charsets.UTF_8))
        // the card is a window over other apps: the shell may allow that for TV Setup, where the TV has no screen for it
        val out = sh("appops set ${RulesClient.SETUP} SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1; am broadcast --include-stopped-packages -n $RECEIVER --es n $arg 2>&1")
        val m = Regex("data=\"([A-Za-z0-9+/=]+)\"").find(out) ?: throw SetupNeed.Missing("notify", SetupNeed.have(sh))
        val r = JSONObject(String(B64.decode(m.groupValues[1]), Charsets.UTF_8))
        if (r.has("error")) throw IOException(if (r.optString("error") == "overlay") "notify:nooverlay" else r.optString("error"))
    }
}
