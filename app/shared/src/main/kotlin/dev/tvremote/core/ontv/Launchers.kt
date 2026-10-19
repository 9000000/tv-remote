package dev.tvremote.core.ontv
import dev.tvremote.update.Ed25519
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64

/**
 * The launcher gallery: the list of home screens a TV can be given (docs/launchers/launchers.json in this repository, published
 * with the intro site), read, checked and matched to a TV. The list is signed (launchers.json.sig, Ed25519) by a key kept apart
 * from the release key ([KEYS]; dev/sign_launchers.py), so whoever takes over the site or the repository still cannot put an
 * APK on anyone's TV: an unsigned or altered list is refused, and so is an APK whose SHA-256 differs from the one the list pins.
 * Nothing but links is hosted here: each APK stays on its author's release page.
 */
object Launchers {
    /** Raw Ed25519 public keys a list may be signed with: the one in use (and, when made, a spare kept apart from it). */
    val KEYS = listOf("de2714846a352ea4e0e5b5dcf2272fee9b10bd2da6ebdea8cff858bac1c3f6ea")
    var testKeys = emptyList<String>()                            // tests / a local list only: a key made for them
    /** The site first, the repository's own copy if the site fails. TVR_LAUNCHERS (computer) or the apps' override file: a test list. */
    var bases = listOf("https://tvremote.pages.dev/launchers/", "https://raw.githubusercontent.com/HoangTran0410/tv-remote/main/docs/launchers/")
    /** A list to read instead of the site's, for trying the gallery before a list is published: TVR_LAUNCHERS (computer) or [useTestFile]. Still signature-checked. */
    @Volatile var testBase: String? = System.getenv("TVR_LAUNCHERS")?.trim()?.takeIf { it.isNotEmpty() }?.let { if (it.endsWith("/")) it else "$it/" }
    /** The apps (no environment): `launchers-url.txt` in the app's own files folder, if someone put one there. */
    fun useTestFile(dir: File?) {
        val u = try { File(dir, "launchers-url.txt").takeIf { it.isFile }?.readText()?.trim() } catch (e: IOException) { null }
        if (!u.isNullOrEmpty()) testBase = if (u.endsWith("/")) u else "$u/"
    }
    const val UNSIGNED = "launcher list not signed by this project"   // Errors.say words it
    const val MISMATCH = "launcher file does not match its signed checksum"
    private const val MAX = 1 shl 20

    class Apk(val abi: String, val url: String, val sha256: String)
    class Entry(val id: String, val pkg: String, val name: String, val desc: Map<String, String>, val thumb: String, val thumbSha: String,
                val source: String, val license: String, val kind: String, val version: String, val files: List<Apk>, val playUrl: String,
                val atv: Boolean, val gtv: Boolean, val minSdk: Int, val fire: Boolean, val notes: Map<String, String>) {
        fun desc(lang: String) = desc[lang] ?: desc["en"] ?: ""
        fun notes(lang: String) = notes[lang] ?: notes["en"] ?: ""
    }
    class Gallery(val updated: String, val entries: List<Entry>, val base: String) {
        operator fun get(id: String): Entry? = entries.firstOrNull { it.id == id }
    }

    /** What decides whether a launcher fits: asked of the TV once (see [facts]). */
    class Facts(val sdk: Int, val abis: List<String>, val leanback: Boolean, val googleTv: Boolean, val fire: Boolean)
    /** [why]: "" when it fits, else sdk / abi / fire / not_tv / atv / gtv, which every page puts in its own words. */
    class Fit(val ok: Boolean, val why: String)

    @Throws(IOException::class)
    fun facts(sh: (String) -> String): Facts = Facts(
        sdk = sh("getprop ro.build.version.sdk").trim().toIntOrNull() ?: 0,
        abis = sh("getprop ro.product.cpu.abilist").trim().split(',').map { it.trim() }.filter { it.isNotEmpty() },
        leanback = sh("pm has-feature android.software.leanback").trim() != "false",   // as Setup.tvOnly: an old pm without has-feature is not turned away
        googleTv = sh("pm path ${Setup.LAUNCHERX}").contains("package:"),
        fire = sh("getprop ro.product.manufacturer").trim().equals("amazon", true) || sh("pm path com.amazon.tv.launcher").contains("package:"))

    fun fits(e: Entry, f: Facts): Fit = when {
        !f.leanback -> Fit(false, "not_tv")
        f.fire && !e.fire -> Fit(false, "fire")
        f.sdk in 1 until e.minSdk -> Fit(false, "sdk")
        f.googleTv && !e.gtv -> Fit(false, "gtv")
        !f.googleTv && !e.atv -> Fit(false, "atv")
        e.kind == "apk" && apk(e, f.abis) == null -> Fit(false, "abi")
        else -> Fit(true, "")
    }

    /**
     * The file for this TV. 64-bit only: the arm64 one. Otherwise the 32-bit one first, which installs on both (as FLauncher always
     * did here), then what the TV's list of ABIs names, last a file that has no native code ("any").
     */
    fun apk(e: Entry, abis: List<String>): Apk? {
        val order = if ("armeabi-v7a" in abis) listOf("armeabi-v7a") + abis.filter { it != "armeabi-v7a" && it != "armeabi" }
                    else abis.filter { it != "armeabi" }                  // 64-bit only, or another chip: what the TV names, never a file it cannot run
        for (a in order + "any") e.files.firstOrNull { it.abi == a }?.let { return it }
        return null
    }

