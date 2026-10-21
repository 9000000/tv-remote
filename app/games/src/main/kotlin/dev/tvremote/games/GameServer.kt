package dev.tvremote.games

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The phones' gamepad: a small HTTP + WebSocket server (plain sockets) that serves assets/pad/ and turns "d A" / "u A" into keys
 * for one player's port. Up to 4 players; a phone keeps its slot for [KEEP_MS] after it drops (same client id), a physical pad
 * is given one by its device id. Every request must carry the app-start token (`?k=`).
 */
class GameServer private constructor(private val ctx: Context) {
    private class Slot {
        var cid: String? = null
        var conn: Conn? = null
        var dev = -1                                       // a physical pad's device id
        var freedAt = 0L
        val held = HashSet<Int>()
        var dirs: Set<String> = emptySet()                 // the d-pad keys the virtual stick holds now (cores without analog)
        @Volatile var ping = -1                            // the phone's own median round trip (ms), -1 = not measured yet
        @Volatile var offset = Long.MIN_VALUE              // the TV's clock minus the phone's (ms), from the ping exchange
    }

    @Volatile var buttons: List<String> = listOf("A", "B", "START", "SELECT")   // the buttons the running system has: the page shows only those
    @Volatile var analog = false                           // true: sticks go to [onAxis]; false: they are turned into D-pad keys (8-way, dead zone .35)
    @Volatile var onAxis: (port: Int, x: Float, y: Float) -> Unit = { _, _, _ -> }
    /** Which pad the phones draw: nes / snes / gba / ps / web (what the TV is running); sent on join and when it changes. */
    @Volatile var system = "nes"; private set
    fun setSystem(id: String) { system = id; send(0, "{\"sys\":\"$id\"}") }
    @Volatile var webMode = false                          // a web game: any pad button is passed on (the game's layout decides which are shown)
    /** The pad page offers "add game" only outside a game, and opens in navigate mode outside one (the phones are told when it changes). */
    @Volatile var inGame = false
        set(v) { val ch = field != v; field = v; if (ch) send(0, "{\"game\":$v}") }
    @Volatile var onKey: (port: Int, action: Int, keyCode: Int) -> Unit = { _, _, _ -> }
    /** Like [onKey] for a phone's press, with the one-way delay the phone's time stamp gives (ms, -1 = unknown). Null = [onKey] alone. */
    @Volatile var onKeyLat: ((port: Int, action: Int, keyCode: Int, latMs: Int) -> Unit)? = null
    @Volatile var onPing: (player: Int, ms: Int) -> Unit = { _, _ -> }   // a phone's ping changed, player 1..4
    fun pingOf(player: Int): Int = if (player in 1..MAX) slots[player - 1].ping else -1
    @Volatile var onPlayers: (List<Int>) -> Unit = {}      // slots (1..4) now in use, any thread
    @Volatile var onAdded: () -> Unit = {}                 // a game or a web link was added
    @Volatile var onText: (player: Int, text: String) -> Unit = { _, _ -> }   // a phone's text field (web games), player 1..4
    private val layouts = arrayOfNulls<String>(MAX)        // per player: the pad layout a web game asked for (JSON), kept for a reconnect
    private var defaultLayout: String? = null
    private val slots = Array(MAX) { Slot() }
    private var server: ServerSocket? = null
    @Volatile var port = 0; private set
    val token: String = TOKEN

    fun start() {
        for (p in PORT until PORT + 20) {
            try { server = ServerSocket(p).also { port = p }; break } catch (e: Exception) { }
        }
        val s = server ?: return Unit.also { Log.w(TAG, "no free port") }
        (ctx as? android.app.Application)?.registerActivityLifecycleCallbacks(tracker)
        Log.i(TAG, "listening ${url()}")
        Thread({ while (!s.isClosed) try { val c = s.accept(); Thread({ serve(c) }, "pad-conn").apply { isDaemon = true }.start() } catch (e: Exception) { } }, "pad-accept")
            .apply { isDaemon = true }.start()
    }

