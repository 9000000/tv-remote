package dev.tvremote.tvui

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * destroy() frees a WebView but Chromium keeps its renderer process (~100 MB) for as long as the app lives: measured on the FPT box,
 * TV Games still held 100 MB of renderer + 48 MB minutes after the game closed, TV Home one after its weather map. On a 2 GB TV
 * that is what makes everything else slow. The renderer is shared by every WebView of the process, so it is ended (Android 10+)
 * only when the last one [made] here goes through [end].
 */
object WebEnd {
    private val live = java.util.Collections.newSetFromMap(java.util.WeakHashMap<WebView, Boolean>())
    private val main = Handler(Looper.getMainLooper())

    fun <T : WebView> made(v: T): T { live.add(v); return v }

    /** Off the screen, no client (its lambdas reach the screen), destroyed; the renderer ended when no other WebView here uses it.
     *  Each step on its own, so one that throws does not leave the rest (and the renderer) alive. */
    fun end(v: WebView?) {
        if (v == null) return
        live.remove(v)
        fun step(f: () -> Unit) { try { f() } catch (e: Throwable) { } }
        step { (v.parent as? ViewGroup)?.removeView(v) }
        step { v.webViewClient = Quiet }
        step { v.stopLoading() }
        step { v.onPause() }
        step { v.removeAllViews() }
        // a page replaced by a new one (TV Home's web wall given another address, issue #1) is ended just before the new one is made,
        // which then joins the same renderer: ended now, it took the new page with it. Asked again once the caller is done.
        val r = if (Build.VERSION.SDK_INT >= 29) try { v.webViewRenderProcess } catch (e: Throwable) { null } else null
        step { v.destroy() }
        if (r != null && Build.VERSION.SDK_INT >= 29) main.post { if (live.isEmpty()) step { r.terminate() } }   // r is only set on 29+: said again for lint
    }

    /** What a WebView that is going away is told: its renderer's end is not a crash (the default answer kills the app). */
    private object Quiet : WebViewClient() {
        override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail) = true
    }
}
