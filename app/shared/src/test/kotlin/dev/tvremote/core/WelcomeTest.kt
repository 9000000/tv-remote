package dev.tvremote.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import dev.tvremote.core.ontv.SetupNeed
import dev.tvremote.core.ontv.Welcome

class WelcomeTest {
    private fun cfg(on: Boolean = true, has: Boolean = true, w: String = "every") = JSONObject().put("on", on).put("has", has).put("when", w)

    @Test fun kinds() {
        assertEquals("video", Welcome.kind("Hello.MP4"))
        assertEquals("video", Welcome.kind("a.b.webm"))
        assertEquals("image", Welcome.kind("x.JPG"))
        assertNull(Welcome.kind("x.gif"))
        assertNull(Welcome.kind("noext"))
        assertNull(Welcome.kind(null))
    }

    @Test fun secondsAreClamped() {
        assertEquals(10, Welcome.seconds(null))
        assertEquals(10, Welcome.seconds("abc"))
        assertEquals(3, Welcome.seconds(0))
        assertEquals(120, Welcome.seconds(9999))
        assertEquals(20, Welcome.seconds("20"))
        assertEquals(8, Welcome.seconds(7.6))
    }

    @Test fun whenDefaultsToEvery() {
        assertEquals("every", Welcome.whenOf(null))
        assertEquals("every", Welcome.whenOf("sometimes"))
        assertEquals("boot", Welcome.whenOf(" BOOT "))
    }

    @Test fun dueByEvent() {
        assertTrue(Welcome.due("boot", cfg(), null))
        assertTrue(Welcome.due("on", cfg(), null))
        assertTrue(Welcome.due("boot", cfg(w = "boot"), null))
        assertFalse(Welcome.due("on", cfg(w = "boot"), null))      // only after a full restart: standby does not show it
        assertFalse(Welcome.due("other", cfg(), null))
        assertFalse(Welcome.due("boot", cfg(on = false), null))
        assertFalse(Welcome.due("boot", cfg(has = false), null))   // nothing to show
    }

    @Test fun twoMinuteGuard() {
        assertFalse(Welcome.due("on", cfg(), 119_999))
        assertTrue(Welcome.due("on", cfg(), 120_000))
        assertFalse(Welcome.due("boot", cfg(), 5_000))
        assertTrue(Welcome.due("boot", cfg(), -1))                 // a clock that went back (a new run of the TV) does not block
    }

    @Test fun applyChanges() {
        val o = JSONObject().put("kind", "video").put("name", "a.mp4").put("size", 5)
        assertTrue(Welcome.apply(o, JSONObject().put("do", "config").put("on", true).put("when", "boot").put("seconds", 500)))
        assertTrue(o.getBoolean("on")); assertEquals("boot", o.getString("when")); assertEquals(120, o.getInt("seconds"))
        assertTrue(Welcome.apply(o, JSONObject().put("do", "config").put("on", false)))
        assertEquals("boot", o.getString("when"))                  // fields not given stay
        assertFalse(o.getBoolean("on"))
        assertTrue(Welcome.apply(o, JSONObject().put("do", "remove")))
        assertFalse(o.has("name")); assertFalse(o.has("kind")); assertEquals("boot", o.getString("when"))
        assertFalse(Welcome.apply(o, JSONObject().put("do", "get")))
        assertFalse(Welcome.apply(o, JSONObject().put("do", "nonsense")))
    }

    private fun reply(o: JSONObject) = "Broadcast completed: result=0, data=\"" + B64.encode(o.toString().toByteArray()) + "\""

    @Test fun callReadsTheReply() {
        val sent = ArrayList<String>()
        val r = Welcome.call({ sent.add(it); reply(JSONObject().put("has", true).put("on", true)) }, JSONObject().put("do", "config").put("on", true))
        assertTrue(r.getBoolean("has"))
        assertTrue(sent[0].startsWith("appops set"))               // lets TV Setup draw over other apps first
        assertTrue(sent[0].contains("--es set "))
    }

    @Test fun callWithoutTvSetup() {
        val none = { _: String -> "" }
        assertEquals("missing", Welcome.call(none, null).getString("setup"))
        try { Welcome.call(none, JSONObject().put("do", "config")); fail() } catch (e: SetupNeed.Missing) { assertEquals("welcome", e.feature) }
        val old = { c: String -> if (c.contains("versionName")) "versionName=0.1.0" else "" }
        assertEquals("old", Welcome.call(old, null).getString("setup"))
        try { Welcome.call(old, JSONObject().put("do", "config")); fail() } catch (e: SetupNeed.Missing) { assertEquals("0.1.0", e.have) }
    }

    @Test fun errorFromTvSetupIsRaised() {
        try { Welcome.call({ reply(JSONObject().put("error", "welcome:unreadable")) }, JSONObject().put("do", "adopt")); fail() }
        catch (e: IllegalArgumentException) { assertEquals("welcome:unreadable", e.message) }
    }
}
