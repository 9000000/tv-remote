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
        Words.lang = Words.pick(speak(resources.configuration))
        dev.tvremote.guard.Guard.install(this, { dev.tvremote.core.guardWords() })
        if (main()) KeyList.start(this) { KeyFix.sync(this); KeysBridge.poke() }   // one TV's odd buttons, from the site's signed list; not again in ManageActivity's :web
    }

    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        Words.lang = Words.pick(speak(c))
    }

    private fun main(): Boolean = try { java.io.File("/proc/self/cmdline").readText().trimEnd('\u0000') == packageName } catch (e: Exception) { true }

    /** The language the system gives THIS app (a per-app choice included): Locale.getDefault() still said the TV's own while this ran, so the dialogs spoke English on a Vietnamese screen. */
    private fun speak(c: Configuration) = c.locales.get(0)?.language ?: Locale.getDefault().language
}
