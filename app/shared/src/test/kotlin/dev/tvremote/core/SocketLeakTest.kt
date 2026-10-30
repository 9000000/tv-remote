package dev.tvremote.core

import dev.tvremote.core.adb.Adb
import dev.tvremote.core.remote.WebSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

/** What a failed connect or open leaves behind. */
class SocketLeakTest {
    /** A failed send(OPEN) must not leave its stream in the table: nothing will ever end it. */
    @Test fun adbOpenThatFailsToSendLeavesNoStream() {
        val adb = Adb(Files.createTempDirectory("keys").toFile())
        fun field(n: String) = Adb::class.java.getDeclaredField(n).apply { isAccessible = true }
        field("output").set(adb, object : OutputStream() { override fun write(b: Int) { throw IOException("broken pipe") } })
        try { adb.open("shell:echo"); fail("should throw") } catch (e: IOException) { }
        assertEquals(0, (field("streams").get(adb) as ConcurrentHashMap<*, *>).size)
    }

    /** A TV that takes the TCP connection and fails the TLS handshake: the open throws (the plain socket is closed with it). */
    @Test fun secureOpenWithAFailedHandshakeThrowsAndTheServerSeesTheEnd() {
        ServerSocket(0).use { srv ->
            var eof = false
            val t = Thread { srv.accept().use { c -> c.getOutputStream().write("not tls\r\n\r\n".toByteArray()); eof = c.getInputStream().let { i -> while (i.read() >= 0) { }; true } } }
            t.start()
            try { WebSocket.open("127.0.0.1", srv.localPort, "/", true, 2000); fail("should throw") } catch (e: IOException) { }
            t.join(5000)
            assertTrue(eof)
        }
    }
}
