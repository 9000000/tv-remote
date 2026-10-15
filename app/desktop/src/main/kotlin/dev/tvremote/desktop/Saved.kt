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
    // one lock for every read and write: the page's requests and the background threads (TV Setup passes) share these objects,
    // and org.json's maps break when one thread writes while another one serializes. What leaves this class is a copy.
    private val cfg: JSONObject = load()

    private fun load(): JSONObject {
        val c = try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() }
        if (!c.has("current")) c.put("current", "")
        if (c.optJSONArray("tvs") == null) c.put("tvs", JSONArray())
        return c
    }

    private val list: JSONArray get() = cfg.getJSONArray("tvs")
    private fun live(host: String): JSONObject? = (0 until list.length()).map { list.getJSONObject(it) }.firstOrNull { it.optString("host") == host && host.isNotEmpty() }
    private fun copy(o: JSONObject) = JSONObject(o.toString())

    val current: String @Synchronized get() = cfg.optString("current")
    val tvs: JSONArray @Synchronized get() = JSONArray(list.toString())

    /** All or nothing: a crash or a full disk mid-write must not leave half a file, which would lose every pairing. */
    @Synchronized fun save() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(cfg.toString(1) + "\n")
        try {                                                         // it holds TV Setup passes: this user only
            Files.setPosixFilePermissions(tmp.toPath(), java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
        } catch (e: Exception) { }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    @Synchronized fun find(host: String): JSONObject? = live(host)?.let { copy(it) }

    /** Changes a saved TV in place (and saves); nothing when it is no longer saved. */
    @Synchronized fun edit(host: String, change: (JSONObject) -> Unit) { live(host)?.let { change(it); save() } }

    @Synchronized fun setCurrent(host: String) { cfg.put("current", host) }

    /** Add or update a TV. [replaceHost]: the entry it used to be saved under (its LAN IP before it got a Tailscale IP). */
    @Synchronized fun upsert(entry: JSONObject, replaceHost: String?): JSONObject {
        val host = entry.optString("host")
        val old = replaceHost?.let { live(it) } ?: live(host)
        val kept = if (old != null) {
            for (k in entry.keys()) entry.opt(k).let { if (it != null && it != "") old.put(k, it) }
            old
        } else entry.also { list.put(it) }
        val left = JSONArray()
        for (i in 0 until list.length()) list.getJSONObject(i).let { if (it === kept || it.optString("host") != host) left.put(it) }
        cfg.put("tvs", left)
        cfg.put("current", host)
        return copy(kept)
    }

    @Synchronized fun remove(host: String) {
        val left = JSONArray()
        for (i in 0 until list.length()) list.getJSONObject(i).let { if (it.optString("host") != host) left.put(it) }
        cfg.put("tvs", left)
        if (current == host) cfg.put("current", if (left.length() > 0) left.getJSONObject(0).optString("host") else "")
    }

    var scenes: JSONArray
        @Synchronized get() = JSONArray((cfg.optJSONArray("scenes") ?: JSONArray()).toString())
        @Synchronized set(v) { cfg.put("scenes", v) }
}
