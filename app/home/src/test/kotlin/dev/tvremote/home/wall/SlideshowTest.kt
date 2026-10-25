package dev.tvremote.home.wall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SlideshowTest {
    private val base = "https://tvremote.pages.dev/home/loop.html"

    @Test fun theAddressNeedsTheSiteTheLoopPageAndAPlainName() {
        val r = Slideshow.ref("$base?s=vn")!!
        assertEquals("vn", r.name); assertEquals(300_000L, r.every)
        assertEquals(60_000L, Slideshow.ref("$base?t=60&s=vn")!!.every)
        assertEquals(5_000L, Slideshow.ref("$base?s=vn&t=0")!!.every)          // too short: the least
        assertEquals(3_600_000L, Slideshow.ref("$base?s=vn&t=999999")!!.every)
        assertEquals(300_000L, Slideshow.ref("$base?s=vn&t=x")!!.every)
        for (bad in listOf("$base?s=VN", "$base?s=a/b", "$base?s=..%2Fx", "$base?s=", "$base", "$base?s=" + "a".repeat(25),
            "http://tvremote.pages.dev/home/loop.html?s=vn", "https://evil.example/home/loop.html?s=vn",
            "https://tvremote.pages.dev.evil.example/home/loop.html?s=vn", "https://evil.tvremote.pages.dev/home/loop.html?s=vn",
            "https://tvremote.pages.dev/other.html?s=vn", "https://tvremote.pages.dev/home/loop.html?i=https%3A%2F%2Ftvremote.pages.dev%2Fa.jpg", "not a url"))
            assertNull(bad, Slideshow.ref(bad))
    }

    @Test fun theListKeepsOnlyPlainJpgsWithWordsAndAtMostMax() {
        val rows = (1..40).joinToString(",") { """{"file":"p$it.jpg","vi":"Vi $it","en":"En $it","credit":"A, CC0"}""" }
        assertEquals(Slideshow.MAX, Slideshow.parse("""{"v":1,"slides":[$rows]}""").size)
        val s = Slideshow.parse("""{"slides":[
            {"file":"a.jpg","vi":"Một","en":"One","credit":"X"},
            {"file":"../a.jpg","vi":"x","en":"x"}, {"file":"https://evil/a.jpg","vi":"x","en":"x"}, {"file":"a.png","vi":"x","en":"x"},
            {"file":"b.jpg"}, {"file":"c.jpg","en":"Only English"}, "junk"]}""")
        assertEquals(listOf("https://tvremote.pages.dev/home/a.jpg", "https://tvremote.pages.dev/home/c.jpg"), s.map { it.pic })
        assertEquals("Only English", s[1].vi)
        assertEquals("Một · One", s[0].line(true)); assertEquals("One · Một", s[0].line(false))
        for (t in listOf("", "not json", "[]", """{"slides":5}""")) assertTrue(Slideshow.parse(t).isEmpty())
    }

    @Test fun theSlideComesFromTheClockSlot() {
        assertEquals(0, Slideshow.index(0, 300_000, 14))
        assertEquals(1, Slideshow.index(300_000, 300_000, 14)); assertEquals(1, Slideshow.index(599_999, 300_000, 14))
        assertEquals(0, Slideshow.index(14 * 300_000L, 300_000, 14)); assertEquals(0, Slideshow.index(5, 300_000, 0))
    }

    @Test fun theSiteListIsAValidSlideshowOfExistingPictures() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "docs/home").isDirectory }
        val s = Slideshow.parse(File(root, "docs/home/vn.json").readText())
        assertTrue(s.size in 10..Slideshow.MAX)
        for (x in s) { assertTrue(x.pic, File(root, "docs/home/" + x.pic.substringAfterLast('/')).isFile); assertTrue(x.credit.isNotEmpty()) }
        assertNotNull(Slideshow.ref(File(root, "docs/home/themes.json").readText().let { Regex("loop\\.html\\?s=vn").find(it)!!.value.let { "https://tvremote.pages.dev/home/$it" } }))
    }
}
