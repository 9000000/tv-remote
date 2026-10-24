package dev.tvremote.home

import dev.tvremote.update.SignedList
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The ready-made themes: the site's list is signed, the packed copy equals it, and an altered one is ignored. */
class HomeThemesTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "docs/home").isDirectory }
    private val list = File(root, "docs/home/themes.json").readBytes()
    private val sig = File(root, "docs/home/themes.json.sig").readText()

    @Test fun theShippedListIsSignedAndAllThemesAreWholeLooks() {
        assertTrue("docs/home/themes.json must verify (dev/sign/sign_themes.py)", SignedList.verify(list, sig))
        val t = HomeConfig.parseReady(String(list), "99.0.0")
        assertEquals("modern", t.first().id)
        assertEquals("Hiện đại", t.first().name("vi")); assertEquals("Modern", t.first().name("en")); assertEquals("Modern", t.first().name("fr"))
        for (r in t) assertEquals(r.id, HomeConfig.THEME_KEYS.sorted(), r.conf.keys().asSequence().toList().sorted())
    }

    @Test fun thePackedCopyEqualsTheSiteList() {
        val packed = File("build/generated/assets")
        assertArrayEquals(list, File(packed, "themes.json").readBytes())
        assertEquals(sig, File(packed, "themes.json.sig").readText())
    }

    @Test fun anAlteredListIsNotTrustedAndSinceWaits() {
        assertTrue(!SignedList.verify(String(list).replace("Modern", "Evil").toByteArray(), sig))
        val j = """{"v":1,"themes":[{"id":"a","names":{"en":"A"},"conf":{"wall":"night"}},{"id":"b","names":{"en":"B"},"since":"9.0.0","conf":{"wall":"night"}},{"id":"Bad id","conf":{}}]}"""
        assertEquals(listOf("a"), HomeConfig.parseReady(j, "0.8.0").map { it.id })
        assertEquals(listOf("a", "b"), HomeConfig.parseReady(j, "9.0.0").map { it.id })
        assertTrue(HomeConfig.parseReady("not json", "1").isEmpty())
    }

    @Test fun aWebPageBackgroundComesThroughTheListOnlyWhenHttp() {
        val j = """{"v":1,"themes":[{"id":"w","names":{"en":"W"},"conf":{"wall":"web","wurl":"https://x.dev/a"}},{"id":"x","names":{"en":"X"},"conf":{"wall":"web","wurl":"javascript:1"}}]}"""
        val t = HomeConfig.parseReady(j, "1")
        assertEquals("web", t[0].conf.getString("wall")); assertEquals("https://x.dev/a", t[0].conf.getString("wurl"))
        assertEquals("night", t[1].conf.getString("wall"))
    }

    @Test fun everyShippedThemeHasAudienceNamesAndNoFlatBlack() {
        val j = org.json.JSONObject(String(list)).getJSONArray("themes")
        for (i in 0 until j.length()) {
            val t = j.getJSONObject(i); val id = t.getString("id")
            assertTrue("$id: for", t.getJSONObject("for").optString("vi").isNotEmpty() && t.getJSONObject("for").optString("en").isNotEmpty())
            assertTrue("$id: names", t.getJSONObject("names").optString("vi").isNotEmpty() && t.getJSONObject("names").optString("en").isNotEmpty())
            assertTrue("$id: a flat black wall is no look", t.getJSONObject("conf").optString("wall") != "black")
            t.getJSONObject("conf").optString("wurl").takeIf { it.isNotEmpty() }?.let { assertTrue("$id: https", it.startsWith("https://")); assertTrue("$id: src", t.optString("src").isNotEmpty()) }
        }
        val r = HomeConfig.parseReady(String(list), "99.0.0")
        assertEquals(j.length(), r.size)
        assertEquals(listOf("tile:dev.tvremote.setup/sleep"), r.first { it.id == "bed" }.tiles)
        assertEquals("sunset", r.first { it.id == "beach" }.look)
    }

    @Test fun aThemesTilesGoFirstBesideTheOwnAndAMissingProviderIsSkipped() {
        HomeConfig.ready = HomeConfig.parseReady(String(list), "99.0.0")
        val own = org.json.JSONObject().put("tiles", org.json.JSONArray(listOf("pin:com.a/b", "tile:dev.tvremote.setup/ram"))).put("order", org.json.JSONArray(listOf("com.x.y")))
        val apply = org.json.JSONObject().put("theme", org.json.JSONObject().put("apply", "bed"))
        val on = HomeConfig.merge(own, apply, { true }, { true })
        assertEquals("tile:dev.tvremote.setup/sleep", on.getJSONArray("tiles").getString(0))
        assertEquals("pin:com.a/b", on.getJSONArray("tiles").getString(1))                       // the person's own stay
        assertEquals("tile:dev.tvremote.setup/sleep", on.getJSONArray("order").getString(0))
        assertEquals("com.x.y", on.getJSONArray("order").getString(1))
        val off = HomeConfig.merge(own, apply, { false }, { true })                                // no TV Setup: nothing added, nothing lost
        assertEquals(2, off.getJSONArray("tiles").length())
        assertEquals(1, off.getJSONArray("order").length())
        val twice = HomeConfig.merge(on, apply, { true }, { true })
        assertEquals(on.getJSONArray("tiles").length(), twice.getJSONArray("tiles").length())     // applied again: no copies
    }
}
