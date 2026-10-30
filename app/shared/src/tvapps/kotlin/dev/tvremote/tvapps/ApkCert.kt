package dev.tvremote.tvapps

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

/**
 * The certificates an APK says it is signed with (APK Signature Scheme v2 / v3 / v3.1 blocks, and the v1 JAR signature's
 * the .RSA / .DSA / .EC file in META-INF when the APK carries one), as SHA-256 hex. TV Apps pins
 * each app to its author's certificate instead of one file's SHA-256, so an author's new release needs no new list.
 * Reading the block does not check the signatures themselves: Android does that at install and refuses an APK whose block
 * does not sign its contents. So every signer of every scheme present must be a pinned one ([allPinned]): an old Android
 * reads v2 only, a new one v3, and a block of either signed by another key must not slip through. v1 counts too: Android
 * 5-6 (minSdk 21) checks only the JAR signature, so a v1 signer by another key beside a pinned v2 block is refused.
 * v1-only APKs are refused (a v2+ block is required).
 * Plain java (no Android class): :shared, the computer app and TV Apps compile this file.
 */
object ApkCert {
    private const val V2 = 0x7109871a
    private const val V3 = 0xf05368c0.toInt()
    private const val V31 = 0x1b93ad61
    private val MAGIC = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)

    /** Scheme id ("v1" / "v2" / "v3" / "v3.1") to the SHA-256 of each signer's certificate; empty when the APK has no signing block. */
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
        v1(apk)?.let { out["v1"] = it }
        out
    }

    /** True when the APK has a v2+ block and every signer in every scheme it carries (v1 too) is one of [pinned] (SHA-256 hex, any case). */
    fun allPinned(certs: Map<String, List<String>>, pinned: Collection<String>): Boolean {
        val ok = pinned.map { it.lowercase().replace(":", "") }.toSet()
        return certs.keys.any { it != "v1" } && certs.values.all { s -> s.isNotEmpty() && s.all { it in ok } }
    }

    // the JAR signature: the signer's certificate of each META-INF/<name>.RSA|DSA|EC (PKCS#7) that has its <name>.SF; null when the
    // APK has none. A block with no .SF is not a signature (a bundled library's file), and the other certificates in a block are its
    // chain, not signers: Android's verifier ignores both, so they must not refuse an install. An unreadable block throws.
    private fun v1(apk: File): List<String>? = ZipFile(apk).use { z ->
        val names = HashSet<String>()
        for (e in z.entries()) names.add(e.name.uppercase())
        val out = ArrayList<String>()
        for (e in z.entries()) {
            val n = e.name.uppercase()
            if (!n.startsWith("META-INF/") || n.indexOf('/', 9) >= 0 || !(n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC"))) continue
            if ((n.substring(0, n.lastIndexOf('.')) + ".SF") !in names) continue
            if (e.size > 1 shl 20) throw IOException("bad signature file")
            val raw = z.getInputStream(e).use { it.readBytes() }
            val certs = try { CertificateFactory.getInstance("X.509").generateCertificates(raw.inputStream()).filterIsInstance<X509Certificate>() }
                        catch (x: GeneralSecurityException) { throw IOException("bad signature file") }
            if (certs.isEmpty()) throw IOException("signature file without a certificate")
            for ((issuer, serial) in signerIds(raw)) {
                val c = certs.firstOrNull { it.serialNumber == serial && it.issuerX500Principal.encoded.contentEquals(issuer) }
                    ?: throw IOException("signer certificate missing")
                out.add(sha256(c.encoded))
            }
        }
        out.takeIf { it.isNotEmpty() }
    }

    // PKCS#7 ContentInfo > SignedData > signerInfos > SignerInfo > issuerAndSerialNumber: (issuer DER, serial) of each signer
    private fun signerIds(p7: ByteArray): List<Pair<ByteArray, java.math.BigInteger>> {
        try {
            val ci = der(p7, 0, p7.size)                                  // ContentInfo: the content type, then [0] SignedData
            val a0 = children(p7, ci.vs, ci.ve).first { it.tag == 0xA0 }
            val parts = children(p7, a0.vs, a0.ve).first().let { children(p7, it.vs, it.ve) }   // the SignedData SEQUENCE
            val infos = parts.last { it.tag == 0x31 }                       // signerInfos: the last SET (certs and crls are tagged [0] / [1])
            return children(p7, infos.vs, infos.ve).map { si ->
                val f = children(p7, si.vs, si.ve)                          // version, issuerAndSerialNumber, ...
                val ias = children(p7, f[1].vs, f[1].ve)
                ias[0].let { p7.copyOfRange(it.start, it.ve) } to java.math.BigInteger(p7.copyOfRange(ias[1].vs, ias[1].ve))
            }.also { if (it.isEmpty()) throw IOException("signature file without a signer") }
        } catch (e: IOException) { throw e } catch (e: RuntimeException) { throw IOException("bad signature file") }
    }
    private class Tlv(val tag: Int, val start: Int, val vs: Int, val ve: Int)
    private fun der(b: ByteArray, at: Int, end: Int): Tlv {
        if (at + 2 > end) throw IOException("bad signature file")
        val tag = b[at].toInt() and 0xff
        var l = b[at + 1].toInt() and 0xff; var p = at + 2
        if (l >= 0x80) { val n = l and 0x7f; if (n == 0 || n > 3 || p + n > end) throw IOException("bad signature file"); l = 0; repeat(n) { l = l shl 8 or (b[p++].toInt() and 0xff) } }
        if (p + l > end) throw IOException("bad signature file")
        return Tlv(tag, at, p, p + l)
    }
    private fun children(b: ByteArray, from: Int, to: Int): List<Tlv> {
        val out = ArrayList<Tlv>(); var p = from
        while (p < to) { val t = der(b, p, to); out.add(t); p = t.ve }
        return out
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
