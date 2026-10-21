package dev.tvremote.games

import dev.tvremote.games.LobbyRule.P
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbyRuleTest {
    @Test fun nobodyInNeverStarts() { assertFalse(LobbyRule.allReady(emptyList())) }

    @Test fun oneReadyPhoneStarts() { assertTrue(LobbyRule.allReady(listOf(P(1, true, true)))) }

    @Test fun oneNotReadyHoldsEveryoneUp() {
        assertFalse(LobbyRule.allReady(listOf(P(1, true, true), P(2, true, true), P(3, true, false))))
        assertTrue(LobbyRule.allReady(listOf(P(1, true, true), P(2, true, true), P(3, true, true))))
    }

    @Test fun physicalPadsNeitherHoldUpNorStartAlone() {
        assertFalse(LobbyRule.allReady(listOf(P(1, false, true))))
        assertFalse(LobbyRule.allReady(listOf(P(1, false, true), P(2, true, false))))
        assertTrue(LobbyRule.allReady(listOf(P(1, false, true), P(2, true, true))))
    }

    @Test fun waitsForTheMinimumNumberOfPlayers() {
        assertFalse(LobbyRule.allReady(listOf(P(1, true, true)), 2))
        assertTrue(LobbyRule.allReady(listOf(P(1, true, true), P(2, true, true)), 2))
        assertTrue(LobbyRule.tooFew(listOf(P(1, true, false)), 2))
    }
}
