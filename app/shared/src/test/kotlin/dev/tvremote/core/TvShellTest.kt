package dev.tvremote.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** TvShell gives what app/web/tvshell.py gives, for every case in the table both test suites read. */
class TvShellTest {
    private val cases = JSONObject(File("../web/tests/tvshell_cases.json").readText())

    @Test fun constants() {
        val c = cases.getJSONObject("consts")
        assertEquals(c.getString("CLEAN"), TvShell.CLEAN)
        assertEquals(c.getString("REBOOT_PREP"), TvShell.REBOOT_PREP)
        assertEquals(c.getString("TVSET_READ"), TvShell.TVSET_READ)
    }

    @Test fun calls() {
        val calls = cases.getJSONArray("calls")
        for (i in 0 until calls.length()) {
            val o = calls.getJSONObject(i)
            val a = o.getJSONArray("call")
            val got = try {
                when (a.getString(0)) {
                    "backup" -> TvShell.backup(a.getString(1))
                    "restore" -> TvShell.restore(a.getString(1))
                    "log" -> TvShell.log(a.getString(1), a.getString(2))
                    else -> TvShell.tvset(a.getString(1), a.get(2).toString())
                }
            } catch (e: IllegalArgumentException) { null }
            assertEquals(a.toString(), if (o.isNull("out")) null else o.getString("out"), got)
        }
    }
}
