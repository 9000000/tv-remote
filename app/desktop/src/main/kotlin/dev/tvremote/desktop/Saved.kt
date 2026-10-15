package dev.tvremote.desktop

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Saved TVs: the same tvs.json as the Python server (app/web/tvconfig.py), read and written the same way, so either server
 * can run on it. Fields this code does not know (relay_*, setup_pass, scenes...) are kept as they are.
 */
class Saved(private val file: File) {
    @Volatile var cfg: JSONObject = load()
        private set

    private fun load(): JSONObject {
        val c = try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() }
        if (!c.has("current")) c.put("current", "")
        if (c.optJSONArray("tvs") == null) c.put("tvs", JSONArray())
        return c
    }

    val current: String get() = cfg.optString("current")
    val tvs: JSONArray get() = cfg.getJSONArray("tvs")

    /** All or nothing: a crash or a full disk mid-write must not leave half a file, which would lose every pairing. */
    @Synchronized fun save() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(cfg.toString(1) + "\n")
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun find(host: String): JSONObject? = (0 until tvs.length()).map { tvs.getJSONObject(it) }.firstOrNull { it.optString("host") == host && host.isNotEmpty() }

    @Synchronized fun setCurrent(host: String) { cfg.put("current", host) }

    /** Add or update a TV. [replaceHost]: the entry it used to be saved under (its LAN IP before it got a Tailscale IP). */
    @Synchronized fun upsert(entry: JSONObject, replaceHost: String?): JSONObject {
        val host = entry.optString("host")
        val old = replaceHost?.let { find(it) } ?: find(host)
        val kept = if (old != null) {
            for (k in entry.keys()) entry.opt(k).let { if (it != null && it != "") old.put(k, it) }
            old
        } else entry.also { tvs.put(it) }
        val left = JSONArray()
        for (i in 0 until tvs.length()) tvs.getJSONObject(i).let { if (it === kept || it.optString("host") != host) left.put(it) }
        cfg.put("tvs", left)
        cfg.put("current", host)
        return kept
    }

    @Synchronized fun remove(host: String) {
        val left = JSONArray()
        for (i in 0 until tvs.length()) tvs.getJSONObject(i).let { if (it.optString("host") != host) left.put(it) }
        cfg.put("tvs", left)
        if (current == host) cfg.put("current", if (left.length() > 0) left.getJSONObject(0).optString("host") else "")
    }

    var scenes: JSONArray
        get() = cfg.optJSONArray("scenes") ?: JSONArray()
        set(v) { cfg.put("scenes", v) }
}
