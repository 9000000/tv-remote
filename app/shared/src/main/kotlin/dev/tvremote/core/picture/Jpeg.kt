package dev.tvremote.core.picture
import java.io.ByteArrayOutputStream

/**
 * A baseline JPEG from plain RGB, in plain Kotlin: the JVM the computer app ships has no ImageIO and the shared core cannot use
 * Android's Bitmap. Used for the picture of a theme sent to the store (a TV screenshot shrunk to 1280 wide, under a size cap).
 * One set of tables (the standard luminance ones) for all three channels: valid, a little bigger than with separate chroma tables.
 */
object Jpeg {
    private val ZIG = intArrayOf(0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5, 12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
        35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51, 58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63)
    private val QBASE = intArrayOf(16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40, 57, 69, 56, 14, 17, 22, 29, 51, 87, 80, 62,
        18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92, 49, 64, 78, 87, 103, 121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99)
    private val DC_BITS = intArrayOf(0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0)
    private val DC_VALS = IntArray(12) { it }
    private val AC_BITS = intArrayOf(0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d)
    private val AC_VALS = intArrayOf(0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07, 0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08,
        0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0, 0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0a, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2a, 0x34, 0x35, 0x36, 0x37,
        0x38, 0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x73, 0x74, 0x75,
        0x76, 0x77, 0x78, 0x79, 0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8, 0xa9,
        0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2,
        0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8, 0xf9, 0xfa)
    private val COS = Array(8) { u -> DoubleArray(8) { x -> (if (u == 0) Math.sqrt(0.125) else 0.5) * Math.cos((2 * x + 1) * u * Math.PI / 16) } }

    /** code and length by symbol, canonical (JPEG Annex C) */
    private fun codes(bits: IntArray, vals: IntArray): Pair<IntArray, IntArray> {
        val code = IntArray(256); val len = IntArray(256)
        var c = 0; var k = 0
        for (l in 1..16) { repeat(bits[l - 1]) { code[vals[k]] = c; len[vals[k]] = l; c++; k++ }; c = c shl 1 }
        return code to len
    }
    private val DC = codes(DC_BITS, DC_VALS)
    private val AC = codes(AC_BITS, AC_VALS)

    private class Bits {
        val out = ByteArrayOutputStream(); var acc = 0; var n = 0
        fun put(v: Int, len: Int) {
            for (i in len - 1 downTo 0) {
                acc = (acc shl 1) or ((v shr i) and 1); n++
                if (n == 8) { out.write(acc); if (acc == 0xFF) out.write(0); acc = 0; n = 0 }
            }
        }
        fun flush() { while (n != 0) put(1, 1) }
    }

    /** [rgb] (w*h*3) as a JPEG of [quality] 1..100. */
    fun encode(w: Int, h: Int, rgb: ByteArray, quality: Int): ByteArray {
        val s = if (quality < 50) 5000 / quality else 200 - quality * 2
        val q = IntArray(64) { ((QBASE[it] * s + 50) / 100).coerceIn(1, 255) }
        val o = ByteArrayOutputStream()
        fun w16(v: Int) { o.write(v shr 8); o.write(v and 255) }
        o.write(0xFF); o.write(0xD8)
        o.write(0xFF); o.write(0xDB); w16(67); o.write(0); for (i in 0 until 64) o.write(q[ZIG[i]])
        o.write(0xFF); o.write(0xC0); w16(17); o.write(8); w16(h); w16(w); o.write(3)
        for (c in 1..3) { o.write(c); o.write(0x11); o.write(0) }
        o.write(0xFF); o.write(0xC4); w16(2 + 17 + 12); o.write(0x00); DC_BITS.forEach { o.write(it) }; DC_VALS.forEach { o.write(it) }
        o.write(0xFF); o.write(0xC4); w16(2 + 17 + 162); o.write(0x10); AC_BITS.forEach { o.write(it) }; AC_VALS.forEach { o.write(it) }
        o.write(0xFF); o.write(0xDA); w16(12); o.write(3); for (c in 1..3) { o.write(c); o.write(0) }; o.write(0); o.write(63); o.write(0)
        val b = Bits()
        val pred = IntArray(3)
        val blk = DoubleArray(64); val tmp = DoubleArray(64); val zq = IntArray(64)
        for (by in 0 until (h + 7) / 8) for (bx in 0 until (w + 7) / 8) for (c in 0..2) {
            for (y in 0 until 8) for (x in 0 until 8) {
                val px = minOf(bx * 8 + x, w - 1); val py = minOf(by * 8 + y, h - 1)
                val i = (py * w + px) * 3
                val r = rgb[i].toInt() and 255; val g = rgb[i + 1].toInt() and 255; val bl = rgb[i + 2].toInt() and 255
                blk[y * 8 + x] = when (c) {
                    0 -> 0.299 * r + 0.587 * g + 0.114 * bl - 128
                    1 -> -0.168736 * r - 0.331264 * g + 0.5 * bl
                    else -> 0.5 * r - 0.418688 * g - 0.081312 * bl
                }
            }
            for (y in 0 until 8) for (u in 0 until 8) { var t = 0.0; for (x in 0 until 8) t += blk[y * 8 + x] * COS[u][x]; tmp[y * 8 + u] = t }
            for (u in 0 until 8) for (v in 0 until 8) { var t = 0.0; for (y in 0 until 8) t += tmp[y * 8 + u] * COS[v][y]; blk[v * 8 + u] = t }
            for (i in 0 until 64) zq[i] = Math.round(blk[ZIG[i]] / q[ZIG[i]]).toInt()
            val diff = zq[0] - pred[c]; pred[c] = zq[0]
            val (sz, bitsOf) = size(diff)
            b.put(DC.first[sz], DC.second[sz]); if (sz > 0) b.put(bitsOf, sz)
            var run = 0
            for (i in 1 until 64) {
                if (zq[i] == 0) { run++; continue }
                while (run > 15) { b.put(AC.first[0xF0], AC.second[0xF0]); run -= 16 }
                val (zs, zb) = size(zq[i]); val sym = (run shl 4) or zs
                b.put(AC.first[sym], AC.second[sym]); b.put(zb, zs); run = 0
            }
            if (run > 0) b.put(AC.first[0], AC.second[0])
        }
        b.flush(); o.write(b.out.toByteArray())
        o.write(0xFF); o.write(0xD9)
        return o.toByteArray()
    }

