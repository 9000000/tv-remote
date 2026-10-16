package dev.tvremote.phone

import android.app.AlertDialog
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
    if (manual) updateNote.text = t("Đang kiểm tra...", "Checking...")
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
                err != null -> t("Không kiểm tra được: ", "Could not check: ") + err
                r == null -> t("Đang dùng bản mới nhất (", "You have the latest version (") + version() + ")."
                else -> t("Có bản mới ", "New version ") + r.version
            }
            if (manual && r != null) askUpdate()                  // asked for it: offer the update right here
        }
    }.start()
}

internal fun MainActivity.showUpdate() {
    val u = update
    updateBtn.visibility = if (u == null) View.GONE else View.VISIBLE
    if (u != null && !updating) updateBtn.setLabel("@download|" + t("Có bản mới ", "New version ") + u.version + t(" · bấm để cập nhật", " · tap to update"))
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

    AlertDialog.Builder(this).setTitle(t("Bản mới ", "Version ") + r.version + t(" (đang dùng ", " (you have ") + version() + ")")
        .setMessage(notes.ifEmpty { t("Tải và cài bản mới?", "Download and install the new version?") })
        .setPositiveButton(t("Cập nhật", "Update")) { _, _ -> startUpdate(r) }.setNegativeButton(t("Để sau", "Later"), null).show()
}

/** Downloads the APK from the release, then hands it to Android, which asks the person to confirm the install. */
internal fun MainActivity.startUpdate(r: Release) {
    updating = true
    progress(t("Đang tải bản ", "Downloading ") + r.version + "...")
    Thread {
        var error: String? = null
        try {
            val apk = File(cacheDir, "update.apk")
            r.download(apk) { pct -> ui.post { progress(t("Đang tải bản ", "Downloading ") + r.version + "... " + pct + "%") } }
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
                progress(t("Đang chờ Android xác nhận cài đặt...", "Waiting for Android to confirm the install..."))
                return@post
            }
            updating = false
            showUpdate()
            message(t("Không cập nhật được: ", "Could not update: ") + err, true)
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
    message(if (status == PackageInstaller.STATUS_FAILURE_ABORTED) t("Đã hủy cập nhật.", "Update cancelled.")
    else t("Không cài được bản mới: ", "Could not install the new version: ") + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE), true)
    return true
}
