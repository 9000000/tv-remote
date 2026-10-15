package dev.tvremote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupNeedTest {
    @Test fun versions() {
        assertTrue(SetupNeed.ok("2.21.0", "pic"))
        assertTrue(SetupNeed.ok("2.21.3", "pic"))
        assertFalse(SetupNeed.ok("2.20.0", "pic"))
        assertTrue(SetupNeed.ok("2.20.0", "draw"))
        assertFalse(SetupNeed.ok("", "draw"))                      // no TV Setup at all
        assertTrue(SetupNeed.ok("", "unknown"))                     // a feature not in the table needs nothing
    }

    @Test fun words() {                                             // the words js/core.js (SETUP_RE) reads back from a server
        assertEquals("setup:old:pic:2.21.0:2.20.0", SetupNeed.Missing("pic", "2.20.0").message)
        assertEquals("setup:missing:draw:2.18.0:", SetupNeed.Missing("draw", "").message)
        assertEquals("Đưa ảnh lên TV cần TV Setup 2.21.0 trở lên (TV đang có 2.20.0). Cập nhật TV Setup?",
            SetupNeed.say(java.io.IOException("2: " + SetupNeed.Missing("pic", "2.20.0").message), true))
        assertEquals("Drawing on the TV needs the TV Setup app on the TV. Install TV Setup?", SetupNeed.say(SetupNeed.Missing("draw", ""), false))
        assertNull(SetupNeed.say(java.io.IOException("timed out"), true))
    }
}
