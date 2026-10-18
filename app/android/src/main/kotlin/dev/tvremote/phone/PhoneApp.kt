package dev.tvremote.phone

import android.app.Application
import dev.tvremote.core.Words

/** Before any screen, widget or tile: the words come from i18n/<lang>.json in the assets (the language: loadLang). */
class PhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Words.open = { name -> try { assets.open(name) } catch (e: java.io.IOException) { null } }
        loadLang(this)
    }
}
