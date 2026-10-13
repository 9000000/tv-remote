package dev.tvremote.core

import android.graphics.Bitmap
import android.graphics.Color

/**
 * QR codes for short texts (links), drawn here so that no library is needed: byte mode, error correction level L,
 * versions 1 to 5 (up to 106 bytes), which all fit one block of data. A fixed mask is used; readers do not mind.
 */
object Qr {
    private val DATA = intArrayOf(19, 34, 55, 80, 108)                // data codewords per version, level L
    private val EC = intArrayOf(7, 10, 15, 20, 26)                    // error correction codewords

    /** The code as a picture, [scale] pixels per module, with the white margin readers need. null when [text] is too long. */
    fun bitmap(text: String, scale: Int): Bitmap? {
        val m = modules(text) ?: return null
        val quiet = 4
        val n = m.size + quiet * 2
        val px = IntArray(n * scale * n * scale) { Color.WHITE }
        for (y in m.indices) for (x in m.indices) if (m[y][x])
            for (dy in 0 until scale) for (dx in 0 until scale) px[((y + quiet) * scale + dy) * n * scale + (x + quiet) * scale + dx] = Color.BLACK
        return Bitmap.createBitmap(px, n * scale, n * scale, Bitmap.Config.ARGB_8888)
    }

    /** The modules, true = dark, [row][column]. */
    fun modules(text: String): Array<BooleanArray>? {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val v = (1..5).firstOrNull { bytes.size + 2 <= DATA[it - 1] } ?: return null
        val cap = DATA[v - 1]
        // mode (4 bits: bytes), length (8 bits), the bytes, a terminator, then padding to the capacity
        val bits = ArrayList<Boolean>()
        fun put(value: Int, n: Int) { for (i in n - 1 downTo 0) bits.add((value ushr i) and 1 == 1) }
        put(4, 4); put(bytes.size, 8)
        for (b in bytes) put(b.toInt() and 0xff, 8)
        put(0, minOf(4, cap * 8 - bits.size))
        while (bits.size % 8 != 0) bits.add(false)
        var pad = 0xEC
        while (bits.size < cap * 8) { put(pad, 8); pad = if (pad == 0xEC) 0x11 else 0xEC }
        val data = IntArray(cap) { i -> (0 until 8).fold(0) { a, j -> (a shl 1) or (if (bits[i * 8 + j]) 1 else 0) } }
        val all = data + remainder(data, EC[v - 1])

        val size = 17 + 4 * v
        val dark = Array(size) { BooleanArray(size) }
        val fixed = Array(size) { BooleanArray(size) }                // finder, timing, format: not data
        fun set(x: Int, y: Int, on: Boolean) { if (x in 0 until size && y in 0 until size) { dark[y][x] = on; fixed[y][x] = true } }
        for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
        for ((cx, cy) in listOf(3 to 3, size - 4 to 3, 3 to size - 4))    // the three corner squares, with their white border
            for (dy in -4..4) for (dx in -4..4) { val d = maxOf(Math.abs(dx), Math.abs(dy)); set(cx + dx, cy + dy, d != 2 && d != 4) }
        if (v >= 2) { val c = size - 7; for (dy in -2..2) for (dx in -2..2) set(c + dx, c + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1) }
        // format: level L (01) and mask 0, protected by a BCH code
        val f = 1 shl 3
        var rem = f
        for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        val format = ((f shl 10) or rem) xor 0x5412
        fun bit(i: Int) = (format ushr i) and 1 == 1
        for (i in 0..5) set(8, i, bit(i))
        set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
        for (i in 9..14) set(14 - i, 8, bit(i))
        for (i in 0..7) set(size - 1 - i, 8, bit(i))
        for (i in 8..14) set(8, size - 15 + i, bit(i))
        set(8, size - 8, true)
        // the data, in a zigzag from the bottom right, two columns at a time, skipping the fixed modules
        var i = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) for (j in 0..1) {
                val x = right - j
                val y = if ((right + 1) and 2 == 0) size - 1 - vert else vert
                if (!fixed[y][x] && i < all.size * 8) { dark[y][x] = (all[i ushr 3] ushr (7 - (i and 7))) and 1 == 1; i++ }
            }
            right -= 2
        }
        for (y in 0 until size) for (x in 0 until size) if (!fixed[y][x] && (x + y) % 2 == 0) dark[y][x] = !dark[y][x]   // mask 0
        return dark
    }

    /** Reed-Solomon: the [n] error correction codewords for [data], over GF(256) with polynomial 0x11D. */
    private fun remainder(data: IntArray, n: Int): IntArray {
        fun mul(a: Int, b: Int): Int { var z = 0; for (i in 7 downTo 0) { z = (z shl 1) xor ((z ushr 7) * 0x11D); z = z xor (((b ushr i) and 1) * a) }; return z }
        val gen = IntArray(n).also { it[n - 1] = 1 }
        var root = 1
        for (i in 0 until n) {
            for (j in 0 until n) { gen[j] = mul(gen[j], root); if (j + 1 < n) gen[j] = gen[j] xor gen[j + 1] }
            root = mul(root, 2)
        }
        val out = IntArray(n)
        for (b in data) {
            val factor = b xor out[0]
            System.arraycopy(out, 1, out, 0, n - 1)
            out[n - 1] = 0
            for (j in 0 until n) out[j] = out[j] xor mul(gen[j], factor)
        }
        return out
    }
}
