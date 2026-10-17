package dev.tvremote

import dev.tvremote.core.adb.TlsBackend
import org.conscrypt.Conscrypt
import java.security.Provider
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager

/**
 * TLS 1.3 and exported keying material for adb's Wireless debugging pairing (core/AdbTls.kt). Compiled into the phone app and the
 * computer app only (their build files add this folder), which carry Conscrypt; the core finds this class by its name.
 */
class ConscryptBackend : TlsBackend {
    private val provider: Provider by lazy { Conscrypt.newProvider() }

    override fun context(keys: Array<KeyManager>, trust: Array<TrustManager>): SSLContext =
        SSLContext.getInstance("TLSv1.3", provider).apply { init(keys, trust, null) }

    override fun exportKeyingMaterial(s: SSLSocket, label: String, length: Int): ByteArray =
        Conscrypt.exportKeyingMaterial(s, label, null, length)
}
