package dev.tvremote.home.wall

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import java.util.concurrent.Executor

/**
 * The "web" wall ([HomeConfig.WEB]): the person's page full-screen behind the home screen and in the screensaver, dimmed like a
 * photo. It is only a picture: never focused, never clicked, so the D-pad and the keys stay with the launcher.
 *
 * A WebView costs the most memory of anything in Home (+95 MB on the FPT box), so every one made here is freed by [stop] the
 * moment it is not needed, and none is ever made with an Activity as its context (a view that is forgotten must not hold a screen).
 */
object WebWall {
    private val main = Handler(Looper.getMainLooper())
    private const val DEAD = "dead"                                  // set by [stop]: a callback still queued for that view does nothing

    /**
     * A WebView loading [url]; [failed] is called (main thread, never from inside the WebView's own callback) when the page itself
     * cannot be had or its renderer died, and the caller takes the view away so the gradient shows; [finished] when a load ends.
     * Null where the TV has no WebView at all (a stripped box): the gradient too.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun make(c: Context, url: String, failed: () -> Unit, finished: (() -> Unit)? = null): WebView? = try {
        WebView(c.applicationContext).apply {
            fun later(f: () -> Unit) { val v = this; main.post { if (v.tag != DEAD) f() } }
            setBackgroundColor(Color.TRANSPARENT)                       // the gradient behind shows through until the page paints
            isFocusable = false; isFocusableInTouchMode = false; isClickable = false; isLongClickable = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS  // a page that calls focus() must not take the keys
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false                // a slideshow with video starts by itself
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW   // an https page with an http picture server on the LAN
                setSupportZoom(false); builtInZoomControls = false
            }
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) { if (r.isForMainFrame) later(failed) }
                override fun onReceivedHttpError(v: WebView, r: WebResourceRequest, e: WebResourceResponse) { if (r.isForMainFrame) later(failed) }
                override fun onPageFinished(v: WebView, u: String) { if (finished != null && u != "about:blank") later(finished) }
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = false   // a redirect stays inside, never to another app
                // the renderer is killed when memory is short: without this answer the whole launcher goes down with it
                override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail): Boolean { later(failed); return true }
            }
            resumeTimers()                                              // a page made after Home was stopped would sit frozen by its pauseTimers
            alive.add(this)
            loadUrl(url)
        }
    } catch (e: Throwable) { null }

    /** The same shade a photo gets, over the page ([user]: as [Backdrop.shade]'s): the words of the home screen stay readable. */
    fun dim(c: Context, user: Float) = View(c).apply { background = veil(user) }
    fun veil(user: Float) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, Backdrop.shade(1f, user))

    // pauseTimers() is the whole process's (the screensaver's page runs in it too, while Home is stopped under it): timers stop only when every page here sleeps
    private val alive = java.util.Collections.newSetFromMap(java.util.WeakHashMap<WebView, Boolean>())
    private val asleep = java.util.Collections.newSetFromMap(java.util.WeakHashMap<WebView, Boolean>())

    /** Away from the screen, the page frozen until [wake]. */
    fun sleep(v: WebView?) {
        if (v == null) return
        v.onPause(); asleep.add(v)
        if (alive.all { it in asleep }) v.pauseTimers()
    }
    fun wake(v: WebView?) { v?.apply { asleep.remove(this); onResume(); resumeTimers() } }

    /**
     * The WebView gone for good: off the screen, nothing loading, no client (its lambdas reach the screen), no children, then
     * destroyed. Each step on its own, so one that throws does not leave the rest (and the renderer process) alive.
     */
    fun stop(v: WebView?) {
        if (v == null) return
        v.tag = DEAD
        alive.remove(v); asleep.remove(v)
        fun step(f: () -> Unit) { try { f() } catch (e: Throwable) { } }
        step { (v.parent as? ViewGroup)?.removeView(v) }
        step { v.webViewClient = Quiet }
        step { v.stopLoading() }
        step { v.loadUrl("about:blank") }
        step { v.clearHistory() }
        step { v.onPause() }
        step { v.removeAllViews() }
        // destroy() frees the view but Chromium keeps its renderer process (~90 MB) alive for as long as the app lives (measured on the FPT
        // box: still there minutes after the wall changed); asking it to end is the only way to get that memory back (Android 10+)
        step { if (Build.VERSION.SDK_INT >= 29) v.webViewRenderProcess?.terminate() }
        step { v.destroy() }
    }

    /** What a WebView that is going away is told: its renderer's end is not a crash (the default answer would take the launcher down with it). */
    private object Quiet : WebViewClient() {
        override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail) = true
    }
}

/**
 * The "web" wall in [HomeConfig] `wmode` "snap": the page is drawn to a picture every `wsnap` minutes and the picture is the
 * background, so the cards' frost and the dim work as on a photo and nothing runs between two shots. The last picture is kept in a
 * file, so Home shows it at once after a restart.
 */
object WebSnap {
    /** The last shot of [url], named by the address: another address never shows the old page. */
    fun file(c: Context, url: String) = File(c.filesDir, "websnap-" + Integer.toHexString(url.hashCode()) + ".jpg")

