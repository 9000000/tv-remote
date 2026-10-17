package dev.tvremote.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.Random
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import dev.tvremote.core.adb.Adb
import dev.tvremote.core.adb.Mdns
import dev.tvremote.core.adb.Spake2
import dev.tvremote.core.remote.Atv
import dev.tvremote.core.remote.Inputs
import dev.tvremote.core.remote.MqttLite
import dev.tvremote.core.remote.Roku
import dev.tvremote.core.remote.Ssdp
import dev.tvremote.core.remote.WebSocket

/**
 * What a TV (or anyone on the Wi-Fi) sends is fed, broken in a few thousand seeded ways, to every parser that reads it. Broken
 * input may end in an IOException (or the parser's own documented error) or null; never an index / size / number error that
 * would end a reader thread (and the app with it on Android), never an allocation sized by a length it was told, never a hang.
 */
class ParserFuzzTest {
    private val r = Random(20261018)

    /** One of: bits flipped, cut short, bytes inserted, a 4-byte run set to an extreme, or random bytes appended. */
    private fun mutate(src: ByteArray): ByteArray {
        var b = src.copyOf()
        repeat(1 + r.nextInt(3)) {
            when (r.nextInt(6)) {
                0 -> if (b.isNotEmpty()) { val i = r.nextInt(b.size); b[i] = (b[i].toInt() xor (1 shl r.nextInt(8))).toByte() }
                1 -> b = b.copyOf(r.nextInt(b.size + 1))
                2 -> { val i = r.nextInt(b.size + 1); val add = ByteArray(1 + r.nextInt(8)).also(r::nextBytes); b = b.copyOf(i) + add + b.copyOfRange(i, b.size) }
                3 -> if (b.size >= 4) { val i = r.nextInt(b.size - 3); val v = listOf(-1, Int.MAX_VALUE, Int.MIN_VALUE, 0x7f7f7f7f, 0)[r.nextInt(5)]; for (k in 0..3) b[i + k] = (v shr (8 * k)).toByte() }
                4 -> b += ByteArray(r.nextInt(16)).also(r::nextBytes)
                else -> if (b.isNotEmpty()) b[r.nextInt(b.size)] = listOf(0xff, 0x80, 0x7f, 0, 0x7e)[r.nextInt(5)].toByte()
            }
        }
        return b
    }

    private fun random(n: Int) = ByteArray(r.nextInt(n + 1)).also(r::nextBytes)

    /** Runs [f]; only [allowed] may come out of it. */
    private fun only(what: String, input: ByteArray, vararg allowed: Class<out Throwable>, f: () -> Unit) {
        try { f() } catch (t: Throwable) {
            if (allowed.none { it.isInstance(t) }) throw AssertionError("$what threw $t on ${input.take(64).joinToString(" ") { "%02x".format(it) }}", t)
        }
    }

    private class FakeSocket(private val inp: InputStream) : Socket() {
        val out = ByteArrayOutputStream()
        override fun getInputStream() = inp
        override fun getOutputStream(): OutputStream = out
    }

    // ------------------------------------------------------------ WebSocket (LG, Samsung)

    private fun ws(inp: InputStream): WebSocket =
        WebSocket::class.java.getDeclaredConstructor(Socket::class.java).apply { isAccessible = true }.newInstance(FakeSocket(inp))

    private fun wsFrame(op: Int, body: ByteArray, fin: Boolean = true, mask: Boolean = false): ByteArray {
        val o = ByteArrayOutputStream()
        o.write((if (fin) 0x80 else 0) or op)
        val m = if (mask) 0x80 else 0
        when {
            body.size < 126 -> o.write(m or body.size)
            body.size < 65536 -> { o.write(m or 126); o.write(body.size shr 8); o.write(body.size and 0xff) }
            else -> { o.write(m or 127); for (i in 7 downTo 0) o.write(((body.size.toLong() shr (8 * i)) and 0xff).toInt()) }
        }
        if (mask) o.write(byteArrayOf(1, 2, 3, 4))
        o.write(body)
        return o.toByteArray()
    }

