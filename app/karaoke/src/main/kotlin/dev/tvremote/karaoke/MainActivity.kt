package dev.tvremote.karaoke

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import dev.tvremote.core.web.Qr
import dev.tvremote.tvui.TvUi
import dev.tvremote.tvui.dp
import dev.tvremote.tvui.label
import dev.tvremote.tvui.menuButton

/**
 * The TV's one Activity, two layers: the player (the official YouTube IFrame player in a WebView, full screen) and over it the queue
 * screen (QR, now / next, buttons). Which one shows is derived from the state: a song on and not paused = the player; Back or a pause =
 * the queue screen. Keys: [KeyRules]. Nothing is downloaded, recorded or played in the background: the video stops when the screen does.
 */
class MainActivity : Activity() {
    private lateinit var s: Session
    private val h = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private lateinit var queueScreen: LinearLayout
    private lateinit var right: LinearLayout
    private lateinit var hud: TextView
    private lateinit var btnResume: TextView
    private lateinit var btnNext: TextView
    private lateinit var btnKey: TextView
    private lateinit var phones: TextView
    private lateinit var qrBox: LinearLayout
    private var qrUrl = ""
    private var playerShown = false
    @Volatile private var loadedQid = -1
    private var loadedTick = -1
    @Volatile private var playedOnce = false
    private var pageReady = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        s = Session.get(this)
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()) }
        web = makePlayer()?.also { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        hud = label("", 22f).apply {
            typeface = Typeface.DEFAULT_BOLD; setPadding(dp(18), dp(10), dp(18), dp(10)); visibility = View.GONE
            background = GradientDrawable().apply { setColor(0xCC0E1116.toInt()); cornerRadius = dp(12).toFloat() }
        }
        buildQueueScreen()
        root.addView(queueScreen, FrameLayout.LayoutParams(-1, -1))
        root.addView(hud, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(48), 0, dp(48), dp(40)) })
        setContentView(root)
        s.onChange = { render() }
        s.onNotice = { code, title -> toast(getString(when (code) { "embed" -> R.string.skipped_embed; "gone" -> R.string.skipped_gone; else -> R.string.skipped_err }, title)) }
        s.open()
        render()
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        s.onChange = null; s.onNotice = null; s.close()
        web?.destroy()
        super.onDestroy()
    }

    /** Leaving the screen stops the video (no background play): the song waits, paused, where the queue screen shows it. */
    private var stoppedPlaying = false
    override fun onStop() { super.onStop(); if (s.queue.now != null && !s.paused) { stoppedPlaying = true; s.pause(true) } }
    /** Coming back (Home and back in) carries on with the song that the screen leaving paused; a pause the person chose stays. */
    override fun onStart() { super.onStart(); if (stoppedPlaying) { stoppedPlaying = false; s.pause(false) } }

    // ---- the player ----
    @SuppressLint("SetJavaScriptEnabled")
    private fun makePlayer(): WebView? = try {
        WebView(this).apply {
            setBackgroundColor(0xFF000000.toInt()); isFocusable = false
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            addJavascriptInterface(Bridge(), "B")
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean { return true }
            }
            loadDataWithBaseURL(EMBED_ORIGIN + "/", PLAYER_HTML.replace("__ORIGIN__", EMBED_ORIGIN), "text/html", "utf-8", null)
        }
    } catch (e: Exception) { null }

    private inner class Bridge {
        @JavascriptInterface fun ready() { h.post { pageReady = true; render() } }
        @JavascriptInterface fun state(c: Int) {
            val q = loadedQid
            if (c == 1) playedOnce = true
            if (c == 0) s.ended(q)
        }
        @JavascriptInterface fun error(c: Int) {
            android.util.Log.w("karaoke", "player error $c")
            s.failed(loadedQid, when (c) { 101, 150, 152, 153 -> "embed"; 100 -> "gone"; else -> "err" })
        }
    }

    private fun js(code: String) { web?.evaluateJavascript(code, null) }

    // ---- state -> screen ----
    private fun render() {
        val now = s.queue.now
        val tick = s.queue.replayTick
        if (now == null) {
            loadedQid = -1; if (pageReady) js("stopAll()")
        } else if (pageReady && (now.qid != loadedQid || tick != loadedTick)) {
            loadedQid = now.qid; loadedTick = tick; playedOnce = false
            js("loadSong('${now.song.vid}')")
            val q = now.qid
            h.postDelayed({ if (loadedQid == q && !playedOnce && !s.paused && playerShown) s.failed(q, "err") }, 30_000)   // never started (no network, page blocked): on to the next
            showHud("${now.song.title}  ·  ${now.by}", 7000)
        }
        val shown = now != null && !s.paused && web != null
        if (shown != playerShown) { playerShown = shown; if (!shown) h.removeCallbacks(hideHud); hud.visibility = if (shown) hud.visibility else View.GONE }
        queueScreen.visibility = if (playerShown) View.GONE else View.VISIBLE
        if (pageReady) js("setPaused(${!playerShown})")
        if (!playerShown) fillQueueScreen()
    }

    private val hideHud = Runnable { hud.visibility = View.GONE }
    private fun showHud(text: String, ms: Long) { hud.text = text; hud.visibility = View.VISIBLE; h.removeCallbacks(hideHud); h.postDelayed(hideHud, ms) }
    private fun toast(t: String) { Toast.makeText(this, t, Toast.LENGTH_LONG).show(); if (playerShown) showHud(t, 6000) }

    // ---- keys ----
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
            val now = s.queue.now != null
            when (KeyRules.decide(playerShown, e.keyCode, s.paused, now)) {
                KeyRules.Do.PAUSE, KeyRules.Do.QUEUE -> { if (now) s.pause(true) else render(); return true }
                KeyRules.Do.RESUME -> { s.pause(false); return true }
                KeyRules.Do.NEXT -> { s.skip(); return true }
                KeyRules.Do.EXIT -> { finish(); return true }
                KeyRules.Do.NONE -> {}
            }
        }
        if (playerShown && e.keyCode != KeyEvent.KEYCODE_VOLUME_UP && e.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && e.keyCode != KeyEvent.KEYCODE_VOLUME_MUTE) return true   // nothing else acts on the player
        return super.dispatchKeyEvent(e)
    }

    // ---- the queue screen ----
    private fun buildQueueScreen() {
        queueScreen = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(TvUi.BG); setPadding(dp(48), dp(27), dp(48), dp(20)) }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(ImageView(this).apply { setImageResource(R.drawable.icon); layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) } })
        head.addView(label(getString(R.string.app_name), 24f, TvUi.TEXT).apply { typeface = Typeface.DEFAULT_BOLD })
        left.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        left.addView(label(getString(R.string.scan), 18f, TvUi.TEXT).apply { typeface = Typeface.DEFAULT_BOLD })
        qrBox = LinearLayout(this).apply { gravity = Gravity.CENTER }
        left.addView(qrBox, LinearLayout.LayoutParams(dp(170), dp(170)).apply { topMargin = dp(8); bottomMargin = dp(8) })
        phones = label("", 13f, TvUi.DIM)
        left.addView(phones)
        btnResume = menuButton(getString(R.string.btn_resume), 260, 44, 16f) { s.pause(false) }
        btnNext = menuButton(getString(R.string.btn_next), 260, 44, 16f) { s.skip() }
        btnKey = menuButton(getString(R.string.btn_key), 260, 44, 16f) { keySheet() }
        left.addView(btnResume); left.addView(btnNext); left.addView(btnKey)
        queueScreen.addView(left, LinearLayout.LayoutParams(dp(270), -1))
        right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        queueScreen.addView(right, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(36) })
    }

    private fun fillQueueScreen() {
        val url = s.url()
        if (url != qrUrl) {
            qrUrl = url; qrBox.removeAllViews()
            Qr.bitmap(url, 8)?.let { bmp -> qrBox.addView(ImageView(this).apply { setImageDrawable(android.graphics.drawable.BitmapDrawable(resources, bmp).apply { isFilterBitmap = false }); scaleType = ImageView.ScaleType.FIT_XY }, LinearLayout.LayoutParams(-1, -1)) }
        }
        phones.text = url.substringBefore("/?k=").removePrefix("http://") + "  ·  " + getString(R.string.phones, countPhones())
        val now = s.queue.now
        btnResume.visibility = if (now != null) View.VISIBLE else View.GONE
        btnNext.visibility = if (now != null) View.VISIBLE else View.GONE
        right.removeAllViews()
        right.addView(label(getString(R.string.now_singing), 16f, TvUi.DIM).apply { typeface = Typeface.DEFAULT_BOLD })
        right.addView(songCard(now), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(16) })
        val up = s.queue.upcoming
        right.addView(label(getString(R.string.up_next) + if (up.isNotEmpty()) "  ·  ${up.size}" else "", 16f, TvUi.DIM).apply { typeface = Typeface.DEFAULT_BOLD })
        if (up.isEmpty() && now == null) right.addView(label(getString(R.string.empty), 18f, TvUi.DIM).apply { setPadding(0, dp(8), 0, 0) })
        up.take(MAX_ROWS).forEachIndexed { i, e -> right.addView(row(i + 1, e), LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(6) }) }
        if (up.size > MAX_ROWS) right.addView(label(getString(R.string.more, up.size - MAX_ROWS), 14f, TvUi.DIM).apply { setPadding(dp(8), dp(6), 0, 0) })
        right.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))
        right.addView(label(getString(R.string.mic_note), 13f, TvUi.DIM).apply { maxLines = 2; gravity = Gravity.START })
        if (btnResume.visibility == View.VISIBLE) { if (queueScreen.findFocus() == null) btnResume.requestFocus() } else if (queueScreen.findFocus() == null) btnKey.requestFocus()
    }

    private fun countPhones(): Int = s.stateJson().let { try { org.json.JSONObject(it).optInt("users") } catch (e: Exception) { 0 } }

    private fun songCard(e: Entry?): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14))
        background = GradientDrawable().apply { setColor(TvUi.CARD); cornerRadius = dp(16).toFloat() }
        if (e == null) { addView(label(getString(R.string.idle_now), 20f, TvUi.DIM)); return@apply }
        addView(thumb(e.song.thumb, 192, 108))
        val t = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        t.addView(label(e.song.title, 22f, TvUi.TEXT).apply { typeface = Typeface.DEFAULT_BOLD; maxLines = 2; gravity = Gravity.START; ellipsize = android.text.TextUtils.TruncateAt.END })
        t.addView(label(getString(R.string.added_by, e.by) + if (s.paused) "  ·  " + getString(R.string.paused) else "", 15f, TvUi.DIM).apply { gravity = Gravity.START; setPadding(0, dp(6), 0, 0) })
        addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })
    }

    private fun row(n: Int, e: Entry): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(4), dp(12), dp(4))
        background = GradientDrawable().apply { setColor(TvUi.FIELD_REST); cornerRadius = dp(12).toFloat() }
        addView(label("$n", 15f, TvUi.DIM).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(28), -2))
        addView(thumb(e.song.thumb, 72, 40))
        val t = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        t.addView(label(e.song.title, 15f, TvUi.TEXT).apply { gravity = Gravity.START; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        t.addView(label(e.by, 12f, TvUi.DIM).apply { gravity = Gravity.START; maxLines = 1 })
        addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
    }

    private fun thumb(url: String, w: Int, hh: Int) = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xFF2A3042.toInt()); layoutParams = LinearLayout.LayoutParams(dp(w), dp(hh))
        Thumbs.into(this, url)
    }

    // ---- the API key, from the TV ----
    private fun keySheet() {
        val (dlg, box) = TvUi.sheet(this, R.style.TvSheet, 520)
        TvUi.sheetTitle(box, getString(R.string.key_title))
        TvUi.sheetNote(box, getString(R.string.key_note))
        TvUi.sheetNote(box, if (s.apiKey.isEmpty()) getString(R.string.key_state_off)
            else getString(R.string.key_state_on, when (s.lastSource) { "api" -> getString(R.string.src_api); "web" -> getString(R.string.src_web); else -> getString(R.string.src_none) }))
        val field = TvUi.field(this, getString(R.string.key_hint), 16f, EditorInfo.IME_ACTION_DONE, TvUi.FIELD_REST, 12f, 14, 10)
        box.addView(field, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(4), dp(4), dp(4), dp(10)) })
        val save = { v: String -> s.apiKey = v; dlg.dismiss(); Toast.makeText(this, getString(if (v.isBlank()) R.string.key_cleared else R.string.key_saved), Toast.LENGTH_SHORT).show() }
        field.setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_DONE) { save(field.text.toString()); true } else false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(TvUi.button(this, getString(R.string.key_save)) { save(field.text.toString()) })
        if (s.apiKey.isNotEmpty()) row.addView(TvUi.button(this, getString(R.string.key_clear)) { save("") }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
        row.addView(TvUi.button(this, getString(R.string.key_cancel)) { dlg.dismiss() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
        box.addView(row)
        dlg.show()
    }

    companion object {
        private const val MAX_ROWS = 3
        /** The page that holds the player: YouTube wants a real https origin as its referrer (none = error 153; its own site = 152). */
        private const val EMBED_ORIGIN = "https://tvremote.pages.dev"

        /** The official IFrame Player API: controls off (the phones are the remote), ads and all as YouTube serves them. */
        private const val PLAYER_HTML = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:fixed;inset:0;width:100%;height:100%}</style></head>
<body><div id="p"></div><script>
var pl=null,ok=false,want=null,pausedWanted=false;
function onYouTubeIframeAPIReady(){pl=new YT.Player('p',{width:'100%',height:'100%',playerVars:{autoplay:1,controls:0,rel:0,playsinline:1,fs:0,disablekb:1,iv_load_policy:3,origin:'__ORIGIN__'},
 events:{onReady:function(){ok=true;B.ready();if(want)loadSong(want)},onStateChange:function(e){B.state(e.data)},onError:function(e){B.error(e.data)}}})}
function loadSong(id){want=id;if(!ok)return;pl.loadVideoById(id);if(pausedWanted)pl.pauseVideo()}
function setPaused(p){pausedWanted=p;if(!ok)return;var st=pl.getPlayerState();if(p&&st===1)pl.pauseVideo();else if(!p&&st===2)pl.playVideo()}
function stopAll(){want=null;if(ok)pl.stopVideo()}
</script><script src="https://www.youtube.com/iframe_api"></script></body></html>"""
    }
}
