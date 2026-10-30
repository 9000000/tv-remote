package dev.tvremote.home.wall

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * A web wall's video ([WebVideo]) kept as a file after its first play, like [NetPicture] keeps a picture: streamed, the platform player
 * fetches it again on each return to Home, and a weak box waits on the network at each start (the FPT box: seconds). The first play
 * streams while this fills the file behind it; [VideoWall] takes the file from its next loop on. A cache: Android may clear it.
 */
object NetVideo {
    private const val MAX = 80L * 1024 * 1024                    // the site's videos are 2-15 MB; a bigger one stays streamed
    private const val KEEP = 3
    @Volatile private var busy = ""
    private const val FOREVER = Long.MAX_VALUE
    private const val BACKOFF = 5L * 60_000                      // a server error, 429 or no room today may be gone in minutes
    private val refused = HashMap<String, Long>()                // url -> until when it is not asked again (every loop turn would send the request again, for nothing): FOREVER = too big or a 4xx
    internal var clock: () -> Long = { System.currentTimeMillis() }

    private fun blocked(url: String) = synchronized(refused) { (refused[url] ?: return false) > clock() }

    private fun dir(c: Context) = File(c.cacheDir, "netvideo").also { it.mkdirs() }
    private fun file(c: Context, url: String) = File(dir(c), Integer.toHexString(url.hashCode()) + ".mp4")

    /** The kept file of [url], or null (none yet). No network. */
    fun kept(c: Context, url: String): File? = file(c, url).takeIf { it.isFile && it.length() > 0 }?.also { it.setLastModified(System.currentTimeMillis()) }

    /** Fills the file of [url] off the main thread, once at a time; [done] runs on that thread when it is there. */
    fun fetch(c: Context, url: String, done: (File) -> Unit) {
        if (kept(c, url) != null || busy == url || blocked(url)) return
        busy = url
        val app = c.applicationContext
        Thread({
            try { load(dir(app), url, app.cacheDir.usableSpace)?.let(done) } finally { busy = "" }
        }, "netvideo").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    /** The blocking part of [fetch]: the file of [url] in [dir], or null (refused, or the network failed). */
    internal fun load(dir: File, url: String, free: Long): File? {
        if (blocked(url)) return null
        val f = File(dir, Integer.toHexString(url.hashCode()) + ".mp4")
        if (!download(url, f, free)) return null
        trim(dir, f); return f
    }

    internal fun forgetRefused() = synchronized(refused) { refused.clear() }

    private fun download(url: String, f: File, free: Long): Boolean {
        val tmp = File(f.path + ".part")
        val k = URL(url).openConnection() as HttpURLConnection
        return try {
            k.connectTimeout = 8_000; k.readTimeout = 15_000
            val code = k.responseCode
            if (code != 200) return refuse(url, if (code in 400..499 && code != 408 && code != 429) FOREVER else BACKOFF)
            val len = (k.getHeaderField("content-length")?.toLongOrNull() ?: -1L)
            if (len > MAX || (len > 0 && free < len * 2 + 100L * 1024 * 1024)) return refuse(url, if (len > MAX) FOREVER else BACKOFF)   // room for it and to spare, as for the library
            var n = 0L
            tmp.outputStream().use { o -> k.inputStream.use { s ->
                val b = ByteArray(64 * 1024)
                while (true) { val r = s.read(b); if (r < 0) break; n += r; if (n > MAX) return refuse(url, FOREVER); o.write(b, 0, r) }
            } }
            n > 0 && (len <= 0 || n == len) && tmp.renameTo(f)
        } catch (e: Exception) { false } finally { k.disconnect(); if (tmp.exists()) tmp.delete() }
    }

    private fun refuse(url: String, ms: Long): Boolean { synchronized(refused) { refused[url] = if (ms == FOREVER) FOREVER else clock() + ms }; return false }   // a dropped network (an exception) is not here: that one may work next time

    private fun trim(dir: File, keep: File) {
        val all = dir.listFiles { x -> x.name.endsWith(".mp4") }?.sortedByDescending { it.lastModified() } ?: return
        all.drop(KEEP).filter { it != keep }.forEach { it.delete() }
    }
}
