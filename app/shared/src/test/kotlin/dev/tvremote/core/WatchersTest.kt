package dev.tvremote.core

import dev.tvremote.core.picture.Feeds
import dev.tvremote.core.picture.Watchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchersTest {
    // as the TV emulator (Android 14) listed them: two pictures, one with sound, one session for keys only
    private val ps = """
        ARGS
        init second_stage
        app_process / com.genymobile.scrcpy.Server 3.3.4 scid=25de71c6 log_level=info video=true audio=false control=true
        app_process / com.genymobile.scrcpy.Server 3.3.4 scid=714749f0 log_level=info video=true audio=true audio_source=playback control=false
        app_process / com.genymobile.scrcpy.Server 3.3.4 scid=26c4dad4 log_level=info video=false audio=false control=true
        app_process /system/bin com.android.commands.monkey.Monkey video=true
        sh -c grep com.genymobile.scrcpy.Server video=true
    """.trimIndent()

    @Test fun countsPicturesAndSoundNotKeys() {
        val c = Watchers.parse(ps)!!
        assertEquals(2, c.watch)
        assertEquals(1, c.listen)
    }

    @Test fun anOldPsIsNoNumber() {
        assertNull(Watchers.parse("USER PID PPID VSIZE RSS WCHAN PC NAME\nroot 1 0 1 1 0 0 S /init"))   // Android 7: no ARGS column
        assertNull(Watchers.parse(""))                                // no ADB
    }

    @Test fun nobodyBrowsingAddsNothing() {
        val o = Feeds().count(ps)
        assertEquals(2, o.getInt("watch"))
        assertEquals(1, o.getInt("listen"))
        assertEquals(0, Feeds().count("").length())
    }
}
