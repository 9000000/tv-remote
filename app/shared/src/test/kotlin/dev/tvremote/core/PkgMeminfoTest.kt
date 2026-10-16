package dev.tvremote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PkgMeminfoTest {
    @Test fun packageNames() {
        for (p in listOf("com.a.b", "com.google.android.youtube.tv", "A.b_c.d9", "a.b._c")) assertTrue(p, Pkg.valid(p))
        for (p in listOf("", "com", "com.a.", ".com.a", "com.a;reboot", "com.a b", "com.1a", "9com.a", "com.a\nb", "com.a\$(id)", "com.a'b"))
            assertFalse(p, Pkg.valid(p))
    }

    @Test fun meminfo() {
        val t = "MemTotal:        2000000 kB\nMemFree:  1 kB\nMemAvailable:    1024000 kB\n"
        assertEquals(1024000L, Meminfo.kb(t, "MemAvailable"))
        assertEquals(2000000L, Meminfo.kb(t, "MemTotal"))
        assertNull(Meminfo.kb(t, "Nope"))
    }

    @Test fun cpu() {
        assertEquals(67, Meminfo.cpu("cpu  100 0 100 800 0 0 0", "cpu  200 0 200 900 0 0 0"))
        assertNull(Meminfo.cpu("cpu  1 0 1 8 0", "cpu  1 0 1 8 0"))
        assertNull(Meminfo.cpu("junk", "junk"))
    }
}
