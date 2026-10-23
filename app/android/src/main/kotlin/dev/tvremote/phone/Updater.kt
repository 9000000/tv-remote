package dev.tvremote.phone

import dev.tvremote.core.T
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import dev.tvremote.update.Release
import java.io.File
import java.io.FileInputStream

// Updates from GitHub releases: checking, asking, downloading and handing the APK to Android's installer.

/** "2.5.4 · 13/10/2026": this app's version and the day it was built. */
internal fun MainActivity.versionLine(): String = version() + released().let { if (it.isEmpty()) "" else " · $it" }

/** The day this build was made, as dd/mm/yyyy ("" when unknown). */
internal fun MainActivity.released(): String = try {
    getString(resources.getIdentifier("released", "string", packageName)).split("-").let { it[2] + "/" + it[1] + "/" + it[0] }
} catch (e: Exception) { "" }

internal fun MainActivity.version(): String =
    try { packageManager.getPackageInfo(packageName, 0).versionName ?: "0" } catch (e: Exception) { "0" }

internal fun MainActivity.checkUpdate(manual: Boolean) {
    if (manual) updateNote.text = T("setup.checking")
    Thread {
        var found: Release? = null
        var error: String? = null
        try { found = Release.newer(version(), "android", "tv-remote.apk") } catch (e: Exception) { error = e.message ?: e.javaClass.simpleName }
        val r = found
        val err = error
        ui.post {
            if (isFinishing) return@post
            if (err == null) {
                update = r
                showUpdate()
            }
            if (manual) updateNote.text = when {
                err != null -> T("updater.could_not_check", "error" to err)
                r == null -> T("updater.you_have_the_latest", "version" to version())
                else -> T("updater.new_version", "version" to r.version)
            }
            if (manual && r != null) askUpdate()                  // asked for it: offer the update right here
        }
    }.start()
}

internal fun MainActivity.showUpdate() {
    val u = update
    updateBtn.visibility = if (u == null) View.GONE else View.VISIBLE
    if (u != null && !updating) updateBtn.setLabel("@download|" + T("updater.new_version_tap_to", "version" to u.version))
}

/** The download shows both on the TV list and in Settings, wherever the update was started from. */
internal fun MainActivity.progress(s: String) {
    updateBtn.setLabel(s)
    updateNote.text = s
}

internal fun MainActivity.askUpdate() {
    val r = update
    if (r == null || updating) return
    val all = r.notes.replace("**", "").replace("\r", "")         // release notes are Markdown
    val notes = if (all.length > 900) all.substring(0, 900) + "..." else all

    Popup.Builder(this).setTitle(T("updater.version_you_have", "version" to r.version, "current" to version()))
        .setMessage(notes.ifEmpty { T("updater.download_and_install_the") })
        .setPositiveButton(T("setup.update_ok")) { _, _ -> startUpdate(r) }.setNegativeButton(T("common.later"), null).show()
}

internal fun MainActivity.startUpdate(r: Release) { if (installAllowed(r)) download(r) }

/** Downloads the APK from the release, then hands it to Android, which asks the person to confirm the install. */
private fun MainActivity.download(r: Release) {
    updating = true
    progress(T("updater.downloading_2", "version" to r.version))
    Thread {
        var error: String? = null
        try {
            val apk = File(cacheDir, "update.apk")
            r.download(apk) { pct -> ui.post { progress(T("updater.downloading", "version" to r.version, "pct" to pct)) } }
            val pi = packageManager.packageInstaller
            val id = pi.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
            pi.openSession(id).use { session ->
                session.openWrite("apk", 0, apk.length()).use { out ->
                    FileInputStream(apk).use { input ->
                        val b = ByteArray(65536)
                        while (true) {
                            val n = input.read(b)
                            if (n <= 0) break
                            out.write(b, 0, n)
                        }
                        session.fsync(out)
                    }
                }
                val back = Intent(this, MainActivity::class.java).setAction(INSTALL_RESULT)
                session.commit(PendingIntent.getActivity(this, id, back,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) 0x02000000 /* FLAG_MUTABLE */ else 0)).intentSender)
            }
            apk.delete()
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
        val err = error
        ui.post {
            if (err == null) {
                progress(T("updater.waiting_for_android_to"))
                return@post
            }
            updating = false
            showUpdate()
            failed(r, T("updater.could_not_update", "error" to err))
        }
    }.start()
}

/**
 * Android 8+ installs from this app only once "Install unknown apps" is on for it. Left to Android, it asks in the middle of
 * the install and drops the install when the person comes back from allowing it (STATUS_FAILURE_ABORTED: "Update cancelled"
 * though nobody cancelled, reported by a user; the same on the Android 14 emulator). So it is asked before the download, and
 * the update goes on by itself on return ([resumeUpdate]).
 */
private fun MainActivity.installAllowed(r: Release): Boolean {
    if (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) return true
    Popup.Builder(this).setTitle(T("updater.allow_title")).setMessage(T("updater.allow_body"))
        .setPositiveButton(T("updater.allow_open")) { _, _ ->
            updateAfterAllow = r
            try { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
            catch (e: Exception) { updateAfterAllow = null; download(r) }   // no such screen here: Android asks itself
        }.setNegativeButton(T("common.later"), null).show()
    return false
}

/** Back from the settings [installAllowed] opened: the update starts if installs are now allowed. */
internal fun MainActivity.resumeUpdate() {
    val r = updateAfterAllow ?: return
    updateAfterAllow = null
    if (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) startUpdate(r)
}

/** Android reports back with the install's progress; true when this intent was that. */
internal fun MainActivity.installResult(i: Intent): Boolean {
    if (INSTALL_RESULT != i.action) return false
    val status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
        @Suppress("DEPRECATION")
        val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        if (confirm != null) startActivity(confirm)
        return true
    }
    if (status == PackageInstaller.STATUS_SUCCESS) return true    // the app is restarted by the install anyway
    updating = false
    showUpdate()
    if (status == PackageInstaller.STATUS_FAILURE_ABORTED && !samsung()) message(T("updater.update_cancelled"), true)
    else failed(update, if (status == PackageInstaller.STATUS_FAILURE_ABORTED) T("updater.update_cancelled")
        else T("updater.could_not_install_the", "error" to i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)))
    return true
}

private fun samsung() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

/**
 * The install failed: the same file from the browser, opened and installed by hand, still works where only this app's
 * install is refused (a maker's installer, a broken download). A Samsung's Auto Blocker refuses both, and may say only
 * "cancelled": there the way to turn it off is added, and the popup shows on a cancel too.
 */
private fun MainActivity.failed(r: Release?, text: String) {
    if (r == null) { message(text, true); return }
    Popup.Builder(this).setTitle(T("updater.failed_title"))
        .setMessage(text + "\n\n" + T("updater.failed_body") + if (samsung()) "\n\n" + T("updater.failed_samsung") else "")
        .setPositiveButton(T("updater.failed_browser")) { _, _ ->
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url))) } catch (e: Exception) { message(r.url, true) }
        }.setNegativeButton(T("common.later"), null).show()
}
