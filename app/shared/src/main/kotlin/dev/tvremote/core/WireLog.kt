package dev.tvremote.core
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import dev.tvremote.core.remote.Remote

/**
 * What happened between the app and the TVs, for the person to send when something does not work: every message to and from
 * an LG or Samsung TV (written against libraries and fakes, not real sets), and for Android TV the ADB and remote-protocol
 * connections, the picture's session, the keys, and every error a page or screen was given. What a real TV did is what
 * tells a bug apart.
 * Nothing that lets someone else in is kept: client keys, tokens and MAC addresses are blanked, typed text is only its length,
 * long answers are cut. One log for every TV ([all]), each line saying which.
 */
class WireLog(private val max: Int = 500) {
    private val lines = ArrayDeque<String>()
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    /** [dir]: "→" sent, "←" received, "·" what happened (connected, closed, an error). */
    fun add(dir: String, text: String) {
        val what = dir + " " + clean(text)
        val now = clock.format(Date())
        synchronized(lines) {
            if (what == last) {                                       // a TV that is off, asked every 10 s: one line, counted
                repeats++
                lines.removeLast()
                lines.addLast("$now $what (×${repeats + 1})")
                return
            }
            last = what; repeats = 0
            lines.addLast("$now $what")
            while (lines.size > max) lines.removeFirst()
        }
    }
    private var last = ""
    private var repeats = 0

    fun text(): String = synchronized(lines) { lines.joinToString("\n") }

    /** The log with what it came from on top: which app ([app]), which TV ([r], an LG or Samsung one in use). */
    fun report(r: Remote? = null): String {
        val tv = r?.let { listOf("TV " + Remote.Kind.name(it.kind), it.model, it.version).filter { s -> s.isNotEmpty() }.joinToString(" · ") } ?: ""
        val os = if ("Android" in app) "" else System.getProperty("os.name") + " " + System.getProperty("os.version")   // Android's says Linux
        return listOf(app, os, tv).filter { it.isNotEmpty() }.joinToString("\n") +
            "\n\n" + text().ifEmpty { "(empty)" }
    }

    companion object {
        private val SECRET = Regex("(\"(?:client-key|token)\"\\s*:\\s*\")[^\"]*(\")|([?&]token=)[^&\\s\"]*|(?<=/bot)\\d+:[A-Za-z0-9_-]+")
        /** What every app sends behind "Gửi nhật ký". */
        val all = WireLog()
        /** Which app and version, set by each app when it starts. */
        @Volatile var app = "TV Remote"

        private val TYPED = Regex("(\"(?:text|Cmd)\"\\s*:\\s*\")([^\"]*)(\")")
        private val KEEP = setOf("Click", "Press", "Release")
        private val MAC = Regex("\\b[0-9A-Fa-f]{2}(?:[:-][0-9A-Fa-f]{2}){5}\\b")
        // A Wi-Fi code's text and the card that makes it carry the network's password: never into a log
        private val WIFI = Regex("WIFI:.*?(?<!\\\\);;|(\"pass\"\\s*:\\s*\")(?:[^\"\\\\]|\\\\.)*(\")")
        private const val LONG = 600

        fun clean(text: String): String {
            val t = SECRET.replace(text) { m -> if (m.groupValues[3].isNotEmpty()) m.groupValues[3] + "…" else m.groupValues[1] + "…" + m.groupValues[2] }
                .let { TYPED.replace(it) { m -> if (m.groupValues[2] in KEEP) m.value else m.groupValues[1] + "…(" + m.groupValues[2].length + ")" + m.groupValues[3] } }
                .let { MAC.replace(it, "xx:xx:xx:xx:xx:xx") }
                .let { WIFI.replace(it) { m -> if (m.groupValues[1].isNotEmpty()) m.groupValues[1] + "…" + m.groupValues[2] else "WIFI:…" } }
                .replace("\n", "\\n")
            return if (t.length > LONG) t.take(LONG) + " …(" + t.length + ")" else t
        }
    }
}
