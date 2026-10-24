package dev.tvremote.home.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsTest {
    private fun feed(n: String) = News.parse(javaClass.getResourceAsStream("/info/$n")!!, n.substringBefore('.'))

    @Test fun everyDefaultFeedReads() {
        for (n in listOf("vnexpress.rss", "tuoitre.rss", "thanhnien.rss", "bbc.rss")) {
            val f = feed(n)!!
            assertTrue("$n has items", f.items.isNotEmpty())
            for (i in f.items) {
                assertTrue("$n title", i.title.isNotEmpty() && !i.title.contains("<"))
                assertTrue("$n link", i.link.startsWith("http"))
                assertTrue("$n summary has no markup", !i.summary.contains("<img") && !i.summary.contains("&nbsp;"))
                assertTrue("$n date", i.at > 0)
            }
        }
    }

    @Test fun pictureFromTheEnclosureOrTheDescription() {
        assertTrue(feed("vnexpress.rss")!!.items.any { it.image.startsWith("http") })
    }

    @Test fun plainTextAndEntities() {
        assertEquals("Tom & Jerry's “day” ok", News.plain("<p>Tom &amp; Jerry&#39;s &ldquo;day&rdquo;</p><br/> ok"))
        assertEquals("A B", News.plain("A&nbsp;&nbsp;B"))
    }

    @Test fun datesInTheShapesFeedsUse() {
        val rfc = News.date("Sun, 25 Oct 2026 04:30:01 +0700")
        assertEquals(rfc, News.date("Sat, 24 Oct 2026 21:30:01 GMT"))
        assertEquals(rfc, News.date("2026-10-25T04:30:01+07:00"))
        assertEquals(rfc - 1000, News.date("10/25/2026 4:30:00 AM"))     // no zone: Vietnam's
        assertEquals(0L, News.date("yesterday"))
    }

    @Test fun mergeKeepsNewestOncePerLink() {
        fun it(l: String, at: Long) = News.Item(l, "http://x/$l", "", "", at, "s")
        val m = News.merge(listOf(listOf(it("a", 1), it("b", 5)), listOf(it("b", 5), it("c", 3))))
        assertEquals(listOf("b", "c", "a"), m.map { it.title })
    }

    @Test fun anAtomFeedAndABrokenOne() {
        val atom = "<feed><title>T</title><entry><title>One</title><link rel=\"alternate\" href=\"https://e.x/1\"/><updated>2026-10-25T01:00:00Z</updated><summary>S</summary></entry></feed>"
        val f = News.parse(atom.byteInputStream(), "e")!!
        assertEquals("One", f.items.single().title); assertEquals("https://e.x/1", f.items.single().link)
        assertNull(News.parse("not xml at all".byteInputStream(), "e"))
    }

    @Test fun aDoctypeIsNotFollowed() {
        val evil = "<!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><rss><channel><title>&x;</title></channel></rss>"
        val f = News.parse(evil.byteInputStream(), "e")
        assertTrue(f == null || f.items.isEmpty())
    }

    @Test fun urlsByChoice() {
        val all = Sources.All(1, null, emptyList(), null, listOf(Sources.Feed("vn", "VN", "vi", "https://a/vn"), Sources.Feed("en", "EN", "en", "https://a/en")))
        assertEquals(listOf("https://a/vn"), News.urls("auto", "vi", listOf("https://m.x/r"), all).map { it.second })
        assertEquals(listOf("https://a/en"), News.urls("auto", "fr", emptyList(), all).map { it.second })
        assertEquals(listOf("https://m.x/r"), News.urls("mine", "vi", listOf("https://m.x/r"), all).map { it.second })
        assertEquals(listOf("https://m.x/r", "https://a/vn"), News.urls("both", "vi", listOf("https://m.x/r"), all).map { it.second })
        assertNotNull(News.urls("mine", "vi", listOf("https://www.m.x/r"), all).single().first.takeIf { it == "m.x" })
    }
}
