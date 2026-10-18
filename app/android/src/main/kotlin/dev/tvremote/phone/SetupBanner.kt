package dev.tvremote.phone

import dev.tvremote.core.T
import android.graphics.Color
import android.view.View
import dev.tvremote.core.Errors
import dev.tvremote.core.ontv.SetupNeed
import dev.tvremote.core.ontv.SetupUpdate
import dev.tvremote.core.picture.Tv
import dev.tvremote.update.Release

// A newer TV Setup on the connected TV, offered on the remote screen itself: most people manage the TV from here and never
// open the Manage page, where the web UI offers it too. The update itself runs there (Manage.kt's /api/setup/update, with its
// progress and the TV's rollback), started by the banner's button.

/** Once the session is up: asks the TV for its TV Setup and GitHub for the newest, at most every few hours per TV. */
internal fun MainActivity.checkSetup(tv: Tv, h: String) {
    val now = System.currentTimeMillis()
    if (now - (setupChecked[h] ?: 0L) < 6 * 3600_000L) { showSetup(h); return }
    setupChecked[h] = now
    Thread({
        val found = try {
            val installed = Regex("versionName=(\\S+)").find(tv.shell("dumpsys package ${SetupUpdate.PKG} 2>/dev/null | grep -m1 versionName="))?.groupValues?.get(1) ?: ""
            if (installed.isEmpty()) null                             // no TV Setup: offered where it is needed (Automation), not here
            else {
                val s = SetupUpdate.parse(tv.shell("cat ${SetupUpdate.DIR}/state 2>/dev/null; echo '|'; cat ${SetupUpdate.DIR}/bad 2>/dev/null"))
                val r = try { Release.newer(installed, "tv", "tv-setup.apk") } catch (e: Exception) { null }
                SetupNews(installed, if (r != null && !SetupUpdate.bad(s, r.version)) r.version else "", s.optString("state"), s.optLong("at"), s.optString("version"), s.optString("from"))
            }
        } catch (e: Exception) { setupChecked.remove(h); null }      // the session closed meanwhile: asked again next time
        ui.post {
            if (isFinishing) return@post
            if (found == null) setupNews.remove(h) else setupNews[h] = found
            showSetup(h)
        }
    }, "setup-check").start()
}

/** After the Manage page: an update may have run there. Asked of the TV only when the last look is stale or was cleared. */
internal fun MainActivity.setupAgain() {
    val tv = tv ?: return
    val h = host ?: return
    if (isFinishing || !tv.ready()) return
    checkSetup(tv, h)
}

internal class SetupNews(val installed: String, val latest: String, val state: String, val at: Long, val version: String, val from: String) {
    /** A new version broke on this TV in the last 3 days and was taken back: said once, until "Got it". */
    fun undone(seen: String) = state == "rolledback" && System.currentTimeMillis() - at < 3 * 86400_000L && seen != at.toString()
}

internal fun MainActivity.showSetup(h: String? = host) {
    val n = if (h != null && h == host && tv != null) setupNews[h] else null
    val undone = n != null && n.undone(pref("setupUndo"))
    val offer = n != null && n.latest.isNotEmpty() && n.latest != pref("setupLater")
    setupBar.visibility = if (undone || offer) View.VISIBLE else View.GONE
    if (n == null || !(undone || offer)) return
    setupBar.background = box(if (undone) ERR_BG else GREEN, 14)
    setupBar.setTextColor(if (undone) ERR_TEXT else Color.WHITE)       // white on the green fill, in both themes
    setupBar.setLabel(if (undone) "@alert|" + T("setup_banner.tv_setup_failed_the", "version" to n.version, "from" to n.from)
        else "@download|" + T("setup_banner.new_tv_setup_for", "latest" to n.latest))
    setupBar.setOnClickListener {
        if (undone) Popup.Builder(this).setTitle(T("setup_banner.tv_setup_failed_on", "version" to n.version))
            .setMessage(T("setup_banner.the_tv_went_back", "from" to n.from))
            .setPositiveButton(T("setup_banner.got_it")) { _, _ -> prefs.edit().putString("setupUndo", n.at.toString()).apply(); showSetup(h) }.show()
        else Popup.Builder(this).setTitle(T("setup_banner.tv_setup_the_tv", "latest" to n.latest, "installed" to n.installed))
            .setMessage(T("setup_banner.the_tv_installs_it"))
            .setPositiveButton(T("setup.update_ok")) { _, _ ->
                setupNews.remove(h); setupChecked.remove(h)                // asked again next time: done, or put back
                showSetup(h)
                managing.open(h, then = "updateSetup()")                  // its banner shows how far it is
            }
            .setNegativeButton(T("common.later")) { _, _ -> prefs.edit().putString("setupLater", n.latest).apply(); showSetup(h) }.show()
    }
}

/** When [e] says TV Setup on the TV is missing or too old for a feature (SetupNeed), asks to install or update it, with the
 *  button that does it on the Manage page; true when it did. Every feature's errors go through here first. */
internal fun MainActivity.offerSetup(e: Throwable): Boolean {
    val say = SetupNeed.say(e, dev.tvremote.core.Words.lang) ?: return false
    if (isFinishing) return true
    val old = generateSequence(e) { it.cause }.any { "setup:old:" in (it.message ?: "") }
    val h = host
    Popup.Builder(this).setMessage(say)
        .setPositiveButton(if (old) T("setup.update_ok") else T("setup_banner.install_tv_setup")) { _, _ ->
            if (h != null) { setupNews.remove(h); setupChecked.remove(h) }      // asked again after: done, or put back
            managing.open(h, then = if (old) "updateSetup()" else "installSetupApk()")
        }
        .setNegativeButton(T("common.later"), null).show()
    return true
}

/** Runs [then] once TV Setup on the TV has [feature] (SetupNeed.MIN); otherwise offerSetup asks for it instead. */
internal fun MainActivity.withSetup(feature: String, then: () -> Unit) {
    val tv = tv ?: return
    Thread({
        val e = try { SetupNeed.check(tv.link() ?: throw java.io.IOException("no TV"), feature); null } catch (e: Exception) { e }
        ui.post { if (isFinishing) return@post; if (e == null) then() else if (!offerSetup(e)) android.widget.Toast.makeText(this, Errors.say(e, dev.tvremote.core.Words.lang), android.widget.Toast.LENGTH_LONG).show() }
    }, "setup-need").start()
}
