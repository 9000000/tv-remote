package dev.tvremote.home

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * The settings for a phone or computer that has ADB on this TV (core/ontv/Home.kt). Only the shell can send this broadcast
 * (see the manifest). Extra "set": the fields to change as base64 JSON ([HomeConfig.merge]); none reads. The answer, base64
 * JSON in the result data: {version, config, apps: [{pkg, label}], photo: whether the photo is there}.
 */
class HomeReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val answer = try {
            val set = i.getStringExtra("set")
            val config = if (set.isNullOrEmpty()) Store.get(c)
                else Store.change(c, JSONObject(String(Base64.decode(set, Base64.DEFAULT), Charsets.UTF_8)))
            val apps = JSONArray()
            for (a in Apps.list(c)) apps.put(JSONObject().put("pkg", a.pkg).put("label", a.label))
            JSONObject().put("version", c.packageManager.getPackageInfo(c.packageName, 0).versionName)
                .put("config", config).put("apps", apps)
                .put("photo", java.io.File(c.getExternalFilesDir(null), "wall.jpg").isFile)
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: e.javaClass.simpleName)
        }
        resultCode = Activity.RESULT_OK
        resultData = Base64.encodeToString(answer.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
}
