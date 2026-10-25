package dev.tvremote.setup

import org.junit.Assert.assertEquals
import org.junit.Test

/** TV Home's Control Center may only switch kids mode ON by itself: "kids:off" opens the PIN screen and changes no rules. */
class HomeControlTest {
    @Test fun kidsOffOnlyOpensThePinScreen() {
        assertEquals("pin", HomeControl.route("kids:off"))
        assertEquals("manage", HomeControl.route("kids:setup"))
    }

    @Test fun onlyTheKnownWordsChangeTheRules() {
        assertEquals("rules", HomeControl.route("kids:on"))
        assertEquals("rules", HomeControl.route("sleep:30"))
        assertEquals("bad", HomeControl.route("sleep:7"))
        assertEquals("bad", HomeControl.route("kids:list"))
        assertEquals("ask", HomeControl.route(""))
    }
}
