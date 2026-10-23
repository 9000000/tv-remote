package dev.tvremote.core

import dev.tvremote.core.adb.TvKeyboard
import dev.tvremote.core.adb.TvKeyboard.Do
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The dumpsys line that says the TV's keyboard is up, and when the typing box follows it. */
class TvKeyboardTest {
    @Test fun parsesTheLine() {
        assertTrue(TvKeyboard.shown("  mInputShown=true"))
        assertFalse(TvKeyboard.shown("  mInputShown=false"))
        assertFalse(TvKeyboard.shown(""))
        assertFalse(TvKeyboard.shown("  mIsInputViewShown=true mStatusIcon=0"))   // stays true with the keyboard hidden
    }

    @Test fun opensThenClosesWhenNothingWasTyped() {
        val f = TvKeyboard.Follow()
        assertEquals(Do.NONE, f.read(false))
        assertEquals(Do.OPEN, f.read(true))
        assertEquals(Do.NONE, f.read(true))
        assertEquals(Do.CLOSE, f.read(false))
        assertEquals(Do.NONE, f.read(false))
    }

    @Test fun keepsTheBoxWhenSomethingWasTyped() {
        val f = TvKeyboard.Follow()
        f.read(true); f.typedByPerson()
        assertEquals(Do.NONE, f.read(false))
    }

    @Test fun staysShutAfterThePersonClosedIt() {
        val f = TvKeyboard.Follow()
        f.read(true); f.closedByPerson()
        assertEquals(Do.NONE, f.read(true))
        assertEquals(Do.NONE, f.read(false))
        assertEquals(Do.OPEN, f.read(true))                           // a new keyboard session opens it again
    }
}
