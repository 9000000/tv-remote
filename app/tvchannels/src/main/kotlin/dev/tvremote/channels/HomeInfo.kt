package dev.tvremote.channels

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The live widget TV Home draws in its app grid ("Keep watching", [ChannelLiveWidget]), asked the way TV Setup's HomeInfo is asked:
 *   "tiles"    the list [{id, title, icon, kind, layout}]   "tile"  one live widget's state now (arg = id)   "tile_do"  its action, {started}
 *   (the wire names say "tile": TV Home and TV Setup already speak them)
 * Answered only to TV Home, by its signing certificate (or this app's own, one key for both: a debug build); TV Home's package name
 * signed by another key gets {result: untrusted}, anything else nothing. A binder call never waits: the guide is read on a thread
 * and shows in the next answer. The press itself is TV Home starting the link in "open" (it is the app in front, which Android 14
 * needs for an app to open an activity), so "tile_do" only says it knows the live widget.
 */
class HomeInfo : ContentProvider() {
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val c = context ?: return null
        if (method !in setOf("tiles", "tile", "tile_do")) return null
        val uid = Binder.getCallingUid()
        if (!fromHome(c, uid)) return null
        if (!trusted(c)) return Bundle().apply { putString("result", "untrusted") }
        val w = words(localized(c, extras?.getString("lang")))
        val json = when (method) {
            "tiles" -> ChannelLiveWidget.list(w).toString()
            "tile" -> if (arg == ChannelLiveWidget.NEXT) state(c, w).toString() else null
            else -> if (arg == ChannelLiveWidget.NEXT) JSONObject().put("started", true).toString() else null
        }
        return Bundle().apply { if (json != null) putString("json", json) }
    }

    /** The words in TV Home's language (it passes "lang"), not this app's own choice: the grid is one screen, in one language. */
    private fun localized(c: Context, lang: String?): Context =
        if (lang == null || lang !in dev.tvremote.i18n.Langs.CODES) c else c.createConfigurationContext(android.content.res.Configuration(c.resources.configuration).apply { setLocale(dev.tvremote.i18n.Langs.locale(lang)) })

    private fun words(c: Context) = ChannelLiveWidget.Words(c.getString(R.string.tile_next_title), c.getString(R.string.tile_next_empty), c.getString(R.string.tile_next_empty_line),
        c.getString(R.string.tile_next_play)) { c.getString(R.string.guide_next, it) }

    private fun state(c: Context, w: ChannelLiveWidget.Words): JSONObject {
        val ch = Store.lastChannel(c)
        var now: Show? = null; var then: Show? = null
        if (ch != null) {
            val shows = Guide.known(ch.id)
            if (shows != null) { now = Guide.now(shows); then = Guide.after(shows) } else ask(c, ch)
        }
        return ChannelLiveWidget.state(w, ch, now, then, Guide::hm)
    }

    /** The guide of [ch] read on a thread of its own (one at a time); [Guide.known] has it for the next answer. */
    private fun ask(c: Context, ch: Channel) {
        if (!asking.compareAndSet(false, true)) return
        val app = c.applicationContext
        Thread({ try { Guide.of(app, listOf(ch)) } catch (e: Exception) { } finally { asking.set(false) } }, "home-guide").apply { isDaemon = true }.start()
    }

    private fun fromHome(c: Context, uid: Int) = c.packageManager.getPackagesForUid(uid)?.contains(HOME) == true

    private fun trusted(c: Context): Boolean = certOk(certs(c.packageManager, HOME), certs(c.packageManager, c.packageName))

    @Suppress("DEPRECATION")
    private fun certs(pm: PackageManager, pkg: String): List<String> = try {
        val sigs = if (Build.VERSION.SDK_INT >= 28) pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            else pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        sigs.orEmpty().map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }
    } catch (e: Exception) { emptyList() }

    override fun onCreate() = true
    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    internal companion object {
        const val HOME = "dev.tvremote.home"
        const val HOME_CERT = "657aabacfdccbc22ff1f61138df7efd79fae765df742f7d15d80dac84c7d81b3"   // app/tvhome/signing.jks, alias tvhome (the same pin TV Setup holds)
        private val asking = AtomicBoolean()

        /** TV Home is trusted when one of its certificates is the pinned release one, or is this app's own (debug builds of all apps share a key). */
        fun certOk(home: List<String>, mine: List<String>): Boolean = home.any { it == HOME_CERT || it in mine }
    }
}
