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

    private fun dir(c: Context) = File(c.cacheDir, "netvideo").also { it.mkdirs() }
    private fun file(c: Context, url: String) = File(dir(c), Integer.toHexString(url.hashCode()) + ".mp4")

    /** The kept file of [url], or null (none yet). No network. */
    fun kept(c: Context, url: String): File? = file(c, url).takeIf { it.isFile && it.length() > 0 }?.also { it.setLastModified(System.currentTimeMillis()) }

    /** Fills the file of [url] off the main thread, once at a time; [done] runs on that thread when it is there. */
    fun fetch(c: Context, url: String, done: (File) -> Unit) {
        if (kept(c, url) != null || busy == url) return
        busy = url
        val app = c.applicationContext
        Thread({
            try {
                val f = file(app, url)
                if (download(url, f, app.cacheDir.usableSpace)) { trim(app, f); done(f) }
            } finally { busy = "" }
        }, "netvideo").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    private fun download(url: String, f: File, free: Long): Boolean {
        val tmp = File(f.path + ".part")
        val k = URL(url).openConnection() as HttpURLConnection
        return try {
            k.connectTimeout = 8_000; k.readTimeout = 15_000
            if (k.responseCode != 200) return false
            val len = (k.getHeaderField("content-length")?.toLongOrNull() ?: -1L)
            if (len > MAX || (len > 0 && free < len * 2 + 100L * 1024 * 1024)) return false   // room for it and to spare, as for the library
            var n = 0L
            tmp.outputStream().use { o -> k.inputStream.use { s ->
                val b = ByteArray(64 * 1024)
                while (true) { val r = s.read(b); if (r < 0) break; n += r; if (n > MAX) return false; o.write(b, 0, r) }
            } }
            n > 0 && (len <= 0 || n == len) && tmp.renameTo(f)
        } catch (e: Exception) { false } finally { k.disconnect(); if (tmp.exists()) tmp.delete() }
    }

    private fun trim(c: Context, keep: File) {
        val all = dir(c).listFiles { x -> x.name.endsWith(".mp4") }?.sortedByDescending { it.lastModified() } ?: return
        all.drop(KEEP).filter { it != keep }.forEach { it.delete() }
    }
}
