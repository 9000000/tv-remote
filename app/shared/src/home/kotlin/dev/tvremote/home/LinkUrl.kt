package dev.tvremote.home

/**
 * Which pasted text may become a link on TV Home: an http(s) address or an app's own deep link (scheme://...). One rule for every
 * side that touches a link (the page asks TV Home, TV Home pins and opens, the shell call): only a plain VIEW + BROWSABLE link,
 * never a scheme that reaches an app's hidden screens (intent:), the TV's files (file:, content:) or runs code (javascript:).
 * app/shared/web/js/home_pin.js `linkParse` is its twin (tests on both sides use the same cases).
 */
object LinkUrl {
    private val BANNED = setOf("intent", "file", "content", "javascript", "data", "about", "blob", "vbscript", "filesystem", "android-app", "view-source", "chrome")
    private val SCHEME = Regex("^([a-z][a-z0-9+.-]{1,30})://(\\S+)$", RegexOption.IGNORE_CASE)
    private val BARE = Regex("^(?:[a-z0-9-]+\\.)+[a-z]{2,}(?:[/?#]\\S*)?$", RegexOption.IGNORE_CASE)

    /** [raw] as the link to use (a bare "example.com/x" gets https://), or null: no scheme://, a banned scheme, spaces, no host for http(s), too long. */
    fun clean(raw: String?): String? {
        val s = (raw ?: "").trim()
        if (s.isEmpty() || s.length > 500 || s.any { it.isWhitespace() || it.isISOControl() }) return null
        val u = if (SCHEME.matches(s)) s else if (BARE.matches(s)) "https://$s" else return null
        val m = SCHEME.find(u)!!
        val scheme = m.groupValues[1].lowercase()
        if (scheme in BANNED) return null
        if (scheme == "http" || scheme == "https") {
            val host = m.groupValues[2].substringBefore('/').substringBefore('?').substringBefore('#')
            if (host.isEmpty() || '@' in host) return null                    // https://user@evil.example/: a link that hides where it goes
        }
        return scheme + u.substring(scheme.length)
    }

    /** "scheme" of a cleaned link, lower case. */
    fun scheme(url: String) = url.substringBefore("://").lowercase()
}
