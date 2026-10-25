package dev.tvremote.home.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the Control Center shows its Inputs tile. */
class InputsTest {
    @Test fun listedInputsShowTheTile() = assertTrue(Inputs.showTile(listed = 3, liveApp = false, box = true))
    @Test fun aLiveTvAppShowsItWithNothingListed() = assertTrue(Inputs.showTile(0, true, true))
    @Test fun aBoxWithNoInputsHasNone() = assertFalse(Inputs.showTile(0, false, true))
    @Test fun anUnknownTvKeepsItAndOpensSettings() = assertTrue(Inputs.showTile(0, false, false))

    @Test fun philipsTypesAreDropped() {
        assertEquals("Xbox", Inputs.tidy("Xbox&GAME_CONSOLE"))
        assertEquals("Switch", Inputs.tidy("Switch &GAME_CONSOLE"))
        assertNull(Inputs.tidy("&GAME_CONSOLE"))
        assertEquals("Tom & Jerry", Inputs.tidy("Tom & Jerry"))
    }
    @Test fun nullAndBlankFallBackToThePort() {
        assertNull(Inputs.tidy("null")); assertNull(Inputs.tidy("  ")); assertNull(Inputs.tidy(null))
        assertEquals("HDMI 4", Inputs.name("null", "HDMI 4", "com.x/.Hdmi4"))
        assertEquals("Xbox", Inputs.name("Xbox&GAME_CONSOLE", "HDMI 1", "id"))
        assertEquals("Hdmi4", Inputs.name("", "null", "com.x/.Hdmi4"))
    }
}