    private fun size(v: Int): Pair<Int, Int> {
        var a = Math.abs(v); var n = 0
        while (a != 0) { n++; a = a shr 1 }
        return n to (if (v < 0) (v - 1) and ((1 shl n) - 1) else v)
    }

    /** [rgb] (w*h*3) scaled down (box average, only ever smaller) to fit [maxW] x [maxH]; the same when it already fits. */
    fun fit(w: Int, h: Int, rgb: ByteArray, maxW: Int, maxH: Int): Triple<Int, Int, ByteArray> {
        val k = minOf(maxW.toDouble() / w, maxH.toDouble() / h)
        if (k >= 1.0) return Triple(w, h, rgb)
        val ow = maxOf(1, (w * k).toInt()); val oh = maxOf(1, (h * k).toInt())
        val out = ByteArray(ow * oh * 3)
        for (y in 0 until oh) {
            val y0 = (y * h.toLong() / oh).toInt(); val y1 = maxOf(y0 + 1, ((y + 1) * h.toLong() / oh).toInt())
            for (x in 0 until ow) {
                val x0 = (x * w.toLong() / ow).toInt(); val x1 = maxOf(x0 + 1, ((x + 1) * w.toLong() / ow).toInt())
                var r = 0; var g = 0; var bl = 0; var n = 0
                for (yy in y0 until minOf(y1, h)) for (xx in x0 until minOf(x1, w)) { val i = (yy * w + xx) * 3; r += rgb[i].toInt() and 255; g += rgb[i + 1].toInt() and 255; bl += rgb[i + 2].toInt() and 255; n++ }
                val o = (y * ow + x) * 3; out[o] = (r / n).toByte(); out[o + 1] = (g / n).toByte(); out[o + 2] = (bl / n).toByte()
            }
        }
        return Triple(ow, oh, out)
    }

    /** A PNG shrunk to [maxW] x [maxH] and encoded under [maxBytes] (the quality steps down from 82); null when it cannot be read or will not fit. */
    fun fromPng(png: ByteArray, maxW: Int, maxH: Int, maxBytes: Int): ByteArray? {
        val (w0, h0, rgb0) = Thumb.rgb(png) ?: return null
        val (w, h, rgb) = fit(w0, h0, rgb0, maxW, maxH)
        for (q in intArrayOf(82, 72, 62, 50, 40, 30, 22, 15)) encode(w, h, rgb, q).let { if (it.size <= maxBytes) return it }
        return null
    }

    /**
     * A JPEG shrunk to at most [maxW] wide (and [maxH] tall) and [maxBytes] (the quality steps down from 85); the bytes themselves when they
     * already fit. Null when it must be changed and cannot be read (progressive, not a JPEG) or will not fit even at the lowest quality.
     */
    fun shrink(jpeg: ByteArray, maxW: Int = 1920, maxH: Int = 1920, maxBytes: Int = 350_000): ByteArray? {
        val (w0, h0, rgb0) = JpegDecode.rgb(jpeg) ?: return if (jpeg.size <= maxBytes && size(jpeg)?.let { it.first <= maxW && it.second <= maxH } != false) jpeg else null
        if (jpeg.size <= maxBytes && w0 <= maxW && h0 <= maxH) return jpeg
        val (w, h, rgb) = fit(w0, h0, rgb0, maxW, maxH)
        for (q in intArrayOf(85, 78, 70, 62, 54, 46, 38, 30, 22, 15)) encode(w, h, rgb, q).let { if (it.size <= maxBytes) return it }
        return null
    }

    /** (width, height) from the frame header, or null. */
    fun size(b: ByteArray): Pair<Int, Int>? {
        var i = 2
        while (i + 9 < b.size && (b[i].toInt() and 255) == 0xFF) {
            val m = b[i + 1].toInt() and 255
            if (m in 0xC0..0xCF && m != 0xC4 && m != 0xC8 && m != 0xCC) return (((b[i + 7].toInt() and 255) shl 8) or (b[i + 8].toInt() and 255)) .let { w -> w to (((b[i + 5].toInt() and 255) shl 8) or (b[i + 6].toInt() and 255)) }
            i += 2 + (((b[i + 2].toInt() and 255) shl 8) or (b[i + 3].toInt() and 255))
        }
        return null
    }
}
