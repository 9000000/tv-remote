package dev.tvremote.home

import org.junit.Assert.assertEquals
import org.junit.Test

class LunarTest {
    @Test fun tet() {                                                  // the first day of each lunar year, as printed on Vietnamese calendars
        assertEquals(Lunar.Day(1, 1, 2024, false), Lunar.of(10, 2, 2024))
        assertEquals(Lunar.Day(1, 1, 2025, false), Lunar.of(29, 1, 2025))
        assertEquals(Lunar.Day(1, 1, 2026, false), Lunar.of(17, 2, 2026))
        assertEquals(Lunar.Day(1, 1, 2027, false), Lunar.of(6, 2, 2027))
    }

    @Test fun daysOfTheYear() {
        assertEquals(Lunar.Day(15, 8, 2026, false), Lunar.of(25, 9, 2026))   // Trung thu
        assertEquals(Lunar.Day(29, 12, 2025, false), Lunar.of(16, 2, 2026))  // giao thừa: no 30th that year
        assertEquals(Lunar.Day(15, 8, 2025, false), Lunar.of(6, 10, 2025))
    }

    @Test fun leapMonth() {                                            // 2025 had a leap 6th month, from 25 July
        assertEquals(Lunar.Day(1, 6, 2025, true), Lunar.of(25, 7, 2025))
        assertEquals(Lunar.Day(30, 6, 2025, false), Lunar.of(24, 7, 2025))
        assertEquals(Lunar.Day(1, 7, 2025, false), Lunar.of(23, 8, 2025))
    }

    @Test fun yearName() {
        assertEquals(2 to 6, Lunar.canChi(2026))                       // Bính Ngọ
        assertEquals(1 to 5, Lunar.canChi(2025))                       // Ất Tỵ
    }
}
