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
        assertEquals(listOf("dev.tvremote.channels", "com.spotify.tv.android", "com.disney.disneyplus"), HomeTiles.targets(running, front, safe))   // TV Channels is an ordinary app; TV Home and TV Setup are spared
        assertTrue(HomeTiles.protectedPkg("dev.tvremote.home", "", emptySet()))
        assertTrue(HomeTiles.protectedPkg("dev.tvremote.setup", "", emptySet()))
    }

    @Test fun sleepPressAddsFifteenUpToTwoHoursThenOff() {
        assertEquals(15, HomeTiles.nextSleep(0))                          // off -> 15
        assertEquals(30, HomeTiles.nextSleep(15)); assertEquals(45, HomeTiles.nextSleep(30)); assertEquals(120, HomeTiles.nextSleep(105))
        assertEquals(45, HomeTiles.nextSleep(25))                         // 30 running down: still the 30 step
        assertEquals(30, HomeTiles.nextSleep(1)); assertEquals(0, HomeTiles.nextSleep(120))
        assertEquals(0, HomeTiles.nextSleep(150))                         // set longer elsewhere: a press switches it off
    }

    @Test fun sleepLeftRoundsUp() {
        assertEquals(0, HomeTiles.sleepLeft(0, 1000)); assertEquals(0, HomeTiles.sleepLeft(1000, 1000))
        assertEquals(1, HomeTiles.sleepLeft(1001, 1000)); assertEquals(30, HomeTiles.sleepLeft(1000 + 30 * 60_000L, 1000))
    }

    @Test fun sleepStateIsAToggleInBothLanguages() {
        val on = HomeTiles.sleepState("en", 60)
        assertEquals("toggle", on.getString("layout")); assertEquals("60 min", on.getString("big")); assertTrue(on.getBoolean("on")); assertEquals("Press: 75 min", on.getString("small"))
        val off = HomeTiles.sleepState("vi", 0)
        assertEquals("Tắt", off.getString("big")); assertFalse(off.getBoolean("on")); assertEquals("Bấm: 15 phút", off.getString("small"))
        assertEquals("Bấm: tắt", HomeTiles.sleepState("vi", 120).getString("small"))
        assertEquals("Hẹn giờ tắt", HomeTiles.list("vi").getJSONObject(1).getString("title"))
        assertEquals("gauge", HomeTiles.list("en").getJSONObject(0).getString("layout"))
    }

    @Test fun watchTileIsAGaugeOfTodayWithYesterdayUnder() {
        val vi = HomeTiles.watchState("vi", 3 * 3600L + 12 * 60, 2 * 3600L + 40 * 60)
        assertEquals("gauge", vi.getString("layout")); assertEquals("3g12", vi.getString("big")); assertEquals("Hôm qua 2g40", vi.getString("small"))
        assertEquals(0.4, vi.getDouble("progress"), 0.001); assertEquals("ok", vi.getString("accent"))
        val en = HomeTiles.watchState("en", 45 * 60L, 0)
        assertEquals("45m", en.getString("big")); assertEquals("Yest. 0m", en.getString("small"))
        assertEquals(1.0, HomeTiles.watchState("en", 12 * 3600L, 0).getDouble("progress"), 0.001)
        assertEquals("warn", HomeTiles.watchAccent(6 * 3600L)); assertEquals("bad", HomeTiles.watchAccent(10 * 3600L))
        assertEquals("watch", HomeTiles.list("en").getJSONObject(2).getString("id"))
    }
}
