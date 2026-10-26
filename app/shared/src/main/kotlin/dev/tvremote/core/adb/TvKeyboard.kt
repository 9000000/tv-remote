package dev.tvremote.core.adb

/**
 * Is someone about to type on the TV? Its on-screen keyboard is up, or a text field has the focus with the keyboard still
 * down (TV Home's fields raise it only on OK, so the remote down the room can type at once). The remotes then open their own
 * typing box. Read over ADB from the input method service, measured on the FPT box (Android 14, Gboard):
 * - `dumpsys input_method --proto`, some 450 bytes in 0.1 s: the text dump is half a megabyte and took 2.4 s there (the
 *   keyboard app's own dump is in it), so the box followed the TV 3 to 4 s late. Sent as hex by `od`, the shell's output being
 *   text; the TV's service message is field 3, in it `input_shown` (12) and the focused field (7) with its `input_type` (1)
 * - the text dump where there is no proto (older Androids): `mInputShown=true`, and the last start of input
 *   (`inputType=0x11 ... fieldId=...`); not `mIsInputViewShown`, which stays true while the keyboard is hidden, nor the
 *   keyboard app's own `inputType=` lines, which have no `fieldId=`
 * An input type of 0 is no text field (a button, a list).
 */
object TvKeyboard {
    const val PROTO = "dumpsys input_method --proto 2>/dev/null | od -An -tx1 -v"
    const val CMD = "dumpsys input_method | grep -E 'mInputShown=|inputType=.*fieldId='"

    private val SHOWN = Regex("""\bmInputShown=true\b""")
    private val FIELD = Regex("""\binputType=0x([0-9a-fA-F]+)\b.*\bfieldId=""")

    /** From the text dump. */
    fun shown(dump: String): Boolean =
        SHOWN.containsMatchIn(dump) || FIELD.findAll(dump).lastOrNull()?.groupValues?.get(1)?.toLongOrNull(16)?.let { it != 0L } == true

    /** From the proto's hex; null when it is not one (no proto on this TV). */
    fun fromProto(hex: String): Boolean? {
        val b = hex.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toIntOrNull(16) ?: return null }
        val top = fields(b, 0, b.size) ?: return null
        val ims = top[3]?.let { it as? IntRange }?.let { fields(b, it.first, it.last + 1) } ?: top
        if (ims[1] !is IntRange) return null                           // cur_method_id: not the service's message
        val type = (ims[7] as? IntRange)?.let { fields(b, it.first, it.last + 1) }?.get(1) as? Long ?: 0L
        return ims[12] == 1L || type != 0L
    }

    // one message's fields: a number for a varint, the bytes' range for a length; null if it does not read as one
    private fun fields(b: List<Int>, from: Int, to: Int): Map<Int, Any>? {
        val out = HashMap<Int, Any>()
        var i = from
        fun varint(): Long? { var v = 0L; var sh = 0; while (i < to && sh < 64) { val x = b[i++]; v = v or ((x and 0x7f).toLong() shl sh); if (x < 0x80) return v; sh += 7 }; return null }
        while (i < to) {
            val key = varint() ?: return null
            val n = (key ushr 3).toInt()
            when ((key and 7).toInt()) {
                0 -> out[n] = varint() ?: return null
                1 -> i += 8
                2 -> { val len = varint() ?: return null; if (len < 0 || i + len > to) return null; out[n] = i until i + len.toInt(); i += len.toInt() }
                5 -> i += 4
                else -> return null
            }
        }
        return if (i == to) out else null
    }

    /**
     * Asks the TV, the proto first; a TV that answers it with something else (no --proto) is asked by the text dump, and the
     * proto again every [RETRY] asks (one [Reader] per TV link). The text dump runs the keyboard app's own dump: ~0.8 s of its
     * CPU a call on the FPT box (Gboard, Android 14, 490 KB), against 37 ms and none for the proto; asked once a second it kept
     * the box lagging. So an empty reply (a TV too busy to answer, a link dropping) is no answer at all: it throws, never switches.
     */
    class Reader {
        private var proto = true
        private var asks = 0
        fun ask(shell: (String) -> String): Boolean {
            if (!proto && ++asks % RETRY == 0) proto = true
            if (proto) {
                val out = shell(PROTO)
                fromProto(out)?.let { return it }
                if (out.isBlank()) throw java.io.IOException("no answer")
                proto = false
            }
            return shown(shell(CMD))
        }
    }
    const val RETRY = 60

    /** What to do with the typing box when the TV keyboard's state is read; see [Follow]. */
    enum class Do { NONE, OPEN, CLOSE }

    /**
     * The rule for opening and closing the typing box, the same on the phone app and (in js/remote.js) the page:
     * open on the keyboard coming up, close on its going away if nothing was typed, and never open again within one
     * keyboard session after the person closed the box.
     */
    class Follow {
        private var was = false
        private var closed = false                                    // the person shut the box during this keyboard session
        private var opened = false                                    // we opened it, and it is still ours to shut
        private var typed = false

        fun read(shown: Boolean): Do {
            val edge = shown != was
            was = shown
            if (!edge) return Do.NONE
            if (shown) { closed = false; typed = false; opened = true; return Do.OPEN }
            val shut = opened && !typed && !closed
            opened = false
            return if (shut) Do.CLOSE else Do.NONE
        }

        fun closedByPerson() { closed = true; opened = false }
        fun typedByPerson() { typed = true }
    }
}
