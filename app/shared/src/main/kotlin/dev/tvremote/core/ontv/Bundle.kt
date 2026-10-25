package dev.tvremote.core.ontv
import dev.tvremote.core.adb.Adb
import dev.tvremote.home.HomeConfig
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * The project's TV apps TV Setup puts on the TV by itself once it has ADB: the site and its ads show TV Home, Channels and Games,
 * while the install guide only installs TV Setup, and nobody went looking for the rest (the user, 2026-10-25). TV Setup then
 * swaps the home to TV Home once, behind the TV's own Keep / Go back question (MainActivity.homeOnce).
 */
object Bundle {
    class App(val name: String, val pkg: String, val install: (Adb, File?, String) -> JSONObject)

    private val home = OwnApp(HomeConfig.PKG, Launchers.SELF_APK, "home", "home:none", HomeConfig.GRANT_WIFI)

    val apps = listOf(
        App("TV Home", HomeConfig.PKG) { a, d, l -> home.install(a, d, l) },
        App("TV Channels", Channels.PKG) { a, d, l -> Channels.install(a, d, l) },
        App("TV Games", Games.PKG) { a, d, l -> Games.install(a, d, l) })

    /** The ones not on the TV, in one shell call. */
    @Throws(IOException::class)
    fun missing(sh: (String) -> String): List<App> {
        val have = sh("pm list packages dev.tvremote 2>/dev/null")
        return apps.filter { !have.contains("package:${it.pkg}\n") && !have.trimEnd().endsWith("package:${it.pkg}") }
    }
}
