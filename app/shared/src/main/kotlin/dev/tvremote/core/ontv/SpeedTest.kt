package dev.tvremote.core.ontv
import org.json.JSONObject
import dev.tvremote.core.adb.Adb

/**
 * The TV's internet speed, measured on the TV by tvr-helper's [Speed] (a phone's own test says nothing of the TV's Wi-Fi). It runs
 * in the background there and writes its lines to a file, so the page asks [read] every half second while it runs, and a remote
 * that goes away meanwhile leaves the result for the next one to read.
 */
object SpeedTest {
    private const val LOG = "/data/local/tmp/tvr-speed.txt"
    private const val STOP = "pkill -f 'core[.]Speed'"                 // [.]: not this shell's own line

    /** Starts a test (one at a time: a running one is stopped first). */
    fun start(a: Adb, helper: ByteArray) {
        val jar = RemoteLabels.install(a, helper)
        a.shell(STOP)
        // waits for its first line: a shell call that ends at once can take the process with it before setsid has moved it away
        a.shell("rm -f $LOG; (CLASSPATH=$jar nohup setsid app_process / dev.tvremote.core.Speed 8 >$LOG 2>&1 </dev/null &); " +
            "for i in 1 2 3 4 5 6 7 8 9 10; do [ -s $LOG ] && break; sleep 0.3; done")
    }

    fun stop(sh: (String) -> String) { sh("$STOP; rm -f $LOG") }

    /** What the test says so far, read in one call. */
    fun read(sh: (String) -> String): JSONObject {
        val out = sh("cat $LOG 2>/dev/null; pgrep -f 'core[.]Speed' >/dev/null && echo running")
        return parse(out)
    }

    /**
     * {running, phase ("ping" | "down" | "up" | "done" | "error" | "" for none yet), ping (ms), down, up (Mbit/s), error}. A phase's
     * speed counts from its first second on: the connections' first second is mostly opening them, not moving data.
     */
    fun parse(out: String): JSONObject {
        val o = JSONObject().put("phase", "")
        val first = HashMap<String, Pair<Long, Long>>()
        for (line in out.lines()) {
            val p = line.trim().split(" ")
            when (p[0]) {
                "ping" -> { o.put("ping", p.getOrNull(1)?.toLongOrNull() ?: continue); o.put("phase", "ping") }
                "down", "up" -> {
                    val bytes = p.getOrNull(1)?.toLongOrNull() ?: continue
                    val ms = p.getOrNull(2)?.toLongOrNull() ?: continue
                    if (ms >= 1000 && p[0] !in first) first[p[0]] = bytes to ms
                    val (b0, t0) = first[p[0]] ?: (0L to 0L)
                    if (ms > t0) o.put(p[0], Math.round((bytes - b0) * 8.0 / (ms - t0) / 100) / 10.0)   // Mbit/s, one decimal
                    o.put("phase", p[0])
                }
                "done" -> o.put("phase", "done")
                "error" -> o.put("phase", "error").put("error", line.trim().removePrefix("error ").substringAfter("Exception: "))
                "running" -> o.put("running", true)
            }
        }
        if (!o.has("running")) o.put("running", false)
        if (!o.optBoolean("running") && o.optString("phase") in setOf("ping", "down", "up")) o.put("phase", "error").put("error", "stopped")
        return o
    }
}
