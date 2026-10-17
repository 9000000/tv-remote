package dev.tvremote.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import dev.tvremote.core.remote.Atv
import dev.tvremote.core.remote.Remote
import dev.tvremote.core.remote.Samsung
import dev.tvremote.core.remote.TvKeys

/** The Samsung adapter against a fake Tizen TV: the remote channel, its token, keys, text, apps, and /api/v2/. */
class SamsungTest {
    /**
     * [pairing]: "prompt" (answers after a while, as when the person accepts), "deny", "timeout", "open" (a 2016 set on
     * 8001: no prompt, no token). It says a few things first, as real sets do (ed.edenTV.update, ms.voiceApp.hide).
     */
    private class FakeSamsung(var pairing: String = "prompt") : AutoCloseable {
        val got = CopyOnWriteArrayList<JSONObject>()
        val tokens = HashSet<String>()
        var prompts = 0
        val ws = FakeWs(
            onText = { c, t ->
                val m = JSONObject(t)
                got.add(m)
                val p = m.optJSONObject("params")
                if (m.optString("method") == "ms.channel.emit" && p?.optString("event") == "ed.installedApp.get")
                    c.send(JSONObject().put("event", "ed.installedApp.get").put("data", JSONObject().put("data", JSONArray()
                        .put(JSONObject().put("appId", "111299001912").put("name", "YouTube").put("icon", "/opt/x.png").put("app_type", 2))
                        .put(JSONObject().put("appId", "3201907018807").put("name", "Netflix"))
                        .put(JSONObject().put("appId", "3201601007250").put("name", "Plex").put("app_type", 4)))).toString())
                if (m.optString("method") == "ms.channel.emit" && p?.optString("event") == "ed.apps.icon")
                    c.send(JSONObject().put("event", "ed.apps.icon").put("data", JSONObject().put("iconPath", p.getJSONObject("data").getString("iconPath"))
                        .put("imageBase64", B64.encode(byteArrayOf(-119, 80, 78, 71)))).toString())
            },
            onOpen = { c ->
                c.send(JSONObject().put("event", "ed.edenTV.update").put("data", JSONObject()).toString())
                c.send(JSONObject().put("event", "ms.voiceApp.hide").put("data", JSONObject()).toString())
                if (pairing == "open") { c.send(JSONObject().put("event", "ms.channel.connect").put("data", JSONObject().put("clients", JSONArray())).toString()); return@FakeWs }
                val token = Regex("token=([^&]+)").find(c.path)?.groupValues?.get(1)
                if (token != null && token in tokens) { connected(c, token); return@FakeWs }
                prompts++
                Thread {
                    Thread.sleep(1800)                                // longer than the adapter's read timeout: it shows it is asking
                    when (pairing) {
                        "deny" -> c.send(JSONObject().put("event", "ms.channel.unauthorized").toString())
                        "timeout" -> c.send(JSONObject().put("event", "ms.channel.timeOut").toString())
                        else -> connected(c, "tok" + prompts)
                    }
                }.start()
            },
            onHttp = { path -> if (path == "/api/v2/") JSONObject().put("device", JSONObject().put("modelName", "QE55Q80B").put("name", "[TV] Living room")
                .put("OS", "Tizen").put("wifiMac", "f4:7b:09:aa:bb:cc").put("PowerState", "on").put("TokenAuthSupport", "true")).toString() else null })

        private fun connected(c: FakeWs.Conn, token: String) {
            tokens.add(token)
            c.send(JSONObject().put("event", "ms.channel.connect").put("data", JSONObject().put("token", token).put("clients", JSONArray())).toString())
        }

        fun keys() = got.filter { it.optJSONObject("params")?.optString("TypeOfRemote") == "SendRemoteKey" }
            .map { it.getJSONObject("params").let { p -> p.getString("Cmd") + " " + p.getString("DataOfCmd") } }

        override fun close() = ws.close()
    }

    private val dir: File = Files.createTempDirectory("samsungtest").toFile()
    private val tvs = ArrayList<FakeSamsung>()
    private val remotes = ArrayList<Samsung>()
    private fun tv(pairing: String = "prompt") = FakeSamsung(pairing).also { tvs.add(it) }
    // TLS is not in the fake: a plain port stands in for 8002
    private fun samsung(tv: FakeSamsung) = Samsung(dir, "127.0.0.1", listOf(tv.ws.port to false), tv.ws.port).also { remotes.add(it) }
    private fun until(what: () -> Boolean) { for (i in 0 until 60) { if (what()) return; Thread.sleep(50) }; fail("timed out") }

    @After fun done() { remotes.forEach { it.close() }; tvs.forEach { it.close() }; dir.deleteRecursively() }

    @Test fun pairsKeepsTheTokenAndLearnsTheTv() {
        val tv = tv()
        val a = samsung(tv)
        var asked = false
        val watch = Thread { while (!a.ready()) { if (a.asking) asked = true; Thread.sleep(50) } }
        watch.start()
        a.connect { }
        watch.join(1000)
        assertTrue(a.ready())
        assertTrue(asked)
        assertEquals("QE55Q80B", a.model)
        assertEquals("F4:7B:09:AA:BB:CC", a.mac)
        assertEquals(true, a.isOn)
        assertTrue(tv.ws.paths[0].contains("name=" + B64.encode("TV Remote".toByteArray())))
        assertFalse(tv.ws.unmasked)
        assertEquals("tok1", TvKeys(dir, "samsung").get("127.0.0.1"))
    }

