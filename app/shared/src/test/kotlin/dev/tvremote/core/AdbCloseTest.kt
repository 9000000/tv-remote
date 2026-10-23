package dev.tvremote.core

import java.io.File
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.tvremote.core.adb.Adb

/** Adb.close from the app's main thread must not touch the socket there (TLS writes close_notify: NetworkOnMainThreadException). */
class AdbCloseTest {
    private class Spy : Socket() {
        val by = CountDownLatch(1)
        @Volatile var thread = ""
        override fun close() { thread = Thread.currentThread().name; by.countDown() }
    }

    private fun closeFrom(name: String): Spy {
        val spy = Spy()
        val t = Thread({ Adb(File(System.getProperty("java.io.tmpdir"))).closeSock(spy) }, name)
        t.start(); t.join()
        assertTrue(spy.by.await(5, TimeUnit.SECONDS))
        return spy
    }

    @Test fun mainThreadHandsTheCloseOver() = assertEquals("adb-close", closeFrom("main").thread)
    @Test fun otherThreadsCloseThemselves() = assertEquals("worker", closeFrom("worker").thread)
}
