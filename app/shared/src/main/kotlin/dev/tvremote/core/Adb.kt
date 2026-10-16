package dev.tvremote.core

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher

/**
 * A small ADB client over TCP: authenticates with this app's own RSA key and multiplexes several streams (shell
 * commands, sockets on the TV) over the one connection. The first connection makes the TV ask "Allow USB debugging?".
 */
class Adb(private val dir: File) : Closeable {
    fun interface Prompt { fun waitingForUser() }

    private class Msg(val cmd: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    /** One ADB stream. Reads block until data, end of stream or the timeout; writes block until the TV acknowledges. */
    inner class Stream internal constructor(internal val local: Int) : Closeable {
        @Volatile internal var remote = 0
        @Volatile private var closed = false
        internal val incoming = LinkedBlockingQueue<ByteArray>()
        internal val opened = Semaphore(0)
        internal val acked = Semaphore(0)
        private var cur = EMPTY
        private var pos = 0

        /** Reads up to len bytes; -1 at end of stream; 0 if nothing arrived within timeoutMs (0 = wait for ever). */
        @Throws(IOException::class)
        fun read(buf: ByteArray, off: Int, len: Int, timeoutMs: Long): Int {
            if (pos == cur.size) {
                if (cur === EOF) return -1
                try {
                    val next = (if (timeoutMs > 0) incoming.poll(timeoutMs, TimeUnit.MILLISECONDS) else incoming.take()) ?: return 0
                    cur = next
                    pos = 0
                    if (cur === EOF) return -1
                    if (!closed) send(OKAY, local, remote, EMPTY)      // the TV sends its next packet only after this
                } catch (e: InterruptedException) {
                    throw IOException("interrupted")
                }
            }
            val n = minOf(len, cur.size - pos)
            System.arraycopy(cur, pos, buf, off, n)
            pos += n
            return n
        }

        @Throws(IOException::class)
        fun readFully(buf: ByteArray, off: Int, len: Int) {
            var at = off
            var left = len
            while (left > 0) {
                val n = read(buf, at, left, 0)
                if (n < 0) throw EOFException("stream closed")
                at += n
                left -= n
            }
        }

        /** Everything until the end of the stream. With [quietMs], gives up when nothing has come for that long (a command that hangs). */
        @Throws(IOException::class)
        fun readAll(quietMs: Long = 0): String {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = read(buf, 0, buf.size, if (quietMs > 0) quietMs else 0)
                if (n < 0) break
                if (n == 0) { close(); throw IOException("the TV stopped answering") }
                out.write(buf, 0, n)
            }
            return out.toString("UTF-8").replace("\r\n", "\n").trim()
        }

        @Synchronized
        @Throws(IOException::class)
        fun write(data: ByteArray, off: Int, len: Int) {
            var at = off
            var left = len
            while (left > 0) {
                if (closed) throw IOException("stream closed")
                val n = minOf(left, maxData)
                send(WRTE, local, remote, data.copyOfRange(at, at + n))
                try {
                    var waited = 0
                    while (!acked.tryAcquire(1, TimeUnit.SECONDS)) {
                        if (closed) throw IOException("stream closed")
                        if (++waited >= WRITE_WAIT) { close(); throw IOException("the TV stopped taking data") }   // never wait for ever
                    }
                } catch (e: InterruptedException) {
                    throw IOException("interrupted")
                }
                at += n
                left -= n
            }
        }

        @Throws(IOException::class)
        fun write(data: ByteArray) = write(data, 0, data.size)

        override fun close() {
            if (closed) return
            end()
            try { send(CLSE, local, remote, EMPTY) } catch (ignored: IOException) { }
        }

        internal fun end() {
            closed = true
            streams.remove(local)
            incoming.add(EOF)
            opened.release()
        }
    }

    internal val key: KeyPair
    private val streams = ConcurrentHashMap<Int, Stream>()
    private var sock: Socket? = null
    private lateinit var input: DataInputStream
    private lateinit var output: OutputStream
    private var nextId = 1
    private var maxData = 4096
    @Volatile private var dead = false

    init {
        val priv = File(dir, "adbkey.pk8")
        val pub = File(dir, "adbkey.x509")
        val kf = KeyFactory.getInstance("RSA")
        if (priv.exists() && pub.exists()) {
            key = KeyPair(kf.generatePublic(X509EncodedKeySpec(pub.readBytes())), kf.generatePrivate(PKCS8EncodedKeySpec(priv.readBytes())))
        } else {
            key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            priv.writeBytes(key.private.encoded)
            pub.writeBytes(key.public.encoded)
        }
    }

