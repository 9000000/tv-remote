package dev.tvremote.core

/** Reading /proc/meminfo and /proc/stat text from the TV: one parser for the Free RAM button, Health and History. */
object Meminfo {
    /** The kB of [key] ("MemAvailable") in /proc/meminfo text, or null. */
    fun kb(text: String, key: String): Long? = Regex("^$key:\\s+(\\d+)", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.toLongOrNull()

    /** CPU load in percent between two "cpu ..." lines of /proc/stat (idle and iowait count as idle), or null when unreadable or unmoved. */
    fun cpu(first: String, second: String): Int? = try {
        fun f(l: String) = l.trim().split(Regex("\\s+")).drop(1).map { it.toLong() }
        val a = f(first); val b = f(second)
        val total = b.sum() - a.sum()
        val idle = b[3] + b[4] - a[3] - a[4]
        if (total > 0) Math.round(100.0 * (total - idle) / total).toInt() else null
    } catch (e: Exception) { null }
}
