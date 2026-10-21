package dev.tvremote.channels

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Channel logos: read off the main thread, scaled down to the card while decoded, kept in memory (~8 MB). */
object Logos {
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) { override fun sizeOf(k: String, b: Bitmap) = b.byteCount }
    private val missing = HashSet<String>()

    fun get(url: String): Bitmap? = cache.get(url)

    /** [url] into [v] (tagged, so a recycled card does not get another channel's logo); [has] says whether it got one. */
    fun into(v: ImageView, url: String, maxW: Int, has: (Boolean) -> Unit) {
        v.tag = url
        cache.get(url)?.let { v.setImageBitmap(it); has(true); return }
        v.setImageDrawable(null)
        if (url.isEmpty() || synchronized(missing) { url in missing }) { has(false); return }
        pool.execute {
            val b = try { load(url, maxW) } catch (e: Throwable) { null }
            if (b != null) cache.put(url, b) else synchronized(missing) { missing.add(url) }
            main.post { if (v.tag == url) { if (b != null) v.setImageBitmap(b); has(b != null) } }
        }
    }

    fun load(url: String, maxW: Int): Bitmap? {
        val bytes = fetch(url, 6000) ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (o.outWidth / (s * 2) >= maxW) s *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
    }

    private fun fetch(url: String, timeout: Int): ByteArray? {
        val k = URL(url).openConnection() as HttpURLConnection
        k.connectTimeout = timeout; k.readTimeout = timeout; k.instanceFollowRedirects = true
        k.setRequestProperty("User-Agent", "TVChannels/1 (Android; https://tvremote.pages.dev)")   // Wikimedia (many logos) asks for a named agent
        return try { if (k.responseCode == 200) k.inputStream.readBytes() else null } finally { k.disconnect() }
    }
}

/**
 * Asks each stream once (at most every 6 hours, [Store.alive]) whether it answers, a few at a time, so the list can say which
 * channels do not play before one is chosen. A playlist must start with #EXTM3U; anything else that answers 200 counts too
 * (a plain TS or MP4 stream). It only reads the first bytes; nothing is played.
 */
object Probe {
    private val pool = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())
    private val asked = HashMap<String, Long>()                       // url -> when: asked again once its answer (6 hours) is old

    fun check(c: android.content.Context, ch: Channel, done: (Boolean) -> Unit) {
        if (Store.alive(c, ch.url) != null) return
        val t = System.currentTimeMillis()
        if (!synchronized(asked) { (asked[ch.url]?.let { t - it > 6 * 3600_000L } ?: true).also { if (it) asked[ch.url] = t } }) return
        pool.execute {
            val ok = try { answers(ch, Providers.resolve(c, ch.url)) } catch (e: Throwable) { false }
            Store.setAlive(c, ch.url, ok)
            main.post { done(ok) }
        }
    }

    fun answers(ch: Channel, playable: String = ch.url): Boolean {
        var url = playable
        repeat(5) {                                                   // by hand: https -> http redirects are common, and Java stops at them
            val k = URL(url).openConnection() as HttpURLConnection
            k.connectTimeout = 6000; k.readTimeout = 6000; k.instanceFollowRedirects = false
            k.setRequestProperty("User-Agent", ch.agent.ifEmpty { Catalog.BROWSER })
            if (ch.referrer.isNotEmpty()) k.setRequestProperty("Referer", ch.referrer)
            try {
                val code = k.responseCode
                if (code in 300..399) { url = URL(URL(url), k.getHeaderField("Location") ?: return false).toString(); return@repeat }
                if (code !in 200..299) return false
                val head = ByteArray(512)
                val n = k.inputStream.read(head)
                val text = if (n > 0) String(head, 0, n).trimStart('\uFEFF', ' ', '\r', '\n') else ""
                return n > 0 && (!url.contains(".m3u8") || text.startsWith("#EXT"))
            } finally { k.disconnect() }
        }
        return false
    }
}
