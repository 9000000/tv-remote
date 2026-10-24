package dev.tvremote.core.ontv
import dev.tvremote.update.Release
import dev.tvremote.update.SignedList
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import dev.tvremote.core.B64

/**
 * The launcher gallery: the list of home screens a TV can be given (docs/launchers/launchers.json in this repository, published
 * with the intro site), read, checked and matched to a TV. The list is signed (launchers.json.sig, Ed25519) by a key kept apart
 * from the release key ([KEYS]; dev/sign/sign_launchers.py), so whoever takes over the site or the repository still cannot put an
 * APK on anyone's TV: an unsigned or altered list is refused, and so is an APK whose SHA-256 differs from the one the list pins.
 * Nothing but links is hosted here: each APK stays on its author's release page.
 */
object Launchers {
    /** Raw Ed25519 public keys a list may be signed with: [SignedList.KEYS], the one reader every app shares. */
    val KEYS = SignedList.KEYS
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
                val atv: Boolean, val gtv: Boolean, val minSdk: Int, val fire: Boolean, val notes: Map<String, String>,
                val langs: List<String> = emptyList(),                  // the languages of its screens (dev/sign/sign_launchers.py reads them); empty: not known
                val fallback: String = "") {                            // what it shows in a language it lacks
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
    fun facts(sh: (String) -> String): Facts {
        // one round trip, not six (~45 ms each through adb): a part per question, between lines of "@@"
        val p = sh("getprop ro.build.version.sdk; echo @@; getprop ro.product.cpu.abilist; echo @@; pm has-feature android.software.leanback; echo @@; " +
            "pm path ${Setup.LAUNCHERX}; echo @@; getprop ro.product.manufacturer; echo @@; pm path com.amazon.tv.launcher")
            .replace("\r", "").split("\n@@\n", "@@\n").map { it.trim() } + List(6) { "" }
        return Facts(
            sdk = p[0].toIntOrNull() ?: 0,
            abis = p[1].split(',').map { it.trim() }.filter { it.isNotEmpty() },
            leanback = p[2] != "false",                                  // as Setup.tvOnly: an old pm without has-feature is not turned away
            googleTv = p[3].contains("package:"),
            fire = p[4].equals("amazon", true) || p[5].contains("package:"))
    }

    fun fits(e: Entry, f: Facts): Fit = when {
        !f.leanback -> Fit(false, "not_tv")
        f.fire && !e.fire -> Fit(false, "fire")
        f.sdk in 1 until e.minSdk -> Fit(false, "sdk")
        f.googleTv && !e.gtv -> Fit(false, "gtv")
        !f.googleTv && !e.atv -> Fit(false, "atv")
        e.kind == "apk" && apk(e, f.abis) == null -> Fit(false, "abi")
        else -> Fit(true, "")
    }

    /** The TV's language ("vi"): its first locale, as Android picks an app's words. "" when it cannot be told. */
    @Throws(IOException::class)
    fun tvLang(sh: (String) -> String): String =
        sh("settings get system system_locales; getprop persist.sys.locale; getprop ro.product.locale").lines().map { it.trim() }
            .firstOrNull { it.isNotEmpty() && it != "null" }?.let { lang(it.substringBefore(',')) } ?: ""

    /** "vi-VN", "in", "iw_IL" -> "vi", "id", "he": what the list writes. */
    fun lang(tag: String): String = tag.substringBefore('-').substringBefore('_').lowercase().let { when (it) { "in" -> "id"; "iw" -> "he"; else -> it } }

    /**
     * True when [e] has no words in the TV's language but has English: shown in English then ([LauncherInstall.english]). On its own
     * Android shows the app's default words, Chinese for UnitedU, and a Flutter app the first language it has: Arabic for LTV, right to left.
     */
    fun english(e: Entry, tvLang: String): Boolean = e.langs.isNotEmpty() && tvLang.isNotEmpty() && tvLang !in e.langs && "en" in e.langs

    /** The language [e] will show on this TV when it lacks the TV's own, for the person to know before installing: "" when it has it, or nobody knows. */
    fun shows(e: Entry, tvLang: String, sdk: Int): String = when {
        e.langs.isEmpty() || tvLang.isEmpty() || tvLang in e.langs -> ""
        sdk >= 33 && english(e, tvLang) -> "en"                      // LauncherInstall.english sets it
        else -> e.fallback                                            // an older Android has no language per app: its own fallback
    }

    /**
     * The file for this TV. 64-bit only: the arm64 one. Otherwise the 32-bit one first, which installs on both (as FLauncher always
     * did here), then what the TV's list of ABIs names, last a file that has no native code ("any").
     */
    /**
     * TV Home, this project's own launcher (kind "self"): the file of the latest release, pinned by that release's signed SHA256SUMS
     * (the release key, [Release.KEYS]) rather than by this list, so the list need not be signed again each release.
     */
    @Throws(IOException::class)
    fun self(): Apk {
        val r = try { Release.newer("0", "home", SELF_APK) } catch (e: IOException) { throw e } catch (e: Exception) { throw IOException(e.message, e) }
        return r?.let { Apk("any", it.url, it.sha256) } ?: throw IOException("launcher:self")   // no release carries it yet
    }
    const val SELF_APK = "tv-home.apk"

    fun apk(e: Entry, abis: List<String>): Apk? {
        val order = if ("armeabi-v7a" in abis) listOf("armeabi-v7a") + abis.filter { it != "armeabi-v7a" && it != "armeabi" }
                    else abis.filter { it != "armeabi" }                  // 64-bit only, or another chip: what the TV names, never a file it cannot run
        for (a in order + "any") e.files.firstOrNull { it.abi == a }?.let { return it }
        return null
    }

    /** True when [sig] (base64) is a signature of exactly [list] by one of [KEYS]. */
    fun signed(list: ByteArray, sig: String): Boolean = SignedList.verify(list, sig, KEYS + testKeys)

    /** The first of [places] that has [name] signed and that [use] accepts; Bloat, Wallpapers and Downloader read theirs through it too. */
    @Throws(IOException::class)
    fun <T : Any> first(places: List<String>, name: String, use: (list: ByteArray, sig: String, base: String) -> T?): T =
        SignedList.fetch(places, name, MAX, KEYS + testKeys, UNSIGNED, use = use)

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
                s.optBoolean("fire_os", false), words(s.optJSONObject("notes")),
                e.optJSONArray("langs")?.let { l -> (0 until l.length()).map { lang(l.optString(it)) } } ?: emptyList(), lang(e.optString("fallback")))
        }.filter { it.kind == "apk" || it.kind == "play" || it.kind == "self" }, base)
    } catch (e: org.json.JSONException) { throw IOException("launcher list: " + e.message) }

    @Volatile private var cache: Pair<Long, Gallery>? = null

    /**
     * The signed list from the first place that has one: a minute's memory, so the screens' reopening costs nothing. Past that, the
     * copy kept in [dir] (its signature checked again) is answered at once and the site read behind it for the next opening: the
     * gallery waited ~1 s on the network every time. An install checks the APK against the list it was shown, as before.
     */
    @Throws(IOException::class)
    fun fetch(fresh: Boolean = false): Gallery {
        cache?.let { if (!fresh && System.currentTimeMillis() - it.first < 60_000) return it.second }
        val kept = if (fresh || testBase != null) null else dir?.let { File(it, "launchers.json") }?.let { f ->
            SignedList.kept(f, KEYS + testKeys)?.let { l -> try { parse(l, File(f.path + ".base").readText()) } catch (e: IOException) { null } } }
        if (kept != null) {
            cache = System.currentTimeMillis() to kept
            Thread({ try { Thread.sleep(2000); load() } catch (e: Exception) { } }, "launchers").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()   // after the screen is drawn: the check is CPU
            return kept
        }
        return load()
    }

    private fun load(): Gallery = first(testBase?.let { listOf(it) } ?: bases, "launchers.json") { list, sig, b ->
        parse(list, b).also {
            cache = System.currentTimeMillis() to it
            if (testBase == null) dir?.let { d -> try { d.mkdirs(); SignedList.keep(File(d, "launchers.json"), list, sig); File(d, "launchers.json.base").writeText(b) } catch (e: IOException) { } }
        }
    }

    /** Where an app keeps the list ([fetch]) and the thumbnails by their hash ([thumb]): set once by the app, null = memory only (the computer). */
    @Volatile var dir: File? = null
    private val thumbs = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    /**
     * A thumbnail's bytes, checked against the signed list's hash. Kept in memory and in [dir]: the 13 pictures are ~0.9 MB
     * and took ~4 s one by one, the gallery waited for them every time it opened. A file on disk is checked again before use.
     */
    @Throws(IOException::class)
    fun thumb(g: Gallery, e: Entry): ByteArray {
        val key = e.thumbSha.ifEmpty { g.base + e.thumb }
        thumbs[key]?.let { return it }
        val f = if (e.thumbSha.isNotEmpty()) dir?.let { File(it, e.thumbSha + ".jpg") } else null
        try { f?.takeIf { it.isFile }?.readBytes()?.takeIf { hex(MessageDigest.getInstance("SHA-256").digest(it)) == e.thumbSha }?.let { thumbs[key] = it; return it } } catch (x: IOException) { }
        val b = get(g.base + e.thumb)
        if (e.thumbSha.isNotEmpty() && hex(MessageDigest.getInstance("SHA-256").digest(b)) != e.thumbSha) throw IOException(MISMATCH)
        thumbs[key] = b
        if (f != null) try { f.parentFile?.mkdirs(); f.writeBytes(b) } catch (x: IOException) { }
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

    internal fun hex(b: ByteArray) = b.joinToString("") { String.format("%02x", it) }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 30000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "tv-remote")
        return c
    }

    internal fun get(url: String): ByteArray = SignedList.get(url, MAX)
}
