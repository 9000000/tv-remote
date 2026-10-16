package dev.tvremote.desktop

import dev.tvremote.core.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

/**
 * The computer app's routing, called without a socket: the key every request must carry (Pass.kt), the page and its files,
 * unknown routes, and that a few routes reach their handlers. No TV is selected, so nothing here touches the network.
 */
class ServerTest {
    @get:Rule val dir = TemporaryFolder()
    private lateinit var server: Server
    private lateinit var pass: Pass
    private lateinit var saved: Saved

    private fun repo(): File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/shared/web/index.html").isFile }

    @Before fun start() {
        pass = Pass(dir.newFile("pass"))
        saved = Saved(File(dir.root, "tvs.json"))
        server = Server(repo(), dir.newFolder("keys"), pass, dir.newFolder("cache"), saved, null) { }
    }

    @After fun end() { server.close() }

    private fun req(method: String, path: String, query: Map<String, String> = emptyMap(), headers: Map<String, String> = emptyMap(), json: JSONObject? = null,
                    keyed: Boolean = false): HttpServer.Response {
        val r = HttpServer.Request()
        r.method = method; r.path = path
        r.query.putAll(query)
        r.headers["host"] = "127.0.0.1:8765"
        if (keyed) r.headers[Pass.HEADER] = pass.key
        r.headers.putAll(headers)
        if (json != null) {
            val b = json.toString().toByteArray()
            r.body = ByteArrayInputStream(b); r.length = b.size.toLong()
            r.headers["content-type"] = "application/json"
        }
        return server.handle(r)
    }

    private fun text(r: HttpServer.Response) = String(r.body, Charsets.UTF_8)

    // ---------------------------------------------------------------- the per-start key
    @Test fun everyApiRequestWithoutTheKeyIsRefused() {
        val gets = listOf("/api/state", "/api/about", "/api/tvs", "/api/scenes", "/api/wirelog", "/api/update", "/api/screen", "/api/nope")
        for (p in gets) assertEquals(p, 403, req("GET", p).status)
        val posts = listOf("/api/key", "/api/scene/run", "/api/scenes", "/api/tv/remove", "/api/shutdown", "/api/update", "/api/pair/start")
        for (p in posts) assertEquals(p, 403, req("POST", p, json = JSONObject()).status)
    }

    @Test fun aWrongKeyIsRefusedAndTheLinkKeyDoesNotOpenTheApi() {
        assertEquals(403, req("GET", "/api/tvs", headers = mapOf(Pass.HEADER to "wrong-wrong")).status)
        assertEquals(403, req("GET", "/api/tvs", headers = mapOf("cookie" to "${Pass.COOKIE}=wrong-wrong")).status)
        assertEquals(403, req("GET", "/api/tvs", query = mapOf("k" to pass.key)).status)    // ?k= is for the page's link only
    }

    @Test fun theHeaderOrTheCookieLetsARequestThrough() {
        assertEquals(200, req("GET", "/api/tvs", keyed = true).status)
        assertEquals(200, req("GET", "/api/tvs", headers = mapOf("cookie" to "${Pass.COOKIE}=${pass.key}")).status)
    }

    @Test fun theLinkWithTheKeyBecomesACookieAndLeavesTheAddressBar() {
        val r = req("GET", "/", query = mapOf("k" to pass.key, "lang" to "en"))
        assertEquals(302, r.status)
        assertEquals("/?lang=en", r.headers["Location"])
        val cookie = r.headers["Set-Cookie"]!!
        assertTrue(cookie, cookie.startsWith("${Pass.COOKIE}=${pass.key};") && "HttpOnly" in cookie && "SameSite=Strict" in cookie)
    }

    @Test fun theKeyIsKeptOnDiskForASecondStart() {
        assertEquals("", Pass.read(File(dir.root, "pass")))            // not before the port is ours (Main: a start that finds it taken)
        pass.save()
        assertEquals(pass.key, Pass.read(File(dir.root, "pass")))
    }

    // ---------------------------------------------------------------- the browser's guards
    @Test fun otherHostsAndOtherSitesAreRefused() {
        assertEquals(403, req("GET", "/api/tvs", keyed = true, headers = mapOf("host" to "evil.example:8765")).status)
        assertEquals(403, req("GET", "/api/tvs", keyed = true, headers = mapOf("origin" to "http://evil.example")).status)
        assertEquals(403, req("GET", "/api/tvs", keyed = true, headers = mapOf("sec-fetch-site" to "cross-site")).status)
        assertEquals(200, req("GET", "/api/tvs", keyed = true, headers = mapOf("origin" to "http://127.0.0.1:8765", "sec-fetch-site" to "same-origin")).status)
    }

