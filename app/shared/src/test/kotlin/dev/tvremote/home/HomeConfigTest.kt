package dev.tvremote.home

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** What the phone or computer sends TV Home is cleaned the same way on both sides: nothing out of range is ever kept. */
class HomeConfigTest {
    private fun dock(c: JSONObject) = c.getJSONArray("dock").let { a -> List(a.length()) { a.getString(it) } }
    private fun hidden(c: JSONObject) = c.getJSONArray("hidden").let { a -> List(a.length()) { a.getString(it) } }

    @Test fun emptyGivesTheDefaults() {
        val c = HomeConfig.clean(JSONObject())
        assertEquals(emptyList<String>(), dock(c))
        assertEquals("night", c.getString("wall"))
        assertEquals(true, c.getBoolean("clock24"))
        assertEquals(true, c.getBoolean("greet"))
        assertEquals(true, c.getBoolean("anim"))
    }

    @Test fun theDockKeepsRealPackagesOnceAndAtMostSix() {
        val c = HomeConfig.clean(JSONObject().put("dock", JSONArray(listOf("a.b", "a.b", "; rm -rf /", "x", "c.d", "e.f", "g.h", "i.j", "k.l", "m.n"))))
        assertEquals(listOf("a.b", "c.d", "e.f", "g.h", "i.j", "k.l"), dock(c))
    }

    @Test fun sizesAreKnownOnesAndDefaultToTheCardsKind() {
        val none = HomeConfig.clean(JSONObject())
        assertEquals(0, none.getJSONObject("wsize").length())                    // nothing chosen: the defaults apply
        assertFalse(none.has("wlayout"))
        assertEquals(setOf("system", "usage", "calendar", "weather"), HomeConfig.bigs(none))
        val c = HomeConfig.clean(JSONObject().put("wsize", JSONObject().put("system", "small").put("lunar", "big").put("timer", "huge").put("bogus", "big")))
        assertEquals("small", c.getJSONObject("wsize").getString("system"))
        assertEquals("big", c.getJSONObject("wsize").getString("lunar"))
        assertFalse(c.getJSONObject("wsize").has("timer"))                       // an unknown size: dropped
        assertFalse(c.getJSONObject("wsize").has("bogus"))
        assertEquals(setOf("usage", "calendar", "weather", "lunar"), HomeConfig.bigs(c))
        val m = HomeConfig.merge(JSONObject().put("widgets", JSONArray(listOf("weather", "calendar"))), JSONObject().put("wsize", JSONObject().put("weather", "small")))
        assertEquals("small", m.getJSONObject("wsize").getString("weather"))
        assertEquals("weather", m.getJSONArray("widgets").getString(0))          // moving never sorts them back
    }

    @Test fun anOldLayoutBecomesTheSizesThatGaveTheSamePicture() {
        val names = listOf("system", "usage", "calendar", "lunar")
        fun sizes(layout: String): List<String> {
            val c = HomeConfig.clean(JSONObject().put("wlayout", layout).put("widgets", JSONArray(names)))
            return names.map { c.getJSONObject("wsize").optString(it, "?") }
        }
        assertEquals(listOf("?", "?", "?", "?"), sizes("row"))
        assertEquals(listOf("big", "small", "small", "small"), sizes("big"))
        assertEquals(listOf("big", "big", "small", "small"), sizes("big2"))
        assertEquals(listOf("small", "small", "big", "small"), sizes("flip"))
        assertEquals(listOf("small", "small", "small", "small"), sizes("quad"))
        // sizes already chosen win over a layout left in an old copy
        val both = HomeConfig.clean(JSONObject().put("wlayout", "quad").put("wsize", JSONObject()).put("widgets", JSONArray(listOf("system"))))
        assertEquals(0, both.getJSONObject("wsize").length())
    }

