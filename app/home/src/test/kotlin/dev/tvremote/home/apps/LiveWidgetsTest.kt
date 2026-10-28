package dev.tvremote.home.apps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which apps may provide live widgets: the pinned key of each, or this app's own. */
class LiveWidgetsTest {
    @Test fun eachProviderHasItsOwnKey() {
        assertTrue(LiveWidgets.PINNED.keys.containsAll(LiveWidgets.PROVIDERS))
        assertTrue(LiveWidgets.PINNED.values.toSet().size == LiveWidgets.PINNED.size)         // three apps, three keys
        assertTrue(LiveWidgets.PINNED.values.all { it.matches(Regex("[0-9a-f]{64}")) })
    }

    @Test fun pinnedOrOwnKeyIsTrusted() {
        val setup = LiveWidgets.PINNED.getValue("dev.tvremote.setup"); val ch = LiveWidgets.PINNED.getValue("dev.tvremote.channels")
        assertTrue(LiveWidgets.certOk("dev.tvremote.setup", listOf(setup), emptyList()))
        assertTrue(LiveWidgets.certOk("dev.tvremote.channels", listOf(ch), listOf("x")))
        assertTrue(LiveWidgets.certOk("dev.tvremote.channels", listOf("dbg"), listOf("dbg")))      // debug builds share a key
        assertFalse(LiveWidgets.certOk("dev.tvremote.channels", listOf(setup), listOf("x")))       // another app's release key is not this one's
        assertFalse(LiveWidgets.certOk("dev.tvremote.setup", emptyList(), listOf("x")))
    }
}
