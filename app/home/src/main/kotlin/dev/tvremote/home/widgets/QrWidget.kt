package dev.tvremote.home.widgets

import android.view.View
import android.widget.LinearLayout.LayoutParams
import dev.tvremote.core.web.Qr
import dev.tvremote.home.HomeQr
import dev.tvremote.home.R
import dev.tvremote.home.widgets.Widgets.Companion.AMBER
import org.json.JSONObject

/** A QR widget: the code of the widget's template, the caption under it and, if asked, the Wi-Fi password as text. */
internal fun Widgets.qrWidget(c: JSONObject): View {
    return qrView(c, tonal = false)?.let { widget().apply { setPadding(dp(14), dp(14), dp(16), dp(14)); addView(it, LayoutParams(-1, -1)) } }       // the same 14 dp on every side: at 8 the white square crowded the 22 dp corners
        ?: pill(tile(AMBER, R.drawable.ic_message), c.optString("cap"), "", 2)             // too long for any code: cleaned away before it gets here
}

/** The code's View alone (the glass widget and the tonal one both put it in their own widget); null when the text is too long for any code. */
internal fun Widgets.qrView(c: JSONObject, tonal: Boolean): View? {
    val f = c.getJSONObject("f")
    val grid = Qr.modules(HomeQr.payload(c.getString("tpl"), f))
    val pass = f.optString("pass")
    val secret = if (c.optBoolean("show") && pass.isNotEmpty()) pass else ""
    return if (grid != null) QrView(a, grid, c.optString("cap"), a.getString(R.string.qr_pass), secret, a.getString(R.string.qr_scan), tonal) else null
}
