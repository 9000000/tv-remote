package dev.tvremote.games

import android.content.Context
import android.content.pm.ApplicationInfo
import dev.tvremote.update.Ed25519
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * What the site tells the app without a release: docs/games/games.json (dev/sign_games.py), the same way TV Channels reads its
 * providers: the site, else GitHub, else the last good copy in files/, else the copy packed in assets. Signed (Ed25519, the
 * launcher list's key): a bad signature is ignored. Kept a day. "cores" (downloaded on first use, [Cores]), "web" (featured
 * games), "homebrew" (free ROMs), "layouts" (pad overrides, served to the phones at /layouts.json).
 */
object Catalog {
    private val KEYS = listOf("de2714846a352ea4e0e5b5dcf2272fee9b10bd2da6ebdea8cff858bac1c3f6ea")   // Launchers.KEYS
    private val BASES = listOf("https://tvremote.pages.dev/games/", "https://raw.githubusercontent.com/HoangTran0410/tv-remote/main/docs/games/")
    private const val DAY = 24 * 3600_000L

    /** Debug builds only: another address and key to test with (`--es cat_base http://10.0.2.2:8800/docs/games/ --es cat_key <hex>` on the library). */
    private fun dbg(c: Context): Pair<String, String>? {
        if (c.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        val p = c.getSharedPreferences("games", Context.MODE_PRIVATE)
        val b = p.getString("cat_base", "") ?: ""
        return if (b.isEmpty()) null else b to (p.getString("cat_key", "") ?: "")
    }

    /** Where the catalog and what it points to (pictures) are read from, in order: the site, then GitHub (a debug build's override alone). */
    fun bases(c: Context): List<String> = dbg(c)?.let { listOf(it.first) } ?: BASES

    fun setDebug(c: Context, base: String?, key: String?) {
        if (c.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        c.getSharedPreferences("games", Context.MODE_PRIVATE).edit().putString("cat_base", base ?: "").putString("cat_key", key ?: "").apply()
        File(c.filesDir, "games.json").delete(); mem = null
    }

    fun verified(c: Context, list: ByteArray, sig: String): Boolean {
        val raw = try { Base64.getDecoder().decode(sig.trim()) } catch (e: IllegalArgumentException) { return false }
        val keys = dbg(c)?.second?.takeIf { it.length == 64 }?.let { listOf(it) } ?: KEYS        // a debug override trusts only its own key
        return keys.any { k -> Ed25519.verify(ByteArray(32) { k.substring(it * 2, it * 2 + 2).toInt(16).toByte() }, list, raw) }
    }

    @Volatile private var mem: JSONObject? = null

    private fun parse(text: String?): JSONObject? = try { text?.let { JSONObject(it) }?.takeIf { it.optInt("v") == 1 } } catch (e: Exception) { null }

    /** The newest list already on the device, never the network: the kept copy (checked again), else the one in the APK. */
    fun peek(c: Context): JSONObject {
        mem?.let { return it }
        val f = File(c.filesDir, "games.json"); val s = File(c.filesDir, "games.json.sig")
        val kept = if (f.exists() && s.exists() && verified(c, f.readBytes(), s.readText())) parse(f.readText()) else null
        val packed = kept ?: try {
            val b = c.assets.open("games.json").use { it.readBytes() }
            val sig = c.assets.open("games.json.sig").use { String(it.readBytes()) }
            if (verified(c, b, sig)) parse(String(b)) else null
        } catch (e: Exception) { null }
        return (packed ?: JSONObject("{\"v\":1}")).also { mem = it }
    }

    /** Asks the site when the kept copy is over a day old (or [force]); true when the list changed. Off the main thread. */
    fun refresh(c: Context, force: Boolean = false): Boolean {
        val f = File(c.filesDir, "games.json")
        if (!force && f.exists() && System.currentTimeMillis() - f.lastModified() < DAY && dbg(c) == null) return false
        val bases = dbg(c)?.let { listOf(it.first) } ?: BASES
        for (b in bases) try {
            val list = get(b + "games.json")?.takeIf { it.isNotEmpty() } ?: continue
            val sig = get(b + "games.json.sig") ?: continue
            val bytes = list.toByteArray(Charsets.UTF_8)
            if (!verified(c, bytes, sig)) continue
            val o = parse(list) ?: continue
            val changed = mem?.toString() != o.toString()
            File(c.filesDir, "games.json.sig").writeText(sig); File(c.filesDir, "games.json.tmp").apply { writeText(list); renameTo(f) }
            mem = o
            return changed
        } catch (e: Exception) { }
        return false
    }

    fun get(url: String): String? {
        val u = URL(url)
        if (u.protocol != "https" && u.protocol != "http") return null
        val h = u.openConnection() as HttpURLConnection
        h.connectTimeout = 6000; h.readTimeout = 8000
        return try { if (h.responseCode == 200) h.inputStream.use { String(it.readBytes().take(1 shl 20).toByteArray(), Charsets.UTF_8) } else null } finally { h.disconnect() }
    }

    fun layouts(c: Context): String = peek(c).optJSONObject("layouts")?.toString() ?: "{}"
}
