package dev.tvremote.games

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import dev.tvremote.tvui.dp
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The picture on a game's tile, kept in files/thumbs as one tile-sized JPEG (so a weak box decodes ~30 KB, never a 300 KB PNG):
 * box art from libretro-thumbnails ("box"), else the page's picture ("art": the catalog's img, a web game's og:image) or its icon
 * ("icon"), else the snapshot taken when the player left the game ("snap"). A 404 is remembered a week (".miss"). Everything here
 * runs off the main thread: one thread reads the disk, at most 3 download.
 */
object Thumbs {
    /** key: what the tile is ("g:<file name>", "w:<url>", "h:<id>"); the rest says where its picture may come from. */
    class Src(val key: String, val boxes: List<String> = emptyList(), val img: String? = null, val page: String? = null, val asset: String? = null) {
        /** What a fetched picture is kept under: with the catalog's img, so a new picture (a new file name) is fetched again. */
        val store get() = if (img != null) "$key@$img" else key
    }

    private const val WEEK = 7 * 24 * 3600_000L
    private const val MIN30 = 30 * 60_000L                             // a catalog picture not on the site yet (pushed after the list): asked again soon
    private const val MAX_BYTES = 4 shl 20
    private const val BACK = 0xFF10131A.toInt()
    private val REMOTE = listOf("box", "art", "icon")
    private val disk = Executors.newSingleThreadExecutor { Thread(it, "thumbs-disk").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    private val net = Executors.newFixedThreadPool(3) { Thread(it, "thumbs-net").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    private val main = Handler(Looper.getMainLooper())
    private val mem = object : android.util.LruCache<String, Pair<Bitmap, String>>(12 shl 20) { override fun sizeOf(k: String, v: Pair<Bitmap, String>) = v.first.byteCount }   // pictures as drawn, by what the tile is kept under
    private val failed = java.util.Collections.synchronizedSet(HashSet<String>())   // a timeout: asked again next time the app starts

    fun tileW(c: Context) = c.dp(200)
    fun tileH(c: Context) = c.dp(125)

    private fun dir(c: Context) = File(c.filesDir, "thumbs").also { it.mkdirs() }
    private fun hash(key: String) = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    private fun file(c: Context, key: String, mode: String) = File(dir(c), "${hash(key)}.$mode.jpg")
    private fun miss(c: Context, key: String) = File(dir(c), "${hash(key)}.miss")

    /** A ROM's box art addresses: the catalog's optional "thumbs" base for the system (cores.<id>.thumbs), else the repo named in [Systems]. */
    fun src(c: Context, sys: Systems.Sys, fileName: String): Src {
        val over = Catalog.peek(c).optJSONObject("cores")?.optJSONObject(sys.id)?.optString("thumbs", "")?.takeIf { it.startsWith("https://") }
        val base = over ?: ThumbNames.base(sys.repo)
        return Src("g:$fileName", boxes = ThumbNames.candidates(fileName).map { ThumbNames.url(base, it) })
    }

    /**
     * A catalog game's picture ("img" of its homebrew entry: a path under the catalog's base, e.g. "img/nova.jpg", or an https address),
     * for a ROM in the library by the file name it was downloaded under (the catalog name without odd characters, see LibraryActivity.getRom).
     * Same key as the Suggestions tile ("h:<id>"), so the picture is fetched and kept once. null = no such entry or no picture.
     */
    fun catalog(c: Context, file: String): Src? {
        val brew = Catalog.peek(c).optJSONArray("homebrew") ?: return null
        val stem = file.substringBeforeLast('.').removeSuffix(" (homebrew)")
        for (i in 0 until brew.length()) {
            val h = brew.optJSONObject(i) ?: continue
            val img = h.optString("img").takeIf { it.isNotEmpty() } ?: continue
            if (stem.equals(h.optString("name").replace(Regex("[^A-Za-z0-9 ._-]"), ""), true)) return Src("h:" + h.optString("id"), img = img)
        }
        return null
    }

    /** Shows what is on disk at once (off the main thread), then asks the network once if there is no real picture yet. [show] runs on the main thread. */
    fun load(c: Context, s: Src, show0: (Bitmap, String) -> Unit) {
        val app = c.applicationContext
        val show = { b: Bitmap, m: String -> mem.put(s.store, b to m); show0(b, m) }
        mem.get(s.store)?.let { (b, m) -> show0(b, m); if (m in REMOTE) return }   // decoded once: a rebuilt row (search, filter, a new folder) draws at once, no disk, no decode
        disk.execute {
            try {
                if (s.asset != null) {
                    val b = app.assets.open(s.asset).use { compose(app, it.readBytes(), "art") } ?: return@execute
                    main.post { show(b, "art") }; return@execute
                }
                var have: String? = null
                for (m in REMOTE + "snap") {
                    val f = file(app, if (m == "snap") s.key else s.store, m)
                    if (f.exists()) { decode(f)?.let { b -> have = m; main.post { show(b, m) } }; if (have != null) break }
                }
                if (have in REMOTE || (s.boxes.isEmpty() && s.img == null && s.page == null)) return@execute
                val ms = miss(app, s.store)
                if (ms.exists() && dev.tvremote.guard.Age.fresh(ms.lastModified(), if (s.img != null) MIN30 else WEEK)) return@execute
                if (s.key in failed) return@execute
                net.execute {
                    try {
                        val r = fetch(app, s)
                        if (r == null) ms.writeText("1") else main.post { show(r.first, r.second) }
                    } catch (e: Exception) { failed.add(s.key) }       // offline or slow: not a 404, so not remembered
                }
            } catch (e: Exception) { }
        }
    }

    private fun decode(f: File): Bitmap? = try { BitmapFactory.decodeFile(f.path) } catch (e: Throwable) { null }

    /** null = nothing there (404 everywhere); an IOException = could not ask. */
    private fun fetch(c: Context, s: Src): Pair<Bitmap, String>? {
        val tries = ArrayList<Pair<String, String>>()                  // address to mode
        s.boxes.forEach { tries += it to "box" }
        s.img?.let { im ->                                             // https, or a path under the catalog's base (site first, then GitHub)
            if (im.startsWith("https://")) tries += im to "art"
            else if (!im.startsWith("/") && !im.contains("..")) Catalog.bases(c).forEach { tries += (it + im) to "art" }
        }
        var pageErr: IOException? = null
        if (s.page != null) {
            try {
                val html = get(s.page, 300_000)?.let { String(it, Charsets.UTF_8) } ?: ""
                val (og, icons) = pageImages(html, URL(s.page))
                og?.let { tries += it to "art" }
                icons.forEach { tries += it to "icon" }
                tries += URL(URL(s.page), "/favicon.ico").toString() to "icon"
            } catch (e: IOException) { pageErr = e }
        }
        var netErr: IOException? = null
        for ((u, mode) in tries) {
            val bytes = try { get(u, MAX_BYTES) } catch (e: IOException) { if (mode == "box") throw e else { netErr = e; null } } ?: continue
            val b = compose(c, bytes, mode) ?: continue
            write(file(c, s.store, mode), b)
            return b to mode
        }
        if (pageErr != null) throw pageErr
        if (netErr != null && s.img != null) throw netErr           // offline is not a 404: asked again later, not remembered a week
        return null
    }

    /** One GET with a cap on the size; null for a 4xx, an IOException for anything else that failed. */
    private fun get(url: String, max: Int): ByteArray? {
        val u = URL(url)
        if (u.protocol != "https" && u.protocol != "http") return null
        val h = u.openConnection() as HttpURLConnection
        h.connectTimeout = 6000; h.readTimeout = 10000; h.setRequestProperty("User-Agent", "TVGames")
        try {
            val code = h.responseCode
            if (code in 400..499) return null
            if (code != 200) throw IOException("HTTP $code")
            val out = java.io.ByteArrayOutputStream()
            h.inputStream.use { i -> val buf = ByteArray(8192); while (out.size() < max) { val n = i.read(buf); if (n < 0) break; out.write(buf, 0, n) } }
            return out.toByteArray()
        } finally { h.disconnect() }
    }

    private fun attrs(tag: String): Map<String, String> =
        Regex("([\\w:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')").findAll(tag).associate { it.groupValues[1].lowercase() to (it.groupValues[2] + it.groupValues[3]).replace("&amp;", "&") }

    /** og:image / twitter:image, and the icons (apple-touch-icon and icon links, the biggest first; svg skipped: it cannot be decoded). */
    fun pageImages(html: String, base: URL): Pair<String?, List<String>> {
        fun abs(h: String) = try { URL(base, h.trim()).toString() } catch (e: Exception) { null }
        var og: String? = null
        val icons = ArrayList<Pair<Int, String>>()
        for (m in Regex("<(meta|link)\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html)) {
            val a = attrs(m.value)
            if (m.groupValues[1].lowercase() == "meta") {
                val n = (a["property"] ?: a["name"] ?: "").lowercase()
                if (og == null && (n == "og:image" || n == "og:image:url" || n == "twitter:image" || n == "twitter:image:src")) og = a["content"]?.let { abs(it) }
            } else if ("icon" in (a["rel"] ?: "").lowercase()) {
                val h = a["href"]?.takeIf { it.isNotBlank() && !it.substringBefore('?').endsWith(".svg", true) && !it.startsWith("data:") } ?: continue
                val size = Regex("(\\d+)x(\\d+)").find(a["sizes"] ?: "")?.let { it.groupValues[1].toInt() * it.groupValues[2].toInt() } ?: if ("apple" in a["rel"]!!.lowercase()) 180 * 180 else 0
                abs(h)?.let { icons += size to it }
            }
        }
        return og to icons.sortedByDescending { it.first }.map { it.second }.distinct().take(3)
    }

    /** The picture as one tile-sized bitmap: art and snapshots fill it (cropped), box art and icons sit whole on a dark backdrop. */
    fun compose(c: Context, bytes: ByteArray, mode: String): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        val tw = tileW(c); val th = tileH(c)
        var ss = 1
        while (o.outWidth / (ss * 2) >= tw && o.outHeight / (ss * 2) >= th) ss *= 2
        val src = try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = ss }) } catch (e: Throwable) { null } ?: return null
        return place(src, tw, th, mode, c.dp(96))
    }

    fun place(src: Bitmap, tw: Int, th: Int, mode: String, iconMax: Int): Bitmap {
        val out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out); cv.drawColor(BACK)
        val sx = tw.toFloat() / src.width; val sy = th.toFloat() / src.height
        var k = if (mode == "art" || mode == "snap") maxOf(sx, sy) else minOf(sx, sy)
        if (mode == "icon") k = minOf(k, iconMax.toFloat() / maxOf(src.width, src.height))
        val m = Matrix().apply { postScale(k, k); postTranslate((tw - src.width * k) / 2, (th - src.height * k) / 2) }
        cv.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun write(f: File, b: Bitmap) {
        val tmp = File(f.path + ".tmp")
        try { tmp.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) }; tmp.renameTo(f) } catch (e: Exception) { tmp.delete() }
    }

    private val copier by lazy { android.os.HandlerThread("thumbs-copy").apply { start() } }

    /**
     * The picture of the game screen (PixelCopy of the GL surface, API 24+) kept as the game's tile when it has no box art: taken a few
     * seconds into the play and now and then (the surface can no longer be read once the screen is leaving, "no data"), and again at
     * [waitMs] > 0 on the way out, waiting that long so the library which resumes next finds the file. A black frame is not kept.
     */
    fun snapshot(c: Context, view: SurfaceView, key: String, waitMs: Long = 0) {
        if (android.os.Build.VERSION.SDK_INT < 24 || view.width < 16 || view.height < 16 || !view.holder.surface.isValid) return
        try {
            val app = c.applicationContext
            val w = tileW(app) * 2
            val bmp = Bitmap.createBitmap(w, maxOf(16, w * view.height / view.width), Bitmap.Config.ARGB_8888)
            val done = CountDownLatch(1)
            android.view.PixelCopy.request(view, bmp, { r ->
                try { if (r == android.view.PixelCopy.SUCCESS && !blank(bmp)) write(file(app, key, "snap"), place(trimBars(bmp), tileW(app), tileH(app), "snap", 0)) }
                catch (e: Throwable) { }
                done.countDown()
            }, Handler(copier.looper))
            if (waitMs > 0) done.await(waitMs, TimeUnit.MILLISECONDS)
        } catch (e: Throwable) { }
    }

    /** The frame without the black bars the core's picture is letterboxed with (a 4:3 game on a 16:9 view); lines are tested on a few samples. */
    fun trimBars(b: Bitmap): Bitmap {
        fun black(x: Int, y: Int) = b.getPixel(x, y) and 0xFFFFFF == 0
        fun colBlack(x: Int) = (1..15).all { black(x, b.height * it / 16) }
        fun rowBlack(y: Int) = (1..15).all { black(b.width * it / 16, y) }
        var l = 0; var r = b.width - 1; var t = 0; var u = b.height - 1
        while (l < r - 8 && colBlack(l)) l++
        while (r > l + 8 && colBlack(r)) r--
        while (t < u - 8 && rowBlack(t)) t++
        while (u > t + 8 && rowBlack(u)) u--
        return if (l == 0 && t == 0 && r == b.width - 1 && u == b.height - 1) b else Bitmap.createBitmap(b, l, t, r - l + 1, u - t + 1)
    }

    /** A black (or one-colour) frame: sampled on a grid, nothing worth showing. */
    fun blank(b: Bitmap): Boolean {
        val first = b.getPixel(b.width / 2, b.height / 2); var diff = 0
        for (x in 1..7) for (y in 1..7) if (b.getPixel(b.width * x / 8, b.height * y / 8) != first) diff++
        return diff < 3
    }
}
