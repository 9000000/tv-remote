package dev.tvremote.home.wall

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * The site's own picture themes (`loop.html?i=<jpg>`) shown as a native picture wall instead of a WebView: on a weak box the
 * WebView's cold start plus an uncached 300 KB download made the theme take far too long (measured by the user on an FPT box).
 * The jpg is fetched once into a small file cache (keyed by address, [KEEP] newest files, [MAX] bytes each), so a second pick,
 * a restart or no network shows it at once. Same hosts as loop.html's HOSTS for images.
 */
object NetPicture {
    private const val KEEP = 8
    private const val MAX = 4 * 1024 * 1024
    private val HOSTS = setOf("tvremote.pages.dev", "upload.wikimedia.org", "images.pexels.com", "images.unsplash.com")   // docs/home/loop.html's HOSTS

    /** The picture's address when [url] is the site's loop page with `?i=` (no network), else null. */
    fun direct(url: String): String? {
        val u = try { URI(url) } catch (e: Exception) { return null }
        if (u.scheme != "https" || u.host?.endsWith("tvremote.pages.dev") != true || u.path.orEmpty() != "/home/loop.html") return null
        val i = u.rawQuery?.split('&')?.firstOrNull { it.startsWith("i=") }?.substringAfter('=')?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: return null
        val iu = try { URI(i) } catch (e: Exception) { return null }
        return if (iu.scheme == "https" && iu.host in HOSTS) i else null
    }

    private fun dir(c: Context) = File(c.cacheDir, "netpic").also { it.mkdirs() }
    fun file(c: Context, pic: String) = File(dir(c), Integer.toHexString(pic.hashCode()) + ".jpg")

    /** The cached file of [pic] (downloaded, 3 tries, if not there); null when it cannot be had. Off the main thread. */
    fun get(c: Context, pic: String): File? {
        val f = file(c, pic)
        if (f.isFile && f.length() > 0) { f.setLastModified(System.currentTimeMillis()); return f }
        repeat(3) { n ->
            if (download(pic, f)) { trim(c, f); return f }
            try { Thread.sleep(1_500L * (n + 1)) } catch (e: InterruptedException) { return null }
        }
        return null
    }

    /** Background fill of the cache for [pics] (the Themes page): the ones already there are skipped. */
    fun prefetch(c: Context, pics: List<String>) {
        for (p in pics.distinct()) if (!file(c, p).isFile) try { download(p, file(c, p)).also { if (it) trim(c, file(c, p)) } } catch (e: Exception) {}
    }

    private fun download(pic: String, f: File): Boolean {
        val tmp = File(f.path + ".part")
        val k = URL(pic).openConnection() as HttpURLConnection
        return try {
            k.connectTimeout = 8_000; k.readTimeout = 10_000
            if (k.responseCode != 200) return false
            var n = 0
            tmp.outputStream().use { o -> k.inputStream.use { s ->
                val b = ByteArray(16 * 1024)
                while (true) { val r = s.read(b); if (r < 0) break; n += r; if (n > MAX) return false; o.write(b, 0, r) }
            } }
            n > 0 && tmp.renameTo(f)
        } catch (e: Exception) { false } finally { k.disconnect(); if (tmp.exists()) tmp.delete() }
    }

    private fun trim(c: Context, keep: File) {
        val all = dir(c).listFiles { x -> x.name.endsWith(".jpg") }?.sortedByDescending { it.lastModified() } ?: return
        all.drop(KEEP).filter { it != keep }.forEach { it.delete() }
    }
}
