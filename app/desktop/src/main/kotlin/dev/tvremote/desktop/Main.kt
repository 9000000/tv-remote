package dev.tvremote.desktop

import dev.tvremote.core.AdbLink
import dev.tvremote.core.HttpServer
import dev.tvremote.core.Manage
import dev.tvremote.core.WebPage
import org.json.JSONObject
import java.io.File

/**
 * A trial of the computer's server on the shared Kotlin core: one TV, the page from app/shared/web, and every /api route that
 * Manage.kt answers for TV Setup and the phone. ADB is spoken by Adb.kt itself, so neither the adb program nor Python is needed.
 *
 *   ./gradlew :desktop:run --args="--tv 192.168.1.88 [--port 8768]"
 *
 * The page is told it talks to the "phone" core for now: that core is the one that, like this, manages one TV with Manage.kt.
 */
fun main(args: Array<String>) {
    val opt = args.toList().zipWithNext().filter { it.first.startsWith("--") }.associate { it.first.substring(2) to it.second }
    val tv = opt["tv"] ?: run { System.err.println("usage: --tv <TV's IP> [--port 8768]"); return }
    val port = opt["port"]?.toIntOrNull() ?: 8768
    val repo = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "app/shared/web/index.html").isFile } ?: run { System.err.println("run it inside the tv-remote folder"); return }
    val web = File(repo, "app/shared/web")
    // its own key, as each app has: the TV asks "Allow USB debugging?" once for this computer's Kotlin server
    val home = File(System.getProperty("user.home"), ".tvremote").also { it.mkdirs() }
    val link = AdbLink(home, tv)
    val manage = Manage(link, null, File(home, "cache").also { it.mkdirs() })
    val version = File(repo, "VERSION").readText().trim()
    val page = WebPage({ name -> File(web, name).inputStream() }, "phone", version + "-" + System.currentTimeMillis() / 1000)

    val server = HttpServer { r ->
        page.serve(r.path)?.let { return@HttpServer it }
        if (r.path == "/api/state" || r.path == "/api/about")
            return@HttpServer HttpServer.Response.json(JSONObject().put("core", "phone").put("host", tv).put("name", tv).put("connected", true).put("is_on", true)
                .put("paired", true).put("adb", JSONObject().put("state", manage.adbState())).put("app_name", "TV Remote").put("version", version).put("released", ""))
        if (r.path == "/api/scenes") return@HttpServer HttpServer.Response.json(JSONObject().put("scenes", org.json.JSONArray()))
        manage.handle(r) ?: HttpServer.Response.error(404, "not found")
    }
    val bound = server.start(port, true)
    println("TV Remote (Kotlin core) for $tv: http://127.0.0.1:$bound/")
    Runtime.getRuntime().addShutdownHook(Thread { link.close(); server.stop() })
    Thread.currentThread().join()
}
