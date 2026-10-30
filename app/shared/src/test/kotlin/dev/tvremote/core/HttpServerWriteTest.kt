package dev.tvremote.core

import dev.tvremote.core.web.HttpServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A browser that stops reading must not hold a reply's thread and slot for ever; one that reads slowly must not be cut. */
class HttpServerWriteTest {
    private val server = HttpServer { HttpServer.Response.stream("application/octet-stream", -1) { out -> body(out) } }
    private var body: (java.io.OutputStream) -> Unit = { }
    private val saved = HttpServer.writeStallMs
    private val savedFile = HttpServer.fileStallMs

    @After fun after() { server.stop(); HttpServer.writeStallMs = saved; HttpServer.fileStallMs = savedFile }

    private fun get(port: Int): Socket = Socket("127.0.0.1", port).also {
        it.getOutputStream().write("GET /x HTTP/1.1\r\nHost: a\r\n\r\n".toByteArray()); it.getOutputStream().flush()
    }

    @Test fun silentReaderIsCut() {
        HttpServer.writeStallMs = 600
        val ended = CountDownLatch(1)
        var failed = false
        body = { out ->
            try { val b = ByteArray(65536); while (true) out.write(b) }          // far past the socket buffers
            catch (e: java.io.IOException) { failed = true; throw e } finally { ended.countDown() }
        }
        val port = server.start(0, true)
        get(port).use {                                           // connects, asks, never reads
            assertTrue("the stalled write was never ended", ended.await(10, TimeUnit.SECONDS))
        }
        assertTrue(failed)
    }

    @Test fun slowReaderIsKept() {
        HttpServer.writeStallMs = 600
        val total = 3 shl 20
        body = { out -> val b = ByteArray(65536); var n = 0; while (n < total) { out.write(b); n += b.size } }
        val port = server.start(0, true)
        get(port).use { s ->
            val buf = ByteArray(65536)
            var got = 0L
            val i = s.getInputStream()
            while (true) { val n = i.read(buf); if (n < 0) break; got += n; Thread.sleep(10) }   // ~6.5 MB/s, well under the stall
            assertTrue(got >= total)
        }
    }

    /** A file reply (patient) whose reader pauses past the API limit is kept; an API reply paused as long is cut. */
    private fun paused(patient: Boolean): Pair<Boolean, Long> {
        HttpServer.writeStallMs = 500
        HttpServer.fileStallMs = 20_000
        val total = 16L shl 20
        val srv = HttpServer { HttpServer.Response.stream("application/octet-stream", total) { out ->
            val b = ByteArray(65536); var n = 0L; while (n < total) { out.write(b); n += b.size }
        }.also { it.patient = patient } }
        try {
            val port = srv.start(0, true)
            get(port).use { s ->
                Thread.sleep(2000)                                // far past writeStallMs, the socket buffers are full by now
                val buf = ByteArray(65536); var got = 0L
                try { while (true) { val n = s.getInputStream().read(buf); if (n < 0) break; got += n } } catch (e: java.io.IOException) { }
                return (got >= total) to got
            }
        } finally { srv.stop() }
    }

    @Test fun pausedFileReplyIsKept() { assertTrue(paused(true).first) }

    @Test fun pausedApiReplyIsCut() { assertFalse(paused(false).first) }
}
