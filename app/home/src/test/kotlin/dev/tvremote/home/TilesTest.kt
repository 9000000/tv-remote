package dev.tvremote.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which apps may provide live tiles: the pinned key of each, or this app's own. */
class TilesTest {
    @Test fun eachProviderHasItsOwnKey() {
        assertTrue(Tiles.PINNED.keys.containsAll(Tiles.PROVIDERS))
        assertTrue(Tiles.PINNED.values.toSet().size == Tiles.PINNED.size)         // three apps, three keys
        assertTrue(Tiles.PINNED.values.all { it.matches(Regex("[0-9a-f]{64}")) })
    }

    @Test fun pinnedOrOwnKeyIsTrusted() {
        val setup = Tiles.PINNED.getValue("dev.tvremote.setup"); val ch = Tiles.PINNED.getValue("dev.tvremote.channels")
        assertTrue(Tiles.certOk("dev.tvremote.setup", listOf(setup), emptyList()))
        assertTrue(Tiles.certOk("dev.tvremote.channels", listOf(ch), listOf("x")))
        assertTrue(Tiles.certOk("dev.tvremote.channels", listOf("dbg"), listOf("dbg")))      // debug builds share a key
        assertFalse(Tiles.certOk("dev.tvremote.channels", listOf(setup), listOf("x")))       // another app's release key is not this one's
        assertFalse(Tiles.certOk("dev.tvremote.setup", emptyList(), listOf("x")))
    }
}
