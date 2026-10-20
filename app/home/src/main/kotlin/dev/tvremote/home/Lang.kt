package dev.tvremote.home

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/** TV Home in the language chosen for it ("lang", [HomeConfig.LANGS]) rather than the TV's, for a TV set to a language TV Home lacks. */
object Lang {
    /** The language the screen was last built in, so a change of it builds the screen again. */
    var shown = ""
        private set

    fun wrap(base: Context): Context {
        shown = try { Store.get(base).optString("lang") } catch (e: Exception) { "" }
        // the default locale too: dates and numbers are formatted with it (DateFormat, String.format) all over the screen
        val loc = if (shown.isEmpty()) Resources.getSystem().configuration.locales[0] else Locale(shown)
        Locale.setDefault(loc)
        if (shown.isEmpty()) return base
        val cfg = Configuration(base.resources.configuration).apply { setLocale(loc) }
        return base.createConfigurationContext(cfg)
    }
}
