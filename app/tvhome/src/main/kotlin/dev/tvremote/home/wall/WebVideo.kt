package dev.tvremote.home.wall

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Finds out whether a "web" wall is really just one video, so it can be played by [VideoWall] (a SurfaceView, hardware decoder, no
 * browser) instead of a WebView: measured on the FPT box, a page with a video cost TV Home 89 % CPU, 63 % janky frames and ~140 MB,
 * the same video as a plain video wall a few percent. Three answers, in this order:
 *  1. the address itself is a media file (.mp4 .webm .m3u8 ...);
 *  2. the address is the project's own loop page (`loop.html?v=<video>`, the site's themes): the video is in `v`;
 *  3. the page's HTML has exactly one `<video>` with a `src` / `<source src>`, and nothing else that shows (no picture, frame,
 *     canvas, svg, outside script): fetched once, lightly.
 * The answer (the video's address, or none) is kept per address, so the page is not asked again.
 */
object WebVideo {
    private const val PREFS = "webvideo"
    private const val NONE = "-"
    private const val RECHECK_MS = 7 * 24 * 3_600_000L              // a page can change into something else; asked again after a week
    private const val HTML_MAX = 256 * 1024
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val MEDIA = Regex("""\.(mp4|m4v|webm|m3u8|mkv|mov)$""", RegexOption.IGNORE_CASE)
    private val HOSTS = setOf("upload.wikimedia.org", "videos.pexels.com")   // docs/home/loop.html's own list of hosts for ?v=

    /** Answers 1 and 2, from the address alone (no network); null = ask the page. */
    fun direct(url: String): String? {
        val u = try { URI(url) } catch (e: Exception) { return null }
        if (u.scheme != "http" && u.scheme != "https") return null
        val path = u.path.orEmpty()
        if (MEDIA.containsMatchIn(path)) return url
        if (path.endsWith("/home/loop.html") && u.host?.endsWith("tvremote.pages.dev") == true) {
            val v = query(u.rawQuery, "v") ?: return null
            val vu = try { URI(v) } catch (e: Exception) { return null }
            return if (vu.scheme == "https" && vu.host in HOSTS) v else null
        }
        return null
    }

    private fun query(q: String?, name: String): String? = q?.split('&')?.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')?.let { java.net.URLDecoder.decode(it, "UTF-8") }

    /** Answer 3 from the page's [html] at [base]: the one video's absolute address, else null. */
    fun fromHtml(html: String, base: String): String? {
        val h = html.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        if (Regex("<(img|iframe|canvas|svg|object|embed|picture|audio)\\b", RegexOption.IGNORE_CASE).containsMatchIn(h)) return null
        if (Regex("<script\\b[^>]*\\bsrc\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(h)) return null
        val scripts = Regex("<script\\b[^>]*>(.*?)</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(h).sumOf { it.groupValues[1].length }
        if (scripts > 2_000) return null                              // a page with a program of its own is more than a video
        val videos = Regex("<video\\b([^>]*)>(.*?)</video>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).findAll(h).toList()
        if (videos.size != 1 || Regex("<video\\b", RegexOption.IGNORE_CASE).findAll(h).count() != 1) return null
        val srcRe = Regex("""\bsrc\s*=\s*["']?([^"'\s>]+)""", RegexOption.IGNORE_CASE)
        val srcs = listOfNotNull(srcRe.find(videos[0].groupValues[1])?.groupValues?.get(1)) +
            Regex("<source\\b([^>]*)>", RegexOption.IGNORE_CASE).findAll(videos[0].groupValues[2]).mapNotNull { srcRe.find(it.groupValues[1])?.groupValues?.get(1) }
        val src = srcs.firstOrNull() ?: return null
        return try {
            val abs = URL(URL(base), src.replace("&amp;", "&")).toString()
            if (abs.startsWith("http://") || abs.startsWith("https://")) abs else null
        } catch (e: Exception) { null }
    }

    /** [done] (main thread) with the video's address or null; at once when the answer is known, else after one light fetch. */
    fun resolve(c: Context, url: String, done: (String?) -> Unit) {
        direct(url)?.let { return done(it) }
        val p = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = Integer.toHexString(url.hashCode())
        p.getString(key, null)?.let { kept ->
            val at = kept.substringBefore(' ').toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - at < RECHECK_MS) return done(kept.substringAfter(' ').takeIf { it != NONE })
        }
        Thread {
            val found = try { fetch(url)?.let { fromHtml(it, url) } } catch (e: Exception) { main.post { done(null) }; return@Thread }   // no network: not kept, the page is shown as it is
            p.edit().putString(key, "${System.currentTimeMillis()} ${found ?: NONE}").apply()
            main.post { done(found) }
        }.start()
    }

    private fun fetch(url: String): String? {
        val k = URL(url).openConnection() as HttpURLConnection
        return try {
            k.connectTimeout = 6_000; k.readTimeout = 6_000
            k.setRequestProperty("Accept", "text/html")
            if (k.responseCode != 200 || k.contentType?.contains("html", true) != true) return ""
            k.inputStream.use { s ->
                val buf = ByteArray(HTML_MAX); var n = 0
                while (n < buf.size) { val r = s.read(buf, n, buf.size - n); if (r < 0) break; n += r }
                String(buf, 0, n, Charsets.UTF_8)
            }
        } finally { k.disconnect() }
    }
}
