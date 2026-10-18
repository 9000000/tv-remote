package dev.tvremote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import dev.tvremote.core.ontv.SetupFiles
import dev.tvremote.core.ontv.SetupNeed
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class SetupFilesTest {
    private val denied = "tail: /sdcard/Android/data/dev.tvremote.setup/files/stats/x: Permission denied\n"   // a TCL on Android 11
    private fun gz(s: String): String {
        val b = ByteArrayOutputStream(); GZIPOutputStream(b).use { it.write(s.toByteArray()) }; return B64.encode(b.toByteArray())
    }
    private fun answer(data: String) = "Broadcasting: Intent { }\nBroadcast completed: result=-1, data=\"$data\"\n"

    @Test fun theShellReadsIt() {
        assertEquals("[1]\n[2]\n", SetupFiles.read({ "[1]\n[2]\n" }, "stats/x", 10))
    }

    @Test fun noFileYet() {
        assertEquals("", SetupFiles.read({ "tail: /sdcard/…/stats/x: No such file or directory\n" }, "stats/x", 10))
    }

    @Test fun deniedTvSetupHandsItOver() {
        val asked = mutableListOf<String>()
        val got = SetupFiles.read({ asked += it; if (it.startsWith("tail")) denied else answer(gz("[1]\n[2]")) }, "stats/x", 10)
        assertEquals("[1]\n[2]", got)
        assertTrue(asked[1].contains("--es get file --es name stats/x --ei lines 10"))
    }

    @Test fun deniedAndTvSetupHasNone() {
        assertEquals("", SetupFiles.read({ if (it.startsWith("tail")) denied else answer("") }, "stats/x", 10))
    }

    @Test fun anOlderTvSetupStillGivesPower() {
        val power = B64.encode("{\"power\":\"[1,\\\"on\\\"]\"}".toByteArray())
        val got = SetupFiles.read({ when {
            it.startsWith("tail") -> denied
            it.contains("get file") -> "Broadcast completed: result=0\n"           // no such extra: no data
            else -> answer(power)
        } }, "stats/power.jsonl", 10)
        assertEquals("[1,\"on\"]", got)
    }

    @Test fun tooOldAsksForAnUpdate() {
        try {
            SetupFiles.read({ when {
                it.startsWith("tail") -> denied
                it.startsWith("dumpsys") -> "    versionName=0.8.0\n"
                else -> "Broadcast completed: result=0\n"
            } }, "stats/samples.jsonl", 10)
            fail()
        } catch (e: SetupNeed.Missing) { assertEquals("setup:old:files:0.8.1:0.8.0", e.message) }
    }

    @Test fun onlyItsOwnFolder() {
        for (bad in listOf("../x", "/etc/x", "stats/../../x", "a b", "x;id")) try { SetupFiles.read({ "" }, bad, 1); fail(bad) } catch (e: IllegalArgumentException) { }
    }
}
