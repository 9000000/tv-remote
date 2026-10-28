package dev.tvremote.home.widgets

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.SAXParserFactory

/**
 * The News widget's data: headlines from RSS feeds ([Sources] for the defaults by language, the person's own links on top): title, the
 * picture the feed gives, the summary the feed gives, the link and the time. Never the article itself. Fetched on a thread of its own,
 * each feed capped ([Net.FEED_CAP]) and read only as far as its newest [PER_FEED] items; kept in prefs, so a dead network shows the last.
 */
object News {
    class Item(val title: String, val link: String, val summary: String, val image: String, val at: Long, val src: String)
    class Feed(val title: String, val items: List<Item>)

    const val PER_FEED = 15
    const val KEEP = 40
    const val FRESH_MS = 30 * 60_000L
    const val RETRY_MS = 5 * 60_000L
    private const val SUMMARY_MAX = 600

    private class Done : RuntimeException() { override fun fillInStackTrace(): Throwable = this }

    // ---- the parser ----

    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);")
    private val NAMED = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "ndash" to "–", "mdash" to "—",
        "hellip" to "…", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "laquo" to "«", "raquo" to "»", "copy" to "©")
    private val TAG = Regex("<[^>]*>")
    private val IMG = Regex("<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    /** [s] with the markup taken out, the entities turned into their characters and the white space made one space. */
    fun plain(s: String): String = TAG.replace(s.replace(Regex("<(br|/p|/div|/li)[^>]*>", RegexOption.IGNORE_CASE), " "), "")
        .let { t -> ENTITY.replace(t) { m ->
            val e = m.groupValues[1]
            try { if (e.startsWith("#x")) String(Character.toChars(e.substring(2).toInt(16))) else if (e.startsWith("#")) String(Character.toChars(e.substring(1).toInt())) else NAMED[e] ?: m.value }
            catch (x: Exception) { m.value }
        } }.replace(Regex("\\s+"), " ").trim()

    private const val Y2000 = 946_684_800_000L
    private val DATES = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z", "dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm Z", "EEE, dd MMM yy HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    /** Dates written without a zone (Tuổi Trẻ: 10/25/2026 12:22:00 AM) are read as Vietnam's. */
    private val LOCAL = listOf("MM/dd/yyyy hh:mm:ss a", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")

    /** Epoch ms of an RSS / Atom date in any of the common shapes; 0 when it is none of them. */
    fun date(raw: String): Long {
        var s = raw.replace(Regex("[\\u00a0\\u202f]"), " ").trim()      // Tuổi Trẻ writes a narrow no-break space before AM / PM
        if (s.isEmpty()) return 0
        s = s.replace(Regex("(GMT|UTC|UT)$"), "+0000").replace(Regex("\\s*(GMT|UTC)\\+?(-?\\d{1,2})$")) { m -> String.format(Locale.ENGLISH, " %s%02d00", if (m.groupValues[2].startsWith("-")) "-" else "+", Math.abs(m.groupValues[2].toInt())) }
            .replace(Regex("Z$"), "+0000").replace(Regex("([+-]\\d\\d):(\\d\\d)$"), "$1$2")
        for (p in DATES) try { return SimpleDateFormat(p, Locale.ENGLISH).parse(s)!!.time.also { require(it > Y2000) } } catch (e: Exception) { }   // a lenient yyyy takes "26" as the year 26
        for (p in LOCAL) try { return SimpleDateFormat(p, Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh") }.parse(s)!!.time } catch (e: Exception) { }
        return 0
    }

    private class Handler(val src: String) : DefaultHandler() {
        val items = ArrayList<Item>(); var title = ""
        private val path = ArrayList<String>(); private val text = StringBuilder()
        private var inItem = false
        private var t = ""; private var link = ""; private var desc = ""; private var content = ""; private var date = ""; private var image = ""

        private fun pic(url: String?, type: String? = null, medium: String? = null) {
            if (image.isNotEmpty() || url.isNullOrBlank() || !url.startsWith("http")) return
            val ok = (type ?: "").startsWith("image") || medium == "image" || Regex("\\.(jpe?g|png|webp|gif)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url) || (type == null && medium == null)
            if (ok && !(type ?: "").startsWith("video") && !(type ?: "").startsWith("audio")) image = url
        }

        override fun startElement(u: String?, l: String?, q: String, a: Attributes) {
            path.add(q); text.setLength(0)
            if (q == "item" || q == "entry") { inItem = true; t = ""; link = ""; desc = ""; content = ""; date = ""; image = "" }
            else if (inItem) when (q) {
                "enclosure" -> pic(a.getValue("url"), a.getValue("type"))
                "media:content" -> pic(a.getValue("url"), a.getValue("type"), a.getValue("medium"))
                "media:thumbnail" -> pic(a.getValue("url"))
                "link" -> if (a.getValue("href") != null && (a.getValue("rel") ?: "alternate") == "alternate") link = a.getValue("href")
            }
        }

        override fun characters(c: CharArray, s: Int, n: Int) { text.append(c, s, n) }

        override fun endElement(u: String?, l: String?, q: String) {
            val v = text.toString().trim()
            path.removeAt(path.size - 1)
            if (inItem) when (q) {
                "title" -> t = v
                "link" -> if (v.isNotEmpty()) link = v
                "description", "summary" -> desc = v
                "content:encoded", "content" -> content = v
                "pubDate", "dc:date", "published", "updated" -> if (date.isEmpty() || q != "updated") date = v
                "item", "entry" -> {
                    inItem = false
                    val title = plain(t)
                    if (title.isNotEmpty() && link.startsWith("http")) {
                        val img = image.ifEmpty { IMG.find(desc)?.groupValues?.get(1) ?: IMG.find(content)?.groupValues?.get(1) ?: "" }
                        items.add(Item(title, link, plain(desc.ifEmpty { content }).take(SUMMARY_MAX), if (img.startsWith("http")) img else "", date(date), src))
                    }
                    if (items.size >= PER_FEED) throw Done()
                }
            } else if (q == "title" && title.isEmpty() && path.lastOrNull().let { it == "channel" || it == "feed" }) title = plain(v)
            text.setLength(0)
        }
    }

    /** The newest items of an RSS 2.0 or Atom feed in [s] (at most [PER_FEED]); [src] names the source on each. Null when it is not XML. */
    fun parse(s: InputStream, src: String): Feed? {
        val h = Handler(src)
        val f = SAXParserFactory.newInstance()
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (e: Exception) { }   // no entity tricks
        try { f.newSAXParser().parse(InputSource(s), h) } catch (e: Exception) { }   // the stop at the last item wanted, or a feed cut by the size cap: what was read stays
        return if (h.items.isEmpty() && h.title.isEmpty()) null else Feed(h.title, h.items)
    }

    /** The items of every feed together: newest first, one per link, at most [KEEP]. */
    fun merge(feeds: List<List<Item>>): List<Item> =
        feeds.flatten().distinctBy { it.link }.sortedByDescending { it.at }.take(KEEP)

    fun toJson(list: List<Item>): JSONArray = JSONArray(list.map { JSONObject().put("t", it.title).put("l", it.link).put("s", it.summary).put("i", it.image).put("a", it.at).put("n", it.src) })
    fun fromJson(a: JSONArray): List<Item> = (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Item(it.optString("t"), it.optString("l"), it.optString("s"), it.optString("i"), it.optLong("a"), it.optString("n")) } }

    /**
     * The feeds to read: TV Home's default list for [lang] ("auto"), the person's own links ("mine"), or both. A link's name is its
     * host until the feed tells its own title.
     */
    fun urls(src: String, lang: String, mine: List<String>, all: Sources.All = Sources.now): List<Pair<String, String>> {
        val own = mine.map { u -> (try { java.net.URI(u).host } catch (e: Exception) { null } ?: u).removePrefix("www.") to u }
        val def = Sources.feedsFor(lang, all).map { it.name to it.url }
        return when (src) { "mine" -> own; "both" -> own + def; else -> def }.distinctBy { it.second }
    }

    // ---- the network and the prefs, the thin part ----

    private fun prefs(c: Context) = c.getSharedPreferences("news", Context.MODE_PRIVATE)

    fun cached(c: Context): List<Item> = prefs(c).getString("items", null)?.let { try { fromJson(JSONArray(it)) } catch (e: Exception) { null } }.orEmpty()

    /** True when the feeds should be read again: other feeds than last time (at once), nothing yet, or an old read (not just after a failure). */
    fun due(c: Context, feeds: List<Pair<String, String>>, now: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(c); val sig = feeds.joinToString("|") { it.second }
        if (feeds.isEmpty()) return false
        if (p.getString("sig", "") != sig) return now - p.getLong("tried", 0) >= RETRY_MS || now < p.getLong("tried", 0)
        if (now - p.getLong("tried", 0) < RETRY_MS && now >= p.getLong("tried", 0)) return false
        return now - p.getLong("at", 0) > FRESH_MS || cached(c).isEmpty()
    }

    private var busy = false

    fun refresh(c: Context, feeds: List<Pair<String, String>>, done: () -> Unit) {
        synchronized(this) { if (busy) return; busy = true }
        Thread {
            try {
                val ctx = c.applicationContext; val now = System.currentTimeMillis(); val p = prefs(ctx)
                var bad = 0
                val got = feeds.map { (name, url) ->
                    val f = Net.open(url, Net.FEED_CAP, 10000) { parse(it, name) }
                    if (f == null || f.items.isEmpty()) bad++
                    f?.items?.map { if (name == it.src && f.title.isNotEmpty() && name.contains('.')) Item(it.title, it.link, it.summary, it.image, it.at, f.title) else it }.orEmpty()
                }
                val list = merge(got)
                val e = p.edit().putLong("tried", if (bad > 0) now else 0).putString("sig", feeds.joinToString("|") { it.second })
                if (list.isNotEmpty()) e.putString("items", toJson(list).toString()).putLong("at", now)
                e.apply()
                if (list.isNotEmpty()) done()
            } catch (e: Exception) {
            } finally { synchronized(this) { busy = false } }
        }.start()
    }
}
