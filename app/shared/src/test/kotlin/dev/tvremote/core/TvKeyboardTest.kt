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

    // the FPT box after an update, Gboard as its keyboard (Android 14), TV Home in front, no field focused: 440 bytes
    private val GBOARD = "1a b5 03 0a 4b 63 6f 6d 2e 67 6f 6f 67 6c 65 2e 61 6e 64 72 6f 69 64 2e 69 6e 70 75 74 6d 65 74 68 6f 64 2e 6c 61 74 69 6e 2f 63 6f 6d 2e 61 6e 64 72 6f 69 64 2e 69 6e 70 75 74 6d 65 74 68 6f 64 2e 6c 61 74 69 6e 2e 4c 61 74 69 6e 49 4d 45 10 2f 1a 43 43 6c 69 65 6e 74 53 74 61 74 65 7b 31 34 66 64 38 61 32 20 6d 55 69 64 3d 31 30 30 36 37 20 6d 50 69 64 3d 32 30 39 39 32 20 6d 53 65 6c 66 52 65 70 6f 72 74 65 64 44 69 73 70 6c 61 79 49 64 3d 30 7d 22 38 39 62 62 37 65 30 63 20 64 65 76 2e 74 76 72 65 6d 6f 74 65 2e 68 6f 6d 65 2f 64 65 76 2e 74 76 72 65 6d 6f 74 65 2e 68 6f 6d 65 2e 48 6f 6d 65 41 63 74 69 76 69 74 79 2a 38 39 62 62 37 65 30 63 20 64 65 76 2e 74 76 72 65 6d 6f 74 65 2e 68 6f 6d 65 2f 64 65 76 2e 74 76 72 65 6d 6f 74 65 2e 68 6f 6d 65 2e 48 6f 6d 65 41 63 74 69 76 69 74 79 32 1f 53 54 41 54 45 5f 55 4e 53 50 45 43 49 46 49 45 44 7c 41 44 4a 55 53 54 5f 52 45 53 49 5a 45 3a 13 22 11 64 65 76 2e 74 76 72 65 6d 6f 74 65 2e 68 6f 6d 65 42 4b 63 6f 6d 2e 67 6f 6f 67 6c 65 2e 61 6e 64 72 6f 69 64 2e 69 6e 70 75 74 6d 65 74 68 6f 64 2e 6c 61 74 69 6e 2f 63 6f 6d 2e 61 6e 64 72 6f 69 64 2e 69 6e 70 75 74 6d 65 74 68 6f 64 2e 6c 61 74 69 6e 2e 4c 61 74 69 6e 49 4d 45 72 19 61 6e 64 72 6f 69 64 2e 6f 73 2e 42 69 6e 64 65 72 40 31 66 32 61 31 38 33 80 01 01 90 01 01 98 01 01 a0 01 01 b8 01 01"

    @Test fun readsTheProto() {
        assertEquals(false, TvKeyboard.fromProto(GBOARD))
        assertEquals(true, TvKeyboard.fromProto(FIELD))
        assertEquals(false, TvKeyboard.fromProto(CHIP))
        assertEquals(true, TvKeyboard.fromProto(CHIP.replace("1a 1c", "1a 1e") + " 60 01"))   // input_shown
        assertEquals(null, TvKeyboard.fromProto(""))
        assertEquals(null, TvKeyboard.fromProto("  mInputShown=true"))           // a TV that ignores --proto prints the text
    }

    @Test fun fallsBackToTheTextOnce() {
        val r = TvKeyboard.Reader(); val asked = ArrayList<String>()
        val sh = { c: String -> asked += c; "  mInputShown=true" }               // a TV that ignores --proto prints the text
        assertTrue(r.ask(sh)); assertTrue(r.ask(sh))
        assertEquals(listOf(TvKeyboard.PROTO, TvKeyboard.CMD, TvKeyboard.CMD), asked)
        asked.clear(); repeat(TvKeyboard.RETRY) { r.ask(sh) }
        assertEquals(1, asked.count { it == TvKeyboard.PROTO })                 // asked again now and then: a TV's answer can change
    }

    @Test fun anEmptyReplyNeverSwitchesToTheText() {
        // the FPT box frozen by a game answered nothing once; the Reader then read the text dump (0.8 s of Gboard's CPU) every second
        val r = TvKeyboard.Reader(); val asked = ArrayList<String>(); var busy = true
        val sh = { c: String -> asked += c; if (busy) "" else CHIP }
        repeat(3) { assertTrue(runCatching { r.ask(sh) }.isFailure) }
        busy = false; assertEquals(false, r.ask(sh))
        assertTrue(asked.all { it == TvKeyboard.PROTO })
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
