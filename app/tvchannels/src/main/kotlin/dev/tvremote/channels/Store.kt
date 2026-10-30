package dev.tvremote.channels

import android.content.Context
import dev.tvremote.guard.Age
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the app keeps: the country on show, the person's groups of channels (whole channels, so one from another country still
 * plays with no list of it read; the favourites are the group [FAV], kept where they always were), the last channel watched,
 * and which streams answered lately ([Probe]).
 */
object Store {
    private fun p(c: Context): android.content.SharedPreferences {
        val s = c.getSharedPreferences("channels", Context.MODE_PRIVATE)
        if (!pruned) synchronized(okLock) {                           // once per process: one key per stream URL piled up for ever
            if (!pruned) { pruned = true; val old = staleOk(s.all, System.currentTimeMillis()); if (old.isNotEmpty()) s.edit().apply { old.forEach { remove(it) } }.apply() }   // read and removed in one edit under the lock [setAlive] takes: a fresh mark written meanwhile is not in it
        }
        return s
    }
    @Volatile private var pruned = false
    private val okLock = Any()

    /**
     * The "ok:<url>" keys whose answer is over 6 hours old: [alive] ignores them already, so they are only weight in the prefs file.
     * None when any stamp is ahead of [now] (a box booted with its clock a year ahead): which marks are old cannot be told until
     * the clock is right, and [setAlive] overwrites an ahead stamp when its stream is checked again.
     */
    internal fun staleOk(all: Map<String, *>, now: Long): List<String> {
        val oks = all.filter { (k, _) -> k.startsWith("ok:") }
        if (oks.any { (_, v) -> v is Long && Math.abs(v) > now }) return emptyList()
        return oks.filter { (_, v) -> v !is Long || !Age.fresh(Math.abs(v), OK_MS, now) }.keys.toList()
    }
    private const val OK_MS = 6 * 3600_000L

    /** Whether [setAlive] has to write: not when the stream already stands as answered (a card re-bound or a stream restarted writes it again and again). */
    internal fun needsAliveWrite(prev: Long, ok: Boolean, now: Long) = !(ok && prev > 0 && Age.fresh(prev, OK_MS, now))

    /** The ids of the favourites, parsed once per change of the stored string (a card bound asks [isFavourite] for each channel). */
    internal class IdCache(private val parse: (String) -> Set<String>) {
        @Volatile private var last: Pair<String, Set<String>>? = null   // one object: a reader never pairs a string with another string's ids
        @Volatile var parses = 0
        fun ids(now: String): Set<String> {
            last?.let { if (it.first == now) return it.second }
            parses++; val v = parse(now); last = now to v; return v
        }
    }
    private val favIds = IdCache { raw -> favouritesOf(raw).map { it.id }.toHashSet() }

