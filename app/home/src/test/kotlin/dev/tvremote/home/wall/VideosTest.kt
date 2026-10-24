package dev.tvremote.home.wall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** When an upload is kept as sent and when it is made light, with the person's frame rate and size choices (no device: the decoder questions are passed in). */
class VideosTest {
    private val hwAll: (String, Int, Int) -> Boolean = { _, _, _ -> true }
    private val rateAll: (String, Int, Int, Int) -> Boolean = { _, _, _, _ -> true }
    private fun v(w: Int, h: Int, fps: Int, bps: Long = 4_000_000, mime: String = "video/avc") = Videos.Probe("video", mime, w, h, 10_000, bps, fps)
    private fun heavy(p: Videos.Probe, o: Videos.Opts = Videos.Opts(0, 0), hw: (String, Int, Int) -> Boolean = hwAll, rate: (String, Int, Int, Int) -> Boolean = rateAll) =
        Videos.heavy(p, 1920, 1080, o, hw, rate)

    @Test fun lightVideoIsKeptAsSent() { assertNull(heavy(v(1920, 1080, 30))); assertNull(heavy(v(1280, 720, 60))) }

    @Test fun heavyReasons() {
        assertEquals("gif", heavy(Videos.Probe("gif", "image/gif", 480, 270, 5000, 0, 0)))
        assertEquals("codec", heavy(v(1920, 1080, 30), hw = { _, _, _ -> false }))
        assertEquals("size", heavy(v(3840, 2160, 30)))
        assertEquals("bitrate", heavy(v(1920, 1080, 30, 12_000_000)))
        assertEquals("fps", heavy(v(1920, 1080, 60), rate = { _, _, _, f -> f <= 30 }))      // "Original", but this decoder does 1080p30 only
    }

    @Test fun frameRateAndSizeAreChoices() {
        assertEquals("fps", heavy(v(1920, 1080, 60), Videos.Opts(30, 0)))
        assertNull(heavy(v(1920, 1080, 30), Videos.Opts(30, 0)))                              // 30 asked, 30 had: kept
        assertEquals("size", heavy(v(1920, 1080, 30), Videos.Opts(0, 720)))
        assertNull(heavy(v(1280, 720, 30), Videos.Opts(0, 720)))
    }

    @Test fun limitAndTarget() {
        assertEquals(1920 to 1080, Videos.limit(1920, 1080, Videos.Opts(0, 0)))
        assertEquals(1280 to 720, Videos.limit(1920, 1080, Videos.Opts(0, 720)))
        assertEquals(1920 to 1080, Videos.limit(1920, 1080, Videos.Opts(0, 1080)))
        assertEquals(1920 to 1080, Videos.target(3840, 2160, 1920, 1080))
        assertEquals(480 to 270, Videos.target(480, 270, 1920, 1080))                         // never bigger than it is
        assertEquals(1080, Videos.target(2160, 3840, 1920, 1080).second)                     // a portrait clip fits the height
        val (w, h) = Videos.target(1001, 563, 640, 360); assertEquals(0, w % 2); assertEquals(0, h % 2)
    }

    @Test fun bitrateByPixels() {
        assertEquals(4_043_520, Videos.bitrate(1920, 1080, 30))
        assertEquals(1_797_120, Videos.bitrate(1280, 720, 30))
        assertEquals(600_000, Videos.bitrate(160, 90, 15)); assertEquals(6_000_000, Videos.bitrate(3840, 2160, 60))
    }
}
