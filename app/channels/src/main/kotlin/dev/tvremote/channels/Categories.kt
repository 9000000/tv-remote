package dev.tvremote.channels

import android.content.Context
import java.io.File
import java.io.IOException
import java.text.Normalizer
import org.json.JSONException
import org.json.JSONObject

/**
 * Ready-made categories of a country (kids, movies, music, a broadcaster's channels…), so a viewer finds channels by browsing, not by
 * building groups. The app knows no country: a country has them when the site has `channels/categories/<cc>.json`
 * ({"v":1,"categories":[{"id","<lang>":"label",…,"match":[…]}]}, in display order); none (404) and the country keeps the plain list.
 * The file is DATA kept a day per country (the old copy serves offline). It names channels only, so it is not signed (a wrong name
 * only moves a card). A channel is matched by its [norm]alised name against each entry: a plain entry is equal, "re:" is a regex on
 * the normalised name; it lands in every category it matches.
 * Pure but for [current] / [refresh], so the matching is unit-tested (CategoriesTest, with the cases the web page shares).
 */
object Categories {
    private val BASES = listOf("https://tvremote.pages.dev/channels/categories/", "https://raw.githubusercontent.com/HoangTran0410/tv-remote/main/docs/channels/categories/")
    private const val DAY = 24 * 3600_000L
    private val TAIL = listOf("fhd", "uhd", "hd", "sd", "4k")                 // longest first: "fhd" before "hd"

    /** [labels]: the words by language code, whatever the file carries ("vi", "en", "de"…). */
    class Cat(val id: String, private val labels: Map<String, String>, private val exact: Map<String, Int>, private val regex: List<Pair<Int, Regex>>) {
        /** The label in [lang], else English, else the id. */
        fun title(lang: String) = labels[lang] ?: labels["en"] ?: id

        /** Index of the first entry [n] (a [norm]alised name) matches, or -1. */
        fun at(n: String): Int {
            var best = exact[n] ?: Int.MAX_VALUE
            for ((i, r) in regex) { if (i >= best) break; if (r.containsMatchIn(n)) { best = i; break } }
            return if (best == Int.MAX_VALUE) -1 else best
        }
    }

    class Doc(val updated: String, val cats: List<Cat>)

    /** One shelf: a category and its channels (each channel its streams, as [byChannel] makes them), in the category's order. */
    class Section(val cat: Cat, val items: List<List<Channel>>)

    class Result(val sections: List<Section>, val others: List<List<Channel>>)

    /** NFD, marks dropped, đ -> d, lowercase, only a-z 0-9, then "hd" / "fhd" / "uhd" / "sd" / "4k" taken off the end (again and again). */
    fun norm(name: String): String {
        val d = Normalizer.normalize(name, Normalizer.Form.NFD)
        val sb = StringBuilder(d.length)
        for (ch in d) {
            val c = when (ch) { 'đ', 'Đ', 'ð', 'Ð' -> 'd'; else -> ch.lowercaseChar() }
            if (c in 'a'..'z' || c in '0'..'9') sb.append(c)          // a combining mark is neither: dropped with the spaces and dots
        }
        var s = sb.toString()
        while (true) { val t = TAIL.firstOrNull { s.endsWith(it) } ?: break; s = s.dropLast(t.length) }
        return s
    }

    fun parse(text: String): Doc? = try {
        val o = JSONObject(text)
        val a = o.optJSONArray("categories")
        if (o.optInt("v") != 1 || a == null) null else {
            val cats = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { c ->
                val id = c.optString("id").ifEmpty { return@mapNotNull null }
                val labels = HashMap<String, String>()
                for (k in c.keys()) if (k != "id" && k != "match") c.optString(k).takeIf { it.isNotEmpty() && c.opt(k) is String }?.let { labels[k] = it }
                val m = c.optJSONArray("match") ?: return@mapNotNull null
                val exact = HashMap<String, Int>(); val re = ArrayList<Pair<Int, Regex>>()
                for (i in 0 until m.length()) {
                    val e = m.optString(i)
                    if (e.startsWith("re:")) try { re.add(i to Regex(e.substring(3))) } catch (x: Exception) { }   // a bad pattern loses its entry only
                    else norm(e).let { if (it.isNotEmpty() && !exact.containsKey(it)) exact[it] = i }
                }
                Cat(id, labels, exact, re)
            }
            if (cats.isEmpty()) null else Doc(o.optString("updated"), cats)
        }
    } catch (e: JSONException) { null }

