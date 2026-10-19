package dev.tvremote.core

import dev.tvremote.core.ontv.LauncherSwap
import dev.tvremote.core.ontv.LauncherWatch
import dev.tvremote.core.ontv.Launchers
import dev.tvremote.core.ontv.Setup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The launcher gallery's safety: the swap and its way back (a fake TV that keeps the record), the watchdog's decisions, the list's fit. */
class LauncherTest {
    companion object { const val WIZ = "com.google.android.tungsten.setupwraith" }
    private class Tv(val pick: Boolean = true) {
        val installed = mutableSetOf("com.google.android.tungsten.setupwraith", Setup.LAUNCHERX, "com.new.home", "com.google.android.tvlauncher")
        val disabled = mutableSetOf("com.google.android.tvlauncher")
        val homes = linkedMapOf("com.new.home" to 0, Setup.LAUNCHERX to 2, WIZ to 1, "com.google.android.tvlauncher" to 2)
        var wizardLocked = false                                       // a TV that refuses to turn one screen of a system app off
        var chosen: String? = null
        var rec = ""
        var clock = 1000L
        val said = mutableListOf<String>()
        fun home(): String {
            val on = homes.filterKeys { it in installed && it !in disabled && "$it/.Home" !in disabled }
            if (!pick && Setup.LAUNCHERX in disabled) return "android/.Resolver"   // a TV that ignores the choice
            chosen?.let { c -> if (c.substringBefore('/') in on && pick) return c }
            return on.maxByOrNull { it.value }?.key?.let { "$it/.Home" } ?: "android/.Resolver"
        }
        fun sh(cmd: String): String {
            said += cmd
            Regex("^pm disable-user --user 0 (\\S+)$").find(cmd)?.let { disabled += it.groupValues[1]; chosen = null; return "" }
            Regex("^pm disable --user 0 (\\S+) 2>&1$").find(cmd)?.let { if (!wizardLocked) disabled += it.groupValues[1]; chosen = null; return "" }
            Regex("^pm enable --user 0 (\\S+)$").find(cmd)?.let { disabled -= it.groupValues[1]; chosen = null; return "" }
            Regex("^cmd package set-home-activity (\\S+)").find(cmd)?.let { chosen = it.groupValues[1]; return "" }
            Regex("^echo '(.*)' > ${LauncherSwap.REC}$").find(cmd)?.let { rec = it.groupValues[1]; return "" }
            return when {
                cmd == "cat ${LauncherSwap.REC} 2>/dev/null" -> rec
                cmd == "date +%s" -> (clock).toString()
                cmd == "pm list packages -e" -> (installed - disabled).joinToString("\n") { "package:$it" }
                cmd.startsWith("pm list packages -d") -> disabled.joinToString("\n") { "package:$it" }
                cmd.startsWith("cmd package query-activities") ->
                    homes.keys.filter { it in installed && it !in disabled && "$it/.Home" !in disabled }.joinToString("\n") { "priority=${homes[it]}\n$it/.Home" }
                cmd.contains("android.app.role.HOME") -> "priority=0\n" + home()
                else -> ""
            }
        }
    }

    @Test fun swapMakesItHomeAndRecordsWhatWentOff() {
        val tv = Tv(); val s = LauncherSwap({ tv.sh(it) })
        assertTrue(s.take("com.new.home", 30))
        assertEquals("com.new.home", tv.home().substringBefore('/'))
        val r = s.state()!!
        assertEquals("pending", r.state)
        assertEquals(setOf(Setup.LAUNCHERX, "$WIZ/.Home"), r.off.toSet())
        assertFalse(WIZ in tv.disabled)                                // its Home only: the whole app off cost an FPT box its saved Wi-Fi
        assertFalse("com.google.android.tvlauncher" in r.off)       // off from the factory: not ours to turn on
        s.keep(); assertEquals("kept", s.state()!!.state)
    }

    @Test fun rollbackPutsBackExactlyWhatWasOff() {
        val tv = Tv(); val s = LauncherSwap({ tv.sh(it) })
        s.take("com.new.home", 30)
        s.rollback("timeout")
        assertEquals(setOf("com.google.android.tvlauncher"), tv.disabled)
        assertEquals(Setup.LAUNCHERX, tv.home().substringBefore('/'))
        assertEquals("rolledback", s.state()!!.state)
        assertEquals("timeout", s.state()!!.why)
    }

