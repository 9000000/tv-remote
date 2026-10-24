package dev.tvremote.karaoke

import android.app.Application
import dev.tvremote.guard.Guard
import dev.tvremote.guard.GuardWords

/** Before any screen: the error guard (shared/src/guard), its words from strings.xml. */
class KaraokeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Guard.install(this, { c ->
            GuardWords(c.getString(R.string.err_title), c.getString(R.string.err_last_time), c.getString(R.string.err_copy), c.getString(R.string.err_copied),
                c.getString(R.string.err_share), c.getString(R.string.err_close)) { n -> c.getString(R.string.err_repeat, n) }
        })
    }
}
