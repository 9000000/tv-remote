package dev.tvremote.karaoke

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

/**
 * The phones' side: a small HTTP + WebSocket server on [PORT] (8790; the next free one if taken), like TV Games' 8780. Every request
 * carries the TV's code (`?k=`, from the QR). GET `/` is the page (assets/pad), `/api/search?q=`, `/api/status`, POST `/api/key` set the API key,
 * `/ws` carries JSON both ways: the phone says `{t:"add"|"remove"|"move"|"skip"|"pause"|"vol"|"replay"|"hello"}`, the TV answers `{t:"state"|"notice"}`.
 */
class KaraokeServer(private val ctx: Context, private val s: Session) {
    @Volatile var port = PORT; private set
    private var sock: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "karaoke-http").apply { isDaemon = true } }
    private val conns = java.util.Collections.synchronizedSet(HashSet<Conn>())

    fun count(): Int = conns.count { it.cid.isNotEmpty() }

    fun start() {
        var ss: ServerSocket? = null
        for (p in PORT until PORT + 10) { try { ss = ServerSocket(p); port = p; break } catch (e: Exception) { } }
        sock = ss ?: return
        pool.execute { try { while (true) { val c = ss.accept(); pool.execute { serve(c) } } } catch (e: Exception) { } }
    }

    fun stop() { try { sock?.close() } catch (e: Exception) { }; synchronized(conns) { conns.toList() }.forEach { it.close() }; pool.shutdownNow() }

    fun broadcast(msg: String) { synchronized(conns) { conns.toList() }.forEach { if (it.cid.isNotEmpty()) it.text(msg) } }

    private inner class Conn(val sock: Socket) {
        val out: OutputStream = sock.getOutputStream()
        @Volatile var cid = ""; @Volatile var name = ""
        private val q = LinkedBlockingQueue<ByteArray>(256)
        @Volatile private var writer: Thread? = null
        fun close() { try { sock.close() } catch (e: Exception) { }; q.offer(END) }
        fun text(m: String) = frame(1, m.toByteArray())
        fun startWriter() {
            writer = Thread({
                try { while (true) { val f = q.take(); if (f === END) break; out.write(f); out.flush() } } catch (e: Exception) { }
                close()
            }, "karaoke-out").apply { isDaemon = true; start() }
        }
        fun frame(op: Int, b: ByteArray) {
            val h = when { b.size < 126 -> byteArrayOf((0x80 or op).toByte(), b.size.toByte())
                b.size < 65536 -> byteArrayOf((0x80 or op).toByte(), 126, (b.size shr 8).toByte(), b.size.toByte())
                else -> ByteArray(10).also { it[0] = (0x80 or op).toByte(); it[1] = 127; for (k in 0 until 8) it[2 + k] = (b.size.toLong() shr (8 * (7 - k))).toByte() } }
            if (writer == null) { try { out.write(h + b); out.flush() } catch (e: Exception) { close() }; return }
            if (!q.offer(h + b)) close()                       // 256 frames behind: that phone is gone
        }
    }

    private fun serve(sk: Socket) {
        val c = Conn(sk)
        try {
            sk.tcpNoDelay = true; sk.soTimeout = 15_000
            val inp = BufferedInputStream(sk.getInputStream())
            val first = line(inp) ?: return
            val h = HashMap<String, String>()
            while (true) { val l = line(inp) ?: return; if (l.isEmpty()) break; if (h.size > 100) return; val i = l.indexOf(':'); if (i > 0) h[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim() }
            val parts = first.split(' ')
            if (parts.size < 2 || (parts[0] != "GET" && parts[0] != "POST")) return reply(c, 405, "text/plain", "no".toByteArray())
            val path = parts[1].substringBefore('?')
            val q = parts[1].substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { it.substringBefore('=') to dec(it.substringAfter('=', "")) }
            if (q["k"] != s.token) return reply(c, 403, "text/plain", "forbidden".toByteArray())
            if (parts[0] == "POST") {
                val n = (h["content-length"]?.toIntOrNull() ?: 0).coerceIn(0, 512)
                val body = ByteArray(n).also { readFully(inp, it) }.toString(Charsets.UTF_8)
                return if (path == "/api/key") { s.apiKey = body; reply(c, 200, JSON, s.statusJson().toString().toByteArray()) } else reply(c, 404, "text/plain", "no".toByteArray())
            }
            when (path) {
                "/ws" -> if (h["upgrade"]?.lowercase() == "websocket") return socket(c, inp, h["sec-websocket-key"] ?: return) else return reply(c, 400, "text/plain", "no".toByteArray())
                "/api/status" -> return reply(c, 200, JSON, s.statusJson().toString().toByteArray())
                "/api/search" -> return reply(c, 200, JSON, s.search(q["q"] ?: "").toString().toByteArray())
            }
            val name = when (path) { "/", "/index.html" -> "index.html"; "/app.css" -> "app.css"; "/app.js" -> "app.js"; "/icon.png" -> "icon.png"; "/manifest.webmanifest" -> "manifest.webmanifest"; else -> null }
                ?: return reply(c, 404, "text/plain", "not found".toByteArray())
            var body = ctx.assets.open("pad/$name").use { it.readBytes() }
            if (name.endsWith(".html") || name.endsWith(".webmanifest")) body = String(body).replace("__K__", s.token).toByteArray()
            reply(c, 200, when { name.endsWith(".css") -> "text/css; charset=utf-8"; name.endsWith(".js") -> "text/javascript; charset=utf-8"; name.endsWith(".png") -> "image/png"
                name.endsWith(".webmanifest") -> "application/manifest+json"; else -> "text/html; charset=utf-8" }, body)
        } catch (e: Exception) {
        } finally { if (c.cid.isEmpty()) c.close() }
    }

    private fun reply(c: Conn, code: Int, type: String, body: ByteArray) {
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        try { c.out.write(head.toByteArray()); c.out.write(body); c.out.flush() } catch (e: Exception) { }
        c.close()
    }

    private fun socket(c: Conn, inp: InputStream, key: String) {
        val acc = Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()), Base64.NO_WRAP)
        c.out.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $acc\r\n\r\n".toByteArray()); c.out.flush(); c.startWriter()
        c.sock.soTimeout = 75_000; c.sock.keepAlive = true        // a phone gone (battery, Wi-Fi) is let go; the page pings every 20 s
        conns.add(c)
        try {
            while (true) {
                val b0 = inp.read(); if (b0 < 0) break
                val op = b0 and 15
                val b1 = inp.read(); if (b1 < 0) break
                var len = (b1 and 127).toLong()
                if (len == 126L) len = ((inp.read() shl 8) or inp.read()).toLong()
                else if (len == 127L) { len = 0; repeat(8) { len = (len shl 8) or inp.read().toLong() } }
                val mask = ByteArray(4); if (b1 and 128 != 0) readFully(inp, mask)
                if (len > 4096) break
                val data = ByteArray(len.toInt()); readFully(inp, data)
                if (b1 and 128 != 0) for (k in data.indices) data[k] = (data[k].toInt() xor mask[k % 4].toInt()).toByte()
                when (op) { 8 -> break; 9 -> c.frame(10, data); 1 -> message(c, String(data)) }
            }
        } catch (e: Exception) { }
        conns.remove(c); c.close(); s.changed()                    // the number of phones changed
    }

    private fun message(c: Conn, raw: String) {
        val m = try { JSONObject(raw) } catch (e: Exception) { return }
        val t = m.optString("t")
        if (t == "hello") {
            c.cid = m.optString("cid").filter { it.isLetterOrDigit() || it == '-' }.take(40).ifEmpty { return }
            c.name = KaraokeQueue.cleanName(m.optString("name"))
            c.text(s.stateJson()); s.changed(); return
        }
        if (c.cid.isEmpty()) return
        when (t) {
            "name" -> c.name = KaraokeQueue.cleanName(m.optString("name"))
            "add" -> {
                val vid = m.optString("vid")
                if (!KaraokeQueue.validId(vid)) return
                val title = SearchParse.unescape(m.optString("title")).replace(Regex("[\\p{Cntrl}]"), " ").take(200).ifEmpty { vid }
                val ok = s.add(Song(vid, title, SearchParse.thumb(vid), m.optString("channel").take(80), m.optString("len").take(12)), c.name, c.cid)
                if (!ok) c.text("{\"t\":\"notice\",\"code\":\"full\",\"title\":\"\"}")
            }
            "remove" -> s.remove(m.optInt("qid"), c.cid)
            "move" -> s.move(m.optInt("qid"), m.optInt("d"), c.cid)
            "skip" -> s.skip()
            "pause" -> s.pause(m.optBoolean("on", true))
            "vol" -> s.volume(m.optInt("d"))
            "replay" -> s.replay()
        }
    }

    private fun dec(s: String) = try { java.net.URLDecoder.decode(s, "UTF-8") } catch (e: Exception) { "" }
    private fun readFully(i: InputStream, b: ByteArray) { var n = 0; while (n < b.size) { val r = i.read(b, n, b.size - n); if (r < 0) throw java.io.EOFException(); n += r } }
    private fun line(i: InputStream): String? {
        val sb = StringBuilder()
        while (true) { val c = i.read(); if (c < 0) return null; if (c == '\n'.code) break; if (c != '\r'.code) sb.append(c.toChar()); if (sb.length > 8192) return null }
        return sb.toString()
    }

    companion object {
        const val PORT = 8790
        private const val JSON = "application/json; charset=utf-8"
        private val END = ByteArray(0)
        fun localIp(): String {
            val all = try { NetworkInterface.getNetworkInterfaces().toList() } catch (e: Exception) { emptyList() }
            val ips = all.filter { it.isUp && !it.isLoopback }.flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }
            return (ips.firstOrNull { it.hostAddress?.startsWith("192.168.") == true } ?: ips.firstOrNull())?.hostAddress ?: "127.0.0.1"
        }
    }
}
