package dev.tvremote.core

import java.io.File
import java.io.IOException

/** An ADB connection to one TV that is opened when first needed and opened again after it drops. */
class AdbLink(private val keyDir: File, private val host: String) {
    private var adb: Adb? = null
    @Volatile private var waiting = false                             // the TV is showing "Allow USB debugging?"

    @Synchronized
    @Throws(IOException::class)
    fun get(): Adb {
        adb?.let { if (!it.isDead()) return it }
        try {
            val a = Adb(keyDir)
            a.connect(host, 5555) { waiting = true }
            waiting = false
            adb = a
            return a
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message ?: e.javaClass.simpleName)
        }
    }

    fun waitingForUser(): Boolean = waiting

    @Synchronized
    fun close() {
        adb?.close()
        adb = null
    }
}
