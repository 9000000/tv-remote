package dev.tvremote.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** TV Home's CPU ring: the busy share between two reads of /proc/stat (HomeInfo.busy). */
class CpuBusyTest {
    @Test fun idleEmulator() {                                         // read one second apart on the TV emulator
        val b = HomeInfo.busy("cpu  43964 7586 61589 25867306 2671 69606 3517 0 0 0", "cpu  43965 7586 61590 25867713 2671 69606 3517 0 0 0")!!
        assertEquals(2f / 409, b, 1e-4f)
    }

    @Test fun waitingForTheDiskIsNotBusy() {
        assertEquals(0.5f, HomeInfo.busy("cpu  0 0 0 0 0", "cpu  50 0 0 25 25")!!, 1e-4f)
    }

    @Test fun firstLineOnlyAndBadInput() {
        assertEquals(1f, HomeInfo.busy("cpu  0 0 0 0\ncpu0 9 9 9 9", "cpu  10 0 0 0\ncpu0 0 0 0 0")!!, 1e-4f)
        assertNull(HomeInfo.busy("", "cpu  1 2 3 4"))
        assertNull(HomeInfo.busy("cpu  1 2 3 4", "cpu  1 2 3 4"))                // no time passed
        assertNull(HomeInfo.busy("cpu  1 2", "cpu  3 4"))
    }
}
