package dev.tvremote.core

import dev.tvremote.core.Voice.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The same cases as app/web/tests/voice.test.js: the page and the phone app must understand the same sentences. */
class VoiceTest {
    private val apps = mapOf("com.google.android.youtube.tv" to "YouTube", "com.netflix.ninja" to "Netflix", "com.fptplay.tv" to "FPT Play", "org.videolan.vlc" to "VLC")
    private val scenes = mapOf("s1" to "Đi ngủ", "s2" to "Xem phim")
    private fun p(t: String) = Voice.parse(t, apps, scenes)

    @Test fun fold() {
        assertEquals("mo youtube", Voice.fold("  Mở YouTube! "))
        assertEquals("dat hen gio", Voice.fold("Đặt hẹn giờ"))
    }

    @Test fun apps() {
        assertEquals(Command.App("com.google.android.youtube.tv", "YouTube"), p("mở YouTube"))
        assertEquals("com.netflix.ninja", (p("mo netflix") as Command.App).pkg)
        assertEquals("com.fptplay.tv", (p("Xem FPT Play") as Command.App).pkg)
        assertEquals("org.videolan.vlc", (p("open vlc") as Command.App).pkg)
        assertEquals("com.google.android.youtube.tv", (p("mở ứng dụng youtube") as Command.App).pkg)
    }

    @Test fun unknownGoesToTheAssistant() {
        assertNull(p("mở Spotify"))
        assertNull(p("thời tiết hôm nay"))
        assertNull(p(""))
    }

    @Test fun keys() {
        assertEquals(Command.Key("MUTE", 1), p("tắt tiếng"))
        assertEquals(Command.Key("PLAY_PAUSE", 1), p("Tạm dừng"))
        assertEquals(Command.Key("HOME", 1), p("về màn hình chính"))
        assertEquals(Command.Key("SLEEP", 1), p("tắt TV"))
        assertEquals(Command.Key("WAKE", 1), p("bật tivi"))
        assertEquals(Command.Key("WAKE", 1), p("bật tv"))
    }

    @Test fun volume() {
        assertEquals(Command.Key("VOL_UP", 3), p("tăng âm lượng"))
        assertEquals(Command.Key("VOL_DOWN", 5), p("giảm âm lượng 5"))
        assertEquals(Command.Key("VOL_UP", 15), p("volume up 99"))
    }

    @Test fun timer() {
        assertEquals(Command.Timer(30), p("hẹn giờ tắt 30 phút"))
        assertEquals(Command.Timer(120), p("tắt TV sau 2 tiếng"))
        assertEquals(Command.Timer(45), p("sleep in 45 minutes"))
        assertEquals(Command.Timer(0), p("hủy hẹn giờ"))
    }

    @Test fun scenes() {
        assertEquals(Command.Scene("s1"), p("đi ngủ"))
        assertEquals(Command.Scene("s2"), p("cảnh xem phim"))
    }
}
