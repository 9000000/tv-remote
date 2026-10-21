package dev.tvremote.channels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Channel logos. A logo is fetched and decoded off the main thread by 3 low-priority workers, scaled down to the card's size
 * (not the picture's), kept in memory (a share of the app's heap) and on disk as that small picture, so a list opened again shows
 * its logos at once. The newest request goes first (what is on screen now, not what was scrolled past), a card scrolled away
 * cancels its request, and the same logo wanted by many cards is fetched once. [prefetch] warms the next rows at a lower priority.
 */
object Logos {
    private const val SIDE = 240                                      // longest side kept: the card's logo is ~160 px on a 1080p TV
    private const val MAX_BYTES = 3_000_000                           // a logo larger than this is not a logo
    private const val DISK_MAX = 24L * 1024 * 1024
    private val main = Handler(Looper.getMainLooper())
    private var cache: LruCache<String, Bitmap>? = null
    private var dir: File? = null
    private val missing = HashSet<String>()
    private val pending = HashMap<String, Job>()                      // main thread only
    private val waiting = java.util.WeakHashMap<ImageView, Job>()     // the request each card waits for (main thread only)
    private var seq = 0L

    private class Job(val url: String, var prio: Int) : Runnable, Comparable<Job> {
        val views = ArrayList<ImageView>(); val ats = ArrayList<(Boolean) -> Unit>(); var seq = 0L
        @Volatile var started = false
        @Volatile var cancelled = false
        override fun compareTo(o: Job) = if (prio != o.prio) o.prio - prio else java.lang.Long.compare(o.seq, seq)   // newest first
        override fun run() { if (!cancelled) work(this) }
    }

