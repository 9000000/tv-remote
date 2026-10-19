package dev.tvremote.phone

import dev.tvremote.core.T
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import dev.tvremote.core.Errors
import dev.tvremote.core.ontv.Dim
import org.json.JSONObject

// 🔅 A tint over the whole TV screen, as on the web page (js/dim.js): TV Setup draws it (Dim.kt in the shared code), sent as
// the colour or the strength changes, so the TV shows each step while the slider moves.

private fun colourWords(c: String) = when (c) {
    "dark" -> T("dim.dark")
    "amber" -> T("dim.amber")
    "yellow" -> T("dim.yellow")
    "sepia" -> T("dim.sepia")
    "red" -> T("dim.red")
    else -> T("dim.blue")
}

internal fun MainActivity.dimDialog() {
    if (needsAdb()) return
    withSetup("dim") {
        val tv = tv ?: return@withSetup
        Thread {
            val now = try { Dim.call({ tv.shell(it) }, null) } catch (e: Exception) { e }
            ui.post {
                if (isFinishing) return@post
                if (now is JSONObject) showDim(now) else failed(now as Exception)
            }
        }.start()
    }
}

private fun MainActivity.failed(e: Exception) {
    if (!offerSetup(e)) Toast.makeText(this, if ("notify:nooverlay" in (e.message ?: "")) T("notify_dialog.this_tv_does_not")
        else Errors.say(e, dev.tvremote.core.Words.lang), Toast.LENGTH_LONG).show()
}

private fun MainActivity.showDim(now: JSONObject) {
    var s = Dim.clean(now)
    var sending: Runnable? = null
    val body = col()
    val colours = col()
    val pct = TextView(this)
    val bar = SeekBar(this).apply { max = (Dim.MAX_PCT - Dim.MIN_PCT) / 5; progress = (s.optInt("pct") - Dim.MIN_PCT) / 5 }
    fun send() {
        sending?.let { ui.removeCallbacks(it) }
        val tv = tv ?: return
        val what = JSONObject(s.toString())
        sending = Runnable { Thread { try { Dim.call({ tv.shell(it) }, what) } catch (e: Exception) { ui.post { if (!isFinishing) failed(e) } } }.start() }
        ui.postDelayed(sending!!, 120)                                // a slider moved: only where it stopped
    }
    fun draw() {
        colours.removeAllViews()
        for ((n, row) in Dim.COLORS.keys.chunked(3).withIndex())
            colours.addView(chips(row.map { it to colourWords(it) }, if (s.optBoolean("on")) s.optString("color") else "") { s.put("color", it).put("on", true); draw(); send() }, gap(if (n == 0) 0 else 6))
        pct.text = T("dim.strength") + ": " + s.optInt("pct") + "%"
    }
    bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(b: SeekBar, p: Int, user: Boolean) { if (user) { s.put("pct", Dim.MIN_PCT + p * 5).put("on", true); draw(); send() } }
        override fun onStartTrackingTouch(b: SeekBar) {}
        override fun onStopTrackingTouch(b: SeekBar) {}
    })
    draw()
    body.addView(colours)
    body.addView(pct, gap(14))
    body.addView(bar, LinearLayout.LayoutParams(-1, -2))
    body.addView(note(T("dim.note")), gap(10))
    Popup.Builder(this).setTitle(T("dim.title")).setView(inDialog(body))
        .setPositiveButton(T("common.close"), null)
        .setNeutralButton(T("dim.off")) { _, _ -> s.put("on", false); send() }
        .show()
}
