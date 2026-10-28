package dev.tvremote.home

import dev.tvremote.core.web.Qr
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** TV Home's QR cards: the text each template builds is what tests/home_qr_cases.json says (js/home_qr.js reads the same table). */
class HomeQrTest {
    @Test fun templatesBuildTheTextOfTheCases() {
        val cases = JSONObject(File("tests/home_qr_cases.json").readText()).getJSONArray("cases")
        assertTrue(cases.length() > 15)
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            assertEquals(c.getString("name"), c.getString("payload"), HomeQr.payload(c.getString("tpl"), c.getJSONObject("f")))
        }
    }

    @Test fun aCardKeepsOnlyItsOwnFieldsAndAnOpenWifiKeepsNoPassword() {
        val k = HomeQr.widget(JSONObject().put("tpl", "wifi").put("cap", "  my \n wifi ").put("show", true)
            .put("f", JSONObject().put("ssid", "A").put("pass", "b").put("sec", "bogus").put("hidden", true).put("url", "x")))!!
        assertEquals("my wifi", k.getString("cap"))
        assertEquals(true, k.getBoolean("show"))
        assertEquals("WPA", k.getJSONObject("f").getString("sec"))
        assertFalse(k.getJSONObject("f").has("url"))
        assertEquals("WIFI:T:WPA;S:A;P:b;H:true;;", HomeQr.payload("wifi", k.getJSONObject("f")))
        // "show" belongs to a Wi-Fi only
        assertEquals(false, HomeQr.widget(JSONObject().put("tpl", "link").put("show", true).put("f", JSONObject().put("url", "a.b")))!!.getBoolean("show"))
    }

    @Test fun nothingTypedOrTooLongOrUnknownIsNoCard() {
        assertNull(HomeQr.widget(JSONObject().put("tpl", "wifi").put("f", JSONObject().put("ssid", ""))))
        assertNull(HomeQr.widget(JSONObject().put("tpl", "fax").put("f", JSONObject().put("num", "1"))))
        assertNotNull(HomeQr.widget(JSONObject().put("tpl", "text").put("f", JSONObject().put("text", "x".repeat(HomeQr.MAX_BYTES + 1)))))   // cut to what fits, not refused
        assertNotNull(HomeQr.widget(JSONObject().put("tpl", "text").put("f", JSONObject().put("text", "x".repeat(HomeQr.MAX_BYTES)))))
        // bytes, not letters: 90 accented letters are 180 bytes, 140 are 280
        assertNotNull(HomeQr.widget(JSONObject().put("tpl", "text").put("f", JSONObject().put("text", "é".repeat(130)))))
        assertNull(HomeQr.widget(JSONObject().put("tpl", "text").put("f", JSONObject().put("text", "é".repeat(140)))))
    }

    @Test fun whatTheTemplatesBuildFitsTheEncoder() {
        val cases = JSONObject(File("tests/home_qr_cases.json").readText()).getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val p = cases.getJSONObject(i).getString("payload")
            if (p.isNotEmpty()) assertNotNull(p, Qr.modules(p))
        }
        assertNull(Qr.modules("x".repeat(HomeQr.MAX_BYTES + 1)))
        assertNotNull(Qr.modules("x".repeat(HomeQr.MAX_BYTES)))
    }
}
