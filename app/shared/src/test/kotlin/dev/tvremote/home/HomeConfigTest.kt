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
    }

    @Test fun theDockKeepsRealPackagesOnceAndAtMostSix() {
        val c = HomeConfig.clean(JSONObject().put("dock", JSONArray(listOf("a.b", "a.b", "; rm -rf /", "x", "c.d", "e.f", "g.h", "i.j", "k.l", "m.n"))))
        assertEquals(listOf("a.b", "c.d", "e.f", "g.h", "i.j", "k.l"), dock(c))
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
}
