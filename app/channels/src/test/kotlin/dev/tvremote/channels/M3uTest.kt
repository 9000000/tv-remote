package dev.tvremote.channels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uTest {
    private val LIST = """
        #EXTM3U
        #EXTINF:-1 tvg-id="AnGiangTV1.vn@SD" tvg-logo="https://i.imgur.com/mgp6RAU.png" group-title="General",An Giang TV 1 (1080p) [Geo-blocked]
        https://tv.angiangtv.vn/live/kgtv/kgtv.m3u8
        #EXTINF:-1 tvg-id="DaNangTV1.vn@SD" tvg-logo="" group-title="News;General",Da Nang TV 1 (1080p) [Not 24/7]
        #EXTVLCOPT:http-referrer=https://danangtv.vn/
        #EXTVLCOPT:http-user-agent=Mozilla/5.0 (Web0S)
        http://example.org/dn1.m3u8
        #EXTINF:-1 tvg-id="" tvg-logo="" group-title="Undefined",Kênh, có dấu phẩy
        https://example.org/x.m3u8
        #EXTINF:-1 tvg-id="Adult.us" group-title="XXX",Nope
        https://example.org/xxx.m3u8
        #EXTINF:-1 tvg-id="Dup.vn",Dup
        https://example.org/d.m3u8
        #EXTINF:-1 tvg-id="Dup.vn",Dup
        https://example.org/d.m3u8
        #EXTINF:-1 tvg-id="Rtmp.vn",Not http
        rtmp://example.org/live
    """.trimIndent()

    @Test fun parsesNamesLabelsAndHeaders() {
        val l = M3u.parse(LIST)
        assertEquals(listOf("AnGiangTV1.vn@SD", "DaNangTV1.vn@SD", "n:Kênh, có dấu phẩy", "Dup.vn"), l.map { it.id })
        val ag = l[0]
        assertEquals("An Giang TV 1", ag.name); assertEquals("1080p", ag.quality); assertTrue(ag.geo); assertFalse(ag.notAlways)
        assertEquals("General", ag.group); assertEquals("vn", ag.country)
        val dn = l[1]
        assertTrue(dn.notAlways); assertEquals("News", dn.group)
        assertEquals("https://danangtv.vn/", dn.referrer); assertEquals("Mozilla/5.0 (Web0S)", dn.agent)
        assertEquals("Undefined", l[2].group)
    }

    @Test fun country() {
        assertEquals("vn", Ids.country("HTV7.vn@SD"))
        assertEquals("us", Ids.country("CNN.us"))
        assertEquals("", Ids.country("n:Foo"))
    }
}
