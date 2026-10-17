package dev.tvremote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import dev.tvremote.core.picture.CatchUp

/**
 * CatchUp against a made-up browser: the TV sends a frame every 100 ms (a key frame every 10 s, or soon after it is asked for
 * one), a key frame 25 KB and a frame 3 KB; the browser reads [bytesPerMs] at that moment. Checked: how old a frame is when the browser gets it, and that it
 * never gets a frame whose earlier frames were thrown away (what the decoder would turn into a smeared picture). Like
 * Deskhub's goodput gate, it also fails when frames are thrown away on a browser that keeps up.
 */
class CatchUpTest {
    private class Seen(val maxAgeMs: Long, val shown: Int, val sent: Int, val catchUps: Int, val resets: Int, val broken: Int)

    private fun watch(bytesPerMs: (Long) -> Double, seconds: Int = 60): Seen {
        var now = 0L
        var askedAt = -1L
        val c = CatchUp(600) { askedAt = now }
        var nextFrame = 0L; var frame = 0; var owed = 0.0          // bytes of the packet being read still to come
        var maxAge = 0L; var shown = 0; var broken = 0
        var last = -1; var chainOk = false                            // the browser's view: can it decode what it gets?
        while (now < seconds * 1000L) {
            if (now >= nextFrame) {
                val asked = askedAt >= 0 && now - askedAt >= 150         // the TV's new key frame, a moment after the ask
                if (asked) { askedAt = -1; c.add(1, ByteArray(30), now) }
                val key = asked || frame % 100 == 0
                c.add(if (key) 2 else 3, ByteBuffer.allocate(if (key) 25000 else 3000).putInt(frame).array(), now)
                frame++; nextFrame += 100
            }
            owed -= bytesPerMs(now)
            if (owed <= 0) {
                val p = c.q.poll()
                if (p == null) owed = 0.0 else {
                    owed += p.bytes.size
                    if (p.kind == 2 || p.kind == 3) {
                        val i = ByteBuffer.wrap(p.bytes, 5, 4).int
                        if (p.kind == 2) chainOk = true else if (i != last + 1) chainOk = false
                        if (!chainOk) broken++
                        last = i; shown++
                        maxAge = maxOf(maxAge, now - p.at)
                    }
                }
            }
            now++
        }
        return Seen(maxAge, shown, frame, c.catchUps, c.resets, broken)
    }

    @Test fun keepsUpWithNothingDropped() {
        val s = watch({ 200.0 })                                       // 200 KB/s for the 30 the TV sends
        assertEquals(0, s.catchUps)
        assertEquals(s.sent, s.shown)
        assertEquals(0, s.broken)
    }

    @Test fun aSlowBrowserStaysNearLive() {
        val s = watch({ 25.0 })                                        // 25 KB/s: a little under what the TV sends
        assertTrue("caught up: ${s.catchUps}", s.catchUps > 0)
        assertTrue("oldest frame shown: ${s.maxAgeMs} ms", s.maxAgeMs < 1500)
        assertEquals(0, s.broken)
    }

    @Test fun aStallOfTwoSecondsCostsOneJump() {
        val s = watch({ t -> if (t in 20_000L..22_000L) 0.0 else 200.0 })
        assertEquals("the TV's encoder restarted once", 1, s.resets)
        assertTrue("oldest frame shown: ${s.maxAgeMs} ms", s.maxAgeMs < 2500)
        assertEquals(0, s.broken)
    }

    @Test fun aVerySlowBrowserStillGetsFreshPictures() {
        val s = watch({ 3.0 })                                         // 3 KB/s: a key frame alone takes 8 s
        assertTrue("caught up: ${s.catchUps}", s.catchUps > 2)
        assertTrue("oldest frame shown: ${s.maxAgeMs} ms", s.maxAgeMs < 20000)
        assertEquals(0, s.broken)
    }
}
