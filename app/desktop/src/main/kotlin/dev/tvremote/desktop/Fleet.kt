package dev.tvremote.desktop

import dev.tvremote.core.AdbLink
import dev.tvremote.core.Fleet
import dev.tvremote.core.Manage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Collections
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/**
 * 📺📺 Several saved TVs at once: one table that compares them, and one APK installed (or one app
 * removed, or a restart) on each TV picked, each with its own progress. The reading and the actions are Fleet.kt's.
 */
class Fleets(private val keys: File, private val cache: File) {
    private val links = HashMap<String, AdbLink>()
    private val used = HashMap<String, Long>()
    private val working = HashMap<String, Int>()                      // TVs a job is busy with: never closed under it
    val jobs: MutableMap<String, JSONObject> = Collections.synchronizedMap(LinkedHashMap())
    private val sweeper = Executors.newSingleThreadScheduledExecutor { Thread(it, "fleet-sweep").apply { isDaemon = true } }

    init {                                                            // a hotel's 30 TVs must not keep 30 connections open all day
        sweeper.scheduleWithFixedDelay({
            synchronized(this) {
                val idle = links.keys.filter { (working[it] ?: 0) == 0 && System.currentTimeMillis() - (used[it] ?: 0) > IDLE_MS }
                idle.forEach { drop(it) }
            }
        }, 1, 1, java.util.concurrent.TimeUnit.MINUTES)
    }

    /**
     * A connection of its own per TV, kept a while so the next action does not connect again. Not the current TV's
     * session link: picking another TV closes that one, which would cut an install half-way.
     */
    @Synchronized fun link(host: String): AdbLink {
        used[host] = System.currentTimeMillis()
        return links.getOrPut(host) { AdbLink(keys, host) }
    }

    @Synchronized fun drop(host: String) { links.remove(host)?.close(); used.remove(host) }

    @Synchronized fun close() { sweeper.shutdownNow(); links.values.forEach { it.close() }; links.clear() }

    /** A job as JSON while its TVs are writing their progress into it. */
    fun snapshot(j: JSONObject): JSONObject {
        repeat(20) { try { return JSONObject(j.toString()) } catch (e: ConcurrentModificationException) { Thread.sleep(5) } }
        return JSONObject(j.toString())
    }

    fun info(host: String, link: AdbLink): JSONObject {
        val o = JSONObject().put("host", host).put("state", "closed")
        try {
            val st = ready(link, 15000)
            o.put("state", st)
            if (st != "ready") return o
            val i = Fleet.info(link.get())
            o.put("model", i.model).put("android", i.android).put("sdk", i.sdk).put("disk_total", i.diskTotal).put("disk_free", i.diskFree)
                .put("ram", i.ram).put("apps", JSONArray(i.apps)).put("setup", i.setup)
        } catch (e: Exception) {
            o.put("state", "error").put("error", e.message ?: "timed out")
        }
        return o
    }

    /** How the link stands once it has had [ms] to connect: ready, unauthorized, closed. */
    private fun ready(link: AdbLink, ms: Long): String {
        val until = System.currentTimeMillis() + ms
        while (true) {
            val st = link.state()
            if (st != "unknown") return st
            if (System.currentTimeMillis() > until) return "closed"
            Thread.sleep(200)
        }
    }

    /** One job for many TVs: [work] runs for each, a few at a time; each part keeps its own progress. [done]: after all of them. */
    fun run(title: String, targets: List<Pair<String, AdbLink>>, done: () -> Unit = {}, work: (AdbLink, JSONObject) -> String): JSONObject {
        val id = UUID.randomUUID().toString().replace("-", "").take(8)
        val parts = JSONObject()
        for ((h, _) in targets) parts.put(h, JSONObject().put("status", "waiting").put("msg", "waiting").put("pct", 0))
        val j = JSONObject().put("id", id).put("title", title).put("status", "running").put("t", System.currentTimeMillis() / 1000.0).put("tvs", parts)
        synchronized(jobs) {
            jobs[id] = j
            while (jobs.size > 10) jobs.remove(jobs.keys.first())
        }
        synchronized(this) { for ((h, _) in targets) working[h] = (working[h] ?: 0) + 1 }
        val gate = Semaphore(PARALLEL)
        val pool = Executors.newCachedThreadPool { Thread(it, "fleet").apply { isDaemon = true } }
        Thread({
            targets.map { (host, link) ->
                pool.submit {
                    val part = parts.getJSONObject(host)
                    gate.acquire()
                    try {
                        part.put("status", "running").put("msg", "connecting")
                        val st = ready(link, 20000)
                        if (st != "ready") throw java.io.IOException("adb:$st")
                        part.put("msg", work(link, part))
                        part.put("status", "done").put("pct", 100)
                    } catch (e: Exception) {
                        part.put("status", "error").put("msg", e.message ?: e.javaClass.simpleName)
                    } finally {
                        gate.release()
                        synchronized(this@Fleets) { working[host] = (working[host] ?: 1) - 1; used[host] = System.currentTimeMillis() }
                    }
                }
            }.forEach { it.get() }
            j.put("status", "done")
            pool.shutdown()
            done()
        }, "fleet-job").apply { isDaemon = true }.start()
        return snapshot(j)
    }

    fun install(link: AdbLink, part: JSONObject, apk: File): String {
        part.put("pct", 10)
        return apk.inputStream().use { Manage(link, null, cache).install(it, apk.length(), part) }
    }

    /** The work for one of the bulk actions that are not an install, refused here before any TV is touched. */
    fun action(name: String, pkg: String): (AdbLink, JSONObject) -> String = when (name) {
        "uninstall" -> {
            Fleet.canUninstall(pkg)?.let { throw IllegalArgumentException(it) }
            val go: (AdbLink, JSONObject) -> String = { link, _ -> Fleet.uninstall(link.get(), pkg); "uninstalled" }
            go
        }
        "reboot" -> { link, _ -> Fleet.reboot(link.get()); link.close(); "restarting" }
        else -> throw IllegalArgumentException("unknown action")
    }

    companion object {
        const val IDLE_MS = 10 * 60_000L                              // unused that long: closed, dialled again when needed
        const val PARALLEL = 3                                        // installs at once: an APK goes over the home Wi-Fi to each TV
    }
}
