package dev.tvremote.tvui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper

/**
 * A TV with its WebView switched off or removed for the user (crash 2da5cf8b, 80296222): WebView() throws there. The screen asks
 * TV Setup to switch it back on over ADB (its HomeInfo "webview", answered only to our own apps by key) and starts over; nothing
 * to switch on, or no TV Setup: Play Store's "Android System WebView" page, Try again and Back.
 */
object NoWebView {
    private const val SETUP = "content://dev.tvremote.setup.home"
    private const val PKG = "com.google.android.webview"

    /** Loads the TV's WebView provider: a TV without one throws here, where it can be caught, not in WebView(). */
    fun ok(c: Context) = try { android.webkit.WebSettings.getDefaultUserAgent(c); true } catch (e: Exception) { false }

    /** TV Setup's answer: fine / fixed / none / noadb / failed, or null without a TV Setup that knows the word. Blocks. */
    private fun ask(c: Context): String? = try { c.contentResolver.call(Uri.parse(SETUP), "webview", null, null)?.getString("result") } catch (e: Exception) { null }

    /** No screen of its own (TV Home's web wall: the gradient stays meanwhile): asks TV Setup once per process, [done] on the main thread with true once a WebView works. */
    fun fix(c: Context, done: (Boolean) -> Unit) {
        if (asked) return; asked = true                               // fine / none / noadb do not change in a minute: no loop on every wall redraw
        val ui = Handler(Looper.getMainLooper())
        Thread({ ask(c); ui.post { done(ok(c)) } }, "webview").apply { isDaemon = true }.start()
    }
    @Volatile private var asked = false

    /** Fills [a] with the repair screen; [again] runs once a WebView works (usually `recreate()`). */
    fun show(a: Activity, again: () -> Unit) {
        val state = TvState(a)
        a.setContentView(state)
        state.loading(w(a, "wv_fixing"))
        val ui = Handler(Looper.getMainLooper())
        Thread({
            val r = ask(a)
            ui.post {
                if (a.isFinishing) return@post
                if (ok(a)) { again(); return@post }
                state.buttons.removeAllViews()
                state.add(TvUi.button(a, w(a, "wv_store")) { store(a) })
                state.add(TvUi.button(a, w(a, "wv_retry")) { show(a, again) })
                state.add(TvUi.button(a, w(a, "wv_back")) { a.finish() })
                state.message(w(a, if (r == null) "wv_none_setup" else "wv_none"))
            }
        }, "webview").apply { isDaemon = true }.start()
    }

    /** tvui/res/values(-vi)/webview.xml: compiled into each app under its own namespace, so there is no one R to name. */
    private fun w(a: Activity, id: String) = a.getString(a.resources.getIdentifier(id, "string", a.packageName))

    private fun store(a: Activity) {
        for (u in listOf("market://details?id=$PKG", "https://play.google.com/store/apps/details?id=$PKG"))
            try { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: Exception) { }
    }
}
