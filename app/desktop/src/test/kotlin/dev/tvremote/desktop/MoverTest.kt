package dev.tvremote.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoverTest {
    private val h = "/Users/a"
    private fun need(p: String?, mac: Boolean = true) = Mover.needsMove(mac, p, h)

    @Test fun whichPathsNeedMoving() {
        assertTrue(need("/private/var/folders/x/T/AppTranslocation/ABC/d/TV Remote.app"))
        assertTrue(need("$h/Downloads/TV Remote.app"))
        assertTrue(need("$h/Downloads/test/TV Remote.app"))
        assertTrue(need("/Volumes/Disk/TV Remote.app"))
        assertFalse(need("/Applications/TV Remote.app"))
        assertFalse(need("$h/Applications/TV Remote.app"))
        assertTrue(need("/Applications/Sub/TV Remote.app"))   // a subfolder of Applications is not Applications itself
    }

    @Test fun onlyAPackagedMacAsks() {
        assertFalse(need("$h/Downloads/TV Remote.app", mac = false))
        assertFalse(need(null))
        assertFalse(need("/opt/tv-remote/lib/app"))                      // not a bundle (Windows, Linux, the repo)
    }

    @Test fun laterIsRememberedPerVersion() {
        val p = "$h/Downloads/TV Remote.app"
        assertTrue(Mover.asks(true, p, h, "", "0.5.0"))
        assertFalse(Mover.asks(true, p, h, "0.5.0", "0.5.0"))
        assertFalse(Mover.asks(true, p, h, "0.5.0\n", "0.5.0"))
        assertTrue(Mover.asks(true, p, h, "0.5.0", "0.5.1"))             // a new version asks again
        assertFalse(Mover.asks(true, "/Applications/TV Remote.app", h, "", "0.5.0"))
    }

    @Test fun onlyOurOwnAppIsEverRemoved() {
        assertFalse(Mover.ours(java.io.File("/tmp")))
        assertFalse(Mover.ours(java.io.File("/System/Applications/Safari.app")))
        assertEquals("dev.tvremote.desktop", Mover.ID)
    }
}
