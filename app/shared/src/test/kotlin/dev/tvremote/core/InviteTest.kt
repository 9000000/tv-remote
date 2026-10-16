package dev.tvremote.core

import org.junit.Assert.assertEquals
import org.junit.Test

class InviteTest {
    private val code = "0123456789abcdef0123456789abcdef"

    @Test fun link() {
        // home Wi-Fi on both sides: the TV's own address, nothing to say
        assertEquals("http://192.168.1.5:8765/#invite=$code", Invite.link("192.168.1.5", code, "192.168.1.5").getString("url"))
        assertEquals("", Invite.link("192.168.1.5", code, "192.168.1.5").getString("warn"))
        // this device is away: the link still points at the TV's Wi-Fi address, and says the scanner must be there
        assertEquals("http://192.168.1.5:8765/#invite=$code", Invite.link("192.168.1.5", code, "100.114.201.16").getString("url"))
        assertEquals("tailscale", Invite.link("192.168.1.5", code, "100.114.201.16").getString("warn"))
        // the TV has no Wi-Fi address: only the Tailscale one, and the scanner needs Tailscale
        assertEquals("http://100.114.201.16:8765/#invite=$code", Invite.link("", code, "100.114.201.16").getString("url"))
        assertEquals("tailscale_only", Invite.link("", code, "100.114.201.16").getString("warn"))
    }

    @Test fun shareWithoutSetup() {
        assertEquals("missing", Invite.share({ "" }, "10.0.2.2").getString("setup"))
    }
}
