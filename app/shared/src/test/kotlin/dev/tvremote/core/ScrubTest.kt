package dev.tvremote.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Scrub gives what tests/scrub_cases.json says; js/scrub.js is held to the same table (tests/scrub.test.js). */
class ScrubTest {
    private val cases = JSONObject(java.io.File("tests/scrub_cases.json").readText()).getJSONArray("cases")

    @Test fun everyCase() {
        for (i in 0 until cases.length()) cases.getJSONObject(i).let { c -> assertEquals(c.getString("name"), c.getString("out"), Scrub.text(c.getString("in"))) }
    }

    @Test fun twiceChangesNothingMore() {
        for (i in 0 until cases.length()) cases.getJSONObject(i).let { c ->
            if (c.optBoolean("again", true)) assertEquals(c.getString("name"), c.getString("out"), Scrub.text(c.getString("out")))
        }
    }
}
