package dev.tvremote.core.ontv
import org.json.JSONObject
import dev.tvremote.core.B64

/**
 * The timers and limits kept by the TV Setup app on the TV (Rules.kt there): the sleep timer, bedtime and daily app limits.
 * They run on the TV itself, so nothing else has to stay on. A phone or computer that has ADB asks for them with a broadcast
 * only the shell can send, the way it gets a TV Setup pass (TrustReceiver); the TV's own page asks the app directly.
 * The computer app uses it too.
 */
object RulesClient {
    const val SETUP = "dev.tvremote.setup"

    /**
     * Starts TV Setup's service from the shell. On Doze's allow list a plain start is let through with no deadline; a foreground
     * start must become a foreground service within 10 s, and a TCL (Android 11) refuses that: the whole app was killed each
     * time a page read the rules. The allow list also keeps such a TV from killing the service later. Foreground only as a last try.
     */
    const val START = "cmd deviceidle whitelist +$SETUP >/dev/null 2>&1; " +
        "am startservice -n $SETUP/.RemoteService 2>&1 | grep -q Error && am start-foreground-service -n $SETUP/.RemoteService >/dev/null 2>&1"

    /**
     * [change] is one action ({"do": "sleep", "minutes": 30}, see Rules.change), or null to read. The answer is the state the
     * pages show, or {"setup": "missing" | "old" | "busy", "have", "min"} when the TV has no TV Setup, one
     * too old to know about rules, or one that did not answer though new enough (being updated or starting).
     */
    fun call(sh: (String) -> String, change: JSONObject?): JSONObject {
        val arg = if (change == null) "" else " --es set " + B64.encode(change.toString().toByteArray(Charsets.UTF_8))
        val out = sh("am broadcast --include-stopped-packages -n $SETUP/.RulesReceiver$arg 2>&1")
        val m = Regex("data=\"([A-Za-z0-9+/=]+)\"").find(out)
        if (m != null) {
            val state = JSONObject(String(B64.decode(m.groupValues[1]), Charsets.UTF_8))
            // Android may not let a receiver start the app's service from the background; the shell may, so it does it here
            if (state.optBoolean("wanted") && !state.optBoolean("running")) sh(START)
            return state
        }
        val have = SetupNeed.have(sh)
        // new enough but no answer: it is being installed or starting again (an update just now), not old: the page waits
        val kind = if (have.isEmpty()) "missing" else if (SetupNeed.ok(have, "rules")) "busy" else "old"
        return JSONObject().put("setup", kind).put("have", have).put("min", SetupNeed.MIN["rules"])
    }
}
