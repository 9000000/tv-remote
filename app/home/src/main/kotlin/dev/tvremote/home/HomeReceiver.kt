package dev.tvremote.home

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * The settings for a phone or computer that has ADB on this TV (core/ontv/Home.kt). Only the shell can send this broadcast
 * (see the manifest). Extra "set": the fields to change as base64 JSON ([HomeConfig.merge]); none reads. "drop": one of the
 * photos to delete (its file goes, and it leaves the set). "thumbs": also small pictures of the photos. The answer, base64
 * JSON in the result data: {version, config, apps: [{pkg, label}], photo: whether wall.jpg is there, photos: [{id, v}] the
 * ones there are (v: when each changed), thumbs: {id: base64 JPEG}, bing: {date, title, copyright} of the picture kept}.
 */
class HomeReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val answer = try {
            i.getStringExtra("drop")?.takeIf { HomeConfig.isPhoto(it) }?.let { drop(c, it) }
            val set = i.getStringExtra("set")
            if (set.isNullOrEmpty()) Tiles.refresh(c)                    // a client reading: what the providers offer is as true as this moment
            val config = if (set.isNullOrEmpty()) Store.get(c)
                else Store.change(c, JSONObject(String(Base64.decode(set, Base64.DEFAULT), Charsets.UTF_8)))
            val apps = JSONArray()
            for (a in Apps.list(c)) apps.put(JSONObject().put("pkg", a.pkg).put("label", a.label))
            val photos = JSONArray()
            val thumbs = JSONObject()
            for (id in Walls.photos(c)) {
                val f = Walls.file(c, id)!!
                photos.put(JSONObject().put("id", id).put("v", f.lastModified()))
                if (i.getBooleanExtra("thumbs", false)) thumb(c, id)?.let { thumbs.put(id, it) }
            }
            JSONObject().put("version", c.packageManager.getPackageInfo(c.packageName, 0).versionName)
                .put("config", config).put("apps", apps)
                .put("photo", java.io.File(c.getExternalFilesDir(null), "wall.jpg").isFile)
                .put("photos", photos).put("thumbs", thumbs)
                .also { o -> Online.info(c)?.let { o.put("bing", it) } }
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: e.javaClass.simpleName)
        }
        resultCode = Activity.RESULT_OK
        resultData = Base64.encodeToString(answer.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    /** 96x54 JPEG of a photo as base64 (the page's chips show it), kept in the cache by the photo's time stamp. */
    private fun thumb(c: Context, id: String): String? {
        val f = Walls.file(c, id) ?: return null
        val cache = java.io.File(c.cacheDir, "t-$id-${f.lastModified()}.jpg")
        val bytes = if (cache.isFile) cache.readBytes() else {
            val b = Walls.small(c, id, 96, 54) ?: return null
            val out = ByteArrayOutputStream()
            Bitmap.createBitmap(b, (b.width - 96) / 2, (b.height - 54) / 2, 96, 54).compress(Bitmap.CompressFormat.JPEG, 70, out)
            c.cacheDir.listFiles { x -> x.name.startsWith("t-$id-") }?.forEach { it.delete() }
            out.toByteArray().also { cache.writeBytes(it) }
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    companion object {
        /** A photo deleted: its file, its place in the set, and the background if it was showing (the next of the set, or Night). */
        fun drop(c: Context, id: String) {
            Walls.file(c, id)?.delete()
            val conf = Store.get(c)
            val a = conf.getJSONArray("walls")
            val walls = List(a.length()) { a.getString(it) }.filter { it != id }
            val wall = if (conf.optString("wall") != id) conf.optString("wall") else walls.firstOrNull { Walls.usable(c, it) } ?: "night"
            Store.change(c, JSONObject().put("walls", JSONArray(walls.ifEmpty { listOf(wall) })).put("wall", wall).put("photo", System.currentTimeMillis()))
        }
    }
}
