package dev.tvremote.desktop

import dev.tvremote.core.HttpServer
import java.io.File
import java.io.PrintStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch

/**
 * The computer's server on the shared Kotlin core (a trial, next to the Python one in app/web): the same page, the same
 * tvs.json, the same pairings, and no Python, adb or ffmpeg. ADB and the remote protocol are spoken by Adb.kt and Atv.kt,
 * the picture goes to the browser as H.264 as TV Setup sends it.
 *
 *   ./gradlew :desktop:run --args="[--tv 192.168.1.88] [--port 8765]"     (inside the tv-remote folder: its tvs.json)
 *   ./gradlew :desktop:packageApp                                          (TV Remote.app with its own Java, see build.gradle.kts)
 *
 * The packaged app (system property tvr.res: the page and the files it needs, inside the app) keeps its TVs and keys in
 * the user's app data folder, taken over once from the tv-remote folder the Python app last ran from.
 * TVS_FILE: another list of TVs (tests); TVR_KEYS: another folder for this computer's keys (tests: a key no TV knows yet).
 */
fun main(args: Array<String>) {
    val opt = args.toList().zipWithNext().filter { it.first.startsWith("--") }.associate { it.first.substring(2) to it.second }
    val port = opt["port"]?.toIntOrNull() ?: 8765
    val res = System.getProperty("tvr.res")?.let { File(it) }
    val packaged = res != null
    val repo = res ?: generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "app/shared/web/index.html").isFile } ?: run { System.err.println("run it inside the tv-remote folder"); kotlin.system.exitProcess(2) }
    val data = if (packaged) App.data().also { it.mkdirs() } else repo
    if (packaged) File(data, "server.log").let { System.setOut(PrintStream(it.outputStream(), true)); System.setErr(System.out) }   // nobody sees a terminal
    val url = "http://127.0.0.1:$port"
    if (packaged && App.answers(url)) { App.browse(url); return }     // already running (or the Python server): just its page
    val legacy = if (packaged) App.pythonFolder() else repo           // where the Python server kept its pairings and TVs
    val keys = File(System.getenv("TVR_KEYS") ?: (if (packaged) File(data, "keys") else File(repo, ".cache/keys")).path)
    Keys.prepare(keys, legacy)
    val tvs = File(System.getenv("TVS_FILE") ?: File(data, "tvs.json").path)
    if (packaged && !tvs.exists() && legacy != null && File(legacy, "tvs.json").isFile) {
        File(legacy, "tvs.json").copyTo(tvs)                          // a copy: the Python server keeps its own list
        tvs.setReadable(false, false); tvs.setReadable(true, true)    // it holds TV Setup passes
    }
    val saved = Saved(tvs)
    val first = opt["tv"]?.trim()?.also { if (!Server.IP.matches(it)) { System.err.println("--tv: not an IPv4 address: $it"); kotlin.system.exitProcess(2) } }
    val done = CountDownLatch(1)
    val server = Server(repo, keys, File(data, if (packaged) "cache" else ".cache/desktop").also { it.mkdirs() }, saved, first) { done.countDown() }
    val http = HttpServer(server)
    val bound = try { http.start(port, true) } catch (e: java.io.IOException) {
        System.err.println("port $port is taken: is TV Remote already running? ($e)")
        if (packaged) App.alert("Cổng $port đang bị một chương trình khác dùng nên TV Remote chưa chạy được. Tắt chương trình đó rồi mở lại.\n\n" +
            "Port $port is used by another program, so TV Remote cannot start. Quit that program and open TV Remote again.")
        return
    }
    println("Remote: http://127.0.0.1:$bound  (TV ${(first ?: saved.current).ifEmpty { "not chosen yet" }})")
    Runtime.getRuntime().addShutdownHook(Thread { http.stop(); server.close() })   // Ctrl+C, logging out: the TV's sessions end too
    if (packaged) { App.onReopen { println("opened again"); App.browse(url) }; App.browse(url) }   // opening the app again while it runs: the page again
    done.await()                                                      // the page's "Tắt" button
    println("Stopping.")
    System.exit(0)
}

/** What only the packaged app needs: where its data goes, the browser, the Python app's folder, a message box. */
object App {
    private val os = System.getProperty("os.name").lowercase()
    private val home = System.getProperty("user.home")

    fun data(): File = when {
        "mac" in os -> File(home, "Library/Application Support/TV Remote")   // the Python app keeps its "folder" note there too
        "win" in os -> File(System.getenv("APPDATA") ?: home, "TV Remote")
        else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "tv-remote")
    }

    /** The tv-remote folder the Python app (TV Remote.app in the repo) last ran from: its tvs.json, cert.pem and key.pem. */
    fun pythonFolder(): File? = try {
        File(File(home, "Library/Application Support/TV Remote/folder").readText().trim()).takeIf { File(it, "app/web/server.py").isFile }
    } catch (e: Exception) {
        null
    }

    fun answers(url: String) = try {
        (URL("$url/api/about").openConnection() as HttpURLConnection).run { connectTimeout = 1000; readTimeout = 2000; responseCode == 200 }
    } catch (e: Exception) {
        false
    }

    fun browse(url: String) {
        try {
            when {
                "mac" in os -> ProcessBuilder("open", url).start()
                "win" in os -> ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start()
                else -> ProcessBuilder("xdg-open", url).start()
            }
        } catch (e: Exception) {
            println("open $url in a browser ($e)")
        }
    }

    /** macOS sends a running app "reopen" when it is opened again; AWT is the only way to hear it from Java. */
    fun onReopen(go: () -> Unit) {
        if ("mac" !in os) return
        try {
            java.awt.Desktop.getDesktop().addAppEventListener(java.awt.desktop.AppReopenedListener { go() })
        } catch (e: Throwable) {
            println("no reopen events: $e")
        }
    }

    fun alert(text: String) {
        if ("mac" !in os) return
        try {
            ProcessBuilder("osascript", "-e", "display dialog \"${text.replace("\"", "'")}\" buttons {\"OK\"} default button 1 with title \"TV Remote\" with icon caution").start().waitFor()
        } catch (e: Exception) { }
    }
}
