package dev.tvremote.desktop

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SavedTest {
    @get:Rule val dir = TemporaryFolder()

    @Test fun roundTripKeepsWhatItDoesNotKnow() {
        val f = dir.newFile("tvs.json")
        f.writeText("""{"current":"192.0.2.5","tvs":[{"host":"192.0.2.5","name":"A","setup_pass":"x"}],"scenes":[{"id":"s1"}],"relay_x":1}""")
        val s = Saved(f)
        s.edit("192.0.2.5") { it.put("name", "B") }
        val back = JSONObject(f.readText())
        assertEquals("B", back.getJSONArray("tvs").getJSONObject(0).getString("name"))
        assertEquals("x", back.getJSONArray("tvs").getJSONObject(0).getString("setup_pass"))
        assertEquals(1, back.getInt("relay_x"))
        assertEquals(1, back.getJSONArray("scenes").length())
    }

    @Test fun corruptFileStartsEmptyAndIsNotRewrittenUntilSaved() {
        val f = dir.newFile("tvs.json")
        f.writeText("{not json")
        val s = Saved(f)
        assertEquals(0, s.tvs.length())
        assertEquals("", s.current)
        assertEquals("{not json", f.readText())                    // reading never destroys the file
    }

    @Test fun unreadableFileIsKeptAsBadBeforeTheNextSave() {
        val f = dir.newFile("tvs.json")
        f.writeText("{not json")
        val s = Saved(f)
        s.upsert(JSONObject().put("host", "192.0.2.9"), null)
        s.save()                                                   // overwrites tvs.json, as the page's next change would
        assertEquals("{not json", java.io.File(dir.root, "tvs.json.bad").readText())
    }

    @Test fun upsertThenRemove() {
        val f = dir.newFile("tvs.json")
        val s = Saved(f)
        s.upsert(JSONObject().put("host", "192.0.2.7").put("name", "TV"), null)
        assertEquals("192.0.2.7", s.current)
        s.remove("192.0.2.7")
        assertEquals(0, s.tvs.length())
        assertNull(s.find("192.0.2.7"))
    }
}
