package dev.tvremote.home

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import dev.tvremote.core.web.Qr
import org.json.JSONObject

/**
 * The QR screen of "Your photo from a phone": the code, the short address for a phone that cannot scan, and what to do. The server
 * ([PhotoServer]) lives exactly as long as this dialog (and at most 10 minutes); [onSet] runs when this screen closes after a photo became the background (the settings panel behind it closes too, to show it).
 */
object PhotoScreen {
    private val PANEL_TOP = 0xFF161B2B.toInt()                        // the same panel as Customize's
    private val PANEL_BOTTOM = 0xFF0C1020.toInt()
    private const val EDGE = 0x33FFFFFF
    private val SOFT = 0xFFB8C7E6.toInt()
    private val GOOD = 0xFF7BE0A1.toInt()
    private val BAD = 0xFFFFB4A9.toInt()
    private val DIM = 0xA6FFFFFF.toInt()
    private val FAINT = 0x8CFFFFFF.toInt()
    private val INK = 0xFF111318.toInt()
    private const val CHIP_FOCUS = 0x2EFFFFFF                         // the option chips: Customize's chips
    private const val CHIP_FILL = 0x12FFFFFF
    private const val CHIP_EDGE = 0x1FFFFFFF

    /** [web] true: the same screen for "Web page as the background" (the phone page pastes a link; the D-pad picks how it updates). */
    fun show(a: Activity, web: Boolean = false, onSet: () -> Unit): Dialog {
        val d = a.resources.displayMetrics.density
        fun dp(v: Number) = (v.toFloat() * d).toInt()
        fun label(t: String, size: Float, color: Int) = Font.on(TextView(a)).apply { text = t; textSize = size; setTextColor(color); gravity = Gravity.CENTER }
        val dialog = Dialog(a, R.style.Menu)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(32), dp(20), dp(32), dp(20)) }
        col.addView(label(a.getString(if (web) R.string.pq_web_title else R.string.pq_title), 22f, Color.WHITE).apply { typeface = Font.medium })
        val status = label(" ", 15f, SOFT)                        // a line kept free: the result must not push Close down
        val addr = label("", 18f, Color.WHITE).apply { typeface = Font.medium }
        val qr = ImageView(a).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        var changed = false

        var firstChip: View? = null
        val s = PhotoServer.start(a) {
            changed = true
            status.text = a.getString(R.string.pq_done); status.setTextColor(GOOD)
            Toast.makeText(a, R.string.pq_toast, Toast.LENGTH_LONG).show()
        }
        if (s == null) {
            col.addView(label(a.getString(R.string.pq_none), 16f, BAD), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        } else {
            val side = minOf(a.resources.displayMetrics.heightPixels * 36 / 100, dp(280))   // at most: shrunk below if the panel would not fit
            qr.setImageBitmap(Qr.bitmap(s.url, 8))
            col.addView(qr, LinearLayout.LayoutParams(side, side).apply { topMargin = dp(16) })
            col.addView(label(a.getString(if (web) R.string.pq_web_step else R.string.pq_step), 15f, SOFT), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            col.addView(label(a.getString(R.string.pq_addr), 13f, DIM), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            addr.text = "http://${s.shown}/?k=${s.token}"
            col.addView(addr, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            col.addView(label(a.getString(R.string.pq_time), 12f, FAINT), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            if (web) firstChip = options(a, col, { changed = true; status.text = a.getString(R.string.pq_done); status.setTextColor(GOOD) }, ::dp)
            col.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        val close = Font.on(TextView(a)).apply {
            text = a.getString(R.string.pq_close); textSize = 16f; setTextColor(INK); gravity = Gravity.CENTER; typeface = Font.medium
            isFocusable = true; isClickable = true; setPadding(dp(32), dp(11), dp(32), dp(11))
            background = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(Color.WHITE) })
                addState(intArrayOf(), GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(SOFT) })
            }
            setOnClickListener { dialog.dismiss() }
        }
        col.addView(close, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })

        val panel = android.widget.FrameLayout(a).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(PANEL_TOP, PANEL_BOTTOM)).apply {
                cornerRadius = dp(28).toFloat(); setStroke(dp(1), EDGE)
            }
            addView(col, android.widget.FrameLayout.LayoutParams(-1, -2))
        }
        val w = minOf(a.resources.displayMetrics.widthPixels - dp(64), dp(640))
        // the panel keeps 40dp of screen above and below (it touched both edges of a 1080p TV at 2x): the QR gives up what is missing
        col.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val over = col.measuredHeight - (a.resources.displayMetrics.heightPixels - dp(80))
        if (over > 0 && qr.parent != null) qr.layoutParams = qr.layoutParams.apply { width = maxOf(dp(120), height - over); height = width }
        dialog.setContentView(panel, ViewGroup.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT))
        dialog.window?.apply { setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT); setDimAmount(0.6f) }
        // closing this screen (OK, Back, or the activity going away) is what stops the server
        dialog.setOnDismissListener { PhotoServer.stop(); if (changed) onSet() }
        dialog.setOnShowListener { (firstChip ?: close).requestFocus() }
        dialog.show()
        return dialog
    }

    private fun glass(r: Int, fill: Int, stroke: Int, d: (Number) -> Int) = GradientDrawable().apply { cornerRadius = d(r).toFloat(); setColor(fill); setStroke(d(1), stroke) }

    /**
     * How the page updates, as three chips the D-pad steps through (the same Store keys the Manage page writes): live or snapshot; with
     * snapshots, how often and whether the page stays open between them. Plus "use the saved page" when one is kept and not on. Returns the first.
     */
    private fun options(a: Activity, col: LinearLayout, used: () -> Unit, dp: (Number) -> Int): View {
        fun chip(onClick: (TextView) -> Unit) = Font.on(TextView(a)).apply {
            textSize = 14f; gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(Color.WHITE); isFocusable = true; isClickable = true; setPadding(dp(10), dp(11), dp(10), dp(11))
            background = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), glass(14, CHIP_FOCUS, Color.WHITE, dp))
                addState(intArrayOf(), glass(14, CHIP_FILL, CHIP_EDGE, dp))
            }
            onFocusChangeListener = View.OnFocusChangeListener { v, f -> Motion.pop(v, f) }
            setOnClickListener { onClick(this) }
        }
        val first = ArrayList<View>()
        val saved = Store.get(a).optString("wurl")
        if (saved.isNotEmpty() && Store.get(a).optString("wall") != HomeConfig.WEB) {
            val use = chip { Store.change(a, JSONObject().put("wall", HomeConfig.WEB).put("wurl", saved)); used() }
            use.text = a.getString(R.string.pq_web_saved, saved.removePrefix("https://").removePrefix("http://"))
            col.addView(use, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }); first.add(use)
        }
        val note = Font.on(TextView(a)).apply { textSize = 12f; setTextColor(FAINT); gravity = Gravity.CENTER }
        lateinit var mode: TextView; lateinit var every: TextView; lateinit var keep: TextView
        fun paint() {
            val c = Store.get(a)
            val snap = c.optString("wmode") == "snap"
            mode.text = a.getString(if (snap) R.string.pq_mode_snap else R.string.pq_mode_live)
            every.text = a.getString(R.string.pq_every, c.optInt("wsnap", HomeConfig.WSNAP_DEFAULT))
            keep.text = a.getString(if (c.optBoolean("wkeep")) R.string.pq_keep_on else R.string.pq_keep_off)
            every.visibility = if (snap) View.VISIBLE else View.GONE; keep.visibility = every.visibility
            note.text = a.getString(if (!snap) R.string.pq_note_live else if (c.optBoolean("wkeep")) R.string.pq_note_keep else R.string.pq_note_snap)
        }
        mode = chip { val snap = Store.get(a).optString("wmode") == "snap"; Store.change(a, JSONObject().put("wmode", if (snap) "live" else "snap")); paint() }
        every = chip { val i = HomeConfig.WSNAPS.indexOf(Store.get(a).optInt("wsnap")); Store.change(a, JSONObject().put("wsnap", HomeConfig.WSNAPS[(i + 1) % HomeConfig.WSNAPS.size])); paint() }
        keep = chip { Store.change(a, JSONObject().put("wkeep", !Store.get(a).optBoolean("wkeep"))); paint() }
        val row = LinearLayout(a).apply { clipChildren = false; clipToPadding = false }
        row.addView(mode, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(every, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        row.addView(keep, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(if (first.isEmpty()) 14 else 8) })
        col.addView(note, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        paint()
        return first.firstOrNull() ?: mode
    }
}
