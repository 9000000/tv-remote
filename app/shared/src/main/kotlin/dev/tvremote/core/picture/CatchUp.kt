package dev.tvremote.core.picture
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

/**
 * The picture's packets on their way to one browser ([Feeds.video]), kept close to live. A browser that reads slower than the
 * TV sends (a weak Wi-Fi, a relayed Tailscale) used to fill the queue until the picture was many seconds behind the TV, and
 * then the session was stopped. Now once the oldest waiting packet is [lagMs] old, every waiting frame is thrown away and
 * [fresh] asks the TV for a new key frame; the frames that come meanwhile are dropped too (they need the ones thrown away),
 * until the next codec config or key frame. The picture jumps to now instead of lagging. Apart from [Feeds] so it can be
 * tried against a made-up clock (CatchUpTest). [now] is in ms.
 */
class CatchUp(private val lagMs: Long, private val fresh: () -> Unit) {
    class Packet(val kind: Int, val bytes: ByteArray, val at: Long)

    val q = LinkedBlockingQueue<Packet>()
    var catchUps = 0; private set
    var resets = 0; private set
    private var skipping = false
    private var resetAt = -RESET_MS
    private var caughtAt = Long.MIN_VALUE / 2                         // what was kept then is counted as waiting from then on

    /** A packet of [kind] (0 size, 1 codec config, 2 key frame, 3 frame) from the TV; false if it was dropped. */
    fun add(kind: Int, data: ByteArray, now: Long): Boolean {
        val oldest = q.firstOrNull { it.kind in 1..3 }                // not the notices in between: they carry no picture
        if (oldest != null && now - maxOf(oldest.at, caughtAt) >= lagMs) catchUp(now)
        if (skipping && kind == 3) return false
        if (kind == 1 || kind == 2) skipping = false
        q.add(Packet(kind, packet(kind, data), now))
        return true
    }

    /**
     * A recent key frame waiting (the one asked for last time, the browser still stuck): only what is before it goes. Otherwise
     * every frame goes, that key frame too, and a new one is asked for, at most once in [RESET_MS] (each ask restarts the TV's
     * encoder; meanwhile the waiting key frame is kept, however old). The page hears of it only when something went.
     */
    private fun catchUp(now: Long) {
        caughtAt = now
        val key = q.lastOrNull { it.kind == 2 }
        var gone = 0
        if (key != null && (now - key.at < lagMs || now - resetAt < RESET_MS)) {
            val it = q.iterator()
            while (it.hasNext()) { val p = it.next(); if (p === key) break; if (p.kind == 2 || p.kind == 3) { it.remove(); gone++ } }
        } else {
            val it = q.iterator()                                     // not removeIf: API 24, and phones run Android 5
            while (it.hasNext()) { val p = it.next(); if (p.kind == 2 || p.kind == 3) { it.remove(); gone++ } }
            skipping = true
            if (now - resetAt >= RESET_MS) { resetAt = now; resets++; fresh() }
        }
        if (gone == 0) return
        catchUps++
        if (q.none { it.kind == 5 }) q.add(                            // one long stall is one notice, not one per catch-up
Packet(5, packet(5, ByteArray(0)), now))                // the page: "it had to catch up", counted there
    }

    /** A browser joining a picture already running: its frames are of no use before the next codec config or key frame. */
    fun fromKey() { skipping = true }

    /** Anything that is not a picture packet (the end, nothing new): never dropped. */
    fun put(p: Packet) = q.add(p)

    companion object {
        const val RESET_MS = 2000L
        fun packet(kind: Int, data: ByteArray): ByteArray = ByteBuffer.allocate(5 + data.size).put(kind.toByte()).putInt(data.size).put(data).array()
    }
}
