package dev.tvremote.channels

/**
 * One channel of a playlist, as iptv-org writes them:
 * `#EXTINF:-1 tvg-id="HTV7.vn@SD" tvg-logo="…" group-title="General",HTV7 (720p) [Geo-blocked]` then the URL, with optional
 * `#EXTVLCOPT:http-referrer=…` / `http-user-agent=…` lines before it (some servers answer only with them).
 * [id] is the tvg-id ("HTV7.vn@SD"), or "n:<name>" when the list gives none: the key of favourites and deeplinks.
 */
data class Channel(
    val id: String, val name: String, val logo: String, val group: String, val url: String,
    val quality: String = "", val geo: Boolean = false, val notAlways: Boolean = false,
    val referrer: String = "", val agent: String = "",
) {
    /** The country an iptv-org id belongs to: "HTV7.vn@SD" -> "vn" (its playlist is countries/vn.m3u). */
    val country: String get() = Ids.country(id)
}

object Ids {
    fun country(id: String): String = id.substringBefore('@').substringAfterLast('.', "").lowercase().takeIf { it.length == 2 } ?: ""
}

object M3u {
    private val ATTR = Regex("""([\w-]+)="([^"]*)"""")
    private val QUALITY = Regex("""\s*\((\d{3,4}[pi])\)""")
    private val LABEL = Regex("""\s*\[([^\]]+)]""")

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        val seen = HashSet<String>()
        var info: String? = null
        var ref = ""; var agent = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> {}
                line.startsWith("#EXTINF") -> { info = line; ref = ""; agent = "" }
                line.startsWith("#EXTVLCOPT:http-referrer=") -> ref = line.substringAfter('=')
                line.startsWith("#EXTVLCOPT:http-user-agent=") -> agent = line.substringAfter('=')
                line.startsWith("#") -> {}
                else -> {
                    val i = info ?: continue
                    info = null
                    val c = entry(i, line, ref, agent) ?: continue
                    if (c.group.equals("XXX", true)) continue                  // never shown, not even behind a switch
                    if (seen.add(c.id + "\n" + c.url)) out.add(c)
                }
            }
        }
        return out
    }

    private fun entry(info: String, url: String, ref0: String, agent0: String): Channel? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val comma = commaAfterAttrs(info)
        val attrs = ATTR.findAll(info.substring(0, comma)).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        var title = info.substring(comma + 1).trim()
        val quality = QUALITY.find(title)?.groupValues?.get(1) ?: ""
        val labels = LABEL.findAll(title).map { it.groupValues[1].lowercase() }.toList()
        title = LABEL.replace(QUALITY.replace(title, ""), "").trim()
        if (title.isEmpty()) return null
        val id = attrs["tvg-id"].orEmpty().ifEmpty { "n:$title" }
        return Channel(
            id = id, name = title, logo = attrs["tvg-logo"].orEmpty(),
            group = attrs["group-title"].orEmpty().substringBefore(';').ifEmpty { "Undefined" }, url = url,
            quality = quality, geo = labels.any { it.startsWith("geo") }, notAlways = labels.any { it.startsWith("not 24") },
            referrer = ref0.ifEmpty { attrs["http-referrer"].orEmpty() }, agent = agent0.ifEmpty { attrs["http-user-agent"].orEmpty() },
        )
    }

    /** The comma that ends the attributes: a title may hold commas, an attribute value may too (inside quotes). */
    private fun commaAfterAttrs(s: String): Int {
        var quoted = false
        for (i in s.indices) {
            when (s[i]) {
                '"' -> quoted = !quoted
                ',' -> if (!quoted) return i
            }
        }
        return s.length - 1
    }
}
