package dev.tvremote.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dumps below are written from the shape of AOSP's TvInputManagerService / TvInputHardwareManager / HdmiControlService dumps
 * and the vendors' input ids; no real TV was at hand, so they show what [Inputs.parse] accepts, not a recording.
 */
class InputsTest {
    private val sep = "#--tvr-inputs--"

    /** Android TV 11, Sony-like: 3 HDMI (one with a PlayStation, CEC names), composite, a tuner. */
    private val sony = """
TV Input Manager State:
  User Ids:
    0
  User Id: 0
    inputMap: inputId -> TvInputState
      com.sony.dtv.tvinput.tuner/.TunerInputService/HW0: info: TvInputInfo{id=com.sony.dtv.tvinput.tuner/.TunerInputService/HW0, pkg=com.sony.dtv.tvinput.tuner, service=com.sony.dtv.tvinput.tuner.TunerInputService}; state: 0
      com.sony.dtv.tvinput.hdmi/.HdmiInputService/HW5: info: TvInputInfo{id=com.sony.dtv.tvinput.hdmi/.HdmiInputService/HW5, pkg=com.sony.dtv.tvinput.hdmi, service=com.sony.dtv.tvinput.hdmi.HdmiInputService}; state: 0
      com.sony.dtv.tvinput.hdmi/.HdmiInputService/HW6: info: TvInputInfo{id=com.sony.dtv.tvinput.hdmi/.HdmiInputService/HW6, pkg=com.sony.dtv.tvinput.hdmi, service=com.sony.dtv.tvinput.hdmi.HdmiInputService}; state: 1
      com.sony.dtv.tvinput.hdmi/.HdmiInputService/HW7: info: TvInputInfo{id=com.sony.dtv.tvinput.hdmi/.HdmiInputService/HW7, pkg=com.sony.dtv.tvinput.hdmi, service=com.sony.dtv.tvinput.hdmi.HdmiInputService}; state: 1
      com.sony.dtv.tvinput.composite/.CompositeInputService/HW3: info: TvInputInfo{id=com.sony.dtv.tvinput.composite/.CompositeInputService/HW3, pkg=com.sony.dtv.tvinput.composite, service=com.sony.dtv.tvinput.composite.CompositeInputService}; state: 1
      com.google.android.videos/.tv.TvInputService: info: TvInputInfo{id=com.google.android.videos/.tv.TvInputService, pkg=com.google.android.videos, service=com.google.android.videos.tv.TvInputService}; state: 0
    TvInputHardwareManager:
      Hardware: TvInputHardwareInfo {id=0, type=2, audio_type=0, audio_address=, cable_connection_status=0}
      Hardware: TvInputHardwareInfo {id=3, type=3, audio_type=0, audio_address=, cable_connection_status=2}
      Hardware: TvInputHardwareInfo {id=5, type=9, audio_type=0, audio_address=, hdmi_port=1, cable_connection_status=1}
      Hardware: TvInputHardwareInfo {id=6, type=9, audio_type=0, audio_address=, hdmi_port=2, cable_connection_status=2}
      Hardware: TvInputHardwareInfo {id=7, type=9, audio_type=0, audio_address=, hdmi_port=3, cable_connection_status=1}
"""
    private val sonyCec = """
HdmiControlService:
  mHdmiControlEnabled: true
  mPortInfo:
    PortInfo{id=1, type=INPUT, address=0x1000, cec=true, arc=false, mhl=false}
  Hdmi devices:
    CEC: logical_address: 0x00 device_type: 0 vendor_id: 0x08001d display_name: Sony TV power_status: 0 physical_address: 0x0000 port_id: 0
    CEC: logical_address: 0x04 device_type: 4 vendor_id: 0x8c display_name: PlayStation 5 power_status: 0 physical_address: 0x1000 port_id: 1
    CEC: logical_address: 0x08 device_type: 4 vendor_id: 0x1234 display_name: Apple TV 4K power_status: 1 physical_address: 0x3000 port_id: 3
"""

