package dev.tvremote.desktop

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.WString
import dev.tvremote.core.T
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The app's own window: the system's web view showing the page (webview, built by native/build.sh: WebKit on Mac, WebView2
 * on Windows, WebKitGTK on Linux), loaded with JNA. What a browser does and a bare web view does not is done here: links to
 * other sites open in the browser, and downloads (a screenshot, the scenes backup) are saved to the Downloads folder.
 */
object Window {
    @Suppress("FunctionName")
    private interface Lib : Library {
        fun webview_create(debug: Int, window: Pointer?): Pointer?
        fun webview_destroy(w: Pointer): Int
        fun webview_run(w: Pointer): Int
        fun webview_terminate(w: Pointer): Int
        fun webview_dispatch(w: Pointer, fn: Dispatch, arg: Pointer?): Int
        fun webview_get_window(w: Pointer): Pointer?
        fun webview_set_title(w: Pointer, title: String): Int
        fun webview_set_size(w: Pointer, width: Int, height: Int, hints: Int): Int
        fun webview_navigate(w: Pointer, url: String): Int
        fun webview_init(w: Pointer, js: String): Int
        fun webview_eval(w: Pointer, js: String): Int
        fun webview_bind(w: Pointer, name: String, fn: Bound, arg: Pointer?): Int
        fun webview_return(w: Pointer, id: String, status: Int, result: String): Int
        fun tvr_mac_setup(w: Pointer)                                 // native/mac.m: dialogs, menus, the window's place
    }

    fun interface Bound : Callback { fun invoke(id: String, req: String, arg: Pointer?) }
    fun interface Dispatch : Callback { fun invoke(w: Pointer?, arg: Pointer?) }

    private val os = System.getProperty("os.name").lowercase()
    private val keep = ArrayList<Callback>()                          // JNA forgets a callback the JVM has collected: crash
    @Volatile private var lib: Lib? = null
    @Volatile private var view: Pointer? = null

