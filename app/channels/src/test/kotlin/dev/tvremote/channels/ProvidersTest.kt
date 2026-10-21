package dev.tvremote.channels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProvidersTest {
    private val docs = File("../../docs/channels").takeIf { it.isDirectory } ?: File("../docs/channels")

    @Test fun theSitesListIsSignedAndRead() {
        val list = File(docs, "providers.json").readBytes()
        assertTrue(Providers.verified(list, File(docs, "providers.json.sig").readText()))
        assertFalse(Providers.verified(list + ' '.code.toByte(), File(docs, "providers.json.sig").readText()))   // one byte more: refused
        assertEquals(listOf("tv360", "vieon"), Providers.parse(String(list)).map { it.id })
    }

    @Test fun offAndUnknownKindsAreLeftOut() {
        val l = """{"v":1,"providers":[{"id":"a","kind":"vieon","country":"VN","on":false},{"id":"b","kind":"later","country":"vn"},
            {"id":"c","kind":"vieon","country":"VN"}]}"""
        assertEquals(listOf("c"), Providers.parse(l).map { it.id })
        assertEquals("vn", Providers.parse(l)[0].country)
        assertTrue(Providers.parse("""{"v":2,"providers":[]}""").isEmpty())
        assertTrue(Providers.parse("not json").isEmpty())
    }

    @Test fun namesBothListsAgreeOn() {
        assertEquals(Providers.key("VTV1"), Providers.key("VTV1 HD"))
        assertEquals(Providers.key("Vinh Long TV 1"), Providers.key("Vĩnh Long 1 HD"))
        assertEquals(Providers.key("Đồng Tháp 1"), Providers.key("Dong Thap TV 1"))
        assertFalse(Providers.key("VTV1") == Providers.key("VTV10"))
    }

    @Test fun links() {
        assertTrue(Providers.isLink("vieon:vtv1-hd"))
        assertFalse(Providers.isLink("https://x/a.m3u8"))
        assertFalse(Providers.isLink("http://x/a.m3u8"))
    }

    private val p = Providers.parse(String(File(docs, "providers.json").readBytes())).first { it.kind == "vieon" }

    @Test fun vieonList() {
        val l = Vieon.parseList("""{"items":[{"slug":"vtv1-hd","title":"VTV1 HD","is_premium":0,"images":{"livetv_logo_light":"https://l/1.png"}},
            {"slug":"vtv3-hd","title":"VTV3 HD","is_premium":1},{"slug":"voh-fm-999","title":"VOH FM 99.9 MHz","is_premium":0,"image_link":"https://l/2.png"},
            {"slug":"","title":"x"}]}""", p)!!
        assertEquals(listOf("vtv1-hd.vn@vieon", "voh-fm-999.vn@vieon"), l.map { it.id })
        assertEquals("vn", l[0].country)                                // its country's list finds it again (links, favourites)
        assertEquals("vieon:vtv1-hd", l[0].url)
        assertEquals("https://l/1.png", l[0].logo)
        assertEquals("Music", l[1].group)
        assertNull(Vieon.parseList("oops", p))
    }

    @Test fun vieonPlay() {
        val now = 1_792_600_000_000L
        val url = "https://cdn.vieon.vn/0123456789abcdef0123456789abcdef/${now + 20 * 3600_000L}/livetv/x/playlist.m3u8?c=a"
        val got = Vieon.parsePlay("""{"id":"x","permission":206,"play_links":{"h264":{"hls":"$url"},"h265":{"hls":"https://h/265.m3u8"}}}""", 206, now)
        assertNotNull(got)
        assertEquals(url, got!!.url)
        assertEquals(now + 6 * 3600_000L, got.until)                    // a day's link: kept at most 6 hours
        val soon = "https://cdn.vieon.vn/0123456789abcdef0123456789abcdef/${now + 2 * 3600_000L}/p.m3u8"
        assertEquals(now + 3600_000L, Vieon.parsePlay("""{"permission":206,"hls_link_play":"$soon"}""", 206, now)!!.until)   // an hour before it runs out
        assertNull(Vieon.parsePlay("""{"id":"x","permission":0,"link_play":""}""", 206, now))   // needs an account
        assertNull(Vieon.parsePlay("<html>", 206, now))
    }

    @Test fun guides() {
        val shows = Tv360.parseGuide("""{"data":{"schedules":[{"name":"Thời sự","epochSt":1000,"epochEt":2000},{"name":"Phim","epochSt":2000,"epochEt":3000},{"name":"","epochSt":3000,"epochEt":4000}]}}""")!!
        assertEquals(listOf("Thời sự", "Phim"), shows.map { it.title })
        assertEquals("Thời sự", Guide.now(shows, 1500)!!.title)
        assertEquals("Phim", Guide.after(shows, 1500)!!.title)
        assertNull(Guide.now(shows, 5000))
        assertEquals(emptyList<Show>(), Tv360.parseGuide("""{"data":{"info":[]}}"""))
        val v = Vieon.parseGuide("""[{"title":"Phim truyện","time_start":1792515600,"time_end":1792517400}]""")!!
        assertEquals(1792515600_000L, v[0].start)                       // seconds there, ms here
        assertNull(Vieon.parseGuide("{}"))
    }

    @Test fun guideIdsFromTheSitesList() {
        val ps = Providers.parse(File(docs, "providers.json").readText())
        val tv360 = ps.first { it.kind == "tv360" }
        assertEquals("4", tv360.guides[Providers.key("VTV3 HD")])        // iptv-org's name finds TV360's id
        assertTrue(tv360.guides.size > 100)
        assertTrue(ps.first { it.kind == "vieon" }.o.optString("guide").contains("{id}"))
        assertTrue(Regex("\\d{4}-\\d{2}-\\d{2}").matches(Guide.day(tv360, 1)))
    }
}
