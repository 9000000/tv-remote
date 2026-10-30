package dev.tvremote.core.ontv

import dev.tvremote.home.LinkUrl
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class LinkUrlTest {
    @Test fun `web pages and deep links pass, a bare address gets https`() {
        assertEquals("https://example.com/a?b=1", LinkUrl.clean(" https://example.com/a?b=1 "))
        assertEquals("https://example.com/x", LinkUrl.clean("example.com/x"))
        assertEquals("vtvgo://channel/1", LinkUrl.clean("vtvgo://channel/1"))
        assertEquals("netflix://title/1", LinkUrl.clean("NETFLIX://title/1"))
    }

    @Test fun `schemes that reach hidden screens, files or code are refused`() {
        for (s in listOf("intent://scan/#Intent;scheme=zxing;end", "file:///sdcard/a", "content://media/1", "javascript:alert(1)", "javascript://x%0aalert(1)", "data:text/html,x", "android-app://com.x"))
            assertNull(s, LinkUrl.clean(s))
    }

    @Test fun `not a link at all, or one that hides its host`() {
        for (s in listOf("", "hello", "https://", "https://user@evil.example/x", "https://a.b/c d", "mailto:a@b.c", "https://example.com/" + "x".repeat(600)))
            assertNull(s, LinkUrl.clean(s))
    }
}
