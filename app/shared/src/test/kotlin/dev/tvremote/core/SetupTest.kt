package dev.tvremote.core

import dev.tvremote.core.ontv.Setup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * Optimize TV and its Undo (Setup.kt) on a made-up TV that keeps what the shell commands do: an app it must never turn off
 * (the TV stops working), a name that would run a second command, Undo missing one it turned off. Wrong here is a TV with no
 * picture or no home screen in someone's living room.
 */
class SetupTest {
    private class FakeTv(val tv: Boolean = true, vararg apps: String) {
        val installed = apps.toMutableSet()
        val disabled = mutableSetOf<String>()
        val offList = mutableListOf<String>()
        val said = mutableListOf<String>()
        var anim = "1.0"
        fun sh(cmd: String): String {
            said += cmd
            Regex("^pm path (\\S+)$").find(cmd)?.let { return if (it.groupValues[1] in installed) "package:/data/app/x.apk" else "" }
            Regex("^pm disable-user --user 0 (\\S+); am force-stop \\S+$").find(cmd)?.let { disabled += it.groupValues[1]; return "" }
            Regex("^pm enable --user 0 (\\S+)$").find(cmd)?.let { disabled -= it.groupValues[1]; return "" }
            Regex("^grep -qx '([^']+)' ${Setup.OFF_LIST} 2>/dev/null \\|\\| echo '\\1' >> ${Setup.OFF_LIST}$").find(cmd)?.let { if (it.groupValues[1] !in offList) offList += it.groupValues[1]; return "" }
            return when {
                cmd == "pm has-feature android.software.leanback" -> tv.toString()
                cmd.startsWith("pm list packages -d") -> disabled.joinToString("\n") { "package:$it" }
                cmd == "cat ${Setup.OFF_LIST} 2>/dev/null" -> offList.joinToString("\n")
                cmd == "rm -f ${Setup.OFF_LIST}" -> { offList.clear(); "" }
                cmd.startsWith("settings put global window_animation_scale 0") -> { anim = "0"; "" }
                cmd.startsWith("for k in ${Setup.ANIM}; do settings put global \$k 1.0") -> { anim = "1.0"; "" }
                cmd.startsWith("cmd package resolve-activity") -> "me.efesser.flauncher/.MainActivity"
                else -> ""
            }
        }
        fun setup() = Setup(null, null, "en", { sh(it) }) { }
    }

    private val musts = arrayOf("android", "com.android.systemui", "com.android.tv.settings", "com.google.android.gms", "com.google.android.tvlauncher",
        "com.google.android.katniss", "dev.tvremote.setup", "com.tailscale.ipn", "me.efesser.flauncher")

    @Test fun whatKeepsTheTvWorkingIsNeverTurnedOff() {
        val tv = FakeTv(true, *(musts.toList() - Setup.TS_PKG).toTypedArray(), "com.foo.game")   // installed, Tailscale would be kept alive over ADB
        tv.setup().apply(Setup.Choice(anim = false, home = false, disable = musts.toList() + "com.foo.game"))
        assertEquals(setOf("com.foo.game"), tv.disabled)
    }

    @Test fun aNameThatWouldRunAnotherCommandIsSkipped() {
        val bad = listOf("com.foo; reboot", "com.foo'x", "com.foo && rm -rf /sdcard", "\$(reboot)", "foo")
        val tv = FakeTv(true, *bad.toTypedArray())
        tv.setup().apply(Setup.Choice(anim = false, home = false, disable = bad))
        assertTrue(tv.disabled.isEmpty())
        assertFalse(tv.said.any { "reboot" in it || "rm -rf" in it })
    }

    @Test fun notInstalledIsNotCounted() {
        val tv = FakeTv(true, "com.foo.game")
        tv.setup().apply(Setup.Choice(anim = false, home = false, disable = listOf("com.foo.game", "com.foo.gone")))
        assertEquals(setOf("com.foo.game"), tv.disabled)
        assertEquals(listOf("com.foo.game"), tv.offList)
    }

    @Test fun theHomeScreenGoesOnlyOnceFLauncherIsThere() {
        val without = FakeTv(true, *Setup.LAUNCHER)
        without.setup().apply(Setup.Choice(anim = false, home = false, disable = emptyList()))
        assertTrue(without.disabled.isEmpty())                        // no home: the launcher stays
        val with = FakeTv(true, *Setup.LAUNCHER, Setup.FL_PKG)
        with.setup().apply(Setup.Choice(anim = false, home = true, disable = emptyList()))
        assertEquals(Setup.LAUNCHER.toSet(), with.disabled)
    }

    @Test fun undoTurnsBackOnAllItTurnedOff() {
        val tv = FakeTv(true, *Setup.GOOGLE, *Setup.LAUNCHER, Setup.FL_PKG, "com.foo.game", "com.bar.shop")
        tv.setup().apply(Setup.Choice(anim = true, home = true, disable = Setup.GOOGLE.toList() + "com.foo.game" + "com.bar.shop"))
        assertEquals(Setup.GOOGLE.size + Setup.LAUNCHER.size + 2, tv.disabled.size)
        assertEquals("0", tv.anim)
        assertTrue(tv.setup().optimized())
        tv.disabled += "com.user.own"                                 // turned off by the person, not by us: left as it is
        tv.setup().undo()
        assertEquals(setOf("com.user.own"), tv.disabled)
        assertTrue(tv.offList.isEmpty())
        assertEquals("1.0", tv.anim)
        assertFalse(tv.setup().optimized())
    }

    @Test fun optimizedSeesAnAppOnlyWeTurnedOff() {                   // so the page offers Undo for a TV optimized with a hand-picked list
        val tv = FakeTv(true, "com.foo.game")
        assertFalse(tv.setup().optimized())
        tv.setup().apply(Setup.Choice(anim = false, home = false, disable = listOf("com.foo.game")))
        assertTrue(tv.setup().optimized())
    }

    @Test fun aPhoneIsNotOptimized() {
        val tv = FakeTv(false, "com.foo.game")
        try { tv.setup().apply(Setup.Choice(disable = listOf("com.foo.game"))); fail("a phone was optimized") } catch (e: IOException) { }
        assertTrue(tv.disabled.isEmpty())
    }
}
