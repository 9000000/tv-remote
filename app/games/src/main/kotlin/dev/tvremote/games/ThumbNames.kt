package dev.tvremote.games

import java.net.URLEncoder

/** The names of libretro-thumbnails (what RetroArch asks for), no Android here so a unit test can run it. */
object ThumbNames {
    const val HOST = "https://raw.githubusercontent.com/libretro-thumbnails/"

    /** RetroArch's own rule: & * / : ` < > ? \ | in a game's name become _ (that is how the files in the repos are named). */
    fun clean(name: String): String = name.replace(Regex("[&*/:`<>?\\\\|]"), "_")

    /** The file names to try, best first: the ROM's own name, then without [tags] and (Rev / Beta / v1.1 ...) marks; at most 2 (a 404 each). */
    fun candidates(fileName: String): List<String> {
        val base = fileName.substringBeforeLast('.').trim()
        if (base.isEmpty()) return emptyList()
        val loose = base.replace(Regex("\\s*\\[[^\\]]*\\]"), "")
            .replace(Regex("\\s*\\((?:Rev|Beta|Proto|Unl|Sample|Alt|Hack|v\\d)[^)]*\\)", RegexOption.IGNORE_CASE), "").trim()
        return listOf(base, loose).filter { it.isNotEmpty() }.map { clean(it) }.distinct()
    }

    /** One image's address under a base like HOST + "<repo>/master/Named_Boxarts/". */
    fun url(base: String, name: String): String = base.trimEnd('/') + "/" + URLEncoder.encode(name, "UTF-8").replace("+", "%20") + ".png"

    fun base(repo: String): String = "${HOST}$repo/master/Named_Boxarts/"

    /** Up to 3 letters for the designed tile: "Super Mario Bros." -> "SMB", "Robo Run" -> "RR", "Quiz" -> "QU". */
    fun initials(name: String): String {
        val words = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return "?"
        val s = if (words.size == 1) words[0].take(2) else words.take(3).joinToString("") { it.take(1) }
        return s.uppercase()
    }
}
