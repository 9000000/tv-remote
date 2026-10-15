package dev.tvremote.desktop

import dev.tvremote.core.HttpServer
import java.io.File
import java.util.concurrent.CountDownLatch

/**
 * The computer's server on the shared Kotlin core (a trial, next to the Python one in app/web): the same page, the same
 * tvs.json, the same pairings, and no Python, adb or ffmpeg. ADB and the remote protocol are spoken by Adb.kt and Atv.kt,
 * the picture goes to the browser as H.264 as TV Setup sends it.
 *
 *   ./gradlew :desktop:run --args="[--tv 192.168.1.88] [--port 8765]"
 *
 * TVS_FILE: another list of TVs (tests); TVR_KEYS: another folder for this computer's keys (tests: a key no TV knows yet).
 */
fun main(args: Array<String>) {
    val opt = args.toList().zipWithNext().filter { it.first.startsWith("--") }.associate { it.first.substring(2) to it.second }
    val port = opt["port"]?.toIntOrNull() ?: 8765
    val repo = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "app/shared/web/index.html").isFile } ?: run { System.err.println("run it inside the tv-remote folder"); return }
    val keys = File(System.getenv("TVR_KEYS") ?: File(repo, ".cache/keys").path)
    Keys.prepare(keys, repo)
    val saved = Saved(File(System.getenv("TVS_FILE") ?: File(repo, "tvs.json").path))
    opt["tv"]?.let { saved.setCurrent(it) }                           // for this run only, unless the page saves something
    val done = CountDownLatch(1)
    val server = Server(repo, keys, File(repo, ".cache/desktop").also { it.mkdirs() }, saved) { done.countDown() }
    val http = HttpServer(server)
    val bound = try { http.start(port, true) } catch (e: java.io.IOException) {
        System.err.println("port $port is taken: is TV Remote already running? ($e)"); return
    }
    println("Remote: http://127.0.0.1:$bound  (TV ${saved.current.ifEmpty { "not chosen yet" }})")
    Runtime.getRuntime().addShutdownHook(Thread { http.stop(); server.close() })   // Ctrl+C, the Mac app's Quit: the TV's sessions end too
    done.await()                                                      // the page's "Tắt" button
    println("Stopping.")
    System.exit(0)
}
