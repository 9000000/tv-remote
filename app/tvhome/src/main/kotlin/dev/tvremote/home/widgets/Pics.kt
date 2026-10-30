package dev.tvremote.home.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * The news pictures: downloaded once into the cache folder (a day), decoded no bigger than the place needs, a few kept in memory.
 * Off the main thread; the view is set only if it still wants that address (the widget is rebuilt every minute).
 */
internal object Pics {
    private val pool = Executors.newFixedThreadPool(2) { r -> Thread(r, "pics").apply { isDaemon = true } }
    private val ui = Handler(Looper.getMainLooper())
    private val mem = object : LruCache<String, Bitmap>(8 * 1024 * 1024) { override fun sizeOf(k: String, v: Bitmap) = v.byteCount }
    private const val DAY = 86_400_000L

    private fun name(url: String) = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Shows [url] in [v] (decoded to about [w] px wide) when it arrives; [v]'s tag is the address it wants. */
    fun into(c: Context, v: ImageView, url: String, w: Int) {
        v.tag = url
        val key = "$url@$w"
        mem.get(key)?.let { v.setImageBitmap(it); return }
        v.setImageDrawable(null)
        val dir = File(c.applicationContext.cacheDir, "news")
        pool.execute {
            val b = try { load(dir, url, w) } catch (e: Throwable) { null } ?: return@execute
            mem.put(key, b)
            ui.post { if (v.tag == url) v.setImageBitmap(b) }
        }
    }

    private fun load(dir: File, url: String, w: Int): Bitmap? {
        dir.mkdirs()
        val f = File(dir, name(url))
        if (!f.isFile || System.currentTimeMillis() - f.lastModified() > DAY) {
            val b = Net.bytes(url, Net.PIC_CAP, 10000) ?: return null
            val t = File(dir, f.name + ".tmp"); t.writeBytes(b); t.renameTo(f)
            dir.listFiles()?.takeIf { it.size > 80 }?.sortedBy { it.lastModified() }?.take(it0(dir))?.forEach { it.delete() }   // the folder stays small
        }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0) { f.delete(); return null }
        var s = 1; while (o.outWidth / (s * 2) >= w) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565 })
    }
    private fun it0(dir: File) = ((dir.listFiles()?.size ?: 0) - 60).coerceAtLeast(0)
}
