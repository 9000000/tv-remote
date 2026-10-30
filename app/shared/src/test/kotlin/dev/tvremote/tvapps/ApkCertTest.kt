package dev.tvremote.tvapps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkCertTest {
    private fun le(n: Int, size: Int): ByteArray = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply { if (size == 8) putLong(n.toLong()) else putInt(n) }.array()
    private fun pre(b: ByteArray) = le(b.size, 4) + b

    // one signer of a scheme: signed data (digests, certificates), then for v3 min/max sdk, then signatures and key
    private fun signer(cert: ByteArray, v3: Boolean): ByteArray {
        val data = pre(ByteArray(0)) + pre(pre(cert)) + pre(ByteArray(0)) + (if (v3) le(28, 4) + le(99, 4) else ByteArray(0))
        return pre(data) + (if (v3) le(28, 4) + le(99, 4) else ByteArray(0)) + pre(ByteArray(0)) + pre(ByteArray(0))
    }

    /** A real zip of [entries], with the signing block with [pairs] spliced in right before its central directory, as apksigner does. */
    private fun apk(pairs: List<Pair<Int, ByteArray>>, entries: Map<String, ByteArray> = mapOf("classes.dex" to ByteArray(10))): File {
        val body = ByteArrayOutputStream()
        for ((id, v) in pairs) body.write(le(v.size + 4, 8) + le(id, 4) + v)
        val size = body.size() + 8 + 16
        val block = le(size, 8) + body.toByteArray() + le(size, 8) + "APK Sig Block 42".toByteArray()
        val zip = ByteArrayOutputStream().also { o -> ZipOutputStream(o).use { z -> for ((n, d) in entries) { z.putNextEntry(ZipEntry(n)); z.write(d); z.closeEntry() } } }.toByteArray()
        val eocd = zip.size - 22                                         // no zip comment: the end record is the last 22 bytes
        val cd = ByteBuffer.wrap(zip, eocd + 16, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val out = zip.copyOfRange(0, cd) + block + zip.copyOfRange(cd, zip.size)
        ByteBuffer.wrap(out, out.size - 6, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(cd + block.size)
        return File.createTempFile("cert", ".apk").apply { deleteOnExit(); writeBytes(out) }
    }

    // PKCS#7 files (what META-INF/CERT.RSA holds) of two self-signed keys, and the SHA-256 of each one's certificate
    private val p7x = java.util.Base64.getDecoder().decode("MIIBnAYJKoZIhvcNAQcCoIIBjTCCAYkCAQExADALBgkqhkiG9w0BBwGgggFxMIIBbTCCAROgAwIBAgIUaa7i1Ii3ZQcXP8J2L7bkjjpZ8KMwCgYIKoZIzj0EAwIwDDEKMAgGA1UEAwwBeDAeFw0yNjEwMzAxMTMyNDFaFw0zNjEwMjcxMTMyNDFaMAwxCjAIBgNVBAMMAXgwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAS57lV8220Q0wvxF7llztjIbFOAg3VUBtackT8Hupk/qglampZrNI9DmkXsBsFVDR9nYIcht2RcDdzNCsRXFjxvo1MwUTAdBgNVHQ4EFgQUeWIVH4vfTvjF00BWvE293dz4AwgwHwYDVR0jBBgwFoAUeWIVH4vfTvjF00BWvE293dz4AwgwDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNIADBFAiBnfa9a1+KfFXZQsJqkLy3/42RxFUS3UGzjztnpSOKu3QIhAIyaN+HUlZFde6z4sRNzMdXIQuo+f7BsM7bN6U4QGt9iMQA=")
    private val p7y = java.util.Base64.getDecoder().decode("MIIBnAYJKoZIhvcNAQcCoIIBjTCCAYkCAQExADALBgkqhkiG9w0BBwGgggFxMIIBbTCCAROgAwIBAgIUH809MuTH1Pn6dH7P5zIrTSFg+TkwCgYIKoZIzj0EAwIwDDEKMAgGA1UEAwwBeTAeFw0yNjEwMzAxMTMyNDFaFw0zNjEwMjcxMTMyNDFaMAwxCjAIBgNVBAMMAXkwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAR5roNJcWhMQUVYFe9Ij0mVfrmJxDQSlv6aHNt9Z6Q531WX1zujIGYrMNQsP27RXwxmaEwMtBX/LDIrP7K8FYD1o1MwUTAdBgNVHQ4EFgQU4wXTlIRGSK0GIV7NI/VnXGYJHp4wHwYDVR0jBBgwFoAU4wXTlIRGSK0GIV7NI/VnXGYJHp4wDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNIADBFAiEA7BmvSlPJfgS0s+80WdrZV+m1wgx2rnjiO65iVEKt6ksCIC5xdJ/Y7BBBWMDalBxkpdDge8u1dRb7pSHsIl05cRiUMQA=")
    private val x = "2dbc16a52c4343d6a781822aece227a99f4442e7709abeb155213aa7514762e8"
    private val y = "319b28159198c31e6bd4c1f4a23175c4d80e5069d544475c28d805198820f2cf"

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
        val f = File.createTempFile("v1only", ".apk").apply { deleteOnExit(); ZipOutputStream(outputStream()).use { z -> z.putNextEntry(ZipEntry("META-INF/CERT.RSA")); z.write(p7x); z.closeEntry() } }
        val c = ApkCert.read(f)
        assertTrue(c.isEmpty()); assertFalse(ApkCert.allPinned(c, listOf(ApkCert.sha256(a))))
    }

    private val v2a = 0x7109871a to pre(pre(signer(a, false)))
    private fun withV1(p7: ByteArray) = apk(listOf(v2a), mapOf("classes.dex" to ByteArray(10), "META-INF/CERT.RSA" to p7, "META-INF/CERT.SF" to ByteArray(3)))

    @Test fun aV1SignerByAnotherKeyIsRefused() {                         // Android 5-6 reads v1 only: the pinned v2 block must not cover for it
        val c = ApkCert.read(withV1(p7y))
        assertEquals(listOf(y), c["v1"])
        assertFalse(ApkCert.allPinned(c, listOf(ApkCert.sha256(a), x)))
    }

    @Test fun aV1SignerOfTheSameKeyPasses() {
        val c = ApkCert.read(withV1(p7x))
        assertEquals(listOf(x), c["v1"])
        assertTrue(ApkCert.allPinned(c, listOf(ApkCert.sha256(a), x)))
    }

    @Test fun anUnreadableV1FileRefusesTheApk() {
        val f = withV1("garbage".toByteArray())
        val c = try { ApkCert.read(f) } catch (e: java.io.IOException) { emptyMap() }   // what AppList does
        assertFalse(ApkCert.allPinned(c, listOf(ApkCert.sha256(a), x)))
    }

    @Test fun anApkWithoutV1StaysAsToday() {
        val c = ApkCert.read(apk(listOf(v2a)))
        assertFalse(c.containsKey("v1")); assertTrue(ApkCert.allPinned(c, listOf(ApkCert.sha256(a))))
    }

    @Test fun matchesApksignerOnARealApk() {                             // a built APK when there is one: the same digest apksigner prints
        val apk = File("../tvhome/build/tv-home.apk").takeIf { it.isFile } ?: return
        val expect = System.getenv("TVR_CERT_HOME") ?: return
        assertEquals(listOf(expect.lowercase()), ApkCert.read(apk)["v2"])
    }
}
