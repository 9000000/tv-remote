package dev.tvremote.core

import dev.tvremote.core.picture.Hush
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HushTest {
    // 10 ms of 48 kHz stereo 16-bit, as scrcpy hands it over: 1920 bytes
    private fun piece(level: Int) = ByteArray(1920).also { b -> for (i in b.indices step 2) { val s = if ((i / 2) % 2 == 0) level else -level; b[i] = s.toByte(); b[i + 1] = (s shr 8).toByte() } }

    @Test fun silenceGoesOutUntilHeldThenOnlyOnce() {
        val h = Hush()
        val said = (1..60).map { h.feed(piece(0)) }                  // 600 ms of zeros
        assertEquals(List(29) { Hush.SEND }, said.take(29))          // a pause inside a song is still sent as it is
        assertEquals(Hush.START, said[29])                           // 300 ms in: the page hears once that the sound paused
        assertTrue(said.drop(30).all { it == Hush.HELD })
        assertTrue(h.held)
    }

    @Test fun soundComesBackAtOnce() {
        val h = Hush()
        repeat(40) { h.feed(piece(0)) }
        assertEquals(Hush.SEND, h.feed(piece(1000)))
        assertFalse(h.held)
        assertEquals(Hush.SEND, h.feed(piece(0)))                     // counting starts again
    }

    @Test fun ditherIsSilenceAQuietSoundIsNot() {
        assertTrue(Hush.silent(piece(3)))
        assertFalse(Hush.silent(piece(40)))                          // -58 dB: a whisper, still sound
        assertFalse(Hush.silent(piece(-200)))
    }
}
