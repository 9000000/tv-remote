package dev.tvremote.core

import dev.tvremote.core.picture.Tv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The frames a second asked of the TV: Auto's per level, the person's own, and the software encoder's lower cap. */
class PictureFpsTest {
    @Test fun autoPerLevel() {
        assertEquals(15, Tv.fpsFor(0, 0, soft = false))               // 480
        assertEquals(20, Tv.fpsFor(1, 0, soft = false))               // 720
        assertEquals(20, Tv.fpsFor(2, 0, soft = false))               // 960
        assertEquals(15, Tv.fpsFor(4, 0, soft = false))               // 240
        assertEquals(30, Tv.fpsFor(5, 0, soft = false))               // the computer's sharpest
    }

    @Test fun softwareEncoderKeepsTheOldRate() {
        assertEquals(Tv.SOFT_FPS, Tv.fpsFor(1, 0, soft = true))
        assertEquals(Tv.SOFT_FPS, Tv.fpsFor(5, 0, soft = true))
    }

    @Test fun chosenByHandWins() {
        assertEquals(60, Tv.fpsFor(1, 60, soft = true))               // picked: the person knows their TV
        assertEquals(60, Tv.fpsFor(1, 500, soft = false))
        assertEquals(30, Tv.fpsFor(99, 0, soft = false))              // a saved level out of range: the last one, as before
    }

    @Test fun aTvWithLittleMemoryIsHeldToTheLowLevelUnlessPickedByHand() {
        assertTrue(Tv.lowRam(1_000_000L)); assertFalse(Tv.lowRam(1_572_864L)); assertFalse(Tv.lowRam(null)); assertFalse(Tv.lowRam(0L))
        assertEquals("video_bit_rate=2000000 max_size=960", Tv.videoOpts(2, 4096, 0, false))
        assertEquals("video_bit_rate=1000000 max_size=704", Tv.videoOpts(2, 4096, 0, true))     // 960 level -> 720 px (704, a multiple of 32), 1 Mbit/s
        assertEquals("video_bit_rate=300000 max_size=480", Tv.videoOpts(0, 4096, 0, true))      // a lower level stays
        assertEquals("video_bit_rate=4000000 max_size=704", Tv.videoOpts(2, 4096, 4_000_000, true))   // a rate set in Advanced is the person's
        assertEquals(20, Tv.fpsFor(5, 0, soft = false, capped = true))                          // the sharpest level's 30 -> 20
        assertEquals(30, Tv.fpsFor(5, 0, soft = false))
        assertEquals(30, Tv.fpsFor(2, 30, soft = false, capped = true))                         // frames asked by hand
    }
}
