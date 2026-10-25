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

    @Test fun decodesWhatItEncodes() {
        val src = image(61, 37)
        val (w, h, px) = dev.tvremote.core.picture.JpegDecode.rgb(Jpeg.encode(61, 37, src, 92))!!
        assertEquals(61, w); assertEquals(37, h)
        var err = 0L; for (i in px.indices) err += Math.abs((px[i].toInt() and 255) - (src[i].toInt() and 255))
        assertTrue("mean error ${err / px.size}", err / px.size < 12)             // a gradient with a hard xor band: lossy but near
    }

    @Test fun progressiveOrJunkIsNull() {
        assertEquals(null, dev.tvremote.core.picture.JpegDecode.rgb(ByteArray(50)))
        val j = Jpeg.encode(16, 16, image(16, 16), 80).also { for (i in 0 until it.size - 1) if ((it[i].toInt() and 255) == 0xFF && (it[i + 1].toInt() and 255) == 0xC0) it[i + 1] = 0xC2.toByte() }
        assertEquals(null, dev.tvremote.core.picture.JpegDecode.rgb(j))
    }

    @Test fun shrinkKeepsSmallAndShrinksBig() {
        val small = Jpeg.encode(64, 64, image(64, 64), 80)
        assertTrue(Jpeg.shrink(small) === small)
        val noisy = ByteArray(1200 * 800 * 3).also { java.util.Random(1).nextBytes(it) }
        val big = Jpeg.encode(1200, 800, noisy, 90)
        assertTrue(big.size > 350_000)
        val s = Jpeg.shrink(big, 600, 600, 150_000)!!
        assertTrue(s.size <= 150_000)
        assertEquals(600 to 400, Jpeg.size(s))
        assertEquals(null, Jpeg.shrink(ByteArray(400_000) { 7 }))                   // too big and unreadable
    }
}
