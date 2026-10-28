package dev.tvremote.channels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TV Home's "Keep watching" live widget: its JSON for the three states, and who may ask for it. */
class ChannelLiveWidgetTest {
    private val w = ChannelLiveWidget.Words("Keep watching", "No channel yet", "Press to pick one", "Press to keep watching") { "Next $it" }
    private val hm = { t: Long -> "t$t" }
    private val vtv = Channel("VTV1.vn@SD", "VTV1", "https://x/vtv1.png", "General", "https://s/1.m3u8")

    @Test fun listOffersOneNextLiveWidget() {
        val l = ChannelLiveWidget.list(w).getJSONObject(0)
        assertEquals("next", l.getString("id")); assertEquals("next", l.getString("layout")); assertEquals("Keep watching", l.getString("title"))
    }

    @Test fun nothingWatchedIsAnEmptyStateThatOpensTheList() {
        val s = ChannelLiveWidget.state(w, null, null, null, hm)
        assertTrue(s.getBoolean("empty")); assertEquals("No channel yet", s.getString("big")); assertEquals("tvchannels://find", s.getString("open"))
        assertEquals("live", s.getString("mode"))
    }

    @Test fun lastChannelWithGuideShowsNowAndNext() {
        val s = ChannelLiveWidget.state(w, vtv, Show("News", 10, 20), Show("Film", 20, 30), hm, clock = 15)
        assertEquals("VTV1", s.getString("big")); assertEquals("News", s.getString("small")); assertEquals(0.5, s.getDouble("progress"), 1e-6); assertEquals("Next t20  Film", s.getString("line2"))
        assertEquals("https://x/vtv1.png", s.getString("logo")); assertEquals("tvchannels://play/VTV1.vn%40SD", s.getString("open"))
        assertFalse(s.has("empty"))
    }

    @Test fun aShowWithNoLengthKeepsItsTimeAndHasNoBar() {
        val s = ChannelLiveWidget.state(w, vtv, Show("News", 10, 10), null, hm)
        assertEquals("t10  News", s.getString("small")); assertFalse(s.has("progress"))
    }

    @Test fun unknownGuideInvitesToPlay() {
        val s = ChannelLiveWidget.state(w, vtv.copy(logo = ""), null, null, hm)
        assertEquals("Press to keep watching", s.getString("small")); assertFalse(s.has("line2")); assertFalse(s.has("logo"))
    }

    @Test fun onlyTheHomeKeyOrOurOwnIsTrusted() {
        assertTrue(HomeInfo.certOk(listOf(HomeInfo.HOME_CERT), listOf("aa")))
        assertTrue(HomeInfo.certOk(listOf("aa"), listOf("aa")))                      // a debug build of both
        assertFalse(HomeInfo.certOk(listOf("bb"), listOf("aa")))
        assertFalse(HomeInfo.certOk(emptyList(), listOf("aa")))
    }
}
