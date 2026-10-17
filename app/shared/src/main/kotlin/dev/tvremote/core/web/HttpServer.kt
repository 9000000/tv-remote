package dev.tvremote.core.web
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import dev.tvremote.core.WireLog

/**
 * A very small HTTP server (one request per connection; TLS when start() is given a context) for the pages the apps serve themselves.
 * It only knows how to read a request and write a reply; what the paths mean is up to the Handler.
 */
class HttpServer(private val handler: Handler) {
    class Request {
        var method = ""
        var path = ""
        val query: MutableMap<String, String> = HashMap()
        val headers: MutableMap<String, String> = HashMap()

        /** The request body: read it as a stream (uploads), or call json() for a small JSON body. */
        var body: InputStream = ByteArrayInputStream(ByteArray(0))
        var length = 0L
        var tls = false                                           // arrived over TLS (the secure port)
        var peer = ""                                             // the address it came from
        private var small: ByteArray? = null

        fun q(name: String): String = query[name] ?: ""

        fun json(max: Int = 65536): JSONObject = jsonOrNull(max) ?: JSONObject()

        /** null for a body that is not one JSON object (or too big): a caller that saves what it got must not save "nothing". */
        fun jsonOrNull(max: Int = 65536): JSONObject? {
            try {
                val bytes = small ?: run {
                    if (length > max) return null
                    val b = ByteArray(length.toInt())
                    var off = 0
                    while (off < b.size) {
                        val n = body.read(b, off, b.size - off)
                        if (n < 0) break
                        off += n
                    }
                    small = b
                    b
                }
                return JSONObject(String(bytes, Charsets.UTF_8))
            } catch (e: Exception) {
                return null
            }
        }

        fun cookie(name: String): String {
            for (part in headers["cookie"].toString().split(";")) {
                val kv = part.trim().split("=", limit = 2)
                if (kv.size == 2 && kv[0] == name) return kv[1]
            }
            return ""
        }
    }

    /** Writes a long reply (a file) straight to the browser. */
    fun interface Body {
        @Throws(IOException::class)
        fun write(out: OutputStream)
    }

    class Response {
        var status = 200
        var type = "application/json; charset=utf-8"
        var body = ByteArray(0)
        var stream: Body? = null                                      // instead of body; length < 0 = unknown, sent until the connection closes
        var length = -1L
        val headers: MutableMap<String, String> = HashMap()

        companion object {
            fun json(status: Int, o: JSONObject) = Response().apply { this.status = status; body = bytes(o.toString()) }
            fun json(o: JSONObject) = json(200, o)
            fun ok() = of("application/json; charset=utf-8", bytes("{\"ok\":true}"))
            fun error(status: Int, message: String) = json(status, JSONObject().put("error", message))
            fun of(type: String, body: ByteArray) = Response().apply { this.type = type; this.body = body }
            fun stream(type: String, length: Long, body: Body) = Response().apply { this.type = type; this.length = length; stream = body }
        }
    }

    fun interface Handler {
        @Throws(Exception::class)
        fun handle(r: Request): Response
    }

    private val pool = Executors.newCachedThreadPool()
    // TV Setup listens on the whole network: a host that opens connections without end must not use up the TV's threads and
    // file descriptors (the scrcpy and ADB sockets need them too). A page uses a handful at once; video and sound one each.
    private val slots = Semaphore(48)
    @Volatile private var server: ServerSocket? = null
    @Volatile private var secure = false

