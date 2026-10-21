package dev.tvremote.channels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter

class GlobalIndexTest {
    private val m3u = """#EXTM3U
#EXTINF:-1 tvg-id="CartoonNetwork.us@SD" tvg-logo="http://l/cn.png" group-title="United States",Cartoon Network (720p) [Geo-blocked]
http://s/cn1.m3u8
#EXTINF:-1 tvg-id="CartoonNetwork.us@SD" tvg-logo="http://l/cn.png" group-title="United States",Cartoon Network
http://s/cn2.m3u8
#EXTINF:-1 tvg-id="Cartoonito.de@SD" tvg-logo="" group-title="Germany",Cartoonito
http://s/ct.m3u8
#EXTINF:-1 tvg-id="CNBC.us@SD" tvg-logo="" group-title="United States",CNBC
http://s/cnbc.m3u8
#EXTINF:-1 tvg-id="" group-title="Vietnam",Đài Truyền hình Cần Thơ
http://s/ct1.m3u8
"""

    private fun index(): GlobalIndex {
        val w = StringWriter(); GlobalIndex.write(StringReader(m3u).buffered(), w)
        return GlobalIndex.load(StringReader(w.toString()).buffered())
    }

    @Test fun roundTripKeepsFields() {
        val i = index()
        assertEquals(5, i.size)
        val c = i.search("cartoon network", "")[0][0]
        assertEquals("CartoonNetwork.us@SD", c.id); assertEquals("Cartoon Network", c.name); assertEquals("720p", c.quality)
        assertTrue(c.geo); assertEquals("United States", c.group); assertEquals("http://l/cn.png", c.logo); assertEquals("us", c.country)
    }

    @Test fun streamsOfOneIdStayTogether() = assertEquals(2, index().search("cartoon network", "")[0].size)

    @Test fun wordStartsMatch() {
        val names = index().search("cartoon", "").map { it[0].name }
        assertEquals(setOf("Cartoonito", "Cartoon Network"), names.toSet())
        assertEquals("Cartoon Network", names[0])   // the whole word before a longer one
    }

    @Test fun accentsAndCaseAndSpaces() {
        val i = index()
        assertEquals("Đài Truyền hình Cần Thơ", i.search("CAN tho", "")[0][0].name)
        assertEquals("Cartoon Network", i.search("cartoonnetwork", "")[0][0].name)
    }

    @Test fun ownCountryIsSkipped() = assertTrue(index().search("cartoon", "us").map { it[0].country }.none { it == "us" })

    @Test fun capApplies() = assertEquals(1, index().search("cartoon", "", 1).size)

    @Test fun ranks() {
        assertEquals(0, GlobalIndex.rank("cnn", "cnn")); assertEquals(1, GlobalIndex.rank("cnn int", "cnn")); assertEquals(2, GlobalIndex.rank("cnnx", "cnn"))
        assertEquals(3, GlobalIndex.rank("the cartoon network", "cartoon net")); assertEquals(4, GlobalIndex.rank("abcartoon", "cartoon"))
        assertNull(GlobalIndex.rank("cnbc", "cartoon")); assertNull(GlobalIndex.rank("ab", "b"))
    }
}
