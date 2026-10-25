package dev.tvremote.core

import dev.tvremote.core.picture.Jpeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JpegTest {
    private fun image(w: Int, h: Int) = ByteArray(w * h * 3).also { for (y in 0 until h) for (x in 0 until w) { val i = (y * w + x) * 3; it[i] = (x * 255 / w).toByte(); it[i + 1] = (y * 255 / h).toByte(); it[i + 2] = ((x xor y) and 255).toByte() } }

    @Test fun makesAJpegWithTheRightFrame() {
        val b = Jpeg.encode(37, 21, image(37, 21), 80)                         // not a multiple of 8: the edge is repeated
        assertEquals(0xFF, b[0].toInt() and 255); assertEquals(0xD8, b[1].toInt() and 255)
        assertEquals(0xFF, b[b.size - 2].toInt() and 255); assertEquals(0xD9, b[b.size - 1].toInt() and 255)
        val sof = (0 until b.size - 8).first { (b[it].toInt() and 255) == 0xFF && (b[it + 1].toInt() and 255) == 0xC0 }
        assertEquals(21, ((b[sof + 5].toInt() and 255) shl 8) or (b[sof + 6].toInt() and 255))
        assertEquals(37, ((b[sof + 7].toInt() and 255) shl 8) or (b[sof + 8].toInt() and 255))
    }

    @Test fun fitOnlyShrinks() {
        val (w, h, px) = Jpeg.fit(1920, 1080, ByteArray(1920 * 1080 * 3), 1280, 720)
        assertEquals(1280, w); assertEquals(720, h); assertEquals(1280 * 720 * 3, px.size)
        assertEquals(100, Jpeg.fit(100, 50, ByteArray(100 * 50 * 3), 1280, 720).first)
    }

    @Test fun lowerQualityIsSmaller() = assertTrue(Jpeg.encode(64, 64, image(64, 64), 30).size < Jpeg.encode(64, 64, image(64, 64), 90).size)
}
