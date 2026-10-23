package dev.tvremote.home.manage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The QR's one-time key, the session it becomes, and the time limits (Access.kt): what keeps another device on the Wi-Fi out. */
class AccessTest {
    private var now = 0L
    private var n = 0
    private fun access(idle: Long = 1000, session: Long = 5000) = Access({ now }, { len -> "x".repeat(len - 1) + (n++ % 10) }, idle, session)

    @Test fun keyWorksOnceThenIsDead() {
        val a = access(); val k = a.issue()
        val s = a.redeem(k)
        assertNotNull(s)
        assertNull("a second use of the same key", a.redeem(k))
        assertTrue(a.check(s))
    }

    @Test fun noKeyNoSessionNoEntry() {
        val a = access()
        assertNull("before any key was made", a.redeem("anything"))
        a.issue()
        assertNull(a.redeem(null)); assertNull(a.redeem("")); assertNull(a.redeem("wrong"))
        assertFalse(a.check(null)); assertFalse(a.check("guess"))      // what the server answers with 401
    }

    @Test fun aWrongKeyDoesNotSpendTheRightOne() {
        val a = access(); val k = a.issue()
        assertNull(a.redeem(k.dropLast(1) + "?"))
        assertNotNull(a.redeem(k))
    }

    @Test fun manyWrongGuessesVoidTheKey() {
        val a = access(); val k = a.issue()
        repeat(Access.MAX_WRONG) { assertNull(a.redeem("nope$it")) }
        assertFalse(a.keyOpen())
        assertNull("the right key after a guesser's tries", a.redeem(k))
    }

    @Test fun aNewKeyCutsTheOldSessions() {
        val a = access(); val s = a.redeem(a.issue())
        assertTrue(a.check(s))
        a.issue()
        assertFalse(a.check(s)); assertEquals(0, a.connected())
    }

    @Test fun sessionEndsAfterItsHour() {
        val a = access(idle = 10_000, session = 5000); val s = a.redeem(a.issue())
        now = 4000; assertTrue(a.check(s))                              // used: idle starts again
        now = 4999; assertTrue(a.check(s))
        now = 5000; assertFalse("an hour (here 5 s) after it was made, however busy", a.check(s))
        assertFalse(a.check(s))
    }

    @Test fun idleServerIsToldToStop() {
        val a = access(idle = 1000); a.issue()
        now = 999; assertFalse(a.idle())
        now = 1000; assertTrue("no authorised request for 10 minutes (here 1 s)", a.idle())
    }

    @Test fun anAuthorisedRequestKeepsItAliveAGuessDoesNot() {
        val a = access(idle = 1000, session = 100_000); val s = a.redeem(a.issue())
        now = 900; assertFalse(a.check("guess"))
        now = 1000; assertTrue("a stranger's request did not count as use", a.idle())
        val a2 = access(idle = 1000, session = 100_000); val t = a2.redeem(a2.issue())
        now = 1800; assertTrue(a2.check(t))
        now = 2700; assertFalse("the session's request moved the idle time on", a2.idle())
        assertNotNull(s)
    }

    @Test fun idleSessionDoesNotComeBack() {
        val a = access(idle = 1000, session = 100_000); val s = a.redeem(a.issue())
        now = 1500
        assertFalse("past the idle time the session is over, as the server has stopped", a.check(s))
    }

    @Test fun connectedCountsLiveSessions() {
        val a = access(session = 5000)
        assertEquals(0, a.connected())
        a.redeem(a.issue()); assertEquals(1, a.connected())
        now = 6000; assertEquals(0, a.connected())
    }

    @Test fun realKeysAreLongAndDifferent() {
        val a = Access()
        val k1 = a.issue(); val k2 = a.issue()
        assertEquals(Access.KEY_LEN, k1.length); assertNotEquals(k1, k2)
        assertNull("the first key after the second was made", a.redeem(k1))
        assertEquals(Access.SESSION_LEN, a.redeem(k2)!!.length)
    }
}
