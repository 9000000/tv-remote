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
 * on, without asking again. When TV Setup is on the TV with auto-update on and ADB, it installs the update quietly (AutoUpdate):
 * the app then stays silent for [UpdatePlan.GRACE] after it first saw the release, and asks only if that passed ([leftToSetup]). Only these three apps compile this file
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
            if (r != null && r.version != prefs.getString("skip", null) && !leftToSetup(r)) ui.post { if (!a.isFinishing && !a.isDestroyed) ask(r) }
        }, "self-update").apply { isDaemon = true }.start()
    }

    /**
     * true when TV Setup will install [r] by itself (it says so, [UpdatePlan]) and it is not yet [UpdatePlan.GRACE] old to us: no
     * question then, the app is simply newer next time. Off the main thread. An old, missing, switched-off or ADB-less TV Setup
     * answers nothing useful: false, ask as always. A TV Setup that lies can only delay this popup by the grace period (nothing
     * is installed on its word), so the answer is trusted by its provider authority being TV Setup's package, not by a pinned key.
     */
    private fun leftToSetup(r: Release): Boolean {
        val now = System.currentTimeMillis()
        if (prefs.getString("seen", null) != r.version) prefs.edit().putString("seen", r.version).putLong("seen_at", now).apply()
        val first = prefs.getLong("seen_at", now)
        if (now - first >= UpdatePlan.GRACE) return false             // asked for long enough: no more waiting
        val silent = try {
            val info = a.packageManager.resolveContentProvider(SETUP_AUTH, 0)
            if (info?.packageName != SETUP_PKG) null
            else a.contentResolver.call(Uri.parse("content://$SETUP_AUTH"), "updates", null, null)?.getString("json")?.let { org.json.JSONObject(it).optBoolean("silent") }
        } catch (e: Exception) { null }
        return UpdatePlan.quiet(silent, first, now)
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
            try {
                var last = -1
                stage(a, r, Intent(a, Installed::class.java), false) { pct ->    // explicit: Android 14 refuses a mutable PendingIntent to an open one
                    if (cancelled) throw IOException(CANCELLED); if (pct != last) { last = pct; ui.post { progress(pct) } }
                }
            } catch (e: Exception) {
                if (e.message != CANCELLED) ui.post { failed(e.message ?: e.javaClass.simpleName) }
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
        private const val SETUP_PKG = "dev.tvremote.setup"
        private const val SETUP_AUTH = "dev.tvremote.setup.home"       // TV Setup's HomeInfo

        /**
         * Downloads [r] and commits it to a PackageInstaller session whose answer goes to [back] (a broadcast to [Installed], or
         * with [toActivity] to an activity: the phone app's own flow). Blocking, off the main thread; throws what went wrong.
         */
        fun stage(c: Context, r: Release, back: Intent, toActivity: Boolean, progress: (Int) -> Unit) {
            val apk = File(c.cacheDir, "update.apk")
            try {
                r.download(apk, progress)
                commit(c, apk, back, toActivity, false)
            } finally {
                apk.delete()                                          // the session holds its own copy
            }
        }

        /** Commits [apk] (checked already) to a PackageInstaller session; [quiet]: no question where Android allows (12+, see the phone's QuietUpdate). */
        fun commit(c: Context, apk: File, back: Intent, toActivity: Boolean, quiet: Boolean) {
            val pi = c.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (quiet && Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                s.openWrite("apk", 0, apk.length()).use { out -> FileInputStream(apk).use { it.copyTo(out, 65536) }; s.fsync(out) }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) 0x02000000 /* FLAG_MUTABLE */ else 0)
                s.commit((if (toActivity) PendingIntent.getActivity(c, id, back, flags) else PendingIntent.getBroadcast(c, id, back, flags)).intentSender)
            }
        }

        /** Samsung's Auto Blocker refuses installs from outside the store and may say only "cancelled": the phone app words its failure for it. */
        fun samsung() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }
}
