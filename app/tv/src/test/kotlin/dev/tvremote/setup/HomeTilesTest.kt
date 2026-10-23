package dev.tvremote.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TV Home's RAM tile: the numbers and words it shows, and which packages a press may close. */
class HomeTilesTest {
    private fun m(total: Long, avail: Long) = HomeTiles.Mem(total, avail)

    @Test fun percentIsUsedOverTotal() {
        assertEquals(43, m(2000, 1140).pct)
        assertEquals(0, m(0, 0).pct)                                   // an unreadable /proc/meminfo
        assertEquals(100, m(1000, -5).pct)
        assertEquals(0, m(1000, 1200).pct)
    }

    @Test fun beforeAfterText() {
        val r = HomeTiles.Result(m(2000, 1140), m(2000, 1420), 0)
        assertEquals(280, r.freedMb)
        assertFalse(r.none)
        assertEquals("43% → 29% · −280 MB", HomeTiles.doneText("en", r.before, r.after, r.freedMb))
        assertEquals("43% → 29% · −280 MB", HomeTiles.doneText("vi", r.before, r.after, r.freedMb))
    }

    @Test fun nothingFreedIsSaidHonestly() {
        val r = HomeTiles.Result(m(2000, 1140), m(2000, 1130), 0)      // memory even went down: no "freed" below zero
        assertEquals(0, r.freedMb)
        assertTrue(r.none)
        assertEquals("Nothing more to free · 44%", HomeTiles.doneText("en", r.before, r.after, r.freedMb))
        assertEquals("Không còn gì để dọn · 44%", HomeTiles.doneText("vi", r.before, r.after, r.freedMb))
        assertTrue(HomeTiles.Result(m(2000, 1140), m(2000, 1150), 0).none)   // 10 MB is noise
    }

    @Test fun accentFollowsHowFull() {
        assertEquals("ok", HomeTiles.accent(69)); assertEquals("warn", HomeTiles.accent(70)); assertEquals("bad", HomeTiles.accent(85))
    }

    @Test fun stateIsLiveThenDoneThenLiveAgain() {
        val last = HomeTiles.Result(m(2000, 1140), m(2000, 1420), 1000)
        val live = HomeTiles.state("en", m(2000, 1140), false, null, false, 500)
        assertEquals("live", live.getString("mode")); assertEquals("43%", live.getString("big")); assertEquals("1140 MB free", live.getString("small"))
        assertEquals(0.43, live.getDouble("progress"), 1e-9)
        val done = HomeTiles.state("en", m(2000, 1420), false, last, false, 2000)
        assertEquals("done", done.getString("mode")); assertEquals("29%", done.getString("big"))
        assertEquals("43% → 29% · −280 MB", done.getJSONObject("result").getString("text"))
        assertEquals(43, done.getJSONObject("result").getInt("before")); assertEquals(29, done.getJSONObject("result").getInt("after"))
        assertEquals("live", HomeTiles.state("en", m(2000, 1420), false, last, false, 1000 + HomeTiles.DONE_MS + 1).getString("mode"))
        assertEquals("working", HomeTiles.state("en", m(2000, 1140), true, last, false, 2000).getString("mode"))
        assertEquals("failed", HomeTiles.state("en", m(2000, 1140), false, null, true, 2000).getString("mode"))
    }

    @Test fun packagesOfPsText() {
        val ps = "NAME\nsystem_server\n[kworker/0:1]\ncom.netflix.ninja\ncom.google.android.youtube.tv:player\ncom.netflix.ninja:remote\nzygote64\n/system/bin/sh\n"
        assertEquals(listOf("com.netflix.ninja", "com.google.android.youtube.tv"), HomeTiles.packagesOf(ps))
    }

    @Test fun neverClosesTheProjectTheScreenOrTheSystem() {
        val front = "com.netflix.ninja"
        val safe = setOf("com.mitv.launcher", "com.google.android.inputmethod.latin", "com.acme.access")   // launcher, keyboard, accessibility
        val running = listOf("dev.tvremote.home", "dev.tvremote.setup", "dev.tvremote.channels", "com.netflix.ninja", "com.mitv.launcher",
            "com.google.android.inputmethod.latin", "com.acme.access", "com.android.systemui", "com.google.android.gms", "android.process.acore",
            "com.tailscale.ipn", "com.spotify.tv.android", "com.disney.disneyplus")
        assertEquals(listOf("com.spotify.tv.android", "com.disney.disneyplus"), HomeTiles.targets(running, front, safe))
        assertTrue(HomeTiles.protectedPkg("dev.tvremote.home", "", emptySet()))
        assertTrue(HomeTiles.protectedPkg("dev.tvremote.setup", "", emptySet()))
    }
}
