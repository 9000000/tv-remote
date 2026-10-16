package dev.tvremote.setup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The devices let in (a browser after the pairing code, or the Phone / Computer app through TrustReceiver). Only the SHA-256
 * of each pass is kept, so reading the prefs does not give a pass. A browser's pass lasts 30 days from its last use (the
 * cookie is renewed daily while it is used); an app's does not run out, but is listed and can be forgotten like the others.
 */
object Paired {
    private const val PREFS = "remote"
    const val WEB_DAYS = 30
    private const val DAY = 24 * 3600_000L
    private const val WRITE_EVERY = 60_000L                           // `seen` is written to the prefs once a minute at most, not per request

    class Entry(val id: String, val h: String, var name: String, val ip: String, val created: Long, var seen: Long, val kind: String, val tls: Boolean) {
        fun json(me: Boolean = false): JSONObject =
            JSONObject().put("id", id).put("name", name).put("ip", ip).put("created", created).put("seen", seen).put("kind", kind).put("tls", tls).put("me", me)
        fun store(): JSONObject = JSONObject().put("id", id).put("h", h).put("name", name).put("ip", ip).put("created", created).put("seen", seen).put("kind", kind).put("tls", tls)
    }

    /** What [check] found: the device, and whether its cookie should be sent again with a new lifetime. */
    class Hit(val entry: Entry, val renew: Boolean)

    private val random = SecureRandom()
    private var list: MutableList<Entry>? = null
    private var written = HashMap<String, Long>()                      // id -> `seen` as last written

    fun sha256(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { String.format("%02x", it) }

    private fun hex(n: Int): String = ByteArray(n).also { random.nextBytes(it) }.joinToString("") { String.format("%02x", it) }

    @Synchronized
    private fun all(c: Context): MutableList<Entry> {
        list?.let { return it }
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val out = ArrayList<Entry>()
        val now = System.currentTimeMillis()
        val raw = p.getString("paired", null)
        if (raw != null) {
            try {
                val a = JSONArray(raw)
                for (i in 0 until a.length()) a.getJSONObject(i).let {
                    out.add(Entry(it.getString("id"), it.getString("h"), it.optString("name"), it.optString("ip"), it.optLong("created"), it.optLong("seen"), it.optString("kind", "web"), it.optBoolean("tls")))
                }
            } catch (e: Exception) { out.clear() }
        } else {                                                      // before 0.x: a plain set of passes; each becomes a device, seen now
            for (tok in p.getStringSet("tokens", null) ?: emptySet()) out.add(Entry(hex(4), sha256(tok), "", "", now, now, "web", false))
            if (out.isNotEmpty()) p.edit().putString("paired", JSONArray(out.map { it.store() }).toString()).remove("tokens").apply()
        }
        out.forEach { written[it.id] = it.seen }
        list = out
        return out
    }

    private fun save(c: Context) {
        val l = list ?: return
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("paired", JSONArray(l.map { it.store() }).toString()).remove("tokens").commit()
        l.forEach { written[it.id] = it.seen }
    }

    /** Devices that are not seen for [WEB_DAYS] days are dropped (apps stay). */
    private fun prune(c: Context, now: Long) {
        val l = all(c)
        if (l.removeAll { it.kind == "web" && now - it.seen > WEB_DAYS * DAY }) save(c)
    }

    @Synchronized fun count(c: Context): Int { prune(c, System.currentTimeMillis()); return all(c).size }

    @Synchronized fun list(c: Context): List<Entry> { prune(c, System.currentTimeMillis()); return all(c).sortedByDescending { it.seen } }

    /** A new pass, returned in the clear this once. */
    @Synchronized
    fun add(c: Context, kind: String, name: String, ip: String, tls: Boolean): String {
        val token = hex(24)
        val now = System.currentTimeMillis()
        all(c).add(Entry(hex(4), sha256(token), name.take(60), ip, now, now, kind, tls))
        save(c)
        return token
    }

    /** The device that holds [token], if it may use it here: a pass made over TLS never works over plain HTTP. */
    @Synchronized
    fun check(c: Context, token: String, tls: Boolean): Hit? {
        if (token.isEmpty()) return null
        val now = System.currentTimeMillis()
        prune(c, now)
        val h = sha256(token).toByteArray()
        val e = all(c).firstOrNull { MessageDigest.isEqual(it.h.toByteArray(), h) } ?: return null
        if (e.tls && !tls) return null
        val renew = e.kind == "web" && now - e.seen > DAY
        e.seen = now
        if (renew || now - (written[e.id] ?: 0L) > WRITE_EVERY) save(c)
        return Hit(e, renew)
    }

    @Synchronized fun forget(c: Context, id: String): Boolean { val r = all(c).removeAll { it.id == id }; if (r) save(c); return r }

    @Synchronized fun forgetAll(c: Context) { all(c).clear(); save(c) }

    /** "iPhone · Safari" from a browser's User-Agent. */
    fun deviceName(ua: String): String {
        val os = when {
            ua.contains("iPhone") -> "iPhone"
            ua.contains("iPad") -> "iPad"
            ua.contains("Android") -> "Android"
            ua.contains("CrOS") -> "Chromebook"
            ua.contains("Mac OS X") || ua.contains("Macintosh") -> "Mac"
            ua.contains("Windows") -> "Windows"
            ua.contains("Linux") -> "Linux"
            else -> ""
        }
        val browser = when {
            ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/") -> "Edge"
            ua.contains("OPR/") || ua.contains("OPiOS/") -> "Opera"
            ua.contains("Firefox/") || ua.contains("FxiOS/") -> "Firefox"
            ua.contains("SamsungBrowser/") -> "Samsung Internet"
            ua.contains("Chrome/") || ua.contains("CriOS/") -> "Chrome"
            ua.contains("Safari/") -> "Safari"
            else -> ""
        }
        return listOf(os, browser).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Web" }
    }
}
