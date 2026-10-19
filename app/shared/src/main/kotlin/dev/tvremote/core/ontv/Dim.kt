package dev.tvremote.core.ontv
import org.json.JSONObject
import java.io.IOException
import dev.tvremote.core.B64

/**
 * A tint over the whole screen, for watching in the dark: a colour at some strength over every app, drawn by TV Setup (DimLayer
 * there), asked with a broadcast only the shell can send, like [Notify]. Darker than the TV's lowest brightness on an OLED; on
 * an LCD the backlight stays on (blacks turn grey), but the warm colours do cut the blue. Kept by TV Setup until turned off,
 * through restarts of its own. The picture TV Remote shows has the tint too: it is on the screen.
 *   {"on": true, "color": "amber", "pct": 40}
 */
object Dim {
    const val RECEIVER = RulesClient.SETUP + "/.DimReceiver"
    /** The colours offered, in their order on the page; dark first (dimming only). */
    val COLORS: Map<String, Int> = linkedMapOf(
        "dark" to 0x000000,
        "amber" to 0xFF8A1E,                                           // a blue light filter: warm, the colour of a candle
        "yellow" to 0xFFC800,
        "sepia" to 0x704214,
        "red" to 0x9A0000,                                             // keeps eyes used to the dark
        "blue" to 0x10307A)
    const val COLOR = "dark"
    const val PCT = 40
    const val MIN_PCT = 5
    const val MAX_PCT = 90                                             // more and nothing is left to see

    fun pct(v: Any?): Int {
        val d = when (v) { is Number -> v.toDouble(); is String -> v.trim().toDoubleOrNull(); else -> null }
        if (d == null || d.isNaN()) return PCT
        return Math.round(d.coerceIn(MIN_PCT.toDouble(), MAX_PCT.toDouble())).toInt()
    }

    /** What TV Setup takes and keeps; anything unknown is the default. */
    fun clean(o: JSONObject): JSONObject {
        val color = o.optString("color").takeIf { it in COLORS } ?: COLOR
        return JSONObject().put("on", o.optBoolean("on")).put("color", color).put("pct", pct(o.opt("pct")))
    }

    /** The colour to draw, opaque: the strength is the window's alpha. */
    fun rgb(o: JSONObject): Int = (0xFF shl 24) or (COLORS[o.optString("color")] ?: 0)

    /** Sets the tint ([set], through [clean]) or, with null, only reads it; the tint now. Throws [SetupNeed.Missing] without the receiver. */
    @Throws(IOException::class)
    fun call(sh: (String) -> String, set: JSONObject?): JSONObject {
        val arg = if (set == null) "" else " --es d " + B64.encode(clean(set).toString().toByteArray(Charsets.UTF_8))
        // a window over other apps: the shell may allow that for TV Setup, where the TV has no screen for it
        val out = sh((if (set?.optBoolean("on") == true) "appops set ${RulesClient.SETUP} SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1; " else "") +
            "am broadcast --include-stopped-packages -n $RECEIVER$arg 2>&1")
        val m = Regex("data=\"([A-Za-z0-9+/=]+)\"").find(out) ?: throw SetupNeed.Missing("dim", SetupNeed.have(sh))
        val r = JSONObject(String(B64.decode(m.groupValues[1]), Charsets.UTF_8))
        if (r.has("error")) throw IOException(if (r.optString("error") == "overlay") "notify:nooverlay" else r.optString("error"))
        return r
    }
}
