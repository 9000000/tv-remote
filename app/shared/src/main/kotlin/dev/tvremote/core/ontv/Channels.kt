package dev.tvremote.core.ontv
import dev.tvremote.core.adb.Adb
import dev.tvremote.update.Release
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder

/**
 * TV Channels (app/channels), the iptv-org player, from the phone or the computer: whether it is on the TV, putting it there
 * (or a newer one) from the latest release, and a channel opened by its name. That app knows nothing of this one: everything
 * goes through its public link, tvchannels://find/<words>, where it picks the channel itself (or shows its search).
 *   read: {installed: false, latest} or {installed, version, latest (a newer one in the latest release, when there is one)}
 */
object Channels {
    const val PKG = "dev.tvremote.channels"
    const val APK = "tv-channels.apk"

    @Throws(IOException::class)
    fun state(sh: (String) -> String): JSONObject {
        val v = Regex("versionName=(\\S+)").find(sh("dumpsys package $PKG | grep -m1 versionName"))?.groupValues?.get(1)
        val o = JSONObject().put("installed", v != null)
        v?.let { o.put("version", it) }
        latest(v ?: "0")?.let { o.put("latest", it.version) }
        return o
    }

    @Volatile private var checked: Triple<Long, String, Release?>? = null
    /** The latest release's TV Channels when newer than [version] ("0": none on the TV), asked of GitHub at most every 6 hours. */
    private fun latest(version: String): Release? {
        checked?.let { (at, v, r) -> if (v == version && System.currentTimeMillis() - at < 6 * 3600_000L) return r }
        val r = try { Release.newer(version, "channels", APK) } catch (e: Exception) { return null }
        checked = Triple(System.currentTimeMillis(), version, r)
        return r
    }

    /** Installs it, or puts the newer one over it (favourites and groups stay): checked against the release's signed SHA256SUMS. */
    @Throws(IOException::class)
    fun install(adb: Adb, dir: File?, lang: String): JSONObject {
        val sh: (String) -> String = { adb.shell(it) }
        checked = null
        val now = state(sh)
        val r = (if (now.has("latest")) checked?.third else null) ?: return now.also { if (!it.optBoolean("installed")) throw IOException("channels:none") }
        val file = File(dir ?: File(System.getProperty("java.io.tmpdir")), "tv-channels-${r.version}.apk")
        try {
            r.download(file, null)
            val out = Setup(adb, dir, lang) { }.install(file)
            if (!out.contains("Success")) throw IOException("launcher:install:" + out.trim().lines().lastOrNull().orEmpty().take(200))
        } finally { file.delete() }
        try { adb.shell("cmd package compile -m speed -f $PKG >/dev/null 2>&1", 120000) } catch (x: IOException) { }   // a cold start on a slow chip, as LauncherInstall
        checked = null
        return state(sh)
    }

    /** The link that opens [words] (a channel's name as said: "vtv1", "an giang 1") in TV Channels; empty words open its list. */
    fun link(words: String): String =
        if (words.isBlank()) "tvchannels://find/" else "tvchannels://find/" + URLEncoder.encode(words.trim().take(80), "UTF-8").replace("+", "%20")

    /** Opens [words] on the TV; false when TV Channels is not there (the link would open nothing, or a chooser). */
    @Throws(IOException::class)
    fun open(sh: (String) -> String, words: String): Boolean {
        val out = sh("am start -a android.intent.action.VIEW -d '${link(words)}' $PKG 2>&1")   // URL-encoded: no quote can get in
        return !out.contains("Error") && !out.contains("unable to resolve", true)
    }
}
