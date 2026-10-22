package dev.tvremote.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * "Your photo from a phone": a tiny HTTP server on the LAN, alive only while the QR screen is open (and at most [LIFETIME_MS]).
 * It serves one page (assets/photo.html + photofit.js, the page's own crop code) and takes one JPEG, which goes where the clients'
 * photos go ([Walls.file]), so TV Setup's / the phone's "Upload your photo" and this one stay interchangeable. No ADB, no TV Setup.
 * A photo the phone's browser cannot open (HEIC, too many pixels) comes as it is (?raw=1): [fromRaw] opens it and cuts the middle.
 *
 * Safety: every request needs the random token that is in the QR's address (403 otherwise), the body is capped at [MAX_BODY], and the
 * token dies with the server. The page and the photo are plain HTTP: nothing secret passes, and the token only lets a phone on the
 * same Wi-Fi set a wallpaper for the next few minutes.
 */
object PhotoServer {
    const val MAX_BODY = 4 * 1024 * 1024                               // a 1920x1080 JPEG at 0.85 is 0.3-1.2 MB; a bigger body is not ours
    const val MAX_RAW = 40 * 1024 * 1024                               // a phone's own photo, sent as it is (?raw=1): a 200 MP JPEG is ~30 MB
    const val LIFETIME_MS = 10 * 60 * 1000L
    private const val PORT = 8790

    class Session(val url: String, val shown: String, val token: String)

    private var sock: ServerSocket? = null
    private var token = ""
    private var pool = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    private var stopper: Runnable? = null

    /** Starts it (again with a new token) and says where it is; null when there is no LAN address to be reached on. [onSet] runs on the main thread after a photo is saved. */
    @Synchronized fun start(c: Context, onSet: () -> Unit): Session? {
        stop()
        val ip = lan(c) ?: return null
        val s = try { ServerSocket(PORT) } catch (e: Exception) { try { ServerSocket(0) } catch (e2: Exception) { return null } }
        val t = newToken()
        sock = s; token = t; pool = Executors.newCachedThreadPool()
        val app = c.applicationContext
        pool.execute { accept(app, s, t, onSet) }
        stopper = Runnable { stop() }.also { main.postDelayed(it, LIFETIME_MS) }
        return Session("http://$ip:${s.localPort}/?k=$t", "$ip:${s.localPort}", t)
    }

    @Synchronized fun stop() {
        stopper?.let { main.removeCallbacks(it) }; stopper = null
        try { sock?.close() } catch (e: Exception) { }
        sock = null; token = ""
        pool.shutdownNow()
    }

    fun running() = sock != null

    private fun newToken(): String {
        val a = "abcdefghjkmnpqrstuvwxyz23456789"                      // no 0/o/1/l/i: it is also read aloud or typed
        val r = SecureRandom()
        return String(CharArray(10) { a[r.nextInt(a.length)] })
    }

    private fun accept(c: Context, s: ServerSocket, t: String, onSet: () -> Unit) {
        while (!s.isClosed) {
            val k = try { s.accept() } catch (e: Exception) { return }
            try { pool.execute { handle(c, k, t, onSet) } } catch (e: Exception) { try { k.close() } catch (e2: Exception) { } }
        }
    }

    private class Req(val method: String, val path: String, val query: Map<String, String>, val lang: String, val length: Int, val body: InputStream)

    private fun handle(c: Context, k: Socket, t: String, onSet: () -> Unit) = k.use {
        try {
            k.soTimeout = 15_000
            val inp = java.io.BufferedInputStream(k.getInputStream())
            val out = k.getOutputStream()
            val req = parse(inp) ?: return@use reply(out, 400, "text/plain", "bad request")
            // constant time, and before anything is read or sent: a wrong or missing token learns nothing
            val given = req.query["k"] ?: ""
            if (!MessageDigest.isEqual(given.toByteArray(), t.toByteArray())) return@use reply(out, 403, "text/plain", "forbidden")
            when {
                req.method == "GET" && req.path == "/" -> reply(out, 200, "text/html; charset=utf-8", page(c, req.lang, t).toByteArray())
                req.method == "GET" && req.path == "/photofit.js" -> reply(out, 200, "application/javascript; charset=utf-8", c.assets.open("photofit.js").use { it.readBytes() })
                req.method == "POST" && req.path == "/photo" -> {
                    if (req.length <= 0) return@use reply(out, 411, "application/json", err("empty"))
                    val raw = req.query["raw"] == "1"
                    if (req.length > (if (raw) MAX_RAW else MAX_BODY)) return@use reply(out, 413, "application/json", err("big"))
                    val jpeg = if (raw) {
                        // to a file, not memory: ImageDecoder reads the size first and decodes only as much as the TV needs
                        val f = File.createTempFile("photo-raw", null, c.cacheDir)            // two phones at once each get their own
                        try {
                            val got = f.outputStream().use { o -> copy(req.body, o, req.length) }
                            if (got != req.length) return@use reply(out, 400, "application/json", err("short"))
                            fromRaw(c, f) ?: return@use reply(out, 415, "application/json", err("image"))
                        } finally { f.delete() }
                    } else {
                        val buf = ByteArrayOutputStream(req.length)
                        if (copy(req.body, buf, req.length) != req.length) return@use reply(out, 400, "application/json", err("short"))
                        buf.toByteArray()
                    }
                    val code = save(c, jpeg, onSet)
                    if (code == "ok") reply(out, 200, "application/json", JSONObject().put("ok", true).toString())
                    else reply(out, if (code == "full") 409 else 415, "application/json", err(code))
                }
                else -> reply(out, 404, "text/plain", "not found")
            }
        } catch (e: Exception) { }
    }

    private fun err(code: String) = JSONObject().put("error", code).toString()

    private fun parse(inp: InputStream): Req? {
        fun line(): String? {
            val sb = StringBuilder()
            while (sb.length < 8192) { val b = inp.read(); if (b < 0) return if (sb.isEmpty()) null else sb.toString(); if (b == '\n'.code) break; if (b != '\r'.code) sb.append(b.toChar()) }
            return sb.toString()
        }
        val first = line()?.split(' ') ?: return null
        if (first.size < 2) return null
        var len = 0; var lang = ""
        for (i in 0 until 60) {
            val h = line() ?: return null
            if (h.isEmpty()) break
            val p = h.indexOf(':'); if (p < 0) continue
            val n = h.substring(0, p).trim().lowercase(); val v = h.substring(p + 1).trim()
            if (n == "content-length") len = v.toIntOrNull() ?: return null
            if (n == "accept-language") lang = v
        }
        val target = first[1]
        val q = target.indexOf('?')
        val query = HashMap<String, String>()
        if (q >= 0) for (kv in target.substring(q + 1).split('&')) { val e = kv.indexOf('='); if (e > 0) query[kv.substring(0, e)] = java.net.URLDecoder.decode(kv.substring(e + 1), "UTF-8") }
        return Req(first[0], if (q >= 0) target.substring(0, q) else target, query, lang, len, inp)
    }

    private fun reply(out: java.io.OutputStream, code: Int, type: String, body: String) = reply(out, code, type, body.toByteArray())
    private fun reply(out: java.io.OutputStream, code: Int, type: String, body: ByteArray) {
        val head = "HTTP/1.1 $code ${when (code) { 200 -> "OK"; 403 -> "Forbidden"; 404 -> "Not Found"; 409 -> "Conflict"; 413 -> "Payload Too Large"; else -> "Error" }}\r\n" +
            "Content-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray()); out.write(body); out.flush()
    }

    /**
     * The JPEG into the first free photo slot (what `Home.photo` on the clients does: "photo" is wall.jpg, then photo2 ...), written beside
     * it and renamed so the launcher never reads half a file, then chosen (and added to the rotation's set when that is on).
     * "ok", or "full" (all slots used), or "image" (not a picture TV Home can read).
     */
    private fun copy(i: InputStream, o: java.io.OutputStream, len: Int): Int {
        val chunk = ByteArray(16384); var got = 0
        while (got < len) { val n = i.read(chunk, 0, minOf(chunk.size, len - got)); if (n < 0) break; o.write(chunk, 0, n); got += n }
        return got
    }

    /** A photo the phone's browser could not open, opened here (HEIC needs Android 9+), its middle cut to the TV's size: a JPEG, or null. */
    private fun fromRaw(c: Context, f: File): ByteArray? {
        val dm = c.resources.displayMetrics
        val (w, h) = Wall.want(dm.widthPixels, dm.heightPixels)
        val big = try {
            if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(f)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE                 // a hardware bitmap cannot be cut
                val k = minOf(1f, maxOf(w.toFloat() / info.size.width, h.toFloat() / info.size.height))   // just covers the screen
                d.setTargetSize(maxOf(1, Math.ceil(info.size.width * k.toDouble()).toInt()), maxOf(1, Math.ceil(info.size.height * k.toDouble()).toInt()))
            } else {                                                            // turned by EXIF only on 9+, where ImageDecoder does it
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, o)
                if (o.outWidth <= 0) return null
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = Wall.sample(o.outWidth, o.outHeight, w, h) })
            }
        } catch (e: Throwable) { null } ?: return null                          // OutOfMemoryError too: one photo must not take TV Home down
        val k = maxOf(w.toFloat() / big.width, h.toFloat() / big.height)
        val sw = minOf(big.width, Math.round(w / k)); val sh = minOf(big.height, Math.round(h / k))
        val cut = Bitmap.createBitmap(big, (big.width - sw) / 2, (big.height - sh) / 2, sw, sh)
        val fit = Bitmap.createScaledBitmap(cut, w, h, true)
        return ByteArrayOutputStream().also { fit.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    private fun save(c: Context, jpeg: ByteArray, onSet: () -> Unit): String {
        if (jpeg.size < 4 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) return "image"
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, o)
        if (o.outWidth < 16 || o.outHeight < 16 || o.outWidth > 8192 || o.outHeight > 8192) return "image"
        val have = Walls.photos(c)
        val id = HomeConfig.PHOTOS.firstOrNull { it !in have } ?: return "full"
        val dst = Walls.file(c, id) ?: return "image"
        dst.parentFile?.mkdirs()
        val tmp = File(dst.parentFile, dst.name + ".part")
        tmp.writeBytes(jpeg)
        if (!tmp.renameTo(dst)) { tmp.delete(); return "image" }
        val done = CountDownLatch(1)
        main.post {                                                    // Store and the screen live on the main thread
            try {
                val conf = Store.get(c)
                val set = conf.optJSONArray("walls")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
                val walls = if (conf.optString("wevery", "off") == "off") listOf(id) else set + id
                Store.change(c, JSONObject().put("walls", JSONArray(walls)).put("wall", id).put("photo", System.currentTimeMillis()))
                onSet()
            } finally { done.countDown() }
        }
        done.await(5, TimeUnit.SECONDS)
        return "ok"
    }

    /** photo.html with the words (the phone's language by Accept-Language: Vietnamese, else English) and the TV's picture size filled in. */
    private fun page(c: Context, accept: String, token: String): String {
        val vi = accept.trimStart().lowercase(Locale.ROOT).startsWith("vi")   // the phone's first choice; anything else reads English
        val cfg = android.content.res.Configuration(c.resources.configuration).apply { setLocale(Locale(if (vi) "vi" else "en")) }
        val r = c.createConfigurationContext(cfg).resources
        val words = JSONObject()
        for (n in listOf("pg_title", "pg_pick", "pg_hint", "pg_send", "pg_sending", "pg_done", "pg_again", "pg_err", "pg_err_big", "pg_err_full", "pg_err_image", "pg_gone", "pg_other", "pg_raw"))
            words.put(n, r.getString(c.resources.getIdentifier(n, "string", c.packageName)))
        val dm = c.resources.displayMetrics
        val (w, h) = Wall.want(dm.widthPixels, dm.heightPixels)
        val cfgJson = JSONObject().put("w", w).put("h", h).put("t", words)
        return c.assets.open("photo.html").bufferedReader().use { it.readText() }
            .replace("__LANG__", if (vi) "vi" else "en").replace("__CFG__", cfgJson.toString().replace("</", "<\\/"))
            .replace("__K__", token)                                         // the script needs the token too: without it a 403, no crop code, and every photo "unreadable"
    }

    /** The TV's address on the LAN (a cable wins over Wi-Fi; a VPN is skipped), as Widgets reads it; null when there is none. */
    fun lan(c: Context): String? {
        var ip: String? = null
        try {
            val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION") for (n in cm.allNetworks) {
                val cap = cm.getNetworkCapabilities(n) ?: continue
                if (cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || !cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                val v4 = cm.getLinkProperties(n)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress } ?: continue
                if (ip == null || cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ip = (v4 as InetAddress).hostAddress
            }
        } catch (e: Exception) { }
        return ip
    }
}
