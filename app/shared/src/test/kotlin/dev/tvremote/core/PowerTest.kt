package dev.tvremote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.tvremote.core.ontv.Power

class PowerTest {
    // what Power.CMD printed on a TCL (Android 11) after the power key, Tailscale killed by TGuard while YouTube played
    private val tcl = """
3600.25 8023.57
now=1792243000
  Runtime uptime (elapsed): +1h0m0s250ms
  Runtime uptime (uptime): +45m0s0ms
#~#
  mWakefulness=Asleep
  mLastWakeTime=1000 (1200000 ms ago)
  mLastSleepTime=2000 (90000 ms ago)
  mLastSleepReason=power_button
Wake Locks: size=1
  PARTIAL_WAKE_LOCK              'tvsetup:awake' ACQ=-1m2s (uid=10090 pid=820)

#~#
secure sleep_timeout=-1
secure attentive_timeout=null
system screen_off_timeout=2147483647
global stay_on_while_plugged_in=0
global hdmi_control_auto_device_off_enabled=0
global wifi_sleep_policy=2
global boot_count=12
secure always_on_vpn_app=com.tailscale.ipn
sys.boot.reason=reboot
persist.tcl.totalstrtimes=2894
#~#
package:/data/app/com.tailscale.ipn-1/base.apk
ts=
setup=820
5: tailscale0    inet 100.80.200.70/32 scope global tailscale0\       valid_lft forever preferred_lft forever
#~#
  1792241851.100  1086  1110 I TGuardService: tguard-kill-lowSwapFree com.tailscale.ipn pid=3739
  1792241851.300   546   600 I ActivityManager: Process com.tailscale.ipn (pid 3739) has died: prcp FGS
  1792241851.310   546   600 I ActivityManager: Process dev.tvremote.setup (pid 3001) has died: fg  SVC
  1792242467.338   546   580 I am_kill : [0,3744,dev.tvremote.setup,0,stop dev.tvremote.setup due to installPackageLI]
#~#
[1792240000,"on"]
[1792241000,"off"]
not json
[1792241851,"vpn_down"]
"""

    @Test fun readsATclAfterThePowerKey() {
        val p = Power.parse(tcl)
        assertEquals("Asleep", p.getString("state"))
        assertEquals(1792243000L, p.getLong("now"))                   // the TV's clock, as its events
        val b = p.getJSONObject("boot")
        assertEquals(3600L, b.getLong("up"))
        assertEquals(900L, b.getLong("deep"))                         // an hour since boot, 45 minutes of it awake
        assertEquals("reboot", b.getString("reason"))
        assertEquals(12L, b.getLong("count"))
        assertFalse(p.getJSONObject("last_sleep").has("ago"))        // counted in uptime, which stood still in deep sleep
        assertEquals("power_button", p.getJSONObject("last_sleep").getString("why"))
        assertFalse(p.has("last_wake"))
        assertEquals(-1L, p.getJSONObject("timeouts").getLong("sleep"))
        assertFalse(p.getJSONObject("timeouts").has("attentive"))     // "null": not set
        assertFalse(p.getBoolean("hdmi_off"))
        assertEquals(2L, p.getLong("wifi_sleep"))
        assertEquals(2894L, p.getJSONObject("deep").getLong("count"))
        assertEquals("TCL", p.getJSONObject("deep").getString("by"))
        assertEquals("tvsetup:awake", p.getJSONArray("locks").getJSONObject(0).getString("tag"))
        val ts = p.getJSONObject("tailscale")
        assertTrue(ts.getBoolean("installed")); assertFalse(ts.getBoolean("running")); assertTrue(ts.getBoolean("always_on"))
        assertTrue(p.getBoolean("setup_running"))
        assertEquals("100.80.200.70", ts.getString("ip"))
        val d = p.getJSONArray("deaths")
        assertEquals(2, d.length())                                   // TGuard's line and Android's are one death; an update is none
        assertEquals("tguard:lowSwapFree", d.getJSONObject(0).getString("why"))
        assertEquals("com.tailscale.ipn", d.getJSONObject(0).getString("pkg"))
        assertEquals("died", d.getJSONObject(1).getString("why"))
        assertEquals(3, p.getJSONArray("events").length())
        assertTrue(p.getBoolean("logged"))
    }

    @Test fun aTvThatNeverSuspendsKeepsItsTimesAndAskedTvSetupForItsLog() {
        val p = Power.parse(tcl.replace("+45m0s0ms", "+1h0m0s250ms").substringBeforeLast("#~#") + "#~#") { "[1,\"on\"]" }
        assertEquals(90L, p.getJSONObject("last_sleep").getLong("ago"))
        assertEquals(1200L, p.getJSONObject("last_wake").getLong("ago"))
        assertEquals(1, p.getJSONArray("events").length())
    }

    @Test fun saysNothingItWasNotTold() {
        val p = Power.parse("")
        assertFalse(p.has("state")); assertFalse(p.has("deep")); assertFalse(p.has("last_sleep"))
        assertFalse(p.getBoolean("logged"))
        assertEquals(0, p.getJSONArray("deaths").length())
    }

    @Test fun durations() {
        assertEquals(93_784_005L, Power.duration("+1d2h3m4s5ms"))
        assertEquals(null, Power.duration("+"))
    }
}
