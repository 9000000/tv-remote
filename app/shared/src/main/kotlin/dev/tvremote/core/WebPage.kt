package dev.tvremote.core

/**
 * The page all three remotes serve (app/shared/web), read through [open]: this app's assets, or the folder itself on the computer. The computer's server sends the very same
 * files; [core] tells the page which of the three it is talking to ("tv" or "phone" here, see js/core.js).
 */
class WebPage(private val open: (String) -> java.io.InputStream, private val core: String, private val stamp: String) {
    private val cache = HashMap<String, ByteArray>()

    /** null when the path is not one of the page's files. */
    fun serve(path: String): HttpServer.Response? {
        val name = if (path == "/" || path == "/index.html") "index.html" else path.substring(1)
        if (!FILE.matches(name)) return null
        val bytes = synchronized(cache) {
            cache[name] ?: try {
                var b = open(name).use { it.readBytes() }
                if (name == "index.html") b = page(String(b, Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
                b.also { if (cache.size < 30) cache[name] = it }
            } catch (e: Exception) { null }
        } ?: return null
        val type = when (name.substringAfterLast('.')) {
            "html" -> "text/html; charset=utf-8"; "css" -> "text/css; charset=utf-8"; "png" -> "image/png"
            "json" -> "application/manifest+json"; else -> "text/javascript; charset=utf-8"
        }
        val out = HttpServer.Response.of(type, bytes)
        // the page names its files with a stamp that changes with every update of the app, so they can be kept; the page itself never is
        out.headers["Cache-Control"] = if (name == "index.html") "no-store" else "max-age=604800"
        return out
    }

    private fun page(html: String): String =
        LINK.replace(html.replaceFirst("<html ", "<html data-core=\"$core\" ")) { it.groupValues[1] + "?v=" + stamp + "\"" }

    private companion object {
        val FILE = Regex("index\\.html|style\\.css|stats\\.js|jmuxer\\.min\\.js|js/[a-z0-9_-]+\\.js|manifest\\.json|remote\\.json|icon-[a-z0-9]+\\.png")   // manifest and icons: "Add to Home screen"; remote.json: the remote's keys, which the Phone app reads too
        val LINK = Regex("((?:src|href)=\"(?:style\\.css|stats\\.js|js/[^\"?]+))\"")
    }
}
