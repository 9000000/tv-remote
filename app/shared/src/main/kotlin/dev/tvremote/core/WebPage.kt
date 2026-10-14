package dev.tvremote.core

import android.content.res.AssetManager

/**
 * The page all three remotes serve (app/shared/web), read from this app's assets. The computer's server sends the very same
 * files; [core] tells the page which of the three it is talking to ("tv" or "phone" here, see js/core.js).
 */
class WebPage(private val assets: AssetManager, private val core: String, private val stamp: String) {
    private val cache = HashMap<String, ByteArray>()

    /** null when the path is not one of the page's files. */
    fun serve(path: String): HttpServer.Response? {
        val name = if (path == "/" || path == "/index.html") "index.html" else path.substring(1)
        if (!FILE.matches(name)) return null
        val bytes = synchronized(cache) {
            cache[name] ?: try {
                var b = assets.open(name).use { it.readBytes() }
                if (name == "index.html") b = page(String(b, Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
                b.also { if (cache.size < 30) cache[name] = it }
            } catch (e: Exception) { null }
        } ?: return null
        val out = HttpServer.Response.of(if (name.endsWith(".html")) "text/html; charset=utf-8" else if (name.endsWith(".css")) "text/css; charset=utf-8" else "text/javascript; charset=utf-8", bytes)
        // the page names its files with a stamp that changes with every update of the app, so they can be kept; the page itself never is
        out.headers["Cache-Control"] = if (name == "index.html") "no-store" else "max-age=604800"
        return out
    }

    private fun page(html: String): String =
        LINK.replace(html.replaceFirst("<html ", "<html data-core=\"$core\" ")) { it.groupValues[1] + "?v=" + stamp + "\"" }

    private companion object {
        val FILE = Regex("index\\.html|style\\.css|stats\\.js|jmuxer\\.min\\.js|js/[a-z0-9_-]+\\.js")
        val LINK = Regex("((?:src|href)=\"(?:style\\.css|stats\\.js|js/[^\"?]+))\"")
    }
}
