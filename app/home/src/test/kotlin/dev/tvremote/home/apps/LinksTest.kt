package dev.tvremote.home.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LinksTest {
    private val site = File("../../docs/home/links.json")

    @Test fun theSitesListParses() {
        val a = Links.parse(site.readText())!!
        val vtv1 = a.items.first { it.pkg == "vn.vtv.vtvgotv" && it.name == "VTV1" }
        assertEquals("vtvgo://play?mediaType=CHANNEL&contentId=1", Links.link(vtv1))   // tried on the FPT box, 2026-10-27
        assertEquals("tv360://recommendation?type=LIVE&id=2", Links.link(a.items.first { it.pkg == "com.viettel.tv360.tv" && it.name == "VTV1" }))
        assertTrue(a.items.all { it.logo.isNotEmpty() && File("../../docs/home/logos/${it.logo}.png").isFile })
    }

    @Test fun linksThatAreNotPlainDeepLinksAreDropped() {
        fun app(uri: String) = """{"pkg":"a.b","name":"A","uri":"$uri","items":[{"id":"1","name":"One"}]}"""
        val a = Links.parse("""{"v":1,"rev":1,"apps":[${app("intent:#Intent;end")},${app("file:///x{id}")},${app("content://x/{id}")},${app("ab://x")},${app("ab://x/{id}")}]}""")!!
        assertEquals(listOf("ab://x/1"), a.items.map { Links.link(it) })
    }

    @Test fun anEntryWithNoPackageTakesHttpsOnly() {
        fun app(uri: String) = """{"pkg":"*","name":"Web","uri":"$uri","items":[{"id":"1","name":"One"}]}"""
        val a = Links.parse("""{"v":1,"rev":1,"apps":[${app("https://www.youtube.com/@{id}")},${app("ab://x/{id}")}]}""")!!
        assertEquals(listOf("https://www.youtube.com/@1"), a.items.map { Links.link(it) })
        assertTrue(a.items[0].any); assertEquals("*Web", a.items[0].group)
        val yt = Links.parse(site.readText())!!.items.filter { it.any && it.app == "YouTube" }
        assertTrue(yt.size >= 25 && yt.all { it.id.matches(Regex("[A-Za-z0-9_.\\-]{1,40}")) })
    }

    @Test fun badItemsAndPackagesAreDropped() {
        val a = Links.parse("""{"v":1,"rev":1,"apps":[{"pkg":"nodot","uri":"a://{id}","items":[{"id":"1","name":"x"}]},
            {"pkg":"a.b","uri":"a://{id}","items":[{"id":"1&x=2","name":"x"},{"id":"2","name":" "},{"id":"3","name":"ok","logo":"../x"}]}]}""")!!
        assertEquals(1, a.items.size); assertEquals("", a.items[0].logo)
        assertNull(Links.parse("""{"v":2}"""))
    }
}
