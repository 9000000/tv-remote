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

/**
 * Update check shared by the phone app and the TV app: asks GitHub for the latest release of this project and, if it is
 * newer than the running app, gives the download link of this app's APK. Nothing else is sent anywhere.
 */
class Release private constructor(val version: String, val url: String, val notes: String) {
    fun interface Progress { fun at(percent: Int) }

    companion object {
        const val REPO = "HoangTran0410/tv-remote"

        /**
         * The latest release, if it carries a newer version of this app. Each app has its own version number: a release lists
         * them in a small file, versions.json ({"android": "2.1.0", "tv": "2.0.3"}), so an app is only offered an update when
         * it changed itself. Releases without that file (before 2.1) are compared by their tag. null when up to date.
         */
        @Throws(Exception::class)
        fun newer(current: String, app: String, asset: String): Release? {
            val c = open("https://api.github.com/repos/$REPO/releases/latest")
            c.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (c.responseCode == 404) return null                // no release published yet
                if (c.responseCode != 200) throw IOException("GitHub HTTP " + c.responseCode)
                val o = JSONObject(text(c))
                var version = o.getString("tag_name").replaceFirst(Regex("^v"), "")
                var apk: String? = null
                var versions: String? = null
                val assets = o.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (asset == a.getString("name")) apk = a.getString("browser_download_url")
                    if ("versions.json" == a.getString("name")) versions = a.getString("browser_download_url")
                }
                if (apk == null) return null
                if (versions != null) {
                    val v = open(versions)
                    try {
                        if (v.responseCode != 200) throw IOException("GitHub HTTP " + v.responseCode)
                        version = JSONObject(text(v)).optString(app, version)
                    } finally {
                        v.disconnect()
                    }
                }
                return if (compare(version, current) > 0) Release(version, apk, o.optString("body", "").trim()) else null
            } finally {
                c.disconnect()
            }
        }

        private fun text(c: HttpURLConnection): String {
            val buf = ByteArrayOutputStream()
            c.inputStream.use { copy(it, buf, 0, null) }
            return buf.toString("UTF-8")
        }

        @Throws(IOException::class)
        fun download(url: String, to: File, progress: Progress?) {
            val c = open(url)
            try {
                if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
                c.inputStream.use { input -> FileOutputStream(to).use { out -> copy(input, out, c.contentLength, progress) } }
            } finally {
                c.disconnect()
            }
        }

        /** "1.10.0" is newer than "1.9": compares the numbers between the dots. */
        fun compare(a: String, b: String): Int {
            val x = a.trim().split(".")
            val y = b.trim().split(".")
            for (i in 0 until maxOf(x.size, y.size)) {
                val d = num(x, i).compareTo(num(y, i))
                if (d != 0) return d
            }
            return 0
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

        private fun copy(input: InputStream, out: OutputStream, total: Int, progress: Progress?) {
            val b = ByteArray(65536)
            var done = 0L
            var last = -1
            while (true) {
                val n = input.read(b)
                if (n <= 0) break
                out.write(b, 0, n)
                done += n
                val pct = if (total > 0) (done * 100 / total).toInt() else 0
                if (progress != null && pct != last) { last = pct; progress.at(pct) }
            }
        }
    }
}
