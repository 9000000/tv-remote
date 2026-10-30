package dev.tvremote.channels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The prefs file stays small and quiet: old stream checks pruned, a stream already known alive not written again, favourites parsed once, no dead mark offline. */
class StoreHealthTest {
    private val now = 1_000_000_000_000L
    private val h = 3600_000L

    @Test fun staleChecksArePruned() {
        val all = HashMap<String, Any>()
        for (i in 0 until 5000) all["ok:http://x/$i"] = if (i % 2 == 0) now - 7 * h else -(now - 9 * h)
        all["ok:http://fresh"] = now - h; all["ok:http://freshDead"] = -(now - 2 * h)
        all["favs"] = "[]"; all["country"] = "VN"; all["last"] = "a"
        val old = Store.staleOk(all, now)
        assertEquals(5000, old.size)
        assertTrue(old.all { it.startsWith("ok:http://x/") })
    }

    @Test fun aMarkAheadOfNowIsNotFreshAndNothingIsPruned() {          // a box booted a year ahead: its marks are ignored, rewritten on the next check, and none pruned meanwhile
        val ahead = now + 365 * 24 * h
        assertTrue(Store.needsAliveWrite(ahead, true, now))
        assertTrue(Store.needsAliveWrite(-ahead, false, now))
        val all = mapOf("ok:http://a" to ahead, "ok:http://b" to now - 9 * h, "ok:http://c" to now - h)
        assertTrue(Store.staleOk(all, now).isEmpty())
        assertEquals(listOf("ok:http://b"), Store.staleOk(all - "ok:http://a", now))
    }

    @Test fun aliveIsWrittenOnce() {
        var stored = 0L; var writes = 0
        repeat(20) {
            if (Store.needsAliveWrite(stored, true, now + it * 1000)) { stored = now + it * 1000; writes++ }
        }
        assertEquals(1, writes)
        assertTrue(Store.needsAliveWrite(stored, true, now + 7 * h))     // its answer is old: written again
        assertTrue(Store.needsAliveWrite(-now, true, now))               // was dead, now alive
        assertTrue(Store.needsAliveWrite(stored, false, now))            // dead is always written
    }

    @Test fun favouritesParsedOncePerChange() {
        val c = Store.IdCache { raw -> raw.split(",").toSet() }
        val a = StringBuilder("a,b").toString()
        repeat(100) { assertTrue("a" in c.ids(a)) }
        assertEquals(1, c.parses)
        assertFalse("c" in c.ids(a))
        assertTrue("c" in c.ids("a,b,c"))
        assertEquals(2, c.parses)
    }

    @Test fun deadOnlyWhileOnline() {
        assertTrue(shouldMarkDead(true))
        assertFalse(shouldMarkDead(false))
    }
}