    @Test fun webSocketFrames() {
        val text = """{"id":"r1","type":"response","payload":{"returnValue":true}}""".toByteArray()
        val seeds = listOf(
            wsFrame(1, text), wsFrame(1, text, mask = true), wsFrame(9, "hi".toByteArray()) + wsFrame(1, text),
            wsFrame(1, text.copyOf(10), fin = false) + wsFrame(0, text.copyOfRange(10, text.size)),
            wsFrame(1, ByteArray(300) { 'a'.code.toByte() }), wsFrame(1, ByteArray(70000) { 'b'.code.toByte() }), wsFrame(8, ByteArray(0)))
        repeat(3000) {
            val b = if (it % 10 == 0) random(64) else mutate(seeds[r.nextInt(seeds.size)])
            only("WebSocket.read", b, IOException::class.java) { val s = ws(ByteArrayInputStream(b)); var n = 0; while (s.read() != null && n++ < 100) { } }
        }
    }

    @Test fun webSocketNegativeLengthIsRefused() {                   // the 8-byte length with its top bit set: was NegativeArraySizeException
        val b = byteArrayOf(0x81.toByte(), 127) + ByteArray(8) { 0xff.toByte() }
        try { ws(ByteArrayInputStream(b)).read(); fail() } catch (e: IOException) { }
    }

    @Test fun webSocketEndlessFragmentsAreRefused() {                // continuations of 1 MB each, never final: was kept growing
        val one = wsFrame(0, ByteArray(1 shl 20), fin = false)
        val endless = object : InputStream() {
            var at = 0L
            override fun read(): Int = if (at++ == 0L) 0x01 else one[((at - 1) % one.size).toInt()].toInt() and 0xff
        }
        try { ws(endless.buffered()).read(); fail() } catch (e: IOException) { }
    }

    // ------------------------------------------------------------ Android TV Remote v2 (Atv)

    private fun pb(vararg fields: Pair<Int, Any>): ByteArray {
        val p = Atv.Pb()
        for ((f, v) in fields) if (v is Long) p.int(f, v) else p.msg(f, v as ByteArray)
        return p.bytes()
    }

    @Test fun atvMessages() {
        val seeds = listOf(
            pb(1 to pb(1 to 622L, 2 to pb(1 to "SHIELD".toByteArray(), 2 to "NVIDIA".toByteArray()))),
            pb(8 to pb(1 to 12345L)), pb(50 to pb(6 to 100L, 7 to 15L, 8 to 0L)), pb(20 to pb(1 to pb(12 to "com.netflix".toByteArray()))),
            pb(40 to pb(1 to 1L)), pb(21 to pb(1 to 3L, 2 to 7L)))
        repeat(4000) {
            val b = if (it % 10 == 0) random(48) else mutate(seeds[r.nextInt(seeds.size)])
            only("Atv.Pb.read", b, IOException::class.java) {
                for (vs in Atv.Pb.read(b).values) for (v in vs) if (v is ByteArray) try { Atv.Pb.read(v) } catch (e: IOException) { }
            }
            val framed = ByteArrayOutputStream().also { Atv.Pb.varint(it, b.size.toLong()); it.write(b) }.toByteArray()
            val f = mutate(framed)
            only("Atv.frame", f, IOException::class.java) { Atv.frame(ByteArrayInputStream(f)) }
        }
    }

