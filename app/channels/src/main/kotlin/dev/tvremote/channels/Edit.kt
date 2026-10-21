package dev.tvremote.channels

import org.json.JSONArray
import org.json.JSONObject

/**
 * The favourites and groups as a phone or computer sends them (ChannelsReceiver): by channel id, in the order to keep. Pure, so
 * the cleaning and the merge are tested off a device. A request is the WHOLE state ({favs: [id], groups: [{id, name, ch: [id]}]});
 * a channel already kept keeps every stream it has, a new one gets its streams from [resolve] (the catalog), one that cannot be
 * found is left out (the list shrinks, never breaks).
 */
object Edit {
    const val MAX_GROUPS = 40
    const val MAX_CHANNELS = 600                                      // per list: the lists are read whole on each draw
    const val MAX_NAME = 40
    private val ID = Regex("[a-z0-9]{1,12}")

    class GroupPlan(val id: String, val name: String, val ids: List<String>)
    class Plan(val favs: List<String>, val groups: List<GroupPlan>)

    /** Null fields are absent in the request (kept as they are); a bad group (no name) is dropped, a bad id is made anew. */
    fun clean(req: JSONObject, newId: () -> String): Pair<List<String>?, List<GroupPlan>?> {
        val favs = req.optJSONArray("favs")?.let { ids(it) }
        val groups = req.optJSONArray("groups")?.let { a ->
            val seen = HashSet<String>()
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { o ->
                val name = o.optString("name").trim().take(MAX_NAME)
                if (name.isEmpty()) return@mapNotNull null
                var id = o.optString("id")
                if (!ID.matches(id) || id == Store.FAV || !seen.add(id)) { id = newId(); while (!seen.add(id)) id = newId() }
                GroupPlan(id, name, ids(o.optJSONArray("ch") ?: JSONArray()))
            }.take(MAX_GROUPS)
        }
        return favs to groups
    }

    private fun ids(a: JSONArray) = (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() && it.length <= 200 }.distinct().take(MAX_CHANNELS)

    /** The streams of each id in order: [have] first (what is kept now), else [resolve]. */
    fun channels(ids: List<String>, have: Map<String, List<Channel>>, resolve: (String) -> List<Channel>): List<Channel> =
        ids.flatMap { id -> have[id] ?: resolve(id) }

    /** A channel for the page: id, name, logo, group; [url]: its first stream too (the export file carries it: another TV may not know the channel). */
    fun brief(ch: Channel, url: Boolean = false) = JSONObject().put("id", ch.id).put("n", ch.name).put("l", ch.logo).put("g", ch.group)
        .also { if (url && ch.url.startsWith("http")) it.put("u", ch.url) }

    // ---------------------------------------------------------------- import: a file made by "Export", on this or another TV

    const val MAX_IMPORT = 3000                                       // channels in one file, all lists together
    class Entry(val id: String, val name: String, val country: String, val url: String)
    class Imp(val favs: List<Entry>, val groups: List<Pair<String, List<Entry>>>, val dropped: Int)
    class G(val id: String, val name: String, val ch: List<Channel>)
    class Result(val favs: List<Channel>, val groups: List<G>, val groupsTouched: Int, val added: Int, val missing: List<String>, val skippedGroups: Int)

    private fun entry(o: JSONObject?): Entry? {
        o ?: return null
        val id = o.optString("id").trim().take(200)
        val name = o.optString("name").trim().take(120)
        if (id.isEmpty() && name.isEmpty()) return null
        val cc = o.optString("country").lowercase().takeIf { it.length == 2 && it.all { c -> c in 'a'..'z' } } ?: ""
        val url = o.optString("url").takeIf { (it.startsWith("http://") || it.startsWith("https://")) && it.length <= 500 && !it.any { c -> c.isWhitespace() } } ?: ""
        return Entry(id, name, cc, url)
    }
    private fun entries(a: JSONArray?, left: IntArray): List<Entry> {
        a ?: return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<Entry>()
        for (i in 0 until a.length()) {
            if (out.size >= MAX_CHANNELS || left[0] <= 0) break
            val e = entry(a.optJSONObject(i)) ?: continue
            if (!seen.add(e.id.ifEmpty { "n:" + e.name })) continue
            out.add(e); left[0]--
        }
        return out
    }

