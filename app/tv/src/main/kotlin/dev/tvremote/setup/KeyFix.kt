package dev.tvremote.setup

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.content.res.Resources
import android.provider.Settings
import org.xmlpull.v1.XmlPullParser

/**
 * Remote buttons that only the stock launcher answered (Input, YouTube, Netflix...). Android sends some keys as a "global key" to
 * one component named in the framework's global_keys.xml, in every app, and eats them there: when that component is gone (Optimize
 * TV turned Google TV's launcher off, a home swap) the button is dead everywhere. [KeyFixService] brings them back.
 *
 * This file decides, with no Android type in [plan] so a test can run it: a key is taken over only when the system maps it to a
 * component that is not there (not installed, or turned off) AND we have something to do for it. A key that still works is
 * never touched, and every other key passes. [sync] keeps the service on in the secure settings only while there is such a key,
 * so a TV whose buttons all work pays nothing for it (every key of a filtering service waits for its answer).
 */
object KeyFix {
    /** What a key does when it is dead: [input] the TV's input picker, [app] the first of [pkgs] that is installed, [home], [settings]. */
    class Rule(val act: String, val pkgs: List<String> = emptyList())

    const val INPUT = "input"; const val APP = "app"; const val HOME = "home"; const val SETTINGS = "settings"

    /** By key code, as the stock launcher would answer them (cleanyourtv's list, checked on Google TV and Xiaomi remotes). */
    val RULES: Map<Int, Rule> = mapOf(
        170 to Rule(INPUT),                                           // KEYCODE_TV
        178 to Rule(INPUT),                                           // KEYCODE_TV_INPUT
        190 to Rule(APP, listOf("com.google.android.youtube.tv")),    // KEYCODE_BUTTON_3: the YouTube button of Google TV remotes
        191 to Rule(APP, listOf("com.netflix.ninja")),                // KEYCODE_BUTTON_4: Netflix
        195 to Rule(APP, listOf("com.amazon.amazonvideo.livingroom")),   // KEYCODE_BUTTON_8: Prime Video
        192 to Rule(HOME),                                            // KEYCODE_BUTTON_5: Xiaomi's PatchWall button
        284 to Rule(HOME),                                            // KEYCODE_ALL_APPS
        176 to Rule(SETTINGS))                                        // KEYCODE_SETTINGS

    /** The key names global_keys.xml uses, for the codes of [RULES] (the file is written with names, not numbers). */
    val NAMES: Map<String, Int> = mapOf(
        "KEYCODE_TV" to 170, "KEYCODE_TV_INPUT" to 178, "KEYCODE_BUTTON_3" to 190, "KEYCODE_BUTTON_4" to 191,
        "KEYCODE_BUTTON_8" to 195, "KEYCODE_BUTTON_5" to 192, "KEYCODE_ALL_APPS" to 284, "KEYCODE_SETTINGS" to 176)

    /**
     * The rules in force: the keys of [RULES] that [mapped] (code → "package/class" the system sends it to) sends to a component
     * [alive] says is gone, and, for an app key, with its app [launchable] (the key then does nothing, as before, when it is not).
     * Returns the rule with the package that will open.
     */
    fun plan(mapped: Map<Int, String>, alive: (String) -> Boolean, launchable: (String) -> Boolean): Map<Int, Rule> {
        val out = HashMap<Int, Rule>()
        for ((code, rule) in RULES) {
            val to = mapped[code] ?: continue                         // not a global key here: the focused app gets it, nothing to fix
            if (alive(to)) continue                                   // still has its handler: not ours to take
            if (rule.act == APP) {
                val pkg = rule.pkgs.firstOrNull(launchable) ?: continue
                out[code] = Rule(APP, listOf(pkg))
            } else out[code] = rule
        }
        return out
    }

