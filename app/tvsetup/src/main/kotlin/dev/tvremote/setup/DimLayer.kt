package dev.tvremote.setup

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.WindowManager
import dev.tvremote.core.ontv.Dim
import org.json.JSONObject

/**
 * The tint over the whole screen (Dim.kt in the shared code): one window of a single colour that takes no keys and no touches,
 * kept in the prefs so it comes back when this app's process starts again ([restore], from RemoteService).
 */
internal object DimLayer {
    private const val PREFS = "dim"
    private val ui = Handler(Looper.getMainLooper())
    private var view: View? = null

    fun state(c: Context): JSONObject =
        Dim.clean(try { JSONObject(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("d", null) ?: "{}") } catch (e: Exception) { JSONObject() })

    /** Keeps [d] (through [Dim.clean]) and draws it; false when the TV does not let TV Setup draw over other apps. */
    fun set(c: Context, d: JSONObject): Boolean {
        val ctx = c.applicationContext
        val o = Dim.clean(d)
        if (o.optBoolean("on") && Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(ctx)) return false
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("d", o.toString()).apply()
        ui.post { draw(ctx, o) }
        return true
    }

    fun restore(c: Context) { val o = state(c); if (o.optBoolean("on")) ui.post { draw(c.applicationContext, o) } }

    private fun draw(c: Context, o: JSONObject) {
        val wm = (c.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager)
        if (!o.optBoolean("on")) { view?.let { try { wm.removeView(it) } catch (e: Exception) { } }; view = null; return }
        val v = view ?: View(c).also { it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        v.setBackgroundColor(Dim.rgb(o))
        var alpha = Dim.pct(o.opt("pct")) / 100f
        // Android 12+ keeps touches from the apps under a window more opaque than 0.8: a phone or tablet with TV Setup
        if (Build.VERSION.SDK_INT >= 31 && c.packageManager.hasSystemFeature("android.hardware.touchscreen")) alpha = minOf(alpha, 0.8f)
        if (view != null) { val lp = v.layoutParams as WindowManager.LayoutParams; lp.alpha = alpha; try { wm.updateViewLayout(v, lp) } catch (e: Exception) { }; return }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        // a TV never turns: the screen exactly. Twice the long side each way was a 3840 x 3840 buffer (59 MB) for one colour
        val tv = c.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val size = if (tv) WindowManager.LayoutParams.MATCH_PARENT
            else maxOf(c.resources.displayMetrics.widthPixels, c.resources.displayMetrics.heightPixels) * 2   // past every edge, whichever way the screen turns
        val lp = WindowManager.LayoutParams(size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT)
        if (!tv) { lp.x = -size / 4; lp.y = -size / 4 }
        lp.alpha = alpha
        try { wm.addView(v, lp); view = v } catch (e: Exception) { Log.w("DimLayer", "no window over other apps: $e") }
    }
}

/** The shell's way to [DimLayer] (Dim.kt): only the shell may send it (the manifest). Extra "d": the tint as base64 JSON; none: only read. */
class DimReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val answer = try {
            val d = i.getStringExtra("d")
            if (d == null || DimLayer.set(c, JSONObject(String(Base64.decode(d, Base64.DEFAULT), Charsets.UTF_8)))) DimLayer.state(c).put("ok", true)
            else JSONObject().put("error", "overlay")
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: e.javaClass.simpleName)
        }
        resultCode = Activity.RESULT_OK
        resultData = Base64.encodeToString(answer.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
}
