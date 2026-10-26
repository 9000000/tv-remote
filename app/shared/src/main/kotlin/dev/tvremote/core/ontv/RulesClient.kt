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
     * What Tailscale.keepAlive does, for TV Setup and TV Home (when installed): Doze, standby buckets and "background restricted" are
     * how a low-RAM TV lets go of them. Each command is on its own line of defence (`2>/dev/null`): Android 8 / 9 lack `cmd appops` /
     * `set-standby-bucket` and must not break what follows. Repeating it changes nothing.
     */
    const val KEEP_ALIVE = "for p in $SETUP dev.tvremote.home; do pm path \$p >/dev/null 2>&1 || continue; " +
        "dumpsys deviceidle whitelist +\$p >/dev/null 2>&1; am set-standby-bucket \$p active >/dev/null 2>&1; " +
        "cmd appops set \$p RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1; done; "

    /**
     * Starts TV Setup's service from the shell. On Doze's allow list a plain start is let through with no deadline; a foreground
     * start must become a foreground service within 10 s, and a TCL (Android 11) refuses that: the whole app was killed each
     * time a page read the rules. The allow list also keeps such a TV from killing the service later. Foreground only as a last try.
     */
    const val START = KEEP_ALIVE +
        "cmd deviceidle whitelist +$SETUP >/dev/null 2>&1; " +
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

    /**
     * Keys a real press would hand to TV Setup's key filter (colour keys TV Home gave an app, the dead YouTube / Netflix / Prime / All
     * apps buttons KeyFix brings back). `input keyevent` and scrcpy inject after that filter, so from a client with ADB these go
     * through [key] first; every other key is sent as it always was.
     */
    val SETUP_KEYS = setOf(183, 184, 185, 186, 190, 191, 195, 284)

    /** True when TV Setup took [code] (opened the app a real press would), false when the key should be sent plainly: nothing set for it, or TV Setup missing / old. */
    fun key(sh: (String) -> String, code: Int): Boolean = try {
        val arg = " --es set " + B64.encode(JSONObject().put("do", "key").put("code", code).toString().toByteArray(Charsets.UTF_8))
        acted(sh("am broadcast --include-stopped-packages -n $SETUP/.RulesReceiver$arg 2>&1"))
    } catch (e: Exception) { false }

    /**
     * Home on a TV whose home screen cannot be swapped (a Chromecast: LauncherSwap's "cast"), where TV Setup's filter may send it to the
     * launcher the person picked (HomeKey in TV Setup): a client's injected Home skips that filter, so there it asks [key] first.
     */
    const val HOME = 3
    fun castHost(sh: (String) -> String): Boolean = try { sh("getprop ro.product.device").trim().lowercase() in LauncherSwap.CAST_HOSTS } catch (e: Exception) { false }

    /** The app a remote's app button opens: when TV Setup did not take the key and the app is not on the TV, the button says so. */
    val APP_KEYS = mapOf(190 to "com.google.android.youtube.tv", 191 to "com.netflix.ninja", 195 to "com.amazon.amazonvideo.livingroom")

    fun appMissing(sh: (String) -> String, code: Int): Boolean {
        val pkg = APP_KEYS[code] ?: return false
        return try { !sh("pm path $pkg 2>/dev/null").contains("package:") } catch (e: Exception) { false }
    }

    /** The broadcast's answer: an old TV Setup says {"error": "unknown action"}, none says nothing at all. */
    fun acted(out: String): Boolean {
        val m = Regex("data=\"([A-Za-z0-9+/=]+)\"").find(out) ?: return false
        return try { JSONObject(String(B64.decode(m.groupValues[1]), Charsets.UTF_8)).optBoolean("acted") } catch (e: Exception) { false }
    }
}