    /** Google TV 12, TCL-like: the cable status prints as a different key set, HDMI 4 behind an AV receiver, AV and a tuner. */
    private val tcl = """
TV Input Manager State:
  User Id: 0
    inputMap: size=6
      com.tcl.tvinput/.hdmi.Hdmi1InputService/HW11: TvInputInfo{id=com.tcl.tvinput/.hdmi.Hdmi1InputService/HW11, pkg=com.tcl.tvinput, service=com.tcl.tvinput.hdmi.Hdmi1InputService}
      com.tcl.tvinput/.hdmi.Hdmi2InputService/HW12: TvInputInfo{id=com.tcl.tvinput/.hdmi.Hdmi2InputService/HW12, pkg=com.tcl.tvinput, service=com.tcl.tvinput.hdmi.Hdmi2InputService}
      com.tcl.tvinput/.hdmi.Hdmi3InputService/HW13: TvInputInfo{id=com.tcl.tvinput/.hdmi.Hdmi3InputService/HW13, pkg=com.tcl.tvinput, service=com.tcl.tvinput.hdmi.Hdmi3InputService}
      com.tcl.tvinput/.av.AvInputService/HW2: TvInputInfo{id=com.tcl.tvinput/.av.AvInputService/HW2, pkg=com.tcl.tvinput, service=com.tcl.tvinput.av.AvInputService}
      com.google.android.tv.dtvinput/.DtvInputService/HW19: TvInputInfo{id=com.google.android.tv.dtvinput/.DtvInputService/HW19, pkg=com.google.android.tv.dtvinput, service=com.google.android.tv.dtvinput.DtvInputService}
      com.google.android.apps.tv.launcherx/.livetab.LiveTvInputService: TvInputInfo{id=com.google.android.apps.tv.launcherx/.livetab.LiveTvInputService, pkg=com.google.android.apps.tv.launcherx, service=x}
    TvInputHardwareManager:
      mHardwareInfos:
        TvInputHardwareInfo {id=2, type=3, audio_type=0, audio_address=, cable_connection_status=2}
        TvInputHardwareInfo {id=11, type=9, audio_type=0, audio_address=, hdmi_port=1, cable_connection_status=2}
        TvInputHardwareInfo {id=12, type=9, audio_type=0, audio_address=, hdmi_port=2, cable_connection_status=1}
        TvInputHardwareInfo {id=13, type=9, audio_type=0, audio_address=, hdmi_port=3, cable_connection_status=1}
        TvInputHardwareInfo {id=19, type=2, audio_type=0, audio_address=, cable_connection_status=1}
"""
    private val tclCec = """
  mDeviceInfos:
    HdmiDeviceInfo{id=2, type=1, logical_address=4, physical_address=0x2000, port_id=2, display_name=Nintendo Switch, power_status=0}
    HdmiDeviceInfo{id=3, type=1, logical_address=5, physical_address=0x3100, port_id=3, display_name=Denon AVR, power_status=0}
    HdmiDeviceInfo{id=4, type=1, logical_address=8, physical_address=0x3110, port_id=3, display_name=Fire TV Stick, power_status=0}
"""

    /** A set-top box and the Google TV emulator: a tuner service, no hardware input. */
    private val box = """
TV Input Manager State:
  User Ids:
    0
  User Id: 0
    inputMap: size=2
      com.google.android.tv.dtvinput/.DtvInputService: info: TvInputInfo{id=com.google.android.tv.dtvinput/.DtvInputService, pkg=com.google.android.tv.dtvinput, service=x}; state: 0
      com.android.tv.tuner/.TunerInputService: info: TvInputInfo{id=com.android.tv.tuner/.TunerInputService, pkg=com.android.tv.tuner, service=y}; state: 0
"""

    private fun p(a: String, b: String = "") = Inputs.parse(a + "\n" + sep + "\n" + b)

