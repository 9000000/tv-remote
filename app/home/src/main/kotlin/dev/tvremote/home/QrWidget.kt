package dev.tvremote.home

import android.view.View
import android.widget.LinearLayout.LayoutParams
import dev.tvremote.core.web.Qr
import dev.tvremote.home.Widgets.Companion.AMBER
import org.json.JSONObject

/** A QR card: the code of the card's template, the caption under it and, if asked, the Wi-Fi password as text. */
internal fun Widgets.qrCard(c: JSONObject): View {
    val f = c.getJSONObject("f")
    val grid = Qr.modules(HomeQr.payload(c.getString("tpl"), f))
    val pass = f.optString("pass")
    val secret = if (c.optBoolean("show") && pass.isNotEmpty()) pass else ""
    return if (grid != null) card().apply { setPadding(dp(14), dp(14), dp(16), dp(14)); addView(QrCard(a, grid, c.optString("cap"), a.getString(R.string.qr_pass), secret, a.getString(R.string.qr_scan)), LayoutParams(-1, -1)) }       // the same 14 dp on every side: at 8 the white square crowded the 22 dp corners
        else pill(tile(AMBER, R.drawable.ic_message), c.optString("cap"), "", 2)             // too long for any code: cleaned away before it gets here
}
