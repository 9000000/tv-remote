package dev.tvremote.desktop

import dev.tvremote.core.Words
import dev.tvremote.core.web.HttpServer
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A key made at each start that every request must carry. 127.0.0.1 is reachable by every program and every other user of
 * this computer, and the TVs' keys are behind this server. The window and the browser are opened with it in the link
 * (?k=), and the server swaps it for a cookie at once, so nobody types anything. A second start (Windows, Linux: the app
 * opened twice) reads it from [file] to open one more window. TVR_PASS fixes it, for test servers and dev/shots/shots.py.
 */
class Pass(private val file: File) {
    val key: String = System.getenv("TVR_PASS")?.takeIf { it.length >= 8 }
        ?: ByteArray(18).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    /** Written once the port is ours: a start that then finds the port taken must not leave the running app's key unreadable. */
    fun save() {
        try {
            file.delete()
            file.createNewFile()
            file.setReadable(false, false); file.setReadable(true, true)   // this user only (Windows: the profile is already theirs)
            file.setWritable(false, false); file.setWritable(true, true)
            file.writeText(key)
        } catch (e: Exception) { println("pass: $e") }               // only the second window needs it
    }

    fun link(base: String) = "$base/?k=$key"

    private fun same(s: String?) = s != null && MessageDigest.isEqual(s.toByteArray(), key.toByteArray())

    /** null: let it through. Otherwise the answer: the cookie for a link carrying the key, a refusal without it. */
    fun check(r: HttpServer.Request): HttpServer.Response? {
        val k = r.query["k"]
        if (k != null && !r.path.startsWith("/api/") && same(k)) {
            val rest = r.query.filterKeys { it != "k" }.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
            return HttpServer.Response().apply {
                status = 302
                headers["Location"] = r.path + (if (rest.isEmpty()) "" else "?$rest")   // the key out of the address bar and the history
                headers["Set-Cookie"] = "$COOKIE=$key; Path=/; HttpOnly; SameSite=Strict"
            }
        }
        if (same(r.cookie(COOKIE)) || same(r.headers[HEADER])) return null
        if (r.path.startsWith("/api/")) return HttpServer.Response.error(403, "locked")
        return HttpServer.Response.of("text/html; charset=utf-8", locked().toByteArray()).apply { status = 403 }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        const val COOKIE = "tvr_pc"
        const val HEADER = "x-tvr-pass"                                // the app talking to itself: a second start, Window.save

        /** The running app's key, for a second start. */
        fun read(file: File): String = try { file.readText().trim() } catch (e: Exception) { "" }

        /** No page to ask which language: both. */
        private fun locked() = "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'><title>TV Remote</title>" +
            "<body style='font:16px system-ui;background:#0d1014;color:#e6e9ee;padding:40px 16px;text-align:center'>" +
            "<p>${Words.get("vi", "pc.locked")}</p><p style='color:#9aa4b2'>${Words.get("en", "pc.locked")}</p>"
    }
}
