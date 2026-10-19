package dev.tvremote.core.ontv

/**
 * The app on the TV's screen read over ADB, for the remote screen's "now open" line when the TV's own remote link does not say
 * it (not paired, or only ADB). [get] answers at once from the last reading and reads again in the background at most every
 * few seconds, so the page's state poll never waits on the TV.
 */
class Foreground {
    @Volatile private var pkg = ""
    @Volatile private var at = 0L
    private val busy = java.util.concurrent.atomic.AtomicBoolean()

    /** The package on screen, "" unknown; [shell] null: no ADB now, so nothing is known. */
    fun get(shell: ((String) -> String)?): String {
        if (shell == null) { at = 0; return "" }                       // kept: back on ADB, the last reading shows until the new one
        val now = System.currentTimeMillis()
        if (now - at > EVERY_MS && busy.compareAndSet(false, true)) Thread({
            try { pkg = parse(shell(CMD)) } catch (e: Exception) { pkg = "" }
            at = System.currentTimeMillis()
            busy.set(false)
        }, "foreground").apply { isDaemon = true }.start()
        return pkg
    }

    companion object {
        const val CMD = "dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity'"
        private const val EVERY_MS = 3000L
        private val PKG = Regex("u\\d+ ([\\w.]+)/")

        fun parse(out: String): String = PKG.find(out)?.groupValues?.get(1) ?: ""
    }
}
