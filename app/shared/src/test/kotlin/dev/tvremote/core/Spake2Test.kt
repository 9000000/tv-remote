package dev.tvremote.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class Spake2Test {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private val client = "adb pair client\u0000".toByteArray()
    private val server = "adb pair server\u0000".toByteArray()

    /** Vector from the real adb's BoringSSL (SPAKE2_CTX_new as bob, called under lldb): our alice with a fixed secret meets it. */
    @Test fun boringSslVector() {
        val a = Spake2(true, client, server, ByteArray(64) { 0x11 })
        assertEquals("58ca6e597eb34c8d91f67c8aa6bf5208691779bf3c1b9b198662db0aa8883a16", hex(a.generate("hello".toByteArray())))
        val key = a.process(hex("11b38de5b0b77be8743da9b57b1e136d1085e35b6d14493846ef63ebb134fe34"))
        assertEquals("483a95ad39fb481d13f7c228dcad7be002f12d4aeb52dc929b8878836dc7916854461dc3a323ea4c05b080492c3fe3945a270b50cff622bbc8b05dcbbaa01e5a", hex(key))
    }

    @Test fun bothSidesAgree() {
        val a = Spake2(true, client, server)
        val b = Spake2(false, server, client)
        val ma = a.generate("123456".toByteArray())
        val mb = b.generate("123456".toByteArray())
        assertEquals(32, ma.size)
        assertArrayEquals(a.process(mb), b.process(ma))
    }

    @Test fun differentPasswordsDisagree() {
        val a = Spake2(true, client, server)
        val b = Spake2(false, server, client)
        val ma = a.generate("123456".toByteArray())
        val mb = b.generate("654321".toByteArray())
        assertFalse(a.process(mb).contentEquals(b.process(ma)))
    }

    @Test fun hkdfZeroSalt() {                                       // RFC 5869 test case 3 (no salt, no info), first block
        val okm = AdbTls.hkdf(ByteArray(22) { 0x0b }, ByteArray(0), 32)
        assertEquals("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d", hex(okm))
    }

    @Test fun packetFraming() {
        assertEquals("010100000003090807", hex(AdbTls.packet(1, byteArrayOf(9, 8, 7))))
    }

    @Test fun codes() {
        assertEquals("017243", AdbTls.cleanCode("017 243"))
        assertNull(AdbTls.cleanCode("12345"))
        assertNull(AdbTls.cleanCode("1234567"))
    }

    @Test fun portsAreRemembered() {
        val d = java.nio.file.Files.createTempDirectory("tvr").toFile()
        assertEquals(0, AdbTls.saved(d, "10.0.0.5"))
        AdbTls.save(d, "10.0.0.5", 34343)
        assertEquals(34343, AdbTls.saved(d, "10.0.0.5"))
        AdbTls.save(d, "10.0.0.5", 0)
        assertEquals(0, AdbTls.saved(d, "10.0.0.5"))
        d.deleteRecursively()
    }
}
