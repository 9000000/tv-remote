package dev.tvremote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupNeedTest {
    @Test fun versions() {
        assertTrue(SetupNeed.ok("0.1.0", "pic"))
        assertTrue(SetupNeed.ok("0.3.2", "pic"))
        assertFalse(SetupNeed.ok("2.21.4", "pic"))                  // the numbers before the restart at 0.1.0 are older
        assertFalse(SetupNeed.ok("1.6.0", "draw"))
        assertFalse(SetupNeed.ok("", "draw"))                      // no TV Setup at all
        assertTrue(SetupNeed.ok("", "unknown"))                     // a feature not in the table needs nothing
    }

    @Test fun words() {                                             // the words js/core.js (SETUP_RE) reads back from a server
        assertEquals("setup:old:pic:0.1.0:2.20.0", SetupNeed.Missing("pic", "2.20.0").message)
        assertEquals("setup:missing:draw:0.1.0:", SetupNeed.Missing("draw", "").message)
        assertEquals("Đưa ảnh lên TV cần TV Setup 0.1.0 trở lên (TV đang có 2.20.0). Cập nhật TV Setup?",
            SetupNeed.say(java.io.IOException("2: " + SetupNeed.Missing("pic", "2.20.0").message), true))
        assertEquals("Drawing on the TV needs the TV Setup app on the TV. Install TV Setup?", SetupNeed.say(SetupNeed.Missing("draw", ""), false))
        assertNull(SetupNeed.say(java.io.IOException("timed out"), true))
    }

    @Test fun rulesWithNoAnswer() {
        // the broadcast finds no receiver: what dumpsys says decides whether TV Setup is missing, old, or only starting again
        fun kind(version: String?) = RulesClient.call({ c -> if (c.startsWith("dumpsys")) version?.let { "    versionName=$it" } ?: "" else "Broadcast completed: result=0" }, null).optString("setup")
        assertEquals("missing", kind(null))
        assertEquals("old", kind("0.0.9"))
        assertEquals("busy", kind("0.3.0"))                           // being updated: no Update button for it
    }
}
