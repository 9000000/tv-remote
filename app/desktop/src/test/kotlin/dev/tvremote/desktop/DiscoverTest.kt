package dev.tvremote.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiscoverTest {
    @get:Rule val dir = TemporaryFolder()

    private fun cli(body: String): String {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"))
        val f = dir.newFile("tailscale"); f.writeText("#!/bin/sh\n$body\n"); f.setExecutable(true)
        return f.path
    }

    /** A tailnet of many machines prints more than a pipe holds (64 KB): the CLI must not be left blocked in write() until the wait ends. */
    @Test fun bigStatusStillGivesPeers() {
        val data = dir.newFile("big.json")
        val peers = (1..2000).joinToString(",") { """"k$it":{"HostName":"pad$it-${"x".repeat(100)}","OS":"linux","Online":false,"TailscaleIPs":["100.64.${it / 250}.${it % 250}"]}""" }
        data.writeText("""{"Peer":{$peers,"tv":{"HostName":"tv","OS":"android","Online":true,"TailscaleIPs":["100.64.9.9"],"CurAddr":""}}}""")
        assertTrue(data.length() > 256 * 1024)
        val got = Discover.tailscalePeers(cli("cat '${data.path}'"), waitSeconds = 5)
        assertEquals(setOf("100.64.9.9"), got.keys)
    }

    @Test fun stuckCliGivesNothingAfterTheWait() {
        val t = System.nanoTime()
        assertTrue(Discover.tailscalePeers(cli("sleep 30"), waitSeconds = 1).isEmpty())
        assertTrue((System.nanoTime() - t) / 1_000_000 < 10_000)
    }
}
