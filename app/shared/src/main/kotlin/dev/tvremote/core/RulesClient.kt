package dev.tvremote.core

import org.json.JSONObject

/**
 * The timers and limits kept by the TV Setup app on the TV (Rules.kt there): the sleep timer, bedtime and daily app limits.
 * They run on the TV itself, so nothing else has to stay on. A phone or computer that has ADB asks for them with a broadcast
 * only the shell can send, the way it gets a TV Setup pass (TrustReceiver); the TV's own page asks the app directly.
 * The computer app uses it too.
 */
object RulesClient {
    const val SETUP = "dev.tvremote.setup"

    /**
     * [change] is one action ({"do": "sleep", "minutes": 30}, see Rules.change), or null to read. The answer is the state the
     * pages show, or {"setup": "missing" | "old"} when the TV has no TV Setup, or one too old to know about rules.
     */
    fun call(sh: (String) -> String, change: JSONObject?): JSONObject {
        val arg = if (change == null) "" else " --es set " + B64.encode(change.toString().toByteArray(Charsets.UTF_8))
        val out = sh("am broadcast --include-stopped-packages -n $SETUP/.RulesReceiver$arg 2>&1")
        val m = Regex("data=\"([A-Za-z0-9+/=]+)\"").find(out)
        if (m != null) {
            val state = JSONObject(String(B64.decode(m.groupValues[1]), Charsets.UTF_8))
            // Android may not let a receiver start the app's service from the background; the shell may, so it does it here
            if (state.optBoolean("wanted") && !state.optBoolean("running")) sh("am start-foreground-service -n $SETUP/.RemoteService 2>&1")
            return state
        }
        return JSONObject().put("setup", if (sh("pm path $SETUP 2>/dev/null").contains("package:")) "old" else "missing")
    }
}
