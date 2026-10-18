package dev.tvremote.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import dev.tvremote.core.remote.Atv
import dev.tvremote.core.remote.Remote
import dev.tvremote.core.remote.Ssdp
import dev.tvremote.core.remote.Vidaa

/** A made-up Hisense TV: its MQTT broker, enough of it to pair, log in with the token and take keys, apps and inputs. */
class VidaaTest {
    private val RAW = Vidaa.RAW
    private val got: MutableList<String> = Collections.synchronizedList(ArrayList())   // "action payload" the TV received
    private val logins: MutableList<String> = Collections.synchronizedList(ArrayList())
    private var legacy = false                                        // an old TV: the fixed login, no code
    private var server: ServerSocket? = null
    private val dir: File = Files.createTempDirectory("vidaa").toFile()

    private fun start(): Int {
        val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        Thread {
            while (!s.isClosed) {
                val c = try { s.accept() } catch (e: IOException) { return@Thread }
                Thread { try { serve(c) } catch (ignored: Exception) { } finally { c.close() } }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true }.start()
        server = s
        return s.localPort
    }

    @After fun stop() { server?.close(); dir.deleteRecursively() }

    private fun serve(c: Socket) {
        val inp = DataInputStream(c.getInputStream()); val out = c.getOutputStream()
        val (head, body) = read(inp) ?: return
        if (head != 0x10) return                                      // a TLS hello: this broker is plain TCP
        var at = 10
        fun s(): String { val n = ((body[at].toInt() and 0xff) shl 8) or (body[at + 1].toInt() and 0xff); val r = String(body, at + 2, n); at += 2 + n; return r }
        val cid = s(); val user = s(); val pass = s()
        logins.add("$user $pass")
        val ok = when {
            user == Vidaa.STATIC_USER -> legacy
            pass == "ACC1" || pass == "REF1" -> true
            user.startsWith("his$") -> !legacy
            else -> false
        }
        send(out, 0x20, byteArrayOf(0, if (ok) 0 else 5))
        if (!ok) return
        val mobile = "/remoteapp/mobile/$cid"
        while (true) {
            val (h, b) = read(inp) ?: return
            if (h shr 4 != 3) continue
            val n = ((b[0].toInt() and 0xff) shl 8) or (b[1].toInt() and 0xff)
            val topic = String(b, 2, n); val payload = String(b, 2 + n, b.size - 2 - n)
            val action = topic.substringAfterLast('/')
            got.add("$action $payload".trim())
            fun say(t: String, p: String) = publish(out, mobile + t, p)
            when (action) {
                "vidaa_app_connect" -> say("/ui_service/data/authentication", "")
                "gettvstate" -> if (pass == Vidaa.STATIC_PASS && !legacy) say("/ui_service/data/authentication", "") else say("/ui_service/data/gettvstate", """{"statetype":"app","name":"Netflix"}""")
                "authenticationcode" -> say("/ui_service/data/authenticationcode", if (payload.contains("1234")) """{"result":1}""" else """{"result":0}""")
                "gettoken" -> say("/platform_service/data/tokenissuance", """{"accesstoken":"ACC1","refreshtoken":"REF1","accesstoken_time":${System.currentTimeMillis() / 1000},"accesstoken_duration_day":2}""")
                "applist" -> say("/ui_service/data/applist", """[{"appId":"3","name":"Netflix","url":"netflix","storeType":0},{"appId":"7","name":"YouTube","url":"youtube"}]""")
                "sourcelist" -> say("/ui_service/data/sourcelist", """[{"sourceid":"4","sourcename":"HDMI 2","displayname2":"PlayStation","is_signal":"1"},{"sourceid":"0","sourcename":"TV"}]""")
                "getvolume" -> say("/platform_service/data/getvolume", """{"volume_type":0,"volume_value":17}""")
                "getdeviceinfo" -> say("/platform_service/data/getdeviceinfo", """{"model_name":"65U8K","brand":"Hisense","wifi_mac":"aa-bb-cc-00-11-22"}""")
            }
        }
    }

    private fun read(i: DataInputStream): Pair<Int, ByteArray>? {
        val h = i.read(); if (h < 0) return null
        var len = 0; var mul = 1
        while (true) { val b = i.readUnsignedByte(); len += (b and 0x7f) * mul; if (b and 0x80 == 0) break; mul *= 128 }
        return h to ByteArray(len).also { i.readFully(it) }
    }

    private fun send(o: OutputStream, head: Int, body: ByteArray) = synchronized(o) {
        val b = ByteArrayOutputStream(); b.write(head); var n = body.size
        do { var x = n % 128; n /= 128; if (n > 0) x = x or 0x80; b.write(x) } while (n > 0)
        b.write(body); o.write(b.toByteArray()); o.flush()
    }

    private fun publish(o: OutputStream, topic: String, payload: String) {
        val t = topic.toByteArray()
        send(o, 0x30, byteArrayOf((t.size shr 8).toByte(), t.size.toByte()) + t + payload.toByteArray())
    }

    private fun sent(n: Int, filter: (String) -> Boolean): List<String> {
        val until = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < until && seen().count(filter) < n) Thread.sleep(20)
        return seen().filter(filter)
    }

