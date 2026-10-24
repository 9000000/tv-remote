package dev.tvremote.karaoke

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.URL
import java.util.concurrent.Executors

/** Small song pictures from i.ytimg.com, kept in memory only (a 1 GB TV: 60 of them at most). Nothing is written to disk. */
object Thumbs {
    private val cache = LruCache<String, Bitmap>(60)
    private val pool = Executors.newFixedThreadPool(2) { r -> Thread(r, "karaoke-thumb").apply { isDaemon = true } }
    private val ui = Handler(Looper.getMainLooper())

    fun into(v: ImageView, url: String) {
        v.tag = url
        cache.get(url)?.let { v.setImageBitmap(it); return }
        v.setImageDrawable(null)
        pool.execute {
            val b = try { URL(url).openStream().use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null } ?: return@execute
            cache.put(url, b)
            ui.post { if (v.tag == url) v.setImageBitmap(b) }
        }
    }
}
