package dev.tvremote.home

import java.util.Calendar

/** The weeks a month calendar shows: where its first cell falls and how many rows it needs. Plain Java, so a test can run it. */
object MonthGrid {
    /** [offset] cells of the month before come first; the cell 0 is [sy]-[sm]-[sd] (month 1..12). */
    class Grid(val year: Int, val month: Int, val days: Int, val offset: Int, val rows: Int, val sy: Int, val sm: Int, val sd: Int)

    /** [month] 1..12; [first] the week's first day as [Calendar.MONDAY] etc. */
    fun of(year: Int, month: Int, first: Int): Grid {
        val c = Calendar.getInstance().apply { clear(); set(year, month - 1, 1) }
        val days = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val offset = (c.get(Calendar.DAY_OF_WEEK) - first + 7) % 7
        c.add(Calendar.DAY_OF_MONTH, -offset)
        return Grid(year, month, days, offset, (offset + days + 6) / 7, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
}
