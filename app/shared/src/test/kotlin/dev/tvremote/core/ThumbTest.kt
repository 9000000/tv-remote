package dev.tvremote.core

import dev.tvremote.core.picture.Thumb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

// android.jar has no ImageIO: a small PNG writer here (with the row filter of the test's choice) and a reader for what Thumb writes
class ThumbTest {
    private fun png(w: Int, h: Int, alpha: Boolean, filter: Int = 0, paint: (Int, Int) -> Int): ByteArray {
        val bpp = if (alpha) 4 else 3
        val rows = Array(h) { y -> ByteArray(w * bpp).also { r -> for (x in 0 until w) { val c = paint(x, y)
            r[x * bpp] = (c shr 16).toByte(); r[x * bpp + 1] = (c shr 8).toByte(); r[x * bpp + 2] = c.toByte(); if (alpha) r[x * bpp + 3] = 0xFF.toByte() } } }
        val raw = ByteArrayOutputStream()
        for (y in 0 until h) {
            raw.write(filter)
            for (i in 0 until w * bpp) {
                val a = if (i >= bpp) rows[y][i - bpp].toInt() and 255 else 0
                val b = if (y > 0) rows[y - 1][i].toInt() and 255 else 0
                val c = if (y > 0 && i >= bpp) rows[y - 1][i - bpp].toInt() and 255 else 0
                val p = a + b - c
                val pa = Math.abs(p - a); val pb = Math.abs(p - b); val pc = Math.abs(p - c)
                val paeth = if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
                val sub = when (filter) { 1 -> a; 2 -> b; 3 -> (a + b) / 2; 4 -> paeth; else -> 0 }
                raw.write(((rows[y][i].toInt() and 255) - sub) and 255)
            }
        }
        val d = Deflater(); d.setInput(raw.toByteArray()); d.finish()
        val z = ByteArrayOutputStream(); val buf = ByteArray(65536)
        while (!d.finished()) z.write(buf, 0, d.deflate(buf))
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        fun chunk(t: String, data: ByteArray) {
            out.write(ByteBuffer.allocate(4).putInt(data.size).array()); val tb = t.toByteArray(); out.write(tb); out.write(data)
            out.write(ByteBuffer.allocate(4).putInt(CRC32().also { it.update(tb); it.update(data) }.value.toInt()).array())
        }
        chunk("IHDR", ByteBuffer.allocate(13).putInt(w).putInt(h).put(8).put(if (alpha) 6 else 2).put(0).put(0).put(0).array())
        chunk("IDAT", z.toByteArray()); chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    /** Width, height and the pixels (0xRRGGBB) of a PNG that Thumb wrote (8-bit RGB, filter 0). */
    private fun read(png: ByteArray): Triple<Int, Int, IntArray> {
        val b = ByteBuffer.wrap(png)
        val w = b.getInt(16); val h = b.getInt(20)
        val len = b.getInt(33)
        val inf = Inflater().apply { setInput(png, 41, len) }
        val raw = ByteArray(h * (1 + w * 3)); var got = 0
        while (got < raw.size) got += inf.inflate(raw, got, raw.size - got)
        return Triple(w, h, IntArray(w * h) { i -> val y = i / w; val x = i % w; val o = y * (1 + w * 3) + 1 + x * 3
            ((raw[o].toInt() and 255) shl 16) or ((raw[o + 1].toInt() and 255) shl 8) or (raw[o + 2].toInt() and 255) })
    }

    @Test fun smallAndKeepsTheColours() {
        for (filter in 0..4) {                                        // every row filter PNG has
            val big = png(1920, 1080, true, filter) { x, y -> if (x < 960) 0xFF0000 else (y / 5) }   // left red, right a blue ramp
            val small = Thumb.small(big, 320)
            assertNotNull(small)
            val (w, h, px) = read(small!!)
            assertEquals(320, w)
            assertEquals(180, h)
            assertEquals(0xFF0000, px[10 * w + 10])
            assertEquals(0, px[10 * w + 300] and 0xFF0000)            // no red on the right
            assertEquals(12, px[10 * w + 300] and 0xFF)               // the ramp: rows 60..65 are 12 12 12 12 12 13 -> 12
            assertTrue(small.size < big.size)
        }
    }

    @Test fun plainRgbAndAlreadySmall() {
        val (w, h, px) = read(Thumb.small(png(100, 50, false) { _, _ -> 0x00FF00 }, 320)!!)
        assertEquals(100, w)                                          // not stretched
        assertEquals(50, h)
        assertEquals(0x00FF00, px[5 * w + 5])
    }

    @Test fun averagesABlock() {
        val (w, _, px) = read(Thumb.small(png(8, 4, true) { x, _ -> if (x % 2 == 0) 0xFFFFFF else 0x000000 }, 4)!!)   // 2x2 blocks: half white
        assertEquals(4, w)
        assertEquals(0x7F7F7F, px[0])
    }

    @Test fun nonsenseIsNull() {
        assertNull(Thumb.small(ByteArray(0)))
        assertNull(Thumb.small("not a png at all, just text of some length....".toByteArray()))
        val ok = png(64, 64, true) { _, _ -> 0 }
        assertNull(Thumb.small(ok.copyOf(ok.size / 2)))               // cut short
    }
}
