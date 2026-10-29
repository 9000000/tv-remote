package dev.tvremote.channels

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideTest {
    private val tv360 = Providers.Provider("tv360", "tv360", "TV360", "vn", false,
        JSONObject().put("guide", "https://x/{id}").put("channels", JSONObject().put("Cinemax", "239").put("VTV3", "4")))
    private fun ch(id: String, name: String) = Channel(id, name, "", "", "https://x/$id.m3u8")

    @Test fun aNameMatchesOnlyInTheProvidersCountry() {
        assertNull(Guide.source(listOf(tv360), listOf(ch("Cinemax.us", "Cinemax"))))        // the US feed: other films
        assertEquals("239", Guide.source(listOf(tv360), listOf(ch("Cinemax.vn", "Cinemax")))?.second)
        assertEquals("4", Guide.source(listOf(tv360), listOf(ch("n:VTV3 HD", "VTV3 HD")))?.second)   // a person's own list: no country
        assertEquals("239", Guide.source(listOf(tv360), listOf(ch("Cinemax.us", "Cinemax"), ch("n:Cinemax", "Cinemax")))?.second)
    }
}
