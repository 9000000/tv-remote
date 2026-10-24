package dev.tvremote.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The anonymous census: the body has exactly the agreed fields, goes once a day per (app, model), and not at all when switched off. */
class CensusTest {
    private val mem = HashMap<String, String>()
    private val sent = ArrayList<Pair<String, String>>()
    private var now = 20_000L * 86_400_000L
    private var accept = true
    private val getprop = """
        [ro.product.brand]: [Google]
        [ro.product.model]: [Chromecast  ]
        [ro.product.device]: [sabrina]
        [ro.product.board]: [sabrina]
        [ro.hardware]: [amlogic]
        [ro.board.platform]: [g12a]
        [ro.build.version.release]: [14]
        [ro.build.version.sdk]: [34]
        [ro.serialno]: [SECRET123]
        [persist.sys.device_name]: [Bro's living room TV]
        [net.hostname]: [android-1234]
        [ro.build.fingerprint]: [Google/sabrina/x]
        @@
        1
    """.trimIndent()

    @Before fun setUp() {
        Census.reset(); mem.clear(); sent.clear(); accept = true
        Census.background = false
        Census.clock = { now }
        Census.url = "http://catcher.test"
        Census.post = { u, b -> sent.add(u to b); accept }
        Census.init("phone", "0.3.1", object : Census.Store {
            override fun get(key: String) = mem[key]
            override fun put(key: String, value: String) { mem[key] = value }
        })
    }
    @After fun tearDown() { Census.reset(); Census.background = true; Census.store = null; Census.app = ""; Census.self = null; Census.post = { _, _ -> false } }

    @Test fun payloadHasTheAgreedFieldsAndNothingElse() {
        val j = Census.payload("abc", "phone", "0.3.1", Census.parse(getprop), Census.Ok(adb = true))
        assertEquals(setOf("id", "app", "v", "tv", "ok"), j.keys().asSequence().toSet())
        assertEquals(setOf("brand", "model", "device", "board", "hw", "soc", "android", "sdk", "leanback"), j.getJSONObject("tv").keys().asSequence().toSet())
        assertEquals(setOf("adb", "video", "optimize"), j.getJSONObject("ok").keys().asSequence().toSet())
        val tv = j.getJSONObject("tv")
        assertEquals("Chromecast", tv.getString("model"))             // trimmed
        assertEquals("g12a", tv.getString("soc"))                     // ro.soc.model missing: the platform
        assertEquals(34, tv.getInt("sdk"))
        assertTrue(tv.getBoolean("leanback"))
        assertTrue(j.getJSONObject("ok").getBoolean("adb"))
        assertTrue(j.getJSONObject("ok").isNull("video"))
        val all = j.toString()
        for (secret in listOf("SECRET123", "living room", "android-1234", "fingerprint")) assertFalse(secret, all.contains(secret))
    }

    @Test fun longOrDirtyValuesAreCut() {
        val p = Census.parse("[ro.product.model]: [" + "x".repeat(200) + "\u0007]\n@@\n0")
        val m = Census.payload("i", "pc", "1", p, Census.Ok()).getJSONObject("tv")
        assertEquals(64, m.getString("model").length)
        assertFalse(m.getBoolean("leanback"))
    }

    @Test fun oneCallPerDayPerModelAndAgainOnNewVersionOrNewResult() {
        Census.connected("10.0.0.5") { getprop }
        assertEquals(1, sent.size)
        assertEquals("http://catcher.test/v1/ping", sent[0].first)
        Census.connected("10.0.0.5") { getprop }                      // a reconnect the same day
        assertEquals(1, sent.size)
        Census.video("10.0.0.5")                                      // a result that became true
        assertEquals(2, sent.size)
        Census.video("10.0.0.5")
        assertEquals(2, sent.size)
        now += 86_400_000L                                            // next day
        Census.connected("10.0.0.5") { getprop }
        assertEquals(3, sent.size)
        Census.version = "0.3.2"                                      // the app updated
        Census.connected("10.0.0.5") { getprop }
        assertEquals(4, sent.size)
        Census.connected("10.0.0.5") { getprop }
        assertEquals(4, sent.size)
    }

    @Test fun anotherModelOrAppIsAnotherEntry() {
        Census.connected("a") { getprop }
        Census.connected("b") { getprop.replace("Chromecast", "Mi Box") }
        assertEquals(2, sent.size)
    }

    @Test fun aFailedSendIsNotRememberedAndWaitsBeforeTryingAgain() {
        accept = false
        Census.connected("a") { getprop }
        Census.connected("a") { getprop }
        assertEquals(1, sent.size)                                    // not hammering
        now += 11 * 60_000L; accept = true
        Census.connected("a") { getprop }
        assertEquals(2, sent.size)
        Census.connected("a") { getprop }
        assertEquals(2, sent.size)
    }

    @Test fun switchedOffNothingIsSentNotEvenAsked() {
        Census.enabled = false
        var asked = false
        Census.connected("a") { asked = true; getprop }
        Census.video("a"); Census.optimize("a")
        assertEquals(0, sent.size)
        assertFalse(asked)
        Census.enabled = true
        Census.connected("a") { getprop }
        assertEquals(1, sent.size)
    }

    @Test fun onByDefaultAndNothingWhileTheHostIsAPlaceholder() {
        assertTrue(Census.enabled)
        assertFalse("Census.URL must be a real host", Census.URL.contains("REPLACE"))
        Census.url = "https://tvr-census.REPLACE.workers.dev"
        Census.connected("a") { getprop }
        assertEquals(0, sent.size)
    }

    @Test fun tvSetupReadsItsOwnPropertiesAndReportsAFailedLink() {
        Census.init("setup", "0.2.0", Census.store!!)
        Census.self = { Census.parse(getprop) }
        var asked = false
        Census.failed("127.0.0.1")
        assertEquals(1, sent.size)
        val j = org.json.JSONObject(sent[0].second)
        assertEquals("setup", j.getString("app"))
        assertFalse(j.getJSONObject("ok").getBoolean("adb"))
        Census.connected("127.0.0.1") { asked = true; "" }            // it did connect after all: a result that became true
        assertFalse(asked)
        assertEquals(2, sent.size)
        assertTrue(org.json.JSONObject(sent[1].second).getJSONObject("ok").getBoolean("adb"))
    }

    @Test fun theIdIsMadeOnceAndIsAUuid() {
        val a = Census.id()
        assertEquals(36, a.length)
        assertEquals(a, Census.id())
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(a))
    }

    @Test fun reachedWithoutAdbNothingIsKnownSoNothingIsSent() {
        Census.video("never-connected")
        assertEquals(0, sent.size)
    }
}
