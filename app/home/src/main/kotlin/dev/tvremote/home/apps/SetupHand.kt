package dev.tvremote.home.apps

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import dev.tvremote.home.HomeConfig
import org.json.JSONObject

/**
 * A word for TV Setup's HomeInfo "control" (HomeControl there), and the screen it hands back started from here: its PIN, its
 * Manage screen. TV Home is the app in front; TV Setup starting it from the background was dropped by Android 14 (the kids tile
 * closed the panel and nothing came). Call it off the main thread; TV Setup's answer, null when it did not answer. An answer with
 * "error" ("bad": a TV Setup older than the word) started nothing.
 */
object SetupHand {
    fun control(a: Activity, word: String): JSONObject? {
        val b = try { a.contentResolver.call(Uri.parse(HomeConfig.USAGE), "control", word, Bundle().apply { putBoolean("hand", true) }) } catch (e: Exception) { null }
        val out = b?.getString("json")?.let { try { JSONObject(it) } catch (e: Exception) { null } }
        @Suppress("DEPRECATION") val go = b?.getParcelable<PendingIntent>("go")
        if (go != null) try {
            val o = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= 34) o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            go.send(a, 0, null, null, null, null, o.toBundle())
        } catch (e: Exception) { return out?.put("error", "failed") }
        return out
    }
}
