package dev.tvremote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the person sends must not let anyone else in, nor show what they typed. */
class WireLogTest {
    @Test fun keysTokensMacsAndTypedTextAreBlanked() {
        val c = WireLog.clean("""{"type":"registered","payload":{"client-key":"abc123"}} /api/v2/x?name=VFY=&token=987654 {"data":{"token":"55"}} aa:bb:cc:00:11:22""")
        assertFalse(c, c.contains("abc123") || c.contains("987654") || c.contains("\"55\"") || c.contains("aa:bb"))
        assertTrue(c, c.contains("\"client-key\":\"…\"") && c.contains("&token=…"))
        assertEquals("""{"payload":{"text":"…(8)"}}""", WireLog.clean("""{"payload":{"text":"xin chào"}}"""))
        assertEquals("""{"Cmd":"Click","DataOfCmd":"KEY_UP"}""", WireLog.clean("""{"Cmd":"Click","DataOfCmd":"KEY_UP"}"""))
        assertTrue(WireLog.clean("""{"Cmd":"aGVsbG8="}""").contains("…(8)"))
    }

    @Test fun theSameLineAgainIsCounted() {
        val l = WireLog()
        repeat(3) { l.add("·", "adb 10.0.0.2 ConnectException") }
        l.add("·", "adb 10.0.0.2 connected")
        assertEquals(2, l.text().lines().size)
        assertTrue(l.text(), l.text().lines()[0].endsWith("(×3)"))
    }

    @Test fun keepsOnlyTheLastLines() {
        val l = WireLog(3)
        for (i in 1..5) l.add("→", "m$i")
        assertEquals(listOf("m3", "m4", "m5"), l.text().lines().map { it.substringAfterLast(' ') })
    }

    @Test fun aWifiPasswordNeverReachesTheLog() {
        val c = WireLog.clean("""{"qrs":{"qr1":{"tpl":"wifi","f":{"ssid":"Home","pass":"hunter2"}}},"x":"WIFI:T:WPA;S:Home;P:hun\;ter2;;"}""")
        assertTrue(c, !c.contains("hunter2") && !c.contains("ter2") && c.contains("Home"))
    }
}