    /** True when [sig] (base64) is a signature of exactly [list] by one of [KEYS]. */
    fun signed(list: ByteArray, sig: String): Boolean {
        val raw = try { Base64.getDecoder().decode(sig.trim()) } catch (e: IllegalArgumentException) { return false }
        return (KEYS + testKeys).any { k -> Ed25519.verify(ByteArray(32) { k.substring(it * 2, it * 2 + 2).toInt(16).toByte() }, list, raw) }
    }

    /** Reads a list whose signature was already checked. A shape that is not ours is an error, not a partial list. */
    @Throws(IOException::class)
    fun parse(list: ByteArray, base: String = ""): Gallery = try {
        val o = JSONObject(String(list, Charsets.UTF_8))
        if (o.optInt("v") != 1) throw IOException("launcher list: unknown version")
        val a = o.getJSONArray("launchers")
        fun words(j: JSONObject?) = j?.keys()?.asSequence()?.associateWith { j.optString(it) } ?: emptyMap()
        Gallery(o.optString("updated"), (0 until a.length()).map { i ->
            val e = a.getJSONObject(i)
            val s = e.getJSONObject("supports")
            val kind = e.getString("kind")
            val files = if (kind == "apk") e.getJSONObject("apk").getJSONObject("files").let { f -> f.keys().asSequence().map { k -> Apk(k, f.getJSONObject(k).getString("url"), f.getJSONObject(k).getString("sha256").lowercase()) }.toList() } else emptyList()
            Entry(e.getString("id"), e.getString("package"), e.getString("name"), words(e.optJSONObject("desc")), e.getString("thumb"), e.optString("thumb_sha256"),
                e.getString("source"), e.optString("license"), kind, e.optJSONObject("apk")?.optString("version") ?: "", files,
                e.optJSONObject("play")?.optString("url") ?: "", s.optBoolean("android_tv", true), s.optBoolean("google_tv", true), s.optInt("min_sdk", 21),
                s.optBoolean("fire_os", false), words(s.optJSONObject("notes")))
        }.filter { it.kind == "apk" || it.kind == "play" }, base)
    } catch (e: org.json.JSONException) { throw IOException("launcher list: " + e.message) }

    @Volatile private var cache: Pair<Long, Gallery>? = null

    /** The signed list from the first place that has one: a minute's memory, so the screens' reopening costs nothing. */
    @Throws(IOException::class)
    fun fetch(fresh: Boolean = false): Gallery {
        cache?.let { if (!fresh && System.currentTimeMillis() - it.first < 60_000) return it.second }
        var last: IOException = IOException("no launcher list")
        for (b in testBase?.let { listOf(it) } ?: bases) try {
            val list = get(b + "launchers.json")
            if (!signed(list, String(get(b + "launchers.json.sig"), Charsets.UTF_8))) throw IOException(UNSIGNED)
            return parse(list, b).also { cache = System.currentTimeMillis() to it }
        } catch (e: IOException) { last = e }
        throw last
    }

    /** A thumbnail's bytes, checked against the signed list's hash. */
    @Throws(IOException::class)
    fun thumb(g: Gallery, e: Entry): ByteArray {
        val b = get(g.base + e.thumb)
        if (e.thumbSha.isNotEmpty() && hex(MessageDigest.getInstance("SHA-256").digest(b)) != e.thumbSha) throw IOException(MISMATCH)
        return b
    }

    /** Downloads [a] to [to], checking its SHA-256 against the list's: a file that differs is deleted, never installed. */
    @Throws(IOException::class)
    fun download(a: Apk, to: File, progress: ((Int) -> Unit)? = null, cancelled: () -> Boolean = { false }) {
        val c = open(a.url)
        try {
            if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
            val md = MessageDigest.getInstance("SHA-256")
            val total = c.contentLengthLong
            var done = 0L
            c.inputStream.use { input -> FileOutputStream(to).use { out ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    if (cancelled()) throw IOException("stopped")
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n); md.update(buf, 0, n); done += n
                    if (progress != null && total > 0) progress((done * 100 / total).toInt())
                }
            } }
            if (total > 0 && done != total) throw IOException("download incomplete ($done of $total bytes)")
            if (hex(md.digest()) != a.sha256) throw IOException(MISMATCH)
        } catch (e: Exception) {
            to.delete(); throw if (e is IOException) e else IOException(e)
        } finally { c.disconnect() }
    }

    private fun hex(b: ByteArray) = b.joinToString("") { String.format("%02x", it) }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 30000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "tv-remote")
        return c
    }

    private fun get(url: String): ByteArray {
        val c = open(url)
        try {
            if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
            val out = ByteArrayOutputStream()
            c.inputStream.use { i ->
                val buf = ByteArray(1 shl 14)
                while (true) {
                    val n = i.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX) throw IOException("launcher list too big")
                }
            }
            return out.toByteArray()
        } finally { c.disconnect() }
    }
}
