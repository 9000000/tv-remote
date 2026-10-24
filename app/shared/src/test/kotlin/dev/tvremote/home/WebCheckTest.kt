package dev.tvremote.home

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket

class WebCheckTest {
    private class Route(val code: Int, val type: String?, val body: String, val extra: Map<String, String> = emptyMap())
    private val routes = mapOf(
        "/page" to Route(200, "text/html; charset=utf-8", "<html><head><title> Bảng  điều khiển &amp; nhà </title></head></html>"),
        "/gone" to Route(404, "text/html", "no"),
        "/hop" to Route(302, null, "", mapOf("Location" to "/page")),
        "/loop" to Route(302, null, "", mapOf("Location" to "/loop")),
        "/file" to Route(200, "application/zip", "PK"),
        "/pic" to Route(200, "image/png", "x"))
    private lateinit var srv: ServerSocket
    private val base get() = "http://127.0.0.1:${srv.localPort}"

    @Before fun up() {                                                // a few lines of HTTP/1.0: the unit tests run on android.jar, no JDK server
        srv = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        Thread {
            while (!srv.isClosed) {
                val k = try { srv.accept() } catch (e: Exception) { break }
                k.use {
                    val path = k.getInputStream().bufferedReader().readLine()?.split(" ")?.getOrNull(1) ?: "/"
                    val r = routes[path] ?: Route(404, "text/plain", "")
                    val b = r.body.toByteArray()
                    val head = StringBuilder("HTTP/1.0 ${r.code} X\r\nContent-Length: ${b.size}\r\n")
                    r.type?.let { head.append("Content-Type: $it\r\n") }; r.extra.forEach { (n, v) -> head.append("$n: $v\r\n") }
                    k.getOutputStream().apply { write(head.append("\r\n").toString().toByteArray()); write(b); flush() }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    @After fun down() = srv.close()

    @Test fun aPageIsGoodWithItsTitle() {
        val r = WebCheck.check("$base/page")
        assertTrue(r.toString(), r.getBoolean("ok")); assertEquals("Bảng điều khiển & nhà", r.getString("title"))
    }

    @Test fun redirectsAreFollowedToTheEnd() {
        val r = WebCheck.check("$base/hop")
        assertTrue(r.getBoolean("ok")); assertEquals("$base/page", r.getString("url"))
        assertEquals("loop", WebCheck.check("$base/loop").getString("why"))
    }

    @Test fun whatTheTvCannotShow() {
        assertEquals("bad", WebCheck.check("ftp://x").getString("why"))
        assertEquals("bad", WebCheck.check("hello").getString("why"))
        WebCheck.check("$base/gone").let { assertFalse(it.getBoolean("ok")); assertEquals("status", it.getString("why")); assertEquals(404, it.getInt("code")) }
        assertEquals("type", WebCheck.check("$base/file").getString("why"))
        assertTrue("a picture shows in the WebView", WebCheck.check("$base/pic").getBoolean("ok"))
    }

    @Test fun aServerThatIsOff() {
        val port = ServerSocket(0).use { it.localPort }                 // free now, nobody listening
        assertEquals("refused", WebCheck.check("http://127.0.0.1:$port/", 2000).getString("why"))
        assertEquals("dns", WebCheck.check("http://no-such-host.invalid/", 2000).getString("why"))
    }
}
