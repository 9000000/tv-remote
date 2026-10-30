package dev.tvremote.core

import dev.tvremote.core.ontv.PlayProtect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class PlayProtectTest {
    private val refused = "Failure [INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed for file:///data/app/vmdl48284915.tmp]"

    @Test fun aFileRefusedOnceIsNotTriedAgainButTheNextBuildIs() {
        val dir = Files.createTempDirectory("pp").toFile()
        PlayProtect.check(dir, "aaa")                                 // nothing kept: goes on
        PlayProtect.check(dir, "aaa", "Success")
        assertFalse(PlayProtect.blocked(dir, "aaa"))
        PlayProtect.check(dir, "aaa", refused)
        PlayProtect.check(dir, "aaa", refused)                        // kept once
        assertEquals(1, File(dir, "play-protect-blocked.txt").readLines().size)
        try { PlayProtect.check(dir, "aaa"); fail() } catch (e: IOException) { assertTrue(e.message!!.contains(PlayProtect.REFUSED)) }
        PlayProtect.check(dir, "bbb")                                 // the next release's file
        PlayProtect.check(dir, "bbb", "Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]")
        assertFalse(PlayProtect.blocked(dir, "bbb"))                  // only Play Protect's refusal is kept
    }

    @Test fun noFolderKeepsNothing() {
        PlayProtect.check(null, "aaa", refused)
        PlayProtect.check(null, "aaa")
        assertFalse(PlayProtect.blocked(null, "aaa"))
    }
}
