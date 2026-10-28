package dev.tvremote.home.apps

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import dev.tvremote.home.R

/**
 * The TV's own Settings, the one way every part of TV Home opens it. Some TVs answer no ACTION_SETTINGS (a stripped system, a vendor
 * shell), so after it come the vendors' main screens, in [VENDOR] order, and the stock Android TV one; only a component that resolves is
 * started. The packages sit in the manifest's <queries> (Android 11+ hides what is not listed). Idea from cleanyourtv's launcher (MIT).
 */
object SystemSettings {
    /** package to main settings activity: Xiaomi, TCL, Amazon, then AOSP Android TV Settings. */
    val VENDOR = listOf(
        "com.xiaomi.mitv.settings" to "com.xiaomi.mitv.settings.entry.MainActivity",
        "com.tcl.settings" to "com.tcl.settings.ui.MainActivity",
        "com.amazon.tv.launcher" to "com.amazon.tv.launcher.ui.MainSettingsActivity",
        "com.android.tv.settings" to "com.android.tv.settings.MainSettings",
    )

    /** The first of [tries] that [resolves], else null. Pure: the order is the whole rule. */
    fun <T> firstThat(tries: List<T>, resolves: (T) -> Boolean): T? = tries.firstOrNull(resolves)

    /** The intent that opens Settings on this TV, or null when nothing does. */
    fun intent(c: Context): Intent? {
        val pm = c.packageManager
        val std = Intent(Settings.ACTION_SETTINGS)
        if (try { std.resolveActivity(pm) != null } catch (e: Exception) { false }) return std.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val v = firstThat(VENDOR) { (p, cls) -> try { pm.resolveActivity(Intent().setClassName(p, cls), 0) != null } catch (e: Exception) { false } } ?: return null
        return Intent().setClassName(v.first, v.second).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Opens Settings; false (after a toast saying what to do) when nothing opened. [say]: false for a caller with no screen to toast on. */
    fun open(c: Context, say: Boolean = true): Boolean {
        val i = intent(c)
        val ok = i != null && try { c.startActivity(i); true } catch (e: Exception) { false }
        if (!ok && say) Toast.makeText(c, R.string.settings_none, Toast.LENGTH_LONG).show()
        return ok
    }
}
