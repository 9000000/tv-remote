package dev.tvremote.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The Downloader codes: the site's file is signed and equals the packed copy; an unsigned or altered one is ignored. */
class DownloaderTest {
    private val dir = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "docs/data") }.first { it.isDirectory }
    private val list get() = File(dir, "downloader.json").readBytes()
    private val sig get() = File(dir, "downloader.json.sig").readText()
    @After fun reset() { Downloader.dir = null }

    @Test fun theShippedFileIsSignedAndEqualsThePackedCopy() {
        val site = Downloader.accept(list, sig)
        assertTrue("docs/data/downloader.json must verify (dev/sign/sign_downloader.py)", site != null)
        assertEquals(Downloader.parse(Downloader.PACKED), site)
        assertEquals("2397346", site!!["setup"])
    }

    @Test fun anAlteredOrUnsignedListIsIgnored() {
        val bad = String(list).replace("2397346", "1111111").toByteArray()
        assertNull(Downloader.accept(bad, sig))
        assertNull(Downloader.accept(list, ""))
        assertNull(Downloader.accept(list, "not base64 !!"))
    }

    @Test fun aShapeThatIsNotOursIsRefused() {
        assertNull(Downloader.parse("""{"v":2,"setup":"2397346"}"""))
        assertNull(Downloader.parse("""{"v":1,"setup":"abc; rm"}"""))
        assertNull(Downloader.parse("not json"))
        assertEquals(mapOf("setup" to "2397346"), Downloader.parse("""{"v":1,"setup":"2397346","games":null}"""))
    }

    @Test fun aKeptCopyIsUsedOnlyWhileItVerifies() {
        val d = createTempDir("dlc")
        try {
            Downloader.dir = d
            assertEquals("packed when nothing is kept", "2397346", Downloader.code("setup"))
            File(d, "downloader.json").writeText("""{"v":1,"setup":"9999999"}""")       // kept but unsigned
            File(d, "downloader.json.sig").writeText("AAAA")
            assertEquals("an unsigned kept copy falls back to the packed one", "2397346", Downloader.code("setup"))
            File(d, "downloader.json").writeBytes(list); File(d, "downloader.json.sig").writeText(sig)
            assertEquals("2397346", Downloader.code("setup"))
        } finally { d.deleteRecursively() }
    }
}
