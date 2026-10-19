package dev.tvremote.core

import dev.tvremote.core.picture.Tv
import org.junit.Assert.assertEquals
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
}
