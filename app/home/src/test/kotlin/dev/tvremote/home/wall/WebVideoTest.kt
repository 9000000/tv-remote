package dev.tvremote.home.wall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebVideoTest {
    @Test fun directMedia() {
        assertEquals("https://a.b/x/v.mp4?t=1", WebVideo.direct("https://a.b/x/v.mp4?t=1"))
        assertEquals("http://a.b/live/index.M3U8", WebVideo.direct("http://a.b/live/index.M3U8"))
        assertNull(WebVideo.direct("https://a.b/page.html"))
        assertNull(WebVideo.direct("ftp://a.b/v.mp4"))
    }

    @Test fun loopPage() {
        val v = "https://upload.wikimedia.org/a/b/c.webm"
        val enc = java.net.URLEncoder.encode(v, "UTF-8")
        assertEquals(v, WebVideo.direct("https://tvremote.pages.dev/home/loop.html?v=$enc&p=x"))
        assertNull(WebVideo.direct("https://tvremote.pages.dev/home/loop.html?v=" + java.net.URLEncoder.encode("https://evil.example/c.webm", "UTF-8")))
        assertNull(WebVideo.direct("https://tvremote.pages.dev/home/loop.html?i=$enc"))
    }

    @Test fun htmlWithOneVideo() {
        val base = "https://h.example/dir/page.html"
        assertEquals("https://h.example/dir/a.mp4", WebVideo.fromHtml("<body><video autoplay loop src=\"a.mp4\"></video></body>", base))
        assertEquals("https://c.example/b.webm?x=1&y=2", WebVideo.fromHtml("<video><source src='https://c.example/b.webm?x=1&amp;y=2'></video>", base))
    }

    @Test fun htmlThatIsMore() {
        val base = "https://h.example/"
        assertNull(WebVideo.fromHtml("<video src=a.mp4></video><video src=b.mp4></video>", base))
        assertNull(WebVideo.fromHtml("<video src=a.mp4></video><img src=x.png>", base))
        assertNull(WebVideo.fromHtml("<script src=app.js></script><video src=a.mp4></video>", base))
        assertNull(WebVideo.fromHtml("<script>" + "x".repeat(3000) + "</script><video src=a.mp4></video>", base))
        assertNull(WebVideo.fromHtml("<p>no video</p>", base))
        assertNull(WebVideo.fromHtml("<video id=v></video>", base))
    }
}
