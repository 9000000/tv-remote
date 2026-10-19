package dev.tvremote.setup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Rules.apply: what every remote's action does to the stored rules (the part of Rules.change that needs no Context). */
class RulesTest {
    private val now = 1_800_000_000_000L
    private fun d(s: String) = JSONObject(s)
    private fun bad(o: JSONObject, s: String) {
        try { Rules.apply(o, d(s), now); fail("accepted $s") } catch (e: IllegalArgumentException) { }
    }

    @Test fun readChangesNothing() {
        val o = JSONObject()
        assertFalse(Rules.apply(o, d("""{"do":""}"""), now))
        assertFalse(Rules.apply(o, d("""{"do":"get"}"""), now))
        assertEquals(0, o.length())
    }

    @Test fun appLocksNeedAPin() {
        val o = JSONObject()
        bad(o, """{"do":"lock","pkg":"com.netflix.ninja","on":true}""")          // no PIN yet
        bad(o, """{"do":"pin","pin":"12"}""")
        bad(o, """{"do":"pin","pin":"12ab"}""")
        Rules.apply(o, d("""{"do":"pin","pin":"2580"}"""), now)
        assertFalse(o.toString().contains("2580"))                                // kept as a hash only
        assertTrue(Rules.pinOk(o, "2580"))
        assertFalse(Rules.pinOk(o, "0000"))
        Rules.apply(o, d("""{"do":"lock","pkg":"com.netflix.ninja","on":true}"""), now)
        Rules.apply(o, d("""{"do":"lock","pkg":"com.netflix.ninja","on":true}"""), now)
        assertEquals(listOf("com.netflix.ninja"), Rules.locked(o))
        bad(o, """{"do":"lock","pkg":"dev.tvremote.setup","on":true}""")        // its own PIN screen
        Rules.apply(o, d("""{"do":"pin","pin":"13579"}"""), now)                 // a new PIN keeps the apps
        assertTrue(Rules.pinOk(o, "13579"))
        assertFalse(Rules.pinOk(o, "2580"))
        assertEquals(listOf("com.netflix.ninja"), Rules.locked(o))
        Rules.apply(o, d("""{"do":"lock","pkg":"com.netflix.ninja","on":false}"""), now)
        assertEquals(emptyList<String>(), Rules.locked(o))
    }

    @Test fun sleepSetsAndCancels() {
        val o = JSONObject()
        Rules.apply(o, d("""{"do":"sleep","minutes":30}"""), now)
        assertEquals(now + 30 * 60_000L, o.getLong("sleep_at"))
        Rules.apply(o, d("""{"do":"sleep","minutes":0}"""), now)
        assertEquals(0L, o.getLong("sleep_at"))
    }

    @Test fun sleepAddStacksOnALaterTimerButNotOnAPastOne() {
        val o = JSONObject().put("sleep_at", now + 10 * 60_000L)
        Rules.apply(o, d("""{"do":"sleep_add","minutes":15}"""), now)
        assertEquals(now + 25 * 60_000L, o.getLong("sleep_at"))
        val old = JSONObject().put("sleep_at", now - 5_000L)
        Rules.apply(old, d("""{"do":"sleep_add","minutes":15}"""), now)
        assertEquals(now + 15 * 60_000L, old.getLong("sleep_at"))
    }

    @Test fun minutesAreCapped() {
        val o = JSONObject()
        Rules.apply(o, d("""{"do":"sleep","minutes":99999}"""), now)
        assertEquals(now + 24 * 60 * 60_000L, o.getLong("sleep_at"))
    }

    @Test fun limitSetsAndZeroRemoves() {
        val o = JSONObject()
        Rules.apply(o, d("""{"do":"limit","pkg":"com.a.b","minutes":60}"""), now)
        assertEquals(60, o.getJSONObject("limits").getInt("com.a.b"))
        Rules.apply(o, d("""{"do":"limit","pkg":"com.a.b","minutes":0}"""), now)
        assertFalse(o.getJSONObject("limits").has("com.a.b"))
    }

    @Test fun extraAddsUpAndDropsOtherDays() {
        val o = JSONObject().put("extra", JSONObject().put("2020-01-01", JSONObject().put("com.a.b", 10)))
        Rules.apply(o, d("""{"do":"extra","pkg":"com.a.b","minutes":10}"""), now)
        Rules.apply(o, d("""{"do":"extra","pkg":"com.a.b","minutes":5}"""), now)
        val extra = o.getJSONObject("extra")
        assertEquals(listOf(Rules.day(now)), extra.keys().asSequence().toList())
        assertEquals(15, Rules.extra(o, "com.a.b", now))
    }

    @Test fun packageNamesAreChecked() {
        for (p in listOf("", "nodots", "com.a;reboot", "com.a b", "com.a.", "1com.a", "com.1a.b"))
            bad(JSONObject(), """{"do":"limit","pkg":"$p","minutes":5}""")
    }

    @Test fun bedtimeRangesPaddedCappedAndChecked() {
        val o = JSONObject()
        Rules.apply(o, d("""{"do":"bedtime","on":true,"ranges":[{"from":"9:05","to":"6:30"}]}"""), now)
        val b = Rules.bedtime(o)
        assertTrue(b.getBoolean("on"))
        assertEquals("09:05", b.getJSONArray("ranges").getJSONObject(0).getString("from"))
        assertEquals("06:30", b.getString("to"))
        val ten = (1..10).joinToString(",") { """{"from":"22:00","to":"06:00"}""" }
        Rules.apply(o, d("""{"do":"bedtime","on":true,"ranges":[$ten]}"""), now)
        assertEquals(8, Rules.bedtime(o).getJSONArray("ranges").length())
        bad(o, """{"do":"bedtime","on":true,"ranges":[{"from":"25:00","to":"06:00"}]}""")
        bad(o, """{"do":"bedtime","on":true,"ranges":[{"from":"2200","to":"06:00"}]}""")
        bad(o, """{"do":"bedtime","on":true,"ranges":[{"from":"22:00","to":"06:60"}]}""")
    }

    @Test fun bedtimeWithNoRangesIsOff() {
        val o = JSONObject()
        Rules.apply(o, d("""{"do":"bedtime","on":true,"ranges":[]}"""), now)
        assertFalse(Rules.bedtime(o).getBoolean("on"))
    }

    @Test fun volumeRangesClampMaxAndCheckTimes() {
        val o = JSONObject()
        Rules.apply(o, d("""{"do":"volume","ranges":[{"from":"22:00","to":"06:00","max":250},{"from":"13:00","to":"14:00"}]}"""), now)
        val v = o.getJSONArray("volume")
        assertEquals(100, v.getJSONObject(0).getInt("max"))
        assertEquals(30, v.getJSONObject(1).getInt("max"))
        bad(o, """{"do":"volume","ranges":[{"from":"x","to":"06:00","max":10}]}""")
        Rules.apply(o, d("""{"do":"volume","ranges":[]}"""), now)
        assertEquals(0, o.getJSONArray("volume").length())
    }

    @Test fun snoozeAndUnknown() {
        val o = JSONObject()
        Rules.apply(o, d("""{"do":"snooze","minutes":20}"""), now)
        assertEquals(now + 20 * 60_000L, o.getLong("snooze_until"))
        bad(o, """{"do":"explode"}""")
    }
}
