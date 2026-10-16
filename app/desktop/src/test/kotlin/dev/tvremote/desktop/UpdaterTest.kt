package dev.tvremote.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun runFromTheRepoIsNotPackaged() {                       // no res folder: git pull is the update, nothing is swapped
        val u = Updater("0.4.1", null) { }
        val j = u.json()
        assertFalse(j.getBoolean("packaged"))
        assertFalse(j.getBoolean("auto"))
        assertEquals("0.4.1", j.getString("current"))
        assertEquals("idle", j.getString("state"))
        u.close()
    }

    @Test fun archiveNameFitsTheSystem() {
        val u = Updater("0.4.1", null) { }
        assertTrue(u.asset, Regex("TV-Remote-(mac\\.zip|windows-(x64|arm64)\\.zip|linux-(x64|arm64)\\.tar\\.gz)").matches(u.asset))
        assertTrue(u.json().getString("url").endsWith("/releases/latest/download/" + u.asset))
        u.close()
    }

    @Test fun installWithoutAnUpdateIsRefused() {
        val u = Updater("0.4.1", null) { }
        try { u.install(); throw AssertionError("should refuse") } catch (e: IllegalArgumentException) { }
        u.close()
    }
}