    @Test fun sonyLike() {
        val l = p(sony, sonyCec)
        assertEquals(listOf("HDMI 1 · PlayStation 5", "HDMI 2", "HDMI 3 · Apple TV 4K", "AV", "TV"), l.map { it.label })
        assertEquals(listOf("com.sony.dtv.tvinput.hdmi/.HdmiInputService/HW5", "com.sony.dtv.tvinput.hdmi/.HdmiInputService/HW6", "com.sony.dtv.tvinput.hdmi/.HdmiInputService/HW7",
            "com.sony.dtv.tvinput.composite/.CompositeInputService/HW3", Inputs.TUNER), l.map { it.id })
        assertEquals(listOf("hdmi", "hdmi", "hdmi", "av", "tuner"), l.map { it.kind })
        assertEquals(listOf(243, 244, 245, 247, 170), l.map { it.key })
        assertEquals(listOf(true, false, true, false), l.take(4).map { it.connected })    // the cable status the dump gave
        assertEquals("PlayStation 5", l[0].device)
        assertFalse("the TV's own CEC entry is not a box", l.any { it.label.contains("Sony TV") })
    }

    @Test fun googleTvTclLike() {
        val l = p(tcl, tclCec)
        // ports come from the dump, else the service name ("Hdmi2InputService"); a box behind a receiver (0x3100) loses to nothing plugged straight in
        assertEquals(listOf("HDMI 1", "HDMI 2 · Nintendo Switch", "HDMI 3 · Denon AVR", "AV", "TV"), l.map { it.label })
        assertEquals(listOf(243, 244, 245, 247, 170), l.map { it.key })
        assertTrue("an app's own input service (Live tab) is not an input", l.none { it.id.contains("launcherx") })
    }

    @Test fun portFromTheServiceNameWhenTheHardwareLinesAreMissing() {
        val l = p(tcl.lines().filter { !it.contains("TvInputHardwareInfo") }.joinToString("\n"))
        assertEquals(listOf("HDMI 1", "HDMI 2", "HDMI 3"), l.filter { it.kind == "hdmi" }.map { it.label })
    }

    @Test fun aBoxHasNone() {
        assertTrue(p(box).isEmpty())
        assertTrue(Inputs.parse(box).isEmpty())                       // and without the second dump at all
    }

    @Test fun neverThrowsOnGarbage() {
        for (s in listOf("", "\n\n", "Can't find service: tv_input", "TvInputInfo{id=", "TvInputHardwareInfo {id=x, type=}", sep, "HW5 HW6 /HW7\n$sep\ndisplay_name: ")) {
            val r = Inputs.parse(s)
            assertTrue(s, r.isEmpty() || r.all { it.id.isNotEmpty() })
        }
    }

    @Test fun hdmiNumbersFillTheGapsWhenNoPortIsPrinted() {
        val dump = """
          TvInputInfo{id=com.x/.HdmiInputService/HW1, pkg=a, service=b}
          TvInputInfo{id=com.x/.HdmiInputService/HW2, pkg=a, service=b}
        """
        assertEquals(listOf("HDMI 1", "HDMI 2"), Inputs.parse(dump).map { it.label })
    }

    @Test fun fallbackKeys() {
        assertEquals(243, Inputs.fallbackKey("hdmi", 1)); assertEquals(246, Inputs.fallbackKey("hdmi", 4)); assertEquals(0, Inputs.fallbackKey("hdmi", 5))
        assertEquals(247, Inputs.fallbackKey("av", 1)); assertEquals(249, Inputs.fallbackKey("component", 1)); assertEquals(251, Inputs.fallbackKey("vga", 1))
    }

    @Test fun theLinkEncodesTheId() {
        assertEquals("content://android.media.tv/passthrough/com.sony.dtv.tvinput.hdmi%2F.HdmiInputService%2FHW5", Inputs.link("com.sony.dtv.tvinput.hdmi/.HdmiInputService/HW5"))
        assertFalse(Inputs.valid("a; reboot")); assertFalse(Inputs.valid("")); assertTrue(Inputs.valid("com.a/.B/HW5"))
    }

