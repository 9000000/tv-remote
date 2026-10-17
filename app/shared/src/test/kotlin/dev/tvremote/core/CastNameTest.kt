package dev.tvremote.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ServerSocket
import dev.tvremote.core.remote.Ssdp

class CastNameTest {
    private fun serve(body: String, hold: Boolean = false): ServerSocket {
        val ss = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        Thread {
            try {
                ss.accept().use { s ->
                    s.getInputStream().read(ByteArray(512))
                    s.getOutputStream().write(("HTTP/1.1 200 OK\r\n\r\n$body").toByteArray()); s.getOutputStream().flush()
                    if (hold) Thread.sleep(3000)                  // the TV may keep the connection open
                }
            } catch (ignored: Exception) { }
        }.apply { isDaemon = true }.start()
        return ss
    }

    @Test fun readsTheName() { serve("""{"name":"Living room","x":1}""").use { assertEquals("Living room", Ssdp.castName("127.0.0.1", it.localPort)) } }
    @Test fun keepsTheNameWhenTheConnectionStaysOpen() { serve("""{"name":"Den"}""", hold = true).use { assertEquals("Den", Ssdp.castName("127.0.0.1", it.localPort)) } }
    @Test fun noAnswerIsEmpty() { val p = ServerSocket(0).use { it.localPort }; assertEquals("", Ssdp.castName("127.0.0.1", p)) }
}