    private fun seen() = synchronized(got) { got.toList() }           // the TV's thread may add while this reads

    private fun waitFor(what: () -> Boolean) { val until = System.currentTimeMillis() + 3000; while (!what() && System.currentTimeMillis() < until) Thread.sleep(20) }

    @Test fun theLoginIsMadeTheWayHisensesAppMakesIt() {                // the same numbers as stevene1919/hisense_vidaa's crypto.py
        val mac = "AA:BB:CC:DD:EE:FF"
        assertEquals("AA:BB:CC:DD:EE:FF\$his\$637064_vidaacommon_001", Vidaa.clientId(mac))
        assertEquals("his$6239759786369374312" to "E4B16BE39DA56468522CACF7411FB50F", Vidaa.credentials(mac, 1700000000, "modern"))
        assertEquals("his$6239759786369374312" to "30CC23251954D2B965504DC81E944805", Vidaa.credentials(mac, 1700000000, "middle"))
        assertEquals("his$1700000000" to "30CC23251954D2B965504DC81E944805", Vidaa.credentials(mac, 1700000000, "remotenow"))
    }

    @Test fun pairsWithTheCodeThenLogsInWithTheTokenAndControlsTheTv() {
        val port = start()
        val v = Vidaa(dir, "127.0.0.1", port)
        try { v.connect {}; fail("connected unpaired") } catch (e: Atv.NotPaired) { }
        v.startPairing()
        try { v.finishPairing("9999"); fail("took a wrong code") } catch (e: IllegalArgumentException) { }
        v.startPairing()
        v.finishPairing("1234")
        v.connect {}
        assertTrue(v.ready())
        assertTrue(logins.last().endsWith(" ACC1"))
        waitFor { v.model.isNotEmpty() && v.volume != null && v.app.isNotEmpty() }
        assertEquals("65U8K", v.model); assertEquals(17, v.volume!!.first); assertEquals("Netflix", v.app); assertEquals(true, v.isOn)
        assertEquals("AA:BB:CC:00:11:22", v.mac)
        v.key(19, Atv.DOWN); v.key(19, Atv.UP); v.key(7, Atv.SHORT); v.key(999, Atv.SHORT)
        v.text("hi")
        assertEquals(listOf("3" to "Netflix", "7" to "YouTube"), v.apps().map { it.id to it.label })
        v.launch("YouTube")
        assertEquals(listOf("4" to "hdmi", "0" to "tuner"), v.inputs().map { it.id to it.kind })
        assertEquals("HDMI 2 · PlayStation", v.inputs()[0].label)
        assertTrue(v.switchInput("4")); assertFalse(v.switchInput("nope"))
        assertTrue(v.setVolume(30))
        assertEquals(listOf("sendkey KEY_UP", "sendkey KEY_0"), sent(2) { it.startsWith("sendkey") })
        assertEquals(2, sent(2) { it.startsWith("txtinputdata") || it.startsWith("bwsinputdata") }.size)
        assertTrue(sent(1) { it.startsWith("launchapp") }.single().contains("\"appId\":\"7\""))
        assertTrue(sent(1) { it.startsWith("changesource") }.single().contains("\"sourceid\":\"4\""))
        assertEquals(listOf("changevolume 30"), sent(1) { it.startsWith("changevolume") })
        v.close()
        val again = Vidaa(dir, "127.0.0.1", port)                     // the token is kept: no code the next time
        again.connect {}
        assertTrue(again.ready())
        again.close()
    }

