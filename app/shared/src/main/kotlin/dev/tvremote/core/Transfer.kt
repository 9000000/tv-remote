package dev.tvremote.core

import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/** Thrown where a transfer or install notices the person pressed Stop. Not an error: the page says "Đã dừng". */
class Cancelled : IOException("stopped")

/**
 * The Stop of one transfer or install. [stop] is called from another thread (the page's POST /api/cancel, a phone button);
 * the work calls [check] between chunks, and whatever it is blocked on (a download, pm install) registers an [onStop] to be
 * let go at once.
 */
class Cancel {
    @Volatile var stopped = false
        private set
    private val hooks = CopyOnWriteArrayList<() -> Unit>()

    fun stop() {
        stopped = true
        hooks.forEach { try { it() } catch (ignored: Exception) { } }
    }

    @Throws(Cancelled::class)
    fun check() { if (stopped) throw Cancelled() }

    /** Runs [f] when stopped (at once if it already is). */
    fun onStop(f: () -> Unit) {
        hooks.add(f)
        if (stopped) try { f() } catch (ignored: Exception) { }
    }
}

/**
 * The TV has no room for the file: refused before a byte is sent. The message is the code the pages and [Errors] turn into
 * words ("TV còn trống 1,2 GB, tệp cần 3,4 GB..."): sizes are in bytes, `need` includes the reserve and, for an APK, the copy
 * and the installed app.
 */
class NoRoom(val size: Long, val free: Long, val need: Long, val apk: Boolean) :
    IllegalArgumentException("storage full: file=$size free=$free need=$need apk=${if (apk) 1 else 0}") {
    companion object {
        private val CODE = Regex("storage full: file=(\\d+) free=(\\d+) need=(\\d+) apk=([01])")

        fun parse(s: String): NoRoom? = CODE.find(s)?.groupValues?.let { NoRoom(it[1].toLong(), it[2].toLong(), it[3].toLong(), it[4] == "1") }

        /** 1.2 GB, 340 MB: as the page's fmtSize writes them. */
        fun size(n: Long): String = if (n > 1_000_000_000L) "%.1f GB".format(java.util.Locale.ROOT, n / 1e9) else if (n > 1_000_000L) "%.1f MB".format(java.util.Locale.ROOT, n / 1e6)
            else if (n > 1000L) "${Math.round(n / 1e3)} KB" else "$n B"
    }

    fun say(vi: Boolean): String =
        if (apk) (if (vi) "TV chỉ còn trống ${size(free)}, không đủ cài APK ${size(size)} (cần trống ít nhất ${size(need)}: bản chép và app sau khi cài). Xóa bớt app hoặc tệp (tab Sức khỏe / Tệp) rồi thử lại."
                  else "The TV has only ${size(free)} free, not enough to install this ${size(size)} APK (it needs at least ${size(need)}: the copy and the installed app). Remove some apps or files (Health / Files tab), then try again.")
        else (if (vi) "TV chỉ còn trống ${size(free)}, không đủ chép tệp ${size(size)} (cần trống ít nhất ${size(need)}). Xóa bớt app hoặc tệp (tab Sức khỏe / Tệp) rồi thử lại."
              else "The TV has only ${size(free)} free, not enough for this ${size(size)} file (it needs at least ${size(need)}). Remove some apps or files (Health / Files tab), then try again.")
}
