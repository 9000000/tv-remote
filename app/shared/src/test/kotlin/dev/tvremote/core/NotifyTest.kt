package dev.tvremote.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import dev.tvremote.core.ontv.Notify
import dev.tvremote.core.ontv.SetupNeed

class NotifyTest {
    private fun clean(s: String) = Notify.clean(JSONObject(s))

    @Test fun busyAsksToLeaveTheRemoteAloneThenSaysDoneAndNeverThrows() {
        val sent = ArrayList<JSONObject>()
        val tv = { cmd: String ->
            sent.add(JSONObject(String(B64.decode(Regex("--es n (\\S+)").find(cmd)!!.groupValues[1]), Charsets.UTF_8)))
            "Broadcast completed: result=-1, data=\"" + B64.encode("{\"ok\":true}".toByteArray()) + "\""
        }
        Notify.busy(tv, "vi", "Đang bật Tailscale")
        Notify.busy(tv, "en", null)
        assertTrue(sent[0].getString("text").startsWith("Đang bật Tailscale. Đừng bấm remote"))
        assertEquals(Notify.MAX_SECONDS, sent[0].getInt("seconds"))   // outlasts the job; the next card takes its place
        assertEquals("TV Remote is done", sent[1].getString("title"))
        Notify.busy({ "Broadcast completed: result=0" }, "vi", "x")   // no TV Setup: goes without, no error
    }

    @Test fun cornerIsOneOfFourOrTheDefault() {
        for (c in Notify.CORNERS) assertEquals(c, Notify.corner(c))
        assertEquals("bottom-left", Notify.corner("  Bottom-Left "))
        for (bad in listOf(null, "", "middle", "top", "left-top")) assertEquals("top-right", Notify.corner(bad))
    }

    @Test fun secondsAreWholeAndClamped() {
        assertEquals(5, Notify.seconds(null))
        assertEquals(5, Notify.seconds("abc"))
        assertEquals(5, Notify.seconds(Double.NaN))
        assertEquals(1, Notify.seconds(0))
        assertEquals(1, Notify.seconds(-30))
        assertEquals(60, Notify.seconds(9999))
        assertEquals(10, Notify.seconds("10"))
        assertEquals(3, Notify.seconds(2.6))
        assertEquals(30, Notify.seconds(30))
    }

    @Test fun cleanKeepsWhatTheTvDraws() {
        val o = clean("""{"title":"  Dinner ","text":" Come down ","corner":"bottom-right","seconds":"10"}""")
        assertEquals("Dinner", o.getString("title"))
        assertEquals("Come down", o.getString("text"))
        assertEquals("bottom-right", o.getString("corner"))
        assertEquals(10, o.getInt("seconds"))
    }

    @Test fun cleanFillsDefaultsAndCutsLongWords() {
        val o = clean("""{"text":"${"x".repeat(500)}","title":"${"t".repeat(100)}","corner":"nowhere","seconds":-4}""")
        assertEquals(Notify.MAX_TEXT, o.getString("text").length)
        assertEquals(Notify.MAX_TITLE, o.getString("title").length)
        assertEquals("top-right", o.getString("corner"))
        assertEquals(1, o.getInt("seconds"))
        assertEquals(5, clean("""{"text":"a"}""").getInt("seconds"))
    }

    @Test fun aTitleAloneOrATextAloneIsEnoughButNothingIsNot() {
        assertEquals("", clean("""{"title":"Hi"}""").getString("text"))
        assertEquals("", clean("""{"text":"Hi"}""").getString("title"))
        for (empty in listOf("{}", """{"title":"  ","text":""}""")) try { clean(empty); fail(empty) } catch (e: IllegalArgumentException) { }
    }

    @Test fun sendBroadcastsToTheReceiverWithTheCleanedMessageAsBase64() {
        val asked = ArrayList<String>()
        val answer = B64.encode("""{"ok":true}""".toByteArray())
        Notify.send({ asked.add(it); "Broadcast completed: result=-1, data=\"$answer\"" }, JSONObject("""{"text":"Hello","corner":"top-left","seconds":3}"""))
        val cmd = asked.single()
        assertTrue(cmd, "-n dev.tvremote.setup/.NotifyReceiver --es n " in cmd)
        val sent = JSONObject(String(B64.decode(cmd.substringAfter("--es n ").substringBefore(" ")), Charsets.UTF_8))
        assertEquals("top-left", sent.getString("corner"))
        assertEquals(3, sent.getInt("seconds"))
        assertEquals("Hello", sent.getString("text"))
    }

    @Test fun noAnswerMeansTvSetupIsMissingOrOldAndTheOverlayRefusalIsNamed() {
        val e = try { Notify.send({ if ("versionName" in it) "versionName=0.4.1" else "Broadcast completed: result=0" }, JSONObject("""{"text":"x"}""")); null } catch (e: SetupNeed.Missing) { e }
        assertEquals("setup:old:notify:0.4.2:0.4.1", e!!.message)
        val refused = B64.encode("""{"error":"overlay"}""".toByteArray())
        try { Notify.send({ "data=\"$refused\"" }, JSONObject("""{"text":"x"}""")); fail() } catch (e: java.io.IOException) { assertEquals("notify:nooverlay", e.message) }
    }

    @Test fun theFeatureNeedsTheNextTvSetup() {
        assertFalse(SetupNeed.ok("0.4.1", "notify"))
        assertTrue(SetupNeed.ok("0.4.2", "notify"))
        assertEquals("Gửi thông báo lên TV", SetupNeed.name("notify", "vi"))
    }
}
