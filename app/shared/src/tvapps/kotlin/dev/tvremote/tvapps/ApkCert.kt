package dev.tvremote.tvapps

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * The certificates an APK says it is signed with (APK Signature Scheme v2 / v3 / v3.1 blocks), as SHA-256 hex. TV Apps pins
 * each app to its author's certificate instead of one file's SHA-256, so an author's new release needs no new list.
 * Reading the block does not check the signatures themselves: Android does that at install and refuses an APK whose block
 * does not sign its contents. So every signer of every scheme present must be a pinned one ([allPinned]): an old Android
 * reads v2 only, a new one v3, and a block of either signed by another key must not slip through. v1-only APKs are refused.
 * Plain java (no Android class): :shared, the computer app and TV Apps compile this file.
 */
object ApkCert {
    private const val V2 = 0x7109871a
    private const val V3 = 0xf05368c0.toInt()
    private const val V31 = 0x1b93ad61
    private val MAGIC = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)

    /** Scheme id ("v2" / "v3" / "v3.1") to the SHA-256 of each signer's certificate; empty when the APK has no signing block. */
    @Throws(IOException::class)
    fun read(apk: File): Map<String, List<String>> = RandomAccessFile(apk, "r").use { f ->
        val block = block(f) ?: return emptyMap()
        val out = LinkedHashMap<String, List<String>>()
        while (block.remaining() >= 12) {
            val len = block.long; if (len < 4 || len > block.remaining()) throw IOException("bad signing block")
            val id = block.int
            val value = slice(block, (len - 4).toInt())
            val name = when (id) { V2 -> "v2"; V3 -> "v3"; V31 -> "v3.1"; else -> null } ?: continue
            out[name] = signers(value, id != V2)
        }
        out
    }

    /** True when the APK has a v2+ block and every signer in every scheme it carries is one of [pinned] (SHA-256 hex, any case). */
    fun allPinned(certs: Map<String, List<String>>, pinned: Collection<String>): Boolean {
        val ok = pinned.map { it.lowercase().replace(":", "") }.toSet()
        return certs.isNotEmpty() && certs.values.all { s -> s.isNotEmpty() && s.all { it in ok } }
    }

    // the block sits right before the central directory, which the end-of-central-directory record points to
    private fun block(f: RandomAccessFile): ByteBuffer? {
        val size = f.length()
        val tail = minOf(size, 22L + 65535).toInt()
        val end = ByteArray(tail).also { f.seek(size - tail); f.readFully(it) }
        var eocd = -1
        for (i in tail - 22 downTo 0) if (end[i] == 0x50.toByte() && end[i + 1] == 0x4b.toByte() && end[i + 2] == 5.toByte() && end[i + 3] == 6.toByte()) { eocd = i; break }
        if (eocd < 0) throw IOException("not an APK")
        val cd = ByteBuffer.wrap(end, eocd + 16, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
        if (cd < 32 || cd > size) throw IOException("not an APK")
        val foot = ByteArray(24).also { f.seek(cd - 24); f.readFully(it) }
        if (!foot.copyOfRange(8, 24).contentEquals(MAGIC)) return null
        val len = ByteBuffer.wrap(foot, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long
        if (len < 24 || len > cd - 8 || len > 32L shl 20) throw IOException("bad signing block")
        val body = ByteArray((len - 24).toInt()).also { f.seek(cd - len); f.readFully(it) }   // the pairs: after the leading size, before the footer
        return ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun slice(b: ByteBuffer, n: Int): ByteBuffer {
        if (n < 0 || n > b.remaining()) throw IOException("bad signing block")
        val s = b.slice().order(ByteOrder.LITTLE_ENDIAN); s.limit(n); b.position(b.position() + n); return s
    }
    private fun prefixed(b: ByteBuffer) = slice(b, b.int)

    // signers > signer > signed data > certificates: the first certificate of each signer is its own
    private fun signers(value: ByteBuffer, v3: Boolean): List<String> {
        val all = prefixed(value)
        val out = ArrayList<String>()
        while (all.hasRemaining()) {
            val signer = prefixed(all)
            val data = prefixed(signer)
            prefixed(data)                                                // digests
            val certs = prefixed(data)
            if (!certs.hasRemaining()) throw IOException("signer without a certificate")
            val der = prefixed(certs)
            out.add(sha256(ByteArray(der.remaining()).also { der.get(it) }))
            if (v3) signer.int                                            // v3: minSdk, maxSdk follow; nothing here needs them
        }
        return out
    }

    fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
