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

    // PKCS#7 files (what META-INF/CERT.RSA holds, `openssl cms -sign -noattr` over a small file) of two self-signed keys, and the SHA-256 of each one's certificate
    private val p7x = java.util.Base64.getDecoder().decode("MIICNgYJKoZIhvcNAQcCoIICJzCCAiMCAQExDTALBglghkgBZQMEAgEwCwYJKoZIhvcNAQcBoIIBcDCCAWwwggEToAMCAQICFEG9tKdymdTg0NYDWJbsd+iXLf6rMAoGCCqGSM49BAMCMAwxCjAIBgNVBAMMAXgwHhcNMjYxMDMwMTI1NzIxWhcNMzYxMDI3MTI1NzIxWjAMMQowCAYDVQQDDAF4MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEnJjN4AzxRHw6gWEVDxaSRtXLc7JE+vPwgiyQEs90kz7TTrcoYylysqvhTw0yBAbJ0x4ZpmXAlqa6OMwAJx2zUKNTMFEwHQYDVR0OBBYEFNMz6k5AiSmHFlMZa61i0pgSSWWfMB8GA1UdIwQYMBaAFNMz6k5AiSmHFlMZa61i0pgSSWWfMA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDRwAwRAIgKAsILwL80sWcM92U43K9p+R0AQmlOi7qhBUNL3quIUoCIGhJHqMBHya+tMZebHjreLZOPCM46js4V+8Ub+mPRo35MYGNMIGKAgEBMCQwDDEKMAgGA1UEAwwBeAIUQb20p3KZ1ODQ1gNYlux36Jct/qswCwYJYIZIAWUDBAIBMAoGCCqGSM49BAMCBEYwRAIgHxwHVPnrtJH9l8BryrMTInjyXVkjt6Ah2PDZHpUnsPcCIHLv5frlEga+i+1r3HaxmoIkoPKpJ3hPmqfwMei70Pau")
    private val p7y = java.util.Base64.getDecoder().decode("MIICNgYJKoZIhvcNAQcCoIICJzCCAiMCAQExDTALBglghkgBZQMEAgEwCwYJKoZIhvcNAQcBoIIBcDCCAWwwggEToAMCAQICFAiWysKb516dpWkHa42cGNr4dbDBMAoGCCqGSM49BAMCMAwxCjAIBgNVBAMMAXkwHhcNMjYxMDMwMTI1NzIxWhcNMzYxMDI3MTI1NzIxWjAMMQowCAYDVQQDDAF5MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEhn3BAOGbnteqEmHyar+lupl5g0LMymprd/kgT6hz3tFfR0W69+ip/lkTLdoA94C2X66z1mmpHGrjyMXNZmt9FKNTMFEwHQYDVR0OBBYEFJLUm5neN6Rzns3wUP8Vty0kggbVMB8GA1UdIwQYMBaAFJLUm5neN6Rzns3wUP8Vty0kggbVMA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDRwAwRAIgZU6FG788TRnsfBytCPmZeVDZKDrNnMDLcl46dVTXLDACIDPqURVhpEzGTNwmWxZn9izmVIs8nmtsOdb0xv+iGnS/MYGNMIGKAgEBMCQwDDEKMAgGA1UEAwwBeQIUCJbKwpvnXp2laQdrjZwY2vh1sMEwCwYJYIZIAWUDBAIBMAoGCCqGSM49BAMCBEYwRAIgE92U0TWmhrU86MZXdcax/tQ0+0PErpfRg8DutjhJ5QcCICThlJxAOZB/6RC9UO3n8UG4uVZeZt++pZ42iDcdNGaQ")
    private val p7xChain = java.util.Base64.getDecoder().decode("MIIDpgYJKoZIhvcNAQcCoIIDlzCCA5MCAQExDTALBglghkgBZQMEAgEwCwYJKoZIhvcNAQcBoIIC4DCCAWwwggEToAMCAQICFAiWysKb516dpWkHa42cGNr4dbDBMAoGCCqGSM49BAMCMAwxCjAIBgNVBAMMAXkwHhcNMjYxMDMwMTI1NzIxWhcNMzYxMDI3MTI1NzIxWjAMMQowCAYDVQQDDAF5MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEhn3BAOGbnteqEmHyar+lupl5g0LMymprd/kgT6hz3tFfR0W69+ip/lkTLdoA94C2X66z1mmpHGrjyMXNZmt9FKNTMFEwHQYDVR0OBBYEFJLUm5neN6Rzns3wUP8Vty0kggbVMB8GA1UdIwQYMBaAFJLUm5neN6Rzns3wUP8Vty0kggbVMA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDRwAwRAIgZU6FG788TRnsfBytCPmZeVDZKDrNnMDLcl46dVTXLDACIDPqURVhpEzGTNwmWxZn9izmVIs8nmtsOdb0xv+iGnS/MIIBbDCCAROgAwIBAgIUQb20p3KZ1ODQ1gNYlux36Jct/qswCgYIKoZIzj0EAwIwDDEKMAgGA1UEAwwBeDAeFw0yNjEwMzAxMjU3MjFaFw0zNjEwMjcxMjU3MjFaMAwxCjAIBgNVBAMMAXgwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAScmM3gDPFEfDqBYRUPFpJG1ctzskT68/CCLJASz3STPtNOtyhjKXKyq+FPDTIEBsnTHhmmZcCWpro4zAAnHbNQo1MwUTAdBgNVHQ4EFgQU0zPqTkCJKYcWUxlrrWLSmBJJZZ8wHwYDVR0jBBgwFoAU0zPqTkCJKYcWUxlrrWLSmBJJZZ8wDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNHADBEAiAoCwgvAvzSxZwz3ZTjcr2n5HQBCaU6LuqEFQ0veq4hSgIgaEkeowEfJr60xl5seOt4tk48IzjqOzhX7xRv6Y9GjfkxgY0wgYoCAQEwJDAMMQowCAYDVQQDDAF4AhRBvbSncpnU4NDWA1iW7Hfoly3+qzALBglghkgBZQMEAgEwCgYIKoZIzj0EAwIERjBEAiBDlpA9upQ0sxhrjra9/+G5s8yZGhFDb9dVLLX0Py6U+AIgcOXbrvHRr8QfO4eFLElycelMpRtIcayFwnp4p8oMz4E=")   // signed by x, y's certificate riding along as a chain
    private val x = "334953e31831804e62d3a91264e55bd8155f5c9865759bf39fbe315fa8ec14d3"
    private val y = "b0c8082a40dd7fa9417d0d11435d34130e4232c56adcb857c5bfcbe1fd6ac5d7"

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

    @Test fun aChainedCertificateIsNotASigner() {                        // only the signer counts: y in the chain must not refuse x's APK
        val c = ApkCert.read(withV1(p7xChain))
        assertEquals(listOf(x), c["v1"])
        assertTrue(ApkCert.allPinned(c, listOf(ApkCert.sha256(a), x)))
    }

    @Test fun aSignatureBlockWithoutItsSfIsNotASigner() {                // a bundled library's META-INF/LIB.DSA (another key) beside the real CERT.RSA + CERT.SF
        val f = apk(listOf(v2a), mapOf("classes.dex" to ByteArray(10), "META-INF/CERT.RSA" to p7x, "META-INF/CERT.SF" to ByteArray(3), "META-INF/LIB.DSA" to p7y))
        val c = ApkCert.read(f)
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
