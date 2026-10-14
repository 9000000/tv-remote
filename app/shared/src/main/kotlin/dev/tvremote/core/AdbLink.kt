package dev.tvremote.core

import java.io.File
import java.io.IOException

/** An ADB connection to one TV that is opened when first needed and opened again after it drops. */
class AdbLink(private val keyDir: File, private val host: String) {
    @Volatile private var adb: Adb? = null
    @Volatile private var dialing: Adb? = null                        // being connected right now: it may sit for minutes waiting for the person at the TV
    @Volatile private var waiting = false                             // the TV is showing "Allow USB debugging?"
    @Volatile private var failed = false                              // the last attempt did not get through
    @Volatile private var dialStart = 0L
    private val lock = Any()

    @Throws(IOException::class)
    fun get(): Adb {
        adb?.let { if (!it.isDead()) return it }
        synchronized(lock) {
            adb?.let { if (!it.isDead()) return it }
            val a = Adb(keyDir)
            dialing = a
            dialStart = System.currentTimeMillis()
            try {
                a.connect(host, 5555) { waiting = true }
                waiting = false
                failed = false
                adb = a
                return a
            } catch (e: IOException) {
                failed = true
                throw e
            } catch (e: Exception) {
                failed = true
                throw IOException(e.message ?: e.javaClass.simpleName)
            } finally {
                dialing = null
            }
        }
    }

    fun waitingForUser(): Boolean = waiting

    /**
     * How things stand, without waiting for anything: ready, unauthorized (the TV is asking the person), unknown (connecting)
     * or closed. Asking is also what starts a connection when there is none, so whoever shows this keeps it alive.
     */
    fun state(): String {
        adb?.let { if (!it.isDead()) return "ready" }
        val d = dialing
        if (d != null && !waiting && System.currentTimeMillis() - dialStart > 25000) {   // stuck dialing (a network that changed under it): cut it, say so
            failed = true
            try { d.close() } catch (ignored: Exception) { }
            return "closed"
        }
        if (d == null) Thread({ try { get() } catch (ignored: Exception) { } }, "adb-link").apply { isDaemon = true }.start()
        return if (waiting) "unauthorized" else if (failed) "closed" else "unknown"
    }

    /** "Try again": a fresh connection, whatever the old one thought of itself. */
    fun reconnect(): String {
        close()
        failed = false
        return state()
    }

    /** Back on screen after a while: checks that the connection still answers (in the background), and dials again if not. */
    fun check() {
        val a = adb ?: return
        Thread({ if (!a.alive(4000)) { try { get() } catch (ignored: Exception) { } } }, "adb-check").apply { isDaemon = true }.start()
    }

    /** Never waits, not even for a connection that is still being made: that one is cut off. */
    fun close() {
        val d = dialing
        val a = adb
        adb = null
        waiting = false
        try { d?.close() } catch (ignored: Exception) { }
        try { a?.close() } catch (ignored: Exception) { }
    }
}
