package dev.tvremote.update

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

/** The one reader of the site's signed lists: a good signature, a bad one, the next place when one fails, and the kept / packed copies. Uses the real launchers.json the site signs. */
class SignedListTest {
    private val dir = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "docs/launchers") }.first { it.isDirectory }
    private val list = File(dir, "launchers.json").readBytes()
    private val sig = File(dir, "launchers.json.sig").readText()

    /** A server for [files] (path -> bytes); everything else is 404. Plain sockets: the Android test classpath has no com.sun.net.httpserver. */
    private class Site(files: Map<String, ByteArray>) {
        private val ss = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val base = "http://127.0.0.1:${ss.localPort}/"
        init {
            Thread {
                while (!ss.isClosed) try {
                    ss.accept().use { s ->
                        val path = s.getInputStream().bufferedReader().readLine().split(" ")[1]
                        val b = files[path]
                        val o = s.getOutputStream()
                        o.write((if (b == null) "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n" else "HTTP/1.1 200 OK\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n").toByteArray())
                        if (b != null) o.write(b)
                        o.flush()
                    }
                } catch (e: IOException) { }
            }.apply { isDaemon = true }.start()
        }
        fun stop() = ss.close()
    }
    private fun serve(files: Map<String, ByteArray>) = Site(files)
    private fun Site.base() = base

    @Test fun goodAndBadSignatures() {
        assertTrue(SignedList.verify(list, sig))
        assertFalse(SignedList.verify(list + 1, sig))                              // one byte more
        assertFalse(SignedList.verify(list, ""))
        assertFalse(SignedList.verify(list, "not base64 !!"))
        assertFalse(SignedList.verify(list, sig, listOf("00".repeat(32))))         // another key
    }

    @Test fun nextPlaceWhenOneIsGoneOrUnsigned() {
        val gone = serve(emptyMap())
        val forged = serve(mapOf("/l.json" to list + 1, "/l.json.sig" to sig.toByteArray()))
        val good = serve(mapOf("/l.json" to list, "/l.json.sig" to sig.toByteArray()))
        try {
            val got = SignedList.fetch(listOf(gone.base(), forged.base(), good.base()), "l.json", 1 shl 20) { l, s, b -> Triple(l, s, b) }
            assertArrayEquals(list, got.first); assertEquals(sig, got.second); assertEquals(good.base(), got.third)
            try { SignedList.fetch(listOf(gone.base(), forged.base()), "l.json", 1 shl 20, unsigned = "nope") { l, _, _ -> l }; fail() }
            catch (e: IOException) { assertEquals("nope", e.message) }             // the last place's error
            try { SignedList.fetch(listOf(good.base()), "l.json", 10) { l, _, _ -> l }; fail() } catch (e: IOException) { assertEquals("list too big", e.message) }
            try { SignedList.fetch(listOf(good.base()), "l.json", 1 shl 20) { _, _, _ -> null }; fail() } catch (e: IOException) { assertEquals("no l.json", e.message) }   // every place refused it
        } finally { gone.stop(); forged.stop(); good.stop() }
    }

    @Test fun keptAndPackedCopies() {
        val d = createTempDir("signed")
        try {
            val f = File(d, "l.json")
            assertNull(SignedList.kept(f))
            SignedList.keep(f, list, sig)
            assertArrayEquals(list, SignedList.kept(f))
            assertTrue(File(d, "l.json.ok").isFile)                                 // checked once: the next read compares a hash
            assertArrayEquals(list, SignedList.kept(f))
            f.writeBytes(list + 1)                                                  // altered on disk: refused, the old mark does not cover it
            assertNull(SignedList.kept(f))
            val assets = mapOf("l.json" to list, "l.json.sig" to sig.toByteArray())
            val open = { n: String -> ByteArrayInputStream(assets[n] ?: throw FileNotFoundException(n)) }
            assertArrayEquals(list, SignedList.packed(open, "l.json"))
            assertNull(SignedList.packed(open, "other.json"))
            assertNull(SignedList.packed({ n -> ByteArrayInputStream(if (n.endsWith(".sig")) "AAAA".toByteArray() else list) }, "l.json"))
        } finally { d.deleteRecursively() }
    }
}
