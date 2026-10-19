package dev.tvremote.core

import dev.tvremote.core.ontv.SpeedTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** SpeedTest.parse: what the page shows of Speed's lines on the TV. */
class SpeedTestTest {
    @Test fun countsFromTheFirstSecond() {
        val o = SpeedTest.parse("ping 23\ndown 1000000 500\ndown 2000000 1000\ndown 14500000 2000\nrunning\n")
        assertEquals("down", o.getString("phase"))
        assertEquals(23, o.getInt("ping"))
        assertEquals(100.0, o.getDouble("down"), 0.01)                        // 12.5 MB in the second after the first
        assertEquals(true, o.getBoolean("running"))
    }

    @Test fun doneAndErrors() {
        val done = SpeedTest.parse("ping 9\ndown 0 1000\ndown 5000000 2000\nup 0 1000\nup 2500000 2000\ndone\n")
        assertEquals("done", done.getString("phase"))
        assertEquals(40.0, done.getDouble("down"), 0.01)
        assertEquals(20.0, done.getDouble("up"), 0.01)
        assertEquals("unknown host", SpeedTest.parse("error java.net.UnknownHostException: unknown host\n").getString("error"))
        assertEquals("stopped", SpeedTest.parse("ping 9\ndown 0 500\n").getString("error"))   // killed half way
        assertEquals("", SpeedTest.parse("").getString("phase"))
    }
}
