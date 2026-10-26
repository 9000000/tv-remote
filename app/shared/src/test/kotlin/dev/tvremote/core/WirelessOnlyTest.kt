package dev.tvremote.core

import dev.tvremote.core.adb.AdbTls
import dev.tvremote.core.adb.Mdns
import dev.tvremote.core.remote.Remote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A TV with no port 5555 that only announces Wireless debugging (cleanyourtv #101, #24): found by mDNS, not "none answer". */
class WirelessOnlyTest {
    /** An mDNS browser that has these services on the network. */
    private fun net(vararg s: Triple<String, String, Int>): (String) -> List<Mdns.Service> =
        { type -> s.filter { it.first == type }.map { Mdns.Service("adb-tv", it.second, it.third) } }

    @Test fun wirelessIsReadFromBothServicesOfThatTvOnly() {
        val find = net(Triple(Mdns.CONNECT, "192.168.1.40", 44119), Triple(Mdns.PAIRING, "192.168.1.40", 37501), Triple(Mdns.CONNECT, "192.168.1.99", 1111))
        val w = AdbTls.wireless("192.168.1.40", 100, {}, find)
        assertEquals(44119, w.connect)
        assertEquals(37501, w.pairing)
        assertTrue(w.any)
        val other = AdbTls.wireless("192.168.1.50", 100, {}, find)
        assertFalse(other.any)
        val onlyPairing = AdbTls.wireless("192.168.1.40", 100, {}, net(Triple(Mdns.PAIRING, "192.168.1.40", 37501)))
        assertEquals(0, onlyPairing.connect)
        assertTrue(onlyPairing.any)                                                       // the code screen is open: pairing is next
    }

    @Test fun detectCallsItAnAndroidTvWhenOnlyWirelessAnswers() {
        // 192.0.2.1 (TEST-NET) answers on no TV port
        assertEquals(Remote.Kind.ANDROID, Remote.Kind.detect("192.0.2.1", 300) { true })
        assertNull(Remote.Kind.detect("192.0.2.1", 300) { false })                       // nothing at all: still "none answer"
    }

    @Test fun diagnoseSteersToPairing() {
        val ports = Diagnose.ports(Remote.Kind.ANDROID)
        fun facts(open: List<Int>, wireless: Boolean, brand: String = "") = Diagnose.Facts(Remote.Kind.ANDROID, "192.168.1.40",
            ports.associateWith { if (it in open) Diagnose.Port.OPEN else Diagnose.Port.REFUSED }, Diagnose.Net.OK, false, null, wireless = wireless, brand = brand)
        assertEquals(Diagnose.WIRELESS, Diagnose.decide(facts(emptyList(), true)))        // 5555 refused: "alive" alone would say the TV dozes
        assertEquals(Diagnose.CLOSED, Diagnose.decide(facts(emptyList(), false)))
        assertEquals(Diagnose.WIRELESS, Diagnose.decide(facts(listOf(6466, 6467), true))) // the keys work, ADB is wireless only
        assertEquals(Diagnose.ADB_OFF, Diagnose.decide(facts(listOf(6466, 6467), false)))
        assertEquals(Diagnose.UP, Diagnose.decide(facts(listOf(5555, 6466), true)))       // 5555 answers: the usual way
        assertEquals(listOf("wireless", "retry"), Diagnose.actions(Diagnose.WIRELESS))
    }

    @Test fun tclGetsTheHiddenSwitchWords() {
        val tclWords = Words.get("en", "setup.tcl_hidden_adb")
        assertTrue(tclWords, tclWords.contains("Up, Down, Left, Right"))
        fun fix(code: String, brand: String, lang: String = "en") =
            Diagnose.say(code, Diagnose.Facts(Remote.Kind.ANDROID, "192.168.1.40", emptyMap(), Diagnose.Net.OK, false, null, brand = brand), lang).second
        for (code in listOf(Diagnose.WIRELESS, Diagnose.ADB_OFF)) {
            assertTrue(fix(code, "TCL 98C12L").contains(tclWords))                        // a TCL
            assertTrue(fix(code, "Living room").contains(tclWords))                       // unknown maker: a short extra hint
            assertFalse(fix(code, "Sony Bravia").contains(tclWords))                      // a known other maker: left out
            assertFalse(fix(code, "Xiaomi Mi TV Stick").contains(tclWords))
            assertTrue(fix(code, "TCL", "vi").contains(Words.get("vi", "setup.tcl_hidden_adb")))
        }
        assertFalse(Diagnose.say(Diagnose.OFF, Diagnose.Facts(Remote.Kind.ANDROID, "x", emptyMap(), Diagnose.Net.OK, false, null, brand = "TCL"), "en").second.contains(tclWords))
    }

    /** The whole run: the probe says Wireless debugging is on, the maker is a TCL, 5555 is shut. */
    private class Fake(val wireless: Boolean, val maker: String = "") : Diagnose.Probe {
        override fun port(host: String, port: Int, ms: Int) = if (port == 6466) Diagnose.Port.OPEN else Diagnose.Port.REFUSED
        override fun net(host: String) = Diagnose.Net.OK
        override fun vpn() = false
        override fun adb(host: String): String? = null
        override fun find(kind: String, name: String, not: String): Diagnose.Found? = null
        override fun wireless(host: String) = wireless
        override fun maker(host: String) = maker
    }

    @Test fun runCarriesItToTheJson() {
        val r = Diagnose.run(Fake(true, "TCL 98C12L"), "192.168.1.40", Remote.Kind.ANDROID, "", "refused", "en")
        assertEquals(Diagnose.WIRELESS, r.code)
        val j = Diagnose.json(r, "en")
        assertEquals("wireless", j.getString("code"))
        assertEquals("wireless", j.getJSONArray("actions").getString(0))
        assertTrue(j.getString("fix").contains("Up, Down, Left, Right"))
        assertTrue((0 until j.getJSONArray("checks").length()).any { j.getJSONArray("checks").getJSONObject(it).getString("id") == "wireless" })
        assertEquals(Diagnose.ADB_OFF, Diagnose.run(Fake(false), "192.168.1.40", Remote.Kind.ANDROID, "", "", "en").code)
        assertEquals(Diagnose.CLOSED, Diagnose.run(Fake(true), "192.168.1.40", Remote.Kind.ROKU, "", "", "en").code)   // not an Android TV: never asked about Wireless debugging
    }

    @Test fun loopbackIsTheTvItself() {                                                 // TV Setup dials 127.0.0.1; mDNS names its Wi-Fi address
        val own = java.net.NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .mapNotNull { it.hostAddress?.substringBefore('%') }.firstOrNull { !it.startsWith("127.") && !it.contains(':') }
        if (own != null) assertTrue(AdbTls.same("127.0.0.1", own))                     // a machine with no network has no own address
        assertTrue(AdbTls.same("192.168.1.40", "192.168.1.40"))
        assertFalse(AdbTls.same("192.168.1.40", "10.9.9.9"))   // another TV: only the same address
        assertFalse(AdbTls.same("127.0.0.1", "203.0.113.7"))
    }
}
