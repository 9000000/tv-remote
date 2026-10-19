package dev.tvremote.desktop

import dev.tvremote.core.FleetStatus
import dev.tvremote.core.picture.Thumb
import dev.tvremote.core.remote.Remote
import dev.tvremote.core.web.HttpServer.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * The list of TVs at a glance (`/api/fleet/status`, `/api/fleet/shot`): what each saved TV is doing, and, when the person asks
 * for it, a thumbnail of its screen. Reads on the links [Fleets] keeps per TV, so no picture session is started for a status;
 * readings and thumbnails are kept a few seconds, so many pages (or one page twice) cost the TVs one reading.
 */
class FleetView(private val fleets: Fleets, private val saved: Saved) {
    private val board = FleetStatus.Board({ host -> readOne(host) })
    private val shots = ConcurrentHashMap<String, Pair<Long, ByteArray?>>()
    private val shooting = ConcurrentHashMap.newKeySet<String>()
    private val gate = Semaphore(2)                                   // a screenshot costs the TV a PNG each: two TVs at a time

    private fun readOne(host: String): FleetStatus.Status {
        val tv = saved.find(host) ?: return FleetStatus.Status(host)
        val kind = Remote.Kind.of(tv.optString("kind"))
        val link = if (kind == Remote.Kind.ANDROID) fleets.link(host) else null
        return FleetStatus.read(host, kind, { FleetStatus.reach(host, it) }, link)
    }

    /** Every saved TV's reading; the last known one for a TV still being asked after [WAIT_MS]. */
    fun status(): Response {
        val tvs = saved.tvs.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("host") } }.filter { it.isNotEmpty() }
        val rows = board.round(tvs, WAIT_MS)
        val now = System.currentTimeMillis()
        return Response.json(JSONObject().put("tvs", JSONArray(rows.map { it.json(now) })))
    }

    /** A TV's screen, small, as a PNG; 204 when there is none now (not an ADB TV, screen off, busy). Saved TVs only. */
    fun shot(host: String): Response {
        val tv = saved.find(host)
        if (tv == null || Remote.Kind.of(tv.optString("kind")) != Remote.Kind.ANDROID) return Response.error(404, "no such TV")
        val now = System.currentTimeMillis()
        shots[host]?.let { (at, png) -> if (now - at < FRESH_MS) return png(png) }
        if (!shooting.add(host)) return png(shots[host]?.second)         // one already being taken: this page shows the last one
        var got = false
        try {
            gate.acquire(); got = true
            val small = try { Thumb.screen(fleets.link(host).get())?.let { Thumb.small(it) } } catch (e: Exception) { null }
            shots[host] = System.currentTimeMillis() to small
            return png(small)
        } finally {
            if (got) gate.release()
            shooting.remove(host)
        }
    }

    private fun png(b: ByteArray?): Response = if (b == null) Response().apply { status = 204 }
        else Response.of("image/png", b).also { it.headers["Cache-Control"] = "no-store" }

    fun forget(host: String) { board.forget(host); shots.remove(host) }
    fun close() = board.close()

    companion object {
        const val WAIT_MS = 9000L                                     // a TV that is off answers in ~1.5 s; this is for one that hangs
        const val FRESH_MS = 3000L
    }
}