    @Test fun atvBrokenLengthsAreIOExceptions() {
        for (b in listOf(byteArrayOf(0x0a, 0x7f), byteArrayOf(0x08, 0x80.toByte()), byteArrayOf(0x0a) + ByteArray(10) { 0xff.toByte() } + 1, byteArrayOf(0x09, 1, 2)))
            try { Atv.Pb.read(b); fail(b.joinToString()) } catch (e: IOException) { }   // were ArrayIndexOutOfBounds / IllegalArgument
        for (b in listOf(ByteArray(5) { 0xff.toByte() }, byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x7f)))
            try { Atv.frame(ByteArrayInputStream(b)); fail(b.joinToString()) } catch (e: IOException) { }   // were NegativeArraySize / 256 MB
        assertEquals(3, Atv.frame(ByteArrayInputStream(byteArrayOf(3, 1, 2, 3)))!!.size)
    }

    // ------------------------------------------------------------ ADB

    private fun adbPacket(cmd: Int, arg0: Int, arg1: Int, data: ByteArray): ByteArray =
        ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.size).putInt(data.sumOf { it.toInt() and 0xff }).putInt(cmd.inv()).put(data).array()

    private val adb by lazy { Adb(Files.createTempDirectory("fuzzadb").toFile()) }

    private fun adbOver(b: ByteArray, sink: OutputStream = ByteArrayOutputStream()): Adb = adb.also { a ->
        Adb::class.java.getDeclaredField("input").apply { isAccessible = true }.set(a, DataInputStream(ByteArrayInputStream(b)))
        Adb::class.java.getDeclaredField("output").apply { isAccessible = true }.set(a, sink)
    }

    private fun call(a: Adb, name: String): Any? = try {
        Adb::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(a)
    } catch (e: java.lang.reflect.InvocationTargetException) { throw e.cause!! }

    @Test fun adbPackets() {
        val CNXN = 0x4e584e43; val AUTH = 0x48545541; val OKAY = 0x59414b4f; val WRTE = 0x45545257
        val seeds = listOf(
            adbPacket(AUTH, 1, 0, ByteArray(20).also(r::nextBytes)) + adbPacket(CNXN, 0x01000000, 4096, "device::ro.product.name=sabrina;".toByteArray()),
            adbPacket(OKAY, 5, 1, ByteArray(0)), adbPacket(WRTE, 5, 1, "hello\n".toByteArray()), adbPacket(CNXN, 0x01000001, 1 shl 18, ByteArray(0)))
        repeat(1500) {
            val b = mutate(seeds[r.nextInt(seeds.size)])
            only("Adb.read", b, IOException::class.java) { call(adbOver(b), "read") }
            only("Adb.authenticate", b, IOException::class.java) { call(adbOver(b), "authenticate") }
        }
    }

    @Test fun adbLongTokenIsAnIOException() {                        // was IllegalBlockSizeException, past every catch (IOException)
        val b = adbPacket(0x48545541, 1, 0, ByteArray(300))
        try { call(adbOver(b), "authenticate"); fail() } catch (e: IOException) { }
    }

    // ------------------------------------------------------------ MQTT (VIDAA)

    @Test fun mqttPackets() {
        fun mq(head: Int, body: ByteArray): ByteArray {
            val o = ByteArrayOutputStream(); o.write(head); var n = body.size
            do { var x = n % 128; n /= 128; if (n > 0) x = x or 0x80; o.write(x) } while (n > 0)
            o.write(body); return o.toByteArray()
        }
        val topic = "/remoteapp/mobile/broadcast/ui_service/state".toByteArray()
        val pub = byteArrayOf(0, topic.size.toByte()) + topic + """{"statetype":"app","name":"Netflix"}""".toByteArray()
        val seeds = listOf(mq(0x20, byteArrayOf(0, 0)) + mq(0x30, pub), mq(0x32, byteArrayOf(0, topic.size.toByte()) + topic + byteArrayOf(0, 7) + "{}".toByteArray()), mq(0x90, byteArrayOf(0, 1, 0)))
        repeat(2000) {
            val b = if (it % 10 == 0) random(32) else mutate(seeds[r.nextInt(seeds.size)])
            var closed = 0
            val m = MqttLite(FakeSocket(ByteArrayInputStream(b)), { _, _ -> }, { closed++ })
            only("MqttLite.loop", b) { MqttLite::class.java.getDeclaredMethod("loop").apply { isAccessible = true }.invoke(m) }   // catches its own: ends the link
            assertTrue(closed <= 1)
        }
    }

    // ------------------------------------------------------------ mDNS, SSDP and the text replies

    @Test fun mdnsAnswers() {
        fun name(o: ByteArrayOutputStream, n: String) { for (l in n.split('.')) { o.write(l.length); o.write(l.toByteArray()) }; o.write(0) }
        fun rec(o: ByteArrayOutputStream, n: String, type: Int, data: ByteArray) {
            name(o, n); o.write(type shr 8); o.write(type and 255); o.write(0); o.write(1); o.write(byteArrayOf(0, 0, 0, 120)); o.write(data.size shr 8); o.write(data.size and 255); o.write(data)
        }
        val o = ByteArrayOutputStream()
        o.write(byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 0, 0, 3, 0, 0, 0, 0))
        rec(o, "_adb-tls-connect._tcp.local", 12, ByteArrayOutputStream().also { name(it, "adb-ABC._adb-tls-connect._tcp.local") }.toByteArray())
        rec(o, "adb-ABC._adb-tls-connect._tcp.local", 33, ByteArrayOutputStream().also { it.write(byteArrayOf(0, 0, 0, 0, 0x85.toByte(), 0x27)); name(it, "tv.local") }.toByteArray())
        o.write(byteArrayOf(0xc0.toByte(), 12, 0, 1, 0, 1, 0, 0, 0, 120, 0, 4, 10, 0, 2, 16))   // a compressed name
        val seed = o.toByteArray()
        repeat(4000) {
            val b = if (it % 10 == 0) random(64) else mutate(seed)
            only("Mdns.parse", b) { Mdns.parse(b); Mdns.parse(b, r.nextInt(b.size + 1)) }
        }
        val loop = byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 0, 0, 1, 0, 0, 0, 0, 0xc0.toByte(), 12)   // a name pointing at itself
        Mdns.parse(loop)
    }

    @Test fun ssdpAnswers() {
        val seed = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLOCATION: http://192.168.1.20:1754/\r\nSERVER: WebOS/4.1.0 UPnP/1.0\r\nST: urn:lge-com:service:webos-second-screen:1\r\n\r\n".toByteArray()
        repeat(3000) {
            val b = mutate(seed); val s = String(b, Charsets.UTF_8)
            only("Ssdp", b) { Ssdp.kind(s); Ssdp.location(s, "192.168.1.20") }
        }
        assertEquals("http://192.168.1.20:1754/", Ssdp.location(String(seed), "192.168.1.20"))
        assertNull(Ssdp.location(String(seed), "192.168.1.30"))        // another device's address: not followed
        assertNull(Ssdp.location("LOCATION: file:///etc/passwd\r\n", "192.168.1.20"))
        assertNull(Ssdp.location("LOCATION: http://192.168.1.20@evil.example/\r\n", "192.168.1.20"))
    }

    @Test fun ssdpDescriptionIsBounded() {                           // was readBytes(): an endless answer was an OutOfMemoryError
        val endless = object : InputStream() { override fun read() = 'x'.code; override fun read(b: ByteArray, o: Int, n: Int): Int { b.fill(120, o, o + n); return n } }
        assertEquals(256 * 1024, Ssdp.atMost(endless, 256 * 1024, 4000).length)
        val trickle = object : InputStream() { override fun read() = 'x'.code; override fun read(b: ByteArray, o: Int, n: Int): Int { Thread.sleep(20); b[o] = 120; return 1 } }
        val t = System.currentTimeMillis()
        Ssdp.atMost(trickle, 1 shl 20, 300)
        assertTrue(System.currentTimeMillis() - t < 1500)
    }

    @Test fun textReplies() {
        val roku = """<apps><app id="12" type="appl" version="5.1">Netflix &amp; co</app><app id="tvinput.hdmi1" type="tvin">HDMI 1</app></apps><user-device-name>Den</user-device-name>""".toByteArray()
        val dump = "Hardware Info: TvInputHardwareInfo {id=5, type=9, audio_type=0, audio_addr=, hdmi_port_id=1, cable_connection_status=1}\n#--tvr-inputs--\n  device: HdmiDeviceInfo: CEC: logical_address: 0x04 device_type: 4 cec_version: 5 vendor_id: 0 display_name: Chromecast power_status: 0 physical_address: 0x1000 port_id: 1\n".toByteArray()
        val lg = """{"devices":[{"id":"HDMI_1","label":"HDMI 1","port":1,"connected":true,"modelName":"PS5"},{"id":"AV_1","label":""}]}""".toByteArray()
        repeat(2000) {
            val a = String(mutate(roku)); val d = String(mutate(dump)); val l = mutate(lg)
            only("Roku", roku) { Roku.parseApps(a); Roku.field(a, "user-device-name"); Roku.activeApp(a) }
            only("Inputs.parse", dump) { Inputs.parse(d) }
            val o = try { JSONObject(String(l)) } catch (e: Exception) { null }
            only("Inputs.parseLg", l) { Inputs.parseLg(o) }
        }
    }

    // ------------------------------------------------------------ adb pairing (Spake2)

    @Test fun spake2Messages() {
        repeat(150) {
            val s = Spake2(true, "a".toByteArray(), "b".toByteArray())
            s.generate("123456".toByteArray())
            val b = if (it % 3 == 0) random(40) else ByteArray(32).also(r::nextBytes)
            only("Spake2.process", b, IllegalArgumentException::class.java) { s.process(b) }   // its documented "bad pairing message"
        }
    }
}
