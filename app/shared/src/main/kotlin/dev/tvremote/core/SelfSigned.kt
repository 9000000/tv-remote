package dev.tvremote.core
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * A certificate for our own RSA key, written out by hand (DER): the Android TV remote protocol only wants one to recognise
 * this phone by, and TV Setup serves its page over TLS with one. Nobody trusts it through a chain: the other side pins
 * its fingerprint (the apps learn it over ADB) or the person compares it with the one on the TV's screen.
 */
object SelfSigned {
    private fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    fun der(tag: Int, vararg parts: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().also { o -> parts.forEach { o.write(it) } }.toByteArray()
        val o = ByteArrayOutputStream()
        o.write(tag)
        if (body.size < 128) o.write(body.size) else { val l = BigInteger.valueOf(body.size.toLong()).toByteArray().dropWhile { it == 0.toByte() }; o.write(0x80 or l.size); o.write(l.toByteArray()) }
        o.write(body)
        return o.toByteArray()
    }

    /** [publicKey] is the key's X.509 encoding (`public.encoded`); the certificate is signed with [key]. */
    fun certificate(publicKey: ByteArray, key: PrivateKey, cn: String = "tv-remote"): ByteArray {
        val alg = der(0x30, hex("06092A864886F70D01010B"), hex("0500"))                 // sha256WithRSAEncryption
        val name = der(0x30, der(0x31, der(0x30, hex("0603550403"), der(0x0C, cn.toByteArray()))))
        val years = der(0x30, der(0x17, "240101000000Z".toByteArray()), der(0x17, "491231235959Z".toByteArray()))
        val serial = der(0x02, BigInteger(63, java.security.SecureRandom()).add(BigInteger.ONE).toByteArray())
        val tbs = der(0x30, der(0xA0, hex("020102")), serial, alg, name, years, name, publicKey)
        val sig = Signature.getInstance("SHA256withRSA").apply { initSign(key); update(tbs) }.sign()
        return der(0x30, tbs, alg, der(0x03, byteArrayOf(0), sig))
    }

    /** SHA-256 of the certificate, 64 lower-case hex digits: what a client pins. */
    fun fingerprint(cert: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { String.format("%02x", it) }

    /** The first 8 bytes in groups of two, "AB12 CD34 EF56 7890": short enough to compare by eye. */
    fun short(fingerprint: String): String = fingerprint.uppercase().take(16).chunked(4).joinToString(" ")

    /** A server-side TLS context holding [pkcs8] (the private key) and [cert] (DER). */
    fun serverContext(pkcs8: ByteArray, cert: ByteArray): SSLContext {
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
        val x509 = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(cert))
        val pass = "tvr".toCharArray()
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        ks.setKeyEntry("tv", key, pass, arrayOf(x509))
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, pass)
        return SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
    }
}
