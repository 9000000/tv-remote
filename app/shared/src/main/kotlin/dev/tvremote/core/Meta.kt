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

/** The phone's side: runs [Meta] on the TV once per connection and answers from what it printed and drew. */
class RemoteLabels(private val link: AdbLink, private val apk: File) : Manage.Labels {
    private var names: Map<String, String>? = null
    @Volatile private var ok = false

    /** Every app's display name, by package; asked from the TV once per connection. */
    @Synchronized
    fun names(): Map<String, String> {
        names?.let { return it }
        val out = HashMap<String, String>()
        try {
            val a = link.get()
            val size = apk.length().toString()
            val check = "[ -f $REMOTE ] && wc -c < $REMOTE || echo 0"
            if (a.shell(check).trim() != size) {
                val s = a.open("exec:cat > $REMOTE")
                apk.inputStream().use { input ->
                    val buf = ByteArray(65536)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        s.write(if (n == buf.size) buf else buf.copyOf(n))
                    }
                }
                s.close()
                for (i in 0 until 40) {
                    if (a.shell(check).trim() == size) break
                    Thread.sleep(150)
                }
            }
            for (line in a.shell("CLASSPATH=$REMOTE app_process / dev.tvremote.core.Meta $DIR 2>/dev/null").split("\n")) {
                val tab = line.indexOf('\t')
                if (tab > 0) out[line.substring(0, tab)] = line.substring(tab + 1).trim()
            }
            ok = out.isNotEmpty()
        } catch (ignored: Exception) {
        }
        names = out                                                   // failed: package names are shown, as before
        return out
    }

    override fun label(pkg: String): String? = names()[pkg]

    override fun icons(): Boolean { names(); return ok }

    /** The PNG [Meta] drew for this app, or null. */
    fun icon(pkg: String): ByteArray? {
        if (!ok || !pkg.matches(Regex("[\\w.]+"))) return null
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
            if (out.size() > 0) out.toByteArray() else null
        } catch (e: IOException) { null }
    }

    private companion object {
        const val REMOTE = "/data/local/tmp/tvr-meta.apk"
        const val DIR = "/data/local/tmp/tvr-meta"
    }
}
