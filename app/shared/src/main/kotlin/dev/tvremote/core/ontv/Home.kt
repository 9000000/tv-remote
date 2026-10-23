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
    fun call(sh: (String) -> String, change: JSONObject?, extra: String = ""): JSONObject {   // [extra]: more receiver arguments (drop, thumbs)
        val arg = " $extra" + if (change == null) "" else " --es set " + B64.encode(HomeConfig.forward(change).let { c ->
            JSONObject().also { o -> for (k in change.keys()) if (c.has(k)) o.put(k, c.get(k)) } }.toString().toByteArray(Charsets.UTF_8))
        // the screensaver is a system setting: TV Home may write it once the shell has granted it that (only ADB can)
        val grant = if (change?.optBoolean("dream") == true) "pm grant ${HomeConfig.PKG} ${HomeConfig.WRITE_SECURE} 2>&1; " else ""
        val out = sh(grant + (if (change != null) HomeConfig.GRANT_WIFI else "") + "am broadcast --include-stopped-packages -n ${HomeConfig.RECEIVER}$arg 2>&1; echo '#~#'; " + LauncherSwap.RESOLVE_HOME)
        val m = Regex("data=\"([A-Za-z0-9+/=]+)\"").find(out)
            ?: return JSONObject().put("installed", false)            // no receiver: TV Home is not on this TV
        val r = JSONObject(String(B64.decode(m.groupValues[1]), Charsets.UTF_8))
        if (r.has("error")) throw IOException(r.optString("error"))
        r.put("installed", true).put("home", out.substringAfter("#~#").contains(HomeConfig.PKG + "/"))
        latest(r.optString("version"))?.let { r.put("latest", it.version) }
        if (unasked) r.put("offline", true)                           // as OwnApp.state
        return r
    }

    @Volatile private var unasked = false                              // the last [latest] could not reach GitHub
    @Volatile private var checked: Triple<Long, String, Release?>? = null
    /** A newer TV Home than [version], asked of GitHub at most every 6 hours; null when none, or GitHub cannot be asked. */
    private fun latest(version: String): Release? {
        unasked = false
        checked?.let { (at, v, r) -> if (v == version && System.currentTimeMillis() - at < 6 * 3600_000L) return r }
        val r = try { Release.newer(version, "home", Launchers.SELF_APK) } catch (e: Exception) { unasked = true; return null }
        checked = Triple(System.currentTimeMillis(), version, r)
        return r
    }

    /** The newer TV Home put over the one on the TV (its settings stay): checked against the release's signed SHA256SUMS first. */
    @Throws(IOException::class)
    fun update(adb: Adb, dir: File?, lang: String, go: () -> Boolean = { true }): JSONObject {     // go: as OwnApp.install
        val sh: (String) -> String = { adb.shell(it) }
        val now = call(sh, null)
        if (!now.optBoolean("installed")) throw IOException("home:missing")
        checked = null
        val r = latest(now.optString("version")) ?: return now
        val file = File(dir ?: File(System.getProperty("java.io.tmpdir")), "tv-home-${r.version}.apk")
        try {
            r.download(file, null)
            if (!go()) return now
            val shown = sh("dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'").contains(HomeConfig.PKG + "/")
            val out = Setup(adb, dir, lang) { }.install(file)
            if (!out.contains("Success")) throw IOException("launcher:install:" + out.trim().lines().lastOrNull().orEmpty().take(200))
            // the install kills the home on screen and Android does not start it again: the last app used came up in its place
            // (an FPT box showed TV Channels, nothing pressed). Also in standby (AutoUpdate's time), where it waited for the wake-up
            if (shown) sh("am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1")
        } finally { file.delete() }
        try { adb.shell(HomeConfig.GRANT_WIFI + "cmd package compile -m speed -f ${HomeConfig.PKG} >/dev/null 2>&1", 120000) } catch (x: IOException) { }   // as LauncherInstall
        checked = null
        return call(sh, null)
    }

    /** A photo as a background: put where TV Home reads it, then chosen (and added to the rotation's set when that is on).
     *  [jpeg] is already the screen's size (the page makes it so). A TV Home without rotation keeps its one photo, wall.jpg. */
    @Throws(IOException::class)
    fun photo(adb: Adb, jpeg: ByteArray): JSONObject {
        val sh: (String) -> String = { adb.shell(it) }
        val now = call(sh, null)
        if (!now.optBoolean("installed")) throw IOException("home:missing")   // also makes TV Home's folder, where the shell may write
        val conf = now.optJSONObject("config") ?: JSONObject()
        if (!conf.optBoolean("wrotate")) {
            if (!adb.put(HomeConfig.PHOTO, jpeg, true)) throw IOException("home:photo")
            return call(sh, JSONObject().put("wall", "photo").put("photo", System.currentTimeMillis()))
        }
        val used = now.optJSONArray("photos")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("id") } } ?: emptyList()
        val id = HomeConfig.PHOTOS.firstOrNull { it !in used } ?: throw IOException("home:photos_full")
        if (!adb.put(HomeConfig.photoPath(id), jpeg, true)) throw IOException("home:photo")
        val set = conf.optJSONArray("walls")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
        val walls = if (conf.optString("wevery", "off") == "off") listOf(id) else set + id
        return call(sh, JSONObject().put("walls", org.json.JSONArray(walls)).put("wall", id).put("photo", System.currentTimeMillis()))
    }

    /** TV Home's QR screen for a photo from a phone, over its home screen: the page on the TV itself has no photos to pick. */
    @Throws(IOException::class)
    fun photoQr(sh: (String) -> String) {
        if (!sh("am start -a ${HomeConfig.PHOTO_QR} -n ${HomeConfig.PKG}/.HomeActivity 2>&1").contains("Starting")) throw IOException("home:photo")
    }

    /** A photo taken out: TV Home deletes the file and its place in the set (it owns them). */
    @Throws(IOException::class)
    fun drop(adb: Adb, id: String): JSONObject {
        if (!HomeConfig.isPhoto(id)) throw IllegalArgumentException("photo id")
        return call({ adb.shell(it) }, null, "--es drop $id")
    }
}
