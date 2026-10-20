package dev.tvremote.home

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * TV Home as the system's screensaver: `screensaver_components` is the one setting every Android TV's screensaver reads. The
 * previous value is kept to put back when this is switched off. Writing it needs WRITE_SECURE_SETTINGS, which only ADB can
 * grant (`pm grant`): the phone or computer does that before it asks, and the TV's own switch falls back to the system's screen.
 */
object Dream {
    private const val KEY = "screensaver_components"

    private fun mine(c: Context) = ComponentName(c, HomeDream::class.java)

    fun isOn(c: Context): Boolean = try {
        Settings.Secure.getString(c.contentResolver, KEY).orEmpty().split(',').any { ComponentName.unflattenFromString(it.trim()) == mine(c) }
    } catch (e: Exception) { false }

    /** @throws SecurityException without the permission */
    fun set(c: Context, on: Boolean) {
        val cr = c.contentResolver
        val prefs = c.getSharedPreferences("home", Context.MODE_PRIVATE)
        if (on) {
            if (!isOn(c)) prefs.edit().putString("dream_prev", Settings.Secure.getString(cr, KEY).orEmpty()).apply()
            Settings.Secure.putString(cr, KEY, mine(c).flattenToShortString())
            Settings.Secure.putInt(cr, "screensaver_enabled", 1)
        } else if (isOn(c)) {
            Settings.Secure.putString(cr, KEY, prefs.getString("dream_prev", "").orEmpty().ifEmpty { null })
            prefs.edit().remove("dream_prev").apply()
        }
    }

    fun canWrite(c: Context) = c.checkSelfPermission(HomeConfig.WRITE_SECURE) == PackageManager.PERMISSION_GRANTED
}
