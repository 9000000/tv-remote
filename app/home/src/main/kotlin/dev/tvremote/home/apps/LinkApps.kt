package dev.tvremote.home.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dev.tvremote.home.LinkUrl
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

    /**
     * The browsers that take [url] (an http(s) link no app of its own takes): what answers "opens in the browser". The stub the framework
     * ships (it only says "install a browser") and this home are never one. Empty for any other scheme: browsers do not take those.
     */
    fun browsers(c: Context, url: String): List<Handler> {
        val s = LinkUrl.scheme(url)
        if (s != "http" && s != "https") return emptyList()
        val pm = c.packageManager
        val generic = taking(c, PROBE).toSet()
        return taking(c, url).filter { it in generic && it != c.packageName && it !in STUBS }
            .filter { try { pm.getApplicationInfo(it, 0).enabled } catch (e: Exception) { false } }
            .map { p -> Handler(p, try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() } catch (e: Exception) { p }) }
    }

    /** What the person is told before pinning [url]: "app" (these apps open it), "browser" (only these browsers), or "none". */
    class Preview(val kind: String, val apps: List<Handler>, val choice: String)

    fun preview(c: Context, url: String): Preview {
        val g = groupFor(url)
        val apps = handlers(c, url, g)
        if (apps.isNotEmpty()) return Preview("app", apps, choice(c, g))
        val b = browsers(c, url)
        return if (b.isNotEmpty()) Preview("browser", b, choice(c, g)) else Preview("none", emptyList(), "")
    }

    /** [pkg]'s icon as a small PNG in base64, for the page's preview (the page cannot read the TV's icons otherwise). */
    fun logo(c: Context, pkg: String, px: Int = 64): String = try {
        val d = c.packageManager.getApplicationIcon(pkg)
        val b = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, px, px); d.draw(android.graphics.Canvas(b))
        val o = java.io.ByteArrayOutputStream(); b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o)
        android.util.Base64.encodeToString(o.toByteArray(), android.util.Base64.NO_WRAP)
    } catch (e: Exception) { "" }

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
    fun groupFor(url: String): String {
        val h = Uri.parse(url).host?.lowercase().orEmpty(); val s = LinkUrl.scheme(url)
        return if (h.endsWith("youtube.com") || h == "youtu.be") "youtube" else if (s == "http" || s == "https") h else "$s://$h"   // a deep link: its scheme and host
    }

    /** The intent a pin of [url] opens: TV Home's own screen-less activity holding the link, so the app is chosen at press time. */
    fun intent(c: Context, url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setClass(c, LinkActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens [url] in the first handler that takes it ([browser]: a browser too when no app does); false when none does. */
    fun open(c: Context, url: String, group: String = "youtube", browser: Boolean = false): Boolean {
        for (h in handlers(c, url, group) + (if (browser) browsers(c, url) else emptyList())) {
            try { c.startActivity(view(url).setPackage(h.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true } catch (e: Exception) { }
        }
        return false
    }
}
