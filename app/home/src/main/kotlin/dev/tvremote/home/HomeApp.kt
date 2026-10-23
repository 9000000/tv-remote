package dev.tvremote.home

import android.app.Application
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
    }
}
