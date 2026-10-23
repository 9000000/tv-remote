package dev.tvremote.games

import android.content.Context
import dev.tvremote.core.SelfSigned
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPairGenerator

/**
 * HTTPS for the phone pad, on its own port beside the plain one: a phone's browser hands the gyroscope (devicemotion) only to a
 * secure page (a POCO, Chrome 155: none over http, readings over https), so the pad's Tilt moves it here (the QR stays http:
 * no warning for touch-only players). The key and a self-signed certificate are made once and kept in the app's private files;
 * nothing vouches for them, so the first visit shows the browser's warning (Advanced, then Proceed), then it remembers. Its own
 * port, not the same one sniffed: Android has no SSLSocketFactory.createSocket(socket, consumed bytes, …) to start TLS after the
 * first byte was read.
 */
class PadTls(private val ctx: Context) {
    private var server: ServerSocket? = null
    /** 0 until the key is made and the port open: the QR stays http meanwhile, or for good if TLS fails here. */
    @Volatile var port = 0; private set

    /** Off the main thread: 2048-bit RSA takes a second or more on a slow TV chip. */
    fun start(serve: (Socket) -> Unit) {
        Thread({
            try {
                val k = File(ctx.filesDir, "pad.pk8"); val crt = File(ctx.filesDir, "pad.crt")
                if (!k.exists() || !crt.exists()) {
                    val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
                    crt.writeBytes(SelfSigned.certificate(pair.public.encoded, pair.private, "TV Games"))
                    k.writeBytes(pair.private.encoded)
                }
                val f = SelfSigned.serverContext(k.readBytes(), crt.readBytes()).serverSocketFactory
                val prefs = ctx.getSharedPreferences("games", Context.MODE_PRIVATE)
                val last = prefs.getInt("padTlsPort", 0)              // the same port as last time: the browser's "Proceed" is per host and port
                var s: ServerSocket? = null
                for (p in listOf(last).filter { it in PORT until PORT + 20 } + (PORT until PORT + 20)) {
                    try { s = f.createServerSocket(p).also { port = p }; break } catch (e: Exception) { }
                }
                server = s ?: return@Thread
                prefs.edit().putInt("padTlsPort", port).apply()
                // the handshake happens on the first read, in serve()'s own thread: a slow or refused one holds up nobody else
                while (!s.isClosed) try { val c = s.accept(); Thread({ serve(c) }, "pad-tls-conn").apply { isDaemon = true }.start() } catch (e: Exception) { }
            } catch (e: Exception) { port = 0 }                          // no TLS: the pad works over http as before, without tilt
        }, "pad-tls").apply { isDaemon = true }.start()
    }

    fun stop() { port = 0; try { server?.close() } catch (e: Exception) { } }

    companion object { private const val PORT = 8880 }
}
