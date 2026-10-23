package dev.tvremote.core.adb

/**
 * Is the TV's on-screen keyboard up? Someone focused a search box there, so the remotes open their own typing box. Read
 * over ADB from the input method service (`mInputShown`, true from Android 11 to 14 as measured on a Google TV with Gboard).
 * Not `mIsInputViewShown`: that line stays true while the keyboard is hidden.
 */
object TvKeyboard {
    /** One cheap line instead of the whole dump (it is some 3000 lines), asked about once a second. */
    const val CMD = "dumpsys input_method | grep -m1 mInputShown"

    private val SHOWN = Regex("""\bmInputShown=true\b""")

    fun shown(dump: String): Boolean = SHOWN.containsMatchIn(dump)

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