    @Test fun switchesByLinkThenByKey() {
        val id = "com.sony.dtv.tvinput.hdmi/.HdmiInputService/HW6"
        val ran = ArrayList<String>()
        assertEquals("intent", Inputs.switch({ ran.add(it); "Starting: Intent { act=android.intent.action.VIEW dat=content://android.media.tv/passthrough/... }" }, id))
        assertEquals(1, ran.size); assertTrue(ran[0].startsWith("am start -a android.intent.action.VIEW -d '") || ran[0].startsWith("am start -a android.intent.action.VIEW -d \""))

        val known = p(sony, sonyCec)
        val ran2 = ArrayList<String>()
        val via = Inputs.switch({ ran2.add(it); if (it.startsWith("am start")) "Error: Activity not started, unable to resolve Intent" else "" }, id, known)
        assertEquals("key", via)
        assertEquals("input keyevent 244", ran2.last())

        val ran3 = ArrayList<String>()                                // no list given: read it only after the link failed
        Inputs.switch({ ran3.add(it); if (it.startsWith("am start")) "Error: unable to resolve Intent" else if (it.startsWith("dumpsys")) sony + sep + sonyCec else "" }, id)
        assertEquals(3, ran3.size); assertEquals("input keyevent 244", ran3.last())
    }

    @Test fun noKeyAndNoLinkIsAnError() {
        try { Inputs.switch({ "Error: unable to resolve" }, "com.x/.Y/HW9", emptyList()); org.junit.Assert.fail() } catch (e: IllegalArgumentException) { }
        try { Inputs.switch({ "" }, "bad id;") ; org.junit.Assert.fail() } catch (e: IllegalArgumentException) { }
    }

    @Test fun tunerIsTheLiveKey() {
        val ran = ArrayList<String>()
        assertEquals("key", Inputs.switch({ ran.add(it); "" }, Inputs.TUNER))
        assertEquals(listOf("input keyevent 170"), ran)
    }

    @Test fun lgList() {
        val j = JSONObject("""{"returnValue":true,"devices":[
            {"id":"HDMI_1","label":"PlayStation","port":1,"connected":true,"appId":"com.webos.app.hdmi1","icon":"x"},
            {"id":"HDMI_2","label":"HDMI 2","port":2,"connected":false,"modelName":""},
            {"id":"HDMI_3","label":"HDMI3","port":3,"connected":true,"modelName":"Chromecast"},
            {"id":"AV_1","label":"AV","connected":false}]}""")
        val l = Inputs.parseLg(j)
        assertEquals(listOf("HDMI 1 · PlayStation", "HDMI 2", "HDMI 3 · Chromecast", "AV"), l.map { it.label })
        assertEquals(listOf("HDMI_1", "HDMI_2", "HDMI_3", "AV_1"), l.map { it.id })
        assertEquals(listOf(true, false, true, false), l.map { it.connected })
        assertTrue(Inputs.parseLg(null).isEmpty()); assertTrue(Inputs.parseLg(JSONObject("{}")).isEmpty())
    }

    @Test fun jsonShape() {
        val o = Inputs.json(p(sony, sonyCec)).getJSONArray("inputs")
        assertEquals(5, o.length())
        assertEquals("HDMI 1 · PlayStation 5", o.getJSONObject(0).getString("label"))
        assertEquals(1, o.getJSONObject(0).getInt("port"))
        assertTrue(o.getJSONObject(0).has("connected")); assertFalse(o.getJSONObject(4).has("connected"))
    }

    @Test fun cacheKeepsAMinuteAndNotFailures() {
        var t = 0L
        val c = Inputs.Cache(60_000) { t }
        var n = 0
        c.get { n++; emptyList() }; c.get { n++; emptyList() }
        assertEquals(1, n)
        t = 61_000; c.get { n++; emptyList() }
        assertEquals(2, n)
        c.get(fresh = true) { n++; emptyList() }
        assertEquals(3, n)
        val c2 = Inputs.Cache(60_000) { t }
        try { c2.get { throw java.io.IOException("x") } } catch (e: java.io.IOException) { }
        assertNull(c2.peek())
    }
}
