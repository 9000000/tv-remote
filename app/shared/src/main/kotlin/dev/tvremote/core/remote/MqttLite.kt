package dev.tvremote.core.remote
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.Socket

/**
 * Just enough MQTT 3.1.1 for a Hisense TV's broker ([Vidaa]): connect with a user and password, subscribe, publish at QoS 0,
 * keep alive. Messages arrive on [onMessage] from the reader thread; [onClosed] once when the link ends. [socket] is plain TCP
 * or already TLS (the TV's broker wants its own client certificate, [Vidaa.tls]).
 */
class MqttLite(private val socket: Socket, private val onMessage: (String, ByteArray) -> Unit, private val onClosed: () -> Unit) : Closeable {
    private val input = DataInputStream(socket.getInputStream().buffered())
    private val output: OutputStream = socket.getOutputStream()
    @Volatile private var closed = false
    @Volatile private var lost = false                                // ended by a failed write or ping, not by the owner: onClosed still runs
    @Volatile private var lastSent = 0L
    private var packetId = 1

    /** The broker said no: 4 bad user name or password, 5 not authorized (what a TV says to the wrong kind of login). */
    class Rejected(val code: Int) : IOException("mqtt connection refused (code $code)")

    /** Sends CONNECT and waits for CONNACK; [Rejected] when the broker refuses, then the reader and pinger start. */
    fun connect(clientId: String, user: String, pass: String, keepAlive: Int = 30, timeoutMs: Int = 8000) {
        val body = ByteArrayOutputStream()
        body.write(str("MQTT")); body.write(4)                       // protocol level 4: 3.1.1
        body.write(0xC2)                                              // user name, password, clean session
        body.write(keepAlive shr 8); body.write(keepAlive and 0xff)
        body.write(str(clientId)); body.write(str(user)); body.write(str(pass))
        socket.soTimeout = timeoutMs
        send(0x10, body.toByteArray())
        val (type, ack) = read() ?: throw IOException("the TV closed the connection")
        if (type shr 4 != 2 || ack.size < 2) throw IOException("mqtt: no CONNACK")
        if (ack[1].toInt() != 0) throw Rejected(ack[1].toInt())
        socket.soTimeout = 0
        Thread({ loop() }, "mqtt-read").apply { isDaemon = true }.start()
        Thread({
            while (!closed) {
                try { Thread.sleep(keepAlive * 500L) } catch (e: InterruptedException) { return@Thread }
                if (!closed && System.currentTimeMillis() - lastSent > keepAlive * 500L) try { send(0xC0, ByteArray(0)) } catch (e: IOException) { drop() }
            }
        }, "mqtt-ping").apply { isDaemon = true }.start()
    }

    fun subscribe(vararg topics: String) {
        val body = ByteArrayOutputStream()
        val id = synchronized(this) { packetId++ and 0xffff }
        body.write(id shr 8); body.write(id and 0xff)
        for (t in topics) { body.write(str(t)); body.write(0) }
        send(0x82, body.toByteArray())
    }

    fun publish(topic: String, payload: String = "") {
        val body = ByteArrayOutputStream()
        body.write(str(topic)); body.write(payload.toByteArray(Charsets.UTF_8))
        send(0x30, body.toByteArray())
    }

    private fun loop() {
        try {
            while (!closed) {
                val (head, body) = read() ?: break
                when (head shr 4) {
                    3 -> {                                            // PUBLISH
                        val n = ((body[0].toInt() and 0xff) shl 8) or (body[1].toInt() and 0xff)
                        val topic = String(body, 2, n, Charsets.UTF_8)
                        val qos = (head shr 1) and 3
                        var at = 2 + n
                        if (qos > 0) { send(0x40, byteArrayOf(body[at], body[at + 1])); at += 2 }   // PUBACK, as QoS 1 asks
                        val payload = body.copyOfRange(at, body.size)
                        try { onMessage(topic, payload) } catch (ignored: Exception) { }
                    }
                }
            }
        } catch (ignored: Exception) { }
        val was = !closed || lost
        close()
        if (was) onClosed()
    }

    private fun read(): Pair<Int, ByteArray>? {
        val head = input.read()
        if (head < 0) return null
        var len = 0; var mul = 1
        while (true) {
            val b = input.readUnsignedByte()
            len += (b and 0x7f) * mul
            if (b and 0x80 == 0) break
            mul *= 128
            if (mul > 128 * 128 * 128) throw IOException("mqtt: bad length")
        }
        if (len > 4 shl 20) throw IOException("mqtt: packet too big")
        val body = ByteArray(len)
        input.readFully(body)
        return head to body
    }

    private fun send(head: Int, body: ByteArray) {
        val o = ByteArrayOutputStream()
        o.write(head)
        var n = body.size
        do { var b = n % 128; n /= 128; if (n > 0) b = b or 0x80; o.write(b) } while (n > 0)
        o.write(body)
        synchronized(output) { output.write(o.toByteArray()); output.flush() }
        lastSent = System.currentTimeMillis()
    }

    private fun str(s: String): ByteArray {
        val b = s.toByteArray(Charsets.UTF_8)
        return byteArrayOf((b.size shr 8).toByte(), (b.size and 0xff).toByte()) + b
    }

    /** The link is broken (a write failed): closed, and [onClosed] is told as if the TV had hung up. */
    fun drop() { lost = true; close() }

    override fun close() {
        if (closed) return
        closed = true
        try { synchronized(output) { output.write(byteArrayOf(0xE0.toByte(), 0)); output.flush() } } catch (ignored: Exception) { }
        try { socket.close() } catch (ignored: Exception) { }
    }

    companion object {
        fun dial(host: String, port: Int, ms: Int): Socket = dev.tvremote.core.Lan.connect(host, port, ms).apply { tcpNoDelay = true }
    }
}
