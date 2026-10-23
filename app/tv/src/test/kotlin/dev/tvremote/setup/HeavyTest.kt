package dev.tvremote.setup

import dev.tvremote.core.ontv.Setup
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeavyTest {
    private val yt = "com.google.android.youtube.tv"
    private val nf = "com.netflix.ninja"
    private val home = "dev.tvremote.home"
    private val maps = "com.example.maps"                              // a real app that is not heavy
    private val homes = setOf("dev.tvremote.home", "com.google.android.tvlauncher")
    private val ime = setOf("com.google.android.inputmethod.latin")
    private val s = 1000L

    /** A run of readings: Tracker plus the lists as HeavyMode feeds them. */
    private class Run(val keep: Set<String> = emptySet(), val homes: Set<String>, val ime: Set<String>) {
        val t = Heavy.Tracker()
        fun look(front: String, now: Long, on: Boolean = true) =
            t.look(front, now, on, { Heavy.isHeavy(it, keep) }, { Heavy.transient(it, homes, ime) })
    }
    private fun run(keep: Set<String> = emptySet()) = Run(keep, homes, ime)

    @Test fun leavingForAnotherAppStopsAfterFifteenSeconds() {
        val r = run()
        assertEquals(emptyList<String>(), r.look(yt, 0))
        assertEquals(emptyList<String>(), r.look(maps, 3 * s))           // the wait begins here
        assertEquals(emptyList<String>(), r.look(maps, 17 * s))          // 14 s
        assertEquals(listOf(yt), r.look(maps, 18 * s))                   // 15 s
    }

    @Test fun quickBackAndForthStopsNothing() {
        val r = run()
        r.look(yt, 0); r.look(maps, 3 * s)
        assertEquals(emptyList<String>(), r.look(yt, 10 * s))             // back within the wait
        assertEquals(emptyList<String>(), r.look(yt, 40 * s))
        assertTrue(r.t.waiting().isEmpty())
    }

    @Test fun waitBeginsAgainAfterComingBack() {
        val r = run()
        r.look(yt, 0); r.look(maps, 3 * s); r.look(yt, 10 * s)
        assertEquals(emptyList<String>(), r.look(maps, 12 * s))
        assertEquals(emptyList<String>(), r.look(maps, 26 * s))           // 14 s from the second leaving, not 23 from the first
        assertEquals(listOf(yt), r.look(maps, 27 * s))
    }

    @Test fun screensaverIsNotAnotherApp() {
        val r = run()
        r.look(yt, 0)
        for (dream in listOf("com.android.dreams.basic", "com.google.android.apps.tv.dreamx", "com.google.android.backdrop", "com.example.screensaver.dream"))
            assertEquals(dream, emptyList<String>(), r.look(dream, 100 * s))
        assertTrue(r.t.waiting().isEmpty())
    }

    @Test fun homeKeyboardSettingsVoiceAndSetupStartNoWait() {
        val r = run()
        r.look(yt, 0)
        for (p in listOf(home, "com.google.android.tvlauncher", "com.google.android.inputmethod.latin", "com.android.tv.settings", "com.android.systemui",
            "com.google.android.katniss", "com.google.android.permissioncontroller", "dev.tvremote.setup", "android", ""))
            assertEquals(p, emptyList<String>(), r.look(p, 100 * s))
        assertTrue(r.t.waiting().isEmpty())
    }

    @Test fun throughTheLauncherToAnotherApp() {
        val r = run()
        r.look(yt, 0); r.look(home, 5 * s); r.look(home, 600 * s)           // an hour on the home screen: still nothing
        assertEquals(emptyList<String>(), r.look(maps, 700 * s))
        assertEquals(listOf(yt), r.look(maps, 715 * s))
    }

    @Test fun launcherAndBackToTheSameAppStopsNothing() {
        val r = run()
        r.look(yt, 0); r.look(home, 5 * s)
        assertEquals(emptyList<String>(), r.look(yt, 30 * s))
        assertEquals(emptyList<String>(), r.look(yt, 100 * s))
    }

    @Test fun oneHeavyAppForAnother() {
        val r = run()
        r.look(yt, 0)
        assertEquals(emptyList<String>(), r.look(nf, 5 * s))               // YouTube is left, Netflix is on screen
        assertEquals(listOf(yt), r.look(nf, 20 * s))                       // Netflix itself is never due while in front
        r.t.done(yt)
        assertEquals(emptyList<String>(), r.look(maps, 21 * s))            // now Netflix is left too
        assertEquals(listOf(nf), r.look(maps, 40 * s))
    }

    @Test fun onlyOnceOnceDone() {
        val r = run()
        r.look(yt, 0); r.look(maps, 3 * s)
        assertEquals(listOf(yt), r.look(maps, 20 * s))
        r.t.done(yt)                                                       // closed
        assertEquals(emptyList<String>(), r.look(maps, 30 * s))
        assertEquals(emptyList<String>(), r.look(maps, 300 * s))
    }

    @Test fun notDoneIsReportedAgainOnTheNextLook() {
        val r = run()
        r.look(yt, 0); r.look(maps, 3 * s)
        assertEquals(listOf(yt), r.look(maps, 20 * s))
        assertEquals(listOf(yt), r.look(maps, 23 * s))                     // the caller has not answered yet: its job to answer
    }

    @Test fun soundPlayingAsksAgainAMinuteLater() {
        val r = run()
        r.look(yt, 0); r.look(maps, 3 * s)
        assertEquals(listOf(yt), r.look(maps, 20 * s))
        r.t.later(yt, 20 * s)
        assertEquals(emptyList<String>(), r.look(maps, 79 * s))
        assertEquals(listOf(yt), r.look(maps, 80 * s))
    }

    @Test fun keptAppIsNeverFollowed() {
        val r = run(setOf(yt))
        r.look(yt, 0)
        assertEquals(emptyList<String>(), r.look(maps, 3 * s))
        assertEquals(emptyList<String>(), r.look(maps, 100 * s))
    }

    @Test fun keptAppDoesNotKeepAnotherHeavyAppAlive() {
        val r = run(setOf(yt))
        r.look(nf, 0); r.look(yt, 3 * s)                                    // the kept one is a real app for the others
        assertEquals(listOf(nf), r.look(yt, 20 * s))
    }

    @Test fun screenOffDropsTheWaitAndNothingFiresWhileOff() {
        val r = run()
        r.look(yt, 0); r.look(maps, 3 * s)
        assertEquals(emptyList<String>(), r.look("", 10 * s, on = false))
        assertEquals(emptyList<String>(), r.look(maps, 100 * s, on = false))
        assertEquals(emptyList<String>(), r.look(maps, 101 * s))            // on again: the wait begins afresh
        assertEquals(emptyList<String>(), r.look(maps, 115 * s))
        assertEquals(listOf(yt), r.look(maps, 116 * s))
    }

    @Test fun unknownFrontChangesNothing() {
        val r = run()
        r.look(yt, 0); r.look(maps, 3 * s)
        assertEquals(emptyList<String>(), r.look("", 10 * s))
        assertEquals(listOf(yt), r.look("", 20 * s))                        // it was left for a real app before the gap
    }

    @Test fun resetForgetsEverything() {
        val r = run()
        r.look(yt, 0); r.look(maps, 3 * s)
        r.t.reset()
        assertEquals(emptyList<String>(), r.look(maps, 100 * s))
    }

    @Test fun cancelKeepsFollowingForTheNextTime() {
        val r = run()
        r.look(yt, 0); r.look(maps, 3 * s)
        r.t.cancel(yt)
        assertEquals(emptyList<String>(), r.look(maps, 100 * s))            // the wait is gone
        r.look(yt, 110 * s); r.look(maps, 112 * s)                          // but it is followed again once it is in front
        assertEquals(listOf(yt), r.look(maps, 130 * s))
    }

    @Test fun anAppNotOnTheListIsNeverHeavy() {
        assertFalse(Heavy.isHeavy(maps, emptySet()))
        assertFalse(Heavy.isHeavy("com.spotify.tv.android", emptySet()))
        assertTrue(Heavy.isHeavy(yt, emptySet()))
        assertFalse(Heavy.isHeavy(yt, setOf(yt)))
    }

    @Test fun musicAppsAreNeverOnTheList() {
        for (m in Heavy.MUSIC) assertFalse(m, Heavy.isHeavy(m, emptySet()))
        for (p in listOf("com.spotify.tv.android", "com.google.android.apps.youtube.music", "com.zing.mp3", "ht.nct", "com.google.android.apps.podcasts"))
            assertFalse(p, Heavy.LIST.containsKey(p))
        assertTrue(Heavy.LIST.keys.none { it in Heavy.MUSIC })
    }

    // ---- the last look before `am force-stop`
    private fun verdict(pkg: String = yt, front: String = maps, keep: Set<String> = emptySet(), mustKeep: (String) -> Boolean = { false },
                        safe: Set<String> = emptySet(), playing: Set<String> = emptySet(), fg: Set<String> = emptySet()) =
        Heavy.verdict(pkg, front, keep, mustKeep, safe, playing, fg)

    @Test fun anIdleHeavyAppIsStopped() = assertEquals(Heavy.Verdict.STOP, verdict())

    @Test fun soundPlayingIsLeftAlone() {
        assertEquals(Heavy.Verdict.LATER, verdict(playing = setOf(yt)))
        assertEquals(Heavy.Verdict.STOP, verdict(playing = setOf("com.spotify.tv.android")))   // another app's sound is not its
    }

    @Test fun mediaForegroundServiceIsLeftAlone() = assertEquals(Heavy.Verdict.LATER, verdict(fg = setOf(yt)))

    @Test fun keptAppIsNeverStopped() = assertEquals(Heavy.Verdict.NEVER, verdict(keep = setOf(yt)))

    @Test fun theAppOnScreenIsNeverStopped() = assertEquals(Heavy.Verdict.NEVER, verdict(front = yt))

    @Test fun protectedAppsAreNeverStopped() {
        assertEquals(Heavy.Verdict.NEVER, verdict(mustKeep = { it == yt }))
        assertEquals(Heavy.Verdict.NEVER, verdict(safe = setOf(yt)))
        assertEquals(Heavy.Verdict.NEVER, verdict(pkg = "dev.tvremote.setup", mustKeep = { Setup.mustKeep(it) }))
        assertEquals(Heavy.Verdict.NEVER, verdict(pkg = "dev.tvremote.home", mustKeep = { Setup.mustKeep(it) }))
        assertEquals(Heavy.Verdict.NEVER, verdict(pkg = "dev.tvremote.channels", mustKeep = { Setup.mustKeep(it) }))
    }

    @Test fun musicAndUnlistedAppsAreNeverStopped() {
        assertEquals(Heavy.Verdict.NEVER, verdict(pkg = "com.spotify.tv.android"))
        assertEquals(Heavy.Verdict.NEVER, verdict(pkg = "com.google.android.apps.youtube.music"))
        assertEquals(Heavy.Verdict.NEVER, verdict(pkg = maps))
    }

    @Test fun neverBeatsLaterWhenBothApply() = assertEquals(Heavy.Verdict.NEVER, verdict(keep = setOf(yt), playing = setOf(yt)))

    // ---- the whole decision, as HeavyMode makes it: a run of readings, then the verdict at the end
    private fun decide(readings: List<Pair<Long, String>>, keep: Set<String> = emptySet(), playing: Set<String> = emptySet(),
                       mustKeep: (String) -> Boolean = { false }): Heavy.Verdict? {
        val r = run(keep)
        var due = emptyList<String>()
        var last = ""
        for ((t, f) in readings) { due = r.look(f, t); last = f }
        val a = due.firstOrNull() ?: return null
        return Heavy.verdict(a, last, keep, mustKeep, emptySet(), playing, emptySet())
    }

    @Test fun wholeYouTubeToOtherApp() =
        assertEquals(Heavy.Verdict.STOP, decide(listOf(0L to yt, 3 * s to maps, 20 * s to maps)))

    @Test fun wholeQuickRoundTrip() = assertEquals(null, decide(listOf(0L to yt, 3 * s to maps, 9 * s to yt, 30 * s to yt)))

    @Test fun wholeIntoTheDream() = assertEquals(null, decide(listOf(0L to yt, 3 * s to "com.android.dreams.basic", 60 * s to "com.android.dreams.basic")))

    @Test fun wholeViaLauncher() = assertEquals(Heavy.Verdict.STOP, decide(listOf(0L to yt, 3 * s to home, 30 * s to maps, 50 * s to maps)))

    @Test fun wholeMusicAppNeverDue() = assertEquals(null, decide(listOf(0L to "com.spotify.tv.android", 3 * s to maps, 60 * s to maps)))

    @Test fun wholeKeepListed() = assertEquals(null, decide(listOf(0L to yt, 3 * s to maps, 60 * s to maps), keep = setOf(yt)))

    @Test fun wholeMustKeep() =
        assertEquals(Heavy.Verdict.NEVER, decide(listOf(0L to yt, 3 * s to maps, 60 * s to maps), mustKeep = { it == yt }))

    @Test fun wholePlayingAudioInTheBackground() =
        assertEquals(Heavy.Verdict.LATER, decide(listOf(0L to yt, 3 * s to maps, 60 * s to maps), playing = setOf(yt)))

    // ---- reading the TV's words
    @Test fun startedPlayersByUid() {
        val audio = """
            AudioPlaybackConfiguration piid:63 deviceId:0 type:android.media.SoundPool u/pid:1000/514 state:idle attr:AudioAttributes: usage=USAGE_ASSISTANCE
            AudioPlaybackConfiguration piid:70 deviceId:0 type:AAudio u/pid:10132/3883 state:started attr:AudioAttributes: usage=USAGE_MEDIA
            AudioPlaybackConfiguration piid:71 deviceId:0 type:AAudio u/pid:10133/3999 state:paused attr:AudioAttributes: usage=USAGE_MEDIA
        """.trimIndent()
        assertEquals(setOf(10132), Heavy.startedUids(audio))
        assertEquals(emptySet<Int>(), Heavy.startedUids(""))
    }

    @Test fun playingSessionsAndroid14() {
        val media = """
            starboard_media com.google.android.youtube.tv/starboard_media (userId=0)
              ownerPid=21195, ownerUid=10089, userId=0
              package=com.google.android.youtube.tv
              state=PlaybackState {state=PLAYING(3), position=0, buffered position=0, speed=1.0, updated=0, actions=0, custom actions=[], active item id=-1, error=null}
            BluetoothMediaBrowserService com.google.android.bluetooth/BluetoothMediaBrowserService (userId=0)
              package=com.google.android.bluetooth
              state=PlaybackState {state=NONE(0), position=0, buffered position=0, speed=0.0, updated=0, actions=0, custom actions=[], active item id=-1, error=null}
            x
              package=com.spotify.tv.android
              state=PlaybackState {state=BUFFERING(6), position=0}
            y
              package=com.netflix.ninja
              state=PlaybackState {state=PAUSED(2), position=0}
        """.trimIndent()
        assertEquals(setOf("com.google.android.youtube.tv", "com.spotify.tv.android"), Heavy.playingSessions(media))
    }

    @Test fun playingSessionsOlderFormat() {
        val media = "  package=a.b\n  state=PlaybackState {state=3, position=0}\n  package=c.d\n  state=PlaybackState {state=2, position=0}\n"
        assertEquals(setOf("a.b"), Heavy.playingSessions(media))
    }

    @Test fun mediaForegroundService() {
        assertTrue(Heavy.foregroundMedia("    isForeground=true foregroundId=1 types=00000002 foregroundNoti=Notification()"))
        assertTrue(Heavy.foregroundMedia("    isForeground=true foregroundId=1 types=0000000a"))
        assertFalse(Heavy.foregroundMedia("    isForeground=true foregroundId=1 types=40000000 foregroundNoti=Notification()"))   // specialUse (TV Setup's)
        assertFalse(Heavy.foregroundMedia("    isForeground=false types=00000002"))
        assertFalse(Heavy.foregroundMedia(""))
        assertTrue(Heavy.foregroundMedia("    isForeground=true foregroundId=1"))                  // before Android 10: no types, any is held
    }

    // ---- the rules
    @Test fun rulesOnAndKeep() {
        val o = JSONObject()
        assertFalse(Heavy.on(o))
        Rules.apply(o, JSONObject().put("do", "heavy").put("on", true), 0L)
        assertTrue(Heavy.on(o))
        Rules.apply(o, JSONObject().put("do", "heavy_keep").put("pkg", yt).put("on", true), 0L)
        Rules.apply(o, JSONObject().put("do", "heavy_keep").put("pkg", nf).put("on", true), 0L)
        Rules.apply(o, JSONObject().put("do", "heavy_keep").put("pkg", yt).put("on", true), 0L)      // twice: once
        assertEquals(setOf(yt, nf), Heavy.keep(o))
        Rules.apply(o, JSONObject().put("do", "heavy_keep").put("pkg", yt).put("on", false), 0L)
        assertEquals(setOf(nf), Heavy.keep(o))
        assertTrue(Heavy.on(o))                                                                       // the keep list does not turn it off
        Rules.apply(o, JSONObject().put("do", "heavy").put("on", false), 0L)
        assertFalse(Heavy.on(o))
        assertEquals(setOf(nf), Heavy.keep(o))
    }

    @Test fun rulesRefuseABadPackageName() {
        val o = JSONObject()
        try { Rules.apply(o, JSONObject().put("do", "heavy_keep").put("pkg", "a b; rm").put("on", true), 0L); org.junit.Assert.fail() }
        catch (e: IllegalArgumentException) { }
    }

    @Test fun clearLeavesTheSwitch() {
        val o = JSONObject("""{"heavy":{"on":true,"keep":["$nf"],"log":[{"pkg":"$yt","label":"YouTube","at":5}]}}""")
        Rules.apply(o, JSONObject().put("do", "heavy_clear"), 0L)
        assertTrue(Heavy.on(o))
        assertEquals(0, o.getJSONObject("heavy").getJSONArray("log").length())
        assertEquals(setOf(nf), Heavy.keep(o))
    }
}
