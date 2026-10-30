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

    // ---- import / export
    private val cat = listOf("VTV1.vn@SD", "VTV3.vn@SD", "CNN.us@SD").map { Channel(it, it.substringBefore('.') + if (it.contains("vn")) " HD" else "", "", "", "http://x/$it") }
    private fun find(e: Edit.Entry): List<Channel> {
        val byId = cat.filter { it.id == e.id }
        return byId.ifEmpty { cat.filter { Edit.fold(it.name) == Edit.fold(e.name) && (e.country.isEmpty() || it.country == e.country) } }
    }
    private fun imp(s: String) = Edit.parseImport(JSONObject(s))
    private fun run(f: List<Channel>, g: List<Edit.G>, s: String, replace: Boolean) = Edit.importInto(f, g, imp(s), replace, { "n${n++}" }, ::find)

    @Test fun badFilesAreRefused() {
        for (s in listOf("{}", """{"kind":"scenes","favourites":[{"id":"a"}]}""", """{"favourites":[],"groups":[]}""", """{"favourites":[{"x":1}]}"""))
            assertTrue(s, runCatching { imp(s) }.isFailure)
    }

    @Test fun exportReadsBack() {
        val doc = Edit.export(cat.take(1), listOf(Edit.G("g", "Tin", cat.drop(2))), "2026-01-01")
        val i = Edit.parseImport(doc)
        assertEquals("VTV1.vn@SD", i.favs[0].id); assertEquals("vn", i.favs[0].country)
        assertEquals("Tin", i.groups[0].first); assertEquals("CNN.us@SD", i.groups[0].second[0].id)
    }

    @Test fun mergeAddsMissingAndKeepsTheRest() {
        val have = listOf(Edit.G("g1", "Tin", cat.take(1)))
        val r = run(cat.drop(2), have, """{"favourites":[{"id":"CNN.us@SD"},{"id":"nope.vn@SD","name":"Ghost"}],"groups":[{"name":" tin ","channels":[{"id":"VTV1.vn@SD"},{"id":"VTV3.vn@SD"}]},{"name":"Mới","channels":[{"name":"vtv3 hd","country":"vn"}]}]}""", false)
        assertEquals(listOf("CNN.us@SD"), r.favs.map { it.id })            // already a favourite: no copy
        assertEquals(listOf("Tin", "Mới"), r.groups.map { it.name })
        assertEquals("g1", r.groups[0].id)
        assertEquals(listOf("VTV1.vn@SD", "VTV3.vn@SD"), r.groups[0].ch.map { it.id })
        assertEquals(listOf("VTV3.vn@SD"), r.groups[1].ch.map { it.id })    // by name + country
        assertEquals(listOf("Ghost"), r.missing)
        assertEquals(2, r.added)
    }

    @Test fun replaceMakesTheFileTheWholeState() {
        val have = listOf(Edit.G("g1", "Cũ", cat.take(1)))
        val r = run(cat.take(1), have, """{"favourites":[{"id":"CNN.us@SD"}],"groups":[{"name":"Mới","channels":[{"id":"VTV3.vn@SD"}]}]}""", true)
        assertEquals(listOf("CNN.us@SD"), r.favs.map { it.id })
        assertEquals(listOf("Mới"), r.groups.map { it.name })
    }

    @Test fun capsAndSkips() {
        val many = (1..60).joinToString(",") { """{"name":"G$it","channels":[]}""" }
        val r = run(emptyList(), emptyList(), """{"groups":[$many,{"name":"","channels":[]}]}""", false)
        assertEquals(Edit.MAX_GROUPS, r.groups.size)
        assertEquals(20, r.skippedGroups)
        assertEquals(1, imp("""{"groups":[$many,{"name":""}]}""").dropped)
        val big = (1..Edit.MAX_IMPORT + 50).joinToString(",") { """{"id":"c$it.vn"}""" }
        assertTrue(imp("""{"favourites":[$big]}""").favs.size <= Edit.MAX_CHANNELS)
    }

    // ---- the person's own playlists

    private fun lists(s: String) = Edit.cleanLists(JSONObject(s))

    @Test fun listsAbsentStayAbsent() = assertNull(lists("""{"favs":[]}"""))

    @Test fun onlyHttpLinksWithAHostPass() {
        val l = lists("""{"lists":[{"name":"a","url":"https://x.vn/a.m3u"},{"name":"b","url":"http://y.com:8080/p?t=1"},
            {"name":"c","url":"ftp://z/c.m3u"},{"name":"d","url":"file:///sdcard/a.m3u"},{"name":"e","url":"javascript:alert(1)"},
            {"name":"f","url":"https:///nohost"},{"name":"g","url":"https://x.vn/has space.m3u"},{"name":"h","url":""}]}""")!!
        assertEquals(listOf("https://x.vn/a.m3u", "http://y.com:8080/p?t=1"), l.map { it.url })
    }

    @Test fun listsAreCappedDedupedAndNamed() {
        val many = (1..9).joinToString(",") { """{"name":"n$it","url":"https://h$it.com/l.m3u"}""" }
        assertEquals(Edit.MAX_LISTS, lists("""{"lists":[$many]}""")!!.size)
        val l = lists("""{"lists":[{"name":"  ","url":"https://www.tv.vn/a.m3u"},{"name":"again","url":"https://www.tv.vn/a.m3u"},
            {"name":"${"x".repeat(80)}","url":"https://b.vn/b.m3u"}]}""")!!
        assertEquals(2, l.size)
        assertEquals("tv.vn", l[0].name)                              // no name: the host
        assertEquals(Edit.MAX_NAME, l[1].name.length)
    }

    @Test fun listIdIsStableAndShort() {
        assertEquals(Edit.listId("https://a.vn/x.m3u"), Edit.listId("https://a.vn/x.m3u"))
        assertTrue(Edit.listId("https://a.vn/x.m3u").matches(Regex("[a-z0-9]{1,7}")))
    }

    @Test fun playlistTextIsRecognised() {
        assertTrue(Playlists.looksLikeM3u("#EXTM3U\n#EXTINF:-1,A\nhttp://a/1"))
        assertTrue(!Playlists.looksLikeM3u("<html>login</html>"))
    }
}
