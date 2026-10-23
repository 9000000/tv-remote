package dev.tvremote.channels

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The person's own m3u links (Store.playlists), each read like a country's list: kept a day in files/, the last copy used when
 * the link does not answer. Nothing here throws: a link that fails is a [Result] with `ok = false`, shown as one line.
 */
object Playlists {
    const val MAX_BYTES = 5 * 1024 * 1024                              // a 1 GB TV box parses it whole; more than this is not a channel list
    private const val MAX_REDIRECTS = 4

    class Result(val channels: List<Channel>, val ok: Boolean)

    private fun file(p: Store.Playlist) = "pl-${p.id}.m3u"
    fun forget(c: Context, p: Store.Playlist) { File(c.filesDir, file(p)).delete() }

    fun channels(c: Context, p: Store.Playlist, fresh: Boolean = false): Result {
        val text = try { Catalog.cached(c, file(p), p.url, fresh) { fetch(p.url) } } catch (e: Exception) { null }
        if (text == null) return Result(emptyList(), false)
        // a page that is not a playlist (a login page, a 200 "not found") parses to nothing: told apart from a real empty list below
        return Result(M3u.parse(text), looksLikeM3u(text))
    }

    fun looksLikeM3u(t: String) = t.take(2000).let { it.contains("#EXTM3U") || it.contains("#EXTINF") }

    /** The body, or null (not 200, too big, not an m3u, no network). Redirects are followed by hand: http -> https is not done by the JDK. */
    fun fetch(start: String): String? {
        var url = start
        repeat(MAX_REDIRECTS + 1) {
            if (!Edit.validUrl(url)) return null
            val k = URL(url).openConnection() as HttpURLConnection
            k.connectTimeout = 8000; k.readTimeout = 15000; k.instanceFollowRedirects = false
            k.setRequestProperty("User-Agent", Catalog.BROWSER)       // some hosts turn away anything else
            try {
                val code = k.responseCode
                if (code in 301..308 && code != 304) { url = URL(URL(url), k.getHeaderField("Location") ?: return null).toString(); return@repeat }
                if (code != 200) return null
                val len = k.contentLengthLong
                if (len > MAX_BYTES) return null
                val out = java.io.ByteArrayOutputStream()
                k.inputStream.use { i ->
                    val buf = ByteArray(16384)
                    while (true) {
                        val n = i.read(buf); if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > MAX_BYTES) return null
                    }
                }
                val s = out.toString("UTF-8")
                return if (looksLikeM3u(s)) s else null
            } finally { k.disconnect() }
        }
        return null
    }
}