    /** native/<this system>/<the library>, in the repo or inside the app (the same layout). */
    fun library(root: File): File {
        val arch = if (System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }) "arm64" else "x64"
        return File(root, "app/desktop/native/" + when {
            "mac" in os -> "mac/libwebview.dylib"
            "win" in os -> "windows-$arch/webview.dll"
            else -> "linux-$arch/libwebview.so"
        })
    }

    /**
     * Shows [url] until the window is closed, on the thread that started the app (Cocoa wants the first one: the app runs with
     * -XstartOnFirstThread). false: this system has no web view to show it in (no WebKitGTK on a Linux, no WebView2 runtime
     * on an old Windows), nothing was shown.
     */
    fun show(root: File, url: String, pass: String): Boolean {
        val base = url.substringBefore("/?")
        val l = try {
            Native.load(library(root).path, Lib::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
        } catch (e: Throwable) {
            println("no window, the browser instead: $e")
            return false
        }
        val w = l.webview_create(0, null) ?: run { println("no window, the browser instead: webview_create failed"); return false }
        lib = l; view = w
        l.webview_set_title(w, "TV Remote")
        l.webview_set_size(w, 1100, 800, 0)
        when {
            "mac" in os -> l.tvr_mac_setup(w)
            "win" in os -> l.webview_get_window(w)?.let { winIcon(it) }
            else -> l.webview_get_window(w)?.let { gtkIcon(it, File(root, "app/shared/web/icon-512.png")) }
        }
        bind(l, w, "tvrOpen") { a -> a.optString(0).takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { App.browse(it) } }
        bind(l, w, "tvrSave") { a -> Thread({ save(base, pass, a.optString(0), a.optString(1)) }, "save").apply { isDaemon = true }.start() }
        l.webview_init(w, PAGE)
        l.webview_navigate(w, url)
        l.webview_run(w)
        view = null
        l.webview_destroy(w)
        return true
    }

    /** Closes the window from any thread (the page's "Tắt"): [show] returns. */
    fun close() {
        val l = lib ?: return
        val w = view ?: return
        val stop = Dispatch { p, _ -> if (p != null) l.webview_terminate(p) }
        synchronized(keep) { keep.add(stop) }
        l.webview_dispatch(w, stop, null)
    }

    /** Runs JavaScript in the page from any thread. */
    private fun eval(js: String) {
        val l = lib ?: return
        val w = view ?: return
        val go = Dispatch { p, _ -> if (p != null) l.webview_eval(p, js) }
        synchronized(keep) { keep.add(go) }
        l.webview_dispatch(w, go, null)
    }

    /** window.[name](...) in the page: answered at once, the work (if long) goes on in a thread. */
    private fun bind(l: Lib, w: Pointer, name: String, work: (JSONArray) -> Unit) {
        val fn = Bound { id, req, _ ->
            try { work(JSONArray(req)) } catch (e: Exception) { println("$name: $e") }
            l.webview_return(w, id, 0, "null")
        }
        synchronized(keep) { keep.add(fn) }
        l.webview_bind(w, name, fn, null)
    }

    /** A download the page asked for: fetched from this server again and written to Downloads, under a name not yet taken. */
    private fun save(base: String, pass: String, path: String, asked: String) {
        try {
            if (!path.startsWith("/")) return
            val c = URL(base + path).openConnection() as HttpURLConnection
            c.setRequestProperty(Pass.HEADER, pass)
            c.readTimeout = 60000
            val given = Regex("filename=\"?([^\";]+)").find(c.getHeaderField("Content-Disposition") ?: "")?.groupValues?.get(1)
            val name = (asked.ifEmpty { given ?: path.substringAfterLast('/').substringBefore('?') }).replace(Regex("[\\\\/:*?\"<>|]"), "_").ifEmpty { "tv-remote" }
            val dir = File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory } ?: File(System.getProperty("user.home"))
            var f = File(dir, name)
            var n = 1
            while (f.exists()) f = File(dir, name.substringBeforeLast('.') + " (${n++})" + (if ('.' in name) "." + name.substringAfterLast('.') else ""))
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            c.inputStream.use { i -> f.outputStream().use { i.copyTo(it) } }
            eval("toast(" + JSONArray().put(T("pc.saved_file", "path" to f.path.replace(System.getProperty("user.home"), "~"))).toString().trim('[', ']') + ")")
        } catch (e: Exception) {
            println("save $path: $e")
            eval("toast(" + JSONArray().put(T("pc.save_failed", "why" to e.message)).toString().trim('[', ']') + ")")
        }
    }

    /** The window's icon on Windows: the one inside the .exe (jpackage put it there), for the title bar and the taskbar. */
    private fun winIcon(hwnd: Pointer) {
        try {
            val exe = ProcessHandle.current().info().command().orElse(null) ?: return
            val icon = NativeLibrary.getInstance("shell32").getFunction("ExtractIconW").invoke(Pointer::class.java, arrayOf(null, WString(exe), 0)) as Pointer?
            val send = NativeLibrary.getInstance("user32").getFunction("SendMessageW")
            if (icon != null) for (size in 0..1) send.invoke(Pointer::class.java, arrayOf(hwnd, 0x80, size.toLong(), icon))   // WM_SETICON small, big
        } catch (e: Throwable) {
            println("window icon: $e")
        }
    }

    /** Linux: GTK shows no icon unless told. */
    private fun gtkIcon(window: Pointer, png: File) {
        try {
            NativeLibrary.getInstance("gtk-3").getFunction("gtk_window_set_icon_from_file").invoke(Int::class.java, arrayOf(window, png.path, null))
        } catch (e: Throwable) {
            println("window icon: $e")
        }
    }

    // In the page before its own scripts: what has to leave the web view goes through the two functions bound above.
    private val PAGE = """
        (() => {
          const away = (u) => { try { const x = new URL(u, location.href); return x.origin !== location.origin ? x.href : null; } catch { return null; } };
          const take = (a) => {
            if (!a.href) return false;
            const out = away(a.href);
            if (out) { window.tvrOpen(out); return true; }
            if (a.hasAttribute('download')) { const x = new URL(a.href, location.href); window.tvrSave(x.pathname + x.search, a.getAttribute('download') || ''); return true; }
            if (a.target === '_blank') { window.tvrOpen(a.href); return true; }
            return false;
          };
          document.addEventListener('click', (e) => { const a = e.target.closest && e.target.closest('a[href]'); if (a && take(a)) e.preventDefault(); }, true);
          const click = HTMLAnchorElement.prototype.click;                 // a link made only to be clicked, never added to the page
          HTMLAnchorElement.prototype.click = function () { if (this.isConnected || !take(this)) click.call(this); };
          window.open = (u) => { if (u) window.tvrOpen(new URL(u, location.href).href); return null; };
        })();
    """.trimIndent()
}
