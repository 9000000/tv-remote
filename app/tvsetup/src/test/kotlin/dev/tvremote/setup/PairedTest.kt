package dev.tvremote.setup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which browsers Paired lets go (Paired.stale): a wrong clock must not forget them. */
class PairedTest {
    private val t = 1_800_000_000_000L
    private val day = 24 * 3600_000L
    private fun e(kind: String, created: Long, seen: Long) = Paired.Entry("i", "h", "n", "", created, seen, kind, false)

    @Test fun unseenForThirtyDaysGoes() {
        assertTrue(Paired.stale(e("web", t, t), t + 31 * day))
        assertFalse(Paired.stale(e("web", t, t), t + 29 * day))
        assertFalse(Paired.stale(e("app", t, t), t + 400 * day))
    }

    @Test fun aClockBeforeItsCreationForgetsNothing() {
        assertFalse(Paired.stale(e("web", t, t), t - 40 * day))
        assertFalse(Paired.stale(e("web", t, t + 5 * day), t + day))   // seen "ahead": not stale either
    }
}
