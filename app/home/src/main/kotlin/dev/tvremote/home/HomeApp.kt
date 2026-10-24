package dev.tvremote.home

import android.app.Application
import android.content.Context
import dev.tvremote.guard.Guard
import dev.tvremote.guard.GuardWords

/** Before any screen or receiver: the error guard (shared/src/guard), its words from strings.xml (no :shared here, so no i18n files). */
class HomeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Guard.install(this, { c ->        // the front Activity's context: it has the language chosen in the app, not only the TV's
            GuardWords(c.getString(R.string.err_title), c.getString(R.string.err_last_time), c.getString(R.string.err_copy), c.getString(R.string.err_copied),
                c.getString(R.string.err_share), c.getString(R.string.err_close)) { n -> c.getString(R.string.err_repeat, n) }
        })
        CrashLoop.watch(this)
    }
}

/**
 * Android relaunches a crashed home app by itself, so a crash on start is a loop the person cannot leave: the one app that
 * could fix it (pick another home in Settings) is the one that never stays up. Crashes are counted on disk (the process dies
 * each time); at [LIMIT] within [WINDOW_MS] HomeActivity opens the TV's Settings instead of building its screen again.
 */
object CrashLoop {
    const val LIMIT = 3
    const val WINDOW_MS = 60_000L
    private const val KEY = "crashes"
    // device-protected: HomeActivity is direct-boot aware and may crash before the first unlock
    private fun prefs(c: Context) = c.createDeviceProtectedStorageContext().getSharedPreferences("crashloop", Context.MODE_PRIVATE)

    fun watch(app: Application) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()   // after Guard's: ours runs first, then its screen and the kill
        Thread.setDefaultUncaughtExceptionHandler { th, t ->
            try { record(app, System.currentTimeMillis()) } catch (ignored: Throwable) { }
            prev?.uncaughtException(th, t)
        }
    }

    fun record(c: Context, now: Long) {
        val p = prefs(c)
        p.edit().putString(KEY, recent(p.getString(KEY, "") ?: "", now).plus(now).joinToString(",")).commit()   // commit: the process is about to die
    }

    /** True once, when the loop is there: the count restarts so Settings is not forced on every later launch. */
    fun tripped(c: Context, now: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(c)
        val n = recent(p.getString(KEY, "") ?: "", now).size
        if (n >= LIMIT) p.edit().remove(KEY).commit()
        return n >= LIMIT
    }

    private fun recent(s: String, now: Long) = s.split(",").mapNotNull { it.toLongOrNull() }.filter { now - it in 0..WINDOW_MS }
}
