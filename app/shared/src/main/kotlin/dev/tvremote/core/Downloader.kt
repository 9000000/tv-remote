package dev.tvremote.core
import dev.tvremote.update.SignedList
import dev.tvremote.core.ontv.Launchers
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * The Downloader (AFTVnews) short codes of this project's APKs: docs/data/downloader.json on the site, signed by the launcher list's
 * key (dev/sign/sign_downloader.py). A code makes a TV install whatever it points at, so a list is used only when its signature
 * checks; otherwise the last good copy kept in the app's folder, otherwise [PACKED] (the same file, checked equal by a test).
 * Read from the site, else GitHub, at most once a day, off the caller's thread ([codes] never waits on the network).
 */
object Downloader {
    /** The copy that ships with the app: docs/data/downloader.json. A code is null where the APK has none yet. */
    const val PACKED = """{"v":1,"setup":"2397346","remote":"2973135","home":"4356026","channels":"8135635","games":"2294968","tvapps":"1082201"}"""
    var bases = listOf("https://tvremote.pages.dev/data/", "https://raw.githubusercontent.com/HoangTran0410/tv-remote/main/docs/data/")
    /** The app's own folder for the kept copy (set once at start; null: nothing is kept, the packed codes serve). */
    @Volatile var dir: File? = null
    private const val DAY = 24 * 3600_000L
    @Volatile private var asked = 0L

    /** The codes of a signed list as id -> code; null for a list that is not signed, not ours, or has a code that is not 4-9 digits. */
    fun accept(list: ByteArray, sig: String): Map<String, String>? = if (Launchers.signed(list, sig)) parse(String(list, Charsets.UTF_8)) else null

    fun parse(text: String): Map<String, String>? {
        try {
            val o = JSONObject(text)
            if (o.optInt("v") != 1) return null
            val m = LinkedHashMap<String, String>()
            for (k in o.keys().asSequence().toList()) {
                if (k == "v" || o.isNull(k)) continue
                val c = o.optString(k)
                if (!c.matches(Regex("\\d{4,9}"))) return null
                m[k] = c
            }
            return m
        } catch (e: Exception) { return null }
    }

    /** What is known now: the kept copy when it still verifies, else the packed one. Starts a refresh when the copy is over a day old. */
    fun codes(): Map<String, String> {
        refreshLater()
        return kept() ?: parse(PACKED)!!
    }

    fun code(id: String): String? = codes()[id]

    private fun kept(): Map<String, String>? {
        val d = dir ?: return null
        val f = File(d, "downloader.json"); val s = File(d, "downloader.json.sig")
        return try { if (f.isFile && s.isFile) accept(f.readBytes(), s.readText()) else null } catch (e: IOException) { null }
    }

    private fun refreshLater() {
        val d = dir ?: return
        val now = System.currentTimeMillis()
        val f = File(d, "downloader.json")
        if (now - asked < 60_000 || (f.isFile && dev.tvremote.guard.Age.fresh(f.lastModified(), DAY, now))) return
        asked = now
        Thread { refresh() }.apply { isDaemon = true }.start()
    }

    /** Asks the places in order for a signed list and keeps the first that verifies; true when one did. */
    fun refresh(): Boolean {
        val d = dir ?: return false
        return try {
            Launchers.first(bases, "downloader.json") { list, sig, _ ->
                if (accept(list, sig) == null) null                      // unsigned or altered, or not our shape: ignored, the next place is tried
                else { SignedList.keep(File(d, "downloader.json"), list, sig); true }
            }
        } catch (e: IOException) { false }
    }

    fun json(): JSONObject = JSONObject().apply { codes().forEach { (k, v) -> put(k, v) } }
}
