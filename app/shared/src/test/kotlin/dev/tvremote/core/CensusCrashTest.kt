package dev.tvremote.core

import dev.tvremote.guard.GuardLogic
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Crash reports ride the stats switch: scrubbed, once per error per run, nothing when off, a failure ignored. */
class CensusCrashTest {
    private val mem = HashMap<String, String>()
    private val sent = ArrayList<Pair<String, JSONObject>>()
    private var now = 20_000L * 86_400_000L
    private var accept = true
    private val stack = "at dev.tvremote.core.Foo.bar(Foo.kt:42)\nat /Users/bob/x.kt:1"

    @Before fun setUp() {
        Census.reset(); mem.clear(); sent.clear(); accept = true
        Census.background = false
        Census.clock = { now }
        Census.url = "http://catcher.test"
        Census.post = { u, b -> sent.add(u to JSONObject(b)); accept }
        Census.init("pc", "0.3.1", object : Census.Store {
            override fun get(key: String) = mem[key]
            override fun put(key: String, value: String) { mem[key] = value }
        })
    }
    @After fun tearDown() { Census.reset(); Census.background = true; Census.store = null; Census.app = ""; Census.post = { _, _ -> false } }

    @Test fun bodyHasTheAgreedFieldsScrubbed() {
        assertTrue(Census.crashNow("page", "TypeError", "no route to 192.168.1.7 for bob@x.com", stack))
        val (u, b) = sent.single()
        assertEquals("http://catcher.test/v1/crash", u)
        assertEquals(setOf("id", "app", "v", "where", "dev", "kind", "msg", "stack"), b.keys().asSequence().toSet())
        assertEquals("pc", b.getString("app")); assertEquals("page", b.getString("where")); assertEquals(36, b.getString("id").length)
        assertEquals("no route to x.x.x.x for email@x", b.getString("msg"))
        assertFalse(b.getString("stack").contains("bob"))
        assertTrue(b.getString("stack").contains("~/x.kt:1"))
        assertNotNull(b.getJSONObject("dev").optString("os").takeIf { it.isNotEmpty() })
    }

    @Test fun onceForTheSameErrorWhateverTheLine() {
        assertTrue(Census.crashNow("kotlin", "K", "m", stack))
        assertTrue(Census.crashNow("kotlin", "K", "m again", stack.replace("Foo.kt:42", "Foo.kt:99")))   // already reported: true, not sent again
        assertEquals(1, sent.size)
        assertTrue(Census.crashNow("kotlin", "K", "m", stack.replace("Foo.bar", "Foo.baz")))
        assertEquals(2, sent.size)
    }

    @Test fun nothingWhenOff() {
        Census.enabled = false
        assertFalse(Census.crashNow("kotlin", "K", "m", stack))
        var answered: Boolean? = null
        Census.crash("kotlin", "K", "m", stack) { answered = it }
        assertEquals(false, answered)
        assertTrue(sent.isEmpty())
    }

    @Test fun aFailureIsQuietAndWaitsAMinute() {
        accept = false
        assertFalse(Census.crashNow("kotlin", "K", "m", stack))
        assertFalse(Census.crashNow("kotlin", "K2", "m", stack))      // within the minute: not even tried
        assertEquals(1, sent.size)
        now += 61_000; accept = true
        assertTrue(Census.crashNow("kotlin", "K2", "m", stack))
        assertEquals(2, sent.size)
    }

    @Test fun guardTextSplitsBackIntoKindMessageStack() {
        val report = GuardLogic.report("t", "app 1", "14", "dev", "main", IllegalStateException("boom 1"))
        val (k, m, st) = GuardLogic.split(report)!!
        assertEquals("java.lang.IllegalStateException", k); assertEquals("boom 1", m)
        assertTrue(st.startsWith("at ") || st.startsWith("\tat ") || st.contains("CensusCrashTest"))
        assertEquals(null, GuardLogic.split("not a report"))
    }
}
