package dev.tvremote.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature

/** Ed25519.kt against RFC 8032's own example and the JDK's Ed25519 (what dev/release/Sign.java signs with); SHA256SUMS read back. */
class EdSignTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test fun rfc8032() {                                             // test 1: the empty message
        val key = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        val sig = hex("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b")
        assertTrue(Ed25519.verify(key, ByteArray(0), sig))
        assertFalse(Ed25519.verify(key, byteArrayOf(0), sig))
    }

    @Test fun jdk() {
        repeat(5) { n ->
            val k = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            val raw = k.public.encoded.let { it.copyOfRange(it.size - 32, it.size) }
            val msg = "abc  tv-setup.apk\n".repeat(n + 1).toByteArray()
            val sig = Signature.getInstance("Ed25519").apply { initSign(k.private); update(msg) }.sign()
            assertTrue(Ed25519.verify(raw, msg, sig))
            assertFalse(Ed25519.verify(raw, msg + 1, sig))                       // one more byte in the list
            assertFalse(Ed25519.verify(raw, msg, sig.copyOf().also { it[5] = (it[5] + 1).toByte() }))
            val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public.encoded.let { it.copyOfRange(it.size - 32, it.size) }
            assertFalse(Ed25519.verify(other, msg, sig))
        }
    }

    @Test fun notOurs() {                                             // signed by a key that is not in Release.KEYS
        val k = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val msg = "x".toByteArray()
        val sig = Signature.getInstance("Ed25519").apply { initSign(k.private); update(msg) }.sign()
        assertFalse(Release.signed(msg, java.util.Base64.getEncoder().encodeToString(sig)))
        assertFalse(Release.signed(msg, "not base64 !"))
    }

    @Test fun sums() {
        val a = "a".repeat(64); val b = "B".repeat(64)
        val list = "$a  tv-remote.apk\n$b *tv-setup.apk\nzz  versions.json\n"
        assertEquals(a, Release.sum(list, "tv-remote.apk"))
        assertEquals("b".repeat(64), Release.sum(list, "tv-setup.apk"))
        assertNull(Release.sum(list, "versions.json"))                             // not a SHA-256
        assertNull(Release.sum(list, "TV-Remote-mac.zip"))
    }
}
