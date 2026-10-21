package dev.tvremote.channels

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Every country's channels in one list, to search a name without knowing its country. Source: iptv-org's index.country.m3u
 * (~3 MB, 12.8 k entries; the API's channels + streams json are 4-8 MB each). Downloaded on the first search that needs it,
 * parsed line by line into a tab-separated index on disk (name already folded; a day, like the catalog), and held in memory
 * as plain strings only while searching ([release]). The m3u itself is never held whole.
 */
class GlobalIndex private constructor(private val lines: Array<String>, private val folds: Array<String>) {
    val size get() = lines.size

    /** Up to [cap] channels (all streams of one id together) matching [q], best first; [skip] drops a country (shown above already). */
    fun search(q: String, skip: String, cap: Int = CAP): List<List<Channel>> {
        val t = Find.fold(q)
        if (t.isEmpty()) return emptyList()
        val hits = ArrayList<Pair<Int, Int>>()                            // rank, line
        for (i in folds.indices) {
            val r = rank(folds[i], t) ?: continue
            hits.add(r to i)
        }
        hits.sortWith(compareBy({ it.first }, { folds[it.second].length }, { it.second }))
        val out = LinkedHashMap<String, ArrayList<Channel>>()
        for ((_, i) in hits) {
            val c = channel(lines[i])
            if (skip.isNotEmpty() && c.country == skip) continue
            val l = out[c.id]
            if (l != null) l.add(c) else { if (out.size >= cap) continue; out[c.id] = arrayListOf(c) }
        }
        return out.values.toList()
    }

    companion object {
        const val CAP = 60
        private const val URL_ = "${Catalog.BASE}/iptv/index.country.m3u"
        private const val FILE = "global-v1.tsv"
        private const val DAY = 24 * 3600_000L
        @Volatile private var held: GlobalIndex? = null

        /** Drops the in-memory copy (the disk copy stays). */
        fun release() { held = null }
        fun held() = held != null

        /** The index, from memory, else the disk copy (under a day old, or the network failing), else downloaded. Null: offline and never fetched. Blocking. */
        @Synchronized fun get(c: Context): GlobalIndex? {
            held?.let { return it }
            val f = File(c.filesDir, FILE)
            if (!(f.exists() && System.currentTimeMillis() - f.lastModified() < DAY)) {
                val ok = try { download(c, f) } catch (e: Exception) { false }
                if (!ok && !f.exists()) return null
            }
            return try { load(f.bufferedReader()).also { held = it } } catch (e: Exception) { null }
        }

        private fun download(c: Context, f: File): Boolean {
            val k = URL(URL_).openConnection() as HttpURLConnection
            k.connectTimeout = 8000; k.readTimeout = 20000
            k.setRequestProperty("User-Agent", "TVChannels/1 (Android)")
            try {
                if (k.responseCode != 200) return false
                val tmp = File(c.filesDir, "$FILE.tmp")
                tmp.bufferedWriter().use { w -> k.inputStream.bufferedReader().use { r -> write(r, w) } }
                return tmp.renameTo(f)
            } finally { k.disconnect() }
        }

        /** m3u in, index lines out, one channel at a time. */
        fun write(m3u: BufferedReader, out: java.io.Writer) {
            out.write("v1\n")
            M3u.parseLines(m3u.lineSequence()) { ch ->
                out.write(listOf(ch.id, ch.name, Find.fold(ch.name), ch.group, ch.logo, ch.url, ch.quality,
                    (if (ch.geo) 1 else 0) + (if (ch.notAlways) 2 else 0), ch.referrer, ch.agent).joinToString("\t") { it.toString().replace('\t', ' ') })
                out.write("\n")
            }
        }

        fun load(r: BufferedReader): GlobalIndex {
            val ls = ArrayList<String>(); val fs = ArrayList<String>()
            r.useLines { seq -> seq.drop(1).forEach { l ->
                val p = l.split('\t', limit = 4)
                if (p.size >= 4) { ls.add(l); fs.add(p[2]) }
            } }
            return GlobalIndex(ls.toTypedArray(), fs.toTypedArray())
        }

        fun channel(l: String): Channel {
            val p = l.split('\t')
            fun s(i: Int) = p.getOrElse(i) { "" }
            val fl = s(7).toIntOrNull() ?: 0
            return Channel(s(0), s(1), s(4), s(3), s(5), s(6), fl and 1 != 0, fl and 2 != 0, s(8), s(9))
        }

        /** 0 the whole name, 1 begins with [q] as whole words ("cartoon" -> Cartoon Network), 2 begins with it (Cartoonito), 3 every word of [q] begins a word of the name, 4 spelled inside (no spaces); null no match. Both already folded. */
        fun rank(name: String, q: String): Int? {
            if (name == q) return 0
            if (name.startsWith("$q ")) return 1
            if (name.startsWith(q)) return 2
            val nw = name.split(' ')
            if (q.split(' ').all { w -> nw.any { it.startsWith(w) } }) return 3
            val qc = q.replace(" ", "")
            return if (qc.length >= 3 && name.replace(" ", "").contains(qc)) 4 else null
        }
    }
}
