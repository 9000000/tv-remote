package dev.tvremote.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseTest {
    @Test fun compare() {
        assertTrue(Release.compare("1.10.0", "1.9") > 0)
        assertTrue(Release.compare("0.2.0", "0.1.9") > 0)
        assertTrue(Release.compare("0.1.0", "0.1") == 0)
    }

    @Test fun beforeTheRestart() {                                  // 1.3.0 … 2.24.2 came before 0.1.0: every one of them is older
        assertTrue(Release.compare("0.1.0", "2.24.2") > 0)
        assertTrue(Release.compare("0.1.0", "1.3.0") > 0)
        assertTrue(Release.compare("2.22.3", "0.1.1") < 0)
        assertTrue(Release.compare("2.24.2", "2.21.4") > 0)        // among themselves as before
        assertTrue(Release.compare("1.2.0", "0.9.0") > 0)          // the new numbers reach 1.0 as usual
    }

    @Test fun signedVersion() {                                    // the version line of SHA256SUMS: a list without it still works
        val list = "${"a".repeat(64)}  tv-remote.apk\nversion android=0.4.1 tv=0.4.0 web=0.4.2\n"
        assertEquals("0.4.1", Release.signedVersion(list, "android"))
        assertEquals("0.4.2", Release.signedVersion(list, "web"))
        assertNull(Release.signedVersion(list, "pc"))
        assertNull(Release.signedVersion("${"a".repeat(64)}  tv-remote.apk\n", "android"))
        assertEquals("a".repeat(64), Release.sum(list, "tv-remote.apk"))
        assertNull(Release.sum(list, "version"))
    }

    @Test fun brief() {                                             // the reader's language only, this app first, first sentences
        val notes = "Web 1 · app 2\n\n**Tiếng Việt**\n- TV Home: nhẹ hơn trên máy yếu. Đo trên FPT.\n  - con\n- App điện thoại: popup cập nhật gọn\n\n**English**\n" +
            "- TV Home: lighter on weak boxes. Measured on FPT.\n  - sub\n- Phone app: a shorter update popup (TiviKit's look)\n- " + "word ".repeat(40) + "\n"
        val en = Release.brief(notes, false, listOf("Phone app"), 2)
        assertEquals(listOf("Phone app: a shorter update popup (TiviKit's look)", "TV Home: lighter on weak boxes"), en.items)
        assertEquals(1, en.more)
        assertTrue(Release.brief(notes, false).items[2].let { it.endsWith("…") && it.length <= 121 })
        assertEquals(listOf("TV Home: nhẹ hơn trên máy yếu", "App điện thoại: popup cập nhật gọn"), Release.brief(notes, true).items)
        assertEquals(listOf("just a line"), Release.brief("just a line", true).items)   // notes without sections
    }
}
