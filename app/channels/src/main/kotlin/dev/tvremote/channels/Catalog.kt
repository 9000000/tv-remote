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

    /** A country's channels: iptv-org's playlist, with the providers' of that country merged in (Providers.kt). */
    fun channels(c: Context, cc: String, fresh: Boolean = false): List<Channel> =
        Providers.merge(c, cc, cached(c, "m3u-$cc.m3u", "$BASE/iptv/countries/$cc.m3u", fresh)?.let { M3u.parse(it) }.orEmpty(), fresh)

    /** Every country iptv-org knows, by name in the TV's language where Java has it ("Việt Nam"), with its flag. */
    fun countries(c: Context): List<Country> {
        val text = cached(c, "countries.json", "$BASE/api/countries.json", false) ?: return emptyList()
        val a = org.json.JSONArray(text)
        val lang = Lang.locale()
        return (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            val code = o.optString("code")
            val local = Locale("", code).getDisplayCountry(lang).takeIf { it.isNotEmpty() && it != code }
            Country(code.lowercase(), local ?: o.optString("name"), o.optString("flag"))
        }.sortedBy { it.name }
    }

    /** The country a list opens on the first time: the TV's own. */
    fun home(): String = Lang.system().country.lowercase().ifEmpty { "us" }

    /** The copy in files/ when younger than a day (or offline), else the network's ([load], or GET [url]). Null: never fetched and offline. */
    fun cached(c: Context, name: String, url: String, fresh: Boolean, load: (() -> String?)? = null): String? {
        val f = File(c.filesDir, name)
        if (!fresh && f.exists() && System.currentTimeMillis() - f.lastModified() < DAY) return f.readText()
        val got = try { load?.invoke() ?: if (load == null) get(url) else null } catch (e: Exception) { null }
        if (got != null) { File(c.filesDir, "$name.tmp").apply { writeText(got); renameTo(f) }; return got }
        return if (f.exists()) f.readText() else null
    }

    fun get(url: String): String? {
        val k = URL(url).openConnection() as HttpURLConnection
        k.connectTimeout = 8000; k.readTimeout = 15000
        k.setRequestProperty("User-Agent", "TVChannels/1 (Android)")
        return try {
            when (k.responseCode) {
                200 -> k.inputStream.bufferedReader().readText()
                404 -> ""                                             // a country with no list: nothing, not an error
                else -> null
            }
        } finally { k.disconnect() }
    }
}