    /** Digits compare as numbers, so VTV2 comes before VTV10. */
    private val natural = Comparator<String> { a, b ->
        var i = 0; var j = 0
        while (i < a.length && j < b.length) {
            if (a[i].isDigit() && b[j].isDigit()) {
                var x = i; while (x < a.length && a[x].isDigit()) x++
                var y = j; while (y < b.length && b[y].isDigit()) y++
                val na = a.substring(i, x).trimStart('0'); val nb = b.substring(j, y).trimStart('0')
                if (na.length != nb.length) return@Comparator na.length - nb.length
                val c = na.compareTo(nb); if (c != 0) return@Comparator c
                i = x; j = y
            } else { if (a[i] != b[j]) return@Comparator a[i] - b[j]; i++; j++ }
        }
        (a.length - i) - (b.length - j)
    }

    /**
     * The shelves of [doc] for [list] (one entry per channel): a category with no channel is left out; inside one the order is the
     * index of the first entry that matched, then the name; two cards with the same normalised name (a playlist and a provider
     * both carry VTV1) show once. [others] is every channel in no category, in the list's order.
     */
    fun categorize(doc: Doc, list: List<List<Channel>>): Result {
        val norms = list.map { norm(it.first().name) }
        val hit = BooleanArray(list.size)
        val out = ArrayList<Section>()
        for (cat in doc.cats) {
            val picked = ArrayList<Triple<Int, String, Int>>()          // entry index, normalised name, place in [list]
            for (k in list.indices) { val at = cat.at(norms[k]); if (at >= 0) { picked.add(Triple(at, norms[k], k)); hit[k] = true } }
            if (picked.isEmpty()) continue
            picked.sortWith(compareBy<Triple<Int, String, Int>> { it.first }.thenComparator { a, b -> natural.compare(a.second, b.second) })
            val seen = HashSet<String>()
            val items = picked.filter { seen.add(it.second) }.map { list[it.third] }
            out.add(Section(cat, items))
        }
        return Result(out, list.filterIndexed { k, _ -> !hit[k] })
    }

    // ---- the data: the site's file per country ----

    private val mem = HashMap<String, Doc>()
    private fun file(c: Context, cc: String) = File(c.filesDir, "cats-$cc.json")
    private fun none(c: Context, cc: String) = File(c.filesDir, "cats-$cc.none")          // the site has no file for this country (asked a day ago)

    /** The kept copy at once, off the network and whatever its age; null: none kept, or the site has none for [cc]. */
    @Synchronized fun current(c: Context, cc: String): Doc? {
        mem[cc]?.let { return it }
        val doc = file(c, cc).takeIf { it.exists() }?.let { try { parse(it.readText()) } catch (e: IOException) { null } }
        return doc?.also { mem[cc] = it }
    }

    /** Whether the kept answer for [cc] (a file, or "none") is over a day old or was never asked. */
    fun stale(c: Context, cc: String): Boolean {
        val f = file(c, cc).takeIf { it.exists() } ?: none(c, cc).takeIf { it.exists() } ?: return true
        return System.currentTimeMillis() - f.lastModified() >= DAY
    }

    /** Asks the site for [cc]'s file (when [force] or [stale]); keeps it if it parses. A 404 is remembered for a day; offline keeps the old copy. */
    fun refresh(c: Context, cc: String, force: Boolean = false) {
        if (!force && !stale(c, cc)) return
        var missing = false
        for (b in BASES) try {
            val text = Catalog.get("$b$cc.json") ?: continue
            if (text.isEmpty()) { missing = true; continue }           // 404: the other address may still have it
            val doc = parse(text) ?: continue                          // not ours (a captive portal's page): the next address
            File(c.filesDir, "cats-$cc.json.tmp").apply { writeText(text); renameTo(file(c, cc)) }
            none(c, cc).delete(); synchronized(this) { mem[cc] = doc }
            return
        } catch (e: Exception) { }
        val f = file(c, cc)
        if (f.exists()) f.setLastModified(System.currentTimeMillis() - DAY + 30 * 60_000L)   // offline: asked again in half an hour
        else if (missing) { none(c, cc).writeText(""); synchronized(this) { mem.remove(cc) } }
    }
}
