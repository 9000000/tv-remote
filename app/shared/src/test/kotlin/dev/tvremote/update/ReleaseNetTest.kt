package dev.tvremote.update

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64

/** The update-security path end to end against a local fake GitHub: newer() -> checked() -> download(). Nothing unsigned or altered is ever offered or kept. */
class ReleaseNetTest {
    /** path -> body; [shortBy] makes a body shorter than its Content-Length (a dropped connection). Plain sockets, as SignedListTest. */
    private class Site(val files: MutableMap<String, ByteArray>, val shortBy: Set<String> = emptySet()) {
        private val ss = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val base = "http://127.0.0.1:${ss.localPort}"
        init {
            Thread {
                while (!ss.isClosed) try {
                    ss.accept().use { s ->
                        val path = s.getInputStream().bufferedReader().readLine().split(" ")[1]
                        val b = files[path]
                        val o = s.getOutputStream()
                        val cut = path in shortBy
                        o.write((if (b == null) "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                                 else "HTTP/1.1 200 OK\r\nContent-Length: ${b.size + if (cut) 100 else 0}\r\nConnection: close\r\n\r\n").toByteArray())
                        if (b != null) o.write(b)
                        o.flush()
                    }
                } catch (e: IOException) { }
            }.apply { isDaemon = true }.start()
        }
        fun stop() = ss.close()
    }

    private val apk = ByteArray(5000) { (it * 7).toByte() }
    private val key: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val tmp = File.createTempFile("rel", "dir").also { it.delete(); it.mkdirs() }
    private var site: Site? = null
    private val oldApi = Release.api
    private val oldKeys = Release.testKeys

    private fun hex(k: KeyPair) = k.public.encoded.let { it.copyOfRange(it.size - 32, it.size) }.joinToString("") { "%02x".format(it) }
    private fun sha(b: ByteArray) = Release.hex(MessageDigest.getInstance("SHA-256").digest(b))
    private fun sign(k: KeyPair, list: String) = Base64.getEncoder().encodeToString(
        Signature.getInstance("Ed25519").apply { initSign(k.private); update(list.toByteArray()) }.sign())

    @Before fun keys() { Release.testKeys = listOf(hex(key)) }

    @After fun back() { Release.api = oldApi; Release.testKeys = oldKeys; site?.stop(); tmp.deleteRecursively() }

    /** Starts the site with one release: [apkBody] is served, [sums] is signed by [signer]; [versions] is versions.json's text. */
    private fun release(versions: String? = "{\"tv\":\"0.2.0\"}", sums: String = "${sha(apk)}  tv-setup.apk\nversion tv=0.2.0\n",
                        signer: KeyPair = key, apkBody: ByteArray = apk, withSig: Boolean = true, short: Set<String> = emptySet()) {
        val s = Site(mutableMapOf(), short)
        val a = ArrayList<String>()
        fun asset(n: String, body: ByteArray) { s.files["/dl/$n"] = body; a.add("{\"name\":\"$n\",\"browser_download_url\":\"${s.base}/dl/$n\"}") }
        asset("tv-setup.apk", apkBody)
        if (versions != null) asset("versions.json", versions.toByteArray())
        asset("SHA256SUMS", sums.toByteArray())
        if (withSig) asset("SHA256SUMS.sig", sign(signer, sums).toByteArray())
        s.files["/repos/${Release.REPO}/releases/latest"] = "{\"tag_name\":\"v0.2.0\",\"body\":\"notes\",\"assets\":[${a.joinToString(",")}]}".toByteArray()
        site = s; Release.api = s.base
    }

    @Test fun goodRelease() {
        release()
        val r = Release.newer("0.1.0", "tv", "tv-setup.apk")!!
        assertEquals("0.2.0", r.version)
        val f = File(tmp, "a.apk")
        r.download(f, null)
        assertTrue(f.readBytes().contentEquals(apk))
        assertNull(Release.newer("0.2.0", "tv", "tv-setup.apk"))                 // already there
    }

    @Test fun versionsJsonAheadOfTheSignedLine() {                              // an old signed list under a forged newer versions.json
        release(versions = "{\"tv\":\"9.9.9\"}", sums = "${sha(apk)}  tv-setup.apk\nversion tv=0.1.0\n")
        try { Release.newer("0.1.0", "tv", "tv-setup.apk"); fail() } catch (e: IOException) { assertEquals(Release.UNSIGNED, e.message) }
    }

    @Test fun otherKeyIsUnsigned() {
        release(signer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair())
        try { Release.newer("0.1.0", "tv", "tv-setup.apk"); fail() } catch (e: IOException) { assertEquals(Release.UNSIGNED, e.message) }
    }

    @Test fun bodyThatDiffersFromTheSumIsDeleted() {
        release(apkBody = apk.copyOf().also { it[10] = (it[10] + 1).toByte() })
        val r = Release.newer("0.1.0", "tv", "tv-setup.apk")!!
        val f = File(tmp, "b.apk")
        try { r.download(f, null); fail() } catch (e: IOException) { assertEquals(Release.MISMATCH, e.message) }
        assertFalse(f.exists())
    }

    @Test fun shortBodyIsIncomplete() {
        release(short = setOf("/dl/tv-setup.apk"))
        val r = Release.newer("0.1.0", "tv", "tv-setup.apk")!!
        val f = File(tmp, "c.apk")
        try { r.download(f, null); fail() } catch (e: IOException) { assertNotNull(e.message) }   // "download incomplete" or the JDK's own "Premature EOF"
        assertFalse(f.exists())
    }

    @Test fun noReleaseOrNoSignatureOffersNothing() {
        site = Site(mutableMapOf()); Release.api = site!!.base                    // 404
        assertNull(Release.newer("0.1.0", "tv", "tv-setup.apk"))
        site!!.stop()
        release(withSig = false)
        assertNull(Release.newer("0.1.0", "tv", "tv-setup.apk"))
    }
}
