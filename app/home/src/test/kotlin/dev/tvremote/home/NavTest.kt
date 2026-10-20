package dev.tvremote.home

import dev.tvremote.home.Nav.At
import dev.tvremote.home.Nav.Dir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavTest {
    private val rows = intArrayOf(6, 6, 1, 4)                             // the second to last row holds a single tile

    @Test fun downToAShortRowLandsOnItsLastTile() {                       // the bug: row 1 col 3, the row below ends in col 0
        assertEquals(At(2, 0), Nav.grid(rows, 1, 3, -1, Dir.DOWN))
    }

    @Test fun theWishedColumnComesBackOnALongRow() {
        val a = Nav.grid(rows, 1, 3, -1, Dir.DOWN)!!                      // to the short row
        val want = Nav.wantAfter(Dir.DOWN, 3, -1)
        assertEquals(3, want)
        assertEquals(At(3, 3), Nav.grid(rows, a.row, a.col, want, Dir.DOWN))   // row 3 has a column 3
        assertEquals(At(1, 3), Nav.grid(rows, a.row, a.col, want, Dir.UP))
    }

    @Test fun upClampsToo() {
        assertEquals(At(2, 0), Nav.grid(rows, 3, 3, -1, Dir.UP))
        assertEquals(At(0, 5), Nav.grid(intArrayOf(6, 2), 1, 1, 5, Dir.UP))   // a wish for column 5 is kept past a short row
    }

    @Test fun sidewaysForgetsTheWish() {
        assertEquals(-1, Nav.wantAfter(Dir.LEFT, 2, 4))
        assertEquals(-1, Nav.wantAfter(Dir.RIGHT, 2, -1))
        assertEquals(4, Nav.wantAfter(Dir.UP, 2, 4))
    }

    @Test fun rightAndLeftWrapBetweenRows() {
        assertEquals(At(1, 0), Nav.grid(rows, 0, 5, -1, Dir.RIGHT))
        assertEquals(At(0, 5), Nav.grid(rows, 1, 0, -1, Dir.LEFT))
        assertEquals(At(1, 5), Nav.grid(rows, 2, 0, -1, Dir.LEFT))
    }

    @Test fun edgesGoNowhere() {
        assertNull(Nav.grid(rows, 0, 0, -1, Dir.LEFT))
        assertNull(Nav.grid(rows, 0, 2, -1, Dir.UP))
        assertNull(Nav.grid(rows, 3, 3, -1, Dir.RIGHT))
        assertNull(Nav.grid(rows, 3, 1, -1, Dir.DOWN))
        assertNull(Nav.grid(intArrayOf(), 0, 0, -1, Dir.DOWN))
    }

    @Test fun nearestMiddle() {
        val dock = intArrayOf(100, 300, 500)
        assertEquals(1, Nav.nearest(dock, 340))
        assertEquals(0, Nav.nearest(dock, -50))
        assertEquals(2, Nav.nearest(dock, 900))
        assertEquals(-1, Nav.nearest(intArrayOf(), 10))
    }
}