    /** Connects and authenticates. If the TV does not know this app's key yet, waits (3 minutes) for the person to allow it. */
    @Throws(Exception::class)
    fun connect(host: String, port: Int, prompt: Prompt) {
        dial(host, port)
        if (authenticate() != null) {
            send(AUTH, AUTH_RSAPUBLICKEY, 0, (publicKey() + "\u0000").toByteArray())   // unknown key: the TV asks the person
            prompt.waitingForUser()
            awaitApproval(host, port)
        }
        sock!!.soTimeout = 0
        sock!!.keepAlive = true
        Thread(::pump, "adb-reader").apply { isDaemon = true }.start()
    }

    /**
     * Connects to a TV the way [connect] does, but first through the TLS port Wireless debugging gave it (see [AdbTls]) when
     * this TV was paired that way; the port changes whenever Wireless debugging is switched on again, so a refused one is
     * looked for again (mDNS) before giving up on it. A TV never paired this way, or whose TLS port is gone, is dialled on 5555.
     */
    @Throws(Exception::class)
    fun connectTo(host: String, prompt: Prompt) {
        val saved = AdbTls.saved(dir, host)
        if (saved > 0) {
            var failure: Exception? = null
            for (round in 0..1) {
                val port = if (round == 0) saved else AdbTls.discoverConnect(host)
                if (port <= 0 || (round == 1 && port == saved)) continue
                try {
                    connectTls(host, port)
                    if (port != saved) AdbTls.save(dir, host, port)
                    return
                } catch (e: Exception) {
                    if (failure == null) failure = e
                    WireLog.all.add("·", "adb $host tls port $port: $e")
                    close0()
                }
            }
            if (!AdbTls.plainOpen(host)) throw failure ?: IOException("adb tls: no port")   // nothing on 5555 to fall back to: say what the TLS port said
        }
        connect(host, 5555, prompt)
    }

    /** A new socket for the next try: this object is used once, so only the dead one is dropped. */
    private fun close0() { try { sock?.close() } catch (ignored: IOException) { } }

    /**
     * Connects to the port Wireless debugging announces for connecting (Android 11+): plain ADB first, then the device asks
     * for TLS (A_STLS) and both sides switch to TLS 1.3 with our key as the client certificate. The key was trusted at
     * pairing, so there is no "Allow USB debugging?" question; an unpaired key is refused here.
     */
    @Throws(Exception::class)
    fun connectTls(host: String, port: Int) {
        dial(host, port)
        send(CNXN, 0x01000001, 1 shl 18, "host::\u0000".toByteArray())
        var m = read()
        if (m.cmd == STLS) {
            send(STLS, m.arg0, 0, EMPTY)
            val ssl = AdbTls.handshake(this, sock!!, host, port)
            sock = ssl
            input = DataInputStream(ssl.inputStream)
            output = ssl.outputStream
            m = try { read() } catch (quiet: SocketTimeoutException) { send(CNXN, 0x01000001, 1 shl 18, "host::\u0000".toByteArray()); read() }
        }
        if (m.cmd == AUTH) throw IOException("adb tls: this device is not paired")
        if (m.cmd != CNXN) throw IOException("adb tls: unexpected answer")
        maxData = m.arg1.coerceIn(4096, 1 shl 18)
        sock!!.soTimeout = 0
        sock!!.keepAlive = true
        Thread(::pump, "adb-reader").apply { isDaemon = true }.start()
    }

