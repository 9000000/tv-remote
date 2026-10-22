package dev.tvremote.core

import dev.tvremote.core.ontv.Privacy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The privacy check's parsers. The stream is what the script prints on the Google TV emulator (Android 14), trimmed; the mic lines are in `appops`' own format. */
class PrivacyTest {
    private val known = mapOf("com.amazon.tv.acr" to ("acr" to "Fire TV content recognition"), "com.zeasn.whaletrackservice" to ("track" to "Zeasn tracking"))

    private val stream = """
#sys
package:com.google.android.tvlauncher
package:com.google.android.tts
package:com.google.android.apps.tv.launcherx
#off
package:com.google.android.tvlauncher
#known
package:com.amazon.tv.acr
#a11y
dev.tvremote.setup/dev.tvremote.setup.KeyFixService
#notif
com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.coreservices.notificationlistener.TvNotificationListenerService
#assist
null
null
com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService
#admin
  com.example.mdm/com.example.mdm.Admin:
#overlay
dev.tvremote.setup
dev.tvremote.games
#usage
dev.tvremote.setup
#revoked
com.example.voice RECORD_AUDIO
#perms
Packages:
  Package [dev.tvremote.home] (a601e41):
        android.permission.ACCESS_FINE_LOCATION: granted=true, flags=[ USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED]
  Package [dev.tvremote.setup] (e9d9927):
      android.permission.RECEIVE_BOOT_COMPLETED: granted=true
  Package [com.google.android.tts] (97f17aa):
        android.permission.RECORD_AUDIO: granted=true, flags=[ GRANTED_BY_DEFAULT]
  Package [com.example.voice] (1234567):
        android.permission.RECORD_AUDIO: granted=true, flags=[ USER_SENSITIVE_WHEN_GRANTED]
        android.permission.CAMERA: granted=true, flags=[ USER_SENSITIVE_WHEN_GRANTED]
        android.permission.ACCESS_COARSE_LOCATION: granted=true, flags=[]
Hidden system packages:
        android.permission.RECORD_AUDIO: granted=true, flags=[ SYSTEM_FIXED]
#total 6
A 1 com.amazon.tv.acr
A 2 com.example.voice RECORD_AUDIO: allow; time=+12m3s ago|CAMERA: allow|
A 3 com.google.android.apps.tv.launcherx
A 4 com.google.android.tts Uid mode: RECORD_AUDIO: foreground|
A 5 dev.tvremote.home Uid mode: COARSE_LOCATION: foreground|FINE_LOCATION: foreground|FINE_LOCATION: allow; time=+12h49m43s190ms ago|
A 6 dev.tvremote.setup
#done
""".trim()

    private fun rows(): Map<String, JSONObject> {
        val r = Privacy.Reader(known)
        val out = LinkedHashMap<String, JSONObject>()
        for (l in stream.lines()) r.line(l)?.let { out[it.getString("pkg")] = it }
        assertEquals(6, r.total); assertTrue(r.done)
        return out
    }
    private fun tags(o: JSONObject) = (0 until o.getJSONArray("tags").length()).map { o.getJSONArray("tags").getString(it) }
    private fun evs(o: JSONObject) = (0 until o.getJSONArray("ev").length()).map { o.getJSONArray("ev").getJSONObject(it).getString("id") }

    @Test fun durationsInAppopsWords() {
        assertEquals(12 * 60 + 3L, Privacy.secs("12m3s"))
        assertEquals(12 * 3600 + 49 * 60 + 43L, Privacy.secs("12h49m43s190ms"))
        assertEquals(86400 + 37 * 60L, Privacy.secs("1d0h37m"))
        assertEquals(0L, Privacy.secs("5ms"))
        assertNull(Privacy.secs("abc"))
    }

