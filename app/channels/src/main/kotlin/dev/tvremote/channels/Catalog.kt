package dev.tvremote.channels

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * iptv-org's lists, read from https://iptv-org.github.io (no server of ours): a country's playlist (countries/<cc>.m3u, ~20 KB
 * for Vietnam; the full API files are 4-8 MB each, too much for a 1 GB TV box) and the names / flags of countries.
 * Each is kept in the app's files and read again once a day; offline, the last copy is used.
 */
object Catalog {
    const val BASE = "https://iptv-org.github.io"
    /** What the player and the stream check send when a list names no agent: some servers turn away anything not a browser (403). */
    const val BROWSER = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"
    private const val DAY = 24 * 3600_000L

    class Country(val code: String, val name: String, val flag: String)

    /** A country's channels: iptv-org's playlist, with the providers' of that country merged in (Providers.kt), and the other countries' channels its categories file asks for ("also"). */
    fun channels(c: Context, cc: String, fresh: Boolean = false): List<Channel> =
        Providers.merge(c, cc, iptv(c, cc, fresh), fresh).let { l -> l + Categories.alsoKept(c, cc, Categories.current(c, cc)) }   // + other countries' channels the categories file asks for (kept copy)

    /** Only iptv-org's playlist of [cc], the part a screen can show first (the providers' channels wait on more requests); [progress] as in [cached]. */
    fun iptv(c: Context, cc: String, fresh: Boolean = false, progress: ((Long, Long) -> Unit)? = null): List<Channel> =
        cached(c, "m3u-$cc.m3u", "$BASE/iptv/countries/$cc.m3u", fresh, progress, valid = { it.isEmpty() || it.trimStart('\uFEFF', ' ', '\r', '\n').startsWith("#EXTM3U") })
            ?.let { M3u.parse(it) }.orEmpty()

    /** Every country iptv-org knows, by name in the TV's language where Java has it ("Việt Nam"), with its flag. */
    fun countries(c: Context): List<Country> {
        val text = cached(c, "countries.json", "$BASE/api/countries.json", false, valid = { it.trimStart().startsWith("[") }) ?: return emptyList()
        val a = try { org.json.JSONArray(text) } catch (e: org.json.JSONException) { return emptyList() }
        val lang = Lang.locale()
        return (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            val code = o.optString("code")
            val local = Locale("", code).getDisplayCountry(lang).takeIf { it.isNotEmpty() && it != code }
            Country(code.lowercase(), local ?: o.optString("name"), o.optString("flag"))
        }.sortedBy { it.name }
    }

    /** The country a list opens on the first time: the TV's own. */
    fun home(): String = Lang.system().country.lowercase().ifEmpty { "us" }

    /** Whether files/[name] is a copy younger than a day: [cached] then answers without the network. */
    fun young(c: Context, name: String): Boolean = File(c.filesDir, name).let { it.exists() && System.currentTimeMillis() - it.lastModified() < DAY }

    /**
     * The copy in files/ when younger than a day (or offline), else the network's ([load], or GET [url]). Null: never fetched and offline.
     * [progress] (bytes so far, bytes in all or -1) is told of a GET of [url] as it comes in, for a bar. An answer [valid] refuses (a
     * hotel Wi-Fi's sign-in page, answered 200 for any address) counts as offline: kept a day, it would be the list until tomorrow.
     */
    fun cached(c: Context, name: String, url: String, fresh: Boolean, progress: ((Long, Long) -> Unit)? = null,
               valid: (String) -> Boolean = { true }, load: (() -> String?)? = null): String? {
        val f = File(c.filesDir, name)
        if (!fresh && young(c, name)) return f.readText()
        val got = try { load?.invoke() ?: if (load == null) get(url, progress) else null } catch (e: Exception) { null }?.takeIf(valid)
        if (got != null) {                                         // a full disk keeps no copy: this time's list still shows (census c9277726)
            val tmp = File(c.filesDir, "$name.tmp")
            try { tmp.writeText(got); tmp.renameTo(f) } catch (e: java.io.IOException) { tmp.delete() }
            return got
        }
        return if (f.exists()) f.readText() else null
    }

    fun get(url: String, progress: ((Long, Long) -> Unit)? = null): String? {
        val k = URL(url).openConnection() as HttpURLConnection
        k.connectTimeout = 8000; k.readTimeout = 15000
        k.setRequestProperty("User-Agent", "TVChannels/1 (Android)")
        if (progress != null) k.setRequestProperty("Accept-Encoding", "identity")   // gzip would make Content-Length smaller than what we count
        return try {
            when (k.responseCode) {
                200 -> if (progress == null) k.inputStream.bufferedReader().readText() else {
                    val total = (k.getHeaderField("content-length")?.toLongOrNull() ?: -1L)
                    val out = java.io.ByteArrayOutputStream(if (total in 1..8_000_000) total.toInt() else 64 * 1024)
                    val buf = ByteArray(16 * 1024)
                    k.inputStream.use { i -> while (true) { val n = i.read(buf); if (n < 0) break; out.write(buf, 0, n); progress(out.size().toLong(), total) } }
                    out.toString("UTF-8")
                }
                404 -> ""                                             // a country with no list: nothing, not an error
                else -> null
            }
        } finally { k.disconnect() }
    }
}