    @Test fun anOldTvTakesTheFixedLoginWithoutACode() {
        legacy = true
        val v = Vidaa(dir, "127.0.0.1", start())
        v.connect {}
        assertTrue(v.ready())
        assertEquals("${Vidaa.STATIC_USER} ${Vidaa.STATIC_PASS}", logins.last())
        v.close()
    }

    @Test fun aTvThatWantsHisensesCertificateSaysSo() {
        val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))   // takes the connection, then hangs up on every login
        Thread { while (!s.isClosed) try { s.accept().close() } catch (e: IOException) { } }.apply { isDaemon = true }.start()
        server = s
        Vidaa.RAW = "http://127.0.0.1:1/"                             // the downloads fail too
        try { Vidaa(dir, "127.0.0.1", s.localPort).connect {}; fail("connected") } catch (e: IOException) { assertEquals(Vidaa.NO_CERT, e.message) }
        finally { Vidaa.RAW = RAW }
        assertTrue(Errors.say(IOException(Vidaa.NO_CERT), "en").contains("VIDAA Smart TV"))
    }

    @Test fun aDownloadedCertificateThatIsNotTheOnePinnedIsNotKept() {
        val key = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\n" + java.util.Base64.getMimeEncoder().encodeToString(key.private.encoded) + "\n-----END PRIVATE KEY-----\n"
        val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))   // serves the same key for every file: no certificate matches
        Thread {
            while (!s.isClosed) try { s.accept().use { c ->
                c.getInputStream().bufferedReader().readLine()
                c.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: ${pem.length}\r\nConnection: close\r\n\r\n$pem").toByteArray())
            } } catch (e: IOException) { }
        }.apply { isDaemon = true }.start()
        server = s
        Vidaa.RAW = "http://127.0.0.1:${s.localPort}/"
        try { assertFalse(Vidaa.fetchCerts(dir)) } finally { Vidaa.RAW = RAW }
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".crt") || it.name.endsWith(".key") })
    }

    @Test fun takesTheCertificateOnlyFromAFileThatHasOne() {
        assertFalse(Vidaa.importCert(dir, "not a certificate".toByteArray()))
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { z -> z.putNextEntry(ZipEntry("res/raw/readme.txt")); z.write("x".toByteArray()); z.closeEntry() }
        assertFalse(Vidaa.importCert(dir, zip.toByteArray()))          // an APK without one
        assertFalse(Vidaa.hasCert(dir))
    }

    @Test fun isFoundByItsSsdpAnswerAndPort() {
        assertEquals(Remote.Kind.VIDAA, Ssdp.kind("HTTP/1.1 200 OK\r\nLOCATION: http://10.0.0.7:38400/MediaServer/rendererdevicedesc.xml\r\nSERVER: Linux UPnP/1.0\r\n"))
        assertEquals(listOf(36669), Remote.Kind.ports(Remote.Kind.VIDAA))
        assertEquals("Hisense VIDAA", Remote.Kind.name(Remote.Kind.VIDAA))
        assertTrue(Remote.of(Remote.Kind.VIDAA, dir, "127.0.0.1") is Vidaa)
    }
}
