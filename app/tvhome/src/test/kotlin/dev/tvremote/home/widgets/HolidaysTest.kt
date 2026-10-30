package dev.tvremote.home.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Calendar

class HolidaysTest {
    private fun on(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, 12, 0) }

    @Test fun christmasIs63DaysFromThe23rdOfOctober2026() {
        val n = Holidays.next(on(2026, 10, 23))!!
        assertEquals("xmas", n.key); assertEquals(63, n.days)
    }

    @Test fun todayCountsAsZero() {
        assertEquals(0, Holidays.next(on(2026, 9, 2))!!.days)
        assertEquals("national", Holidays.next(on(2026, 9, 2))!!.key)
    }

    @Test fun lunarDaysAreFound() {
        val tet = Holidays.next(on(2026, 2, 10))!!                    // Tết 2026 is 17 Feb
        assertEquals("tet", tet.key); assertEquals(7, tet.days)
        val moon = Holidays.next(on(2026, 9, 20))!!                   // Trung thu 2026 is 25 Sep
        assertEquals("midautumn", moon.key); assertEquals(5, moon.days)
    }

    @Test fun everyDayOfAYearHasANextHoliday() {
        val c = on(2026, 1, 1)
        repeat(366) { assertNotNull(Holidays.next(c)); c.add(Calendar.DAY_OF_MONTH, 1) }
    }
}
