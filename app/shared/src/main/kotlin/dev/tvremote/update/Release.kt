package dev.tvremote.update

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64

/**
 * Update check shared by the phone app and the TV app: asks GitHub for the latest release of this project and, if it is
 * newer than the running app, gives the download link of this app's APK. Nothing else is sent anywhere.
 *
 * A release lists the SHA-256 of each of its files in SHA256SUMS, signed (SHA256SUMS.sig, Ed25519) by a key that never goes
 * to GitHub (dev/Sign.java, dev/release.sh): a file that does not match is deleted, not installed, so neither a broken
 * download nor someone who took over the repository or a GitHub token can put an update on anyone's device. A release
 * without them is not offered yet (dev/release.sh adds them last, once the computer apps are attached).
 */
class Release private constructor(val version: String, val url: String, val notes: String, val sha256: String) {
    fun interface Progress { fun at(percent: Int) }

    /** Downloads this file to [to] and checks it against the signed SHA256SUMS: a file that does not match is deleted. */
    @Throws(IOException::class)
    fun download(to: File, progress: Progress?) {
        val c = open(url)
        try {
            if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
            val md = MessageDigest.getInstance("SHA-256")
            c.inputStream.use { input -> FileOutputStream(to).use { out -> copy(input, out, c.contentLength, progress, md) } }
            check(md)
        } catch (e: Exception) {
            to.delete(); throw e
        } finally {
            c.disconnect()
        }
    }

    /** For a download that goes elsewhere (straight to the TV): [md] has seen every byte of it. */
    @Throws(IOException::class)
    fun check(md: MessageDigest) {
        if (hex(md.digest()) != sha256) throw IOException(MISMATCH)
    }

    companion object {
        const val REPO = "HoangTran0410/tv-remote"
        /** The keys a release's SHA256SUMS may be signed with (raw Ed25519 public keys): the one in use, and a spare kept
         *  apart from it, so losing one does not leave every installed app refusing updates. */
        val KEYS = listOf(
            "8bda551ed91896e42bafabe7cee2b95f8401e33da105b3a092c333db4dc96b3a",
            "510ce48a7e710113b56e9c51c70ce3023be5188c722e4e304c159729481d9149",
        )
        const val MISMATCH = "update file does not match its signed checksum"   // Errors.say words it
        const val UNSIGNED = "update list not signed by this project"
        var api = "https://api.github.com"                            // tests of the computer app's updater: a fake GitHub (TVR_RELEASES)

        /**
         * The latest release, if it carries a newer version of this app. Each app has its own version number: a release lists
         * them in a small file, versions.json ({"android": "2.1.0", "tv": "2.0.3"}), so an app is only offered an update when
         * it changed itself. Releases without that file (before 2.1) are compared by their tag. null when up to date.
         */
        @Throws(Exception::class)
        fun newer(current: String, app: String, asset: String): Release? {
            val c = open("$api/repos/$REPO/releases/latest")
            c.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (c.responseCode == 404) return null                // no release published yet
                if (c.responseCode != 200) throw IOException("GitHub HTTP " + c.responseCode)
                val o = JSONObject(text(c))
                var version = o.getString("tag_name").replaceFirst(Regex("^v"), "")
                var apk: String? = null
                var versions: String? = null
                var sums: String? = null
                var sig: String? = null
                val assets = o.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val u = a.getString("browser_download_url")
                    when (a.getString("name")) {
                        asset -> apk = u
                        "versions.json" -> versions = u
                        "SHA256SUMS" -> sums = u
                        "SHA256SUMS.sig" -> sig = u
                    }
                }
                if (apk == null || sums == null || sig == null) return null   // still being put together (or from before the checksums)
                if (versions != null) version = JSONObject(get(versions)).optString(app, version)
                if (compare(version, current) <= 0) return null
                val list = get(sums)
                if (!signed(list.toByteArray(Charsets.UTF_8), get(sig))) throw IOException(UNSIGNED)
                val sha = sum(list, asset) ?: return null
                return Release(version, apk, o.optString("body", "").trim(), sha)
            } finally {
                c.disconnect()
            }
        }

        private fun get(url: String): String {
            val c = open(url)
            try {
                if (c.responseCode != 200) throw IOException("GitHub HTTP " + c.responseCode)
                return text(c)
            } finally {
                c.disconnect()
            }
        }

        /** [sig] (base64) is one of [KEYS]' signature of [list]. */
        fun signed(list: ByteArray, sig: String): Boolean {
            val raw = try { Base64.getDecoder().decode(sig.trim()) } catch (e: IllegalArgumentException) { return false }
            return KEYS.any { k -> Ed25519.verify(ByteArray(32) { k.substring(it * 2, it * 2 + 2).toInt(16).toByte() }, list, raw) }
        }

        /** The SHA-256 of [name] in a SHA256SUMS list ("<hex>  <name>" per line, as sha256sum writes it). */
        fun sum(list: String, name: String): String? = list.lineSequence()
            .map { it.trim().split(Regex("\\s+\\*?"), 2) }
            .firstOrNull { it.size == 2 && it[1] == name && it[0].matches(Regex("[0-9a-fA-F]{64}")) }?.get(0)?.lowercase()

        fun hex(b: ByteArray): String = b.joinToString("") { String.format("%02x", it) }

        private fun text(c: HttpURLConnection): String {
            val buf = ByteArrayOutputStream()
            c.inputStream.use { copy(it, buf, 0, null, null) }
            return buf.toString("UTF-8")
        }

        /** "1.10.0" is newer than "1.9": compares the numbers between the dots. */
        fun compare(a: String, b: String): Int {
            val la = legacy(a)
            if (la != legacy(b)) return if (la) -1 else 1
            val x = a.trim().split(".")
            val y = b.trim().split(".")
            for (i in 0 until maxOf(x.size, y.size)) {
                val d = num(x, i).compareTo(num(y, i))
                if (d != 0) return d
            }
            return 0
        }

        /**
         * The numbers before they restarted at 0.1.0 (October 2026: 1.3.0 up to 2.24.2) are older than every later one, so a TV
         * or computer still on them is offered the update. dev/release.sh refuses to reach 1.3.0 while this is here: drop it then.
         */
        fun legacy(v: String): Boolean {
            val p = v.trim().split(".")
            return num(p, 0) == 2 || (num(p, 0) == 1 && num(p, 1) >= 3)
        }

        private fun num(parts: List<String>, i: Int): Int =
            if (i < parts.size) parts[i].replace(Regex("\\D.*"), "").toIntOrNull() ?: 0 else 0

        private fun open(url: String): HttpURLConnection {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 20000
            c.setRequestProperty("User-Agent", "tv-remote")
            return c
        }

        private fun copy(input: InputStream, out: OutputStream, total: Int, progress: Progress?, md: MessageDigest?) {
            val b = ByteArray(65536)
            var done = 0L
            var last = -1
            while (true) {
                val n = input.read(b)
                if (n <= 0) break
                out.write(b, 0, n)
                md?.update(b, 0, n)
                done += n
                val pct = if (total > 0) (done * 100 / total).toInt() else 0
                if (progress != null && pct != last) { last = pct; progress.at(pct) }
            }
            if (total > 0 && done != total.toLong()) throw IOException("download incomplete ($done of $total bytes)")   // a dropped connection ends the stream like a finished one
        }
    }
}
