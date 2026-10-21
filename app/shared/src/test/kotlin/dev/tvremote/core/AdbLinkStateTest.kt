package dev.tvremote.core

import dev.tvremote.core.adb.AdbLink
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

/** A redial after a failure reads "unknown" (connecting), not "closed": a page opened then said "needs ADB" a second before ADB was back. */
class AdbLinkStateTest {
    private fun settle(l: AdbLink): String {
        val until = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < until) { val s = l.state(); if (s == "closed") return s; Thread.sleep(50) }
        return l.state()
    }

    @Test fun redialIsUnknown() {
        val l = AdbLink(Files.createTempDirectory("keys").toFile(), "127.0.0.1:1")   // port 1: refused at once
        assertEquals("unknown", l.state())                                // the first dial
        assertEquals("closed", settle(l))                                 // it failed, nothing under way
        AdbLink::class.java.getDeclaredField("lastTry").apply { isAccessible = true }.setLong(l, 0L)   // 10 s later
        assertEquals("unknown", l.state())                                // dialing again: not "closed"
    }
}
