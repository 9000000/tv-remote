package dev.tvremote.core

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.Executors
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Keys only, for a TV whose ADB debugging is still off: the "Android TV Remote" protocol every Android TV answers on
 * ports 6467 (pairing, once, with the code the TV shows) and 6466 (keys and "open this"). It is what the Google TV phone
 * app speaks, and what the computer web uses through androidtvremote2. No picture, no apps, no files: those need ADB.
 */
class Atv(dir: File, private val host: String) : Closeable {
    class NotPaired : Exception("not paired")

    private val key: PrivateKey
    private val cert: X509Certificate
    @Volatile private var sock: SSLSocket? = null
    private val out = Executors.newSingleThreadExecutor()
    @Volatile private var live = false
    @Volatile private var closed = false

    /** What the TV calls itself, known once connected. */
    @Volatile var model = ""
        private set

    init {
        val k = File(dir, "atv.pk8")
        val c = File(dir, "atv.crt")
        if (!k.exists() || !c.exists()) {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            c.writeBytes(selfSigned(pair.public.encoded, pair.private))
            k.writeBytes(pair.private.encoded)
        }
        key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(k.readBytes()))
        cert = CertificateFactory.getInstance("X.509").generateCertificate(c.inputStream()) as X509Certificate
    }

    fun ready(): Boolean = live && !closed

    // ---------------------------------------------------------------- pairing (port 6467)
    /** Asks the TV to show its code. Then [finishPairing] with what the person reads there. */
    fun startPairing() {
        val s = dial(6467)
        sock = s
        fun step(field: Int, body: ByteArray, expect: Int) {
            send(s, Pb().int(1, 2).int(2, 200).msg(field, body).bytes())
            answer(s, expect)
        }
        step(10, Pb().str(1, "atvremote").str(2, "TV Remote").bytes(), 11)
        val hex6 = Pb().int(1, 3).int(2, 6).bytes()                    // the code: 6 hexadecimal characters
        step(20, Pb().msg(1, hex6).int(3, 1).bytes(), 20)
        step(30, Pb().msg(1, hex6).int(2, 1).bytes(), 31)
    }

    /** Throws when the code is wrong; the TV then drops the attempt and [startPairing] has to be run again. */
    fun finishPairing(code: String) {
        val s = sock ?: throw IllegalStateException("startPairing first")
        val c = code.trim().uppercase()
        require(c.length == 6 && c.all { it in "0123456789ABCDEF" }) { "bad code" }
        val mine = cert.publicKey as RSAPublicKey
        val theirs = s.session.peerCertificates[0].publicKey as RSAPublicKey
        val h = MessageDigest.getInstance("SHA-256")
        for (n in listOf(mine.modulus, mine.publicExponent, theirs.modulus, theirs.publicExponent)) h.update(unsigned(n))
        h.update(hex(c.substring(2)))
        val secret = h.digest()
        require(secret[0] == hex(c.substring(0, 2))[0]) { "bad code" }
        send(s, Pb().int(1, 2).int(2, 200).msg(40, Pb().raw(1, secret).bytes()).bytes())
        answer(s, 41)
        close()
    }

    private fun answer(s: SSLSocket, field: Int) {
        val m = Pb.read(frame(s.inputStream) ?: throw java.io.IOException("the TV closed the pairing"))
        if ((m[2]?.firstOrNull() as? Long ?: 0L) != 200L || m[field] == null) throw java.io.IOException("pairing refused")
    }

    // ---------------------------------------------------------------- remote (port 6466)
    @Volatile private var ime = 0L                                    // the TV's counters for its text field, echoed back with the text
    @Volatile private var field = 0L

    /** The app in front has told us about a text field that takes text from the TV's keyboard service; see [text] and [keys]. */
    @Volatile var hasField = false
        private set

    /** The package on the TV's screen, as the TV reports it ("" until it does). */
    @Volatile var app = ""
        private set

    /**
     * Blocks until the TV accepts keys. [NotPaired]: the TV does not know this phone yet. [onClosed] is called once, later, if
     * the connection ends for good: a TV that drops it (some do after a link they cannot open) is simply dialled again.
     */
    fun connect(onClosed: () -> Unit) {
        var s = open() ?: throw NotPaired()
        Thread({
            while (true) {
                try { while (true) handle(s, frame(s.inputStream) ?: break) } catch (ignored: Exception) { }
                live = false
                var again: SSLSocket? = null
                for (i in 0 until 4) {
                    if (closed) break
                    try { Thread.sleep(600) } catch (ignored: InterruptedException) { }
                    again = try { open() } catch (e: Exception) { null }
                    if (again != null) break
                }
                if (again != null && closed) { try { again.close() } catch (ignored: Exception) { }; break }   // closed while dialling
                s = again ?: break
            }
            if (!closed) onClosed()
        }, "atv").apply { isDaemon = true }.start()
    }

    /** Dials and answers the TV's greeting. null when the TV lets us in and then drops us without a word: it does not know this phone. */
    private fun open(): SSLSocket? {
        val s = try { dial(6466) } catch (e: javax.net.ssl.SSLException) { return null }
        sock = s
        s.soTimeout = 8000
        try {
            while (!live) handle(s, frame(s.inputStream) ?: break)
        } catch (slow: java.net.SocketTimeoutException) {             // a weak link is not a reason to ask for a new code
            try { s.close() } catch (ignored: Exception) { }
            throw java.io.IOException("timed out")
        } catch (ignored: Exception) {
        }
        if (!live) { try { s.close() } catch (ignored: Exception) { }; return null }
        s.soTimeout = 0
        return s
    }

    private fun handle(s: SSLSocket, body: ByteArray) {
        val m = Pb.read(body)
        m[1]?.let {                                                   // remote_configure: who the TV is; answer with who we are
            val info = Pb.read(it[0] as ByteArray)[2]?.firstOrNull() as? ByteArray
            if (info != null) model = (Pb.read(info)[1]?.firstOrNull() as? ByteArray)?.toString(Charsets.UTF_8) ?: ""
            val me = Pb().str(1, "TV Remote").str(2, "tv-remote").int(3, 1).str(4, "1").str(5, "atvremote").str(6, "1.0.0").bytes()
            send(s, Pb().msg(1, Pb().int(1, 622).msg(2, me).bytes()).bytes())
        }
        m[2]?.let {                                                   // remote_set_active
            send(s, Pb().msg(2, Pb().int(1, 622).bytes()).bytes())
            live = true
        }
        m[8]?.let {                                                   // ping
            val n = Pb.read(it[0] as ByteArray)[1]?.firstOrNull() as? Long ?: 0L
            send(s, Pb().msg(9, Pb().int(1, n).bytes()).bytes())
        }
        m[21]?.let {                                                  // remote_ime_batch_edit: a text field on the TV has the focus
            val e = Pb.read(it[0] as ByteArray)
            ime = e[1]?.firstOrNull() as? Long ?: 0L
            field = e[2]?.firstOrNull() as? Long ?: 0L
            hasField = true
        }
        m[20]?.let {                                                  // remote_ime_key_inject: sent whenever another app comes to the front
            val info = Pb.read(it[0] as ByteArray)[1]?.firstOrNull() as? ByteArray
            val pkg = info?.let { i -> Pb.read(i)[12]?.firstOrNull() as? ByteArray }?.toString(Charsets.UTF_8)
            if (!pkg.isNullOrEmpty()) { if (pkg != app) hasField = false; app = pkg }
        }
    }

    /** [direction]: [DOWN] and [UP] for a held key, [SHORT] for a tap. */
    fun key(code: Int, direction: Int) = post(Pb().msg(10, Pb().int(1, code.toLong()).int(2, direction.toLong()).bytes()).bytes())

    /**
     * The key held down for [ms], as a finger on a real remote holds it. Apps that move a pointer with the arrows (TV browsers)
     * move it for as long as the key is down, so a key that goes down and up in the same instant moves nothing there.
     */
    fun pulse(code: Int, ms: Long) {
        if (closed || sock == null) return
        val down = Pb().msg(10, Pb().int(1, code.toLong()).int(2, DOWN.toLong()).bytes()).bytes()
        val up = Pb().msg(10, Pb().int(1, code.toLong()).int(2, UP.toLong()).bytes()).bytes()
        out.execute {
            val s = sock
            if (!live || s == null) return@execute
            try {
                send(s, down)
                try { Thread.sleep(ms) } catch (ignored: InterruptedException) { }
                send(s, up)
            } catch (e: Exception) { try { s.close() } catch (ignored: Exception) { } }
        }
    }

    /** Fills the text field that has the focus on the TV with [t] (it replaces what is there). Nothing happens when no field has it. */
    fun text(t: String) {
        if (t.isEmpty()) return
        val at = (t.length - 1).toLong()
        val edit = Pb().int(1, 1).msg(2, Pb().int(1, at).int(2, at).str(3, t).bytes()).bytes()
        post(Pb().msg(21, Pb().int(1, ime).int(2, field).msg(3, edit).bytes()).bytes())
    }

    /**
     * Types [t] as key presses, one per character, the way a keyboard plugged into the TV would: for apps with a keyboard of
     * their own (Downloader), whose fields the TV's keyboard service never hears about. Only what has a key without Shift
     * gets through: capitals arrive in lower case, and characters such as ":" are left out (see [typable]).
     */
    fun keys(t: String) {
        for (c in t) (code(c).takeIf { it != 0 } ?: code(c.lowercaseChar())).let { if (it != 0) key(it, SHORT) }
    }

    /** Opens a link on the TV, or an app by its package name: the app when it is installed, its Play Store page when it is not. */
    fun launch(link: String) {
        val full = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(link)) link else "market://launch?id=$link"
        post(Pb().msg(90, Pb().str(1, full).bytes()).bytes())
    }

    private fun post(message: ByteArray) {
        if (closed || sock == null) return
        out.execute {
            for (i in 0 until 30) { if (live || closed) break; try { Thread.sleep(200) } catch (e: InterruptedException) { return@execute } }   // being dialled again
            val s = sock
            if (!live || s == null) return@execute
            try { send(s, message) } catch (e: Exception) { try { s.close() } catch (ignored: Exception) { } }
        }
    }

    override fun close() {
        closed = true
        out.shutdown()
        val s = sock
        Thread { try { s?.close() } catch (ignored: Exception) { } }.start()   // may be called on the UI thread
    }

    // ---------------------------------------------------------------- plumbing
    private fun dial(port: Int): SSLSocket {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setKeyEntry("k", key, CharArray(0), arrayOf(cert)) }
        val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, CharArray(0)) }
        val anyTv = object : X509TrustManager {                       // the TV's certificate is self-made too; the pairing code is what ties the two together
            override fun checkClientTrusted(c: Array<X509Certificate>, a: String) {}
            override fun checkServerTrusted(c: Array<X509Certificate>, a: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ctx = SSLContext.getInstance("TLS").apply { init(km.keyManagers, arrayOf(anyTv), null) }
        val plain = java.net.Socket()
        plain.connect(java.net.InetSocketAddress(host, port), 5000)
        val s = ctx.socketFactory.createSocket(plain, host, port, true) as SSLSocket
        s.soTimeout = 15000
        s.startHandshake()
        s.soTimeout = 0
        return s
    }

    @Synchronized
    private fun send(s: SSLSocket, body: ByteArray) {
        val o = ByteArrayOutputStream()
        Pb.varint(o, body.size.toLong())
        o.write(body)
        s.outputStream.write(o.toByteArray())
        s.outputStream.flush()
    }

    /** One message: its length, then that many bytes. null at the end of the stream. */
    private fun frame(i: InputStream): ByteArray? {
        var len = 0
        var shift = 0
        while (true) {
            val b = i.read()
            if (b < 0) return null
            len = len or ((b and 0x7f) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        val buf = ByteArray(len)
        var got = 0
        while (got < len) {
            val n = i.read(buf, got, len - got)
            if (n < 0) return null
            got += n
        }
        return buf
    }

    /** Just enough protocol buffers: numbers, strings/bytes and nested messages. */
    private class Pb {
        private val o = ByteArrayOutputStream()
        fun int(field: Int, v: Long): Pb { varint(o, (field shl 3).toLong()); varint(o, v); return this }
        fun raw(field: Int, v: ByteArray): Pb { varint(o, (field shl 3 or 2).toLong()); varint(o, v.size.toLong()); o.write(v); return this }
        fun str(field: Int, v: String) = raw(field, v.toByteArray(Charsets.UTF_8))
        fun msg(field: Int, v: ByteArray) = raw(field, v)
        fun bytes(): ByteArray = o.toByteArray()

        companion object {
            fun varint(o: ByteArrayOutputStream, value: Long) {
                var v = value
                while (v and 0x7fL.inv() != 0L) { o.write(((v and 0x7f) or 0x80).toInt()); v = v ushr 7 }
                o.write(v.toInt())
            }

            /** field number -> its values in order: Long for numbers, ByteArray for everything with a length. */
            fun read(b: ByteArray): Map<Int, List<Any>> {
                val out = HashMap<Int, MutableList<Any>>()
                var p = 0
                fun num(): Long { var v = 0L; var s = 0; while (true) { val x = b[p++].toInt(); v = v or ((x and 0x7f).toLong() shl s); if (x and 0x80 == 0) return v; s += 7 } }
                while (p < b.size) {
                    val tag = num().toInt()
                    val v: Any = when (tag and 7) {
                        0 -> num()
                        2 -> { val n = num().toInt(); b.copyOfRange(p, p + n).also { p += n } }
                        1 -> { p += 8; 0L }
                        5 -> { p += 4; 0L }
                        else -> throw java.io.IOException("bad message")
                    }
                    out.getOrPut(tag ushr 3) { ArrayList() }.add(v)
                }
                return out
            }
        }
    }

    companion object {
        const val DOWN = 1
        const val UP = 2
        const val SHORT = 3

        /** The key that types [c] without Shift, or 0. */
        private fun code(c: Char): Int = when (c) {
            in 'a'..'z' -> 29 + (c - 'a')
            in '0'..'9' -> 7 + (c - '0')
            ' ' -> 62; '.' -> 56; '/' -> 76; '-' -> 69; ',' -> 55; '=' -> 70; ';' -> 74; '\'' -> 75; '@' -> 77; '+' -> 81
            '#' -> 18; '*' -> 17; '[' -> 71; ']' -> 72; '\\' -> 73; '`' -> 68
            else -> 0
        }

        /** True when [keys] types [t] exactly: every character has a key of its own. */
        fun typable(t: String): Boolean = t.all { code(it) != 0 }

        private fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

        /** The number as the TV hashes it: its hexadecimal digits, padded to whole bytes, without a sign byte. */
        private fun unsigned(n: BigInteger): ByteArray = n.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }

        // A certificate for our own key, written out by hand (DER): the TV only wants one to recognise this phone by.
        private fun der(tag: Int, vararg parts: ByteArray): ByteArray {
            val body = ByteArrayOutputStream().also { o -> parts.forEach { o.write(it) } }.toByteArray()
            val o = ByteArrayOutputStream()
            o.write(tag)
            if (body.size < 128) o.write(body.size) else { val l = BigInteger.valueOf(body.size.toLong()).toByteArray().dropWhile { it == 0.toByte() }; o.write(0x80 or l.size); o.write(l.toByteArray()) }
            o.write(body)
            return o.toByteArray()
        }

        private fun selfSigned(publicKey: ByteArray, key: PrivateKey): ByteArray {
            val alg = der(0x30, hex("06092A864886F70D01010B"), hex("0500"))                 // sha256WithRSAEncryption
            val name = der(0x30, der(0x31, der(0x30, hex("0603550403"), der(0x0C, "tv-remote".toByteArray()))))
            val years = der(0x30, der(0x17, "240101000000Z".toByteArray()), der(0x17, "491231235959Z".toByteArray()))
            val serial = der(0x02, BigInteger(63, java.security.SecureRandom()).add(BigInteger.ONE).toByteArray())
            val tbs = der(0x30, der(0xA0, hex("020102")), serial, alg, name, years, name, publicKey)
            val sig = Signature.getInstance("SHA256withRSA").apply { initSign(key); update(tbs) }.sign()
            return der(0x30, tbs, alg, der(0x03, byteArrayOf(0), sig))
        }
    }
}
