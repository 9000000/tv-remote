package dev.tvremote.setup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Who may read the stats switch (HomeInfo "census"): our own apps by their signing key, nobody else. */
class CensusTrustTest {
    private val home = HomeInfo.CENSUS_APPS.getValue("dev.tvremote.home")
    @Test fun ourAppsByTheirKey() {
        assertTrue(HomeInfo.censusOk("dev.tvremote.home", listOf(home), emptyList()))
        assertTrue(HomeInfo.censusOk("dev.tvremote.channels", listOf(HomeInfo.CENSUS_APPS.getValue("dev.tvremote.channels")), emptyList()))
        assertTrue(HomeInfo.censusOk("dev.tvremote.games", listOf(HomeInfo.CENSUS_APPS.getValue("dev.tvremote.games")), emptyList()))
        assertTrue(HomeInfo.censusOk("dev.tvremote.apps", listOf(HomeInfo.CENSUS_APPS.getValue("dev.tvremote.apps")), emptyList()))
        assertTrue(HomeInfo.censusOk("dev.tvremote.games", listOf("debugkey"), listOf("debugkey")))   // debug builds share one key
    }
    @Test fun nobodyElse() {
        assertFalse(HomeInfo.censusOk("dev.tvremote.home", listOf("otherkey"), listOf("mine")))        // the right name, another key
        assertFalse(HomeInfo.censusOk("com.evil.app", listOf(home), listOf(home)))                      // not one of ours
        assertFalse(HomeInfo.censusOk("dev.tvremote.games", emptyList(), emptyList()))
    }
}
