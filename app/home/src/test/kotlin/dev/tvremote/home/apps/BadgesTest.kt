package dev.tvremote.home.apps

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BadgesTest {
    private fun info(s: String) = Badges.parse(JSONObject(s))

    @Test fun timeLeftRoundsUp() {
        val i = info("""{"limits":[{"pkg":"a.b","min":30,"used":600},{"pkg":"c.d","min":10,"used":541}]}""")
        assertEquals(Badges.Mark(false, 20), i.of("a.b"))
        assertEquals(Badges.Mark(false, 1), i.of("c.d"))                  // 59 s left is "1m", not "0m"
        assertNull(i.of("x.y"))
    }

    @Test fun usedUpAndLockedAreLocks() {
        val i = info("""{"limits":[{"pkg":"a.b","min":10,"used":600},{"pkg":"c.d","min":0,"used":0}],"locked":["e.f"]}""")
        assertEquals(Badges.LOCK, i.of("a.b"))
        assertEquals(Badges.LOCK, i.of("e.f"))
        assertNull(i.of("c.d"))                                           // no limit set
    }

    @Test fun lockBeatsTheLimit() {
        assertEquals(Badges.LOCK, info("""{"limits":[{"pkg":"a.b","min":60,"used":0}],"locked":["a.b"]}""").of("a.b"))
    }

    @Test fun bedtimeLocksEverything() {
        assertEquals(Badges.LOCK, info("""{"bed":true}""").of("any.app"))
        assertNull(Badges.NONE.of("any.app"))
    }

    @Test fun kidsModeLocksAllButTheOpenOnes() {
        val i = Badges.withOwn(info("""{"kids":true,"open":["yt.kids"],"limits":[{"pkg":"yt.kids","min":60,"used":600}]}"""), setOf("x.y"))
        assertEquals(Badges.LOCK, i.of("any.app"))                       // a FPT box asked the PIN for every app, no tile had a lock
        assertEquals(50, i.of("yt.kids")!!.minutes)                       // an open one keeps its time left
        assertEquals(Badges.LOCK, i.of("x.y"))
        assertNull(info("""{"open":["a.b"]}""").of("c.d"))               // kids mode off: no lock from the list
    }

    @Test fun labels() {
        assertEquals("20p", Badges.label(20, "p", "g"))
        assertEquals("1g05", Badges.label(65, "p", "g"))
        assertEquals("2h00", Badges.label(120, "m", "h"))
        assertEquals("59m", Badges.label(59, "m", "h"))
    }
}
