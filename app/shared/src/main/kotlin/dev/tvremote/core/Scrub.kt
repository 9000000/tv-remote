package dev.tvremote.core

/**
 * What an error report must not carry, taken out BEFORE it leaves the device (Census.crash). The page has the same function, in the
 * same order, as js/scrub.js; tests/scrub_cases.json is the table both are held to (ScrubTest.kt, tests/scrub.test.js).
 * Out: keys, tokens, passwords and MACs (WireLog.blank), `?k=` / `pass=` in a link, emails, the person's folder name in a path
 * (/Users/<name>, /home/<name>, C:\Users\<name> -> ~), IPv4 and IPv6 addresses. Not done: the names a person gave their TVs (not
 * known here; they are not in a stack, only possibly in a message). Running it twice changes nothing more.
 */
object Scrub {
    private const val H = "[0-9a-fA-F]{1,4}"
    private const val OCT = "(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)"
    private val KEYQ = Regex("([?&](?:k|key|pass|password|pwd|token)=)[^&\\s\"')]*")
    private val BEARER = Regex("(Bearer\\s+)[A-Za-z0-9._~+/=-]+")
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}")
    private val WINHOME = Regex("\\b[A-Za-z]:[\\\\/]+Users[\\\\/]+[^\\\\/\\s:*?\"<>|)']+")
    private val UNIXHOME = Regex("(^|[^\\w.-])/(?:Users|home)/[^/\\s:)'\"]+")
    private val IPV4 = Regex("\\b(?:$OCT\\.){3}$OCT\\b")
    private val IPV6 = Regex("(^|[^\\w:])((?:$H:){7}$H|(?:$H:){1,6}:(?:$H(?::$H){0,5})?|::(?:$H(?::$H){0,6})?)(?![\\w:])")

    fun text(s: String): String = WireLog.blank(s)
        .let { KEYQ.replace(it) { m -> m.groupValues[1] + "…" } }
        .let { BEARER.replace(it) { m -> m.groupValues[1] + "…" } }
        .let { EMAIL.replace(it, "email@x") }
        .let { WINHOME.replace(it, "~") }
        .let { UNIXHOME.replace(it) { m -> m.groupValues[1] + "~" } }
        .let { IPV4.replace(it, "x.x.x.x") }
        .let { IPV6.replace(it) { m -> m.groupValues[1] + "x.x.x.x" } }
}
