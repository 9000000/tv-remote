package dev.tvremote.channels

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditTest {
    private var n = 0
    private fun clean(s: String) = Edit.clean(JSONObject(s)) { "g${n++}" }
    private fun ch(id: String, vararg urls: String) = urls.map { Channel(id, id, "", "", it) }

    @Test fun absentFieldsStayAbsent() {
        val (f, g) = clean("""{"favs":["a","b"]}""")
        assertEquals(listOf("a", "b"), f)
        assertNull(g)
    }

    @Test fun favouritesAreDeduplicatedAndBlanksDropped() {
        assertEquals(listOf("a", "b"), clean("""{"favs":["a","","b","a"]}""").first)
    }

    @Test fun groupsNeedANameAndKeepAGoodId() {
        val g = clean("""{"groups":[{"id":"abc1","name":" Thể thao ","ch":["x","y","x"]},{"id":"zz","name":"  ","ch":["q"]},{"id":"","name":"New"}]}""").second!!
        assertEquals(listOf("abc1", "g0"), g.map { it.id })
        assertEquals("Thể thao", g[0].name)
        assertEquals(listOf("x", "y"), g[0].ids)
        assertTrue(g[1].ids.isEmpty())
    }

    @Test fun aBadOrRepeatedOrReservedIdIsMadeAnew() {
        val g = clean("""{"groups":[{"id":"fav","name":"A"},{"id":"A B","name":"B"},{"id":"k1","name":"C"},{"id":"k1","name":"D"}]}""").second!!
        assertEquals(4, g.map { it.id }.toSet().size)
        assertTrue(g.none { it.id == "fav" })
        assertEquals("k1", g[2].id)
    }

    @Test fun listsAreCapped() {
        val ids = (0 until 900).joinToString(",") { "\"c$it\"" }
        assertEquals(Edit.MAX_CHANNELS, clean("""{"favs":[$ids]}""").first!!.size)
        val gs = (0 until 60).joinToString(",") { """{"name":"n$it"}""" }
        assertEquals(Edit.MAX_GROUPS, clean("""{"groups":[$gs]}""").second!!.size)
    }

    @Test fun keptChannelsKeepTheirStreamsNewOnesAreResolvedUnknownLeftOut() {
        val have = mapOf("a" to ch("a", "http://1", "http://2"))
        val out = Edit.channels(listOf("b", "a", "zz"), have) { if (it == "b") ch("b", "http://b") else emptyList() }
        assertEquals(listOf("b", "a", "a"), out.map { it.id })
        assertEquals(listOf("http://b", "http://1", "http://2"), out.map { it.url })
    }
}