    @Test fun micUsedAMinutesAgoIsHighAndSaysSo() {
        val v = rows().getValue("com.example.voice")
        assertEquals("high", v.getString("grade"))
        assertEquals(listOf("mic", "cam", "loc"), tags(v))
        assertTrue(evs(v).containsAll(listOf("mic_used", "cam_idle", "loc_idle")))
        val used = (0 until v.getJSONArray("ev").length()).map { v.getJSONArray("ev").getJSONObject(it) }.first { it.getString("id") == "mic_used" }
        assertEquals(723, used.getInt("n"))
        assertEquals("RECORD_AUDIO", v.getJSONArray("revoked").getString(0))       // kept on the TV from an earlier Revoke
    }

    @Test fun contentRecognitionAndAccessibilityAreHigh() {
        val r = rows()
        assertEquals("high", r.getValue("com.amazon.tv.acr").getString("grade"))
        assertEquals(listOf("acr"), tags(r.getValue("com.amazon.tv.acr")))
        val s = r.getValue("dev.tvremote.setup")
        assertEquals("high", s.getString("grade"))
        assertEquals(listOf("a11y", "overlay", "usage", "boot"), tags(s))
    }

    @Test fun specialAccessAndSystemAppsHoldingOnly() {
        val r = rows()
        assertEquals("med", r.getValue("com.google.android.apps.tv.launcherx").getString("grade"))      // a notification listener
        assertEquals(listOf("notif"), tags(r.getValue("com.google.android.apps.tv.launcherx")))
        val tts = r.getValue("com.google.android.tts")                                                    // a system app that holds the mic, never seen using it, but the voice recognizer
        assertEquals("med", tts.getString("grade"))
        assertTrue(tags(tts).containsAll(listOf("mic", "assist")))
        assertTrue(r.getValue("com.google.android.tts").getBoolean("sys"))
    }

    @Test fun locationUsedHalfADayAgoIsMediumNotHigh() {
        val h = rows().getValue("dev.tvremote.home")
        assertEquals("med", h.getString("grade"))
        assertEquals(listOf("loc"), tags(h))
        assertTrue(evs(h).contains("loc_used"))
    }

    @Test fun gradeRules() {
        val none = emptySet<String>()
        assertEquals("low", Privacy.grade(setOf("mic"), emptyMap(), none, true, false, null))             // a system app that only holds it
        assertEquals("med", Privacy.grade(setOf("mic"), emptyMap(), none, false, false, null))            // a user's app that holds it
        assertEquals("high", Privacy.grade(setOf("mic"), mapOf("mic" to 3600L), none, true, false, null))
        assertEquals("med", Privacy.grade(setOf("mic"), mapOf("mic" to 3L * 86400), none, true, false, null))   // used, but days ago
        assertEquals("high", Privacy.grade(setOf("cam"), emptyMap(), setOf("cam"), true, false, null))     // in use right now
        assertEquals("low", Privacy.grade(setOf("mic", "a11y"), emptyMap(), none, false, true, null))      // turned off: nothing more to fear
        assertEquals("med", Privacy.grade(emptySet(), emptyMap(), none, false, false, "track"))
    }

    @Test fun deviceAdminIsRead() {
        val r = Privacy.Reader(known)
        for (l in listOf("#admin", "  com.example.mdm/com.example.mdm.Admin:", "#perms", "A 1 com.example.mdm ")) r.line(l)?.let {
            assertTrue(tags(it).contains("admin")); assertEquals("med", it.getString("grade"))
            return
        }
        throw AssertionError("no row")
    }

    @Test fun scriptIsOneLowPriorityStream() {
        val c = Privacy.command(listOf("com.amazon.tv.acr"))
        assertTrue(c.startsWith("nice -n 19 sh -c '"))
        assertTrue(c.contains("com\\.amazon\\.tv\\.acr"))
        assertTrue(c.contains("dumpsys package packages"))
        assertFalse(c.contains("dumpsys package ${'$'}"))                                                   // never one dumpsys per app
        assertTrue(c.contains("sleep 0.05"))
    }
}
