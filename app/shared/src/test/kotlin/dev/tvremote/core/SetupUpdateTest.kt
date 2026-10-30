package dev.tvremote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.tvremote.core.ontv.SetupUpdate

/** The state line the update script writes on the TV, as the pages and TV Setup read it. */
class SetupUpdateTest {
    @Test fun rolledBack() {
        val s = SetupUpdate.parse("rolledback 1792053915 2.18.1 2.18.0 \n|\n2.18.1\n2.18.1\njunk;rm\n")
        assertEquals("rolledback", s.getString("state"))
        assertEquals(1792053915000L, s.getLong("at"))
        assertEquals("2.18.1", s.getString("version"))
        assertEquals("2.18.0", s.getString("from"))
        assertEquals(1, s.getJSONArray("bad").length())               // once, and nothing that is not a version
        assertTrue(SetupUpdate.bad(s, "2.18.1"))
        assertFalse(SetupUpdate.bad(s, "2.18.2"))
        assertFalse(SetupUpdate.busy(s))
    }

    @Test fun failedKeepsItsReason() {
        val s = SetupUpdate.parse("failed 1792053000 2.19.0 2.18.0 Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE] x\n|\n")
        assertEquals("Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE] x", s.getString("detail"))
    }

    @Test fun nothingYet() {
        val s = SetupUpdate.parse("\n|\n")
        assertEquals("", s.getString("state"))
        assertEquals(0, s.getJSONArray("bad").length())
    }

    @Test fun runningUntilStale() {
        val now = System.currentTimeMillis() / 1000
        assertTrue(SetupUpdate.busy(SetupUpdate.parse("checking $now 2.19.0 2.18.0 \n|")))
        assertFalse(SetupUpdate.busy(SetupUpdate.parse("installing ${now - 3600} 2.19.0 2.18.0 \n|")))   // the TV restarted mid-way
    }

    @Test fun stampAheadOfNowIsStale() {                            // a clock that jumped back must not leave "busy" for ever
        val now = System.currentTimeMillis() / 1000
        assertFalse(SetupUpdate.busy(SetupUpdate.parse("installing ${now + 86400} 2.19.0 2.18.0 \n|")))
        assertTrue(SetupUpdate.busy(SetupUpdate.parse("installing ${now + 5} 2.19.0 2.18.0 \n|")))   // a few seconds of slack
    }

    @Test fun failedWaitsOnlyWhileRecent() {
        val now = 1_800_000_000_000L
        fun failed(ago: Long, v: String = "2.19.0") = SetupUpdate.parse("failed ${(now - ago) / 1000} $v 2.18.0 x\n|")
        val h = 3_600_000L
        assertFalse(SetupUpdate.failedLately(failed(7 * h), "2.19.0", now))        // old: try again
        assertTrue(SetupUpdate.failedLately(failed(1 * h), "2.19.0", now))         // recent: wait
        assertFalse(SetupUpdate.failedLately(failed(-24 * h), "2.19.0", now))      // stamped ahead of now: stale
        assertFalse(SetupUpdate.failedLately(failed(1 * h, "2.19.1"), "2.19.0", now))   // another version
        assertFalse(SetupUpdate.failedLately(SetupUpdate.parse("ok ${now / 1000} 2.19.0 2.18.0 \n|"), "2.19.0", now))
    }
}
