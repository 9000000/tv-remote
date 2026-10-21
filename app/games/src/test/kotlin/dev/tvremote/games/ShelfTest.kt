package dev.tvremote.games

import org.junit.Assert.*
import org.junit.Test

class ShelfTest {
    @Test fun favouritesToggle() {
        val d = Shelf.Data().toggleFav("g:a").toggleFav("w:b")
        assertEquals(listOf("g:a", "w:b"), d.fav)
        assertEquals(listOf("w:b"), d.toggleFav("g:a").fav)
    }

    @Test fun groupsRenameDeleteAndMembers() {
        var d = Shelf.Data().addGroup(Shelf.Group("f1", "Nhóm 1", emptyList()))
        d = d.toggleIn("f1", "g:a").toggleIn("f1", "w:b")
        assertTrue(d.inGroup("f1", "g:a"))
        d = d.toggleIn("f1", "g:a")
        assertFalse(d.inGroup("f1", "g:a"))
        assertEquals("Party", d.rename("f1", "Party").groups[0].name)
        assertTrue(d.delete("f1").groups.isEmpty())
        assertEquals("Nhóm 2", d.freeName("Nhóm"))
    }

    @Test fun searchIgnoresAccentsAndCase() {
        assertTrue(Browse.nameMatches("Đố vui", "do VUI"))
        assertTrue(Browse.nameMatches("Table Tennis", "  tenn"))
        assertFalse(Browse.nameMatches("Pong", "tank"))
        assertTrue(Browse.nameMatches("Pong", ""))
    }

    @Test fun playerFilters() {
        assertTrue(Browse.playersOk(1, 1, 1)); assertFalse(Browse.playersOk(1, 4, 2))
        assertTrue(Browse.playersOk(2, 2, 2)); assertFalse(Browse.playersOk(2, 1, 1))
        assertTrue(Browse.playersOk(3, 4, 1)); assertFalse(Browse.playersOk(3, 2, 1))
        assertFalse(Browse.playersOk(1, 0, 1)); assertTrue(Browse.playersOk(null, 0, 1))
    }
}
