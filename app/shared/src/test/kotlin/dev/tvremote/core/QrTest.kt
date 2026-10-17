package dev.tvremote.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import dev.tvremote.core.web.Qr

/** Qr.kt draws the same codes as the page's js/qr.js: the ones in tests/qr_cases.json (checked on the JS side by qr.test.js). */
class QrTest {
    @Test fun sameCodesAsThePage() {
        val cases = JSONObject(File("tests/qr_cases.json").readText()).getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val want = if (c.isNull("rows")) null else c.getJSONArray("rows").let { r -> List(r.length()) { r.getString(it) } }
            val got = Qr.modules(c.getString("text"))?.map { row -> row.joinToString("") { if (it) "1" else "0" } }
            assertEquals(c.getString("text"), want, got)
        }
    }
}
