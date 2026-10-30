package dev.tvremote.home.widgets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A box that boots with its clock a year ahead saves stamps in the future; once the clock is corrected they must read as old. */
class FreshStampTest {
    private val now = 1_800_000_000_000L
    private val future = now + 365L * 24 * 3600_000
    private val young = now - 60_000

    @Test fun weather() {
        assertFalse(Weather.stale(young, null, false, true, now))
        assertTrue(Weather.stale(future, null, false, true, now))
        assertTrue(Weather.stale(young, future, true, true, now))       // the air reading too
        assertFalse(Weather.stale(young, young, true, true, now))
    }

    @Test fun market() {
        fun snap(r: Long, g: Long, b: Long) = Market.Snap(emptyList(), null, null, r, g, b)
        assertFalse(Market.stale(snap(young, young, young), true, now))
        assertTrue(Market.stale(snap(future, young, young), false, now))
        assertTrue(Market.stale(snap(young, young, future), true, now))
        assertFalse(Market.stale(snap(young, young, future), false, now))   // BTC off: its stamp does not count
    }

    @Test fun news() {
        assertFalse(News.stale(young, now))
        assertTrue(News.stale(future, now))
    }
}
