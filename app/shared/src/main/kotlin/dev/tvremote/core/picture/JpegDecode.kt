package dev.tvremote.core.picture

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * A baseline JPEG read back to RGB in plain Kotlin (the JVM the computer app ships has no ImageIO and the phone app's BitmapFactory is
 * not in :shared), the other half of [Jpeg]'s encoder. Enough for what a browser's canvas or a camera app writes: sequential Huffman,
 * 8 bits, 1 or 3 components with any sampling, one scan, restart intervals. A progressive or arithmetic file answers null (the caller
 * keeps the bytes it had). Used by ThemeShare to shrink a photo before it goes to the community store.
 */
object JpegDecode {
    private class Comp(val id: Int, val h: Int, val v: Int, val tq: Int) {
        var dc = 0; var td = 0; var ta = 0
        var bw = 0; var bh = 0                                       // blocks across / down, padded to whole MCUs
        var plane = ByteArray(0)                                     // bw*8 x bh*8 samples
    }

    private class Huff(lens: IntArray, val vals: IntArray) {
        val mincode = IntArray(17); val maxcode = IntArray(17) { -1 }; val valptr = IntArray(17)
        init {
            var code = 0; var k = 0
            for (l in 1..16) {
                valptr[l] = k; mincode[l] = code
                code += lens[l]; k += lens[l]
                if (lens[l] > 0) maxcode[l] = code - 1
                code = code shl 1
            }
        }
    }

    private val ZIGZAG = intArrayOf(0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5, 12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
        35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51, 58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63)
    private val COS = Array(8) { x -> FloatArray(8) { u -> ((if (u == 0) 1.0 / sqrt(2.0) else 1.0) * 0.5 * cos((2 * x + 1) * u * PI / 16)).toFloat() } }

    /** (width, height, rgb w*h*3), or null when this is not a JPEG this reader handles. */
    fun rgb(b: ByteArray, maxPixels: Long = 40_000_000L): Triple<Int, Int, ByteArray>? = try { Reader(b, maxPixels).run() } catch (e: Exception) { null }

    private class Reader(val b: ByteArray, val maxPixels: Long) {
        var p = 0
        val q = Array(4) { IntArray(64) { 1 } }
        val dcT = arrayOfNulls<Huff>(4); val acT = arrayOfNulls<Huff>(4)
        var comps = emptyList<Comp>(); var w = 0; var h = 0; var restart = 0
        var bits = 0; var nbits = 0; var hit = false                 // hit: a marker was met inside the data

        fun u8() = b[p++].toInt() and 255
        fun u16() = (u8() shl 8) or u8()

        fun run(): Triple<Int, Int, ByteArray>? {
            if (b.size < 4 || u16() != 0xFFD8) return null
            while (p + 4 <= b.size) {
                if (u8() != 0xFF) return null
                var m = u8()
                while (m == 0xFF) m = u8()
                if (m == 0xD9) return null
                if (m in 0xD0..0xD8 || m == 0x01 || m == 0) continue
                val len = u16(); val end = p + len - 2
                when (m) {
                    0xDB -> while (p < end) { val pq = u8(); val t = pq and 3; for (i in 0 until 64) q[t][ZIGZAG[i]] = if (pq shr 4 == 0) u8() else u16() }
                    0xC0, 0xC1 -> {
                        if (u8() != 8) return null
                        h = u16(); w = u16()
                        val n = u8()
                        if (w <= 0 || h <= 0 || w.toLong() * h > maxPixels || (n != 1 && n != 3)) return null
                        comps = List(n) { val id = u8(); val s = u8(); val tq = u8(); Comp(id, s shr 4, s and 15, tq and 3) }
                        if (comps.any { it.h !in 1..4 || it.v !in 1..4 }) return null
                    }
                    0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF -> return null
                    0xC4 -> while (p < end) {
                        val tc = u8(); val lens = IntArray(17); var n = 0
                        for (i in 1..16) { lens[i] = u8(); n += lens[i] }
                        val vals = IntArray(n) { u8() }
                        (if (tc shr 4 == 0) dcT else acT)[tc and 3] = Huff(lens, vals)
                    }
                    0xDD -> restart = u16()
                    0xDA -> {
                        if (comps.isEmpty() || u8() != comps.size) return null     // one scan with every component
                        for (i in comps.indices) { val id = u8(); val t = u8(); val c = comps.firstOrNull { it.id == id } ?: return null; c.td = t shr 4; c.ta = t and 15 }
                        p = end
                        return decode()
                    }
                    else -> {}
                }
                p = end
            }
            return null
        }

