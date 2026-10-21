package dev.tvremote.channels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindTest {
    private fun c(id: String, name: String, url: String = "http://x/$id") = listOf(Channel(id, name, "", "", url))
    private val list = listOf(c("a", "VTV10"), c("b", "VTV1 HD"), c("c", "An Giang TV 1"), c("d", "Vĩnh Long 1"), c("e", "Vinh Long 2"))
    private fun best(t: String, mine: Set<String> = emptySet(), dead: Set<String> = emptySet(), l: List<List<Channel>> = list) =
        Find.best(l, t, { it in mine }, { it.id in dead })?.first()?.id

    @Test fun names() {
        assertEquals("b", best("vtv1"))                // on a word boundary, ahead of VTV10
        assertEquals("c", best("an giang 1"))
        assertEquals("d", best("Vinh Long 1"))
        assertEquals("c", best("angiangtv1"))            // spaces ignored
        assertNull(best("zzz nothing"))
    }

    @Test fun ties() {
        val l = listOf(c("x", "News One"), c("y", "News One"))
        assertEquals("y", best("news one", mine = setOf("y"), l = l))
        assertEquals("y", best("news one", dead = setOf("x"), l = l))
        assertEquals("a", best("vtv10"))
        assertEquals("b", best("vtv1 hd"))
    }
}
