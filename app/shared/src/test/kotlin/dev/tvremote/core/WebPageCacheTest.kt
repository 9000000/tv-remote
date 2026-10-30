package dev.tvremote.core

import dev.tvremote.core.web.WebPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/** The page's files are read once and zipped once however many there are (it had a cap of 60 and the page has ~80). */
class WebPageCacheTest {
    @Test fun everyFileIsKeptWhetherPlainOrZipped() {
        val opened = HashMap<String, Int>()
        val page = WebPage({ p -> opened[p] = (opened[p] ?: 0) + 1; ByteArrayInputStream(ByteArray(5000) { (it % 7).toByte() }) }, "phone", "1")
        val names = (1..120).map { "js/f$it.js" }
        repeat(2) { for (n in names) assertTrue(page.serve("/$n", "gzip") != null) }
        assertEquals(setOf(1), opened.values.toSet())             // 'open' once per path across two gzip requests
        assertEquals(names.size, opened.size)
    }
}
