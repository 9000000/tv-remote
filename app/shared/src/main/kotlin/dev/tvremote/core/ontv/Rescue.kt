package dev.tvremote.core.ontv

import org.json.JSONObject

/**
 * "TV frozen": one press stops the app on screen and goes Home, for a TV whose app hangs and whose Back and Home keys do
 * nothing useful. Only an app a person could have opened is stopped ([shouldStop]); a launcher, TV Home, TV Setup, the
 * system's own screens and the keyboard stay, since stopping those is what freezes a TV for good.
 */
object Rescue {
    /** Whether [pkg] (the app on screen) is stopped: not the home screen or keyboard in use, nor anything [mustKeep] names. */
    fun shouldStop(pkg: String, home: String, keyboard: String, mustKeep: (String) -> Boolean): Boolean =
        pkg.isNotEmpty() && pkg != home && pkg != keyboard && !mustKeep(pkg)

    /**
     * Stops the app on screen when it may be, then presses Home; and opens TV Home when that is the home screen (some
     * TVs ignore the key after a freeze). {"stopped": package or "", "kept": package left alone or ""}.
     */
    fun run(sh: (String) -> String, mustKeep: (String) -> Boolean = { Setup.mustKeep(it) }): JSONObject {
        val front = Foreground.parse(sh(Foreground.CMD))
        val home = LauncherSwap(sh).homeOpens().substringBefore('/')     // TV Home by TV Setup's key filter too (`input` passes the filter by)
        val keyboard = sh("settings get secure default_input_method").trim().substringBefore('/')
        val stop = shouldStop(front, home, keyboard, mustKeep)
        if (stop) sh("am force-stop " + front)                        // the name came from dumpsys: letters, digits, dots only
        sh("input keyevent 3")
        if (home == Setup.HOME_PKG) sh("am start -n ${Setup.HOME_PKG}/.HomeActivity >/dev/null 2>&1")
        return JSONObject().put("stopped", if (stop) front else "").put("kept", if (stop) "" else front)
    }
}
