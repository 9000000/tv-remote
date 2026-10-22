package dev.tvremote.games

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/**
 * What a person keeps in the library besides the games: favourites and their own folders (groups), by game id
 * ("g:<file name>" a ROM, "w:<url>" a web game). [Data] is plain values with pure changes, so the rules are unit tested; [Shelf] keeps it
 * in the app's prefs. A game that is gone is simply not drawn: its id stays until it is taken out, so it comes back when the file does.
 */
object Shelf {
    class Group(val id: String, val name: String, val items: List<String>)
    class Data(val fav: List<String> = emptyList(), val groups: List<Group> = emptyList()) {
        fun toggleFav(id: String) = Data(if (id in fav) fav - id else fav + id, groups)
        fun addGroup(g: Group) = Data(fav, groups + g)
        fun rename(id: String, name: String) = Data(fav, groups.map { if (it.id == id) Group(it.id, name, it.items) else it })
        fun delete(id: String) = Data(fav, groups.filter { it.id != id })
        fun toggleIn(gid: String, item: String) = Data(fav, groups.map {
            if (it.id != gid) it else Group(it.id, it.name, if (item in it.items) it.items - item else it.items + item)
        })
        fun inGroup(gid: String, item: String) = groups.firstOrNull { it.id == gid }?.items?.contains(item) == true
        /** "Nhóm 1", "Nhóm 2"...: the first number not taken by a group of that name. */
        fun freeName(base: String): String { var n = groups.size + 1; while (groups.any { it.name == "$base $n" }) n++; return "$base $n" }
    }

    fun toJson(d: Data): String = JSONObject().put("fav", JSONArray(d.fav)).put("groups", JSONArray().apply {
        d.groups.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("items", JSONArray(it.items))) }
    }).toString()

    fun parse(s: String?): Data = try {
        val o = JSONObject(s ?: "{}")
        fun list(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }.distinct()
        Data(list(o.optJSONArray("fav")), (0 until (o.optJSONArray("groups")?.length() ?: 0)).mapNotNull { i ->
            o.getJSONArray("groups").optJSONObject(i)?.let { g -> Group(g.getString("id"), g.optString("name").ifBlank { "?" }, list(g.optJSONArray("items"))) }
        })
    } catch (e: Exception) { Data() }

    private fun prefs(c: Context) = c.getSharedPreferences("shelf", Context.MODE_PRIVATE)
    fun get(c: Context): Data = parse(prefs(c).getString("data", null))
    fun set(c: Context, d: Data) { prefs(c).edit().putString("data", toJson(d)).apply() }
    fun change(c: Context, f: (Data) -> Data) = set(c, f(get(c)))

    fun newGroup(c: Context, name: String): Group {
        val g = Group("f" + java.lang.Long.toString(System.nanoTime() % 2176782336L, 36), name.trim().take(40), emptyList())
        change(c) { it.addGroup(g) }; return g
    }
}

/** Search and the filters, as pure rules. */
object Browse {
    /** Lower case, no accents ("Đố vui" = "do vui"): a TV keyboard has few accents. */
    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('đ', 'd')

    fun nameMatches(name: String, q: String): Boolean = q.isBlank() || norm(name).contains(norm(q.trim()))

    /** 1 = plays alone, 2 = plays with two, 3 = three or four. [max] 0 = not known: matches none of them. */
    fun playersOk(f: Int?, max: Int, min: Int): Boolean = when (f) {
        null -> true
        1 -> max > 0 && min <= 1
        2 -> max >= 2 && min <= 2
        3 -> max >= 3
        else -> true
    }

    private val GENRES = mapOf(
        "action" to ("Hành động" to "Action"), "sports" to ("Thể thao" to "Sports"), "sport" to ("Thể thao" to "Sports"),
        "quiz" to ("Đố vui" to "Quiz"), "racing" to ("Đua xe" to "Racing"), "shooter" to ("Bắn súng" to "Shooter"),
        "puzzle" to ("Giải đố" to "Puzzle"), "platformer" to ("Đi cảnh" to "Platformer"), "party" to ("Chơi nhóm" to "Party"),
        "arcade" to ("Arcade" to "Arcade"), "adventure" to ("Phiêu lưu" to "Adventure"), "rpg" to ("Nhập vai" to "RPG"),
        "strategy" to ("Chiến thuật" to "Strategy"), "fighting" to ("Đối kháng" to "Fighting"), "music" to ("Âm nhạc" to "Music"),
    )

    /** A genre's id ("sports") from the words [genre] gave, in either language; null for one the catalog made up. */
    fun genreId(words: String): String? = GENRES.entries.firstOrNull { (_, w) -> words.equals(w.first, true) || words.equals(w.second, true) }?.key

    /** The catalog's "genre": a word (an id like "sports" or the words themselves), {"vi":..,"en":..}, or a list of either (the first). null = none. */
    fun genre(v: Any?, en: Boolean): String? {
        val raw = when (v) {
            is String -> v
            is JSONObject -> v.optString(if (en) "en" else "vi").ifEmpty { v.optString("en") }.ifEmpty { v.optString("vi") }
            is JSONArray -> if (v.length() > 0) return genre(v.opt(0), en) else ""
            else -> ""
        }.trim()
        if (raw.isEmpty()) return null
        return GENRES[raw.lowercase()]?.let { if (en) it.second else it.first } ?: raw
    }
}
