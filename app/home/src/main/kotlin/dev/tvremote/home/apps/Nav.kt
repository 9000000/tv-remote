package dev.tvremote.home.apps

/**
 * Where the D-pad goes in the grid of apps, as plain numbers (so it is tested without views): the grid is rows of [rows] tiles
 * (the last one may be short). Like a TV's own grid, Up and Down keep the column asked for ([want]) as far as the row below or
 * above has it, else land on that row's last tile; Right on a row's last tile goes to the next row's first, Left the other way.
 */
object Nav {
    /** A tile by its row and column in the grid. */
    data class At(val row: Int, val col: Int)

    /**
     * The tile after pressing [dir] on ([row], [col]) of a grid whose rows hold [rows] tiles each; [want] the column kept by earlier
     * vertical moves (-1 = [col]). Null = nowhere to go: the edge of the grid.
     */
    fun grid(rows: IntArray, row: Int, col: Int, want: Int, dir: Dir): At? {
        if (row !in rows.indices || rows[row] <= 0) return null
        val keep = if (want >= 0) want else col
        return when (dir) {
            Dir.RIGHT -> if (col + 1 < rows[row]) At(row, col + 1) else if (row + 1 < rows.size) At(row + 1, 0) else null
            Dir.LEFT -> if (col > 0) At(row, col - 1) else if (row > 0) At(row - 1, rows[row - 1] - 1) else null
            Dir.DOWN -> if (row + 1 < rows.size) At(row + 1, minOf(keep, rows[row + 1] - 1)) else null
            Dir.UP -> if (row > 0) At(row - 1, minOf(keep, rows[row - 1] - 1)) else null
        }
    }

    /** The column to remember after a move: a vertical one keeps the wish, a sideways one forgets it. */
    fun wantAfter(dir: Dir, col: Int, want: Int) = if (dir == Dir.UP || dir == Dir.DOWN) (if (want >= 0) want else col) else -1

    /** The index in [centres] (x of each tile's middle, any unit) closest to [x]; -1 when there are none. Between the dock and the grid. */
    fun nearest(centres: IntArray, x: Int): Int {
        var best = -1
        for (i in centres.indices) if (best < 0 || Math.abs(centres[i] - x) < Math.abs(centres[best] - x)) best = i
        return best
    }

    enum class Dir { UP, DOWN, LEFT, RIGHT }
}
