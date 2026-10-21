package dev.tvremote.games

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject

/**
 * A web game: a page in a full-screen WebView. The phones drive it three ways at once (WEBGAMES.md):
 *  - keyboard, player 1 only: D-pad = arrows, A = Z, B = X, X = A, Y = S, L = Q, R = W, L2 = E, R2 = R, C = C, D = V, Start = Enter, Select = Shift
 *    (the TV remote's own keys reach the page as they are: arrows, OK = Enter);
 *  - the Gamepad API: navigator.getGamepads() gives one standard-mapping pad per phone (tvpad.js);
 *  - window.tvpad: join / leave / button / text events, layout(), vibrate(), say().
 * The bridge and the shim exist only for the page the game was opened with (its origin); anything else the page navigates to gets neither.
 */
class WebGameActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var server: GameServer
    private lateinit var cards: GameCards
    private var origin = ""
    @Volatile private var pageOrigin = ""
    private var players = listOf<Int>()
    private val nameOf = GameServer.KEYS.entries.associate { it.value to it.key }
    private val keyOf = mapOf(
        "UP" to KeyEvent.KEYCODE_DPAD_UP, "DOWN" to KeyEvent.KEYCODE_DPAD_DOWN, "LEFT" to KeyEvent.KEYCODE_DPAD_LEFT, "RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "A" to KeyEvent.KEYCODE_Z, "B" to KeyEvent.KEYCODE_X, "X" to KeyEvent.KEYCODE_A, "Y" to KeyEvent.KEYCODE_S,
        "L" to KeyEvent.KEYCODE_Q, "R" to KeyEvent.KEYCODE_W, "C" to KeyEvent.KEYCODE_C, "D" to KeyEvent.KEYCODE_V,
        "L2" to KeyEvent.KEYCODE_E, "R2" to KeyEvent.KEYCODE_R, "START" to KeyEvent.KEYCODE_ENTER, "SELECT" to KeyEvent.KEYCODE_SHIFT_LEFT,
    )

    // no ":-1" for a link without a port: that crashed the origin rule
    private fun originOf(u: String): String = try { val x = java.net.URI(u); "${x.scheme}://${x.host}${if (x.port > 0) ":${x.port}" else ""}" } catch (e: Exception) { "" }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra("name") ?: "Web"
        val link = intent.getStringExtra("url") ?: return finish()
        server = GameServer.acquire(this)
        val builtin = link.startsWith("builtin:")
        val url = if (builtin) "${server.base()}/webgames/${link.removePrefix("builtin:")}/?k=${server.token}" else link
        origin = originOf(url)
        val shim = assets.open("tvpad.js").use { String(it.readBytes()) }

        web = WebView(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true; mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false; allowContentAccess = false; mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            addJavascriptInterface(Bridge(), "TVPadNative")
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean { Log.d(TAG, "console ${m.messageLevel()}: ${m.message()} (${m.sourceId().substringAfterLast('/')}:${m.lineNumber()})"); return true }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(v: WebView, u: String, f: Bitmap?) {
                    pageOrigin = originOf(u)
                    // no document-start support (old WebView): the shim goes in as the page starts, which is early enough for most pages
                    if (pageOrigin == origin && !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) v.evaluateJavascript(shim, null)
                }
            }
            // WebViewCompat can limit the script to the game's origin; the bridge itself also checks pageOrigin on every call
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) WebViewCompat.addDocumentStartJavaScript(this, shim, setOf(origin))
            isFocusable = true; isFocusableInTouchMode = true
        }
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()); addView(web, FrameLayout.LayoutParams(-1, -1)) }
        setContentView(root)
        cards = GameCards(this, root, server, name, if (builtin) null else url,
            onPause = { p -> if (p) { web.onPause(); web.pauseTimers() } else { web.onResume(); web.resumeTimers(); web.requestFocus() } },
            onSave = null, onLoad = null, onQuit = { finish() }, alone = false)
        web.loadUrl(url)
    }

    override fun onResume() {
        super.onResume()
        if (!::server.isInitialized) return
        server.inGame = true; server.webMode = true
        server.buttons = listOf("A", "B", "START", "SELECT")
        server.clearLayouts(); server.setSystem(intent.getStringExtra("pad")?.takeIf { it.isNotEmpty() } ?: "web"); server.analog = true
        server.onAxis = { port, x, y -> runOnUiThread { axis(port, x, y) } }
        server.onKey = { port, action, code -> runOnUiThread { input(port, action == KeyEvent.ACTION_DOWN, nameOf[code] ?: return@runOnUiThread) } }
        server.onKeyLat = { port, action, code, lat -> runOnUiThread { input(port, action == KeyEvent.ACTION_DOWN, nameOf[code] ?: return@runOnUiThread, lat) } }
        server.onPing = { p, ms -> runOnUiThread { cards.setPing(p, ms); js("__tvpadIn('ping',$p,$ms)") } }   // the cards' labels and the page
        for (p in 1..4) server.pingOf(p).let { if (it >= 0) js("__tvpadIn('ping',$p,$it)") }
        server.onText = { p, t -> runOnUiThread { js("__tvpadIn('text',$p,${JSONObject.quote(t)})") } }
        server.onPlayers = { l -> runOnUiThread { cards.setPlayers(l); diff(l) } }
        server.onAdded = { }
    }

    override fun onPause() { if (::server.isInitialized) server.onKeyLat = null; super.onPause() }

    override fun onDestroy() {
        if (::web.isInitialized) { web.removeJavascriptInterface("TVPadNative"); web.destroy() }
        if (::server.isInitialized) { server.webMode = false; server.clearLayouts(); GameServer.release() }
        super.onDestroy()
    }

    private fun js(code: String) { if (pageOrigin == origin) web.evaluateJavascript("window.__tvpadIn&&$code", null) }

    private fun diff(now: List<Int>) {
        (now - players.toSet()).forEach { js("__tvpadIn('join',$it)") }
        (players - now.toSet()).forEach { js("__tvpadIn('leave',$it)") }
        players = now
    }

    private val held = HashMap<Int, Set<String>>()
    /** A phone's stick: the Gamepad API axes 0 / 1 of its pad, and for player 1 also the arrow keys. */
    private fun axis(port: Int, x: Float, y: Float) {
        js("__tvpadIn('axis',${port + 1},$x,$y)")
        if (port != 0) return
        val now = GameServer.dirs(x, y); val before = held[0] ?: emptySet(); held[0] = now
        (before - now).forEach { keyOf[it]?.let { c -> web.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, c)) } }
        (now - before).forEach { keyOf[it]?.let { c -> web.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, c)) } }
    }

    /** One pad button of player port+1: to the Gamepad API / tvpad, and for player 1 also as a key. */
    private fun input(port: Int, down: Boolean, name: String, lat: Int = -1) {
        js("__tvpadIn('button',${port + 1},'$name',$down,$lat)")
        if (port == 0) keyOf[name]?.let { web.dispatchKeyEvent(KeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, it)) }
    }

    inner class Bridge {
        private fun ok() = pageOrigin == origin
        @JavascriptInterface fun players(): String = JSONArray(players).toString()
        @JavascriptInterface fun ping(player: Int): Int = server.pingOf(player)
        @JavascriptInterface fun layout(player: Int, json: String) { if (ok()) server.setLayout(player, json) }
        @JavascriptInterface fun vibrate(player: Int, ms: Int) { if (ok()) server.vibrate(player, ms) }
        @JavascriptInterface fun say(player: Int, text: String) { if (ok()) server.say(player, text) }
    }

    private fun isPad(ev: KeyEvent) = ev.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK) != 0

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        if (cards.key(ev)) return true
        if (cards.paused) return super.dispatchKeyEvent(ev)
        if (isPad(ev) && nameOf.containsKey(ev.keyCode)) {                 // a physical pad: its own slot, like a phone
            if (ev.repeatCount == 0) { val port = server.padPort(ev.deviceId); if (port >= 0) input(port, ev.action == KeyEvent.ACTION_DOWN, nameOf[ev.keyCode]!!) }
            return true
        }
        if (!web.hasFocus()) web.requestFocus()
        return super.dispatchKeyEvent(ev)                                    // the TV remote goes to the page as it is
    }

    companion object { private const val TAG = "WebGame" }
}
