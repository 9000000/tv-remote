package dev.tvremote.karaoke

import org.junit.Assert.*
import org.junit.Test

private fun res(n: String) = SearchTest::class.java.classLoader!!.getResource(n)!!.readText()

class SearchTest {
    @Test fun apiResultsKeepOnlyVideosAndUnescape() {
        val r = SearchParse.api(res("api_ok.json"))
        assertNull(r.error); assertFalse(r.quota)
        assertEquals(listOf("ekFIGh0cbRU", "naG3fboU4b0"), r.songs.map { it.vid })       // the channel row is dropped
        assertEquals("Karaoke \"Đế Vương\" & beat 'chuẩn'", r.songs[0].title)
        assertEquals("Karaoke Minh Nha", r.songs[0].channel)
        assertEquals("https://i.ytimg.com/vi/ekFIGh0cbRU/mqdefault.jpg", r.songs[0].thumb)
    }

    @Test fun apiQuotaAndWrongKey() {
        val q = SearchParse.api(res("api_quota.json")); assertTrue(q.quota); assertEquals("quotaExceeded", q.error); assertTrue(q.songs.isEmpty())
        val k = SearchParse.api(res("api_badkey.json")); assertFalse(k.quota); assertNotNull(k.error)
        assertNotNull(SearchParse.api("<html>not json").error)
    }

    @Test fun innerTubeVideosInOrderSkippingLiveAndBroken() {
        val s = SearchParse.innerTube(res("innertube.json"))
        assertEquals(listOf("ekFIGh0cbRU", "naG3fboU4b0", "abcdEFGH123"), s.take(2).map { it.vid } + s.last().vid)
        assertFalse(s.any { it.vid == "LIVE0000001" })                                   // a live stream has no length
        assertEquals("Karaoke Đế Vương - Đình Dũng-Beat chuẩn", s[0].title)
        assertEquals("Karaoke Minh Nha", s[0].channel); assertEquals("4:19", s[0].length)
        assertEquals("Lockup & style song", s.last().title)                              // the newer card shape
    }

    @Test fun htmlPageWithInitialData() {
        val s = SearchParse.html(res("results.html"))
        assertEquals(SearchParse.innerTube(res("innertube.json")).map { it.vid }, s.map { it.vid })
    }

    @Test fun nonsenseGivesNothingNotACrash() {
        assertTrue(SearchParse.innerTube("").isEmpty()); assertTrue(SearchParse.innerTube("[1,2]").isEmpty()); assertTrue(SearchParse.innerTube("{\"a\":{\"videoRenderer\":5}}").isEmpty())
        assertTrue(SearchParse.html("<html>no data").isEmpty()); assertTrue(SearchParse.html("ytInitialData = {\"a\":").isEmpty()); assertTrue(SearchParse.html("ytInitialData").isEmpty())
    }

    @Test fun unescapeNumbers() { assertEquals("a & b < c é A", SearchParse.unescape("a &amp; b &lt; c &#233; &#x41;")); assertEquals("&#zz;", SearchParse.unescape("&#zz;")) }

    // ---- which source answers ----
    private val web = res("innertube.json")

    @Test fun keyFirstThenNothingElse() {
        val urls = ArrayList<String>()
        val s = Searcher { u, _ -> urls.add(u); res("api_ok.json") }.search("đế vương", "KEY1")
        assertEquals("api", s.source); assertEquals(2, s.songs.size)
        assertEquals(1, urls.size); assertTrue(urls[0].contains("videoEmbeddable=true") && urls[0].contains("type=video") && urls[0].contains("key=KEY1"))
        assertTrue(urls[0].contains("%C4%91%E1%BA%BF+v%C6%B0%C6%A1ng+karaoke"))          // the query + " karaoke"
    }

    @Test fun quotaFallsBackToTheWebAndIsCounted() {
        val se = Searcher { u, _ -> if (u.contains("googleapis")) res("api_quota.json") else web }
        val s = se.search("x", "KEY"); assertEquals("web", s.source); assertEquals("quota", s.note); assertFalse(s.error); assertEquals(1, se.quotaHits); assertTrue(s.songs.isNotEmpty())
        se.search("y", "KEY"); assertEquals(2, se.quotaHits)
    }

    @Test fun wrongKeyOrNoNetworkToGoogleFallsBack() {
        assertEquals("key", Searcher { u, _ -> if (u.contains("googleapis")) res("api_badkey.json") else web }.search("x", "bad").note)
        assertEquals("net", Searcher { u, _ -> if (u.contains("googleapis")) null else web }.search("x", "k").note)
    }

    @Test fun noKeyGoesStraightToTheWebAndInnerTubeThenHtml() {
        val urls = ArrayList<String>()
        val a = Searcher { u, _ -> urls.add(u); web }.search("x", ""); assertEquals("web", a.source); assertEquals(1, urls.size); assertTrue(urls[0].contains("youtubei/v1/search"))
        urls.clear()
        val b = Searcher { u, _ -> urls.add(u); if (u.contains("youtubei")) "{}" else res("results.html") }.search("x", "")
        assertEquals(2, urls.size); assertTrue(b.songs.isNotEmpty())                    // InnerTube gave nothing: the page was read
    }

    @Test fun everythingFailsIsAnErrorNotAnEmptyAnswer() {
        val s = Searcher { _, _ -> null }.search("x", ""); assertTrue(s.error); assertTrue(s.songs.isEmpty())
        assertFalse(Searcher { _, _ -> null }.search("   ", "").error)                   // an empty query is not an error
    }
}
