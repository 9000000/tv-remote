package dev.tvremote.home.apps

import android.content.Context
import dev.tvremote.home.LinkUrl
import org.json.JSONArray
import org.json.JSONObject

/**
 * Any other link turned into a pin (YouTube has its own, [YouTube]): an https page or an app's deep link, pinned as TV Home's own
 * shortcut into [dev.tvremote.home.LinkActivity], which finds the app at press time ([LinkApps]). The preview the paste page shows
 * before pinning is [preview]; [pin] needs an app or a browser that takes the link, so no pin is made for a link nothing here opens.
 */
object WebLink {
    /** The answer to /h/link: {url, kind: app|browser|none, apps: [{pkg, label, logo}], choice, linkOk}; or {error: pin_bad_link}. */
    fun preview(c: Context, raw: String): JSONObject {
        val u = LinkUrl.clean(raw) ?: return JSONObject().put("error", YouTube.NO_LINK)
        val p = LinkApps.preview(c, u)
        val apps = JSONArray()
        for (h in p.apps) apps.put(JSONObject().put("pkg", h.pkg).put("label", h.label).put("logo", LinkApps.logo(c, h.pkg)))
        return JSONObject().put("url", u).put("kind", p.kind).put("apps", apps).put("choice", p.choice).put("linkOk", true)
    }

    private val TITLE = Regex("<title[^>]*>([^<]{1,200})</title>", RegexOption.IGNORE_CASE)

    /** The page's own title for an https link (its first 64 KB, bounded); null for a deep link or when it cannot be read. */
    private fun title(url: String): String? {
        if (!url.startsWith("https://")) return null
        val html = YouTube.getBytes(url, 64 * 1024, "</title>", 3000)?.let { String(it, Charsets.UTF_8) } ?: return null
        return TITLE.find(html)?.groupValues?.get(1)?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
    }

    /** Pins [raw]: "ok" (also when it was pinned already) or an error code. [pkg]: the app the person picked as the default for this kind of link. Blocking (network). */
    fun pin(c: Context, raw: String, name: String?, pkg: String?): String {
        val u = LinkUrl.clean(raw) ?: return YouTube.NO_LINK
        if (!Pins.can(c)) return YouTube.FAILED
        val g = LinkApps.groupFor(u)
        if (pkg != null && (LinkApps.handlers(c, u, g) + LinkApps.browsers(c, u)).any { it.pkg == pkg }) LinkApps.choose(c, g, pkg)
        val app = LinkApps.preview(c, u).apps.firstOrNull() ?: return YouTube.NO_APP
        val web = u.startsWith("http")                                    // a deep link's "host" is the app's own word ("x", "open"): the app's name says more
        val label = name?.trim()?.takeIf { it.isNotEmpty() } ?: (if (web) title(u) ?: android.net.Uri.parse(u).host?.removePrefix("www.")?.takeIf { it.isNotEmpty() } else app.label) ?: u.substringAfter("://")
        LinkApps.noteGroup(c, Pins.ownId(LinkApps.intent(c, u)), g, u)
        return when (Pins.pinIntent(c, app.pkg, label.take(80), LinkApps.intent(c, u), null)) {
            Pins.Made.FAIL -> YouTube.FAILED
            else -> "ok"
        }
    }
}
