package dev.tvremote.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * App names and pictures of a TV, for a remote that is not running on it. Half of this runs on the TV: the phone copies
 * its own APK there and starts [Meta.main] with app_process, the way scrcpy-server is started. As the shell user it can ask
 * the TV's package manager what a phone cannot see from outside: each app's display name and its banner or icon.
 * It prints "package<TAB>name" lines and leaves one PNG per app that can be opened in the directory it is given.
 */
object Meta {
    @JvmStatic
    fun main(args: Array<String>) {
        val dir = File(args[0])
        dir.mkdirs()
        @Suppress("DEPRECATION")
        Looper.prepareMainLooper()
        val at = Class.forName("android.app.ActivityThread")
        val ctx = at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null)) as Context
        val pm = ctx.packageManager
        for (ai in pm.getInstalledApplications(0)) {
            val pkg = ai.packageName
            val label = try { ai.loadLabel(pm).toString() } catch (e: Exception) { pkg }
            println(pkg + "\t" + label.replace('\n', ' ').replace('\t', ' '))
            val png = File(dir, "$pkg.png")
            val shown = ai.flags and ApplicationInfo.FLAG_SYSTEM == 0 || pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null
            if (!shown || png.length() > 0 && png.lastModified() >= File(ai.sourceDir).lastModified()) continue   // drawn already, and the app has not been updated since
            try {
                var d: Drawable? = null
                try { d = pm.getApplicationBanner(ai) } catch (ignored: Exception) { }   // TV apps carry a wide banner; fall back to the icon
                val banner = d != null
                val drawable = d ?: pm.getApplicationIcon(ai)
                val bmp = Bitmap.createBitmap(if (banner) 240 else 96, if (banner) 135 else 96, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, bmp.width, bmp.height)
                drawable.draw(Canvas(bmp))
                val tmp = File(dir, "$pkg.tmp")
                FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                tmp.renameTo(png)
            } catch (ignored: Exception) {
            }
        }
        System.out.flush()
        System.exit(0)                                                // the system context leaves threads running
    }
}

/**
 * The phone's side: runs [Meta] on the TV and answers from what it printed and drew. With a [cache] (one folder per TV) the
 * names and pictures are kept on the phone: the next time the list shows at once from them, and Meta runs in the background
 * only to catch apps installed or updated since. Without one, every opening waited for Meta and fetched each picture again.
 */
class RemoteLabels(private val link: AdbLink, private val apk: File, private val cache: File? = null) : Manage.Labels {
    @Volatile private var names: Map<String, String>? = null
    @Volatile private var ok = false
    @Volatile private var stamps: Map<String, String>? = null         // package -> "time size" of its PNG on the TV, once Meta has run
    private val running = Any()

    /** Every app's display name, by package. */
    @Synchronized
    fun names(): Map<String, String> {
        names?.let { return it }
        val saved = load()
        if (saved != null) {
            names = saved
            ok = true
            Thread({ ask() }, "meta-refresh").apply { isDaemon = true }.start()
            return saved
        }
        return ask()
    }

    /** One run of Meta on the TV. */
    private fun ask(): Map<String, String> = synchronized(running) {
        val out = HashMap<String, String>()
        try {
            val a = link.get()
            // one file per build, written whole and then renamed: see CLEAN
            val remote = "/data/local/tmp/tvr-meta-${apk.length()}.apk"
            if (a.shell("[ -f $remote ] && echo y").trim() != "y") {
                a.shell(CLEAN)
                val s = a.open("exec:cat > $remote.part")
                apk.inputStream().use { input ->
                    val buf = ByteArray(65536)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        s.write(if (n == buf.size) buf else buf.copyOf(n))
                    }
                }
                s.close()
                val size = apk.length().toString()
                for (i in 0 until 40) {
                    if (a.shell("wc -c < $remote.part").trim() == size) break
                    Thread.sleep(150)
                }
                a.shell("mv $remote.part $remote")
            }
            for (line in a.shell("CLASSPATH=$remote app_process / dev.tvremote.core.Meta $DIR 2>/dev/null").split("\n")) {
                val tab = line.indexOf('\t')
                if (tab > 0) out[line.substring(0, tab)] = line.substring(tab + 1).trim()
            }
            if (out.isNotEmpty()) {
                val st = HashMap<String, String>()
                for (line in a.shell("cd $DIR && stat -c '%n %Y %s' *.png 2>/dev/null").split("\n")) {
                    val p = line.trim().split(" ")
                    if (p.size == 3 && p[0].endsWith(".png")) st[p[0].removeSuffix(".png")] = p[1] + " " + p[2]
                }
                stamps = st
                ok = true
                names = out
                save(out)
            }
        } catch (ignored: Exception) {
        }
        names ?: out.also { names = it }                              // failed with nothing saved: package names are shown, as before
    }

    override fun label(pkg: String): String? = names()[pkg]

    override fun icons(): Boolean { names(); return ok }

    /** The PNG [Meta] drew for this app, or null: the phone's copy while it is still the TV's, else fetched (and kept). */
    fun icon(pkg: String): ByteArray? {
        if (!ok || !pkg.matches(Regex("[\\w.]+"))) return null
        val file = cache?.let { File(it, "$pkg.png") }
        val stamp = cache?.let { File(it, "$pkg.stamp") }
        val st = stamps
        try {
            if (file != null && file.length() > 0 && (st == null || st[pkg] != null && stamp!!.readText() == st[pkg])) return file.readBytes()
        } catch (ignored: IOException) {
        }
        return try {
            val s = link.get().open("exec:cat $DIR/$pkg.png")
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(65536)
            s.use {
                while (true) {
                    val n = it.read(buf, 0, buf.size, 10000)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
            }
            if (out.size() == 0) return null
            val png = out.toByteArray()
            if (file != null) try {
                file.parentFile?.mkdirs()
                file.writeBytes(png)
                stamp!!.writeText(st?.get(pkg) ?: "")
            } catch (ignored: IOException) {
            }
            png
        } catch (e: IOException) { null }
    }

    private fun load(): Map<String, String>? = try {
        val o = org.json.JSONObject(File(cache ?: return null, "names.json").readText())
        o.keys().asSequence().associateWith { o.getString(it) }.takeIf { it.isNotEmpty() }
    } catch (e: Exception) { null }

    private fun save(m: Map<String, String>) {
        val dir = cache ?: return
        try {
            dir.mkdirs()
            val tmp = File(dir, "names.json.tmp")
            tmp.writeText(org.json.JSONObject(m).toString())
            tmp.renameTo(File(dir, "names.json"))
        } catch (ignored: Exception) {
        }
    }

    private companion object {
        // The computer, the phone app and TV Setup each bring their own APK. With one shared name one of them overwrote it while
        // another started it: a crash on the TV each time. Builds older than a few days are cleared; one still in use stays
        // readable to the process that has it open.
        const val CLEAN = "find /data/local/tmp -maxdepth 1 -name 'tvr-meta*.apk' -mtime +3 -delete 2>/dev/null; rm -f /data/local/tmp/tvr-meta.apk"
        const val DIR = "/data/local/tmp/tvr-meta"
    }
}