    @Test fun aHomeThatDoesNotLandIsPutBackAtOnce() {
        val tv = Tv(pick = false); val s = LauncherSwap({ tv.sh(it) })
        assertFalse(s.take("com.new.home", 30))
        assertEquals(setOf("com.google.android.tvlauncher"), tv.disabled)
        assertEquals("rolledback", s.state()!!.state)
    }

    @Test fun aWizardHomeThatCannotGoOffStopsTheSwap() {
        val tv = Tv().apply { wizardLocked = true }; val s = LauncherSwap({ tv.sh(it) })
        assertFalse(s.take("com.new.home", 30))
        assertEquals(setOf("com.google.android.tvlauncher"), tv.disabled)
        assertFalse(tv.said.any { it == "pm disable-user --user 0 $WIZ" })
    }

    @Test fun aSwapThatOutlivedTheWatchdogIsUndoneAgain() {
        val tv = Tv(); val s = LauncherSwap({ c -> tv.sh(c).also { if (c.startsWith("cmd package set-home-activity")) LauncherSwap({ tv.sh(it) }).rollback("interrupted") } })
        assertFalse(s.take("com.new.home", 30))
        assertEquals(setOf("com.google.android.tvlauncher"), tv.disabled)
        assertEquals(Setup.LAUNCHERX, tv.home().substringBefore('/'))
    }

    @Test fun aTvWhoseWizardIsNoHomeKeepsItOn() {                    // the FPT Play box: the wizard is installed, not a home screen
        val tv = Tv().apply { homes.remove(WIZ) }; val s = LauncherSwap({ tv.sh(it) })
        assertTrue(s.take("com.new.home", 30))
        assertFalse(tv.said.any { WIZ in it && "disable" in it })
    }

    @Test fun aPackageWithNoHomeActivityIsRefusedUntouched() {
        val tv = Tv(); tv.installed += "com.other"
        assertFalse(LauncherSwap({ tv.sh(it) }).take("com.other", 30))
        assertEquals(setOf("com.google.android.tvlauncher"), tv.disabled)
    }

    @Test fun badPackageNamesAreRefused() {
        val tv = Tv()
        try { LauncherSwap({ tv.sh(it) }).take("x; reboot", 30); org.junit.Assert.fail() } catch (e: java.io.IOException) { }
        assertTrue(tv.said.none { "reboot" in it })
    }

    @Test fun watchdogWaitsForPendingThenRollsBackOnTimeout() {
        val tv = Tv(); val s = LauncherSwap({ tv.sh(it) }); val w = LauncherWatch({ tv.sh(it) })
        s.take("com.new.home", 30)
        assertNull(w.check())
        tv.clock += 30 + 20
        assertEquals("timeout", w.check())
        assertEquals(Setup.LAUNCHERX, tv.home().substringBefore('/'))
        assertNull(w.check())                                        // over: nothing left to watch
    }

    @Test fun watchdogRollsBackOnABadHomeAndOnlyAfterKeepToo() {
        val tv = Tv(); val s = LauncherSwap({ tv.sh(it) }); val w = LauncherWatch({ tv.sh(it) })
        s.take("com.new.home", 30); s.keep()
        assertNull(w.check())
        tv.installed -= "com.new.home"                               // it was removed: Home lands on the wizard
        assertEquals("home", w.check())
    }

    @Test fun crashesAreCountedPerPackageWithinFiveMinutes() {
        val log = """
            1760000000.123  1  1 E AndroidRuntime: FATAL EXCEPTION: main
            1760000000.124  1  1 E AndroidRuntime: Process: com.new.home, PID: 123
            1760000100.5  1  1 E AndroidRuntime: Process: com.other, PID: 9
            1760000200.1  1  1 F DEBUG   : pid: 5, tid: 5, name: x  >>> com.new.home <<<
        """.trimIndent()
        val t = LauncherWatch.crashes(log, "com.new.home")
        assertEquals(listOf(1760000000L, 1760000200L), t)
        assertTrue(LauncherWatch.looping(t, 1759999900))
        assertFalse(LauncherWatch.looping(t, 1760000100))                  // the first one is before the swap
        assertFalse(LauncherWatch.looping(listOf(1760000000L, 1760000400L), 0))  // more than five minutes apart
    }

    @Test fun badHomes() {
        assertTrue(LauncherSwap.badHome("android/com.android.internal.app.ResolverActivity"))
        assertTrue(LauncherSwap.badHome("com.google.android.tungsten.setupwraith/.RecoveryActivity"))
        assertTrue(LauncherSwap.badHome("com.android.settings/.FallbackHome"))
        assertFalse(LauncherSwap.badHome("com.new.home/.Home"))
    }
}
