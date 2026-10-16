package dev.tvremote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteTest {
    private val code = "0123456789abcdef0123456789abcdef"

    @Test fun link() {
        // home Wi-Fi on both sides: the TV's own address, nothing to say
        assertEquals("http://192.168.1.5:8765/#invite=$code", Invite.link("192.168.1.5", code, "192.168.1.5").getString("url"))
        assertEquals("", Invite.link("192.168.1.5", code, "192.168.1.5").getString("warn"))
        // this device is away: the link still points at the TV's Wi-Fi address, and says the scanner must be there
        assertEquals("http://192.168.1.5:8765/#invite=$code", Invite.link("192.168.1.5", code, "100.64.0.9").getString("url"))
        assertEquals("tailscale", Invite.link("192.168.1.5", code, "100.64.0.9").getString("warn"))
        // the TV has no Wi-Fi address: only the Tailscale one, and the scanner needs Tailscale
        assertEquals("http://100.64.0.9:8765/#invite=$code", Invite.link("", code, "100.64.0.9").getString("url"))
        assertEquals("tailscale_only", Invite.link("", code, "100.64.0.9").getString("warn"))
    }

    @Test fun shareWithoutSetup() {
        assertEquals("missing", Invite.share({ "" }, "10.0.2.2", "remote").getString("setup"))
    }

    @Test fun shareAsksForTheScope() {
        val sent = ArrayList<String>()
        val sh = { c: String -> sent.add(c); if (c.contains("versionName")) "versionName=0.9.9" else if (c.contains("TrustReceiver")) "Broadcast completed: result=-1, data=\"" + "a".repeat(32) + " 192.168.1.5\"" else "" }
        assertEquals("http://192.168.1.5:8765/#invite=" + "a".repeat(32), Invite.share(sh, "10.0.2.2", "view").getString("url"))
        assertTrue(sent.any { it.contains("--es scope view") })
        Invite.share(sh, "10.0.2.2", "root; reboot")                 // never into the shell as given
        assertTrue(sent.last { it.contains("TrustReceiver") }.contains("--es scope remote "))
    }
}
