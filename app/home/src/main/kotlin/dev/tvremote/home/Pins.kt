package dev.tvremote.home

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.widget.Toast

/**
 * Shortcuts other apps pin to the favourites, through Android's own request (ShortcutManager.requestPinShortcut, Android 8+):
 * a TV channel, a playlist, a page. This home knows none of those apps; the system keeps which shortcuts are pinned here
 * (asked again on every build); [order] is ours, outside HomeConfig: the phone and computer do not set it. They sit in the dock after its apps, under the same limit.
 */
object Pins {
    private fun la(c: Context) = c.getSystemService(LauncherApps::class.java)

    /** Only the default home may read or start pinned shortcuts (hasShortcutHostPermission); anything else sees none. */
    fun can(c: Context) = Build.VERSION.SDK_INT >= 26 && try { la(c)?.hasShortcutHostPermission() == true } catch (e: Exception) { false }

    fun key(s: ShortcutInfo) = "pin:${s.`package`}/${s.id}"

    private fun prefs(c: Context) = c.getSharedPreferences("pins", Context.MODE_PRIVATE)
    fun order(c: Context): List<String> = prefs(c).getString("order", "")!!.split('\n').filter { it.isNotEmpty() }
    fun order(c: Context, l: List<String>) { prefs(c).edit().putString("order", l.joinToString("\n")).apply() }

    /** The pinned shortcuts, as tiles' apps, in the order they were added; a disabled one (its app took it back) is left out. */
    fun list(c: Context): List<App> {
        if (!can(c)) return emptyList()
        val got = try {
            la(c)!!.getShortcuts(LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED), Process.myUserHandle())
        } catch (e: Exception) { null }.orEmpty().filter { it.isEnabled }
        val order = order(c)
        return got.sortedBy { order.indexOf(key(it)).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .map { App(key(it), (it.shortLabel ?: it.longLabel ?: it.`package`).toString(), null, it) }
    }

    @TargetApi(26)
    fun picture(c: Context, a: App) {
        val s = a.pin as ShortcutInfo
        val d = try { la(c)!!.getShortcutIconDrawable(s, c.resources.displayMetrics.densityDpi) } catch (e: Exception) { null }
            ?: try { c.packageManager.getApplicationIcon(s.`package`) } catch (e: Exception) { null }   // the system lost its bitmap ("Icon file not found"): the app's own icon, not the hidden apps' eye
        if (d != null && wide(d.intrinsicWidth, d.intrinsicHeight) && d.intrinsicWidth > 0) a.banner = d else a.icon = d
    }

    @TargetApi(26)
    fun open(c: Context, a: App, o: android.os.Bundle? = null): Boolean {
        val s = a.pin as ShortcutInfo
        return try { la(c)!!.startShortcut(s.`package`, s.id, null, o, Process.myUserHandle()); true } catch (e: Exception) { false }
    }

    /** Unpins [a]: the system keeps a list per app, so the app's other pins are named again. */
    @TargetApi(26)
    fun remove(c: Context, a: App) {
        val s = a.pin as ShortcutInfo
        val keep = list(c).mapNotNull { it.pin as? ShortcutInfo }.filter { it.`package` == s.`package` && it.id != s.id }.map { it.id }
        try { la(c)!!.pinShortcuts(s.`package`, keep, Process.myUserHandle()) } catch (e: Exception) {}
        order(c, order(c) - key(s))
    }

    /** Room for one more in the dock: its apps (still installed) and the pins share [HomeConfig.DOCK_MAX]. */
    fun room(c: Context): Boolean {
        val apps = Store.list(c, "dock").count { Apps.one(c, it) != null }
        return apps + list(c).size < HomeConfig.DOCK_MAX
    }
}

/**
 * Android asks the default home to confirm a pin (CONFIRM_PIN_SHORTCUT). The app that asked has already asked the person (its
 * own "Pin to Home" button), so it is taken at once, with a word on screen; when the dock is full it is refused, and said.
 * App widgets (CONFIRM_PIN_APPWIDGET) are not taken yet: this home has no widget host.
 */
@TargetApi(26)
class PinActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val req = try { getSystemService(LauncherApps::class.java)?.getPinItemRequest(intent) } catch (e: Exception) { null }
        val s = req?.takeIf { it.isValid && it.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT }?.shortcutInfo
        when {
            s == null -> {}
            !Pins.room(this) -> Toast.makeText(this, getString(R.string.dock_full, HomeConfig.DOCK_MAX), Toast.LENGTH_LONG).show()
            req.accept() -> {
                Pins.order(this, Pins.order(this).filter { it != Pins.key(s) } + Pins.key(s))
                Toast.makeText(this, getString(R.string.pinned_here, s.shortLabel ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