    /**
     * Navigate mode: a phone's remote keys go to the TV Games screen in front. Android lets an app inject keys into itself only through the view tree
     * (Instrumentation needs INJECT_EVENTS), so the key is dispatched to the resumed activity, and a D-pad key nobody used moves the focus like
     * the system would (focusSearch).
     */
    @Volatile private var top: java.lang.ref.WeakReference<android.app.Activity>? = null
    private val tracker = object : android.app.Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(a: android.app.Activity) { top = java.lang.ref.WeakReference(a) }
        override fun onActivityPaused(a: android.app.Activity) { if (top?.get() === a) top = null }
        override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
        override fun onActivityStarted(a: android.app.Activity) {}
        override fun onActivityStopped(a: android.app.Activity) {}
        override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
        override fun onActivityDestroyed(a: android.app.Activity) {}
    }

    fun nav(name: String) {
        if (name == "HOME") {
            ctx.startActivity(android.content.Intent(ctx, LibraryActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP))
            return
        }
        val code = NAV[name] ?: return
        val a = top?.get() ?: return
        if (name == "BACK" && a is LibraryActivity) {                      // Back here would leave TV Games and strand the pad (the TV's own remote still can)
            a.runOnUiThread { android.widget.Toast.makeText(a, a.getString(R.string.in_library), android.widget.Toast.LENGTH_SHORT).show() }
            return
        }
        a.runOnUiThread {
            fun ev(act: Int) = SystemClock.uptimeMillis().let { KeyEvent(it, it, act, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD) }
            if (!a.dispatchKeyEvent(ev(KeyEvent.ACTION_DOWN)) && code in FOCUS) {
                val dir = FOCUS.getValue(code)
                val cur = a.currentFocus ?: a.window.decorView
                (cur.focusSearch(dir) ?: cur).requestFocus(dir)
            }
            a.dispatchKeyEvent(ev(KeyEvent.ACTION_UP))
        }
    }

    fun stop() {
        (ctx as? android.app.Application)?.unregisterActivityLifecycleCallbacks(tracker)
        try { server?.close() } catch (e: Exception) { }
        synchronized(slots) { slots.forEach { it.conn?.close() } }
    }

    /** The link the QR holds: the TV's Wi-Fi address, the port and the token. */
    fun url(): String = "http://${localIp()}:$port/?k=$token"

    /** What the page's own origin looks like from inside the TV: the web games load from here. */
    fun base(): String = "http://127.0.0.1:$port"

    /** A web game asks for a pad layout: player 1..4, or 0 for everyone (and whoever joins later). null / "null" = the usual pad. */
    fun setLayout(player: Int, json: String?) {
        val j = json?.takeIf { it.length <= 2000 && (it.startsWith("{") || it == "null") }?.takeIf { it != "null" }
        synchronized(slots) {
            if (player == 0) { defaultLayout = j; layouts.fill(null) } else if (player in 1..MAX) layouts[player - 1] = j
        }
        send(player, "{\"layout\":${j ?: "null"}}")
    }
    fun clearLayouts() = setLayout(0, null)
    fun say(player: Int, text: String) = send(player, "{\"say\":${org.json.JSONObject.quote(text.take(200))}}")
    fun vibrate(player: Int, ms: Int) = send(player, "{\"vibrate\":${ms.coerceIn(0, 2000)}}")
    private fun send(player: Int, msg: String) {
        val cs = synchronized(slots) { slots.filterIndexed { i, _ -> player == 0 || player == i + 1 }.mapNotNull { it.conn } }
        cs.forEach { it.text(msg) }
    }

    fun addUrl(): String = "http://${localIp()}:$port/add?k=$token"

    // ---- slots ---------------------------------------------------------------------------------------------------------

    private fun reserved(s: Slot, now: Long) = s.conn == null && s.cid != null && now - s.freedAt < KEEP_MS

    /** The slot for a phone: its own again, else the first free one, else one whose keeping time is running. -1 = full. */
    private fun assign(cid: String, c: Conn): Int {
        val changed: Int
        synchronized(slots) {
            val now = SystemClock.uptimeMillis()
            var i = slots.indexOfFirst { it.cid == cid }
            if (i >= 0) { slots[i].conn?.takeIf { it !== c }?.close() }
            else i = slots.indexOfFirst { it.conn == null && it.dev < 0 && !reserved(it, now) }.takeIf { it >= 0 }
                ?: slots.indexOfFirst { it.conn == null && it.dev < 0 }
            if (i < 0) return -1
            slots[i].cid = cid; slots[i].conn = c
            changed = i
        }
        notifyPlayers()
        return changed
    }

    private fun drop(i: Int, c: Conn) {
        synchronized(slots) {
            val s = slots[i]
            if (s.conn !== c) return                       // a reconnect already took the slot over
            s.held.toList().forEach { onKey(i, KeyEvent.ACTION_UP, it) }; s.held.clear()
            s.conn = null; s.freedAt = SystemClock.uptimeMillis(); s.ping = -1; s.offset = Long.MIN_VALUE
        }
        notifyPlayers()
    }

    /** The port of a physical pad (device id), given the first free slot the first time it is seen. -1 = none left. */
    fun padPort(dev: Int): Int {
        var fresh = false
        val r: Int
        synchronized(slots) {
            var i = slots.indexOfFirst { it.dev == dev }
            if (i < 0) {
                val now = SystemClock.uptimeMillis()
                i = slots.indexOfFirst { it.conn == null && it.dev < 0 && !reserved(it, now) }
                if (i >= 0) { slots[i].dev = dev; slots[i].cid = null; fresh = true }
            }
            r = i
        }
        if (fresh) notifyPlayers()
        return r
    }

    /** Free the slots of pads that were unplugged. */
    fun padGone(present: (Int) -> Boolean) {
        var any = false
        synchronized(slots) { slots.forEach { if (it.dev >= 0 && !present(it.dev)) { it.dev = -1; any = true } } }
        if (any) notifyPlayers()
    }

    private fun notifyPlayers() {
        val l = synchronized(slots) { slots.indices.filter { slots[it].conn != null || slots[it].dev >= 0 }.map { it + 1 } }
        onPlayers(l)
    }

    /** A phone's virtual stick, x / y -1..1 (y down). */
    private fun axis(i: Int, x: Float, y: Float) {
        if (analog) { onAxis(i, x.coerceIn(-1f, 1f), y.coerceIn(-1f, 1f)); return }
        val now = dirs(x, y)
        val before = synchronized(slots) { slots[i].dirs.also { slots[i].dirs = now } }
        (before - now).forEach { key(i, false, it) }
        (now - before).forEach { key(i, true, it) }
    }

    /** The press's one-way delay: the TV's clock now minus the phone's time stamp moved onto the TV's clock. -1 = no stamp or no offset yet. */
    private fun delay(i: Int, stamp: Long?): Int {
        val off = slots[i].offset
        if (stamp == null || off == Long.MIN_VALUE) return -1
        return (System.currentTimeMillis() - (stamp + off)).coerceIn(0, 5000).toInt()
    }

    private fun key(i: Int, down: Boolean, name: String, stamp: Long? = null) {
        val code = KEYS[name] ?: return
        if (!webMode && name !in buttons && name !in DPAD && name !in CHOICE) return
        synchronized(slots) { if (down) slots[i].held.add(code) else slots[i].held.remove(code) }
        val t0 = System.nanoTime()
        val act = if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        onKeyLat?.let { it(i, act, code, delay(i, stamp)) } ?: onKey(i, act, code)
        Log.d(TAG, "key p${i + 1} ${if (down) "d" else "u"} $name handled in ${(System.nanoTime() - t0) / 1000} us")
    }

    // ---- HTTP + WebSocket ----------------------------------------------------------------------------------------------

    private inner class Conn(val sock: Socket) {
        val out: OutputStream = sock.getOutputStream()
        fun close() { try { sock.close() } catch (e: Exception) { } }
        fun text(s: String) = frame(1, s.toByteArray())
        @Synchronized fun frame(op: Int, b: ByteArray) {
            try {
                out.write(0x80 or op)
                when { b.size < 126 -> out.write(b.size); b.size < 65536 -> { out.write(126); out.write(b.size shr 8); out.write(b.size) }
                    else -> { out.write(127); for (k in 7 downTo 0) out.write((b.size.toLong() shr (8 * k)).toInt()) } }
                out.write(b); out.flush()
            } catch (e: Exception) { close() }
        }
    }

    private fun serve(sock: Socket) {
        val conn = Conn(sock)
        try {
            sock.tcpNoDelay = true
            val inp = BufferedInputStream(sock.getInputStream())
            val first = line(inp) ?: return
            val h = HashMap<String, String>()
            while (true) { val l = line(inp) ?: return; if (l.isEmpty()) break; val c = l.indexOf(':'); if (c > 0) h[l.substring(0, c).trim().lowercase()] = l.substring(c + 1).trim() }
            val parts = first.split(' ')
            if (parts.size < 2 || (parts[0] != "GET" && parts[0] != "POST")) return reply(conn, 405, "text/plain", "no".toByteArray())
            val path = parts[1].substringBefore('?')
            val q = parts[1].substringAfter('?', "").split('&').associate { it.substringBefore('=') to it.substringAfter('=', "") }
            // the web games' own files (and what they load next to them) go by a cookie set with the first, tokened request
            val cookie = h["cookie"]?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("tvk=") }?.substring(4)
            if (q["k"] != token && !(path.startsWith("/webgames/") && cookie == token)) return reply(conn, 403, "text/plain", "forbidden".toByteArray())
            if (parts[0] == "POST") return when (path) { "/upload" -> upload(conn, inp, h, q); "/weblink" -> weblink(conn, q); else -> reply(conn, 404, "text/plain", "no".toByteArray()) }
            if (path.startsWith("/webgames/")) return webfile(conn, path.removePrefix("/webgames/"), q["k"] == token)
            if (path == "/ws" && h["upgrade"]?.lowercase() == "websocket") return socket(conn, inp, h["sec-websocket-key"] ?: return)
            val name = when (path) { "/", "/index.html" -> "index.html"; "/pad.css" -> "pad.css"; "/pad.js" -> "pad.js"; "/add" -> "add.html"; "/layouts.json" -> "layouts.json"; else -> null }
                ?: return reply(conn, 404, "text/plain", "not found".toByteArray())
            var body = if (name == "layouts.json") Catalog.layouts(ctx).toByteArray() else ctx.assets.open("pad/$name").use { it.readBytes() }   // the catalog's pad overrides
            if (name.endsWith(".html")) body = String(body).replace("__K__", token).toByteArray()
            reply(conn, 200, when { name.endsWith(".css") -> "text/css"; name.endsWith(".js") -> "text/javascript"; name.endsWith(".json") -> "application/json"; else -> "text/html" } + "; charset=utf-8", body)
        } catch (e: Exception) {
        }
    }

    private fun webfile(c: Conn, rel0: String, setCookie: Boolean) {
        var rel = rel0
        if (rel.contains("..") || !Regex("[A-Za-z0-9_./-]*").matches(rel)) return reply(c, 404, "text/plain", "no".toByteArray())
        if (rel.isEmpty() || rel.endsWith("/")) rel += "index.html" else if (!rel.substringAfterLast('/').contains('.')) rel += "/index.html"
        val body = try { ctx.assets.open("webgames/$rel").use { it.readBytes() } } catch (e: Exception) { return reply(c, 404, "text/plain", "no".toByteArray()) }
        val type = when (rel.substringAfterLast('.')) { "html" -> "text/html"; "js" -> "text/javascript"; "css" -> "text/css"; "json" -> "application/json"
            "png" -> "image/png"; "svg" -> "image/svg+xml"; "mp3" -> "audio/mpeg"; "ogg" -> "audio/ogg"; else -> "application/octet-stream" }
        reply(c, 200, "$type; charset=utf-8", body, if (setCookie) "Set-Cookie: tvk=$token; Path=/webgames/\r\n" else "")
    }

    /** A web game link from the /add page: name + http(s) address into files/web.json. */
    private fun weblink(c: Conn, q: Map<String, String>) {
        fun dec(s: String?) = try { java.net.URLDecoder.decode(s ?: "", "UTF-8") } catch (e: Exception) { "" }
        val ok = Systems.addWeb(ctx, dec(q["name"]).trim(), dec(q["url"]).trim())
        reply(c, if (ok) 200 else 400, "text/plain", (if (ok) "ok" else "bad").toByteArray())
        if (ok) onAdded()
    }

    /** A game from the phone: the body (a raw file, Content-Length known) goes to files/roms through a temp file. */
    private fun upload(c: Conn, inp: InputStream, h: Map<String, String>, q: Map<String, String>) {
        val len = h["content-length"]?.toLongOrNull() ?: return reply(c, 411, "text/plain", "length".toByteArray())
        val name = safeName(try { java.net.URLDecoder.decode(q["name"] ?: "", "UTF-8") } catch (e: Exception) { "" })
            ?: return reply(c, 415, "text/plain", "type".toByteArray())
        if (len <= 0 || len > MAX_UPLOAD) return reply(c, 413, "text/plain", "size".toByteArray())
        c.sock.soTimeout = 30_000
        val dir = Systems.romsDir(ctx)
        val tmp = File(dir, ".up-${System.nanoTime()}")
        try {
            tmp.outputStream().use { o -> val buf = ByteArray(64 * 1024); var left = len
                while (left > 0) { val r = inp.read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (r < 0) throw java.io.EOFException(); o.write(buf, 0, r); left -= r } }
            val dst = File(dir, name)
            if (dst.exists()) dst.delete()
            if (!tmp.renameTo(dst)) throw java.io.IOException("rename")
        } catch (e: Exception) { tmp.delete(); return reply(c, 500, "text/plain", "fail".toByteArray()) }
        reply(c, 200, "text/plain", "ok".toByteArray())
        onAdded()
    }

    /** The last path part, only plain characters, an extension a core exists for. null = refuse. */
    private fun safeName(raw: String): String? {
        val base = raw.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^\\p{L}\\p{N} ._()\\[\\]!&'+,-]"), "_").trimStart('.', ' ').take(100)
        val ext = base.substringAfterLast('.', "").lowercase()
        return if (base.length > ext.length + 1 && ext in Systems.uploadExts(ctx)) base else null
    }

    private fun reply(c: Conn, code: Int, type: String, body: ByteArray, extra: String = "") {
        val head = "HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\n${extra}Connection: close\r\n\r\n"
        try { c.out.write(head.toByteArray()); c.out.write(body); c.out.flush() } catch (e: Exception) { }
        c.close()
    }

    private fun socket(c: Conn, inp: InputStream, key: String) {
        val acc = Base64.encodeToString(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()), Base64.NO_WRAP)
        c.out.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $acc\r\n\r\n".toByteArray()); c.out.flush()
        c.sock.soTimeout = 0
        var slot = -1
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
                when (op) {
                    8 -> break
                    9 -> c.frame(10, data)
                    1 -> {
                        val msg = String(data)
                        when {
                            msg.startsWith("hello ") -> {
                                val i = assign(msg.substring(6).trim().take(40), c)
                                if (i < 0) { c.text("{\"full\":true}"); break }
                                slot = i
                                c.text("{\"slot\":${i + 1},\"game\":$inGame,\"btns\":[${buttons.joinToString(",") { "\"$it\"" }}],\"sys\":\"$system\"}")
                                (synchronized(slots) { layouts[i] ?: defaultLayout })?.let { c.text("{\"layout\":$it}") }
                            }
                            msg.startsWith("p ") -> c.text(msg.trim() + " " + System.currentTimeMillis())          // the phone's stamp back + the TV's clock
                            slot >= 0 && msg.startsWith("r ") -> msg.split(' ').let { if (it.size == 3) report(slot, it[1].toIntOrNull(), it[2].toLongOrNull()) }
                            msg.startsWith("n ") -> nav(msg.substring(2).trim())
                            slot >= 0 && msg.startsWith("a ") -> msg.split(' ').let { if (it.size == 3) axis(slot, (it[1].toIntOrNull() ?: 0) / 100f, (it[2].toIntOrNull() ?: 0) / 100f) }
                            slot >= 0 && msg.startsWith("t ") -> onText(slot + 1, msg.substring(2).take(200))
                            slot >= 0 && msg.length >= 3 && (msg[0] == 'd' || msg[0] == 'u') -> msg.substring(2).trim().split(' ').let { key(slot, msg[0] == 'd', it[0], it.getOrNull(1)?.toLongOrNull()) }
                        }
                    }
                }
            }
        } catch (e: Exception) { }
        if (slot >= 0) drop(slot, c)
        c.close()
    }

    /** A phone's own measure: median round trip and clock offset. */
    private fun report(i: Int, ping: Int?, offset: Long?) {
        if (ping == null || ping !in 0..10_000) return
        slots[i].ping = ping; if (offset != null) slots[i].offset = offset
        onPing(i + 1, ping)
    }

    private fun readFully(i: InputStream, b: ByteArray) { var n = 0; while (n < b.size) { val r = i.read(b, n, b.size - n); if (r < 0) throw java.io.EOFException(); n += r } }
    private fun line(i: InputStream): String? {
        val sb = StringBuilder()
        while (true) { val c = i.read(); if (c < 0) return null; if (c == '\n'.code) break; if (c != '\r'.code) sb.append(c.toChar()); if (sb.length > 8192) return null }
        return sb.toString()
    }

    companion object {
        private const val TAG = "GameServer"
        private const val PORT = 8780
        private const val MAX = 4
        private const val MAX_UPLOAD = 64L * 1024 * 1024
        private const val KEEP_MS = 30_000L
        private val DPAD = setOf("UP", "DOWN", "LEFT", "RIGHT")
        /** A stick as D-pad directions: 8-way, dead zone .35. */
        fun dirs(x: Float, y: Float): Set<String> {
            val r = HashSet<String>()
            if (Math.hypot(x.toDouble(), y.toDouble()) < 0.35) return r
            val a = Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble()))     // 0 = right, 90 = down
            if (a > -67.5 && a < 67.5) r.add("RIGHT"); if (a > 112.5 || a < -112.5) r.add("LEFT")
            if (a > 22.5 && a < 157.5) r.add("DOWN"); if (a < -22.5 && a > -157.5) r.add("UP")
            return r
        }
        private val NAV = mapOf("UP" to KeyEvent.KEYCODE_DPAD_UP, "DOWN" to KeyEvent.KEYCODE_DPAD_DOWN, "LEFT" to KeyEvent.KEYCODE_DPAD_LEFT,
            "RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT, "OK" to KeyEvent.KEYCODE_DPAD_CENTER, "BACK" to KeyEvent.KEYCODE_BACK)
        private val FOCUS = mapOf(KeyEvent.KEYCODE_DPAD_UP to View.FOCUS_UP, KeyEvent.KEYCODE_DPAD_DOWN to View.FOCUS_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT to View.FOCUS_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT to View.FOCUS_RIGHT)
        private val CHOICE = setOf("C", "D", "L2", "R2")                   // answer buttons of web games (A and B are the pad's own)
        /** One token per app start: a link from an earlier start no longer works. */
        private val TOKEN: String = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val KEYS = mapOf(
            "UP" to KeyEvent.KEYCODE_DPAD_UP, "DOWN" to KeyEvent.KEYCODE_DPAD_DOWN, "LEFT" to KeyEvent.KEYCODE_DPAD_LEFT, "RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
            "A" to KeyEvent.KEYCODE_BUTTON_A, "B" to KeyEvent.KEYCODE_BUTTON_B, "X" to KeyEvent.KEYCODE_BUTTON_X, "Y" to KeyEvent.KEYCODE_BUTTON_Y,
            "L" to KeyEvent.KEYCODE_BUTTON_L1, "R" to KeyEvent.KEYCODE_BUTTON_R1, "START" to KeyEvent.KEYCODE_BUTTON_START, "SELECT" to KeyEvent.KEYCODE_BUTTON_SELECT,
            "L2" to KeyEvent.KEYCODE_BUTTON_L2, "R2" to KeyEvent.KEYCODE_BUTTON_R2, "C" to KeyEvent.KEYCODE_BUTTON_C, "D" to KeyEvent.KEYCODE_BUTTON_Z,
        )

        private var inst: GameServer? = null
        private var users = 0
        /** One server for the library and the game: started by the first activity, stopped when the last one goes. */
        @Synchronized fun acquire(ctx: Context): GameServer {
            val g = inst ?: GameServer(ctx.applicationContext).also { inst = it; it.start() }
            users++; return g
        }
        @Synchronized fun release() { if (--users <= 0) { inst?.stop(); inst = null; users = 0 } }

        fun localIp(): String {
            val all = try { NetworkInterface.getNetworkInterfaces().toList() } catch (e: Exception) { emptyList() }
            val ips = all.filter { it.isUp && !it.isLoopback }.flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }
            return (ips.firstOrNull { it.hostAddress?.startsWith("192.168.") == true } ?: ips.firstOrNull())?.hostAddress ?: "127.0.0.1"
        }
    }
}
