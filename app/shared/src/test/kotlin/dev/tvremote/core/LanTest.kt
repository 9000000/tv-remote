package dev.tvremote.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import java.net.URLConnection

/** Which way a TV is dialed with a VPN up: the Wi-Fi under it or the VPN's own, and what is said when neither reaches it. */
class LanTest {
    private val real = Lan.dial
    @After fun back() { Lan.dial = real; Lan.wifi = { null }; Lan.forget() }

    private class Wifi(val subnet: String, val prefix: Int) : Lan.Wifi {
        override fun bind(s: Socket) {}
        override fun onLink(ip: InetAddress) = Lan.inSubnet(ip, InetAddress.getByName(subnet), prefix)
        override fun open(url: URL): URLConnection = throw IOException("not here")
    }

    /** Dials [host] with [works] saying which way answers (true: the Wi-Fi); returns the ways tried, in order. */
    private fun tries(host: String, works: (Boolean) -> Boolean): Pair<List<Boolean>, Exception?> {
        val tried = ArrayList<Boolean>()
        Lan.dial = { _, _, _, w, _ -> tried.add(w != null); if (works(w != null)) Socket() else throw IOException("no route") }
        val e = try { Lan.connect(host, 5555, 100); null } catch (e: Exception) { e }
        return tried to e
    }

    @Test fun homeAddresses() {
        for (h in listOf("192.168.1.20", "10.0.0.5", "172.16.3.4", "172.31.255.1", "169.254.9.9", "fe80::1", "fd7a::5", "[fe80::2]")) assertNotNull(h, Lan.home(h))
        for (h in listOf("100.101.102.103", "172.32.0.1", "8.8.8.8", "living-room.local", "tv", "2001:db8::1", "")) assertNull(h, Lan.home(h))
    }

    @Test fun subnets() {
        val net = InetAddress.getByName("192.168.1.0")
        assertTrue(Lan.inSubnet(InetAddress.getByName("192.168.1.77"), net, 24))
        assertTrue(!Lan.inSubnet(InetAddress.getByName("192.168.2.77"), net, 24))
        assertTrue(Lan.inSubnet(InetAddress.getByName("192.168.2.77"), net, 16))
        assertTrue(Lan.inSubnet(InetAddress.getByName("10.1.2.3"), InetAddress.getByName("10.0.0.0"), 8))
        assertTrue(!Lan.inSubnet(InetAddress.getByName("10.1.2.3"), InetAddress.getByName("fe80::1"), 8))
    }

    @Test fun noVpnIsThePlainWayOnly() {
        val (tried, e) = tries("192.168.1.20") { false }
        assertEquals(listOf(false), tried)
        assertTrue("$e", e !is java.net.ConnectException || !e.message!!.contains(Lan.BLOCKED))
    }

    @Test fun aTvOnTheWifiGoesAroundTheVpnFirst() {
        Lan.wifi = { Wifi("192.168.1.0", 24) }
        assertEquals(listOf(true), tries("192.168.1.20") { it }.first)
    }

    @Test fun anotherHomeNetworkTriesTheVpnFirstThenTheWifi() {
        Lan.wifi = { Wifi("192.168.1.0", 24) }                      // 10.0.0.5: a subnet the VPN may route (Tailscale subnet router)
        assertEquals(listOf(false, true), tries("10.0.0.5") { it }.first)
        assertEquals("what worked is tried first next time", listOf(true), tries("10.0.0.5") { it }.first)
    }

    @Test fun tailscaleAndNamesStayOnTheVpn() {
        Lan.wifi = { Wifi("192.168.1.0", 24) }
        assertEquals(listOf(false), tries("100.101.102.103") { false }.first)
        assertEquals(listOf(false), tries("tv.lan") { false }.first)
    }

    @Test fun neitherWaySaysTheVpnIsInTheWay() {
        Lan.wifi = { Wifi("192.168.1.0", 24) }
        val (tried, e) = tries("192.168.1.20") { false }
        assertEquals(listOf(true, false), tried)
        if (e == null) fail("connected")
        assertTrue(e!!.message!!.contains(Lan.BLOCKED))
        assertTrue(Errors.say(e, false).contains("VPN"))
        assertTrue(Errors.say(e, true).contains("VPN"))
    }
}
