package dev.tvremote.core

import java.text.Normalizer

/**
 * Spoken or typed sentences the remote carries out itself, before handing anything to the TV's Assistant: "mở YouTube",
 * "tắt tiếng", "hẹn giờ tắt 30 phút", the name of a scene. Faster than the Assistant, and it works on TVs without one.
 * The same rules as the web page's js/voice.js (tests on both sides).
 */
object Voice {
    sealed class Command {
        data class Key(val key: String, val times: Int) : Command()
        data class App(val pkg: String, val name: String) : Command()
        data class Timer(val minutes: Int) : Command()
        data class Scene(val id: String) : Command()
    }

    /** Lower case, no accents, no punctuation: "Mở YouTube!" and "mo youtube" read the same. */
    fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('đ', 'd').replace('Đ', 'd')
        .lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    private val KEYS = listOf(
        Regex("^(tat tieng|bat tieng|im lang|mute|unmute)$") to "MUTE",
        Regex("^(tam dung|dung lai|dung|phat|phat tiep|tiep tuc|pause|play|resume|stop)$") to "PLAY_PAUSE",
        Regex("^(ve )?(man hinh chinh|trang chu|home|go home)$") to "HOME",
        Regex("^(quay lai|tro lai|back|go back)$") to "BACK",
        Regex("^(bai tiep|bai sau|tiep theo|next)$") to "NEXT",
        Regex("^(bai truoc|truoc do|previous)$") to "PREV",
        Regex("^(tat tv|tat ti ?vi|tat may|turn off( the)? tv|tv off)$") to "SLEEP",
        Regex("^(bat tv|bat ti ?vi|mo tv|mo ti ?vi|turn on( the)? tv|tv on)$") to "WAKE")
    private val UP = Regex("^(tang am luong|to len|to hon|volume up|louder|turn it up)( (\\d+))?$")
    private val DOWN = Regex("^(giam am luong|nho lai|nho hon|be lai|volume down|quieter|turn it down)( (\\d+))?$")
    private val CANCEL = Regex("^(huy hen gio|bo hen gio|cancel( the)?( sleep)? timer)$")
    private val TIMER = Regex("^(hen gio tat( tv)?|hen gio|tat tv sau|tat sau|sleep in|turn off( the tv)? in|sleep timer) (\\d+) ?(phut|p|minutes?|mins?|gio|tieng|hours?|h)?$")
    private val OPEN = Regex("^(mo|bat|vao|chay|xem|open|launch|start|play)( ung dung| app)? (.+)$")

    /** [apps]: package to name; [scenes]: id to name. Null means: ask the TV's Assistant. */
    fun parse(text: String, apps: Map<String, String>, scenes: Map<String, String>): Command? {
        val s = fold(text)
        if (s.isEmpty()) return null
        scenes.entries.firstOrNull { (_, n) -> fold(n) == s || fold("canh $n") == s || fold("scene $n") == s }?.let { return Command.Scene(it.key) }
        for ((re, key) in KEYS) if (re.matches(s)) return Command.Key(key, 1)
        UP.matchEntire(s)?.let { return Command.Key("VOL_UP", minOf(it.groupValues[3].toIntOrNull() ?: 3, 15)) }
        DOWN.matchEntire(s)?.let { return Command.Key("VOL_DOWN", minOf(it.groupValues[3].toIntOrNull() ?: 3, 15)) }
        if (CANCEL.matches(s)) return Command.Timer(0)
        TIMER.matchEntire(s)?.let { m ->
            val hours = m.groupValues[5].matches(Regex("gio|tieng|hours?|h"))
            return Command.Timer(minOf((m.groupValues[4].toIntOrNull() ?: 720) * (if (hours) 60 else 1), 720))   // "99999999999 phút": the most, not a crash
        }
        OPEN.matchEntire(s)?.let { m ->
            val want = m.groupValues[3]
            val named = apps.entries.map { it to fold(it.value) }.filter { it.second.isNotEmpty() }
            val hit = named.firstOrNull { it.second == want } ?: named.firstOrNull { it.second.startsWith(want) || want.startsWith(it.second + " ") }
                ?: named.firstOrNull { want.length >= 3 && it.second.contains(want) } ?: named.firstOrNull { it.second.length >= 3 && want.contains(it.second) }
            if (hit != null) return Command.App(hit.first.key, hit.first.value)
        }
        return null
    }
}