    private val pool = java.util.concurrent.ThreadPoolExecutor(3, 3, 0L, java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.PriorityBlockingQueue<Runnable>()) { r ->
        Thread { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND + 4); r.run() }   // the screen's frames come first
    }

    private fun init(c: Context) {
        if (cache != null) return
        synchronized(this) {
            if (cache != null) return
            val am = c.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val mb = (am.memoryClass / 6).coerceIn(6, 24)             // ~a sixth of the heap: a weak TV keeps few, a strong one many
            dir = File(c.cacheDir, "logos").also { it.mkdirs() }
            cache = object : LruCache<String, Bitmap>(mb * 1024 * 1024) { override fun sizeOf(k: String, b: Bitmap) = b.byteCount }
            Thread { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); trim() }.start()
        }
    }

    fun get(url: String): Bitmap? = cache?.get(url)

    /**
     * [url] into [v] (tagged, so a recycled card does not get another channel's logo); [has] says whether it got one (called at
     * once for a logo kept in memory, else when it arrives; false while it is on its way is never said: the card shows its letters).
     */
    fun into(v: ImageView, url: String, maxW: Int, has: (Boolean) -> Unit) {
        init(v.context.applicationContext)
        if (v.tag == url && v.drawable != null) { has(true); return }   // rebound to the same logo: nothing to do
        cancel(v)
        v.tag = url
        cache?.get(url)?.let { v.setImageBitmap(it); has(true); return }
        v.setImageDrawable(null)
        if (url.isEmpty() || synchronized(missing) { url in missing }) { has(false); return }
        has(false)                                                    // the letters show while it comes
        val j = pending[url]?.takeIf { !it.cancelled } ?: Job(url, 1).also { pending[url] = it; it.seq = ++seq; pool.execute(it) }
        if (j.prio < 1) { pool.remove(j); j.prio = 1; j.seq = ++seq; pool.execute(j) }   // it was only a prefetch: on screen now
        j.views.add(v); j.ats.add(has); waiting[v] = j
    }

    /** A card that is no longer showing its logo (scrolled away or recycled): its request is dropped if no one else wants it. */
    fun cancel(v: ImageView) {
        val j = waiting.remove(v) ?: return
        val i = j.views.indexOf(v)
        if (i >= 0) { j.views.removeAt(i); j.ats.removeAt(i) }
        if (j.views.isEmpty() && !j.started) { j.cancelled = true; pool.remove(j); pending.remove(j.url) }
    }

    /** Warms the cache for logos about to come on screen (lowest priority; skipped when kept or already asked). */
    fun prefetch(c: Context, url: String) {
        init(c.applicationContext)
        if (url.isEmpty() || cache?.get(url) != null || pending.containsKey(url) || synchronized(missing) { url in missing }) return
        Job(url, 0).also { it.seq = ++seq; pending[url] = it; pool.execute(it) }
    }

    private fun work(j: Job) {
        j.started = true
        val b = try { fromDisk(j.url) ?: fromNet(j.url) } catch (e: Throwable) { null }
        if (b != null) cache?.put(j.url, b) else synchronized(missing) { missing.add(j.url) }
        main.post {
            pending.remove(j.url)
            for (i in j.views.indices) {
                val v = j.views[i]
                if (waiting[v] === j) waiting.remove(v)
                if (v.tag == j.url) { if (b != null) v.setImageBitmap(b); j.ats[i](b != null) }
            }
        }
    }

    private const val VER = "v3_"                                     // v3: + light logos flagged (an "L" ends the name); older files are deleted
    private val light = java.util.concurrent.ConcurrentHashMap<String, Boolean>()   // url -> a light logo: its tile is dark
    private fun file(url: String, l: Boolean = false) = File(dir, VER + Integer.toHexString(url.hashCode()) + "_" + url.length + if (l) "L" else "")

    /** Whether [url]'s logo is light (pale lettering): the card then shows it on a dark tile. Known once the logo is in. */
    fun isLight(url: String) = light.containsKey(url)

    private fun fromDisk(url: String): Bitmap? {
        val l = file(url, true).exists()
        val f = file(url, l).takeIf { it.exists() } ?: return null
        if (l) light[url] = true
        return BitmapFactory.decodeFile(f.path).also { if (it != null) f.setLastModified(System.currentTimeMillis()) else f.delete() }
    }

    private fun fromNet(url: String): Bitmap? {
        val b = load(url, SIDE) ?: return null
        val out = cut(b)
        val px = IntArray(out.width * out.height).also { out.getPixels(it, 0, out.width, 0, 0, out.width, out.height) }
        val l = LogoFit.isLight(px, out.width, out.height)
        if (l) light[url] = true
        try { file(url, l).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (e: Throwable) {}
        return out
    }

    /** [b] without its border (once, here, not on every draw), then scaled so its longest side is [SIDE]: the card then only fits it in a box. */
    private fun cut(b: Bitmap): Bitmap {
        val w = b.width; val h = b.height
        val px = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
        val r = LogoFit.bounds(px, w, h)
        val cw = r[2] - r[0]; val ch = r[3] - r[1]
        val big = maxOf(cw, ch)
        if (big == w.coerceAtLeast(h) && cw == w && ch == h && big <= SIDE * 5 / 4) return b
        val k = SIDE.toFloat() / big
        val nw = maxOf(1, Math.round(cw * k)); val nh = maxOf(1, Math.round(ch * k))
        val crop = if (cw == w && ch == h) b else Bitmap.createBitmap(b, r[0], r[1], cw, ch)
        return Bitmap.createScaledBitmap(crop, nw, nh, true)
    }

    /** The oldest files go once the folder is over [DISK_MAX]. */
    private fun trim() {
        var fs = dir?.listFiles() ?: return
        for (f in fs) if (!f.name.startsWith(VER)) f.delete()         // the old sizes, before the border was cut
        fs = dir?.listFiles() ?: return
        var sum = fs.sumOf { it.length() }
        if (sum <= DISK_MAX) return
        for (f in fs.sortedBy { it.lastModified() }) { if (sum <= DISK_MAX * 3 / 4) break; sum -= f.length(); f.delete() }
    }

    /** The picture at [url], decoded no larger than needed for [maxW] (a power-of-two sample), not kept: for the one-off uses. */
    fun load(url: String, maxW: Int): Bitmap? {
        val bytes = fetch(url, 6000) ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (o.outWidth / (s * 2) >= maxW) s *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
    }

    private fun fetch(url: String, timeout: Int): ByteArray? {
        val k = URL(url).openConnection() as HttpURLConnection
        k.connectTimeout = timeout; k.readTimeout = timeout; k.instanceFollowRedirects = true
        k.setRequestProperty("User-Agent", "TVChannels/1 (Android; https://tvremote.pages.dev)")   // Wikimedia (many logos) asks for a named agent
        return try { if (k.responseCode == 200 && k.contentLength <= MAX_BYTES) k.inputStream.readBytes() else null } finally { k.disconnect() }
    }
}

