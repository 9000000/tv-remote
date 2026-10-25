package dev.tvremote.home.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL

/**
 * Other apps' rows, as Android TV's own launcher shows them: "Play Next" (what each app says was being watched) and the programmes of
 * their channels (VTV Go's channels, TV Channels' favourites). TvProvider gives a launcher every app's rows marked searchable (the
 * default) with READ_TV_LISTINGS, a runtime permission granted over ADB with the others (HomeConfig.GRANT_WIFI); without it, or before
 * Android 8, there are none. Asked by the user (2026-10-26): Clean TV users missed Play Next on the home screen.
 */
object Watch {
    const val PERM = "android.permission.READ_TV_LISTINGS"
    private const val PER_APP = 6                                     // one app's channel must not fill the row

    class Item(val key: String, val pkg: String, val title: String, val art: String, val intent: String, val app: String)

    fun allowed(c: Context) = Build.VERSION.SDK_INT >= 26 && c.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED

    /** Up to [max] items: Play Next, newest first, then the channels' programmes, one app after another; [hidden] apps left out. */
    fun read(c: Context, max: Int, hidden: Set<String>): List<Item> {
        if (!allowed(c) || max <= 0) return emptyList()
        val labels = HashMap<String, String?>()
        fun label(pkg: String) = labels.getOrPut(pkg) {
            try { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { null }   // gone, or not visible: its rows are stale
        }
        fun rows(uri: Uri, order: String, prefix: String): List<Item> = try {
            c.contentResolver.query(uri, arrayOf("_id", "package_name", "title", "poster_art_uri", "thumbnail_uri", "intent_uri"), null, null, order)?.use { k ->
                val out = ArrayList<Item>()
                while (k.moveToNext() && out.size < 200) {
                    val pkg = k.getString(1) ?: continue
                    val intent = k.getString(5) ?: continue
                    if (pkg in hidden || pkg == c.packageName) continue
                    val app = label(pkg) ?: continue
                    val art = k.getString(3)?.ifEmpty { null } ?: k.getString(4) ?: ""
                    out.add(Item(prefix + k.getLong(0), pkg, k.getString(2)?.trim().orEmpty().ifEmpty { app }, art, intent, app))
                }
                out
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }                        // SecurityException (taken back), a TV without TvProvider

        val next = rows(TvContract.WatchNextPrograms.CONTENT_URI, "last_engagement_time_utc_millis DESC", "n")
        // TV Channels' favourites are left out: they repeat the shortcuts people pin from TV Channels (its Play Next, the last watched, stays)
        val shows = rows(TvContract.PreviewPrograms.CONTENT_URI, "channel_id, weight DESC", "p").filter { it.pkg != "dev.tvremote.channels" }.groupBy { it.pkg }.mapValues { it.value.take(PER_APP) }
        val out = ArrayList(next.distinctBy { it.intent }.take(max))
        val queues = shows.values.map { ArrayDeque(it) }
        while (out.size < max && queues.any { it.isNotEmpty() }) for (q in queues) if (out.size < max) q.removeFirstOrNull()?.let { s -> if (out.none { it.intent == s.intent }) out.add(s) }
        return out
    }

    /** The intent an item opens, made safe to start from a launcher: no selector, no URI grants. */
    fun intent(i: Item): Intent? = try {
        Intent.parseUri(i.intent, Intent.URI_INTENT_SCHEME).apply {
            selector = null
            flags = (flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION).inv()) or Intent.FLAG_ACTIVITY_NEW_TASK
        }
    } catch (e: Exception) { null }

    private val cache = object : LinkedHashMap<String, Bitmap>(16, .75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Bitmap>?) = size > 40 }

    /** [i]'s picture as a [w]-wide 16:9 tile: a wide poster fills it, a logo or a tall poster sits whole on [bg]. Off the main thread; null: none. */
    fun art(c: Context, i: Item, w: Int, bg: Int): Bitmap? {
        if (i.art.isEmpty()) return null
        synchronized(cache) { cache[i.art + w]?.let { return it } }
        val raw = try { bytes(c, Uri.parse(i.art)) } catch (e: Exception) { null } ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, o)
        if (o.outWidth <= 0) return null
        var s = 1; while (o.outWidth / (s * 2) >= w) s *= 2
        val src = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val h = w * 9 / 16
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out); cv.drawColor(bg)
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val r = src.width.toFloat() / src.height
        if (r in 1.6f..1.95f) {                                       // a 16:9 poster: cropped to fill
            val cw = (src.height * 16f / 9).toInt().coerceAtMost(src.width); val ch = (cw * 9f / 16).toInt().coerceAtMost(src.height)
            cv.drawBitmap(src, Rect((src.width - cw) / 2, (src.height - ch) / 2, (src.width + cw) / 2, (src.height + ch) / 2), Rect(0, 0, w, h), p)
        } else {                                                      // a channel's logo: whole, with room around it
            val k = minOf(w * .7f / src.width, h * .62f / src.height)
            val dw = src.width * k; val dh = src.height * k
            cv.drawBitmap(src, null, RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), p)
        }
        src.recycle()
        try {                                                         // whose it is: the app's icon small in a corner, as Android TV's launcher does (a name there hid the logos)
            val d = c.packageManager.getApplicationIcon(i.pkg); val sz = h * 24 / 100; val m = h * 6 / 100
            d.setBounds(w - m - sz, h - m - sz, w - m, h - m); d.draw(cv)
        } catch (e: Exception) { }
        synchronized(cache) { cache[i.art + w] = out }
        return out
    }

    private fun bytes(c: Context, u: Uri): ByteArray? = when (u.scheme) {
        "http", "https" -> (URL(u.toString()).openConnection() as HttpURLConnection).run {
            try {
                connectTimeout = 5000; readTimeout = 8000; instanceFollowRedirects = true
                if (responseCode !in 200..299 || contentLength > 4_000_000) null else inputStream.use { it.readBytes().takeIf { b -> b.size <= 4_000_000 } }
            } finally { disconnect() }
        }
        "content", "android.resource" -> c.contentResolver.openInputStream(u)?.use { it.readBytes() }
        else -> null
    }
}
