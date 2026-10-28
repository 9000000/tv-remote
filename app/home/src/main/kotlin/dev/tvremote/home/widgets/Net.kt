package dev.tvremote.home.widgets

import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** The widgets' small HTTP reads (market, news, pictures): off the main thread by the callers, a timeout and a size cap on each, null on any failure. */
internal object Net {
    const val FEED_CAP = 2 * 1024 * 1024                              // a feed is read up to this many bytes (the parser stops sooner, at its last item)
    const val PIC_CAP = 1536 * 1024

    /** Reads at most [cap] bytes of [s]; the rest is dropped (a longer body is cut, not an error: the parsers stop at the first complete item). */
    class Capped(s: InputStream, private var left: Int) : FilterInputStream(s) {
        override fun read(): Int { if (left <= 0) return -1; return super.read().also { if (it >= 0) left-- } }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            return super.read(b, off, minOf(len, left)).also { if (it > 0) left -= it }
        }
    }

    /** Opens [url] (http or https only, redirects followed by the system) and gives [use] the capped body; null when the answer is not 200 or anything fails. */
    fun <T> open(url: String, cap: Int, ms: Int = 8000, use: (InputStream) -> T?): T? = try {
        if (!url.startsWith("https://") && !url.startsWith("http://")) null
        else {
            val u = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = ms; readTimeout = ms; instanceFollowRedirects = true
                setRequestProperty("User-Agent", "TV-Home"); setRequestProperty("Accept", "*/*")
            }
            try { if (u.responseCode == 200) u.inputStream.use { use(Capped(it, cap)) } else null } finally { u.disconnect() }
        }
    } catch (e: Exception) { null }

    fun bytes(url: String, cap: Int, ms: Int = 8000): ByteArray? = open(url, cap, ms) { s -> ByteArrayOutputStream().also { s.copyTo(it) }.toByteArray() }
    fun text(url: String, cap: Int = 400_000, ms: Int = 8000): String? = bytes(url, cap, ms)?.toString(Charsets.UTF_8)
}