    private fun awaitApproval(host: String, port: Int) {
        var asking = sock!!
        var askingIn = input
        val end = System.currentTimeMillis() + 180000
        var askingAlive = true
        var askAgainAt = 0L
        var asked = 1
        while (System.currentTimeMillis() < end) {
            if (dead) { asking.close(); throw IOException("closed") }   // whoever wanted this connection has let go of it (and the TV's prompt with it)
            if (askingAlive) {
                sock = asking
                input = askingIn
                output = asking.getOutputStream()
                try {
                    asking.soTimeout = 3000
                    while (true) {
                        val m = read()
                        if (m.cmd == CNXN) { maxData = m.arg1.coerceIn(4096, 1 shl 18); return }
                    }
                } catch (waiting: SocketTimeoutException) {
                    // still undecided on that connection
                } catch (closedByTv: IOException) {
                    askingAlive = false
                    askAgainAt = System.currentTimeMillis() + 6000
                }
            } else {
                Thread.sleep(2000)
            }
            // Some TVs never upgrade the connection that asked; once "Always allow" is ticked a fresh one just works.
            try {
                dial(host, port)
                if (authenticate() == null) { asking.close(); return }
                if (!askingAlive && asked < 4 && System.currentTimeMillis() > askAgainAt) {
                    // the question went away on the TV without "Always allow" (Back, Home, Cancel, a timeout): ask it once more
                    send(AUTH, AUTH_RSAPUBLICKEY, 0, (publicKey() + "\u0000").toByteArray())
                    asking.close()
                    asking = sock!!
                    askingIn = input
                    askingAlive = true
                    asked++
                    continue
                }
                sock!!.close()
            } catch (retry: IOException) {
                val s = sock                                          // adbd busy, try again: not with this half-open socket
                if (s != null && s !== asking) try { s.close() } catch (ignored: IOException) { }
            }
        }
        asking.close()
        throw IOException("timeout")
    }

    private fun dial(host: String, port: Int) {
        val s = Socket()
        sock = s
        s.connect(InetSocketAddress(host, port), 6000)
        s.tcpNoDelay = true
        s.soTimeout = 15000
        input = DataInputStream(s.getInputStream())
        output = s.getOutputStream()
    }

    /** Signs the TV's challenge with our key. Returns null when connected, or the next challenge if the key is not trusted yet. */
    private fun authenticate(): ByteArray? {
        send(CNXN, 0x01000000, 1 shl 18, "host::\u0000".toByteArray())
        var signed = false
        while (true) {
            val m = read()
            if (m.cmd == CNXN) {
                maxData = m.arg1.coerceIn(4096, 1 shl 18)
                return null
            }
            if (m.cmd != AUTH || m.arg0 != AUTH_TOKEN) continue
            if (signed) return m.data
            signed = true
            val c = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            c.init(Cipher.ENCRYPT_MODE, key.private)
            c.update(SHA1_PREFIX)
            send(AUTH, AUTH_SIGNATURE, 0, c.doFinal(m.data))
        }
    }

    /** Reader thread: routes every incoming packet to its stream. */
    private fun pump() {
        try {
            while (true) {
                val m = read()
                val s = streams[m.arg1] ?: continue
                when (m.cmd) {
                    OKAY -> if (s.remote == 0) { s.remote = m.arg0; s.opened.release() } else s.acked.release()
                    WRTE -> s.incoming.add(m.data)                    // acknowledged when read, so a slow reader slows the TV down instead of filling memory
                    CLSE -> s.end()
                }
            }
        } catch (e: IOException) {
            if (!dead) WireLog.all.add("·", "adb connection lost: $e")
            dead = true
            for (s in streams.values.toList()) s.end()
        }
    }

    /** Opens an ADB service, e.g. "shell:ls" or "localabstract:name". Throws if the TV refuses it. */
    @Throws(IOException::class)
    fun open(service: String, answerMs: Long = 10000): Stream {
        if (dead) throw IOException("adb connection lost")
        val s = synchronized(this) { Stream(nextId++) }
        streams[s.local] = s
        send(OPEN, s.local, 0, (service + "\u0000").toByteArray())
        try {
            // no answer at all: the connection died quietly (the phone slept, Wi-Fi or VPN changed); drop it so the next call dials again
            if (!s.opened.tryAcquire(answerMs, TimeUnit.MILLISECONDS)) { s.end(); close(); throw IOException("no answer from the TV") }
        } catch (e: InterruptedException) {
            throw IOException("interrupted")
        }
        if (s.remote == 0) throw IOException("refused: $service")     // closed already is fine: a quick command can finish before we look, its output is waiting
        return s
    }

    /** Like shell(), but without a terminal in between, so binary output is not altered (file contents). */
    @Throws(IOException::class)
    fun exec(cmd: String): Stream = open("exec:$cmd")

