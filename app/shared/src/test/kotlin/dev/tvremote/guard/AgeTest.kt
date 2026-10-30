package dev.tvremote.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AgeTest {
    private val day = 24 * 3600_000L

    @Test fun youngIsFreshOldAndFutureAreNot() {
        assertTrue(Age.fresh(1_000_000, day, now = 1_000_000 + day - 1))
        assertFalse(Age.fresh(1_000_000, day, now = 1_000_000 + day))
        assertFalse("saved while the clock ran a year ahead", Age.fresh(1_000_000 + 365 * day, day, now = 1_000_000))
    }

    /** A file written at a wrong-year boot has an mtime in the future: it must be asked for again once the clock is right. */
    @Test fun aFileFromTheFutureIsStale() {
        val f = File.createTempFile("age", ".json").apply { deleteOnExit(); setLastModified(System.currentTimeMillis() + 365 * day) }
        assertFalse(Age.fresh(f.lastModified(), day))
    }
}