        fun fill() {
            while (nbits <= 24) {
                var v = 0
                if (!hit && p < b.size) {
                    v = u8()
                    if (v == 0xFF) {
                        val n = if (p < b.size) b[p].toInt() and 255 else 0xD9
                        if (n == 0) p++ else { hit = true; p--; v = 0 }   // a marker: zeros till the caller sees it
                    }
                }
                bits = bits or (v shl (24 - nbits)); nbits += 8
            }
        }
        fun bit(): Int { if (nbits < 1) fill(); val r = bits ushr 31; bits = bits shl 1; nbits--; return r }
        fun get(n: Int): Int { var r = 0; for (i in 0 until n) r = (r shl 1) or bit(); return r }
        fun ext(v: Int, n: Int) = if (n == 0) 0 else if (v < (1 shl (n - 1))) v - (1 shl n) + 1 else v
        fun sym(t: Huff): Int {
            var code = 0
            for (l in 1..16) {
                code = (code shl 1) or bit()
                if (t.maxcode[l] >= 0 && code <= t.maxcode[l] && code >= t.mincode[l]) return t.vals[t.valptr[l] + code - t.mincode[l]]
            }
            throw IllegalStateException("code")
        }

        fun decode(): Triple<Int, Int, ByteArray>? {
            val hm = comps.maxOf { it.h }; val vm = comps.maxOf { it.v }
            val single = comps.size == 1
            val mx = if (single) (w + 7) / 8 else (w + hm * 8 - 1) / (hm * 8)
            val my = if (single) (h + 7) / 8 else (h + vm * 8 - 1) / (vm * 8)
            for (c in comps) {
                c.bw = if (single) mx else mx * c.h; c.bh = if (single) my else my * c.v
                c.plane = ByteArray(c.bw * 8 * c.bh * 8)
            }
            val blk = FloatArray(64); val tmp = FloatArray(64)
            var left = restart
            for (my0 in 0 until my) for (mx0 in 0 until mx) {
                if (restart > 0 && left == 0) {                       // RSTn: byte-align, skip the marker, reset the predictors
                    nbits = 0; bits = 0
                    if (hit) { hit = false; p += 2 } else { while (p + 1 < b.size && !(b[p].toInt() == -1 && (b[p + 1].toInt() and 255) in 0xD0..0xD7)) p++; p += 2 }
                    comps.forEach { it.dc = 0 }; left = restart
                }
                left--
                for (c in comps) {
                    val bh = if (single) 1 else c.v; val bw = if (single) 1 else c.h
                    val dt = dcT[c.td] ?: return null; val at = acT[c.ta] ?: return null
                    val qt = q[c.tq]
                    for (by in 0 until bh) for (bx in 0 until bw) {
                        blk.fill(0f)
                        val s = sym(dt)
                        c.dc += ext(get(s), s)
                        blk[0] = (c.dc * qt[0]).toFloat()
                        var k = 1
                        while (k < 64) {
                            val rs = sym(at); val r = rs shr 4; val sz = rs and 15
                            if (sz == 0) { if (r == 15) { k += 16; continue } else break }
                            k += r
                            if (k > 63) break
                            val z = ZIGZAG[k]
                            blk[z] = (ext(get(sz), sz) * qt[z]).toFloat()
                            k++
                        }
                        idct(blk, tmp)
                        val ox = (mx0 * bw + bx) * 8; val oy = (my0 * bh + by) * 8; val stride = c.bw * 8
                        for (y in 0 until 8) for (x in 0 until 8) c.plane[(oy + y) * stride + ox + x] = (blk[y * 8 + x] + 128.5f).toInt().coerceIn(0, 255).toByte()
                    }
                }
            }
            val out = ByteArray(w * h * 3)
            for (y in 0 until h) for (x in 0 until w) {
                val o = (y * w + x) * 3
                fun sample(c: Comp): Int = c.plane[((y * c.v / vm)) * (c.bw * 8) + (x * c.h / hm)].toInt() and 255
                if (single) { val g = sample(comps[0]).toByte(); out[o] = g; out[o + 1] = g; out[o + 2] = g }
                else {
                    val yy = sample(comps[0]).toFloat(); val cb = sample(comps[1]) - 128f; val cr = sample(comps[2]) - 128f
                    out[o] = (yy + 1.402f * cr + 0.5f).toInt().coerceIn(0, 255).toByte()
                    out[o + 1] = (yy - 0.344136f * cb - 0.714136f * cr + 0.5f).toInt().coerceIn(0, 255).toByte()
                    out[o + 2] = (yy + 1.772f * cb + 0.5f).toInt().coerceIn(0, 255).toByte()
                }
            }
            return Triple(w, h, out)
        }

        fun idct(f: FloatArray, t: FloatArray) {
            for (v in 0 until 8) for (x in 0 until 8) { var s = 0f; for (u in 0 until 8) s += COS[x][u] * f[v * 8 + u]; t[v * 8 + x] = s }
            for (x in 0 until 8) for (y in 0 until 8) { var s = 0f; for (v in 0 until 8) s += COS[y][v] * t[v * 8 + x]; f[y * 8 + x] = s }
        }
    }
}
