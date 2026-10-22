package dev.tvremote.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Opening an input is an implicit VIEW of the input's channel URI, and any app that claims the type could take it (a player, a
 * file manager). Ask who answers and pin the intent to the TV's own app; nobody fitting: the plain intent, as before.
 */
object InputOpen {
    const val INPUTS = "com.android.tv.action.VIEW_INPUTS"
    class Cand(val pkg: String, val system: Boolean)

    /** The package to pin: a system app, the TV app (its name says tv / tuner / live) first; null when no system app answers. Pure. */
    fun pick(c: List<Cand>): String? {
        val sys = c.filter { it.system }
        return sys.firstOrNull { Regex("tv|tuner|live", RegexOption.IGNORE_CASE).containsMatchIn(it.pkg) }?.pkg ?: sys.firstOrNull()?.pkg
    }

    /** Whether TV Home must answer VIEW_INPUTS: only when no other app does (see the manifest's InputsEntry). Pure. */
    fun answer(others: List<String>, self: String): Boolean = others.none { it != self }

    /** Turns the VIEW_INPUTS entry on or off to match [answer]; the system keeps the state, so this runs on each start of the home. */
    fun sync(c: Context) {
        try {
            val others = c.packageManager.queryIntentActivities(Intent(INPUTS), 0).map { it.activityInfo.packageName }
            val on = answer(others, c.packageName)
            c.packageManager.setComponentEnabledSetting(ComponentName(c, "dev.tvremote.home.InputsEntry"),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        } catch (e: Exception) { }
    }

    fun intent(c: Context, uri: Uri): Intent {
        val i = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            val cands = c.packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, uri), 0).map {
                Cand(it.activityInfo.packageName, it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0)
            }
            pick(cands)?.let { i.setPackage(it) }
        } catch (e: Exception) { }
        return i
    }
}
