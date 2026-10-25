package dev.tvremote.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePlanTest {
    private val now = 1_000_000_000_000L
    private val min = 60_000L

    @Test fun appStaysQuietOnlyWhileSetupCanAndTheGraceHolds() {
        assertTrue(UpdatePlan.quiet(true, now - 3600_000L, now))                       // seen an hour ago, TV Setup on
        assertFalse(UpdatePlan.quiet(true, now - UpdatePlan.GRACE, now))               // grace over: asks
        assertFalse(UpdatePlan.quiet(false, now, now))                                 // auto-update off / no ADB
        assertFalse(UpdatePlan.quiet(null, now, now))                                  // old or missing TV Setup: as before
    }

    @Test fun standbyAllowsAnything() {
        assertTrue(UpdatePlan.mayInstall(false, true, now, now, true))
        assertTrue(UpdatePlan.mayInstall(false, false, null, now, true))
    }

    @Test fun awakeNeverOverTheTopApp() {
        assertFalse(UpdatePlan.mayInstall(true, true, 0L, now, true))
        assertFalse(UpdatePlan.mayInstall(true, true, null, now, false))
    }

    @Test fun awakeHomeOnlyNeedsNotBeingOnTop() {
        assertTrue(UpdatePlan.mayInstall(true, false, now, now, false))
    }

    @Test fun awakeChannelsAndGamesWaitForTheyAreUnused() {
        assertFalse(UpdatePlan.mayInstall(true, false, now - 5 * min, now, true))      // left 5 minutes ago
        assertFalse(UpdatePlan.mayInstall(true, false, now - 29 * min, now, true))
        assertTrue(UpdatePlan.mayInstall(true, false, now - 30 * min, now, true))
        assertTrue(UpdatePlan.mayInstall(true, false, 0L, now, true))                  // not used today
        assertFalse(UpdatePlan.mayInstall(true, false, null, now, true))               // no usage access: unknown is not "unused"
    }
}