    /** global_keys.xml's `<key keyCode="KEYCODE_X" component="pkg/cls" />` lines → code to component, for the names of [NAMES] (and plain numbers). */
    fun parse(p: XmlPullParser): Map<Int, String> {
        val keys = ArrayList<Pair<String?, String?>>()
        var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT) {
            if (e == XmlPullParser.START_TAG && p.name == "key") keys.add(p.getAttributeValue(null, "keyCode") to p.getAttributeValue(null, "component"))
            e = p.next()
        }
        return byCode(keys)
    }

    /** (name or number, component) pairs → code to component, for the codes of [RULES] only (a name we do not know is none of our business). */
    fun byCode(keys: List<Pair<String?, String?>>): Map<Int, String> {
        val out = HashMap<Int, String>()
        for ((name, comp) in keys) {
            val code = if (name == null) null else NAMES[name] ?: name.toIntOrNull()
            if (code != null && comp != null && code in RULES) out[code] = comp
        }
        return out
    }

    /** The system's own table (overlays of the maker included, as far as the framework shows them to an app). Empty when it cannot be read. */
    fun systemMap(): Map<Int, String> = try {
        val r = Resources.getSystem()
        val id = r.getIdentifier("global_keys", "xml", "android")
        if (id == 0) emptyMap() else r.getXml(id).use { parse(it) }
    } catch (e: Exception) { emptyMap() }

    /** A component that Android would still deliver the key's broadcast to: installed, its package and itself turned on. */
    fun alive(c: Context, comp: String): Boolean {
        val cn = ComponentName.unflattenFromString(comp) ?: return true   // not a name we understand: leave the key alone
        return try {
            val i = c.packageManager.getReceiverInfo(cn, 0)           // 0: a turned off one is "not found"
            i.enabled && i.applicationInfo.enabled
        } catch (e: PackageManager.NameNotFoundException) { false } catch (e: Exception) { true }
    }

    fun launchable(c: Context, pkg: String): Boolean = c.packageManager.getLeanbackLaunchIntentForPackage(pkg) != null
        || c.packageManager.getLaunchIntentForPackage(pkg) != null

    fun current(c: Context): Map<Int, Rule> = plan(systemMap(), { alive(c, it) }, { launchable(c, it) })

    // ---- on or off -------------------------------------------------------------------------------------------------

    private const val PREFS = "keyfix"
    private const val PERM = "android.permission.WRITE_SECURE_SETTINGS"
    val SERVICE = "dev.tvremote.setup/dev.tvremote.setup.KeyFixService"

    /** Wanted unless a person switched it off on TV Setup's screen. */
    fun wanted(c: Context): Boolean = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", true)
    fun want(c: Context, on: Boolean) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply(); sync(c) }
    fun can(c: Context): Boolean = c.packageManager.checkPermission(PERM, c.packageName) == PackageManager.PERMISSION_GRANTED

    /** The list with [SERVICE] put in or taken out, the others (KeyCatch while the screen is off, TalkBack) as they were. */
    fun list(was: String?, on: Boolean): String {
        val keep = (was ?: "").split(":").filter { it.isNotEmpty() && it != "null" && !isOurs(it) }
        return (if (on) keep + SERVICE else keep).joinToString(":")
    }

    /** Android rewrites the list in its short form ("dev.tvremote.setup/.KeyFixService") once the service is bound. */
    fun isOurs(entry: String): Boolean = entry == SERVICE || entry == "dev.tvremote.setup/.KeyFixService"

    fun has(was: String?): Boolean = was?.split(":")?.any { isOurs(it) } == true

    @Volatile var prepared = false

    /**
     * Android 13+ keeps an app that was not put there by a store from switching an accessibility service on (a firmware logs it as
     * "disallowed by device admin policy", as on the FPT box): the shell lifts that for this app, once, while there is ADB.
     */
    fun prepare(c: Context, adb: dev.tvremote.core.adb.Adb) {
        try { adb.shell("appops set ${c.packageName} ACCESS_RESTRICTED_SETTINGS allow 2>&1"); prepared = true } catch (e: Exception) { }
    }

    private var wrote = 0L

    /** Turn the service on while a dead key needs it and it is wanted, off when not; nothing when this app may not write the settings. */
    fun sync(c: Context) {
        if (!can(c)) return
        try {
            val r = c.contentResolver
            val was = Settings.Secure.getString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val has = has(was)
            val map = systemMap()
            val now0 = plan(map, { alive(c, it) }, { launchable(c, it) })
            android.util.Log.i("KeyFix", "global keys ${map.size}, to bring back ${now0.keys}, service on=$has")   // adb logcat -s KeyFix
            val need = wanted(c) && now0.isNotEmpty()
            if (need == has) return
            val t = System.currentTimeMillis()
            if (need && t - wrote < 60000) return                     // the system took it out again just now (a policy): not every few seconds
            if (need) wrote = t
            val now = list(was, need)
            Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, now.ifEmpty { null })
            Settings.Secure.putInt(r, Settings.Secure.ACCESSIBILITY_ENABLED, if (now.isEmpty()) 0 else 1)
        } catch (e: Exception) { android.util.Log.w("KeyFix", "no change: $e") }   // the grant was taken back: the keys are as they were before
    }
}

/** Looks again (a few seconds after the last change) when a package comes, goes or is turned off, or the accessibility list changes under us. */
internal class KeyFixWatch(private val c: Context) {
    private val ui = Handler(Looper.getMainLooper())
    private val again = Runnable { Thread({ KeyFix.sync(c) }, "keyfix-sync").apply { isDaemon = true }.start() }
    private fun soon() { ui.removeCallbacks(again); ui.postDelayed(again, 3000) }
    private val packages = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { soon() }
    }
    private val list = object : ContentObserver(ui) {
        override fun onChange(self: Boolean) { soon() }               // the system took us out, or the list was rewritten: put it right
    }

    fun start() {
        try {
            c.registerReceiver(packages, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            })
            c.contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, list)
        } catch (e: Exception) { }
        soon()
    }

    fun stop() {
        try { c.unregisterReceiver(packages) } catch (e: Exception) { }
        try { c.contentResolver.unregisterContentObserver(list) } catch (e: Exception) { }
        ui.removeCallbacks(again)
    }
}
