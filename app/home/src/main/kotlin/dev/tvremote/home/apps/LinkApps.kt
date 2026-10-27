package dev.tvremote.home.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dev.tvremote.home.LinkActivity

/**
 * Who opens an https link: whatever app on this TV handles it, found when the link is pressed, never a package written into the pin
 * (people run YouTube mods, SmartTube, ReVanced... often with the stock YouTube for TV turned off; uninstall one and install another
 * and every pin still works). The apps are those whose BROWSABLE VIEW filter takes the link, less browsers and stubs (an app that
 * takes a plain https://example.com/ link too: Downloader, a browser, the framework's BrowserStub). Order: the person's own choice
 * ([choose], one for all of a group such as "youtube") > the stock YouTube for TV > the system's own order. A pin is a shortcut into
 * [LinkActivity] holding the link; a links.json entry with pkg "*" ([Links]) uses the same, so any "whoever handles this link" app can be added without code.
 */
object LinkApps {
    class Handler(val pkg: String, val label: String)

    const val STOCK = "com.google.android.youtube.tv"
    private val STUBS = setOf("com.android.tv.frameworkpackagestubs", "com.android.documentsui", "com.android.vending")
    private const val PROBE = "https://example.com/"

    private fun view(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)

    // MATCH_ALL, not MATCH_DEFAULT_ONLY: from Android 12 that returns only apps with a verified domain for the link, never YouTube
    private fun taking(c: Context, url: String): List<String> = try {
        c.packageManager.queryIntentActivities(view(url), PackageManager.MATCH_ALL).mapNotNull { it.activityInfo?.packageName }.distinct()
    } catch (e: Exception) { emptyList() }

    /** The apps that can open [url], in the order they are tried: the choice first, the stock YouTube next. Empty when none is installed and on. */
    fun handlers(c: Context, url: String, group: String = "youtube"): List<Handler> {
        val generic = taking(c, PROBE).toSet()
        val pm = c.packageManager
        val got = taking(c, url).filter { it != c.packageName && it !in generic && it !in STUBS }
            .filter { try { pm.getApplicationInfo(it, 0).enabled } catch (e: Exception) { false } }
        val mine = choice(c, group)
        val ordered = got.sortedBy { if (it == mine) 0 else if (it == STOCK) 1 else 2 }   // stable: the rest keep the system's order
        return ordered.map { p -> Handler(p, try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() } catch (e: Exception) { p }) }
    }

    fun best(c: Context, url: String, group: String = "youtube"): Handler? = handlers(c, url, group).firstOrNull()

    private fun prefs(c: Context) = c.getSharedPreferences("linkapps", Context.MODE_PRIVATE)
    /** The app the person chose for [group]'s links ("" = automatic). It may be gone: [handlers] then ignores it. */
    fun choice(c: Context, group: String): String = prefs(c).getString("choice.$group", "") ?: ""
    fun choose(c: Context, group: String, pkg: String) { prefs(c).edit().putString("choice.$group", pkg).apply() }

    /** Which group a pin's link belongs to (remembered by the pin's id), for its menu's "Open with". */
    fun noteGroup(c: Context, id: String, group: String, url: String) { prefs(c).edit().putString("group.$id", group).putString("url.$id", url).apply() }
    fun groupOf(c: Context, id: String): String? = prefs(c).getString("group.$id", null)
    fun urlOf(c: Context, id: String): String? = prefs(c).getString("url.$id", null)

    /** The group a link's pins share one choice in: YouTube's hosts are "youtube", any other link its host. */
    fun groupFor(url: String): String { val h = Uri.parse(url).host?.lowercase().orEmpty(); return if (h.endsWith("youtube.com") || h == "youtu.be") "youtube" else h }

    /** The intent a pin of [url] opens: TV Home's own screen-less activity holding the link, so the app is chosen at press time. */
    fun intent(c: Context, url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setClass(c, LinkActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens [url] in the first handler that takes it; false when none does. */
    fun open(c: Context, url: String, group: String = "youtube"): Boolean {
        for (h in handlers(c, url, group)) {
            try { c.startActivity(view(url).setPackage(h.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true } catch (e: Exception) { }
        }
        return false
    }
}
