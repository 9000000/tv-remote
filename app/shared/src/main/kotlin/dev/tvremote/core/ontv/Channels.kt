package dev.tvremote.core.ontv
import dev.tvremote.core.B64
import dev.tvremote.core.adb.Adb
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
    /** A programme reminder shows over other apps only with "display over other apps", a Settings toggle most TVs do not have: granted here, with every use (like TV Games' pad). */
    private const val ALLOW_OVERLAY = "appops set $PKG SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1"

    private val app = OwnApp(PKG, APK, "channels", "channels:none", ALLOW_OVERLAY)

    @Throws(IOException::class)
    fun state(sh: (String) -> String): JSONObject = app.state(sh)

    /** Installs it, or puts the newer one over it (favourites and groups stay): checked against the release's signed SHA256SUMS. */
    @Throws(IOException::class)
    fun install(adb: Adb, dir: File?, lang: String): JSONObject = app.install(adb, dir, lang)

    /**
     * Favourites and groups, through a broadcast only the shell can send (ChannelsReceiver in app/channels), like [Home.call].
     * [change]: the whole state by channel id ({favs: [id], groups: [{id, name, ch: [id]}]}), saved first; [cc]: that country's
     * channels too (fresh: read again from the network); [countries]: the countries' names. The answer is the receiver's
     * (favs, groups, country, list, countries) with `installed`; a TV Channels from before the receiver has `receiver: false`.
     */
    @Throws(IOException::class)
    fun manage(sh: (String) -> String, change: JSONObject?, cc: String? = null, fresh: Boolean = false, countries: Boolean = false,
               import: JSONObject? = null, export: Boolean = false): JSONObject {
        require(cc == null || cc.matches(Regex("[a-zA-Z]{2}"))) { "country" }
        val arg = (if (change == null) "" else " --es set " + B64.encode(change.toString().toByteArray(Charsets.UTF_8))) +
            (if (cc == null) "" else " --es list $cc" + if (fresh) " --ez fresh true" else "") + (if (countries) " --ez countries true" else "") +
            (if (export) " --ez export true" else "") + (if (import == null) "" else " --es import " + packImport(import))
        val out = sh("$ALLOW_OVERLAY; am broadcast --include-stopped-packages -n $PKG/.ChannelsReceiver$arg 2>&1")
        val m = Regex("data=\"([A-Za-z0-9+/=]+)\"").find(out)
            ?: return state(sh).put("receiver", false)                // not there, or from before the receiver
        val r = JSONObject(String(B64.decode(m.groupValues[1]), Charsets.UTF_8))
        if (r.has("error")) throw IOException(r.optString("error"))
        r.put("installed", true).put("receiver", true)
        r.optJSONObject("list")?.let { l ->                           // the channels came as a file (too big for the answer): read here, so the page gets one reply
            val path = l.optString("path")
            if (path.matches(Regex("/[A-Za-z0-9_./-]+\\.json"))) l.put("channels", org.json.JSONArray(sh("cat $path").trim().ifEmpty { "[]" })).remove("path")
        }
        app.latest(r.optString("version"))?.let { r.put("latest", it.version) }
        return r
    }

    /** An import request as the broadcast takes it: one argument may be ~128 KB, so a big file loses its stream addresses first, then is refused. */
    private fun packImport(req: JSONObject): String {
        var b = B64.encode(req.toString().toByteArray(Charsets.UTF_8))
        if (b.length > 100_000) {
            val doc = JSONObject(req.toString()).getJSONObject("doc")
            fun strip(a: org.json.JSONArray?) { if (a != null) for (i in 0 until a.length()) a.optJSONObject(i)?.remove("url") }
            strip(doc.optJSONArray("favourites"))
            doc.optJSONArray("groups")?.let { g -> for (i in 0 until g.length()) strip(g.optJSONObject(i)?.optJSONArray("channels")) }
            b = B64.encode(JSONObject().put("mode", req.optString("mode")).put("doc", doc).toString().toByteArray(Charsets.UTF_8))
        }
        if (b.length > 100_000) throw IllegalArgumentException("channels:file_big")
        return b
    }

    /** The link that opens [words] (a channel's name as said: "vtv1", "an giang 1") in TV Channels; empty words open its list. */
    fun link(words: String): String =
        if (words.isBlank()) "tvchannels://find/" else "tvchannels://find/" + URLEncoder.encode(words.trim().take(80), "UTF-8").replace("+", "%20")

    /** Opens [words] on the TV; false when TV Channels is not there (the link would open nothing, or a chooser). */
    @Throws(IOException::class)
    fun open(sh: (String) -> String, words: String): Boolean {
        val out = sh("$ALLOW_OVERLAY; am start -a android.intent.action.VIEW -d '${link(words)}' $PKG 2>&1")   // URL-encoded: no quote can get in
        return !out.contains("Error") && !out.contains("unable to resolve", true)
    }
}
