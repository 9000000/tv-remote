package dev.tvremote.games

import dev.tvremote.games.PadNames.S
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PadNamesTest {
    @Test fun cleanTrimsAndCollapses() {
        assertEquals("Bố Hoàng", PadNames.clean("  Bố \n  Hoàng  "))
        assertNull(PadNames.clean("   \u0007 "))
        assertNull(PadNames.clean(null))
    }

    @Test fun cleanCapsAtSixteenCodePointsKeepingEmoji() {
        assertEquals("abcdefghijklmnop", PadNames.clean("abcdefghijklmnopqrstuvwxyz"))
        val e = "😀".repeat(20)
        assertEquals("😀".repeat(16), PadNames.clean(e))
        assertEquals("Mẹ 🎮", PadNames.clean("Mẹ 🎮"))
    }

    @Test fun cleanDropsBidiOverrides() {
        assertEquals("a b", PadNames.clean("a‮b"))
    }

    @Test fun pickKeepsAPhonesOwnSlotEvenIfLaterOnesAreFree() {
        val s = listOf(S(null, false, false, false), S("b", false, false, true), S("c", true, false, false), S(null, false, false, false))
        assertEquals(1, PadNames.pick(s, "b"))
        assertEquals(2, PadNames.pick(s, "c"))
    }

    @Test fun pickGivesTheFirstFreeUnkeptSlotElseAKeptOne() {
        val s = listOf(S("a", false, false, true), S("b", true, false, false), S("c", false, true, false), S("d", false, false, true))
        assertEquals(0, PadNames.pick(s, "new"))                     // nothing unkept: a kept one is taken (first)
        val t = listOf(S("a", false, false, true), S("b", true, false, false), S("c", false, false, false))
        assertEquals(2, PadNames.pick(t, "new"))
    }

    @Test fun pickIsMinusOneWhenFull() {
        assertEquals(-1, PadNames.pick(List(4) { S("x$it", true, false, false) }, "new"))
    }
}
