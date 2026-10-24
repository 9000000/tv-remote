package dev.tvremote.update

import dev.tvremote.core.B64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The site's signed lists (launchers, wallpapers, bloat, downloader, games, providers), read ONE way for every app: asked of the
 * site first and the repository's copy second, the signature (Ed25519, [KEYS], made by dev/sign/sign_*.py) checked before a byte
 * is used, a copy kept in files/ and the one packed in the APK as the fallbacks. Plain java, so :shared and the TV apps compile
 * this same file (no Android class; the packed copy is reached through a lambda).
 */
object SignedList {
    /** Raw Ed25519 public keys a list may be signed with: the one in use (and, when made, a spare kept apart from it). */
    val KEYS = listOf("de2714846a352ea4e0e5b5dcf2272fee9b10bd2da6ebdea8cff858bac1c3f6ea")
    const val UNSIGNED = "list not signed by this project"

    /** True when [sig] (base64) is a signature of exactly [list] by one of [keys]. */
    fun verify(list: ByteArray, sig: String, keys: List<String> = KEYS): Boolean {
        val raw = try { B64.decode(sig.trim()) } catch (e: IllegalArgumentException) { return false }
        return keys.any { k -> Ed25519.verify(ByteArray(32) { k.substring(it * 2, it * 2 + 2).toInt(16).toByte() }, list, raw) }
    }

    /** One file, at most [cap] bytes: a longer one, another status or another scheme is an error, never a cut-off list. */
    @Throws(IOException::class)
    fun get(url: String, cap: Int, connectMs: Int = 10000, readMs: Int = 30000): ByteArray {
        val u = URL(url)
        if (u.protocol != "https" && u.protocol != "http") throw IOException("not http: $url")
        val c = u.openConnection() as HttpURLConnection
        c.connectTimeout = connectMs
        c.readTimeout = readMs
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "tv-remote")
        try {
            if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
            val out = ByteArrayOutputStream()
            c.inputStream.use { i ->
                val buf = ByteArray(1 shl 14)
                while (true) {
                    val n = i.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    if (out.size() > cap) throw IOException("list too big")
                }
            }
            return out.toByteArray()
        } finally { c.disconnect() }
    }

    /**
     * The first of [bases] that has [name] with a good signature in [name].sig (checked against [keys]) and that [use] accepts:
     * [use] gets the list and the base it came from; null or an IOException from it moves on to the next base. When none does,
     * the last error is thrown ([unsigned] when it was the signature). [sig] is passed too, to keep beside the list ([keep]).
     */
    @Throws(IOException::class)
    fun <T : Any> fetch(bases: List<String>, name: String, cap: Int, keys: List<String> = KEYS, unsigned: String = UNSIGNED,
                        connectMs: Int = 10000, readMs: Int = 30000, use: (list: ByteArray, sig: String, base: String) -> T?): T {
        var last = IOException("no $name")
        for (b in bases) try {
            val list = get(b + name, cap, connectMs, readMs)
            val sig = String(get("$b$name.sig", cap, connectMs, readMs), Charsets.UTF_8)
            if (!verify(list, sig, keys)) throw IOException(unsigned)
            return use(list, sig, b) ?: continue
        } catch (e: IOException) { last = e }
        throw last
    }

    /** The copy kept in [f] (with its [f].sig beside it), if the signature still checks; null when missing or altered. */
    fun kept(f: File, keys: List<String> = KEYS): ByteArray? {
        val s = File(f.path + ".sig")
        return try { if (f.isFile && s.isFile) f.readBytes().takeIf { verify(it, s.readText(), keys) } else null } catch (e: IOException) { null }
    }

    /** The copy packed in the APK: [open] gives the asset [name] and [name].sig (any failure: null); null when missing or unsigned. */
    fun packed(open: (String) -> InputStream, name: String, keys: List<String> = KEYS): ByteArray? = try {
        val b = open(name).use { it.readBytes() }
        val sig = open("$name.sig").use { String(it.readBytes(), Charsets.UTF_8) }
        b.takeIf { verify(it, sig, keys) }
    } catch (e: Exception) { null }

    /** Keeps [list] in [f] (written to .tmp, then renamed), and [sig] in [f].sig when there is one. */
    fun keep(f: File, list: ByteArray, sig: String? = null) {
        if (sig != null) File(f.path + ".sig").writeText(sig)
        File(f.path + ".tmp").apply { writeBytes(list); renameTo(f) }
    }
}
