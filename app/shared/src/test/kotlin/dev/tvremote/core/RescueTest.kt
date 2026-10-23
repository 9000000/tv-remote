package dev.tvremote.core

import dev.tvremote.core.ontv.Rescue
import dev.tvremote.core.ontv.RulesClient
import dev.tvremote.core.ontv.Setup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RescueTest {
    private val keep = { p: String -> Setup.mustKeep(p) }
    private val stops = { p: String, home: String -> Rescue.shouldStop(p, home, "com.google.android.inputmethod.latin", keep) }

    @Test fun stopsAnAppAPersonOpened() {
        assertTrue(stops("com.google.android.youtube.tv", "dev.tvremote.home"))
        assertTrue(stops("com.netflix.ninja", "com.google.android.apps.tv.launcherx"))
    }

    @Test fun leavesTheTvsOwnScreensAlone() {
        assertFalse(stops("", "dev.tvremote.home"))                                  // nothing known on screen
        assertFalse(stops("com.google.android.apps.tv.launcherx", "com.google.android.apps.tv.launcherx"))
        assertFalse(stops("com.some.launcher", "com.some.launcher"))                 // whatever Home opens, even an unknown one
        assertFalse(stops("dev.tvremote.home", "com.some.launcher"))                 // TV Home
        assertFalse(stops("dev.tvremote.setup", "dev.tvremote.home"))                // TV Setup
        assertFalse(stops("com.android.systemui", "dev.tvremote.home"))
        assertFalse(stops("com.android.tv.settings", "dev.tvremote.home"))
        assertFalse(stops("com.google.android.inputmethod.latin", "dev.tvremote.home"))   // the keyboard in use
    }

    @Test fun runStopsThenPressesHome() {
        val said = ArrayList<String>()
        val sh = { c: String ->
            said += c
            when {
                c.startsWith("dumpsys activity") -> "mResumedActivity: ActivityRecord{1 u0 com.netflix.ninja/.MainActivity t5}"
                c.contains("get-role-holders") || c.contains("android.intent.category.HOME") -> "dev.tvremote.home"
                c.startsWith("settings get") -> "null"
                else -> ""
            }
        }
        val o = Rescue.run(sh)
        assertEquals("com.netflix.ninja", o.getString("stopped"))
        assertTrue("am force-stop com.netflix.ninja" in said)
        assertTrue("input keyevent 3" in said)
        assertTrue(said.indexOf("am force-stop com.netflix.ninja") < said.indexOf("input keyevent 3"))
    }

    @Test fun runKeepsTvSetupButStillGoesHome() {
        val said = ArrayList<String>()
        val sh = { c: String ->
            said += c
            if (c.startsWith("dumpsys activity")) "mResumedActivity: ActivityRecord{1 u0 dev.tvremote.setup/.MainActivity t5}" else ""
        }
        val o = Rescue.run(sh)
        assertEquals("", o.getString("stopped"))
        assertFalse(said.any { it.startsWith("am force-stop") })
        assertTrue("input keyevent 3" in said)
    }

    @Test fun keepAliveSilencesWhatOldAndroidLacks() {
        val k = RulesClient.KEEP_ALIVE
        assertTrue(k.contains("dev.tvremote.setup") && k.contains("dev.tvremote.home"))
        for (c in listOf("deviceidle whitelist", "set-standby-bucket", "RUN_ANY_IN_BACKGROUND")) assertTrue(c, k.substringAfter(c).substringBefore(";").contains("2>/dev/null") || k.substringAfter(c).substringBefore(";").contains("2>&1"))
        assertTrue(RulesClient.START.startsWith(k))
    }
}
