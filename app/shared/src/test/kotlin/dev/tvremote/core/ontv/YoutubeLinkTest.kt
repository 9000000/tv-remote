package dev.tvremote.core.ontv

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class YoutubeLinkTest {
    @Test fun onlyYoutubeLinksReachTheShell() {
        assertEquals("https://youtu.be/abc", Home.youtubeUrl(" https://youtu.be/abc "))
        assertEquals("https://www.youtube.com/@x", Home.youtubeUrl("https://www.youtube.com/@x"))
        assertNull(Home.youtubeUrl("http://youtube.com/watch?v=1"))
        assertNull(Home.youtubeUrl("https://youtube.com.evil.com/x"))
        assertNull(Home.youtubeUrl("https://evil.com/youtube.com"))
        assertNull(Home.youtubeUrl("https://youtube.com@evil.com/x"))
        assertNull(Home.youtubeUrl("https://youtu.be/a b"))
    }

    // measured on an FPT box: stock YouTube turned off, a mod, a downloader and the framework's stub all take the link
    @Test fun aModBeatsTheStubsAndDownloaders() {
        val out = "priority=0 preferredOrder=0 match=0x600000 specificIndex=-1 isDefault=false\ndev.cobalt.youtube/.MainActivity\n" +
            "priority=0 preferredOrder=0 match=0x600000 specificIndex=-1 isDefault=false\ncom.esaba.downloader/.Main\n" +
            "com.android.tv.frameworkpackagestubs/.Stubs\$BrowserStub\n"
        val yt = Home.queryPackages(out)
        assertEquals(listOf("dev.cobalt.youtube", "com.esaba.downloader", "com.android.tv.frameworkpackagestubs"), yt)
        assertEquals("dev.cobalt.youtube", Home.youtubeApp(yt, listOf("com.android.tv.frameworkpackagestubs")))
    }

    @Test fun stockFirstThenPreferredThenNone() {
        val yt = listOf("com.liskovsoft.smarttubetv", "com.google.android.youtube.tv", "org.mozilla.firefox")
        assertEquals("com.google.android.youtube.tv", Home.youtubeApp(yt, listOf("org.mozilla.firefox")))
        assertEquals("com.liskovsoft.smarttubetv", Home.youtubeApp(yt, emptyList(), "com.liskovsoft.smarttubetv"))
        assertNull(Home.youtubeApp(listOf("com.esaba.downloader", "com.android.tv.frameworkpackagestubs"), emptyList()))
    }
}
