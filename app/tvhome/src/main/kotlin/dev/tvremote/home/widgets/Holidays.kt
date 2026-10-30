package dev.tvremote.home.widgets

import java.util.Calendar

/**
 * The next Vietnamese public or family holiday and the days to it, worked out on the TV with no network: fixed days of the solar
 * calendar, and days of the lunar one ([Lunar]). Names go by key (the screen turns a key into words, strings.xml "h_<key>").
 */
object Holidays {
    class Next(val key: String, val days: Int)

    private val SOLAR = listOf(                                        // month, day, key
        Triple(1, 1, "newyear"), Triple(4, 30, "liberation"), Triple(5, 1, "labour"), Triple(9, 2, "national"), Triple(12, 25, "xmas"))     // the big days only: Teachers\' Day would hide Christmas for a month
    private val LUNAR = listOf(                                        // lunar month, day, key
        Triple(1, 1, "tet"), Triple(3, 10, "hung"), Triple(4, 15, "buddha"), Triple(5, 5, "duanwu"),
        Triple(7, 15, "vulan"), Triple(8, 15, "midautumn"))

    /** The first holiday on or after [today] (0 = today); a year ahead at most, and one is always in reach. */
    fun next(today: Calendar): Next? {
        val c = today.clone() as Calendar
        for (i in 0..400) {
            val d = c.get(Calendar.DAY_OF_MONTH); val m = c.get(Calendar.MONTH) + 1
            SOLAR.firstOrNull { it.first == m && it.second == d }?.let { return Next(it.third, i) }
            val l = Lunar.of(d, m, c.get(Calendar.YEAR))
            if (!l.leap) LUNAR.firstOrNull { it.first == l.month && it.second == l.day }?.let { return Next(it.third, i) }
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return null
    }
}
