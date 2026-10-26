package dev.tvremote.core.ontv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class WallpapersTest {
    private val sha = "a".repeat(64)
    private fun list(file: String = "x-y.jpg") = """{"v":1,"walls":[{"id":"x","name":{"vi":"X","en":"Ex"},"file":"$file","sha256":"$sha",
        "bytes":300000,"thumb":"x-t.jpg","thumb_sha256":"$sha","credit":"NASA","source":"https://images.nasa.gov/details/x","license":"Public domain"}]}"""

    @Test fun parsesAnEntry() {
        val g = Wallpapers.parse(list().toByteArray(), "https://e/")
        assertEquals("Ex", g["x"]!!.name["en"])
        assertEquals("NASA", g["x"]!!.credit)
        assertEquals("https://e/", g.base)
    }

    @Test fun refusesAFileNameThatIsAPath() {
        for (bad in listOf("../x.jpg", "a/b.jpg", "x.png", "http://e/x.jpg")) assertThrows(IOException::class.java) { Wallpapers.parse(list(bad).toByteArray(), "b/") }
    }

    @Test fun theSitesListIsSignedAndMatchesItsFiles() {
        val dir = listOf("../docs/home", "../../docs/home", "docs/home").map { File(it) }.firstOrNull { File(it, "wallpapers.json").isFile } ?: return
        val bytes = File(dir, "wallpapers.json").readBytes()
        assertTrue(Launchers.signed(bytes, File(dir, "wallpapers.json.sig").readText()))
        for (w in Wallpapers.parse(bytes, "b/").walls) {
            val f = File(dir, w.file).readBytes()
            assertEquals(w.sha, java.security.MessageDigest.getInstance("SHA-256").digest(f).joinToString("") { String.format("%02x", it) })
            assertTrue(f.size < 600_000)
        }
        // the suggested videos and pages: each thumb is there and matches, each page a host TV Home draws natively or a known live page
        val links = Wallpapers.parse(bytes, "b/").links
        assertTrue(links.size >= 10)
        for (l in links) {
            if (l.thumb.isNotEmpty()) assertEquals(l.thumbSha, java.security.MessageDigest.getInstance("SHA-256").digest(File(dir, l.thumb).readBytes()).joinToString("") { String.format("%02x", it) })
            if (l.kind != "page") assertTrue(l.url, l.url.startsWith("https://tvremote.pages.dev/home/loop.html?"))
        }
    }
}
