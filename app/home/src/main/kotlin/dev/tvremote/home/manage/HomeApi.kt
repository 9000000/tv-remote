package dev.tvremote.home.manage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import dev.tvremote.home.apps.Apps
import dev.tvremote.home.wall.Online
import dev.tvremote.home.apps.Pins
import dev.tvremote.home.Store
import dev.tvremote.home.apps.Tiles
import dev.tvremote.home.wall.Walls
import dev.tvremote.home.HomeConfig

/**
 * What a client is told of TV Home and what it may change: one place for the two ways in, [HomeReceiver] (a phone or computer with ADB,
 * the answer in a broadcast) and [ManageServer] (the settings page TV Home serves itself). The answer: {version, config, apps:
 * [{pkg, label}], photo: whether wall.jpg is there, photos: [{id, v}] the ones there are (v: when each changed), thumbs: {id: base64
 * JPEG}, bing: {date, title, copyright} of the picture kept}. Always on the main thread: Store and the screen live there.
 */
object HomeApi {
    /** [set] changed first (HomeConfig.merge cleans it), [drop] one photo deleted first, [thumbs] the photos' small pictures too. */
    fun snapshot(c: Context, set: JSONObject?, drop: String?, thumbs: Boolean): JSONObject {
        if (drop != null && HomeConfig.isPhoto(drop)) drop(c, drop)
        if (set == null) { Tiles.refresh(c); Pins.refresh(c) }         // a client reading: what the providers and the apps offer is as true as this moment
        val config = if (set == null) Store.get(c) else Store.change(c, set)
        val apps = JSONArray()
        for (a in Apps.list(c)) apps.put(JSONObject().put("pkg", a.pkg).put("label", a.label))
        val photos = JSONArray()
        val pictures = JSONObject()
        for (id in Walls.photos(c)) {
            val f = Walls.file(c, id)!!
            photos.put(JSONObject().put("id", id).put("v", f.lastModified()))
            if (thumbs) thumb(c, id)?.let { pictures.put(id, it) }
        }
        return JSONObject().put("version", c.packageManager.getPackageInfo(c.packageName, 0).versionName)
            .put("config", config).put("apps", apps)
            .put("photo", File(c.getExternalFilesDir(null), "wall.jpg").isFile)
            .put("photos", photos).put("thumbs", pictures)
            .also { o -> Online.info(c)?.let { o.put("bing", it) } }
    }

    /** 96x54 JPEG of a photo as base64 (the page's chips show it), kept in the cache by the photo's time stamp. */
    private fun thumb(c: Context, id: String): String? {
        val f = Walls.file(c, id) ?: return null
        val cache = File(c.cacheDir, "t-$id-${f.lastModified()}.jpg")
        val bytes = if (cache.isFile) cache.readBytes() else {
            val b = Walls.small(c, id, 96, 54) ?: return null
            val out = ByteArrayOutputStream()
            Bitmap.createBitmap(b, (b.width - 96) / 2, (b.height - 54) / 2, 96, 54).compress(Bitmap.CompressFormat.JPEG, 70, out)
            c.cacheDir.listFiles { x -> x.name.startsWith("t-$id-") }?.forEach { it.delete() }
            out.toByteArray().also { cache.writeBytes(it) }
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** A photo deleted: its file, its place in the set, and the background if it was showing (the next of the set, or Night). */
    fun drop(c: Context, id: String) {
        Walls.file(c, id)?.delete()
        val conf = Store.get(c)
        val a = conf.getJSONArray("walls")
        val walls = List(a.length()) { a.getString(it) }.filter { it != id }
        val wall = if (conf.optString("wall") != id) conf.optString("wall") else walls.firstOrNull { Walls.usable(c, it) } ?: "night"
        Store.change(c, JSONObject().put("walls", JSONArray(walls.ifEmpty { listOf(wall) })).put("wall", wall).put("photo", System.currentTimeMillis()))
    }

    /**
     * The JPEG (already the screen's size: the page crops it) into the first free photo slot (what `Home.photo` on the clients does:
     * "photo" is wall.jpg, then photo2 ...), written beside it and renamed so the launcher never reads half a file, then chosen (and added
     * to the rotation's set when that is on). "ok", or "full" (all slots used), or "image" (not a picture TV Home can read).
     */
    fun savePhoto(c: Context, jpeg: ByteArray): String {
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
        val conf = Store.get(c)
        val set = conf.optJSONArray("walls")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
        val walls = if (conf.optString("wevery", "off") == "off") listOf(id) else set + id
        Store.change(c, JSONObject().put("walls", JSONArray(walls)).put("wall", id).put("photo", System.currentTimeMillis()))
        return "ok"
    }
}
