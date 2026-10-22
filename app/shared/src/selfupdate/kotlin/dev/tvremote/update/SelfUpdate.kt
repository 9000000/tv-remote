package dev.tvremote.update

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.Settings
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * TV Home, TV Channels and TV Games keep themselves up to date, asking first (app/DESIGN-TV.md "Update question"). Opened, the
 * app looks at the latest release at most every 12 hours ([Release.newer]: its own number in versions.json, its APK checked
 * against the signed SHA256SUMS) and, when it carries a newer version the person did not skip or put off, the app asks with
 * its own popup: Update / Later (a day) / Skip this version. Update downloads it here and hands it to Android's installer,
 * which asks once more. The first time, Android 8+ wants this app allowed to install apps: [install] opens that switch
 * first (Android's own path ends the install there), and [check] goes on with the download once the person is back with it
 * on, without asking again. Only these three apps compile this file
 * (src/selfupdate); the phone and computer apps still put them on the TV over ADB (OwnApp), which needs no question.
 */
class SelfUpdate(private val a: Activity, private val app: String, private val asset: String) {
    private val prefs = a.getSharedPreferences("self_update", Context.MODE_PRIVATE)
    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var cancelled = false

    /**
     * Asks GitHub when it is time, then [ask] on the main thread with a newer release this person still wants to hear about.
     * Back from allowing installs for this update: [go] with it at once. Called from onResume, so that return is seen.
     */
    fun check(ask: (Release) -> Unit, go: (Release) -> Unit) {
        val waiting = prefs.getString("allow", null)
        if (waiting != null) prefs.edit().remove("allow").apply()     // back without the switch on: a no, asked again at the next check
        if (waiting != null && allowed()) {
            Thread({
                val r = try { Release.newer(version(), app, asset) } catch (e: Exception) { null }
                if (r != null) ui.post { if (!a.isFinishing && !a.isDestroyed) go(r) }
            }, "self-update").apply { isDaemon = true }.start()
            return
        }
        val now = System.currentTimeMillis()
        if (now < prefs.getLong("later", 0) || now - prefs.getLong("checked", 0) < 12 * HOUR) return
        prefs.edit().putLong("checked", now).apply()
        Thread({
            val r = try { Release.newer(version(), app, asset) } catch (e: Exception) { null }   // offline, GitHub busy: next time
            if (r != null && r.version != prefs.getString("skip", null)) ui.post { if (!a.isFinishing && !a.isDestroyed) ask(r) }
        }, "self-update").apply { isDaemon = true }.start()
    }

    fun later() = prefs.edit().putLong("later", System.currentTimeMillis() + 24 * HOUR).apply()
    fun skip(r: Release) = prefs.edit().putString("skip", r.version).apply()
    fun cancel() { cancelled = true }
    private fun allowed() = Build.VERSION.SDK_INT < 26 || a.packageManager.canRequestPackageInstalls()
    fun version(): String = try { a.packageManager.getPackageInfo(a.packageName, 0).versionName ?: "0" } catch (e: Exception) { "0" }

    /**
     * Downloads [r] ([progress] in percent, on the main thread) and hands it to Android's installer, which shows its own
     * question ([Installed]); [failed] gets what went wrong (a cancel says nothing), on the main thread. false: Android's
     * "install unknown apps" switch was opened instead, and [check] goes on from there.
     */
    fun install(r: Release, progress: (Int) -> Unit, failed: (String) -> Unit): Boolean {
        if (!allowed()) try {
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName)))
            prefs.edit().putString("allow", r.version).apply()
            return false
        } catch (e: Exception) { }                                    // no such screen on this TV: Android's installer asks its own way
        cancelled = false
        Installed.failed = { m -> ui.post { failed(m) } }
        Thread({
            val apk = File(a.cacheDir, "update.apk")
            try {
                var last = -1
                r.download(apk) { pct -> if (cancelled) throw IOException(CANCELLED); if (pct != last) { last = pct; ui.post { progress(pct) } } }
                val pi = a.packageManager.packageInstaller
                val id = pi.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
                pi.openSession(id).use { s ->
                    s.openWrite("apk", 0, apk.length()).use { out -> FileInputStream(apk).use { it.copyTo(out, 65536) }; s.fsync(out) }
                    val back = Intent(a, Installed::class.java)       // explicit: Android 14 refuses a mutable PendingIntent to an open one
                    s.commit(PendingIntent.getBroadcast(a, id, back,
                        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) 0x02000000 /* FLAG_MUTABLE */ else 0)).intentSender)
                }
            } catch (e: Exception) {
                if (e.message != CANCELLED) ui.post { failed(e.message ?: e.javaClass.simpleName) }
            } finally {
                apk.delete()                                          // the session holds its own copy
            }
        }, "self-update").apply { isDaemon = true }.start()
        return true
    }

    /** Android's answer about the install: its question to show, or why it failed (the app is closed by a good one anyway). */
    class Installed : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val ask = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    try { c.startActivity(ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { failed?.invoke(e.message ?: "") }
                }
                PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> {}   // done, or the person said no
                else -> failed?.invoke(i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "")
            }
        }

        companion object { @Volatile var failed: ((String) -> Unit)? = null }
    }

    companion object {
        private const val HOUR = 3600_000L
        private const val CANCELLED = "cancelled"
    }
}