    /** Every shot but [keep] deleted (all of them when null): once the wall is not the web page, the picture is only a file taking room. */
    fun clear(c: Context, keep: File? = null) { c.filesDir.listFiles { f -> f.name.startsWith("websnap-") && f != keep }?.forEach { it.delete() } }

    /** [b] as the shot of [url]: written beside the file and renamed, so Home never reads half of one. Off the main thread. */
    fun save(c: Context, url: String, b: Bitmap) {
        val f = file(c, url)
        val tmp = File(f.parentFile, f.name + ".part")
        try {
            tmp.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (tmp.renameTo(f)) clear(c, f) else tmp.delete()
        } catch (e: Exception) { tmp.delete() }
    }
}

/**
 * Takes the shots of [WebSnap] while Home is on screen. The WebView is made at the screen's size, moved off the screen (still
 * attached and visible, or it would not paint), given [SETTLE_MS] after the load ends for the page's own pictures to come, and
 * drawn into a bitmap of the wanted size. With [keep] it then sleeps in place until the next shot (the page keeps its state, it costs
 * its memory); without, it is destroyed at once and made again next time. Nothing here outlives [stop]: Home calls it when the
 * wall changes, when memory is short and when it is destroyed.
 */
class WebSnapper(
    private val app: Context, private val url: String, private val every: Long, private val keep: Boolean,
    private val host: () -> ViewGroup?, private val ui: Handler, private val bg: Executor, private val shot: () -> Unit,
) {
    val sig = "$url|$every|$keep"
    private var view: WebView? = null
    private var loaded = false
    private var stopped = false
    private val grab = Runnable { grab() }
    private val shoot = Runnable { shoot() }
    private val giveUp = Runnable { fail() }

    /** Schedules the next shot from the last one's time (at once when there is none or it is due): Home is on screen. */
    fun resume() {
        if (stopped) return
        val last = WebSnap.file(app, url).lastModified()
        val now = System.currentTimeMillis()
        later(if (last <= 0 || last > now) 0 else (last + every - now).coerceIn(0, every))
    }

    /** Home is off the screen: no shot is taken; one in the making is dropped, and a kept page sleeps. */
    fun pause() {
        cancel()
        if (keep) WebWall.sleep(view) else release()
    }

    fun stop() { stopped = true; cancel(); release() }

    private fun later(ms: Long) { ui.removeCallbacks(grab); ui.postDelayed(grab, ms) }
    private fun cancel() { ui.removeCallbacks(grab); ui.removeCallbacks(shoot); ui.removeCallbacks(giveUp) }
    private fun release() { loaded = false; WebWall.stop(view); view = null }

    private fun grab() {
        if (stopped) return
        val root = host() ?: return later(RETRY_MS)
        val dm = root.resources.displayMetrics
        var v = view
        if (v == null) {
            v = WebWall.make(app, url, ::fail, ::loaded) ?: return later(RETRY_MS)
            v.translationX = dm.widthPixels.toFloat()                   // beside the screen, not on it
            root.addView(v, 0, android.widget.FrameLayout.LayoutParams(dm.widthPixels, dm.heightPixels))
            view = v
            ui.postDelayed(giveUp, LOAD_MS)                             // a page that never ends loading is a failed one
        } else {
            WebWall.wake(v)                                             // kept: it only needs a moment to paint again, no reload
            ui.postDelayed(shoot, REPAINT_MS)
        }
    }

    private fun loaded() {
        if (stopped || view == null) return
        loaded = true
        ui.removeCallbacks(giveUp)
        ui.removeCallbacks(shoot); ui.postDelayed(shoot, SETTLE_MS)    // a later load (a redirect, a frame) starts the wait again
    }

    private fun shoot() {
        val v = view ?: return
        if (stopped || !loaded || v.width <= 0 || v.height <= 0) return fail()
        val (bw, bh) = Wall.want(v.width, v.height)
        val b = try { Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888) } catch (e: Throwable) { return fail() }
        try {
            val c = Canvas(b)
            c.drawColor(Color.BLACK)                                    // a page with no background of its own
            c.scale(bw.toFloat() / v.width, bh.toFloat() / v.height)
            v.draw(c)
        } catch (e: Throwable) { b.recycle(); return fail() }
        if (keep) WebWall.sleep(v) else release()
        later(every)
        try {
            bg.execute { WebSnap.save(app, url, b); b.recycle(); ui.post { if (!stopped) shot() } }
        } catch (e: Exception) { b.recycle() }                          // the executor is shut: Home is going
    }

    private fun fail() {
        if (stopped) return
        cancel(); release()
        later(RETRY_MS)
    }

    companion object {
        const val SETTLE_MS = 5_000L                                    // after the load ends: the first picture of a slideshow, a font, a clock
        const val REPAINT_MS = 2_500L                                   // a kept page woken: enough to paint what it shows now
        const val LOAD_MS = 60_000L
        const val RETRY_MS = 120_000L
    }
}