    @Test fun aPostNeedsJson() {
        val bad = HttpServer.Request().apply { method = "POST"; path = "/api/key"; headers["host"] = "127.0.0.1"; headers[Pass.HEADER] = pass.key; headers["content-type"] = "text/plain" }
        assertEquals(415, server.handle(bad).status)
        val broken = HttpServer.Request().apply {
            method = "POST"; path = "/api/scenes"; headers["host"] = "127.0.0.1"; headers[Pass.HEADER] = pass.key; headers["content-type"] = "application/json"
            body = ByteArrayInputStream("{nope".toByteArray()); length = 5
        }
        assertEquals(400, server.handle(broken).status)
    }

    // ---------------------------------------------------------------- the page
    @Test fun theLockedPageSaysHowToOpenTheApp() {
        val r = req("GET", "/")
        assertEquals(403, r.status)
        assertTrue(r.type.startsWith("text/html"))
        assertTrue(text(r).contains("TV Remote"))
    }

    @Test fun thePageAndItsFilesAreServedToWhoHoldsTheKey() {
        val index = req("GET", "/", keyed = true)
        assertEquals(200, index.status)
        assertTrue(index.type.startsWith("text/html"))
        assertTrue(text(index).contains("data-core=\"pc\""))
        val css = req("GET", "/style.css", keyed = true)
        assertEquals(200, css.status)
        assertTrue(css.type.startsWith("text/css"))
        val js = req("GET", "/js/core.js", keyed = true)
        assertEquals(200, js.status)
        assertTrue(js.type.contains("javascript"))
        val spec = req("GET", "/remote.json", keyed = true)
        assertEquals(200, spec.status)
        JSONObject(text(spec))
    }

    @Test fun unknownFilesAreNotFoundAndCannotEscapeTheFolder() {
        assertEquals(404, req("GET", "/nothing-here.txt", keyed = true).status)
        assertEquals(404, req("GET", "/js/nothing.js", keyed = true).status)
        assertTrue(req("GET", "/../../VERSION", keyed = true).status in setOf(400, 403, 404))
        assertTrue(req("GET", "/..%2f..%2fVERSION", keyed = true).status in setOf(400, 403, 404))
    }

    // ---------------------------------------------------------------- the routes
    @Test fun anUnknownApiRouteWithNoTvAsksToPickOne() {
        val r = req("GET", "/api/nope", keyed = true)
        assertEquals(400, r.status)
        assertEquals("no TV selected", JSONObject(text(r)).getString("error"))
    }

    @Test fun stateAndTheTvListAnswerWithNoTv() {
        val state = JSONObject(text(req("GET", "/api/state", keyed = true)))
        assertTrue(state.has("connected") || state.has("host"))
        val tvs = JSONObject(text(req("GET", "/api/tvs", keyed = true)))
        assertEquals(0, tvs.getJSONArray("tvs").length())
    }

    @Test fun updateReportsNotPackagedWhenRunFromTheRepo() {
        assertEquals(false, JSONObject(text(req("GET", "/api/update", keyed = true))).getBoolean("packaged"))
        assertEquals(400, req("POST", "/api/update", keyed = true, json = JSONObject().put("do", "other")).status)
    }

    @Test fun scenesAreSavedCleanedAndRunByIdOrSteps() {
        val list = JSONArray().put(JSONObject().put("id", "s1").put("name", "Night").put("steps", JSONArray().put(JSONObject().put("do", "wake")).put(JSONObject().put("do", "bogus"))))
        val out = JSONObject(text(req("POST", "/api/scenes", keyed = true, json = JSONObject().put("scenes", list))))
        assertEquals(1, out.getJSONArray("scenes").getJSONObject(0).getJSONArray("steps").length())   // the unknown step was dropped
        assertEquals(1, JSONObject(text(req("GET", "/api/scenes", keyed = true))).getJSONArray("scenes").length())
        assertEquals(400, req("POST", "/api/scenes", keyed = true, json = JSONObject()).status)          // a broken import must not wipe them
        assertEquals(1, saved.scenes.length())
        assertEquals(404, req("POST", "/api/scene/run", keyed = true, json = JSONObject().put("id", "nope")).status)
        val none = req("POST", "/api/scene/run", keyed = true, json = JSONObject().put("steps", JSONArray()))
        assertEquals(200, none.status)                                                                   // reached the scene runner: nothing to do, nothing failed
        assertEquals(true, JSONObject(text(none)).getBoolean("ok"))
        val noTv = req("POST", "/api/scene/run", keyed = true, json = JSONObject().put("id", "s1"))
        assertEquals(200, noTv.status)                                                                   // a failed step is named, not an HTTP error
        assertTrue(JSONObject(text(noTv)).getString("failed").startsWith("1:"))
    }
}
