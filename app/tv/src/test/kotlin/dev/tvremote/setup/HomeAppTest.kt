package dev.tvremote.setup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HomeInfo "app": what a request may become (a Rules action or one fixed shell line) before any code is asked. */
class HomeAppTest {
    private fun d(s: String) = JSONObject(s)

    @Test fun shellLinesAreFixedAndOnlyForRealPackages() {
        assertEquals("am force-stop com.netflix.ninja", HomeApp.command("stop", "com.netflix.ninja"))
        assertEquals("pm clear --cache-only com.google.android.youtube.tv", HomeApp.command("cache", "com.google.android.youtube.tv"))
        assertNull(HomeApp.command("stop", "com.x; reboot"))
        assertNull(HomeApp.command("stop", "com.x && rm -rf /sdcard"))
        assertNull(HomeApp.command("stop", "x"))                                // not a package name
        assertNull(HomeApp.command("stop", "dev.tvremote.setup"))               // the lock's own screen and the home screen are not stopped from a menu
        assertNull(HomeApp.command("stop", "dev.tvremote.home"))
        assertNull(HomeApp.command("reboot", "com.netflix.ninja"))
        assertNull(HomeApp.command("", "com.netflix.ninja"))
    }

    @Test fun lockAndLimitBecomeRulesActionsOnly() {
        val lock = HomeApp.rulesAction(d("""{"op":"lock","pkg":"com.netflix.ninja","on":true}"""))!!
        assertEquals("lock", lock.getString("do")); assertTrue(lock.getBoolean("on"))
        val limit = HomeApp.rulesAction(d("""{"op":"limit","pkg":"com.netflix.ninja","minutes":45}"""))!!
        assertEquals("limit", limit.getString("do")); assertEquals(45, limit.getInt("minutes"))
        assertEquals(24 * 60, HomeApp.rulesAction(d("""{"op":"limit","pkg":"com.netflix.ninja","minutes":99999}"""))!!.getInt("minutes"))
        assertEquals(0, HomeApp.rulesAction(d("""{"op":"limit","pkg":"com.netflix.ninja","minutes":-5}"""))!!.getInt("minutes"))
        assertNull(HomeApp.rulesAction(d("""{"op":"lock","pkg":"dev.tvremote.home","on":true}""")))
        assertNull(HomeApp.rulesAction(d("""{"op":"limit","pkg":"dev.tvremote.setup","minutes":5}""")))
        assertNull(HomeApp.rulesAction(d("""{"op":"lock","pkg":"not a package","on":true}""")))
        assertNull(HomeApp.rulesAction(d("""{"op":"sleep","minutes":5}""")))      // no other Rules action is reachable from here
        assertNull(HomeApp.rulesAction(d("""{"op":"totp","pkg":"com.netflix.ninja"}""")))
    }

    @Test fun theActionsGoThroughTheSameCleaningAsEveryRemote() {
        val o = JSONObject()
        val now = 1_800_000_000_000L
        try { Rules.apply(o, HomeApp.rulesAction(d("""{"op":"lock","pkg":"com.netflix.ninja","on":true}"""))!!, now); org.junit.Assert.fail("locked without a PIN") } catch (e: IllegalArgumentException) { }
        Rules.apply(o, HomeApp.rulesAction(d("""{"op":"pin","pin":"2580"}"""))!!, now)
        Rules.apply(o, HomeApp.rulesAction(d("""{"op":"lock","pkg":"com.netflix.ninja","on":true}"""))!!, now)
        Rules.apply(o, HomeApp.rulesAction(d("""{"op":"limit","pkg":"com.netflix.ninja","minutes":30}"""))!!, now)
        val s = HomeApp.state(o, "com.netflix.ninja", 600, true)
        assertTrue(s.getBoolean("pin")); assertTrue(s.getBoolean("locked")); assertEquals(30, s.getInt("limit")); assertEquals(600L, s.getLong("used"))
        Rules.apply(o, HomeApp.rulesAction(d("""{"op":"limit","pkg":"com.netflix.ninja","minutes":0}"""))!!, now)
        assertEquals(0, HomeApp.state(o, "com.netflix.ninja", 0, false).getInt("limit"))
        assertFalse(HomeApp.state(o, "com.other.app", 0, false).getBoolean("locked"))
    }
}
