package dev.tvremote.core

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A stand-in for a TV's WebSocket server (LgTest, SamsungTest): plain sockets, text frames, and plain HTTP GETs on the same
 * port (Samsung's /api/v2/). [onText] gets each message with its connection, which knows the path it opened.
 */
class FakeWs(
    private val onText: (FakeWs.Conn, String) -> Unit,
    private val onOpen: (FakeWs.Conn) -> Unit = {},
    private val onHttp: (String) -> String? = { null },
) : Closeable {
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = server.localPort
    val conns = CopyOnWriteArrayList<Conn>()
    val paths = CopyOnWriteArrayList<String>()

    class Conn(private val s: Socket, val path: String) {
        private val out: OutputStream = s.getOutputStream()
        @Synchronized fun send(text: String) {
            val b = text.toByteArray(Charsets.UTF_8)
            val o = ByteArrayOutputStream()
            o.write(0x81)
            when {
                b.size < 126 -> o.write(b.size)
                b.size < 65536 -> { o.write(126); o.write(b.size shr 8); o.write(b.size and 0xff) }
                else -> { o.write(127); for (i in 7 downTo 0) o.write(((b.size.toLong() shr (8 * i)) and 0xff).toInt()) }
            }
            o.write(b)
            try { out.write(o.toByteArray()); out.flush() } catch (ignored: Exception) { }
        }
        fun drop() { try { s.close() } catch (ignored: Exception) { } }
    }

    init {
        Thread({
            while (!server.isClosed) {
                val s = try { server.accept() } catch (e: Exception) { break }
                Thread { serve(s) }.apply { isDaemon = true }.start()
            }
        }, "fake-ws").apply { isDaemon = true }.start()
    }

    private fun serve(s: Socket) {
        try {
            val inp = s.getInputStream()
            val head = StringBuilder()
            while (!head.endsWith("\r\n\r\n")) { val b = inp.read(); if (b < 0) return; head.append(b.toChar()) }
            val path = head.split(" ")[1]
            val key = Regex("(?im)^sec-websocket-key:\\s*(\\S+)").find(head)?.groupValues?.get(1)
            if (key == null) {
                val body = onHttp(path)
                val res = if (body == null) "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    else "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body"
                s.getOutputStream().write(res.toByteArray()); s.getOutputStream().flush(); s.close()
                return
            }
            paths.add(path)
            val accept = B64.encode(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()))
            s.getOutputStream().write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray())
            val c = Conn(s, path)
            conns.add(c)
            onOpen(c)
            while (true) {
                val text = frame(inp) ?: break
                onText(c, text)
            }
        } catch (ignored: Exception) {
        } finally {
            try { s.close() } catch (ignored: Exception) { }
        }
    }

    /** A client frame; an unmasked one ends the connection, as a real TV would. */
    private fun frame(inp: InputStream): String? {
        val b0 = inp.read(); if (b0 < 0) return null
        val b1 = inp.read(); if (b1 < 0) return null
        if (b1 and 0x80 == 0) { unmasked = true; return null }
        var len = (b1 and 0x7f).toLong()
        if (len == 126L) len = ((inp.read() shl 8) or inp.read()).toLong()
        else if (len == 127L) { len = 0; repeat(8) { len = (len shl 8) or inp.read().toLong() } }
        val mask = ByteArray(4) { inp.read().toByte() }
        val body = ByteArray(len.toInt())
        var got = 0
        while (got < body.size) { val n = inp.read(body, got, body.size - got); if (n < 0) return null; got += n }
        for (i in body.indices) body[i] = (body[i].toInt() xor mask[i and 3].toInt()).toByte()
        if (b0 and 0x0f == 0x8) return null
        return String(body, Charsets.UTF_8)
    }
    @Volatile var unmasked = false

    override fun close() { server.close(); for (c in conns) c.drop() }

    companion object {
        /** A port nothing listens on, for the fallback to the next one. */
        fun deadPort(): Int = ServerSocket(0).use { it.localPort }
    }
}
