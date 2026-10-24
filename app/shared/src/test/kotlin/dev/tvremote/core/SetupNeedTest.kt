package dev.tvremote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.tvremote.core.ontv.RulesClient
import dev.tvremote.core.B64
import dev.tvremote.core.ontv.Setup
import dev.tvremote.core.ontv.SetupNeed

class SetupNeedTest {
    @Test fun versions() {
        assertTrue(SetupNeed.ok("0.1.0", "pic"))
        assertTrue(SetupNeed.ok("0.3.2", "pic"))
        assertFalse(SetupNeed.ok("2.21.4", "pic"))                  // the numbers before the restart at 0.1.0 are older
        assertFalse(SetupNeed.ok("1.6.0", "draw"))
        assertFalse(SetupNeed.ok("", "draw"))                      // no TV Setup at all
        assertTrue(SetupNeed.ok("", "unknown"))                     // a feature not in the table needs nothing
    }

    @Test fun severalScheduleWindowsNeedTheNewerSetupButOneWindowDoesNot() {
        assertFalse(SetupNeed.ok("0.21.0", "schedule_windows"))
        assertTrue(SetupNeed.ok("0.21.1", "schedule_windows"))
        assertTrue(SetupNeed.ok("0.21.0", "schedule"))                  // the old shape (one window) is the older feature
        assertEquals("setup:old:schedule_windows:0.21.1:0.21.0", SetupNeed.Missing("schedule_windows", "0.21.0").message)
    }

    @Test fun words() {                                             // the words js/core.js (SETUP_RE) reads back from a server
        assertEquals("setup:old:pic:0.1.0:2.20.0", SetupNeed.Missing("pic", "2.20.0").message)
        assertEquals("setup:missing:draw:0.1.0:", SetupNeed.Missing("draw", "").message)
        assertEquals("Đưa ảnh lên TV cần TV Setup 0.1.0 trở lên (TV đang có 2.20.0). Cập nhật TV Setup?",
            SetupNeed.say(java.io.IOException("2: " + SetupNeed.Missing("pic", "2.20.0").message), "vi"))
        assertEquals("Drawing on the TV needs the TV Setup app on the TV. Install TV Setup?", SetupNeed.say(SetupNeed.Missing("draw", ""), "en"))
        assertNull(SetupNeed.say(java.io.IOException("timed out"), "vi"))
    }

    @Test fun rulesWithNoAnswer() {
        // the broadcast finds no receiver: what dumpsys says decides whether TV Setup is missing, old, or only starting again
        fun kind(version: String?) = RulesClient.call({ c -> if (c.startsWith("dumpsys")) version?.let { "    versionName=$it" } ?: "" else "Broadcast completed: result=0" }, null).optString("setup")
        assertEquals("missing", kind(null))
        assertEquals("old", kind("0.0.9"))
        assertEquals("busy", kind("0.3.0"))                           // being updated: no Update button for it
    }

    @Test fun aSentKeyIsActedOnOnlyWhenTvSetupSaysSo() {
        fun answer(json: String) = "Broadcast completed: result=-1, data=\"" + B64.encode(json.toByteArray()) + "\""
        assertTrue(RulesClient.acted(answer("""{"acted":true}""")))
        assertFalse(RulesClient.acted(answer("""{"acted":false}""")))      // nothing set for the key: the client presses it
        assertFalse(RulesClient.acted(answer("""{"error":"unknown action"}""")))   // an old TV Setup
        assertFalse(RulesClient.acted("Broadcast completed: result=0"))      // none
        assertFalse(RulesClient.key({ throw java.io.IOException("gone") }, 183))
    }

    @Test fun theGamesPadAddress() {
        val g = dev.tvremote.core.ontv.Games
        assertEquals("http://10.0.0.5:8780/?k=0123456789abcdef", g.padUrl("8780 0123456789abcdef\n", "10.0.0.5"))
        assertNull(g.padUrl("", "10.0.0.5")); assertNull(g.padUrl("8780 short", "10.0.0.5")); assertNull(g.padUrl("x 0123456789abcdef", "10.0.0.5"))
    }
}