    /** localOnly: reachable from this device alone (127.0.0.1); otherwise from every network the device is on. [tls]: serve HTTPS. */
    @Throws(IOException::class)
    fun start(port: Int, localOnly: Boolean, tls: SSLContext? = null): Int {
        val s = if (tls == null) ServerSocket() else tls.serverSocketFactory.createServerSocket().also {
            val ok = setOf("TLSv1.2", "TLSv1.3")                  // nothing older, whatever the platform still offers
            (it as SSLServerSocket).enabledProtocols = it.supportedProtocols.filter { p -> p in ok }.toTypedArray()
        }
        secure = tls != null
        s.reuseAddress = true
        s.bind(if (localOnly) InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), port) else InetSocketAddress(port))
        server = s
        Thread({
            while (!s.isClosed) {
                try {
                    val c = s.accept()
                    if (!slots.tryAcquire()) { try { c.close() } catch (ignored: IOException) { }; continue }
                    pool.execute { try { serve(c) } finally { slots.release() } }
                } catch (e: IOException) {
                    if (s.isClosed) break
                    try { Thread.sleep(100) } catch (ignored: InterruptedException) { }   // out of descriptors: don't spin
                }
            }
        }, "http-accept").apply { isDaemon = true }.start()
        return s.localPort
    }

    fun stop() {
        val s = server
        server = null
        try { s?.close() } catch (ignored: IOException) { }
    }

    fun running(): Boolean = server != null

    private fun serve(c: Socket) {
        try {
            c.use { sock ->
                sock.soTimeout = 10000
                val r = read(sock.getInputStream())?.also { it.tls = secure; it.peer = sock.inetAddress?.hostAddress ?: "" }
                sock.soTimeout = 30000
                val out = if (r == null) Response.error(400, "bad request") else try {
                    handler.handle(r)
                } catch (e: Exception) {
                    Response.error(500, e.message ?: e.javaClass.simpleName)
                }
                if (out.status >= 400 && r != null) WireLog.all.add("·", "api " + r.method + " " + r.path + " " + out.status + " " + String(out.body.take(300).toByteArray(), Charsets.UTF_8))
                val stream = out.stream
                val len = if (stream == null) out.body.size.toLong() else out.length
                val h = StringBuilder("HTTP/1.1 ").append(out.status).append(when (out.status) { 200 -> " OK"; 206 -> " Partial Content"; 302 -> " Found"; else -> " Error" }).append("\r\n")
                    .append("Content-Type: ").append(out.type).append("\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\n")
                if (len >= 0) h.append("Content-Length: ").append(len).append("\r\n")
                for ((k, v) in out.headers) h.append(k).append(": ").append(v).append("\r\n")
                val o = sock.getOutputStream()
                o.write(bytes(h.append("\r\n").toString()))
                if (stream != null) stream.write(o) else o.write(out.body)
                o.flush()
            }
        } catch (ignored: IOException) {
            // the browser went away
        } catch (e: Throwable) {
            // anything else from a reply being written ends this connection only: an uncaught exception would end the app
        }
    }

    companion object {
        /** Request line and headers; the body stays in the stream for the handler. */
        private fun read(input: InputStream): Request? {
            val head = ByteArrayOutputStream()
            val until = System.nanoTime() + 15_000_000_000L               // a head sent a byte at a time doesn't hold a thread
            var state = 0
            while (state < 4) {                                       // until the blank line
                val b = input.read()
                if (b < 0 || head.size() > 16384 || System.nanoTime() > until) return null
                head.write(b)
                state = when {
                    (b == '\r'.code && (state == 0 || state == 2)) || (b == '\n'.code && (state == 1 || state == 3)) -> state + 1
                    b == '\r'.code -> 1
                    else -> 0
                }
            }
            val lines = head.toString("ISO-8859-1").split("\r\n")
            val first = lines[0].split(" ")
            if (first.size < 2) return null
            val r = Request()
            r.method = first[0]
            val target = first[1]
            val q = target.indexOf('?')
            r.path = if (q < 0) target else target.substring(0, q)
            if (q >= 0) for (kv in target.substring(q + 1).split("&")) {
                val p = kv.split("=", limit = 2)
                try {
                    r.query[URLDecoder.decode(p[0], "UTF-8")] = if (p.size > 1) URLDecoder.decode(p[1], "UTF-8") else ""
                } catch (e: IllegalArgumentException) {                // "%zz": a bad request, not a reason to crash
                    return null
                }
            }
            for (line in lines.drop(1)) {
                val colon = line.indexOf(':')
                if (colon > 0) r.headers[line.substring(0, colon).trim().lowercase(Locale.ROOT)] = line.substring(colon + 1).trim()
            }
            r.length = r.headers["content-length"]?.toLongOrNull() ?: 0
            if (r.length < 0) return null
            r.body = input
            return r
        }

        fun bytes(s: String): ByteArray = s.toByteArray(Charsets.UTF_8)
    }
}
