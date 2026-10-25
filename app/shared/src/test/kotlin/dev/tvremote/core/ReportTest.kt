package dev.tvremote.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sending core shared with TV Home, Channels and Games (app/shared/src/report): the switch rule, the id, the bodies. */
class ReportTest {
    @Test fun tvSetupsSwitchWinsWhenItAnswers() {
        assertFalse(Report.decide(false, true))            // TV Setup off: nothing, whatever the app's own says
        assertTrue(Report.decide(true, false))             // TV Setup on: sent
        assertTrue(Report.decide(null, true))              // TV Setup missing / old / other key: the app's own (on by default)
        assertFalse(Report.decide(null, false))
    }

    @Test fun oneTvStaysOneInstall() {
        val setup = "3f2b8c1e-5a4d-4e6f-9a7b-1c2d3e4f5a6b"
        assertEquals(setup, Report.chooseId(setup, "own-own-own-own-own-own-own-own-own-1"))
        assertEquals("own", Report.chooseId(null, "own"))
        assertEquals("own", Report.chooseId("", "own"))
        assertEquals("own", Report.chooseId("short", "own"))
    }

    @Test fun aHomeCrashBodyHasTheAgreedFieldsScrubbed() {
        val b = Report.crashBody("3f2b8c1e-5a4d-4e6f-9a7b-1c2d3e4f5a6b", "home", "0.4.2", "kotlin", JSONObject().put("model", "X1"),
            "java.lang.IllegalStateException", "no route to 192.168.1.7 for bob@x.com", "at dev.tvremote.home.Foo.bar(Foo.kt:42)\nat /Users/bob/x.kt:1")
        assertEquals(setOf("id", "app", "v", "where", "dev", "kind", "msg", "stack"), b.keys().asSequence().toSet())
        assertEquals("home", b.getString("app"))
        assertEquals("no route to x.x.x.x for email@x", b.getString("msg"))
        assertTrue(b.getString("stack").contains("~/x.kt:1"))
    }

    @Test fun aHomePingBodyIsTheTvItself() {
        val m = mapOf("ro.product.brand" to "Sony", "ro.product.model" to "BRAVIA 4K", "ro.build.version.release" to "14", "ro.build.version.sdk" to "34")
        val b = Report.pingBody("3f2b8c1e-5a4d-4e6f-9a7b-1c2d3e4f5a6b", "home", "0.4.2", m, true, null, null, null)
        assertEquals("home", b.getString("app"))
        assertEquals("BRAVIA 4K", b.getJSONObject("tv").getString("model"))
        assertTrue(b.getJSONObject("tv").getBoolean("leanback"))
        assertTrue(b.getJSONObject("ok").isNull("adb"))
        assertTrue(Report.due(null, 1, "1", 0)); assertFalse(Report.due("1|1|0", 1, "1", 0)); assertTrue(Report.due("1|1|0", 2, "1", 0))
    }

    @Test fun onceSendsAnErrorOnceThenQuietAfterAFailure() {
        var now = 0L; var ok = false; var n = 0
        val once = Report.Once { now }
        val post = { _: String, _: String -> n++; ok }
        assertFalse(once.send("u", "k", post) { JSONObject() })
        assertFalse(once.send("u", "k", post) { JSONObject() }); assertEquals(1, n)      // a minute of quiet
        now = 61_000; ok = true
        assertTrue(once.send("u", "k", post) { JSONObject() }); assertTrue(once.send("u", "k", post) { JSONObject() }); assertEquals(2, n)
    }
}
