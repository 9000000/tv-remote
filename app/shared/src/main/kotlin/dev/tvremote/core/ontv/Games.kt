package dev.tvremote.core.ontv
import dev.tvremote.core.adb.Adb
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * TV Games (app/games), retro and web games played with phones as pads, from the phone or the computer: whether it is on the TV,
 * putting it there (or a newer one) from the latest release, and opening it. Playing needs no ADB and no TV Setup: the app serves
 * its own pad page (a QR on the TV), so ADB is only for installing it from here. See [OwnApp] for the state's shape (`soon`:
 * the latest release has no tv-games.apk yet).
 */
object Games {
    const val PKG = "dev.tvremote.games"
    const val APK = "tv-games.apk"
    /** The pad's "display over other apps", a Settings toggle most TVs do not have: granted here (as for TV Channels). */
    private const val ALLOW_OVERLAY = "appops set $PKG SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1"
    private val app = OwnApp(PKG, APK, "games", "games:none", ALLOW_OVERLAY)

    @Throws(IOException::class)
    fun state(sh: (String) -> String): JSONObject = app.state(sh)

    @Throws(IOException::class)
    fun install(adb: Adb, dir: File?, lang: String): JSONObject = app.install(adb, dir, lang)

    /** Opens its library on the TV; false when TV Games is not there. */
    @Throws(IOException::class)
    fun open(sh: (String) -> String): Boolean {
        val out = sh("$ALLOW_OVERLAY; am start -n $PKG/.LibraryActivity 2>&1")
        return !out.contains("Error") && !out.contains("does not exist", true) && !out.contains("unable to resolve", true)
    }
}
