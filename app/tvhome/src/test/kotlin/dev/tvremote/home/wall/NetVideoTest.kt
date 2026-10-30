package dev.tvremote.home.wall

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger

class NetVideoTest {
    private val hits = AtomicInteger()
    private lateinit var server: ServerSocket
    private lateinit var dir: File
    private fun url(p: String) = "http://127.0.0.1:${server.localPort}/$p"

    @Before fun up() {
        NetVideo.forgetRefused()
        dir = java.nio.file.Files.createTempDirectory("netvideo").toFile()
        server = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        Thread {                                                  // a bare HTTP server: the Android classpath has no com.sun one
            try { while (true) server.accept().use { s ->
                val path = s.getInputStream().bufferedReader().readLine().split(" ")[1]; hits.incrementAndGet()
                val o = s.getOutputStream()
                when (path) {
                    "/big" -> o.write("HTTP/1.1 200 OK\r\nContent-Length: ${200L * 1024 * 1024}\r\n\r\n".toByteArray())   // says 200 MB, sends none
                    "/gone" -> o.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    else -> { o.write("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\n".toByteArray()); o.write(ByteArray(1000)) }
                }
                o.flush()
            } } catch (e: Exception) { }
        }.apply { isDaemon = true }.start()
    }

    @After fun down() { server.close(); dir.deleteRecursively() }

    @Test fun `a video too big is asked for once`() {
        repeat(5) { assertNull(NetVideo.load(dir, url("big"), Long.MAX_VALUE)) }
        assertEquals(1, hits.get())
    }

    @Test fun `a refused answer is asked for once`() {
        repeat(5) { assertNull(NetVideo.load(dir, url("gone"), Long.MAX_VALUE)) }
        assertEquals(1, hits.get())
    }

    @Test fun `no room is asked for once`() {
        repeat(3) { assertNull(NetVideo.load(dir, url("ok"), 10L)) }   // the size comes with the answer, so the first request is the only one
        assertEquals(1, hits.get())
    }

    @Test fun `a good one is kept`() {
        assertNotNull(NetVideo.load(dir, url("ok"), Long.MAX_VALUE))
    }
}