    /** [f] runs when the favourites or groups change (ChannelsReceiver wrote them while the list is open); keep the returned listener until [unwatch]. */
    fun watch(c: Context, f: () -> Unit) = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, k -> if (k == "favs" || k == "groups" || k == "lists") f() }
        .also { p(c).registerOnSharedPreferenceChangeListener(it) }
    fun unwatch(c: Context, l: android.content.SharedPreferences.OnSharedPreferenceChangeListener) = p(c).unregisterOnSharedPreferenceChangeListener(l)

    fun country(c: Context): String = p(c).getString("country", null) ?: Catalog.home()
    fun setCountry(c: Context, cc: String) = p(c).edit().putString("country", cc).apply()

    private fun favouritesOf(raw: String?): List<Channel> {
        val a = try { JSONArray(raw ?: "[]") } catch (e: Exception) { JSONArray() }
        return (0 until a.length()).mapNotNull { read(a.optJSONObject(it)) }
    }
    fun favourites(c: Context): List<Channel> = favouritesOf(p(c).getString("favs", "[]"))
    fun isFavourite(c: Context, id: String) = id in favIds.ids(p(c).getString("favs", "[]") ?: "[]")
    /** Adds [ch] (all its streams) or takes it away; true when it is a favourite now. */
    fun toggleFavourite(c: Context, ch: List<Channel>): Boolean {
        val id = ch.first().id
        val now = favourites(c).filter { it.id != id }
        val on = now.size == favourites(c).size
        val out = JSONArray()
        (if (on) now + ch else now).forEach { out.put(write(it)) }
        p(c).edit().putString("favs", out.toString()).apply()
        HomeRows.favourites(c)
        return on
    }

    /** A group the person made: a name and its channels, in the order they were added. */
    class Group(val id: String, val name: String, val channels: List<Channel>)

    const val FAV = "fav"

    /** The person's own groups, in the order they were made (the favourites are not one of them: [FAV]). */
    fun groups(c: Context): List<Group> {
        val a = try { JSONArray(p(c).getString("groups", "[]")) } catch (e: Exception) { JSONArray() }
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { o ->
            val ch = o.optJSONArray("ch") ?: JSONArray()
            Group(o.optString("id"), o.optString("name"), (0 until ch.length()).mapNotNull { read(ch.optJSONObject(it)) })
        }.filter { it.id.isNotEmpty() }
    }
    private fun saveGroups(c: Context, l: List<Group>) {
        val a = JSONArray()
        l.forEach { g -> a.put(JSONObject().put("id", g.id).put("name", g.name).put("ch", JSONArray().apply { g.channels.forEach { put(write(it)) } })) }
        p(c).edit().putString("groups", a.toString()).apply()
    }
    /** The favourites and the groups set at once (ChannelsReceiver: a phone or computer edited them). */
    fun replace(c: Context, favs: List<Channel>, groups: List<Group>) {
        p(c).edit().putString("favs", JSONArray().apply { favs.forEach { put(write(it)) } }.toString()).apply()
        saveGroups(c, groups)
        HomeRows.favourites(c)
    }
    fun group(c: Context, id: String): Group? = groups(c).firstOrNull { it.id == id }

    /** The channels of [gid]: the favourites or one of the groups. */
    fun channels(c: Context, gid: String): List<Channel> = if (gid == FAV) favourites(c) else group(c, gid)?.channels.orEmpty()
    fun has(c: Context, gid: String, id: String) = channels(c, gid).any { it.id == id }
    /** Adds [ch] (all its streams) to [gid] or takes it out; true when it is in now. */
    fun toggle(c: Context, gid: String, ch: List<Channel>): Boolean {
        if (gid == FAV) return toggleFavourite(c, ch)
        val id = ch.first().id
        var on = false
        saveGroups(c, groups(c).map { g ->
            if (g.id != gid) g else {
                val rest = g.channels.filter { it.id != id }
                on = rest.size == g.channels.size
                Group(g.id, g.name, if (on) rest + ch else rest)
            }
        })
        return on
    }
    /** A new empty group; its id is short and never reused, so a link pinned to a deleted group finds nothing. */
    fun addGroup(c: Context, name: String): String {
        val id = java.lang.Long.toString(System.currentTimeMillis(), 36)
        saveGroups(c, groups(c) + Group(id, name.trim(), emptyList()))
        return id
    }
    fun renameGroup(c: Context, id: String, name: String) = saveGroups(c, groups(c).map { if (it.id == id) Group(id, name.trim(), it.channels) else it })
    fun deleteGroup(c: Context, id: String) = saveGroups(c, groups(c).filter { it.id != id })

    /** A playlist of the person's own (an m3u link, see [Playlists]); [id] comes from the link, so a cached copy is found again. */
    class Playlist(val id: String, val name: String, val url: String)

    fun playlists(c: Context): List<Playlist> {
        val a = try { JSONArray(p(c).getString("lists", "[]")) } catch (e: Exception) { JSONArray() }
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { Playlist(it.optString("id"), it.optString("name"), it.optString("url")) }
            .filter { it.id.isNotEmpty() && Edit.validUrl(it.url) }
    }
    fun playlist(c: Context, id: String) = playlists(c).firstOrNull { it.id == id }
    fun setPlaylists(c: Context, l: List<Playlist>) {
        val gone = playlists(c).filter { o -> l.none { it.id == o.id } }
        p(c).edit().putString("lists", JSONArray().apply { l.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url)) } }.toString()).apply()
        gone.forEach { Playlists.forget(c, it) }                      // a removed link's copy (it may hold a token) goes with it
    }

    /** A small string of a feature that keeps its own format (Reminders). */
    fun raw(c: Context, k: String): String? = p(c).getString(k, null)
    fun putRaw(c: Context, k: String, v: String) = p(c).edit().putString(k, v).apply()

    fun last(c: Context): String? = p(c).getString("last", null)
    fun setLast(c: Context, id: String) = p(c).edit().putString("last", id).apply()
    /** The last channel watched whole (name, logo, streams), for TV Home's "Keep watching" live widget, which needs no list read to show it. */
    fun setLastChannel(c: Context, ch: Channel) { p(c).edit().putString("last_ch", write(ch).toString()).apply(); publishLast(c, ch); HomeRows.watched(c, ch) }

    /** The dynamic launcher shortcut `last` (TV Home's picker lists it): the channel just watched, so it follows what is playing. */
    private fun publishLast(c: Context, ch: Channel) {
        if (android.os.Build.VERSION.SDK_INT < 25) return          // no ShortcutManager before 7.1
        try {
            val i = android.content.Intent(android.content.Intent.ACTION_VIEW, Pins.link(ch.id)).setClass(c, PlayActivity::class.java)
            val s = android.content.pm.ShortcutInfo.Builder(c, "last").setShortLabel(c.getString(R.string.sc_last_short)).setLongLabel(ch.name.ifEmpty { c.getString(R.string.sc_last_short) })
                .setIcon(android.graphics.drawable.Icon.createWithResource(c, R.drawable.icon)).setIntent(i).build()
            c.getSystemService(android.content.pm.ShortcutManager::class.java)?.setDynamicShortcuts(listOf(s))
        } catch (e: Exception) { }                                   // rate-limited or no launcher: the list is only a convenience
    }
    fun lastChannel(c: Context): Channel? = try { p(c).getString("last_ch", null)?.let { read(JSONObject(it)) } } catch (e: Exception) { null }

    /** The stream's last check: true answered, false did not, null not checked in the last 6 hours. */
    fun alive(c: Context, url: String): Boolean? {
        val v = p(c).getLong("ok:$url", 0L)
        if (v == 0L || !Age.fresh(Math.abs(v), OK_MS)) return null
        return v > 0
    }
    fun setAlive(c: Context, url: String, ok: Boolean) {
        val t = System.currentTimeMillis()
        val s = p(c)
        synchronized(okLock) {
            if (!needsAliveWrite(s.getLong("ok:$url", 0L), ok, t)) return
            s.edit().putLong("ok:$url", if (ok) t else -t).apply()
        }
    }

    private fun write(ch: Channel) = JSONObject().put("id", ch.id).put("name", ch.name).put("logo", ch.logo).put("group", ch.group)
        .put("url", ch.url).put("q", ch.quality).put("geo", ch.geo).put("na", ch.notAlways).put("ref", ch.referrer).put("ua", ch.agent).put("g", ch.guide)

    private fun read(o: JSONObject?): Channel? {
        o ?: return null
        val url = o.optString("url").takeIf { it.startsWith("http") || Providers.isLink(it) } ?: return null   // a provider's "vieon:<slug>" too
        return Channel(o.optString("id"), o.optString("name"), o.optString("logo"), o.optString("group"), url,
            o.optString("q"), o.optBoolean("geo"), o.optBoolean("na"), o.optString("ref"), o.optString("ua"), o.optString("g"))
    }
}

/** A list's channels by id, in the list's order: one entry per channel, its streams (the first, then the ones to try after it). */
fun List<Channel>.byChannel(): List<List<Channel>> = groupBy { it.id }.values.toList()
