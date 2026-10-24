package dev.tvremote.core.scenes
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
        data class Channel(val name: String) : Command()                  // a TV channel by name: TV Channels finds it itself
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
        Regex("^(kenh tiep( theo)?|kenh sau|next channel|channel up)$") to "CH_UP",
        Regex("^(kenh truoc|kenh truoc do|previous channel|channel down)$") to "CH_DOWN",
        Regex("^(tat tv|tat ti ?vi|tat may|turn off( the)? tv|tv off)$") to "SLEEP",
        Regex("^(bat tv|bat ti ?vi|mo tv|mo ti ?vi|turn on( the)? tv|tv on)$") to "WAKE")
    private val UP = Regex("^(tang am luong|to len|to hon|volume up|louder|turn it up)( (\\d+))?$")
    private val DOWN = Regex("^(giam am luong|nho lai|nho hon|be lai|volume down|quieter|turn it down)( (\\d+))?$")
    private val CANCEL = Regex("^(huy hen gio|bo hen gio|cancel( the)?( sleep)? timer)$")
    private val TIMER = Regex("^(hen gio tat( tv)?|hen gio|tat tv sau|tat sau|sleep in|turn off( the tv)? in|sleep timer) (\\d+) ?(phut|p|minutes?|mins?|gio|tieng|hours?|h)?$")
    // before OPEN: "mở kênh vtv1" is a channel, not an app called "kenh vtv1"
    private val CHANNEL = Regex("^((mo|xem|chuyen|bat|chuyen sang|chuyen qua) )?kenh (.+)$|^(open |watch )?channel (.+)$|^tune to (.+)$")
    private val OPEN = Regex("^(mo|bat|vao|chay|xem|open|launch|start|play)( ung dung| app)? (.+)$")

    /** True when [sentence] asks to open something ("mo youtube"): the Phone app then asks the TV for its app names before giving up on it. */
    fun wantsApp(sentence: String): Boolean = OPEN.matches(fold(sentence))

    /** The link TV Channels (dev.tvremote.channels) takes; spaces as %20, not +. */
    fun channelLink(name: String): String = "tvchannels://find/" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")

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
        CHANNEL.matchEntire(s)?.let { m -> return Command.Channel((m.groupValues[3] + m.groupValues[5] + m.groupValues[6]).trim()) }
        OPEN.matchEntire(s)?.let { m ->
            val want = m.groupValues[3]
            val named = apps.entries.map { it to fold(it.value) }.filter { it.second.isNotEmpty() }
            val hit = named.firstOrNull { it.second == want } ?: named.firstOrNull { it.second.startsWith(want) || want.startsWith(it.second + " ") }
                ?: named.firstOrNull { want.length >= 3 && it.second.contains(want) } ?: named.firstOrNull { it.second.length >= 3 && want.contains(it.second) }
            if (hit != null) return Command.App(hit.first.key, hit.first.value)
        }
        return null
    }

    /** What a sentence would do, in words ([id] in i18n with [args]), without doing it: the Phone app's hold-to-talk preview. */
    data class Say(val id: String, val args: Map<String, String> = emptyMap())
    /** [command] null: the sentence goes to the TV's search / Assistant, as [parse] says. */
    data class Plan(val command: Command?, val say: Say)

    // whole ids, not built from the key name: tests/i18n.test.js finds the words in use by reading them
    private val KEY_SAY = mapOf("MUTE" to "talk.k_mute", "PLAY_PAUSE" to "talk.k_play_pause", "HOME" to "talk.k_home", "BACK" to "talk.k_back",
        "NEXT" to "talk.k_next", "PREV" to "talk.k_prev", "CH_UP" to "talk.k_ch_up", "CH_DOWN" to "talk.k_ch_down", "SLEEP" to "talk.k_sleep",
        "WAKE" to "talk.k_wake", "VOL_UP" to "talk.k_vol_up", "VOL_DOWN" to "talk.k_vol_down")
    private val KEY_N = mapOf("VOL_UP" to "talk.k_vol_up_n", "VOL_DOWN" to "talk.k_vol_down_n")

    fun say(c: Command): Say = when (c) {
        is Command.Key -> if (c.times > 1) Say(KEY_N[c.key] ?: "talk.k_vol_up_n", mapOf("n" to c.times.toString())) else Say(KEY_SAY[c.key] ?: "talk.k_home")
        is Command.App -> Say("talk.open_app", mapOf("name" to c.name))
        is Command.Channel -> Say("talk.channel", mapOf("name" to c.name))
        is Command.Timer -> if (c.minutes == 0) Say("talk.timer_off") else Say("talk.timer", mapOf("n" to c.minutes.toString()))
        is Command.Scene -> Say("talk.scene")
    }

    /** [parse] plus its words; null for nothing heard. A scene is named from [scenes]. Pure: the preview calls it on every partial result. */
    fun preview(text: String, apps: Map<String, String>, scenes: Map<String, String>): Plan? {
        if (fold(text).isEmpty()) return null
        val c = parse(text, apps, scenes) ?: return Plan(null, Say("talk.ask", mapOf("sentence" to text.trim())))
        val say = if (c is Command.Scene) Say("talk.scene", mapOf("name" to (scenes[c.id] ?: ""))) else say(c)
        return Plan(c, say)
    }
}
