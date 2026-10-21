package dev.tvremote.channels

import java.text.Normalizer

/** Finds a channel by the name a person says ("vtv1", "an giang 1") among a country's channels, without knowing iptv-org ids. */
object Find {
    /** Lower case, no accents (đ -> d), no punctuation, single spaces. */
    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase().replace('đ', 'd'), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ").trim()

    /** 0 exact, 1 starts with the words ("vtv1" -> "VTV1 HD", not "VTV10"), 2 starts with, 3 every word present, 4 contained; null no match. Spaces are ignored too, so "vtv 1" == "vtv1". */
    fun tier(name: String, text: String): Int? {
        val n = fold(name); val t = fold(text)
        if (t.isEmpty()) return null
        val nc = n.replace(" ", ""); val tc = t.replace(" ", "")
        return when {
            n == t || nc == tc -> 0
            wordsStart(n, tc) -> 1
            n.startsWith(t) || nc.startsWith(tc) -> 2
            t.split(" ").all { n.contains(it) } -> 3
            nc.contains(tc) -> 4
            else -> null
        }
    }

    /** Whole leading words of [n] spell [tc] ("vtv 1 hd" for "vtv1"), so "vtv10" does not count. */
    private fun wordsStart(n: String, tc: String): Boolean {
        var a = ""
        return n.split(" ").any { a += it; a == tc }
    }

    /** The best channel of [list] for [text]; ties go to [mine] (favourites, groups), then to one not known dead, then list order. */
    fun best(list: List<List<Channel>>, text: String, mine: (String) -> Boolean, dead: (Channel) -> Boolean): List<Channel>? =
        list.mapIndexedNotNull { i, ch -> tier(ch.first().name, text)?.let { Triple(it, i, ch) } }
            .minWithOrNull(compareBy<Triple<Int, Int, List<Channel>>>({ it.first }, { if (mine(it.third.first().id)) 0 else 1 },
                { if (it.third.all(dead)) 1 else 0 }, { it.second }))?.third
}
