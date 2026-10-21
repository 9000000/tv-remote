package dev.tvremote.channels

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the app keeps: the country on show, the favourites (whole channels, so a favourite from another country still plays
 * with no list of it read), the last channel watched, and which streams answered lately ([Probe]).
 */
object Store {
    private fun p(c: Context) = c.getSharedPreferences("channels", Context.MODE_PRIVATE)

    fun country(c: Context): String = p(c).getString("country", null) ?: Catalog.home()
    fun setCountry(c: Context, cc: String) = p(c).edit().putString("country", cc).apply()

    fun favourites(c: Context): List<Channel> {
        val a = try { JSONArray(p(c).getString("favs", "[]")) } catch (e: Exception) { JSONArray() }
        return (0 until a.length()).mapNotNull { read(a.optJSONObject(it)) }
    }
    fun isFavourite(c: Context, id: String) = favourites(c).any { it.id == id }
    /** Adds [ch] (all its streams) or takes it away; true when it is a favourite now. */
    fun toggleFavourite(c: Context, ch: List<Channel>): Boolean {
        val id = ch.first().id
        val now = favourites(c).filter { it.id != id }
        val on = now.size == favourites(c).size
        val out = JSONArray()
        (if (on) now + ch else now).forEach { out.put(write(it)) }
        p(c).edit().putString("favs", out.toString()).apply()
        return on
    }

    fun last(c: Context): String? = p(c).getString("last", null)
    fun setLast(c: Context, id: String) = p(c).edit().putString("last", id).apply()

    /** The stream's last check: true answered, false did not, null not checked in the last 6 hours. */
    fun alive(c: Context, url: String): Boolean? {
        val v = p(c).getLong("ok:$url", 0L)
        if (v == 0L || System.currentTimeMillis() - Math.abs(v) > 6 * 3600_000L) return null
        return v > 0
    }
    fun setAlive(c: Context, url: String, ok: Boolean) {
        val t = System.currentTimeMillis()
        p(c).edit().putLong("ok:$url", if (ok) t else -t).apply()
    }

    private fun write(ch: Channel) = JSONObject().put("id", ch.id).put("name", ch.name).put("logo", ch.logo).put("group", ch.group)
        .put("url", ch.url).put("q", ch.quality).put("geo", ch.geo).put("na", ch.notAlways).put("ref", ch.referrer).put("ua", ch.agent)

    private fun read(o: JSONObject?): Channel? {
        o ?: return null
        val url = o.optString("url").takeIf { it.startsWith("http") } ?: return null
        return Channel(o.optString("id"), o.optString("name"), o.optString("logo"), o.optString("group"), url,
            o.optString("q"), o.optBoolean("geo"), o.optBoolean("na"), o.optString("ref"), o.optString("ua"))
    }
}

/** A list's channels by id, in the list's order: one entry per channel, its streams (the first, then the ones to try after it). */
fun List<Channel>.byChannel(): List<List<Channel>> = groupBy { it.id }.values.toList()