    /** A file's content cleaned: {favourites: [entry], groups: [{name, channels: [entry]}]} with what is beyond the caps left out (counted). Throws on a file that is not one. */
    fun parseImport(doc: JSONObject): Imp {
        if (doc.has("kind") && doc.optString("kind") != "tvchannels") throw IllegalArgumentException("not a TV Channels file")
        if (!doc.has("favourites") && !doc.has("groups")) throw IllegalArgumentException("not a TV Channels file")
        val left = intArrayOf(MAX_IMPORT)
        val favs = entries(doc.optJSONArray("favourites"), left)
        val ga = doc.optJSONArray("groups")
        val groups = ArrayList<Pair<String, List<Entry>>>()
        var dropped = 0
        if (ga != null) for (i in 0 until ga.length()) {
            val o = ga.optJSONObject(i) ?: continue
            val name = o.optString("name").trim().take(MAX_NAME)
            if (name.isEmpty()) { dropped++; continue }
            groups.add(name to entries(o.optJSONArray("channels"), left))
        }
        if (favs.isEmpty() && groups.all { it.second.isEmpty() } && groups.isEmpty()) throw IllegalArgumentException("nothing in the file")
        return Imp(favs, groups, dropped)
    }

    fun fold(s: String) = java.text.Normalizer.normalize(s.lowercase().replace('đ', 'd'), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()

    /**
     * [imp] put into what is kept: [replace] = false adds the groups and channels not there yet (a group of the same name, any
     * case, takes the missing channels; the rest stays), true makes the file the whole state. [find] gives an entry's streams:
     * by id, else by name + country, else its own url (null / empty: not found, listed by name).
     */
    fun importInto(favs: List<Channel>, groups: List<G>, imp: Imp, replace: Boolean, newId: () -> String, find: (Entry) -> List<Channel>): Result {
        val missing = ArrayList<String>()
        var added = 0
        fun resolve(l: List<Entry>, into: MutableList<Channel>) {
            val ids = into.map { it.id }.toHashSet()
            for (e in l) {
                val got = find(e)
                if (got.isEmpty()) { missing.add(e.name.ifEmpty { e.id }); continue }
                if (got.first().id in ids) continue
                ids.add(got.first().id); into.addAll(got); added++
            }
        }
        val f = if (replace) ArrayList() else ArrayList(favs)
        resolve(imp.favs, f)
        val out = if (replace) ArrayList<G>() else ArrayList(groups)
        val chans = HashMap<String, MutableList<Channel>>()
        var touched = 0; var skipped = 0
        val used = HashSet(out.map { it.id })
        for ((name, es) in imp.groups) {
            var at = out.indexOfFirst { fold(it.name) == fold(name) }
            if (at < 0) {
                if (out.size >= MAX_GROUPS) { skipped++; continue }
                var id = newId(); while (!used.add(id)) id = newId()
                out.add(G(id, name, emptyList())); at = out.size - 1
            }
            val l = ArrayList(out[at].ch)
            val before = added
            resolve(es, l)
            if (added > before || es.isEmpty()) touched++
            out[at] = G(out[at].id, out[at].name, l.take(MAX_CHANNELS * 4))
        }
        return Result(f, out, touched, added, missing, skipped)
    }

    private fun entryJson(ch: Channel) = JSONObject().put("id", ch.id).put("name", ch.name).put("country", Ids.country(ch.id)).put("url", ch.url)
    private fun entryList(l: List<Channel>) = JSONArray().apply { l.byChannel().forEach { put(entryJson(it.first())) } }

    /** The file "Export" saves: what [parseImport] reads back, on this or another TV. */
    fun export(favs: List<Channel>, groups: List<G>, now: String) = JSONObject().put("app", "tv-remote").put("kind", "tvchannels").put("version", 1)
        .put("date", now).put("favourites", entryList(favs))
        .put("groups", JSONArray().apply { groups.forEach { put(JSONObject().put("name", it.name).put("channels", entryList(it.ch))) } })
}
