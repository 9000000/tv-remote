package dev.tvremote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SameTvTest {
    private val home = SameTv.Tv("100.64.0.7", lan = "192.168.1.5", mac = "AA:BB:CC:00:00:01")

    @Test fun picked_on_wifi_and_over_tailscale() {
        assertTrue(SameTv.same(home, SameTv.Tv("192.168.1.5")))
        assertTrue(SameTv.same(SameTv.Tv("192.168.1.5", ts = "100.64.0.7"), SameTv.Tv("100.64.0.7")))
        assertTrue(SameTv.same(home, SameTv.Tv("192.168.1.9", mac = "aa:bb:cc:00:00:01")))   // a new IP from the router
    }

    @Test fun two_homes_with_the_same_wifi_address() {
        assertFalse(SameTv.same(home, SameTv.Tv("100.64.0.9", lan = "192.168.1.5")))
        assertFalse(SameTv.same(home, SameTv.Tv("192.168.1.5", mac = "AA:BB:CC:00:00:02")))
        assertFalse(SameTv.same(SameTv.Tv(""), SameTv.Tv("")))
    }

    @Test fun groups_keep_the_first_and_its_order() {
        val g = SameTv.groups(listOf(SameTv.Tv("192.168.1.5"), SameTv.Tv("10.0.0.2"), home)) { it }
        assertEquals(listOf("192.168.1.5", "10.0.0.2"), g.map { it.first.host })
        assertEquals(listOf("100.64.0.7"), g[0].second.map { it.host })
    }
}
