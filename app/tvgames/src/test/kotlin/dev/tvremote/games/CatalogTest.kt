package dev.tvremote.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [Catalog.byFile] answers what the old scan answered: the first entry whose cleaned name is the file's. */
class CatalogTest {
    private val clean = Regex("[^A-Za-z0-9 ._-]")
    private val entries = (0 until 300).map { (if (it % 7 == 0) "Gäme: $it / ★" else "Game $it") to it } + ("Game 3" to 999)

    @Test fun sameAnswersAsTheScan() {
        val m = Catalog.byFile(entries)
        for (file in listOf("Game 1", "Game 3", "Gme 7", "Gme 14", "Game 299", "None", "")) {
            val want = entries.firstOrNull { it.first.replace(clean, "") == file }?.second
            assertEquals(file, want, m[file])
        }
        assertEquals(3, m["Game 3"])                    // a name twice: the first wins
        assertNull(m["Game 999"])
    }
}
