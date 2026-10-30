package dev.tvremote.games

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Cores that are not packed in the APK: fetched on the first game of that system from the address in the catalog, the zip's
 * sha256 checked BEFORE anything is unpacked, then kept read-only in files/cores/ (what System.load / dlopen reads).
 */
object Cores {
    fun dir(c: Context) = File(c.filesDir, "cores").also { it.mkdirs() }

    /** The catalog's file for this device's ABI, or null (no build for it). */
    fun abi(entry: JSONObject): JSONObject? = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { entry.optJSONObject("abi")?.optJSONObject(it) }

    /** Where the core is, if it is here: packed with the APK, else downloaded earlier. */
    fun path(c: Context, sys: Systems.Sys): String? {
        val n = sys.core ?: return null
        return listOf(File(c.applicationInfo.nativeLibraryDir, n), File(dir(c), n)).firstOrNull { it.exists() }?.path
    }

    /** null = done; else the words for what went wrong. [progress] gets 0..100; [cancelled] is asked while it reads. */
    fun fetch(c: Context, sys: Systems.Sys, progress: (Int) -> Unit, cancelled: () -> Boolean): String? {
        val e = sys.need?.let { abi(it) } ?: return c.getString(R.string.core_none)
        val file = sys.core ?: return c.getString(R.string.core_none)
        val zip = File(c.cacheDir, "core.zip")
        try {
            val h = URL(e.getString("url")).openConnection() as HttpURLConnection
            h.connectTimeout = 8000; h.readTimeout = 15000
            if (h.responseCode != 200) return c.getString(R.string.core_net)
            val want = e.optLong("size"); val md = MessageDigest.getInstance("SHA-256"); var got = 0L
            h.inputStream.use { i -> zip.outputStream().use { o ->
                val buf = ByteArray(32 * 1024)
                while (true) {
                    if (cancelled()) return ""
                    val n = i.read(buf); if (n < 0) break
                    got += n; if (got > want + 1024) return c.getString(R.string.core_bad)       // longer than the catalog says: not that file
                    md.update(buf, 0, n); o.write(buf, 0, n)
                    if (want > 0) progress((got * 100 / want).toInt().coerceAtMost(100))
                }
            } }
            val sum = md.digest().joinToString("") { "%02x".format(it) }
            if (!sum.equals(e.optString("sha256"), true)) return c.getString(R.string.core_bad)  // before any of it is unpacked, let alone loaded
            val out = File(dir(c), "$file.tmp")
            var found = false
            ZipInputStream(zip.inputStream()).use { z ->
                while (true) {
                    val en = z.nextEntry ?: break
                    if (!en.isDirectory && en.name.endsWith(".so")) { out.outputStream().use { z.copyTo(it) }; found = true; break }
                }
            }
            if (!found) return c.getString(R.string.core_bad)
            val dest = File(dir(c), file)
            dest.delete(); out.renameTo(dest); dest.setReadOnly()                                  // Android 14 wants loaded code read-only
            return null
        } catch (ex: Exception) { return c.getString(R.string.core_net) } finally { zip.delete() }
    }
}
