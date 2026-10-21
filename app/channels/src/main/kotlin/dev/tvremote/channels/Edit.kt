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

    /** A channel for the page: id, name, logo, and the country its id names (the page filters its lists by it). */
    fun brief(ch: Channel) = JSONObject().put("id", ch.id).put("n", ch.name).put("l", ch.logo).put("g", ch.group)
}
