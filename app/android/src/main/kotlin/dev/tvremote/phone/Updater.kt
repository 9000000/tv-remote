package dev.tvremote.phone

import dev.tvremote.core.T
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
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

/** Downloads the APK from the release, then hands it to Android, which asks the person to confirm the install. */
internal fun MainActivity.startUpdate(r: Release) {
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
            message(T("updater.could_not_update", "error" to err), true)
        }
    }.start()
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
    message(if (status == PackageInstaller.STATUS_FAILURE_ABORTED) T("updater.update_cancelled")
    else T("updater.could_not_install_the", "error" to i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)), true)
    return true
}
