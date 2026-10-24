package dev.tvremote.karaoke

import org.junit.Assert.*
import org.junit.Test

class QueueTest {
    private fun song(n: Int) = Song("vid%08d".format(n), "Song $n", "t$n")

    @Test fun firstSongPlaysAtOnceTheRestWait() {
        val q = KaraokeQueue()
        q.add(song(1), "An", "a"); q.add(song(2), "Bình", "b"); q.add(song(3), "An", "a")
        assertEquals("Song 1", q.now!!.song.title); assertEquals(listOf("Song 2", "Song 3"), q.upcoming.map { it.song.title })
    }

    @Test fun advanceMovesToHistoryAndEndsEmpty() {
        val q = KaraokeQueue(); q.add(song(1), "An", "a"); q.add(song(2), "An", "a")
        assertEquals("Song 2", q.advance()!!.song.title); assertEquals(1, q.history.size)
        assertNull(q.advance()); assertNull(q.now); assertEquals(2, q.history.size)
        q.add(song(3), "An", "a"); assertEquals("Song 3", q.now!!.song.title)               // singing starts again by itself
    }

    @Test fun onlyTheOwnerMovesOrRemoves() {
        val q = KaraokeQueue(); q.add(song(0), "x", "x"); q.add(song(1), "An", "a"); q.add(song(2), "Bình", "b"); q.add(song(3), "An", "a")
        val one = q.upcoming[0].qid; val two = q.upcoming[1].qid; val three = q.upcoming[2].qid
        assertFalse(q.remove(two, "a")); assertFalse(q.move(two, -1, "a"))                    // Bình's song, An asks
        assertTrue(q.move(three, -1, "a")); assertEquals(listOf(one, three, two), q.upcoming.map { it.qid })
        assertFalse(q.move(one, -1, "a"))                                                      // already first
        assertFalse(q.move(two, 1, "b"))                                                       // already last
        assertTrue(q.remove(one, "a")); assertEquals(listOf(three, two), q.upcoming.map { it.qid })
        assertTrue(q.remove(two, "tv", force = true))                                          // the TV may
        assertFalse(q.remove(999, "a"))
    }

    @Test fun replayBumpsTheTickOnlyWhenSomethingPlays() {
        val q = KaraokeQueue(); assertFalse(q.replay()); q.add(song(1), "An", "a"); assertTrue(q.replay()); assertEquals(1, q.replayTick)
    }

    @Test fun queueIsCappedAndNamesAreCleaned() {
        val q = KaraokeQueue(); repeat(KaraokeQueue.MAX_QUEUE + 1) { assertNotNull(q.add(song(it), "n", "c")) }     // 1 playing + MAX waiting
        assertNull(q.add(song(999), "n", "c"))
        assertEquals("Ân", KaraokeQueue.cleanName("  <Ân>\n")); assertEquals("?", KaraokeQueue.cleanName("<>")); assertEquals(16, KaraokeQueue.cleanName("x".repeat(40)).length)
    }

    @Test fun videoIdsAreChecked() {
        assertTrue(KaraokeQueue.validId("ekFIGh0cbRU")); assertFalse(KaraokeQueue.validId("short")); assertFalse(KaraokeQueue.validId("ekFIGh0cbR'"))
        assertFalse(KaraokeQueue.validId("ekFIGh0cbRU\");alert(1)"))                          // never reaches the player page's script
    }

    @Test fun saveAndLoadKeepIdsOnlyAndPutNowFirst() {
        val q = KaraokeQueue(); q.add(song(1), "An", "a"); q.add(song(2), "Bình", "b"); q.advance(); q.add(song(3), "An", "a")
        val raw = q.save()
        val r = KaraokeQueue(); r.load(raw)
        assertNull(r.now); assertEquals(listOf("Song 2", "Song 3"), r.upcoming.map { it.song.title }); assertEquals(1, r.history.size)
        r.add(song(4), "x", "x"); assertEquals(4, (r.upcoming + r.history + listOfNotNull(r.now)).map { it.qid }.toSet().size)     // new ids do not repeat old ones
        val bad = KaraokeQueue(); bad.load("{not json"); assertNull(bad.now); assertTrue(bad.upcoming.isEmpty())
        val evil = KaraokeQueue(); evil.load("{\"queue\":[{\"vid\":\"x');evil()\",\"title\":\"t\"}]}"); assertTrue(evil.upcoming.isEmpty())
    }

    @Test fun jsonForPhones() {
        val q = KaraokeQueue(); q.add(song(1), "An", "a"); q.add(song(2), "Bình", "b")
        val j = q.json(); assertEquals("Song 1", j.getJSONObject("now").getString("title")); assertEquals(1, j.getJSONArray("queue").length())
    }
}
