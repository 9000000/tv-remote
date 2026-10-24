package dev.tvremote.home.apps

import dev.tvremote.home.apps.HomeLock.Tries
import dev.tvremote.home.apps.HomeLock.Verdict
import org.junit.Assert.*
import org.junit.Test

class HomeLockTest {
    private val salt = "s1"
    private val stored = HomeLock.hash(salt, "2580")

    @Test fun hashIsSaltedAndNeverThePin() {
        assertNotEquals("2580", stored)
        assertEquals(stored, HomeLock.hash(salt, "2580"))
        assertNotEquals(stored, HomeLock.hash("s2", "2580"))
        assertNotEquals(stored, HomeLock.hash(salt, "2581"))
        assertFalse(stored.contains("2580"))
    }

    @Test fun onlyFourDigitsAreAPin() {
        assertTrue(HomeLock.valid("0123")); assertFalse(HomeLock.valid("123")); assertFalse(HomeLock.valid("12345")); assertFalse(HomeLock.valid("12a4"))
    }

    @Test fun rightCodeClearsTheCount() {
        val (v, t) = HomeLock.attempt(salt, stored, "2580", Tries(3, 1, 0), 1000)
        assertEquals(Verdict.Ok, v); assertEquals(Tries(), t)
    }

    @Test fun wrongCodesCountDownThenWait() {
        var t = Tries(); var left = 0
        for (i in 1..4) { val (v, n) = HomeLock.attempt(salt, stored, "0000", t, 1000); t = n; left = (v as Verdict.Wrong).left }
        assertEquals(1, left)
        val (v, n) = HomeLock.attempt(salt, stored, "0000", t, 1000)
        assertEquals(Verdict.Wait(60), v)                            // the 5th wrong in a row: 1 minute
        assertEquals(61_000L, n.until)
    }

    @Test fun duringAWaitEvenTheRightCodeIsRefusedAndNotCounted() {
        val t = Tries(0, 1, 61_000L)
        val (v, n) = HomeLock.attempt(salt, stored, "2580", t, 31_000)
        assertEquals(Verdict.Wait(30), v); assertEquals(t, n)
        assertEquals(Verdict.Ok, HomeLock.attempt(salt, stored, "2580", t, 61_000).first)   // after it, right opens again
    }

    @Test fun waitsGrowAndStopAtAnHour() {
        var t = Tries(); var now = 0L; val waits = ArrayList<Long>()
        repeat(6) {
            repeat(HomeLock.TRIES) { val (v, n) = HomeLock.attempt(salt, stored, "1111", t, now); t = n; if (v is Verdict.Wait) waits.add(v.seconds) }
            now = t.until
        }
        assertEquals(listOf(60L, 300L, 900L, 3600L, 3600L, 3600L), waits)
    }

    @Test fun noPinKeptNeverOpens() {
        assertTrue(HomeLock.attempt("", "", "", Tries(), 0).first is Verdict.Wrong)
    }

    @Test fun theGateAsksOnlyForALockedAppNotYetCleared() {
        val locked = setOf("a.b")
        assertTrue(HomeLock.needsPin(locked, "a.b", false))
        assertFalse(HomeLock.needsPin(locked, "a.b", true))      // after the right code
        assertFalse(HomeLock.needsPin(locked, "c.d", false))
        assertFalse(HomeLock.needsPin(emptySet(), "a.b", false))
    }
}