    @Test fun deniedIsRefusedAndTimeoutIsAnError() {
        try { samsung(tv("deny")).connect { }; fail() } catch (e: Remote.Refused) { }
        try { samsung(tv("timeout")).connect { }; fail() } catch (e: Remote.Refused) { fail("not a refusal") } catch (e: java.io.IOException) { }
    }

    @Test fun keysHeldKeysTextAndApps() {
        val tv = tv()
        val a = samsung(tv)
        a.connect { }
        a.key(19, Atv.SHORT); a.key(23, Atv.SHORT); a.key(4, Atv.SHORT)
        a.key(24, Atv.DOWN); a.key(24, Atv.UP)
        a.key(85, Atv.SHORT); a.key(85, Atv.SHORT)
        a.key(219, Atv.SHORT)                                         // no Assistant key on Samsung: ignored
        a.text("hello")
        a.launch("3201907018807")
        a.launch("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        until { tv.got.size >= 10 }
        assertEquals(listOf("Click KEY_UP", "Click KEY_ENTER", "Click KEY_RETURN", "Press KEY_VOLUP", "Release KEY_VOLUP", "Click KEY_PLAY", "Click KEY_PAUSE"), tv.keys())
        val text = tv.got.first { it.optJSONObject("params")?.optString("TypeOfRemote") == "SendInputString" }.getJSONObject("params")
        assertEquals("hello", String(B64.decode(text.getString("Cmd"))))
        val launches = tv.got.filter { it.optJSONObject("params")?.optString("event") == "ed.apps.launch" }.map { it.getJSONObject("params").getJSONObject("data") }
        assertEquals("3201907018807", launches[0].getString("appId"))
        assertEquals(Samsung.YOUTUBE, launches[1].getString("appId"))
        assertEquals("dQw4w9WgXcQ", launches[1].getString("metaTag"))
        val apps = a.apps()
        assertEquals(listOf("YouTube", "Netflix", "Plex"), apps.map { it.label })
        assertFalse(a.setVolume(10))                                  // steps only
    }

    @Test fun theSecondTimeTheTokenLetsUsStraightIn() {
        val tv = tv()
        samsung(tv).connect { }
        val t0 = System.currentTimeMillis()
        val b = samsung(tv)
        b.connect { }
        assertTrue(b.ready())
        assertEquals(1, tv.prompts)
        assertTrue(System.currentTimeMillis() - t0 < 1500)
    }

    @Test fun aSetWithoutTokensConnectsAfterTheDeadSecurePort() {
        val tv = tv("open")
        val a = Samsung(dir, "127.0.0.1", listOf(FakeWs.deadPort() to false, tv.ws.port to false), tv.ws.port).also { remotes.add(it) }
        a.connect { }
        assertTrue(a.ready())
        assertEquals("", TvKeys(dir, "samsung").get("127.0.0.1"))      // nothing to keep
    }

    @Test fun eachNewKeyboardOnTheTvGetsAHelloBeforeText() {
        val tv = tv()
        val a = samsung(tv)
        a.connect { }
        a.text("a"); a.text("ab")
        until { tv.got.count { it.optJSONObject("params")?.optString("TypeOfRemote") == "SendInputString" } == 2 }
        tv.ws.conns.last().send(JSONObject().put("event", "ms.remote.imeEnd").toString())
        Thread.sleep(200)
        a.text("c")
        until { tv.got.count { it.optJSONObject("params")?.optString("TypeOfRemote") == "SendInputString" } == 3 }
        val seq = tv.got.map { it.optJSONObject("params")?.let { p -> p.optString("TypeOfRemote").ifEmpty { p.optString("event") } } }
            .filter { it == "SendInputString" || it == "custom.remote.textReceived" }
        assertEquals(listOf("custom.remote.textReceived", "SendInputString", "SendInputString", "custom.remote.textReceived", "SendInputString"), seq)
    }

    @Test fun appsLaunchTheWayTheListSaysAndTheirPicturesComeOverTheChannel() {
        val tv = tv()
        val a = samsung(tv)
        a.connect { }
        a.apps()
        a.launch("3201601007250")                                     // app_type 4: a native app, though its id is a number
        a.launch("111299001912")
        until { tv.got.count { it.optJSONObject("params")?.optString("event") == "ed.apps.launch" } == 2 }
        val types = tv.got.filter { it.optJSONObject("params")?.optString("event") == "ed.apps.launch" }.map { it.getJSONObject("params").getJSONObject("data").getString("action_type") }
        assertEquals(listOf("NATIVE_LAUNCH", "DEEP_LINK"), types)
        assertEquals(listOf(-119, 80, 78, 71), a.icon("111299001912")!!.map { it.toInt() })
        assertEquals(null, a.icon("3201907018807"))                  // no picture in the list
    }
}
