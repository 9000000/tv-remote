package dev.tvremote.home

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import dev.tvremote.home.manage.HomeApi
import org.json.JSONObject

/**
 * The settings for a phone or computer that has ADB on this TV (core/ontv/Home.kt). Only the shell can send this broadcast
 * (see the manifest). Extra "set": the fields to change as base64 JSON ([HomeConfig.merge]); none reads. "drop": one of the
 * photos to delete (its file goes, and it leaves the set). "addvideo": the name of a file in the staging folder to take into the media library ("vname": its name, base64). "thumbs": also small pictures of the photos. The answer, base64
 * JSON in the result data, is [HomeApi.snapshot]'s. The settings page TV Home serves itself ([ManageServer]) gives the same answer.
 */
class HomeReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val answer = try {
            val set = i.getStringExtra("set")?.takeIf { it.isNotEmpty() }?.let { JSONObject(String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8)) }
            i.getStringExtra("addvideo")?.takeIf { Regex("in[a-z0-9]+\\.(mp4|webm|gif)").matches(it) }?.let { f ->
                val name = i.getStringExtra("vname")?.let { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) } ?: f
                val why = HomeApi.addVideo(c, java.io.File(dev.tvremote.home.wall.Videos.staging(c), f), name)
                if (why != "ok") throw IllegalStateException("video_$why")
            }
            HomeApi.snapshot(c, set, i.getStringExtra("drop"), i.getBooleanExtra("thumbs", false))
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: e.javaClass.simpleName)
        }
        resultCode = Activity.RESULT_OK
        resultData = Base64.encodeToString(answer.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
}
