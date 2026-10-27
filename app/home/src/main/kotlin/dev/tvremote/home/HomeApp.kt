package dev.tvremote.home

import android.app.Application
import android.content.Context
import dev.tvremote.guard.Guard
import dev.tvremote.guard.GuardWords
import dev.tvremote.home.wall.Videos

/** Before any screen or receiver: the error guard (shared/src/guard), its words from strings.xml (no :shared here, so no i18n files). */
class HomeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Guard.install(this, { c ->        // the front Activity's context: it has the language chosen in the app, not only the TV's
            GuardWords(c.getString(R.string.err_title), c.getString(R.string.err_last_time), c.getString(R.string.err_copy), c.getString(R.string.err_copied),
                c.getString(R.string.err_share), c.getString(R.string.err_close)) { n -> c.getString(R.string.err_repeat, n) }
        })
        dev.tvremote.report.OwnReport.start(this, "home", ping = true)        // anonymous error reports + the daily census ping (TV Setup's switch, else Customize's)
        CrashLoop.watch(this)
        dev.tvremote.home.widgets.Sources.start(this)                         // the market sources and news feeds: kept or packed list now, the site's when newer
        dev.tvremote.home.apps.Links.start(this)                            // other apps' channels to pin (VTVgo, TV360): the list, then the logos of the installed ones
        Themes.start(this)                                                  // the ready-made themes: kept or packed list now, the site's when it is newer
        // a saved theme keeps its photo / video: the library and the photos' delete refuse to remove one a theme shows (409 "inuse")
        Videos.refs.add { wall -> HomeConfig.themeUses(Store.get(this), wall) }
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
