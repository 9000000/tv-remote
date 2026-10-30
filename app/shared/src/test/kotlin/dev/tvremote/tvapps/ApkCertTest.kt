package dev.tvremote.tvapps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ApkCertTest {
    private fun le(n: Int, size: Int): ByteArray = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply { if (size == 8) putLong(n.toLong()) else putInt(n) }.array()
    private fun pre(b: ByteArray) = le(b.size, 4) + b

    // one signer of a scheme: signed data (digests, certificates), then for v3 min/max sdk, then signatures and key
    private fun signer(cert: ByteArray, v3: Boolean): ByteArray {
        val data = pre(ByteArray(0)) + pre(pre(cert)) + pre(ByteArray(0)) + (if (v3) le(28, 4) + le(99, 4) else ByteArray(0))
        return pre(data) + (if (v3) le(28, 4) + le(99, 4) else ByteArray(0)) + pre(ByteArray(0)) + pre(ByteArray(0))
    }

    /** A zip-shaped file: some bytes, the signing block with [pairs], an empty central directory, the end record pointing at it. */
    private fun apk(pairs: List<Pair<Int, ByteArray>>): File {
        val body = ByteArrayOutputStream()
        for ((id, v) in pairs) body.write(le(v.size + 4, 8) + le(id, 4) + v)
        val size = body.size() + 8 + 16
        val block = le(size, 8) + body.toByteArray() + le(size, 8) + "APK Sig Block 42".toByteArray()
        val head = ByteArray(100)
        val cd = head.size + block.size
        val eocd = byteArrayOf(0x50, 0x4b, 5, 6) + ByteArray(12) + le(cd, 4) + ByteArray(2)
        return File.createTempFile("cert", ".apk").apply { deleteOnExit(); writeBytes(head + block + eocd) }
    }

    private val a = "cert A".toByteArray()
    private val b = "cert B".toByteArray()

    @Test fun readsEachSchemesSigner() {
        val f = apk(listOf(0x7109871a to pre(pre(signer(a, false))), 0xf05368c0.toInt() to pre(pre(signer(a, true)))))
        val c = ApkCert.read(f)
        assertEquals(listOf(ApkCert.sha256(a)), c["v2"]); assertEquals(listOf(ApkCert.sha256(a)), c["v3"])
        assertTrue(ApkCert.allPinned(c, listOf(ApkCert.sha256(a).uppercase())))
    }

    @Test fun aSchemeSignedByAnotherKeyIsRefused() {                     // an old Android reads v2 only: v3 by the author is not enough
        val f = apk(listOf(0x7109871a to pre(pre(signer(b, false))), 0xf05368c0.toInt() to pre(pre(signer(a, true)))))
        assertFalse(ApkCert.allPinned(ApkCert.read(f), listOf(ApkCert.sha256(a))))
    }

    @Test fun noSigningBlockIsRefused() {                                // v1 only (or no signature): nothing to pin
        val head = ByteArray(100)
        val f = File.createTempFile("v1only", ".apk").apply { deleteOnExit(); writeBytes(head + byteArrayOf(0x50, 0x4b, 5, 6) + ByteArray(12) + le(100, 4) + ByteArray(2)) }
        val c = ApkCert.read(f)
        assertTrue(c.isEmpty()); assertFalse(ApkCert.allPinned(c, listOf(ApkCert.sha256(a))))
    }

    @Test fun matchesApksignerOnARealApk() {                             // a built APK when there is one: the same digest apksigner prints
        val apk = File("../tvhome/build/tv-home.apk").takeIf { it.isFile } ?: return
        val expect = System.getenv("TVR_CERT_HOME") ?: return
        assertEquals(listOf(expect.lowercase()), ApkCert.read(apk)["v2"])
    }
}
