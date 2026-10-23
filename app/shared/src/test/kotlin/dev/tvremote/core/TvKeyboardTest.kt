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

    @Test fun aFocusedTextFieldCountsWithTheKeyboardDown() {
        val field = "    inputType=0x11 imeOptions=0x10000006 fieldId=0xffffffff fieldName=null actionId=0 actionLabel=null"
        val none = "    inputType=0x0 imeOptions=0x0 fieldId=0x0 fieldName=null actionId=0 actionLabel=null"
        assertTrue(TvKeyboard.shown("  mInputShown=false\n$none\n$field"))
        assertFalse(TvKeyboard.shown("  mInputShown=false\n$field\n$none"))   // only the last start counts
        assertFalse(TvKeyboard.shown("  mInputShown=false\n    inputType=0x11 imeOptions=0x10000006 privateImeOptions=null"))   // the keyboard app's own line
    }

    // measured on the FPT box: the link field in TV Home with the keyboard down, then the focus on a chip below it
    private val FIELD = "1a 23 0a 04 61 2f 62 49 10 4f 3a 08 08 11 22 04 64 65 76 2e 80 01 01 a0 01 01 b8 01 01 98 01 01 90 01 01 48 00"
    private val CHIP = "1a 1c 0a 04 61 2f 62 49 10 4f 3a 06 22 04 64 65 76 2e 80 01 01 a0 01 01 98 01 01 90 01 01"

    @Test fun readsTheProto() {
        assertEquals(true, TvKeyboard.fromProto(FIELD))
        assertEquals(false, TvKeyboard.fromProto(CHIP))
        assertEquals(true, TvKeyboard.fromProto(CHIP.replace("1a 1c", "1a 1e") + " 60 01"))   // input_shown
        assertEquals(null, TvKeyboard.fromProto(""))
        assertEquals(null, TvKeyboard.fromProto("  mInputShown=true"))           // a TV that ignores --proto prints the text
    }

    @Test fun fallsBackToTheTextOnce() {
        val r = TvKeyboard.Reader(); val asked = ArrayList<String>()
        val sh = { c: String -> asked += c; if (c == TvKeyboard.PROTO) "" else "  mInputShown=true" }
        assertTrue(r.ask(sh)); assertTrue(r.ask(sh))
        assertEquals(listOf(TvKeyboard.PROTO, TvKeyboard.CMD, TvKeyboard.CMD), asked)
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
