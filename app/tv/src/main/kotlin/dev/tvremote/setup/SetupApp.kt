package dev.tvremote.setup

import android.app.Application
import android.content.res.Configuration
import dev.tvremote.core.Words
import java.util.Locale

/** Before any screen, service or receiver: the words (i18n/<lang>.json in the assets) in the TV's language, again when it changes. */
class SetupApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Words.open = { name -> try { assets.open(name) } catch (e: java.io.IOException) { null } }
        Words.lang = Words.pick(Locale.getDefault().language)
    }

    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        Words.lang = Words.pick(Locale.getDefault().language)
    }
}
