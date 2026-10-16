package dev.tvremote.setup

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * The web UI on the TV itself, for when there is no phone or computer at hand: the page RemoteService serves, in a WebView,
 * let in by the pass MainActivity hands over (RemoteService.selfPass, in memory only). The page knows it is on the TV (?ontv=1,
 * js/ontv.js): no Remote tab, and the D-pad moves between its controls.
 * It runs in its own process (":web" in the manifest): a WebView takes 100 MB+ and, on a TV short of memory, can stall or be
 * killed; then only this screen goes, never the service phones and computers use.
 */
class ManageActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var web: WebView
    private var tries = 0
    private var picked: ValueCallback<Array<Uri>>? = null
    private val url = "http://127.0.0.1:${RemoteService.PORT}/?ontv=1"
    private var pass = ""

    companion object { const val PASS = "pass" }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        pass = intent.getStringExtra(PASS) ?: run { finish(); return }   // only MainActivity opens it, with the pass
        val box = android.widget.FrameLayout(this)
        box.setBackgroundColor(Color.rgb(13, 16, 20))
        val wait = android.widget.TextView(this)
        wait.text = if (java.util.Locale.getDefault().language == "vi") "Đang mở…" else "Opening…"
        wait.setTextColor(Color.rgb(160, 170, 185))
        wait.textSize = 18f
        box.addView(wait, android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER))
        setContentView(box)
        // WebView's first start in this process loads its library (seconds on a slow TV, 8 s on the emulator): done here on
        // a thread, so the screen keeps answering the remote meanwhile instead of Android calling TV Setup frozen
        Thread({
            try { android.webkit.WebSettings.getDefaultUserAgent(applicationContext) } catch (e: Exception) { }
            ui.post { if (!isFinishing && !isDestroyed) { box.removeView(wait); open(box) } }
        }, "webview").start()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun open(box: android.widget.FrameLayout) {
        web = WebView(this)
        web.setBackgroundColor(Color.rgb(13, 16, 20))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        CookieManager.getInstance().setCookie("http://127.0.0.1:${RemoteService.PORT}",
            RemoteService.SELF_COOKIE + "=" + pass + "; Path=/; HttpOnly; SameSite=Strict")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                if (r.url.host == "127.0.0.1") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, r.url)) } catch (e: Exception) { }   // a link out (GitHub, help): the TV's browser, if it has one
                return true
            }
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                // the service is still starting (its first start makes a key): ask again for a while
                if (r.isForMainFrame && tries++ < 40) ui.postDelayed({ web.loadUrl(url) }, 500)
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                picked?.onReceiveValue(null)
                picked = cb
                return try { startActivityForResult(p.createIntent(), 1); true } catch (e: Exception) { picked = null; false }   // a TV with no file picker: the page says so
            }
        }
        box.addView(web)
        web.loadUrl(url)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        picked?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data))
        picked = null
    }

    // Back: the page closes what is open (a dialog, a menu) first; only with nothing open does it leave
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_BACK) {
            if (!::web.isInitialized) finish()
            else if (e.action == KeyEvent.ACTION_UP) web.evaluateJavascript("window.tvBack ? tvBack() : false") { if (it != "true") finish() }
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    // no MainActivity.shown here (another process): the page's requests keep RemoteService from updating meanwhile (inUse)
    override fun onResume() { super.onResume(); if (::web.isInitialized) web.onResume() }
    override fun onPause() { super.onPause(); if (::web.isInitialized) web.onPause() }
    override fun onDestroy() { if (::web.isInitialized) web.destroy(); super.onDestroy() }
}
