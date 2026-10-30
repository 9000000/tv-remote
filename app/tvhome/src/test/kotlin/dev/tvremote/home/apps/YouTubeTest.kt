package dev.tvremote.home.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeTest {
    private fun url(s: String) = YouTube.parse(s)?.url
    private val V = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    @Test fun videosInEveryForm() {
        assertEquals(V, url("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s&list=PLabcdefghij"))
        assertEquals(V, url("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals(V, url("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(V, url("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(V, url("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals(V, url("https://www.youtube.com/live/dQw4w9WgXcQ?feature=share"))
        assertEquals(V, url("youtube.com/watch?v=dQw4w9WgXcQ"))             // no scheme: https is added
        assertEquals(V, url("  http://youtu.be/dQw4w9WgXcQ  "))
    }

    @Test fun playlistsAndChannels() {
        assertEquals("https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI", url("https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI&si=x"))
        assertEquals("https://www.youtube.com/@VTV24", url("https://www.youtube.com/@VTV24/videos"))
        assertEquals("https://www.youtube.com/@VTV24", url("https://m.youtube.com/@VTV24?si=1"))
        assertEquals("https://www.youtube.com/channel/UCabsTV34JwALXKGMqHpvUiA", url("https://www.youtube.com/channel/UCabsTV34JwALXKGMqHpvUiA/featured"))
        assertEquals("https://www.youtube.com/c/Vtv24", url("https://www.youtube.com/c/Vtv24"))
        assertEquals("https://www.youtube.com/user/somebody", url("https://www.youtube.com/user/somebody"))
        assertEquals(YouTube.Kind.CHANNEL, YouTube.parse("https://www.youtube.com/@VTV24")!!.kind)
        assertEquals(YouTube.Kind.PLAYLIST, YouTube.parse("https://www.youtube.com/watch?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI")!!.kind)
    }

    @Test fun notYouTubeOrNotAPlace() {
        assertNull(url("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(url("https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ"))
        assertNull(url("https://www.youtube.com/"))
        assertNull(url("https://www.youtube.com/watch?v=short"))
        assertNull(url("https://www.youtube.com/channel/notachannel"))
        assertNull(url("intent://www.youtube.com/watch?v=dQw4w9WgXcQ#Intent;end"))
        assertNull(url("javascript:alert(1)"))
        assertNull(url("https://www.youtube.com/watch?v=dQw4w9WgXcQ extra"))
        assertNull(url(""))
    }

    @Test fun channelPageTags() {
        val (t, i) = YouTube.og("""<meta property="og:title" content="VTV24 &amp; Co"><meta property="og:image" content="https://yt3.googleusercontent.com/x=s900-c-k-c0x00ffffff-no-rj">""")
        assertEquals("VTV24 & Co", t); assertEquals("https://yt3.googleusercontent.com/x=s400-c-k-c0x00ffffff-no-rj", i)
    }
}
