package dev.tvremote.core.picture

/**
 * How many are watching or listening to the TV: every phone, computer or TV Setup that shows its picture runs its own
 * scrcpy-server there (Tv.kt), so the TV's process list counts them, ours and anyone else's (scrcpy itself too). Several
 * browsers of one server share one session ([Feeds]): the server adds those it serves beyond the first.
 */
object Watchers {
    const val CMD = "ps -A -o ARGS 2>/dev/null"

    class Count(val watch: Int, val listen: Int)

    /** null where the TV's ps cannot list the arguments (Android 7 and older): better no number than a wrong one. */
    fun parse(ps: String): Count? {
        val lines = ps.lines().map { it.trim() }
        if (lines.firstOrNull() != "ARGS") return null
        val servers = lines.filter { it.startsWith("app_process") && "com.genymobile.scrcpy.Server" in it }
        return Count(servers.count { " video=true" in it }, servers.count { " audio=true" in it })
    }
}
