package dev.tvremote.core.ontv
import java.io.IOException
import dev.tvremote.core.adb.Adb

/**
 * The TV's screen off while everything else goes on: the sound plays, the remotes still see the picture and press keys.
 * tvr-helper's [Screen] turns the panel off and stays on the TV, apart from the remote that asked, until a key of the TV's own
 * remote (or any real input device) turns it back on, so nobody at the TV takes a black screen for broken, or the TV goes to
 * standby, or a remote turns it [on]. Every client starts it the same way, so the phone or the computer may go away meanwhile.
 */
object ScreenOff {
    // the one waiting for a key (it would turn the panel off again) and its getevent; [.]: not this shell's own line
    private const val WAITING = "pkill -f 'core[.]Screen off' && pkill -x getevent"

    /** Turns the screen off; IOException with the helper's words when this TV will not. Blocks a second or two. */
    fun off(a: Adb, helper: ByteArray) = run(a, helper, "off")

    /** Turns it on again from a remote. */
    fun on(a: Adb, helper: ByteArray) = run(a, helper, "on")

    /** Whether the screen is off by [off] now (its helper still waits). */
    fun isOff(sh: (String) -> String): Boolean = sh("pgrep -f 'core[.]Screen off' >/dev/null && echo off").trim() == "off"

    private fun run(a: Adb, helper: ByteArray, how: String) {
        val jar = RemoteLabels.install(a, helper)
        val log = "/data/local/tmp/tvr-screen.txt"
        a.shell(WAITING)                                              // a call of its own: the line below names it too, and would be killed
        val said = a.shell("rm -f $log; (CLASSPATH=$jar nohup setsid app_process / dev.tvremote.core.Screen $how >$log 2>&1 </dev/null &); " +
            "for i in 1 2 3 4 5 6 7 8 9 10; do [ -s $log ] && break; sleep 0.3; done; head -1 $log").trim()
        if (said != "ok") throw IOException(if (said.isEmpty()) "the TV did not answer" else said.removePrefix("error "))
    }
}
