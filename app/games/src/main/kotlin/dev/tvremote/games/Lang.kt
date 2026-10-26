package dev.tvremote.games

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * The app's language: "" follows the TV, or "vi" / "en" (the same choice as TV Channels and TV Home). Plain Activities (no AppCompat),
 * so each one wraps its base context in [wrap]; the chosen locale is also the JVM default. Every place that needs the language
 * (genre words, web game names, Guard's dialog, the notification) reads [code] and nothing else.
 */
object Lang {
    val CHOICES = listOf("") + dev.tvremote.i18n.Langs.CODES

    fun pref(c: Context): String = c.getSharedPreferences("games", Context.MODE_PRIVATE).getString("lang", "") ?: ""
    fun set(c: Context, l: String) = c.getSharedPreferences("games", Context.MODE_PRIVATE).edit().putString("lang", l).apply()

    /** The TV's own locale, whatever the app shows. */
    fun system(): Locale = Resources.getSystem().configuration.let { if (android.os.Build.VERSION.SDK_INT >= 24) it.locales[0] else @Suppress("DEPRECATION") it.locale }

    /** The words' language now: one of the list (English is every other language's fallback). [pref] "" = follow [system]. Pure, for the unit test. */
    fun decide(pref: String, system: String): String = dev.tvremote.i18n.Langs.of(pref.ifEmpty { system }) ?: "en"

    fun code(): String = decide("", locale().language)

    /** What [wrap] chose. The JVM default is not trusted: Android sets it back to the system's on a configuration change (the keyboard opening). */
    @Volatile private var chosen: Locale? = null
    fun locale(): Locale = chosen ?: Locale.getDefault()

    /** [base] with the chosen language, for an Activity's / Service's attachBaseContext. */
    fun wrap(base: Context): Context {
        val l = pref(base)
        val loc = if (l.isEmpty()) system() else dev.tvremote.i18n.Langs.locale(l)
        Locale.setDefault(loc); chosen = loc
        if (l.isEmpty()) return base
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(loc) })
    }
}