    /** The packing gives what tests/home_pack_cases.json says (the page's homePack reads the same table). */
    @Test fun packingFollowsTheSharedCases() {
        val cases = JSONObject(java.io.File("tests/home_pack_cases.json").readText()).getJSONArray("cases")
        fun list(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val p = HomeConfig.pack(list(c.getJSONArray("keys")), list(c.getJSONArray("big")).toSet(), c.optBoolean("note"))
            val cols = c.getJSONArray("cols")
            assertEquals(c.getString("name"), (0 until cols.length()).map { list(cols.getJSONArray(it)) }, p.cols.map { it.keys })
            assertEquals(c.getString("name"), list(c.getJSONArray("waiting")), p.waiting)
            assertEquals(c.getString("name"), true, p.cols.sumOf { it.weight } <= HomeConfig.BUDGET)
        }
    }

    @Test fun widgetsAreKnownOnesOnceInTheirOrderAsSent() {
        val c = HomeConfig.clean(JSONObject().put("widgets", JSONArray(listOf("usage", "bogus", "usage", "system"))))
        val w = c.getJSONArray("widgets").let { a -> (0 until a.length()).map { a.getString(it) } }
        assertEquals(listOf("usage", "system"), w)
        assertEquals(0, HomeConfig.clean(JSONObject()).getJSONArray("widgets").length())   // none until asked for
    }

    @Test fun theWeatherCardAndItsPlace() {
        val c = HomeConfig.clean(JSONObject().put("widgets", JSONArray(listOf("weather"))).put("place", "  Đà   Nẵng " + "x".repeat(100)))
        assertEquals("weather", c.getJSONArray("widgets").getString(0))
        assertEquals(HomeConfig.PLACE_MAX, c.getString("place").length)
        assertEquals("Đà Nẵng x", c.getString("place").take(9))
        assertEquals("", HomeConfig.clean(JSONObject()).getString("place"))      // automatic until a city is typed
    }

    @Test fun anAppInTheDockIsNeverHidden() {
        val c = HomeConfig.clean(JSONObject().put("dock", JSONArray(listOf("a.b"))).put("hidden", JSONArray(listOf("a.b", "c.d"))))
        assertEquals(listOf("c.d"), hidden(c))
    }

    @Test fun anUnknownWallOrFieldIsDropped() {
        val c = HomeConfig.clean(JSONObject().put("wall", "../../etc").put("evil", 1).put("photo", -5))
        assertEquals("night", c.getString("wall"))
        assertFalse(c.has("evil"))
        assertEquals(0L, c.getLong("photo"))
    }

    @Test fun mergeChangesOnlyWhatIsSent() {
        val old = HomeConfig.clean(JSONObject().put("dock", JSONArray(listOf("a.b"))).put("wall", "ocean"))
        val n = HomeConfig.merge(old, JSONObject().put("clock24", false))
        assertEquals(listOf("a.b"), dock(n))
        assertEquals("ocean", n.getString("wall"))
        assertFalse(n.getBoolean("clock24"))
    }

    @Test fun animIsABooleanAndMerges() {
        assertFalse(HomeConfig.clean(JSONObject().put("anim", false)).getBoolean("anim"))
        assertEquals(true, HomeConfig.clean(JSONObject().put("anim", "no")).getBoolean("anim"))   // not a boolean: the default
        val n = HomeConfig.merge(HomeConfig.clean(JSONObject()), JSONObject().put("anim", false))
        assertFalse(n.getBoolean("anim"))
    }

    @Test fun everyBuiltInWallIsAccepted() {
        for (w in HomeConfig.WALLS) assertEquals(w, HomeConfig.clean(JSONObject().put("wall", w)).getString("wall"))
        assertEquals(true, HomeConfig.WALLS.size >= 16)
    }

    @Test fun noteIsOneShortLine() {
        assertEquals("Tắt TV lúc 9h nhé", HomeConfig.clean(JSONObject().put("note", "  Tắt TV\n lúc   9h nhé ")).getString("note"))
        assertEquals(HomeConfig.NOTE_MAX, HomeConfig.clean(JSONObject().put("note", "x".repeat(500))).getString("note").length)
        assertEquals("", HomeConfig.clean(JSONObject()).getString("note"))
    }

    @Test fun dreamIsOffUntilSet() {
        assertFalse(HomeConfig.clean(JSONObject()).getBoolean("dream"))
        assertEquals(true, HomeConfig.merge(HomeConfig.clean(JSONObject()), JSONObject().put("dream", true)).getBoolean("dream"))
    }
}
