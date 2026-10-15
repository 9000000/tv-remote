package dev.tvremote.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Looper
import java.io.File
import java.io.FileOutputStream

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
