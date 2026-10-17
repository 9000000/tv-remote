package dev.tvremote.core

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.tvremote.core.adb.Mdns

class MdnsTest {
    private fun name(o: ByteArrayOutputStream, n: String) { for (l in n.split('.')) { o.write(l.length); o.write(l.toByteArray()) }; o.write(0) }
    private fun rec(o: ByteArrayOutputStream, n: String, type: Int, data: ByteArray) {
        name(o, n); o.write(type shr 8); o.write(type and 255); o.write(0); o.write(1); o.write(byteArrayOf(0, 0, 0, 120)); o.write(data.size shr 8); o.write(data.size and 255); o.write(data)
    }

    @Test fun queryAsksForUnicastReply() {
        val q = Mdns.query("_adb-tls-connect._tcp.local", 12)
        assertEquals(1, q[5].toInt())
        assertEquals(0x80.toByte(), q[q.size - 2])
        assertEquals(1.toByte(), q[q.size - 1])
    }

    @Test fun answerBecomesAService() {
        val o = ByteArrayOutputStream()
        o.write(byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 0, 0, 3, 0, 0, 0, 0))
        val ptr = ByteArrayOutputStream().also { name(it, "adb-ABC._adb-tls-connect._tcp.local") }.toByteArray()
        rec(o, "_adb-tls-connect._tcp.local", 12, ptr)
        val srv = ByteArrayOutputStream().also { it.write(byteArrayOf(0, 0, 0, 0, 0x85.toByte(), 0x27)); name(it, "tv.local") }.toByteArray()   // port 34087
        rec(o, "adb-ABC._adb-tls-connect._tcp.local", 33, srv)
        rec(o, "tv.local", 1, byteArrayOf(10, 0, 2, 16))
        val c = Mdns.Collector(Mdns.CONNECT)
        c.add(Mdns.parse(o.toByteArray()), "10.0.2.16")
        val s = c.found()
        assertEquals(1, s.size)
        assertEquals("10.0.2.16", s[0].ip)
        assertEquals(34087, s[0].port)
        assertTrue(Mdns.parse(ByteArray(3)).isEmpty())
    }
}
