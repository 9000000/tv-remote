package dev.tvremote.core.picture
import dev.tvremote.core.adb.Adb
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * A small picture of what a TV shows, for the list of TVs (a thumbnail per TV every few seconds): the TV's own `screencap -p`,
 * shrunk here in plain Kotlin because the JVM the computer app ships has no ImageIO and a 1080p PNG is far more than a card needs.
 */
object Thumb {
    /** The TV's screen as a PNG over [adb]; null when it came back empty (screen off, a TV that refuses). Nothing is left running on the TV. */
    @Throws(IOException::class)
    fun screen(adb: Adb): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(65536)
        adb.open("exec:screencap -p").use {
            while (true) {
                val n = it.read(buf, 0, buf.size, 15000)
                if (n <= 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_PNG) throw IOException("picture too big")
            }
        }
        return if (out.size() > 8) out.toByteArray() else null
    }

    /** [png] shrunk to at most [maxW] wide (a whole-number step, box-averaged); null when it is not an 8-bit RGB / RGBA PNG without interlacing. */
    fun small(png: ByteArray, maxW: Int = 320): ByteArray? {
        val b = ByteBuffer.wrap(png)
        if (png.size < 33 || b.getLong(0) != 0x89504E470D0A1A0AUL.toLong()) return null
        var w = 0; var h = 0; var ct = 0
        val idat = ByteArrayOutputStream()
        var at = 8
        while (at + 12 <= png.size) {
            val len = b.getInt(at); val type = String(png, at + 4, 4, Charsets.ISO_8859_1)
            if (len < 0 || at + 12 + len > png.size) return null
            when (type) {
                "IHDR" -> { w = b.getInt(at + 8); h = b.getInt(at + 12); ct = png[at + 17].toInt()
                    if (png[at + 16].toInt() != 8 || png[at + 20].toInt() != 0 || ct != 2 && ct != 6) return null }
                "IDAT" -> idat.write(png, at + 8, len)
                "IEND" -> break
            }
            at += 12 + len
        }
        if (w <= 0 || h <= 0) return null
        val bpp = if (ct == 6) 4 else 3
        val f = maxOf(1, (w + maxW - 1) / maxW)
        val ow = w / f; val oh = h / f
        if (ow <= 0 || oh <= 0) return null
        val inf = Inflater().apply { setInput(idat.toByteArray()) }
        val stride = w * bpp
        var prev = ByteArray(stride); var cur = ByteArray(stride)
        val sum = IntArray(ow * 3)
        val out = ByteArrayOutputStream()
        val line = ByteArray(1 + ow * 3)
        try {
            for (y in 0 until h) {
                val ft = inflate(inf) ; if (ft < 0) return null
                if (!fill(inf, cur, stride)) return null
                unfilter(ft, cur, prev, bpp)
                if (y / f < oh) {
                    for (x in 0 until ow * f) { val o = x / f * 3; val s = x * bpp; sum[o] += cur[s].toInt() and 255; sum[o + 1] += cur[s + 1].toInt() and 255; sum[o + 2] += cur[s + 2].toInt() and 255 }
                    if (y % f == f - 1) {
                        for (i in 0 until ow * 3) line[1 + i] = (sum[i] / (f * f)).toByte()
                        out.write(line); sum.fill(0)
                    }
                }
                val t = prev; prev = cur; cur = t
            }
        } catch (e: Exception) { return null } finally { inf.end() }
        return encode(ow, oh, out.toByteArray())
    }

    /** The whole PNG as 8-bit RGB (w, h, w*h*3 bytes), the alpha dropped; null on the same terms as [small]. */
    fun rgb(png: ByteArray): Triple<Int, Int, ByteArray>? {
        val b = ByteBuffer.wrap(png)
        if (png.size < 33 || b.getLong(0) != 0x89504E470D0A1A0AUL.toLong()) return null
        var w = 0; var h = 0; var ct = 0
        val idat = ByteArrayOutputStream()
        var at = 8
        while (at + 12 <= png.size) {
            val len = b.getInt(at); val type = String(png, at + 4, 4, Charsets.ISO_8859_1)
            if (len < 0 || at + 12 + len > png.size) return null
            when (type) {
                "IHDR" -> { w = b.getInt(at + 8); h = b.getInt(at + 12); ct = png[at + 17].toInt()
                    if (png[at + 16].toInt() != 8 || png[at + 20].toInt() != 0 || ct != 2 && ct != 6) return null }
                "IDAT" -> idat.write(png, at + 8, len)
                "IEND" -> break
            }
            at += 12 + len
        }
        if (w <= 0 || h <= 0 || w.toLong() * h > 40_000_000L) return null
        val bpp = if (ct == 6) 4 else 3
        val inf = Inflater().apply { setInput(idat.toByteArray()) }
        val stride = w * bpp
        var prev = ByteArray(stride); var cur = ByteArray(stride)
        val out = ByteArray(w * h * 3)
        try {
            for (y in 0 until h) {
                val ft = inflate(inf); if (ft < 0) return null
                if (!fill(inf, cur, stride)) return null
                unfilter(ft, cur, prev, bpp)
                var o = y * w * 3
                for (x in 0 until w) { val s = x * bpp; out[o++] = cur[s]; out[o++] = cur[s + 1]; out[o++] = cur[s + 2] }
                val t = prev; prev = cur; cur = t
            }
        } catch (e: Exception) { return null } finally { inf.end() }
        return Triple(w, h, out)
    }

    private fun inflate(inf: Inflater): Int { val one = ByteArray(1); return if (fill(inf, one, 1)) one[0].toInt() and 255 else -1 }

    private fun fill(inf: Inflater, dst: ByteArray, n: Int): Boolean {
        var got = 0
        while (got < n) { val r = inf.inflate(dst, got, n - got); if (r == 0 && (inf.finished() || inf.needsInput())) return false; got += r }
        return true
    }

    /** PNG's five row filters, undone in place ([prev]: the row above, already plain). */
    private fun unfilter(t: Int, cur: ByteArray, prev: ByteArray, bpp: Int) {
        for (i in cur.indices) {
            val a = if (i >= bpp) cur[i - bpp].toInt() and 255 else 0
            val b = prev[i].toInt() and 255
            val c = if (i >= bpp) prev[i - bpp].toInt() and 255 else 0
            val add = when (t) {
                1 -> a; 2 -> b; 3 -> (a + b) / 2
                4 -> { val p = a + b - c; val pa = Math.abs(p - a); val pb = Math.abs(p - b); val pc = Math.abs(p - c); if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c }
                else -> 0
            }
            cur[i] = (cur[i] + add).toByte()
        }
    }

    private fun encode(w: Int, h: Int, rows: ByteArray): ByteArray {
        val d = Deflater(6)
        d.setInput(rows); d.finish()
        val z = ByteArrayOutputStream(); val tmp = ByteArray(8192)
        while (!d.finished()) z.write(tmp, 0, d.deflate(tmp))
        d.end()
        val o = ByteArrayOutputStream()
        o.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        chunk(o, "IHDR", ByteBuffer.allocate(13).putInt(w).putInt(h).put(8).put(2).put(0).put(0).put(0).array())
        chunk(o, "IDAT", z.toByteArray()); chunk(o, "IEND", ByteArray(0))
        return o.toByteArray()
    }

    private fun chunk(o: ByteArrayOutputStream, type: String, data: ByteArray) {
        o.write(ByteBuffer.allocate(4).putInt(data.size).array())
        val t = type.toByteArray(Charsets.ISO_8859_1)
        o.write(t); o.write(data)
        o.write(ByteBuffer.allocate(4).putInt(CRC32().also { it.update(t); it.update(data) }.value.toInt()).array())
    }

    private const val MAX_PNG = 12 * 1024 * 1024
}
