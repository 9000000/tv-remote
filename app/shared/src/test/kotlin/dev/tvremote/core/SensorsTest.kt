package dev.tvremote.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorsTest {
    // the Google TV emulator (Android 14)
    private val hal = "Thermal Status: 0\nCurrent temperatures from HAL:\n\tTemperature{mValue=30.8, mType=3, mName=test temperature sensor, mStatus=0}\nCurrent cooling devices from HAL:\n"

    @Test fun thermalFromTheHal() {
        val t = Sensors.thermal(hal, "")!!
        assertEquals(0, t.getInt("status"))
        assertEquals("test temperature sensor", t.getJSONArray("temps").getJSONObject(0).getString("name"))
        assertEquals(30.8, t.getJSONArray("temps").getJSONObject(0).getDouble("c"), 0.01)
    }

    @Test fun thermalSkipsBatteryCurrentAndTakesHottestFirst() {
        val t = Sensors.thermal("Thermal Status: 3\n\tTemperature{mValue=4000.0, mType=7, mName=ibat, mStatus=0}\n" +
            "\tTemperature{mValue=48.5, mType=0, mName=cpu0, mStatus=0}\n\tTemperature{mValue=61.0, mType=1, mName=gpu, mStatus=2}\n", "")!!
        assertEquals(3, t.getInt("status"))
        val temps = t.getJSONArray("temps")
        assertEquals(2, temps.length())
        assertEquals("gpu", temps.getJSONObject(0).getString("name"))
    }

    @Test fun thermalFallsBackToKernelZones() {
        val t = Sensors.thermal("", "soc_thermal 52000\nddr_thermal 49500\n \nbroken \n")!!
        assertFalse(t.has("status"))
        assertEquals(52.0, t.getJSONArray("temps").getJSONObject(0).getDouble("c"), 0.01)
        assertEquals(2, t.getJSONArray("temps").length())
        assertNull(Sensors.thermal("", " \n \n"))                         // the emulator's zones: nothing readable
    }

    @Test fun gpuLoad() {
        assertEquals(37, Sensors.gpu("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage:37 %"))
        assertEquals(25, Sensors.gpu("/sys/class/kgsl/kgsl-3d0/gpubusy:  250000  1000000"))
        assertEquals(12, Sensors.gpu("/sys/kernel/ged/hal/gpu_utilization:12 0 0"))
        assertNull(Sensors.gpu("/sys/class/kgsl/kgsl-3d0/gpubusy: 0 0"))
        assertNull(Sensors.gpu(""))
    }

    @Test fun netBytesLeaveOutLoopbackAndTunnels() {
        val dev = "Inter-|   Receive\n face |bytes    packets\n    lo: 253655682  165555    0    0    0     0          0         0 253655682  165555    0    0    0     0       0          0\n" +
            "  eth0: 461024259  360845    0    0    0     0          0         0  5280292   44965    0    0    0     0       0          0\n" +
            " wlan0:     1000       1    0    0    0     0          0         0      500       1    0    0    0     0       0          0\n" +
            "tun0:     9999       1    0    0    0     0          0         0      999       1    0    0    0     0       0          0\n"
        assertArrayEquals(longArrayOf(461025259, 5280792), Sensors.netBytes(dev))
        assertNull(Sensors.netBytes(""))
    }

    @Test fun bluetoothPairedAndConnected() {
        val out = "  enabled: true\n  Bonded devices:\n    F4:60:E2:11:22:33 [ DUAL ] Remote G20\n    XX:XX:XX:XX:AA:BB [BR/EDR] WH-1000XM4\n\n" +
            "#in\nRemote G20|      Identifier: bus=0x0005, vendor=0x1d5a, product=0xc081, version=0x0001, bluetoothAddress=F4:60:E2:11:22:33\n" +
            "Xbox Wireless Controller|      Identifier: bus=0x0005, vendor=0x045e, product=0x0b13, version=0x0509, bluetoothAddress=<not set>\n"
        val bt = Sensors.bluetooth(out)!!
        assertTrue(bt.getBoolean("on"))
        val d = bt.getJSONArray("devices")
        assertEquals(3, d.length())
        val byName = (0 until d.length()).associate { d.getJSONObject(it).getString("name") to d.getJSONObject(it).getBoolean("connected") }
        assertEquals(mapOf("Remote G20" to true, "Xbox Wireless Controller" to true, "WH-1000XM4" to false), byName)
    }

    @Test fun bluetoothOnTheEmulator() {
        val bt = Sensors.bluetooth("  enabled: true\n  Bonded devices:\n\n#in\n")!!
        assertEquals(0, bt.getJSONArray("devices").length())
        assertNull(Sensors.bluetooth("#in\n"))                            // no adapter: nothing said
    }
}
