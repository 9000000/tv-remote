package dev.tvremote.channels

import org.json.JSONArray
import org.json.JSONObject

/**
 * The live tile TV Home draws for this app (HomeInfo answers it): "Keep watching", layout "next". The same JSON shape as TV Setup's
 * tiles (id, title, big, small, mode, icon, tone, accent) plus what the "next" layout reads: line2 (the next programme), logo (the
 * channel's picture, an address) and "open" (the link a press follows: TV Home starts it itself, because it is the app in front).
 * Pure: the words and the guide come in as arguments, so it is tested without a TV.
 */
object ChannelTile {
    const val NEXT = "next"

    /** The words of the tile in the TV's language (strings.xml). */
    class Words(val title: String, val empty: String, val emptyLine: String, val play: String, val next: (String) -> String)

    fun list(w: Words): JSONArray = JSONArray().put(JSONObject().put("id", NEXT).put("title", w.title).put("icon", "tv").put("kind", "next").put("layout", "next"))

    /** [Pins.link] as text, without android.net.Uri (it is only a stub in unit tests). */
    fun link(id: String) = "tvchannels://play/" + java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20")

    /** The state of the tile: the last channel watched, what is on now and next when the guide knows, else an invitation; nothing watched yet: an empty state that opens the list. */
    fun state(w: Words, ch: Channel?, now: Show?, then: Show?, hm: (Long) -> String, clock: Long = System.currentTimeMillis()): JSONObject {
        val o = JSONObject().put("id", NEXT).put("title", w.title).put("layout", "next").put("mode", "live").put("icon", "tv").put("accent", "ok").put("tone", "watch")
        if (ch == null) return o.put("empty", true).put("big", w.empty).put("small", w.emptyLine).put("open", "tvchannels://find")
        o.put("big", ch.name).put("open", link(ch.id))
        if (ch.logo.startsWith("http")) o.put("logo", ch.logo)
        // TV Home draws the programme's name and a thin bar for how far it is (3 m away a time text is noise); the "next" line stays in the state for the clients that show it
        if (now != null && now.end > now.start) o.put("progress", ((clock - now.start).toDouble() / (now.end - now.start)).coerceIn(0.0, 1.0))
        o.put("small", if (now == null) w.play else if (o.has("progress")) now.title else "${hm(now.start)}  ${now.title}")
        if (then != null) o.put("line2", w.next("${hm(then.start)}  ${then.title}"))
        return o
    }
}
