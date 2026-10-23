package dev.tvremote.core.ontv

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutTest {
    @Test fun blockListRefusesWhatTheTvOrThisProjectNeeds() {
        val never = listOf(
            "dev.tvremote.setup", "dev.tvremote.home", "dev.tvremote.channels", "dev.tvremote.games", "dev.tvremote.phone",   // TV Setup, TV Home, TV Channels, TV Games, ours
            "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.tvrecommendations",      // launchers
            "com.google.android.gms", "com.google.android.gsf", "com.android.vending", "com.android.systemui",                    // Play services / Store, system
            "com.android.settings", "com.android.tv.settings", "android", "com.android.providers.settings",                      // settings
            "com.example.mylauncher", "com.tailscale.ipn", "com.google.android.tv.remote.service", "com.android.tv.provision")
        for (p in never) assertNotNull(p, Checkout.blocked(p))
        for (bad in listOf("", "x", "com.x;reboot", "../x", "com.x\n")) assertNotNull(bad, Checkout.blocked(bad))
    }

    @Test fun blockListLetsTheGuestAppsThrough() {
        for (p in Checkout.SUGGEST.keys) assertNull(p, Checkout.blocked(p))           // every suggestion can be cleared
        assertNull(Checkout.blocked("com.esaba.downloader"))
    }

    @Test fun cleanKeepsOrderAndDropsBlockedAndRepeated() {
        val got = Checkout.clean(listOf("com.netflix.ninja", "dev.tvremote.setup", " com.netflix.ninja ", "", "com.spotify.tv.android", "com.google.android.gms"))
        assertEquals(listOf("com.netflix.ninja", "com.spotify.tv.android"), got)
    }

    @Test fun preselectIsOnlyWhatIsInstalled() {
        val got = Checkout.preselect(setOf("com.netflix.ninja", "com.google.android.youtube.tv", "com.unknown.app", "dev.tvremote.setup"))
        assertEquals(listOf("com.google.android.youtube.tv", "com.netflix.ninja"), got)
        assertEquals(emptyList<String>(), Checkout.preselect(emptySet()))
    }

    @Test fun configRoundTripAndRepair() {
        val c = Checkout.Config.of(JSONObject("""{"apps":["com.netflix.ninja","dev.tvremote.setup"],"volume":140}"""))
        assertEquals(listOf("com.netflix.ninja"), c.apps)                             // a blocked app in the saved file is dropped on read
        assertEquals(100, c.volume)
        val none = Checkout.Config.of(null)
        assertNull(none.apps)                                                         // never chosen: the preselection decides
        assertEquals(Checkout.VOLUME, none.volume)
        assertEquals(30, Checkout.Config.of(c.json().put("volume", 30)).volume)
    }

    @Test fun volumeIndexFollowsTheTvsRange() {
        assertEquals(4, Checkout.index(25, "[V] volume is 3 in range [0..15]"))      // 25% of 15 = 3.75
        assertEquals(25, Checkout.index(25, "volume is 3 in range [0..100]"))
        assertEquals(4, Checkout.index(25, "no range here"))                          // 15 steps when the TV does not say
        assertEquals(0, Checkout.index(-5, "range [0..15]"))
        assertEquals(15, Checkout.index(500, "range [0..15]"))
    }

    @Test fun planOrderWithCurrentTvSetup() {
        val p = Checkout.plan(setOf("com.netflix.ninja", "com.google.android.youtube.tv", "dev.tvremote.setup"), "0.19.1",
            listOf("com.google.android.youtube.tv", "com.spotify.tv.android", "com.netflix.ninja", "dev.tvremote.setup"))
        assertEquals(listOf("clear:com.google.android.youtube.tv", "clear:com.netflix.ninja", "volume", "web", "home"), p.map { it.toString() })   // Spotify is not on this TV; TV Setup is never listed
    }

    @Test fun planWithOldOrNoTvSetupSkipsOnlyTheWebStep() {
        val old = Checkout.plan(setOf("com.netflix.ninja"), "0.19.0", listOf("com.netflix.ninja"))
        assertEquals(listOf("clear:com.netflix.ninja", "volume", "web (skip old)", "home"), old.map { it.toString() })
        val none = Checkout.plan(setOf("com.netflix.ninja"), "", listOf("com.netflix.ninja"))
        assertEquals(listOf("clear:com.netflix.ninja", "volume", "web (skip missing)", "home"), none.map { it.toString() })
    }

    @Test fun planWithNothingToClearStillDoesTheRest() {
        assertEquals(listOf("volume", "web", "home"), Checkout.plan(emptySet(), "0.19.1", listOf("com.netflix.ninja")).map { it.toString() })
    }

    /** A TV that answers the commands run() sends; [cmds] keeps them in order. */
    private class FakeTv(val setup: String, val installed: Set<String>, val forgot: Int = 2, val clearFails: Set<String> = emptySet()) {
        val cmds = ArrayList<String>()
        var vol = 3
        fun sh(c: String): String {
            cmds += c
            return when {
                c.startsWith("pm list packages") -> installed.joinToString("\n") { "package:$it" }
                c.contains("versionName=") -> if (setup.isEmpty()) "" else "    versionName=$setup"
                c.contains("get-role-holders") -> "dev.tvremote.home/.HomeActivity"
                c.startsWith("pm clear ") -> if (c.substringAfter("pm clear ").substringBefore(" ") in clearFails) "Failed" else "Success"
                c.contains("media_session volume --stream 3 --set") -> { vol = Regex("--set (\\d+)").find(c)!!.groupValues[1].toInt(); "" }
                c.contains("media_session volume --stream 3 --get") -> "volume is $vol in range [0..15]"
                c.contains("RulesReceiver") -> "data=\"" + java.util.Base64.getEncoder().encodeToString(JSONObject().put("forgot", forgot).toString().toByteArray()) + "\""
                else -> ""
            }
        }
        fun ran(prefix: String) = cmds.indexOfFirst { it.startsWith(prefix) || it.contains(prefix) }
    }

    @Test fun runDoesEverythingInOrder() {
        val tv = FakeTv("0.19.1", setOf("com.netflix.ninja", "com.google.android.youtube.tv", "dev.tvremote.setup", "dev.tvremote.home"))
        val said = ArrayList<String>()
        val r = Checkout.run(tv::sh, Checkout.Config(listOf("com.google.android.youtube.tv", "com.netflix.ninja", "dev.tvremote.home"), 25)) { said += it }
        assertEquals(2, r.cleared); assertTrue(r.failed.isEmpty()); assertEquals("ok", r.web); assertEquals(2, r.forgot); assertTrue(r.home)
        assertEquals(4, tv.vol)                                                       // 25% of 0..15
        val clear1 = tv.ran("pm clear com.google.android.youtube.tv"); val clear2 = tv.ran("pm clear com.netflix.ninja")
        val vol = tv.ran("cmd media_session volume --stream 3 --set"); val web = tv.ran("RulesReceiver"); val home = tv.ran("android.intent.category.HOME 2>&1")
        assertTrue("$clear1 $clear2 $vol $web $home", clear1 in 0 until clear2 && clear2 < vol && vol < web && web < home)
        assertEquals(-1, tv.ran("pm clear dev.tvremote"))                             // our own apps are never cleared, even asked for
        assertEquals(listOf("checkout clear 1/2", "checkout clear 2/2", "checkout volume", "checkout web", "checkout home"), said)
        assertEquals("checkout:c=2,f=0,v=25,w=ok,n=2,h=1,a=com.google.android.youtube.tv+com.netflix.ninja", r.token())
        assertEquals(listOf("com.google.android.youtube.tv", "com.netflix.ninja"), Checkout.Result.parse(r.token())!!.apps)
    }

    @Test fun runWithOldTvSetupSaysSoAndNeverCallsIt() {
        val tv = FakeTv("0.19.0", setOf("com.netflix.ninja"))
        val r = Checkout.run(tv::sh, Checkout.Config(null, 40))                       // never chosen: Netflix is preselected
        assertEquals(1, r.cleared); assertEquals("old", r.web); assertTrue(r.home)
        assertEquals(-1, tv.ran("RulesReceiver"))
        assertEquals(6, tv.vol)
        assertEquals("checkout:c=1,f=0,v=40,w=old,n=0,h=1,a=com.netflix.ninja", r.token())
    }

    @Test fun runWithoutTvSetup() {
        val r = Checkout.run(FakeTv("", setOf("com.netflix.ninja"))::sh, Checkout.Config(listOf("com.netflix.ninja"), 25))
        assertEquals("missing", r.web)
    }

    @Test fun aFailedClearIsCountedAndTheRestGoesOn() {
        val tv = FakeTv("0.19.1", setOf("com.netflix.ninja", "com.spotify.tv.android"), clearFails = setOf("com.netflix.ninja"))
        val r = Checkout.run(tv::sh, Checkout.Config(listOf("com.netflix.ninja", "com.spotify.tv.android"), 25))
        assertEquals(1, r.cleared); assertEquals(listOf("com.netflix.ninja"), r.failed); assertTrue(r.home); assertEquals("ok", r.web)
    }

    @Test fun anAppThatIsTheHomeScreenIsNotCleared() {
        val tv = FakeTv("0.19.1", setOf("com.netflix.ninja", "com.example.homeapp"))
        val sh = { c: String -> if (c.contains("get-role-holders")) "com.example.homeapp/.Main" else tv.sh(c) }
        val r = Checkout.run(sh, Checkout.Config(listOf("com.example.homeapp", "com.netflix.ninja"), 25))
        assertEquals(1, r.cleared); assertEquals(-1, tv.ran("pm clear com.example.homeapp"))
    }

    @Test fun tokenRoundTrip() {
        val t = "checkout:c=3,f=1,v=25,w=old,n=0,h=1"
        val r = Checkout.Result.parse(t)!!
        assertEquals(3, r.cleared); assertEquals(1, r.failed.size); assertEquals(25, r.volume); assertEquals("old", r.web); assertTrue(r.home)
        assertEquals(t, r.token())
        assertNull(Checkout.Result.parse("uninstalled 2"))
        assertFalse(Checkout.Result.parse("checkout:c=0,f=0,v=0,w=ok,n=0,h=0")!!.home)
    }
}
