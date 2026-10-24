package dev.tvremote.karaoke

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Reading what a search returns, with no Android and no network (unit-tested on saved samples). YouTube's own pages change shape
 * now and then, so the web readers walk the whole JSON for what they know and skip what they do not: a changed page gives fewer
 * songs, not a crash.
 */
object SearchParse {
    class Api(val songs: List<Song>, val quota: Boolean, val error: String?)

    /** `search.list` answer: [Api.quota] on 403 quotaExceeded / rateLimitExceeded, [Api.error] set on any other error (a wrong key...). */
    fun api(json: String): Api {
        val o = try { JSONObject(json) } catch (e: Exception) { return Api(emptyList(), false, "unreadable") }
        o.optJSONObject("error")?.let { err ->
            val reasons = err.optJSONArray("errors")?.let { a -> (0 until a.length()).map { a.optJSONObject(it)?.optString("reason") ?: "" } } ?: emptyList()
            val quota = reasons.any { it == "quotaExceeded" || it == "rateLimitExceeded" || it == "dailyLimitExceeded" }
            return Api(emptyList(), quota, reasons.firstOrNull() ?: ("http" + err.optInt("code")))
        }
        val items = o.optJSONArray("items") ?: return Api(emptyList(), false, null)
        val out = ArrayList<Song>()
        for (i in 0 until items.length()) {
            val it = items.optJSONObject(i) ?: continue
            val id = it.optJSONObject("id")?.optString("videoId") ?: continue
            if (!KaraokeQueue.validId(id)) continue
            val sn = it.optJSONObject("snippet") ?: continue
            out.add(Song(id, unescape(sn.optString("title")), thumb(id), unescape(sn.optString("channelTitle"))))
        }
        return Api(out, false, null)
    }

