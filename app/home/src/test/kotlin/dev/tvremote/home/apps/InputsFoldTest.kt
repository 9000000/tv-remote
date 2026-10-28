package dev.tvremote.home.apps

import dev.tvremote.home.HomeConfig
import dev.tvremote.home.ui.Drop
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Inputs fold option and the Settings fallback order (the Android parts are tried on a TV). */
class InputsFoldTest {
    private val CONNECTED = 0; private val STANDBY = 1; private val DISCONNECTED = 2; private val UNKNOWN = -1
    private fun row(label: String, group: Int, folds: Boolean) = Drop.Row(0, label, null, group, folds)

    @Test fun jacksAlwaysFold() { for (s in listOf(CONNECTED, DISCONNECTED, UNKNOWN)) assertTrue(Inputs.foldable(1, s, false)) }
    @Test fun hdmiFoldsOnlyWhenTrustedAndDisconnected() {
        assertTrue(Inputs.foldable(0, DISCONNECTED, false))
        assertFalse(Inputs.foldable(0, CONNECTED, false))
        assertFalse(Inputs.foldable(0, STANDBY, false))         // a device that is there
        assertFalse(Inputs.foldable(0, UNKNOWN, false))         // the TV does not say
        assertFalse(Inputs.foldable(0, DISCONNECTED, true))     // every port "connected" earlier: the answers mean nothing
    }
    @Test fun tunerNeverFolds() = assertFalse(Inputs.foldable(2, DISCONNECTED, false))

    @Test fun foldMovesRestBehindOneRow() {
        val rows = listOf(row("HDMI 1", 0, false), row("HDMI 2", 0, true), row("AV", 1, true), row("TV", 2, false))
        val out = Inputs.foldRows(rows, "More (2)")
        assertEquals(listOf("HDMI 1", "TV", "More (2)"), out.map { it.label })
        assertEquals(listOf("HDMI 2", "AV"), out.last().more!!.map { it.label })
    }
    @Test fun nothingFoldedLeavesTheListAlone() {
        val rows = listOf(row("HDMI 1", 0, false), row("TV", 2, false))
        assertEquals(rows, Inputs.foldRows(rows, "x"))
    }
    @Test fun neverLeavesAnEmptyMenu() {
        val rows = listOf(row("HDMI 1", 0, true), row("AV", 1, true))
        assertEquals(rows, Inputs.foldRows(rows, "x"))
    }

    @Test fun foldIsOffByDefault() {
        assertFalse(HomeConfig.clean(JSONObject()).getBoolean("infold"))
        assertTrue(HomeConfig.clean(JSONObject().put("infold", true)).getBoolean("infold"))
    }

    @Test fun settingsFallbackOrder() {
        assertEquals(listOf("com.xiaomi.mitv.settings", "com.tcl.settings", "com.amazon.tv.launcher", "com.android.tv.settings"), SystemSettings.VENDOR.map { it.first })
        assertEquals("com.tcl.settings", SystemSettings.firstThat(SystemSettings.VENDOR) { it.first != "com.xiaomi.mitv.settings" }!!.first)
        assertNull(SystemSettings.firstThat(SystemSettings.VENDOR) { false })
        assertNotNull(SystemSettings.firstThat(SystemSettings.VENDOR) { true })
    }
}
