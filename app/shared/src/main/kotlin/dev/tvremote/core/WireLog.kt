package dev.tvremote.core

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * The last messages between the app and an LG or Samsung TV, for the person to send when something does not work: these
 * adapters were written against libraries and fakes, not real sets, so what a real TV answered is what tells a bug apart.
 * Nothing that lets someone else in is kept: client keys, tokens and MAC addresses are blanked, typed text is only its length,
 * long answers are cut. One log for every TV ([all]), each line saying which.
 */
class WireLog(private val max: Int = 300) {
    private val lines = ArrayDeque<String>()
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    /** [dir]: "→" sent, "←" received, "·" what happened (connected, closed, an error). */
    fun add(dir: String, text: String) {
        val line = clock.format(Date()) + " " + dir + " " + clean(text)
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > max) lines.removeFirst()
        }
    }

    fun text(): String = synchronized(lines) { lines.joinToString("\n") }

    /** The log with what it came from on top: which app, which TV ([r], the one in use, if any). */
    fun report(app: String, r: Remote?): String {
        val tv = r?.let { listOf("TV " + Remote.Kind.name(it.kind), it.model, it.version).filter { s -> s.isNotEmpty() }.joinToString(" · ") } ?: ""
        return listOf(app, System.getProperty("os.name") + " " + System.getProperty("os.version"), tv).filter { it.isNotEmpty() }.joinToString("\n") +
            "\n\n" + text().ifEmpty { "(empty)" }
    }

    companion object {
        private val SECRET = Regex("(\"(?:client-key|token)\"\\s*:\\s*\")[^\"]*(\")|([?&]token=)[^&\\s\"]*")
        /** What the Phone app and the Computer app show behind "Gửi nhật ký". */
        val all = WireLog()

        private val TYPED = Regex("(\"(?:text|Cmd)\"\\s*:\\s*\")([^\"]*)(\")")
        private val KEEP = setOf("Click", "Press", "Release")
        private val MAC = Regex("\\b[0-9A-Fa-f]{2}(?:[:-][0-9A-Fa-f]{2}){5}\\b")
        private const val LONG = 600

        fun clean(text: String): String {
            val t = SECRET.replace(text) { m -> if (m.groupValues[3].isNotEmpty()) m.groupValues[3] + "…" else m.groupValues[1] + "…" + m.groupValues[2] }
                .let { TYPED.replace(it) { m -> if (m.groupValues[2] in KEEP) m.value else m.groupValues[1] + "…(" + m.groupValues[2].length + ")" + m.groupValues[3] } }
                .let { MAC.replace(it, "xx:xx:xx:xx:xx:xx") }
                .replace("\n", "\\n")
            return if (t.length > LONG) t.take(LONG) + " …(" + t.length + ")" else t
        }
    }
}