    /** The InnerTube `youtubei/v1/search` answer, or a page's `ytInitialData` (the same tree). */
    fun innerTube(json: String): List<Song> {
        val root: Any = try { JSONObject(json) } catch (e: Exception) { return emptyList() }
        val out = ArrayList<Song>(); val seen = HashSet<String>()
        fun add(s: Song) { if (seen.add(s.vid)) out.add(s) }
        fun walk(n: Any?, depth: Int) {
            if (depth > 40 || out.size >= 20) return
            when (n) {
                is JSONObject -> for (k in n.keys()) {
                    val v = n.opt(k)
                    when {
                        k == "videoRenderer" && v is JSONObject -> video(v)?.let { add(it) }
                        k == "lockupViewModel" && v is JSONObject -> lockup(v)?.let { add(it) }
                        else -> walk(v, depth + 1)
                    }
                }
                is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i), depth + 1)
            }
        }
        walk(root, 0)
        return out
    }

    /** A results page: the JSON after `ytInitialData =`, cut out by counting braces (strings respected), then read like [innerTube]. */
    fun html(html: String): List<Song> {
        val at = html.indexOf("ytInitialData"); if (at < 0) return emptyList()
        val start = html.indexOf('{', html.indexOf('=', at).takeIf { it > 0 } ?: return emptyList()); if (start < 0) return emptyList()
        var depth = 0; var inStr = false; var esc = false
        for (i in start until html.length) {
            val c = html[i]
            if (inStr) { if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false; continue }
            if (c == '"') inStr = true else if (c == '{') depth++ else if (c == '}') { depth--; if (depth == 0) return innerTube(html.substring(start, i + 1)) }
        }
        return emptyList()
    }

    private fun video(v: JSONObject): Song? {
        val id = v.optString("videoId"); if (!KaraokeQueue.validId(id)) return null
        val len = text(v.optJSONObject("lengthText")); if (len.isEmpty()) return null       // a live stream / a premiere: no length, no karaoke
        val title = text(v.optJSONObject("title")).ifEmpty { return null }
        val by = text(v.optJSONObject("ownerText")).ifEmpty { text(v.optJSONObject("longBylineText")) }
        return Song(id, unescape(title), thumb(id), unescape(by), len)
    }

    private fun lockup(v: JSONObject): Song? {
        if (v.optString("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val id = v.optString("contentId"); if (!KaraokeQueue.validId(id)) return null
        val title = v.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")?.optJSONObject("title")?.optString("content") ?: return null
        return Song(id, unescape(title), thumb(id))
    }

    /** `simpleText`, or the `runs` joined. */
    private fun text(o: JSONObject?): String {
        if (o == null) return ""
        o.optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
        val r = o.optJSONArray("runs") ?: return ""
        return (0 until r.length()).joinToString("") { r.optJSONObject(it)?.optString("text") ?: "" }
    }

    /** A small thumbnail by the id: stable, no signed address to expire in a queue. */
    fun thumb(id: String) = "https://i.ytimg.com/vi/$id/mqdefault.jpg"

    fun unescape(s: String): String {
        if ('&' !in s) return s
        return Regex("&(#x?[0-9a-fA-F]+|amp|quot|lt|gt|apos);").replace(s) { m ->
            when (val g = m.groupValues[1]) {
                "amp" -> "&"; "quot" -> "\""; "lt" -> "<"; "gt" -> ">"; "apos" -> "'"
                else -> try { String(Character.toChars(if (g[1] == 'x') g.substring(2).toInt(16) else g.substring(1).toInt())) } catch (e: Exception) { m.value }
            }
        }
    }
}

/**
 * Search, in the order the person chose: their API key first (the official `search.list`, embeddable videos only), and when there is no
 * key or it fails (quota, wrong key, no network to googleapis), YouTube's public results. The key goes only into the request: never
 * logged, never in a message. [quotaHits] counts the answers that said "quota used up" since the app started.
 */
class Searcher(private val http: (url: String, post: String?) -> String? = ::fetch) {
    class Out(val songs: List<Song>, val source: String, val note: String, val error: Boolean)

    @Volatile var quotaHits = 0; private set

    fun search(q: String, key: String): Out {
        val query = q.trim().take(100)
        if (query.isEmpty()) return Out(emptyList(), "none", "", false)
        var note = ""
        if (key.isNotEmpty()) {
            val url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&videoEmbeddable=true&maxResults=15" +
                "&q=" + enc("$query karaoke") + "&key=" + enc(key)
            val body = http(url, null)
            if (body != null) {
                val r = SearchParse.api(body)
                if (r.error == null) return Out(r.songs, "api", "", false)
                if (r.quota) { quotaHits++; note = "quota" } else note = "key"
            } else note = "net"
        }
        val songs = web(query)
        if (songs.isNotEmpty()) return Out(songs, "web", note, false)
        return Out(emptyList(), "web", note, true)
    }

    private fun web(query: String): List<Song> {
        val q = "$query karaoke"
        val body = "{\"context\":{\"client\":{\"clientName\":\"WEB\",\"clientVersion\":\"2.20250101.00.00\",\"hl\":\"vi\",\"gl\":\"VN\"}},\"query\":${JSONObject.quote(q)}}"
        http("https://www.youtube.com/youtubei/v1/search?prettyPrint=false", body)?.let { SearchParse.innerTube(it) }?.takeIf { it.isNotEmpty() }?.let { return it }
        http("https://www.youtube.com/results?search_query=" + enc(q) + "&hl=vi", null)?.let { SearchParse.html(it) }?.takeIf { it.isNotEmpty() }?.let { return it }
        return emptyList()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        /** GET, or POST of a JSON body; the body of the answer even for an HTTP error (the API explains itself there), null when unreachable. */
        fun fetch(url: String, post: String?): String? = try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
            c.setRequestProperty("Accept-Language", "vi,en;q=0.8")
            c.setRequestProperty("Cookie", "CONSENT=YES+1")                  // EU consent wall otherwise
            if (post != null) { c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(post.toByteArray()) } }
            val s = try { c.inputStream } catch (e: Exception) { c.errorStream }
            s?.use { val b = it.readBytes(); String(b, 0, minOf(b.size, 4_000_000), Charsets.UTF_8) }
        } catch (e: Exception) { null }
    }
}
