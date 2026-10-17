package dev.tvremote.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Collections
import dev.tvremote.core.remote.Atv
import dev.tvremote.core.remote.Remote
import dev.tvremote.core.remote.Roku
import dev.tvremote.core.scenes.Scenes
import dev.tvremote.core.remote.Ssdp

class RokuTest {
    private val hits: MutableList<String> = Collections.synchronizedList(ArrayList())
    private var limited = false
    private var server: ServerSocket? = null

    private val info = """<?xml version="1.0" encoding="UTF-8" ?>
<device-info><vendor-name>TCL</vendor-name><model-name>8 Series</model-name><friendly-model-name>TCL·Roku TV</friendly-model-name>
<user-device-name>Living &amp; room</user-device-name><software-version>14.1.4</software-version><wifi-mac>d4:e2:2f:00:11:22</wifi-mac>
<is-tv>true</is-tv><power-mode>PowerOn</power-mode></device-info>"""
    private val apps = """<apps><app id="12" type="appl" version="5.1">Netflix</app><app id="837" type="appl" version="2.2">YouTube</app>
<app id="tvinput.hdmi1" type="tvin" version="1.0">PlayStation</app><app id="tvinput.dtv" type="tvin" version="1.0">Antenna TV</app></apps>"""

    private fun start(): Int {
        val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        Thread {
            while (!s.isClosed) {
                val c = try { s.accept() } catch (e: IOException) { return@Thread }
                c.use {
                    val head = c.getInputStream().bufferedReader().readLine() ?: return@use
                    val (method, target) = head.split(" ").let { it[0] to it[1] }
                    hits.add("$method $target")
                    val path = target.substringBefore('?')
                    val (code, body) = when {
                        limited && path != "/query/device-info" -> 403 to "ECP command not allowed"
                        path == "/query/device-info" -> 200 to info
                        path == "/query/apps" -> 200 to apps
                        path == "/query/active-app" -> 200 to """<active-app><app id="12" type="appl" version="5.1">Netflix</app></active-app>"""
                        path == "/query/icon/12" -> 200 to "PNGDATA-PNGDATA-PNGDATA"
                        method == "POST" -> 200 to ""
                        else -> 404 to ""
                    }
                    val b = body.toByteArray()
                    c.getOutputStream().apply { write("HTTP/1.1 $code X\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(b); flush() }
                }
            }
        }.apply { isDaemon = true }.start()
        server = s
        return s.localPort
    }

    @After fun stop() { server?.close() }

    private fun posts(n: Int): List<String> {
        val until = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < until && hits.count { it.startsWith("POST") } < n) Thread.sleep(20)
        return hits.filter { it.startsWith("POST") }
    }

    @Test fun connectsReadsTheDeviceAndSendsKeysTextAndLaunches() {
        val r = Roku(File("."), "127.0.0.1", start())
        r.connect {}
        assertTrue(r.ready())
        assertEquals("TCL·Roku TV", r.model); assertEquals("TCL", r.vendor); assertEquals("14.1.4", r.version)
        assertEquals("D4:E2:2F:00:11:22", r.mac); assertEquals(true, r.isOn); assertEquals("12", r.app)
        r.key(19, Atv.SHORT)
        r.key(85, Atv.DOWN); r.key(85, Atv.UP)
        r.key(24, Atv.SHORT)                                          // a Roku TV has volume keys
        r.key(7, Atv.SHORT)                                           // no number keys: nothing sent
        r.key(26, Atv.DOWN); r.key(26, Atv.UP)                        // on: power goes off, one press however long it is held
        r.text("a b")
        r.launch("837")
        r.launch("https://youtu.be/dQw4w9WgXcQ")
        r.launch("https://example.com")                               // no browser: nothing sent
        assertEquals(listOf("POST /keypress/Up", "POST /keydown/Play", "POST /keyup/Play", "POST /keypress/VolumeUp", "POST /keypress/PowerOff",
            "POST /keypress/Lit_a", "POST /keypress/Lit_%20", "POST /keypress/Lit_b", "POST /launch/837",
            "POST /launch/837?contentId=dQw4w9WgXcQ&mediaType=movie"), posts(10))
        assertEquals(false, r.isOn)
        r.close()
    }

    @Test fun listsAppsInputsAndIcons() {
        val r = Roku(File("."), "127.0.0.1", start())
        r.connect {}
        assertEquals(listOf("12" to "Netflix", "837" to "YouTube"), r.apps().map { it.id to it.label })
        assertEquals(listOf("tvinput.hdmi1" to "hdmi", "tvinput.dtv" to "tuner"), r.inputs().map { it.id to it.kind })
        assertEquals(1, r.inputs()[0].port)
        assertTrue(r.switchInput("tvinput.hdmi1"))
        assertFalse(r.switchInput("12; rm"))
        assertEquals("PNGDATA-PNGDATA-PNGDATA", String(r.icon("12")!!))
        assertEquals(null, r.icon("../x"))
        r.close()
    }

    @Test fun aRokuThatKeepsPhonesOutSaysHowToAllowIt() {
        limited = true
        val r = Roku(File("."), "127.0.0.1", start())
        try { r.connect {}; fail("connected") } catch (e: IOException) { assertTrue(e.message!!.startsWith(Roku.LIMITED)) }
        assertFalse(r.ready())
        assertTrue(Errors.say(IOException(Roku.LIMITED), false).contains("Control by mobile apps"))
    }

    @Test fun findsItByItsSsdpAnswerAndPorts() {
        assertEquals(Remote.Kind.ROKU, Ssdp.kind("HTTP/1.1 200 OK\r\nST: roku:ecp\r\nUSN: uuid:roku:ecp:X00\r\nLOCATION: http://10.0.0.5:8060/\r\n"))
        assertEquals(listOf(8060), Remote.Kind.ports(Remote.Kind.ROKU))
        assertEquals("Roku", Remote.Kind.name(Remote.Kind.ROKU))
        assertTrue("ENTER" !in Remote.Kind.keyNames(Remote.Kind.ROKU) || Scenes.KEYS["ENTER"] in Roku.KEYS.keys)
        assertTrue(Remote.of(Remote.Kind.ROKU, File("."), "127.0.0.1") is Roku)
    }

    @Test fun aRokuTvHasVolumeKeysAndTheHomeScreenIsNoApp() {
        val r = Roku(File("."), "127.0.0.1", start())
        r.connect {}
        assertTrue(24 in r.keys)                                      // the TV above
        r.close()
        assertEquals("", Roku.activeApp("<active-app><app>Roku</app><screensaver id=\"5\" type=\"ssvr\">x</screensaver></active-app>"))
        assertEquals("12", Roku.activeApp("<active-app><app id=\"12\" subtype=\"rsga\" type=\"appl\">Netflix</app></active-app>"))   // not "subtype"
    }
}
