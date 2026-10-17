package dev.tvremote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL
import java.security.KeyPairGenerator
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import dev.tvremote.core.web.HttpServer
import dev.tvremote.core.ontv.Setup

class SelfSignedTest {
    @Test fun servesOverTlsAndPinningWorks() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = SelfSigned.certificate(pair.public.encoded, pair.private, "TV Setup")
        val fp = SelfSigned.fingerprint(cert)
        assertEquals(64, fp.length)
        assertTrue(SelfSigned.short(fp).matches(Regex("([0-9A-F]{4} ){3}[0-9A-F]{4}")))
        val server = HttpServer { r -> HttpServer.Response.json(org.json.JSONObject().put("tls", r.tls)) }
        val port = server.start(0, true, SelfSigned.serverContext(pair.private.encoded, cert))
        try {
            // the pin is the whole trust: the right fingerprint is let through (see AdbPrompt.ask), another one is refused
            val c = URL("https://127.0.0.1:$port/").openConnection() as HttpsURLConnection
            c.hostnameVerifier = HostnameVerifier { _, _ -> true }
            val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, a: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, a: String) { assertEquals(fp, SelfSigned.fingerprint(chain[0].encoded)) }
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            }), null)
            c.sslSocketFactory = ctx.socketFactory
            assertTrue(c.inputStream.bufferedReader().readText().contains("\"tls\":true"))
        } finally { server.stop() }
    }
}
