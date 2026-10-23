package dev.tvremote.channels

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CategoriesTest {
    private fun ch(name: String) = listOf(Channel("id.$name", name, "", "", "http://x/$name"))
    private val doc = Categories.parse("""{"v":1,"updated":"x","categories":[
        {"id":"vtv","vi":"VTV","en":"VTV","match":["vtv3","vtv1","re:^vtv\\d+$"]},
        {"id":"kids","vi":"Thiếu nhi","en":"Kids","match":["htv3","cartoonnetwork"]},
        {"id":"htv","vi":"HTV","en":"HTV","match":["re:^htv\\d$"]},
        {"id":"empty","vi":"Trống","en":"Empty","match":["nothinghere"]}]}""")!!

    /** The vectors the page shares (app/shared/tests/fixtures/norm.json): the TV and the page group channels the same way. */
    @Test fun normVectors() {
        val a = JSONObject(File("../shared/tests/fixtures/norm.json").readText()).getJSONArray("norm")
        assertTrue(a.length() > 5)
        for (i in 0 until a.length()) a.getJSONObject(i).let { assertEquals(it.getString("in"), it.getString("out"), Categories.norm(it.getString("in"))) }
        assertEquals("vtv1", Categories.norm("VTV1 HD")); assertEquals("danang1", Categories.norm("Đà Nẵng 1")); assertEquals("thvl1", Categories.norm("THVL1 FHD"))
    }

    @Test fun parse() {
        assertNull(Categories.parse("not json")); assertNull(Categories.parse("""{"v":2,"categories":[]}""")); assertNull(Categories.parse("""{"v":1,"categories":[]}"""))
        assertEquals(4, doc.cats.size)
        assertEquals("Thiếu nhi", doc.cats[1].title("vi"))
        val bad = Categories.parse("""{"v":1,"categories":[{"id":"a","vi":"A","match":["re:(","x"]},{"vi":"no id","match":["y"]}]}""")!!
        assertEquals(1, bad.cats.size); assertEquals(1, bad.cats[0].at("x"))      // a broken pattern costs its entry only; its index stays
    }

    @Test fun order() {
        val r = Categories.categorize(doc, listOf("VTV10", "VTV2 HD", "VTV3", "Cartoon Network", "VTV1", "HTV3", "HTV7 HD", "Zing").map { ch(it) })
        assertEquals(listOf("vtv", "kids", "htv"), r.sections.map { it.cat.id })                // the empty category is left out
        assertEquals(listOf("VTV3", "VTV1", "VTV2 HD", "VTV10"), r.sections[0].items.map { it.first().name })   // entry index first, then the name, 2 before 10
        assertEquals(listOf("HTV3", "Cartoon Network"), r.sections[1].items.map { it.first().name })
        assertEquals(listOf("HTV3", "HTV7 HD"), r.sections[2].items.map { it.first().name })     // HTV3 is in two categories
        assertEquals(listOf("Zing"), r.others.map { it.first().name })
    }

    @Test fun sameNameShowsOnce() {
        val r = Categories.categorize(doc, listOf(listOf(Channel("a.vn", "VTV1", "", "", "u1")), listOf(Channel("b", "VTV1 HD", "", "", "u2"))))
        assertEquals(1, r.sections[0].items.size); assertEquals("a.vn", r.sections[0].items[0].first().id)
        assertTrue(r.others.isEmpty())
    }

    @Test fun titles() {
        assertEquals("Thiếu nhi", doc.cats[1].title("vi")); assertEquals("Kids", doc.cats[1].title("en"))
        assertEquals("Kids", doc.cats[1].title("de"))                    // a language the file lacks: English
        val d = Categories.parse("""{"v":1,"categories":[{"id":"x","de":"Kinder","match":["a"]}]}""")!!
        assertEquals("Kinder", d.cats[0].title("de")); assertEquals("x", d.cats[0].title("en"))   // no English either: the id
    }

    /** Every list the site ships parses and sorts the names a viewer would expect into some shelf. */
    @Test fun shippedLists() {
        val files = File("../../docs/channels/categories").listFiles { f -> f.name.endsWith(".json") }.orEmpty()
        assertTrue(files.isNotEmpty())
        for (f in files) {
            val d = Categories.parse(f.readText()); assertNotNull(f.name, d)
            assertTrue(f.name, d!!.cats.all { it.title("en").isNotEmpty() })
        }
    }
}
