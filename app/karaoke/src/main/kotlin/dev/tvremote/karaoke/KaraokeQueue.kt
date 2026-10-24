package dev.tvremote.karaoke

import org.json.JSONArray
import org.json.JSONObject

/** A video from a search: only ids, titles and thumbnails are ever kept (nothing of the video itself). */
data class Song(val vid: String, val title: String, val thumb: String, val channel: String = "", val length: String = "")

/** A song in the queue: [by] the name shown, [cid] the phone that added it (only that phone may reorder / remove it). */
data class Entry(val qid: Int, val song: Song, val by: String, val cid: String)

/**
 * The shared queue, with no Android in it (unit-tested): [now] is the song on the TV, [upcoming] the rest in order, [history] the last
 * songs sung. Every method is called under the session's lock. A phone may move / remove only its own songs; the TV ([force]) any.
 */
class KaraokeQueue {
    var now: Entry? = null; private set
    val upcoming = ArrayList<Entry>()
    val history = ArrayList<Entry>()
    private var nextId = 1
    /** Goes up when the song on the TV must start again ("hát lại"): the player compares it with the one it started. */
    var replayTick = 0; private set

    fun add(song: Song, by: String, cid: String): Entry? {
        if (upcoming.size >= MAX_QUEUE) return null
        val e = Entry(nextId++, song, cleanName(by), cid)
        upcoming.add(e)
        if (now == null) advance()
        return e
    }

    fun remove(qid: Int, cid: String, force: Boolean = false): Boolean {
        val i = upcoming.indexOfFirst { it.qid == qid }
        if (i < 0 || (!force && upcoming[i].cid != cid)) return false
        upcoming.removeAt(i); return true
    }

    /** One step earlier (-1) or later (+1) among the upcoming songs. */
    fun move(qid: Int, dir: Int, cid: String, force: Boolean = false): Boolean {
        val i = upcoming.indexOfFirst { it.qid == qid }
        val j = i + dir.coerceIn(-1, 1)
        if (i < 0 || j < 0 || j >= upcoming.size || (!force && upcoming[i].cid != cid)) return false
        upcoming.add(j, upcoming.removeAt(i)); return true
    }

    /** The song on the TV is over (or skipped): it goes to the history, the next one takes its place. Returns the new [now]. */
    fun advance(): Entry? {
        now?.let { history.add(it); while (history.size > MAX_HISTORY) history.removeAt(0) }
        now = if (upcoming.isEmpty()) null else upcoming.removeAt(0)
        return now
    }

    /** Sing the song on the TV again from the start. False when nothing is on. */
    fun replay(): Boolean { if (now == null) return false; replayTick++; return true }

    /** Puts the song sung last back on the TV ahead of the rest, e.g. "hát lại" after it ended and the queue is empty. */
    fun sing(e: Entry) { if (now == null) now = e else upcoming.add(0, e) }

    fun json(): JSONObject = JSONObject().put("now", now?.let { entry(it) } ?: JSONObject.NULL)
        .put("queue", JSONArray().also { a -> upcoming.forEach { a.put(entry(it)) } })
        .put("history", JSONArray().also { a -> history.takeLast(10).reversed().forEach { a.put(entry(it)) } })

    fun save(): String = JSONObject().put("now", now?.let { entry(it) } ?: JSONObject.NULL)
        .put("queue", JSONArray().also { a -> upcoming.forEach { a.put(entry(it)) } })
        .put("history", JSONArray().also { a -> history.forEach { a.put(entry(it)) } }).toString()

    /** Back after a restart: the song that was on goes first in line, nothing starts by itself. */
    fun load(s: String) {
        try {
            val o = JSONObject(s)
            upcoming.clear(); history.clear(); now = null
            o.optJSONObject("now")?.let { parse(it)?.let { e -> upcoming.add(e) } }
            o.optJSONArray("queue")?.let { a -> for (i in 0 until a.length()) parse(a.getJSONObject(i))?.let { upcoming.add(it) } }
            o.optJSONArray("history")?.let { a -> for (i in 0 until a.length()) parse(a.getJSONObject(i))?.let { history.add(it) } }
            nextId = (upcoming + history).maxOfOrNull { it.qid }?.plus(1) ?: 1
        } catch (e: Exception) { upcoming.clear(); history.clear(); now = null }
    }

    private fun entry(e: Entry) = JSONObject().put("qid", e.qid).put("vid", e.song.vid).put("title", e.song.title)
        .put("thumb", e.song.thumb).put("channel", e.song.channel).put("len", e.song.length).put("by", e.by).put("cid", e.cid)

    private fun parse(o: JSONObject): Entry? {
        val vid = o.optString("vid"); if (!validId(vid)) return null
        return Entry(o.optInt("qid"), Song(vid, o.optString("title").take(200), o.optString("thumb"), o.optString("channel").take(80), o.optString("len").take(12)),
            o.optString("by").take(20), o.optString("cid").take(40))
    }

    companion object {
        const val MAX_QUEUE = 100
        const val MAX_HISTORY = 50
        private val ID = Regex("^[A-Za-z0-9_-]{11}$")
        /** A YouTube video id: 11 characters. Checked before it is ever put in the player's page. */
        fun validId(v: String) = ID.matches(v)
        fun cleanName(s: String): String = s.replace(Regex("[\\p{Cntrl}<>]"), "").trim().take(16).ifEmpty { "?" }
    }
}
