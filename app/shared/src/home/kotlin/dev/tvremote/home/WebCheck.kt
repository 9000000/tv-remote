package dev.tvremote.home

import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Whether a web page can be TV Home's background (HomeConfig.wurl), asked before it is set: the settings page shows the answer
 * under the address, so a typo or a server that is off is seen there and not as a plain gradient on the TV. Run where the page is
 * served: TV Home itself ("Manage from phone", ManageServer), so the answer is the TV's own view of the network; the phone and
 * computer hosts ask from where they are (the same Wi-Fi as a rule). Plain JVM: the rules are tested without a TV.
 * The answer: {ok, why, code, type, title, url (after redirects), ms}; why: bad | dns | refused | timeout | ssl | cleartext | status | loop | type | net.
 * A page is shown in a WebView of its own, never in a frame, so X-Frame-Options and CSP frame-ancestors do not matter.
 */
object WebCheck {
    private const val HOPS = 5
    private const val PEEK = 64 * 1024                                // enough for a <title>, not a whole page
    private val TITLE = Regex("<title[^>]*>([^<]{1,200})", RegexOption.IGNORE_CASE)

    fun check(raw: String, timeoutMs: Int = 6000, clock: () -> Long = { System.nanoTime() / 1_000_000 }): JSONObject {
        val u = HomeConfig.wurl(raw)
        if (u.isEmpty()) return no("bad")
        val start = clock()
        var at = u
        try {
            repeat(HOPS) {
                val c = (URL(at).openConnection() as HttpURLConnection).apply {
                    connectTimeout = timeoutMs; readTimeout = timeoutMs; instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")
                    setRequestProperty("Accept", "text/html,*/*")
                }
                try {
                    val code = c.responseCode
                    val next = c.getHeaderField("Location")
                    if (code in 300..399 && next != null) { at = URL(URL(at), next).toString(); return@repeat }   // http to https and back: a WebView follows both
                    val type = (c.contentType ?: "").substringBefore(';').trim().lowercase()
                    val out = JSONObject().put("code", code).put("type", type).put("url", at).put("ms", clock() - start)
                    if (code >= 400) return out.put("ok", false).put("why", "status")
                    val page = type.isEmpty() || type == "text/html" || type == "application/xhtml+xml"
                    if (!page && !type.startsWith("image/") && !type.startsWith("text/")) return out.put("ok", false).put("why", "type")   // a file to download: the WebView would show nothing
                    if (page) title(c)?.let { out.put("title", it) }
                    return out.put("ok", true)
                } finally { c.disconnect() }
            }
            return no("loop").put("url", at)                            // round and round: the WebView gives up too
        } catch (e: UnknownHostException) { return no("dns")
        } catch (e: SocketTimeoutException) { return no("timeout")
        } catch (e: ConnectException) { return no("refused")
        } catch (e: SSLException) { return no("ssl")
        } catch (e: IOException) { return no(if ((e.message ?: "").contains("cleartext", true)) "cleartext" else "net")
        } catch (e: Exception) { return no("bad") }                   // an address URL itself refuses (a port out of range ...)
    }

    private fun no(why: String) = JSONObject().put("ok", false).put("why", why)

    private fun title(c: HttpURLConnection): String? = try {
        val b = c.inputStream.use { s -> val buf = ByteArray(PEEK); var n = 0; while (n < PEEK) { val r = s.read(buf, n, PEEK - n); if (r < 0) break; n += r }; buf.copyOf(n) }
        TITLE.find(String(b, Charsets.UTF_8))?.groupValues?.get(1)?.trim()?.replace(Regex("\\s+"), " ")
            ?.replace("&amp;", "&")?.replace("&#39;", "'")?.replace("&quot;", "\"")?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) { null }
}
