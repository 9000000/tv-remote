package dev.tvremote.core

import dev.tvremote.core.ontv.ThemeFile
import dev.tvremote.core.ontv.ThemeShare
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import org.junit.Test
import org.junit.Assert.assertEquals

class ThemeFileTest {
    @Test fun keysAreOwnOnesInOrderOfUse() {
        val c = JSONObject("""{"wall":"photo2","walls":["photo","photo2","night","vid:0123abcd"]}""")
        assertEquals(listOf("photo2", "photo", "vid:0123abcd"), ThemeFile.mediaKeys(c))
        assertEquals(listOf("photo2", "photo"), ThemeShare.photoKeys(c))
    }

    @Test fun fileReadsBackWithItsMedia() {
        val conf = JSONObject("""{"wall":"photo"}""")
        val bytes = ByteArray(100_000) { (it * 7).toByte() }                // more than one 48 KB chunk, not a multiple of 3 either
        val out = ByteArrayOutputStream()
        ThemeFile.write(out, "N", conf, listOf("photo")) { _, sink -> sink(bytes, 70_000); sink(bytes.copyOfRange(70_000, 100_000), 30_000) }
        val j = JSONObject(out.toString())
        assertEquals(1, j.getInt("tvtheme")); assertEquals("N", j.getString("name"))
        val d = j.getJSONObject("media").getString("photo")
        assertEquals("data:image/jpeg;base64,", d.substringBefore(',') + ",")
        assertEquals(bytes.toList(), java.util.Base64.getDecoder().decode(d.substringAfter(',')).toList())
    }

    @Test fun videoTypeComesFromItsBytes() {
        val webm = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 1, 2, 3)
        val mp4 = byteArrayOf(0, 0, 0, 0x20, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
        val out = ByteArrayOutputStream()
        ThemeFile.write(out, "N", JSONObject("""{"walls":["vid:0000000a","vid:0000000b"]}"""), listOf("vid:0000000a", "vid:0000000b")) { k, sink ->
            if (k.endsWith("a")) sink(webm, webm.size) else sink(mp4, mp4.size)
        }
        val m = JSONObject(out.toString()).getJSONObject("media")
        assertEquals("data:video/webm;base64", m.getString("vid:0000000a").substringBefore(','))
        assertEquals("data:video/mp4;base64", m.getString("vid:0000000b").substringBefore(','))
    }

    @Test fun fileWithoutMediaIsPlainV1() {
        val out = ByteArrayOutputStream()
        ThemeFile.write(out, "N", JSONObject("""{"wall":"night"}"""), emptyList()) { _, _ -> }
        assertEquals(false, JSONObject(out.toString()).has("media"))
    }
}
