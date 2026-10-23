package dev.tvremote.home.wall

import android.content.Context
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Bing's pictures as a background ("bing"): its public home-page archive, no account or key. The last [KEEP] days' pictures are
 * kept on the TV and the rotation turns through them ([step]) before it moves on, newest first; a new day's picture comes first.
 * The list is asked for at most every [ASK_MS] while Home is on screen (a small JSON), a picture fetched only once. Offline (or
 * Bing down) nothing changes, and a TV that never got one shows the next of its set.
 */
object Online {
    const val KEEP = 8                                               // what Bing's archive gives in one answer
    private const val ASK_MS = 3 * 3_600_000L                        // the day's picture comes at a time of Bing's own (per market): seen within 3 hours
    private const val RETRY_MS = 30 * 60_000L                        // after a failure: not on every minute
    private const val MAX_BYTES = 6_000_000
    @Volatile private var tried = 0L

    private fun dir(c: Context) = File(c.filesDir, "bing").apply { mkdirs() }
    private fun meta(c: Context) = File(c.filesDir, "bing.json")
    private fun state(c: Context): JSONObject = try { JSONObject(meta(c).readText()) } catch (e: Exception) { JSONObject() }

    /** The pictures kept, newest first: {date, title, copyright}, each with its file there. */
    private fun kept(c: Context): List<JSONObject> {
        old(c)
        val l = state(c).optJSONArray("list") ?: return emptyList()
        return (0 until l.length()).map { l.getJSONObject(it) }.filter { pic(c, it.optString("date")).isFile }
    }
    private fun pic(c: Context, date: String) = File(dir(c), "$date.jpg")

    /** An older TV Home kept one picture as bing.jpg: it becomes the first of the list. */
    private fun old(c: Context) {
        val f = File(c.filesDir, "bing.jpg")
        if (!f.isFile) return
        val o = state(c)
        if (o.has("list")) { f.delete(); return }
        val date = o.optString("date").ifEmpty { "0" }
        if (f.renameTo(pic(c, date)))
            meta(c).writeText(JSONObject().put("at", 0L).put("pick", 0)            // asked again at once: the other days come too
                .put("list", JSONArray().put(JSONObject().put("date", date).put("title", o.optString("title")).put("copyright", o.optString("copyright")))).toString())
    }

    /** The picture shown now (the one [step] reached), null before the first came. */
    fun file(c: Context): File {
        val k = kept(c)
        if (k.isEmpty()) return pic(c, "none")
        return pic(c, k[Math.floorMod(state(c).optInt("pick"), k.size)].optString("date"))
    }

    fun count(c: Context) = kept(c).size

    /** {date, title, copyright, at} of the picture shown, null before the first came. */
    fun info(c: Context): JSONObject? {
        val k = kept(c)
        if (k.isEmpty()) return null
        return JSONObject(k[Math.floorMod(state(c).optInt("pick"), k.size)].toString()).put("at", state(c).optLong("at")).put("count", k.size)
    }

    /**
     * The next older picture, for the rotation; true when it moved, false once all were shown (then it starts again at the newest
     * and the rotation goes on to the next background of the set).
     */
    fun step(c: Context): Boolean {
        val n = count(c)
        val o = state(c)
        val next = o.optInt("pick") + 1
        o.put("pick", if (next < n) next else 0)
        meta(c).writeText(o.toString())
        return next < n
    }

    /** Whether to ask Bing now: nothing kept, or not asked for [ASK_MS], and not failed a moment ago. Cheap: called every minute. */
    fun wanted(c: Context, now: Long = System.currentTimeMillis()): Boolean {
        if (now - tried < RETRY_MS) return false
        val at = state(c).optLong("at")
        return count(c) == 0 || now - at > ASK_MS || now < at
    }

    /** The market Bing is asked for: the TV's language and country (it picks the pictures and the words of their credit), else en-US. */
    fun market(l: Locale = Locale.getDefault()) = if (l.language.isNotEmpty() && l.country.isNotEmpty()) l.language + "-" + l.country else "en-US"

    /** Asks Bing and fetches the pictures not kept yet; true when a new newest one is on the TV. Blocks (network): never on the main thread. */
    fun refresh(c: Context): Boolean {
        tried = System.currentTimeMillis()
        return try {
            val have = kept(c)
            val imgs = JSONObject(String(get("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=$KEEP&mkt=" + market(), 128_000), Charsets.UTF_8))
                .getJSONArray("images")
            val list = JSONArray()
            for (i in 0 until minOf(imgs.length(), KEEP)) {
                val img = imgs.getJSONObject(i)
                val date = img.optString("startdate").takeIf { it.matches(Regex("\\d{8}")) } ?: continue
                if (!pic(c, date).isFile && !fetch(c, img, date)) continue   // one that fails is asked again next time
                list.put(JSONObject().put("date", date).put("title", img.optString("title")).put("copyright", img.optString("copyright")))
            }
            if (list.length() == 0) return false                         // nothing came: keep what we have
            val dates = (0 until list.length()).map { list.getJSONObject(it).optString("date") }.toSet()
            dir(c).listFiles()?.forEach { if (it.name.removeSuffix(".jpg") !in dates) it.delete() }   // older than the last [KEEP] days
            val o = state(c)
            val fresh = have.firstOrNull()?.optString("date") != list.getJSONObject(0).optString("date")
            meta(c).writeText(o.put("list", list).put("at", System.currentTimeMillis()).put("pick", if (fresh) 0 else o.optInt("pick")).toString())
            fresh
        } catch (e: Throwable) { false }
    }

    private fun fetch(c: Context, img: JSONObject, date: String): Boolean {
        val url = img.optString("url").ifEmpty { return false }.let { if (it.startsWith("http")) it else "https://www.bing.com$it" }
        val bytes = try { get(url, MAX_BYTES) } catch (e: Exception) { return false }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth < 800 || o.outHeight < 450) return false          // not a picture
        val tmp = File(dir(c), "$date.tmp")
        tmp.writeBytes(bytes)
        return tmp.renameTo(pic(c, date))
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
