package dev.tvremote.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Fleet reads what app/web/fleet.py reads, for every case in the table both test suites use. */
class FleetTest {
    private val cases = JSONObject(File("../web/tests/fleet_cases.json").readText())

    @Test fun command() = assertEquals(cases.getString("INFO"), Fleet.INFO)

    @Test fun parse() {
        val all = cases.getJSONArray("cases")
        for (i in 0 until all.length()) {
            val c = all.getJSONObject(i)
            val want = c.getJSONObject("info")
            val got = Fleet.parse(c.getString("out"))
            val apps = want.getJSONArray("apps")
            assertEquals(Fleet.Info(want.getString("model"), want.getString("android"), want.getInt("sdk"), want.getLong("disk_total"),
                want.getLong("disk_free"), want.getLong("ram"), List(apps.length()) { apps.getString(it) }, want.getString("setup")), got)
        }
    }

    @Test fun refused() {
        assertNull(Fleet.canUninstall("com.esaba.downloader"))
        for (bad in listOf("", "com.x;reboot", "../x", "com.x\n", "dev.tvremote.setup")) assertNotNull(bad, Fleet.canUninstall(bad))
    }
}
