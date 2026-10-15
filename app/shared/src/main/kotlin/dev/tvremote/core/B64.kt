package dev.tvremote.core

/**
 * Base64 without android.util or java.util (the first is Android only, the second Android 8+ only, and the core runs on
 * Android 5 phones and on the computer alike). Encodes on one line; decodes skipping line breaks, as `base64` on the TV prints.
 */
object B64 {
    private const val ABC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val INDEX = IntArray(128) { -1 }.also { for (i in ABC.indices) it[ABC[i].code] = i }

    fun encode(b: ByteArray): String {
        val out = StringBuilder((b.size + 2) / 3 * 4)
        var i = 0
        while (i < b.size) {
            val n = (b[i].toInt() and 0xff shl 16) or ((if (i + 1 < b.size) b[i + 1].toInt() and 0xff else 0) shl 8) or (if (i + 2 < b.size) b[i + 2].toInt() and 0xff else 0)
            out.append(ABC[n shr 18 and 63]).append(ABC[n shr 12 and 63])
            out.append(if (i + 1 < b.size) ABC[n shr 6 and 63] else '=').append(if (i + 2 < b.size) ABC[n and 63] else '=')
            i += 3
        }
        return out.toString()
    }

    /** Throws IllegalArgumentException on a character that is not base64, as android.util.Base64 does. */
    fun decode(s: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(s.length * 3 / 4)
        var acc = 0
        var bits = 0
        for (c in s) {
            if (c == '=' ) break
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t') continue
            val v = if (c.code < 128) INDEX[c.code] else -1
            if (v < 0) throw IllegalArgumentException("bad base64")
            acc = (acc shl 6) or v
            bits += 6
            if (bits >= 8) { bits -= 8; out.write(acc shr bits and 0xff) }
        }
        return out.toByteArray()
    }
}
