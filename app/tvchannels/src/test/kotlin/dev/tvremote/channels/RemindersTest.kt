package dev.tvremote.channels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemindersTest {
    private val m = 60_000L
    private val now = 1_000_000_000L

    @Test fun twoMinutesBeforeWhenThereIsTime() = assertEquals(now + 8 * m, Reminders.fireAt(now + 10 * m, now))
    @Test fun atTheStartWhenLessThanTheLeadIsLeft() = assertEquals(now + m, Reminders.fireAt(now + m, now))
    @Test fun exactlyTheLeadAwayFiresAtTheStart() = assertEquals(now + 2 * m, Reminders.fireAt(now + 2 * m, now))
    @Test fun nothingForAProgrammeThatBegan() { assertNull(Reminders.fireAt(now, now)); assertNull(Reminders.fireAt(now - m, now)) }
    @Test fun anotherLead() = assertEquals(now + 5 * m, Reminders.fireAt(now + 10 * m, now, 5 * m))

    @Test fun cleanDropsOldOnesAndDoubles() {
        val keep = Reminders.Item("a", "A", "News", now + 10 * m)
        val grace = Reminders.Item("b", "B", "Film", now - 3 * m)         // began 3 min ago: still kept (the TV may have just woken)
        val old = Reminders.Item("c", "C", "Old", now - 6 * m)
        val twin = Reminders.Item("a", "A", "News again", now + 10 * m)    // same channel and start
        assertEquals(listOf("a@${now + 10 * m}", "b@${now - 3 * m}"), Reminders.clean(listOf(keep, grace, old, twin), now).map { it.key() })
    }

    /**
     * A box that boots with its clock a year ahead calls [Reminders.rearm] then: every reminder looks long past. Rearming (and the alarm
     * that uses one up) must never save a list cut by the clock, or all the others are gone for good.
     */
    @Test fun noSaveCutByTheClockOnBootOrWhenOneFires() {
        val src = java.io.File("src/main/kotlin/dev/tvremote/channels/Reminders.kt").readText()
        fun body(name: String) = src.lineSequence().first { "fun $name(" in it }
        assertEquals(false, "save(" in body("rearm"))
        for (n in listOf("done", "remove")) assertEquals(n, true, "stored(c)" in body(n) && "all(c)" !in body(n))
    }
}
