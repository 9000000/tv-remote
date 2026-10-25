package dev.tvremote.home.wall

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * The site's slideshow themes (`loop.html?s=<name>`, e.g. the "Việt Nam" theme) shown natively, like [NetPicture] does for one picture:
 * the list `docs/home/<name>.json` (file, vi, en, credit) is fetched once a day into a small file, each picture comes through
 * [NetPicture]'s cache, and TV Home turns to the next every [Ref.every] ms while it is on screen. Older TV Homes run the same URL in a
 * WebView (loop.html has the same mode), so the address and the list file are the whole contract; nothing here needs a release of the site.
 */
object Slideshow {
    const val MAX = 24                                           // pictures per list (NetPicture keeps this many files)
    const val DAY = 24L * 3600 * 1000
    private const val SITE = "https://tvremote.pages.dev/home/"

    class Ref(val name: String, val every: Long)
    class Slide(val pic: String, val vi: String, val en: String, val credit: String) {
        /** The caption line: the person's language first (Vietnamese or not), then the other. */
        fun line(vi0: Boolean) = if (vi0) "$vi · $en" else "$en · $vi"
    }

    private val NAME = Regex("[a-z0-9-]{1,24}")
    private val FILE = Regex("[a-z0-9._-]{1,60}\\.jpg")

    /** The list's name (and seconds between pictures, `t`: 5..3600, 300 when absent) when [url] is the site's loop page with `?s=`; else null. No network. */
    fun ref(url: String): Ref? {
        val u = try { URI(url) } catch (e: Exception) { return null }
        if (u.scheme != "https" || u.host != "tvremote.pages.dev" || u.path.orEmpty() != "/home/loop.html") return null
        val q = u.rawQuery?.split('&')?.mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }?.toMap() ?: return null
        val name = q["s"]?.takeIf { NAME.matches(it) } ?: return null
        val t = (q["t"]?.toIntOrNull() ?: 300).coerceIn(5, 3600)
        return Ref(name, t * 1000L)
    }

    /** The slides of a list file's text: bad entries (a file name that is not a plain jpg, no words) dropped, at most [MAX]; never throws. */
    fun parse(text: String): List<Slide> = try {
        val a = JSONObject(text).optJSONArray("slides")
        val out = ArrayList<Slide>()
        if (a != null) for (k in 0 until a.length()) {
            val o = a.optJSONObject(k) ?: continue
            val f = o.optString("file")
            val vi = o.optString("vi").trim().take(80); val en = o.optString("en").trim().take(80)
            if (!FILE.matches(f) || (vi.isEmpty() && en.isEmpty())) continue
            out.add(Slide(SITE + f, vi.ifEmpty { en }, en.ifEmpty { vi }, o.optString("credit").trim().take(100)))
            if (out.size >= MAX) break
        }
        out
    } catch (e: Exception) { emptyList() }

    /** Which slide is on screen at [now]: the clock's slot, so every start and every day begins elsewhere, and a restart returns to the same one. */
    fun index(now: Long, every: Long, n: Int): Int = if (n <= 0) 0 else ((now / every) % n).toInt()

    private fun file(c: Context, name: String) = File(c.filesDir, "slides-$name.json")

    /** The cached list at once (any age), for showing something while the network answers; empty when none. */
    fun kept(c: Context, name: String): List<Slide> = file(c, name).takeIf { it.isFile }?.let { parse(it.readText()) } ?: emptyList()

    /** The list, fetched when the kept copy is over a day old (a failed fetch keeps the old one). Off the main thread. */
    fun load(c: Context, name: String, now: Long = System.currentTimeMillis()): List<Slide> {
        val f = file(c, name)
        if (!f.isFile || now - f.lastModified() > DAY || f.lastModified() > now) fetch(f, name)
        return kept(c, name)
    }

    private fun fetch(f: File, name: String) {
        val k = URL("$SITE$name.json").openConnection() as HttpURLConnection
        try {
            k.connectTimeout = 8_000; k.readTimeout = 10_000
            if (k.responseCode != 200) return
            val text = k.inputStream.use { it.readBytes().takeIf { b -> b.size <= 64 * 1024 } }?.toString(Charsets.UTF_8) ?: return
            if (parse(text).isEmpty()) return                      // not a list (a page the site answered with): the old one stays
            val tmp = File(f.path + ".part")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) tmp.delete()
        } catch (e: Exception) { } finally { k.disconnect() }
    }
}