    /**
     * Writes [size] bytes from [feed] to [path] on the TV, the stream left open until the file holds them all: some TVs (a
     * Sony on Android 9) end cat as the stream closes and lose what it had not written yet, all of a small file. True once whole.
     */
    @Throws(IOException::class)
    fun put(path: String, size: Long, feed: (Stream) -> Unit): Boolean {
        val s = exec("cat > " + q(path))
        try {
            feed(s)
            var got = ""
            var still = 0                                             // checks in a row without the file growing
            while (still < 40) {                                      // 8 s without growing: it will not finish
                val now = shell("stat -c %s " + q(path) + " 2>/dev/null").trim()
                if (now == size.toString()) return true
                still = if (now == got) still + 1 else 0
                got = now
                try { Thread.sleep(200) } catch (e: InterruptedException) { throw IOException("interrupted") }
            }
            return false
        } finally {
            s.close()
        }
    }

    fun put(path: String, data: ByteArray): Boolean = put(path, data.size.toLong()) { it.write(data) }

    /**
     * Runs a command in the TV's shell (as the `shell` user) and returns everything it printed. Throws when it prints nothing
     * for [quietMs] (2 minutes; longer for installs), so no caller waits for ever on a command that hangs.
     */
    @Throws(IOException::class)
    fun shell(cmd: String, quietMs: Long = 120000): String = open("shell:$cmd").use { it.readAll(quietMs) }

    fun isDead(): Boolean = dead

    /** Asks the TV for a trivial answer within [ms]; a connection that died quietly is closed by the asking. */
    fun alive(ms: Long): Boolean = try { open("shell:echo", ms).use { it.readAll(ms) }; !dead } catch (e: IOException) { false }

    override fun close() {
        dead = true
        try { sock?.close() } catch (ignored: IOException) { }
    }

    @Synchronized
    private fun send(cmd: Int, arg0: Int, arg1: Int, data: ByteArray) {
        val sum = data.sumOf { it.toInt() and 0xff }
        val b = ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.size).putInt(sum).putInt(cmd.inv()).put(data)
        output.write(b.array())
        output.flush()
    }

    private fun read(): Msg {
        val head = ByteArray(24)
        input.readFully(head)
        val b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = b.int
        val arg0 = b.int
        val arg1 = b.int
        val len = b.int
        if (len < 0 || len > (1 shl 20)) throw IOException("bad adb packet")
        val data = ByteArray(len)
        input.readFully(data)
        return Msg(cmd, arg0, arg1, data)
    }

    /** What the TV's "Allow USB debugging?" question shows for this key: MD5 of the key, as AB:CD:... */
    fun fingerprint(): String {
        val blob = B64.decode(publicKey().substringBefore(' '))
        return java.security.MessageDigest.getInstance("MD5").digest(blob).joinToString(":") { String.format("%02X", it) }
    }

    /** The public key in adb's own format: little-endian words of n, R^2 mod n and -1/n mod 2^32, base64 encoded. */
    internal fun publicKey(): String {
        val k = key.public as RSAPublicKey
        val n = k.modulus
        val r32 = BigInteger.ONE.shiftLeft(32)
        val r = BigInteger.ONE.shiftLeft(2048)
        val b = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(64).putInt(n.mod(r32).modInverse(r32).negate().mod(r32).toInt())
        putWords(b, n)
        putWords(b, r.multiply(r).mod(n))
        b.putInt(k.publicExponent.toInt())
        return B64.encode(b.array()) + " tvremote@phone"
    }

    companion object {
        /** Seconds a write may wait for the TV to take it. */
        private const val WRITE_WAIT = 30
        private const val CNXN = 0x4e584e43
        private const val AUTH = 0x48545541
        private const val STLS = 0x534c5453
        private const val OPEN = 0x4e45504f
        private const val OKAY = 0x59414b4f
        private const val CLSE = 0x45534c43
        private const val WRTE = 0x45545257
        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_RSAPUBLICKEY = 3
        private val EMPTY = ByteArray(0)
        private val EOF = ByteArray(0)                                // told apart from EMPTY by identity

        /** ASN.1 DigestInfo header for SHA-1: adbd treats the 20-byte token as a SHA-1 digest to be PKCS#1 v1.5 signed. */
        private val SHA1_PREFIX = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)

        /** Quotes a string for the TV's shell. */
        fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

        private fun putWords(b: ByteBuffer, v: BigInteger) {
            for (i in 0 until 64) b.putInt(v.shiftRight(32 * i).toInt())
        }
    }
}
