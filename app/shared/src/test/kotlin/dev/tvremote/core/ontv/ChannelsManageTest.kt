package dev.tvremote.core.ontv

import dev.tvremote.core.B64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelsManageTest {
    @Test fun sendsTheStateAndReadsTheAnswer() {
        var cmd = ""
        val answer = B64.encode("""{"version":"0.2.0","favs":[],"groups":[]}""".toByteArray())
        val r = Channels.manage({ cmd = it; "Broadcast completed: result=-1, data=\"$answer\"" }, JSONObject("""{"favs":["a"]}"""), "vn", true, true)
        assertTrue(cmd.contains("-n dev.tvremote.channels/.ChannelsReceiver"))
        assertTrue(cmd.contains("--es list vn") && cmd.contains("--ez fresh true") && cmd.contains("--ez countries true") && cmd.contains("--es set "))
        assertTrue(r.getBoolean("receiver") && r.getBoolean("installed"))
    }

    @Test fun anOldTvChannelsHasNoReceiver() {
        val r = Channels.manage({ if (it.startsWith("am ")) "Broadcast completed: result=0" else "versionName=0.1.0" }, null)
        assertFalse(r.getBoolean("receiver"))
        assertTrue(r.getBoolean("installed"))
    }

    @Test(expected = IllegalArgumentException::class) fun aCountryIsTwoLetters() { Channels.manage({ "" }, null, "v n; rm") }

    @Test fun countsEachCountrysChannelsOnce() {
        val m3u = """#EXTM3U
#EXTINF:-1 tvg-id="VTV1.vn@SD" group-title="Vietnam",VTV1
http://a
#EXTINF:-1 tvg-id="VTV1.vn@HD" group-title="Vietnam",VTV1 HD
http://b
#EXTINF:-1 tvg-id="" group-title="Vietnam",Local
http://c
#EXTINF:-1 tvg-id="CNN.us" group-title="United States",CNN
http://d
#EXTINF:-1 tvg-id="X.int" group-title="International",X
http://e""".lineSequence()
        assertEquals(mapOf("vn" to 2, "us" to 1), Channels.count(m3u, mapOf("Vietnam" to "vn", "United States" to "us")))
    }
}
