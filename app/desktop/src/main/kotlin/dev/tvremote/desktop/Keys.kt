package dev.tvremote.desktop

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/**
 * This computer's two identities towards its TVs, in the files Adb.kt and Atv.kt read: taken over from the Python server
 * when it has them (~/.android/adbkey, which the TVs already allowed; cert.pem / key.pem in the repo root, which they are
 * paired with), so switching servers asks nothing again on any TV. Made fresh by Adb / Atv when there is nothing to take over.
 */
object Keys {
    fun prepare(dir: File, repo: File) {
        dir.mkdirs()
        try {
            val adb = File(System.getProperty("user.home"), ".android/adbkey")
            if (!File(dir, "adbkey.pk8").exists() && adb.isFile) {
                val der = pem(adb.readText(), "PRIVATE KEY")
                val priv = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der)) as RSAPrivateCrtKey
                val pub = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(priv.modulus, priv.publicExponent))
                File(dir, "adbkey.x509").writeBytes(pub.encoded)
                File(dir, "adbkey.pk8").writeBytes(der)
            }
        } catch (e: Exception) {
            System.err.println("could not take over ~/.android/adbkey (the TVs will ask once): $e")
        }
        try {
            val cert = File(repo, "cert.pem")
            val key = File(repo, "key.pem")
            if (!File(dir, "atv.pk8").exists() && cert.isFile && key.isFile) {
                val k = key.readText()
                val pk8 = if (k.contains("BEGIN RSA PRIVATE KEY")) pkcs8(pem(k, "RSA PRIVATE KEY")) else pem(k, "PRIVATE KEY")
                KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pk8))   // refuses a key it cannot read, before anything is written
                File(dir, "atv.crt").writeBytes(pem(cert.readText(), "CERTIFICATE"))
                File(dir, "atv.pk8").writeBytes(pk8)
            }
        } catch (e: Exception) {
            System.err.println("could not take over cert.pem / key.pem (the TVs will need pairing again): $e")
        }
        for (f in dir.listFiles().orEmpty()) { f.setReadable(false, false); f.setReadable(true, true) }   // like key.pem: this user only
    }

    private fun pem(text: String, label: String): ByteArray {
        val body = text.substringAfter("-----BEGIN $label-----").substringBefore("-----END $label-----")
        require(body.length < text.length) { "no $label" }
        return Base64.getMimeDecoder().decode(body)
    }

    /** A PKCS#1 RSA key (what OpenSSL wrote for the Python server) inside the PKCS#8 envelope Java reads. */
    private fun pkcs8(pkcs1: ByteArray): ByteArray {
        val algorithm = byteArrayOf(0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00)
        return der(0x30, byteArrayOf(0x02, 0x01, 0x00) + algorithm + der(0x04, pkcs1))
    }

    private fun der(tag: Int, body: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(tag)
        val n = body.size
        when {
            n < 0x80 -> o.write(n)
            n < 0x100 -> { o.write(0x81); o.write(n) }
            n < 0x10000 -> { o.write(0x82); o.write(n shr 8); o.write(n and 0xff) }
            else -> { o.write(0x83); o.write(n shr 16); o.write((n shr 8) and 0xff); o.write(n and 0xff) }
        }
        o.write(body)
        return o.toByteArray()
    }
}
