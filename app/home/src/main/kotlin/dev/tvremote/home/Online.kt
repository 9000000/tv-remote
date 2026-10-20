package dev.tvremote.home

import android.content.Context
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * The day's Bing picture as a background ("bing"): its public home-page archive, no account or key. One request for the list and
 * one for the 1920x1080 picture, at most about once a day, and only when it is the one shown or part of the rotating set. The
 * last picture stays on the TV: offline (or Bing down) nothing changes, and a TV that never got one shows the next of its set.
 */
object Online {
    private const val STALE_MS = 20 * 3_600_000L                     // a new picture comes once a day: no need to ask sooner
    private const val RETRY_MS = 30 * 60_000L                        // after a failure: not on every screen change
    private const val MAX_BYTES = 6_000_000
    @Volatile private var tried = 0L

    fun file(c: Context) = File(c.filesDir, "bing.jpg")
    private fun meta(c: Context) = File(c.filesDir, "bing.json")

    /** {date, title} of the picture kept, null before the first one came. */
    fun info(c: Context): JSONObject? = try { JSONObject(meta(c).readText()) } catch (e: Exception) { null }

    /** Whether a new picture is worth asking for: none yet, or the one kept is from before today, and not tried a moment ago. */
    fun wanted(c: Context, now: Long = System.currentTimeMillis()): Boolean {
        if (now - tried < RETRY_MS) return false
        val at = info(c)?.optLong("at", 0) ?: 0L
        return !file(c).isFile || now - at > STALE_MS || now < at
    }

    /** The market Bing is asked for: the TV's language and country (it picks the pictures and the words of their credit), else en-US. */
    fun market(l: Locale = Locale.getDefault()) = if (l.language.isNotEmpty() && l.country.isNotEmpty()) l.language + "-" + l.country else "en-US"

    /** Asks Bing and keeps the picture; true when a new picture is on the TV. Blocks (network): never on the main thread. */
    fun refresh(c: Context): Boolean {
        tried = System.currentTimeMillis()
        return try {
            val list = JSONObject(String(get("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=" + market(), 64_000), Charsets.UTF_8))
            val img = list.getJSONArray("images").getJSONObject(0)
            val date = img.optString("startdate")
            if (date.isNotEmpty() && date == info(c)?.optString("date") && file(c).isFile) {      // the same picture: just not asked again today
                meta(c).writeText(info(c)!!.put("at", System.currentTimeMillis()).toString()); return false
            }
            val url = img.getString("url").let { if (it.startsWith("http")) it else "https://www.bing.com$it" }
            val bytes = get(url, MAX_BYTES)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            if (o.outWidth < 800 || o.outHeight < 450) return false      // not a picture: keep the one we have
            val tmp = File(c.filesDir, "bing.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file(c))) return false
            meta(c).writeText(JSONObject().put("date", date).put("title", img.optString("title")).put("copyright", img.optString("copyright"))
                .put("at", System.currentTimeMillis()).toString())
            true
        } catch (e: Throwable) { false }
    }

    private fun get(u: String, max: Int): ByteArray {
        val conn = URL(u).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000; conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "TV-Home")
            if (conn.responseCode != 200) throw java.io.IOException("http ${conn.responseCode}")
            val out = java.io.ByteArrayOutputStream()
            conn.inputStream.use { s ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = s.read(buf); if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > max) throw java.io.IOException("too large")
                }
            }
            return out.toByteArray()
        } finally { conn.disconnect() }
    }
}
