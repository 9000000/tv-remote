package dev.tvremote.core.picture
import java.io.File

/**
 * Opt-in timing log for measuring picture / sound latency: set TVR_TRACE=/path/file and every stage writes one line
 * "wall-ms tag fields" (System.currentTimeMillis, the same clock the page's performance.timeOrigin gives on one computer).
 * Off by default: [on] is false and nothing is built or written.
 */
object Trace {
    private val out: java.io.PrintWriter? = System.getenv("TVR_TRACE")?.takeIf { it.isNotEmpty() }?.let {
        java.io.PrintWriter(java.io.BufferedWriter(java.io.FileWriter(File(it), true)), true)
    }
    val on get() = out != null
    @Synchronized fun log(tag: String, fields: String = "") { out?.println("${System.currentTimeMillis()} $tag $fields") }
}
