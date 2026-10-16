package dev.tvremote.core

import java.io.IOException

/**
 * The TV's screen off, the sound on (music, a podcast): tvr-helper's [Screen] turns the panel off and stays on the TV, apart
 * from the remote that asked, until any key is pressed (it turns the screen back on, so nobody takes a black TV for broken)
 * or the TV goes to standby. Every client starts it the same way, so the phone or the computer may go away meanwhile.
 */
object ScreenOff {
    /** Turns the screen off; IOException with the helper's words when this TV will not. Blocks a second or two. */
    fun off(a: Adb, helper: ByteArray) {
        val jar = RemoteLabels.install(a, helper)
        val log = "/data/local/tmp/tvr-screen.txt"
        val said = a.shell("rm -f $log; (CLASSPATH=$jar nohup setsid app_process / dev.tvremote.core.Screen off >$log 2>&1 </dev/null &); " +
            "for i in 1 2 3 4 5 6 7 8 9 10; do [ -s $log ] && break; sleep 0.3; done; head -1 $log").trim()
        if (said != "ok") throw IOException(if (said.isEmpty()) "the TV did not answer" else said.removePrefix("error "))
    }
}
