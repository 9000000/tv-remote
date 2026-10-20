package dev.tvremote.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class MonthGridTest {
    private fun first(g: MonthGrid.Grid) = Triple(g.sy, g.sm, g.sd)

    @Test fun february2026StartsOnASunday() {         // 1 Feb 2026 is a Sunday: a Monday-first week puts it last
        val m = MonthGrid.of(2026, 2, Calendar.MONDAY)
        assertEquals(Triple(2026, 1, 26), first(m)); assertEquals(6, m.offset); assertEquals(5, m.rows)
        val s = MonthGrid.of(2026, 2, Calendar.SUNDAY)
        assertEquals(Triple(2026, 2, 1), first(s)); assertEquals(0, s.offset); assertEquals(4, s.rows)   // 28 days: four exact weeks
    }

    @Test fun october2026() {                         // starts on a Thursday
        val m = MonthGrid.of(2026, 10, Calendar.MONDAY)
        assertEquals(Triple(2026, 9, 28), first(m)); assertEquals(31, m.days); assertEquals(5, m.rows)
        assertEquals(Triple(2026, 9, 27), first(MonthGrid.of(2026, 10, Calendar.SUNDAY)))
    }

    @Test fun sixRowsAndAYearBack() {                 // March 2026 starts on a Sunday and has 31 days; January 2027 on a Friday
        val m = MonthGrid.of(2026, 3, Calendar.MONDAY)
        assertEquals(Triple(2026, 2, 23), first(m)); assertEquals(6, m.rows)
        assertEquals(Triple(2026, 12, 28), first(MonthGrid.of(2027, 1, Calendar.MONDAY)))
    }

    @Test fun leapFebruary() {
        assertEquals(29, MonthGrid.of(2028, 2, Calendar.MONDAY).days)
    }
}