/**
 * Asks each stream once (at most every 6 hours, [Store.alive]) whether it answers, a few at a time, so the list can say which
 * channels do not play before one is chosen. A playlist must start with #EXTM3U; anything else that answers 200 counts too
 * (a plain TS or MP4 stream). It only reads the first bytes; nothing is played.
 */
object Probe {
    private val pool = Executors.newFixedThreadPool(2) { r ->      // two, behind the frames: each asks over TLS, which is real work on a weak chip
        Thread { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND + 4); r.run() }
    }
    private val main = Handler(Looper.getMainLooper())
    private val asked = HashMap<String, Long>()                       // url -> when: asked again once its answer (6 hours) is old

    fun check(c: android.content.Context, ch: Channel, done: (Boolean) -> Unit) {
        if (Store.alive(c, ch.url) != null) return
        val t = System.currentTimeMillis()
        if (!synchronized(asked) { (asked[ch.url]?.let { t - it > 6 * 3600_000L } ?: true).also { if (it) asked[ch.url] = t } }) return
        pool.execute {
            val ok = try { answers(ch, Providers.resolve(c, ch.url)) } catch (e: Throwable) { false }
            Store.setAlive(c, ch.url, ok)
            main.post { done(ok) }
        }
    }

    fun answers(ch: Channel, playable: String = ch.url): Boolean {
        var url = playable
        repeat(5) {                                                   // by hand: https -> http redirects are common, and Java stops at them
            val k = URL(url).openConnection() as HttpURLConnection
            k.connectTimeout = 6000; k.readTimeout = 6000; k.instanceFollowRedirects = false
            k.setRequestProperty("User-Agent", ch.agent.ifEmpty { Catalog.BROWSER })
            if (ch.referrer.isNotEmpty()) k.setRequestProperty("Referer", ch.referrer)
            try {
                val code = k.responseCode
                if (code in 300..399) { url = URL(URL(url), k.getHeaderField("Location") ?: return false).toString(); return@repeat }
                if (code !in 200..299) return false
                val head = ByteArray(512)
                val n = k.inputStream.read(head)
                val text = if (n > 0) String(head, 0, n).trimStart('\uFEFF', ' ', '\r', '\n') else ""
                return n > 0 && (!url.contains(".m3u8") || text.startsWith("#EXT"))
            } finally { k.disconnect() }
        }
        return false
    }
}

/**
 * A card's logo: drawn inside one box (82% x 68% of the card's picture, centred) by [LogoFit.scale], the same for every logo,
 * so the cards look even whatever the logo's shape. The bitmap is already cut of its border.
 */
class LogoView(c: Context) : ImageView(c) {
    init { scaleType = ScaleType.MATRIX }
    override fun setImageBitmap(bm: Bitmap?) { super.setImageBitmap(bm); place() }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); place() }
    private fun place() {
        val d = drawable ?: return
        val bw = d.intrinsicWidth; val bh = d.intrinsicHeight
        if (bw <= 0 || bh <= 0 || width <= 0) return
        val s = LogoFit.scale(bw, bh, (width * 0.82f).toInt(), (height * 0.68f).toInt())
        imageMatrix = android.graphics.Matrix().apply { setScale(s, s); postTranslate((width - bw * s) / 2f, (height - bh * s) / 2f) }
    }
}
