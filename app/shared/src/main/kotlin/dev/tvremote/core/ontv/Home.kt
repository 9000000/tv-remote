package dev.tvremote.core.ontv
import dev.tvremote.core.B64
import dev.tvremote.core.adb.Adb
import dev.tvremote.home.HomeConfig
import dev.tvremote.update.Release
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * TV Home, this project's launcher (app/home), set up from the phone or the computer: its favourites, hidden apps, background
 * and clock, through a broadcast only the shell can send (HomeReceiver there), like [RulesClient]. The settings' shape and
 * limits are [HomeConfig], compiled into both sides.
 *   read:   {installed: false} or {installed, version, home (the TV's home is TV Home now), config, apps: [{pkg, label}], photo,
 *           latest (a newer TV Home in the latest release, when there is one)}
 */
object Home {
    /** The settings and TV Home's view of the TV's apps; [change] (any fields of [HomeConfig]) is saved first. */
    @Throws(IOException::class)
    fun call(sh: (String) -> String, change: JSONObject?): JSONObject {
        val arg = if (change == null) "" else " --es set " + B64.encode(HomeConfig.clean(change).let { c ->
            JSONObject().also { o -> for (k in change.keys()) if (c.has(k)) o.put(k, c.get(k)) } }.toString().toByteArray(Charsets.UTF_8))
        // the screensaver is a system setting: TV Home may write it once the shell has granted it that (only ADB can)
        val grant = if (change?.optBoolean("dream") == true) "pm grant ${HomeConfig.PKG} ${HomeConfig.WRITE_SECURE} 2>&1; " else ""
        val out = sh(grant + "am broadcast --include-stopped-packages -n ${HomeConfig.RECEIVER}$arg 2>&1; echo '#~#'; " + LauncherSwap.RESOLVE_HOME)
        val m = Regex("data=\"([A-Za-z0-9+/=]+)\"").find(out)
            ?: return JSONObject().put("installed", false)            // no receiver: TV Home is not on this TV
        val r = JSONObject(String(B64.decode(m.groupValues[1]), Charsets.UTF_8))
        if (r.has("error")) throw IOException(r.optString("error"))
        r.put("installed", true).put("home", out.substringAfter("#~#").contains(HomeConfig.PKG + "/"))
        latest(r.optString("version"))?.let { r.put("latest", it.version) }
        return r
    }

    @Volatile private var checked: Triple<Long, String, Release?>? = null
    /** A newer TV Home than [version], asked of GitHub at most every 6 hours; null when none, or GitHub cannot be asked. */
    private fun latest(version: String): Release? {
        checked?.let { (at, v, r) -> if (v == version && System.currentTimeMillis() - at < 6 * 3600_000L) return r }
        val r = try { Release.newer(version, "home", Launchers.SELF_APK) } catch (e: Exception) { return null }
        checked = Triple(System.currentTimeMillis(), version, r)
        return r
    }

    /** The newer TV Home put over the one on the TV (its settings stay): checked against the release's signed SHA256SUMS first. */
    @Throws(IOException::class)
    fun update(adb: Adb, dir: File?, lang: String): JSONObject {
        val sh: (String) -> String = { adb.shell(it) }
        val now = call(sh, null)
        if (!now.optBoolean("installed")) throw IOException("home:missing")
        checked = null
        val r = latest(now.optString("version")) ?: return now
        val file = File(dir ?: File(System.getProperty("java.io.tmpdir")), "tv-home-${r.version}.apk")
        try {
            r.download(file, null)
            val out = Setup(adb, dir, lang) { }.install(file)
            if (!out.contains("Success")) throw IOException("launcher:install:" + out.trim().lines().lastOrNull().orEmpty().take(200))
        } finally { file.delete() }
        try { adb.shell("cmd package compile -m speed -f ${HomeConfig.PKG} >/dev/null 2>&1", 120000) } catch (x: IOException) { }   // as LauncherInstall
        checked = null
        return call(sh, null)
    }

    /** A photo as the background: put where TV Home reads it, then chosen. [jpeg] is already the screen's size (the page makes it so). */
    @Throws(IOException::class)
    fun photo(adb: Adb, jpeg: ByteArray): JSONObject {
        val sh: (String) -> String = { adb.shell(it) }
        if (!call(sh, null).optBoolean("installed")) throw IOException("home:missing")   // also makes TV Home's folder, where the shell may write
        if (!adb.put(HomeConfig.PHOTO, jpeg)) throw IOException("home:photo")
        return call(sh, JSONObject().put("wall", "photo").put("photo", System.currentTimeMillis()))
    }
}
