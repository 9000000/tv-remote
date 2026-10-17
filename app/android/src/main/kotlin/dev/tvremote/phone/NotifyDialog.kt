package dev.tvremote.phone

import android.app.AlertDialog
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import dev.tvremote.core.Errors
import dev.tvremote.core.ontv.Notify
import org.json.JSONObject

// 🔔 A message on the TV: a card in a corner over whatever is playing, as on the web page (js/notify.js). TV Setup draws it; the
// message goes over ADB (Notify.kt in the shared code). The last corner and length are kept for next time.

private val SECONDS = intArrayOf(3, 5, 10, 30)                        // the same chips as the page's

private fun cornerWords(c: String) = when (c) {
    "top-left" -> t("↖ Trên trái", "↖ Top left")
    "top-right" -> t("↗ Trên phải", "↗ Top right")
    "bottom-left" -> t("↙ Dưới trái", "↙ Bottom left")
    else -> t("↘ Dưới phải", "↘ Bottom right")
}

/** A row of choices that look like buttons, the picked one lit; [pick] is told the new choice. */
internal fun MainActivity.chips(options: List<Pair<String, String>>, now: String, pick: (String) -> Unit): LinearLayout {
    val row = LinearLayout(this)
    options.forEachIndexed { i, (value, words) ->
        val b = button(words, if (value == now) ACCENT else BTN)
        b.textSize = 13f
        b.isSingleLine = true                                         // "10 giây" on one line: a wrapped chip was cut at the bottom
        b.setPadding(0, b.paddingTop, 0, b.paddingBottom)
        b.setOnClickListener { pick(value) }
        row.addView(b, cell(40, i > 0))
    }
    return row
}

internal fun MainActivity.notifyDialog() {
    if (needsAdb()) return
    withSetup("notify") { showNotifyDialog() }                        // TV Setup draws the card (SetupNeed "notify"): asked to update first
}

private fun MainActivity.showNotifyDialog() {
    var corner = Notify.corner(prefs.getString("notify.corner", null))
    var seconds = Notify.seconds(prefs.getInt("notify.seconds", Notify.SECONDS)).let { if (it in SECONDS) it else Notify.SECONDS }
    val title: EditText = input(t("Tiêu đề (không bắt buộc)", "Title (optional)")).also { it.filters = arrayOf(android.text.InputFilter.LengthFilter(Notify.MAX_TITLE)) }
    val text: EditText = input(t("Nội dung ngắn, vd Xuống ăn cơm!", "A short message, e.g. Dinner is ready!")).also {
        it.setSingleLine(false); it.minLines = 2; it.maxLines = 4
        it.filters = arrayOf(android.text.InputFilter.LengthFilter(Notify.MAX_TEXT))
    }
    val body = col()
    body.addView(title)
    body.addView(text, gap(8))
    body.addView(head(t("GÓC MÀN HÌNH", "CORNER")), gap(14))
    val corners = col()
    val seconds2 = LinearLayout(this)
    fun draw() {
        corners.removeAllViews()
        for ((n, pair) in Notify.CORNERS.chunked(2).withIndex())
            corners.addView(chips(pair.map { it to cornerWords(it) }, corner) { corner = it; draw() }, gap(if (n == 0) 6 else 6))
        seconds2.removeAllViews()
        seconds2.addView(chips(SECONDS.map { it.toString() to "$it " + t("giây", "s") }, seconds.toString()) { seconds = it.toInt(); draw() }, LinearLayout.LayoutParams(-1, -2))
    }
    draw()
    body.addView(corners)
    body.addView(head(t("HIỆN TRONG", "STAYS FOR")), gap(14))
    body.addView(seconds2, gap(6))
    AlertDialog.Builder(this).setTitle(t("Gửi thông báo lên TV", "Notify the TV")).setView(inDialog(body))
        .setPositiveButton(t("Gửi", "Send")) { _, _ ->
            val msg = JSONObject().put("title", title.text.toString().trim()).put("text", text.text.toString().trim()).put("corner", corner).put("seconds", seconds)
            if (msg.getString("title").isEmpty() && msg.getString("text").isEmpty()) {
                Toast.makeText(this, t("Gõ nội dung cần gửi", "Type what to send"), Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            prefs.edit().putString("notify.corner", corner).putInt("notify.seconds", seconds).apply()
            sendNotify(msg)
        }
        .setNegativeButton(t("Hủy", "Cancel"), null).show()
}

private fun MainActivity.sendNotify(msg: JSONObject) {
    val tv = tv ?: return
    Thread {
        val err = try { Notify.send({ tv.shell(it) }, msg); null } catch (e: Exception) { e }
        ui.post {
            if (isFinishing) return@post
            if (err == null) Toast.makeText(this, t("Đã gửi lên TV", "Sent to the TV"), Toast.LENGTH_SHORT).show()
            else if (!offerSetup(err)) Toast.makeText(this, if ("notify:nooverlay" in (err.message ?: ""))
                t("TV này không cho app hiện thông báo đè lên màn hình.", "This TV does not let apps show a notification over its screen.")
                else Errors.say(err, VI), Toast.LENGTH_LONG).show()
        }
    }.start()
}
