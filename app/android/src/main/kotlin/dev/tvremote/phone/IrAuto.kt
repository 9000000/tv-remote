package dev.tvremote.phone

import dev.tvremote.core.adb.Adb
import java.io.Closeable

// Dò mã by itself: the phone sends each remote's code while the TV, over ADB, says whether its own IR eye got a key (getevent),
// so nobody has to watch the TV and tap "it reacted". Only for the device ADB reaches: a box's ADB cannot see the TV it is plugged
// into, so that one is still found by hand (IrRemote.kt irFind).

/** What `getevent -l` prints, read line by line: which device is which, and whether a line is a key from a real one. */
internal object IrEvents {
    private val ADD = Regex("""^add device \d+: (/dev/input/\S+)""")
    private val NAME = Regex("""^\s+name:\s+"(.*)"""")
    private val EVENT = Regex("""^(/dev/input/\S+): (EV_KEY\s+\S+\s+DOWN|EV_MSC\s+MSC_SCAN\s+\S+)""")

    /** Fed every line in order: true for a key pressed on a device of the TV's own. Not virtual-*: keys sent over the network
     *  (Android TV Remote service), never the IR eye. MSC_SCAN too: the IR decoder reports a code even for a key the TV maps to nothing. */
    class Reader {
        private val names = HashMap<String, String>()
        private var adding: String? = null

        fun key(line: String): Boolean {
            ADD.find(line)?.let { adding = it.groupValues[1]; return false }
            NAME.find(line)?.let { m -> adding?.let { names[it] = m.groupValues[1] }; adding = null; return false }
            val e = EVENT.find(line) ?: return false
            return !(names[e.groupValues[1]] ?: "").startsWith("virtual")
        }
    }
}

/** The TV's keys as they come, while open. [last]: when the latest arrived (irNow), 0 for none yet. */
internal class IrHear(adb: Adb) : Closeable {
    @Volatile var last = 0L
        private set
    @Volatile private var closed = false
    private val s = adb.open("shell:getevent -l")

    init {
        Thread({
            val r = IrEvents.Reader()
            val buf = ByteArray(4096)
            val line = StringBuilder()
            try {
                while (!closed) {
                    val n = s.read(buf, 0, buf.size, 500)
                    if (n < 0) break
                    for (i in 0 until n) {
                        val c = buf[i].toInt().toChar()
                        if (c != '\n') { line.append(c); continue }
                        if (r.key(line.toString())) last = irNow()
                        line.setLength(0)
                    }
                }
            } catch (ignored: Exception) { }
        }, "ir-hear").apply { isDaemon = true }.start()
    }

    override fun close() { closed = true; try { s.close() } catch (ignored: Exception) { } }
}

/**
 * Steps through [steps] (one code per distinct remote, IrCodes.distinct): sends one, waits for the TV to hear it, and on a key
 * sends it once more to be sure (a person pressing the real remote meanwhile is not a match). [send] is the IR LED, [heard] the
 * time of the TV's last key; [at] is told each step. The match, or null when none or [stop].
 */
internal fun <P> irScan(steps: List<P>, code: (P) -> String, send: (String) -> Unit, heard: () -> Long, stop: () -> Boolean,
                        at: (Int) -> Unit, waitMs: Long = 700): P? {
    fun answered(c: String): Boolean {
        val t = irNow()
        send(c)
        val end = t + waitMs
        while (irNow() < end) { if (heard() > t) return true; if (stop()) return false; Thread.sleep(40) }
        return heard() > t
    }
    for ((i, p) in steps.withIndex()) {
        if (stop()) return null
        at(i)
        if (answered(code(p))) { Thread.sleep(300); if (answered(code(p))) return p }
    }
    return null
}

internal fun irNow() = System.nanoTime() / 1_000_000                   // not SystemClock: the unit tests run without Android
