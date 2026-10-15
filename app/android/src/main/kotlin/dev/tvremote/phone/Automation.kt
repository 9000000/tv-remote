package dev.tvremote.phone

import android.app.AlertDialog
import android.widget.Toast
import dev.tvremote.core.AdbLink
import dev.tvremote.core.RemoteLabels
import dev.tvremote.core.RulesClient
import dev.tvremote.core.SetupNeed
import dev.tvremote.core.Scenes
import dev.tvremote.core.Voice
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The remote's 😴 and 🎬 buttons and what the 🎤 hears: TV Setup's sleep timer (it runs on the TV, see Rules.kt there), this
// phone's scenes (SceneStore), and sentences carried out here before the TV's Assistant is asked (Voice.kt). Bedtime, app
// limits and editing scenes are on the Automation tab of the Manage screen.

private fun MainActivity.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

/** The TV's clock to this phone's: "23:10" for the moment the timer ends. */
private fun hm(tvTime: Long, tvNow: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(tvTime - (tvNow - System.currentTimeMillis())))

/** Asks TV Setup on a worker thread, answers on the UI thread. */
private fun MainActivity.rules(change: JSONObject?, then: (JSONObject) -> Unit) {
    val tv = tv
    if (tv == null || !tv.ready()) return
    Thread {
        val r = try { RulesClient.call({ tv.shell(it) }, change) } catch (e: Exception) { JSONObject().put("error", e.message ?: "") }
        ui.post { if (!isFinishing) then(r) }
    }.start()
}

private fun MainActivity.needsSetup(r: JSONObject): Boolean {
    if (!r.has("setup") && !r.has("error")) return false
    if (r.has("setup") && offerSetup(SetupNeed.Missing("rules", r.optString("have")))) return true
    AlertDialog.Builder(this).setTitle(t("Cần TV Setup trên TV", "TV Setup is needed on the TV"))
        .setMessage(if (r.has("error")) r.optString("error") else t(
            "Hẹn giờ tắt, giờ đi ngủ và giới hạn app chạy ngay trên TV bằng app TV Setup (bản 2.13.0 trở lên), để TV tự tắt được cả khi điện thoại đã tắt. Trang Tự động có nút cài giúp.",
            "The sleep timer, bedtime and app limits run on the TV itself, in the TV Setup app (2.13.0 or newer), so the TV turns off even with the phone off. The Automation page has a button to install it."))
        .setPositiveButton(t("Mở trang Tự động", "Open Automation")) { _, _ -> managing.open(host, "auto") }
        .setNegativeButton(t("Đóng", "Close"), null).show()
    return true
}

internal fun MainActivity.setTimer(minutes: Int) {
    rules(JSONObject().put("do", "sleep").put("minutes", minutes)) { r ->
        if (!needsSetup(r)) toast(if (minutes > 0) t("TV sẽ tự tắt lúc ", "The TV turns off at ") + hm(r.optLong("sleep_at"), r.optLong("now")) else t("Đã hủy hẹn giờ tắt", "Sleep timer cancelled"))
    }
}

/** "40" is minutes from now, "23:30" / "23h30" / "23h" a time of day (the next one); 0 = not understood. Same as sleepMinutes in auto.js. */
internal fun sleepMinutes(v: String, now: java.util.Calendar = java.util.Calendar.getInstance()): Int {
    val s = v.trim().lowercase()
    if (s.isNotEmpty() && s.all { it.isDigit() }) return s.toIntOrNull()?.takeIf { it in 1..1440 } ?: 0
    val m = Regex("""^(\d{1,2})\s*[:hg.]\s*(\d{2})?$""").find(s) ?: return 0
    val h = m.groupValues[1].toInt(); val min = m.groupValues[2].ifEmpty { "0" }.toInt()
    if (h > 23 || min > 59) return 0
    val at = (now.clone() as java.util.Calendar).apply { set(java.util.Calendar.HOUR_OF_DAY, h); set(java.util.Calendar.MINUTE, min); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
    if (!at.after(now)) at.add(java.util.Calendar.DATE, 1)
    return maxOf(1, Math.round((at.timeInMillis - now.timeInMillis) / 60000.0).toInt())
}

/** 😴: a few lengths to pick from, and where the timer stands. */
internal fun MainActivity.timerMenu() {
    if (needsAdb()) return
    rules(null) { r ->
        if (needsSetup(r)) return@rules
        val at = r.optLong("sleep_at")
        val items = ArrayList<Triple<String, String, () -> Unit>>()
        for (m in intArrayOf(15, 30, 45, 60, 90, 120)) items.add(Triple(t("Tắt sau ", "Off in ") + (if (m < 60) "$m " + t("phút", "min") else "${m / 60} " + t("giờ", "h") + (if (m % 60 > 0) " 30" else "")), "") { setTimer(m) })
        if (at > 0) {
            items.add(0, Triple("@plus|" + t("Thêm 15 phút", "15 more minutes"), "") { rules(JSONObject().put("do", "sleep_add").put("minutes", 15)) { x -> toast(t("TV sẽ tự tắt lúc ", "The TV turns off at ") + hm(x.optLong("sleep_at"), x.optLong("now"))) } })
            items.add(1, Triple("@close|" + t("Hủy hẹn giờ", "Cancel the timer"), "") { setTimer(0) })
        }
        items.add(Triple("@timer|" + t("Tự chọn...", "Custom..."), t("Số phút, hoặc giờ tắt như 23:30", "Minutes, or a time like 23:30")) {
            askText(t("Tắt TV sau bao lâu?", "Turn the TV off when?"), t("vd 40 (phút) hoặc 23:30", "e.g. 40 (minutes) or 23:30"), t("Hẹn", "Set")) {
                val m = sleepMinutes(it)
                if (m > 0) setTimer(m) else toast(t("Nhập số phút (vd 40) hoặc giờ tắt (vd 23:30)", "Type minutes (e.g. 40) or a time to turn off (e.g. 23:30)"))
            }
        })
        items.add(Triple("@moon|" + t("Giờ đi ngủ, giới hạn app...", "Bedtime, app limits..."), "") { managing.open(host, "auto") })
        menu(if (at > 0) t("TV tự tắt lúc ", "The TV turns off at ") + hm(at, r.optLong("now")) else t("Hẹn giờ tắt TV", "Sleep timer"), if (at > 0) 0 else -1, items)
    }
}

/** 🎬: this phone's scenes, and the way to make more. */
internal fun MainActivity.scenesMenu() {
    val items = SceneStore.list(this).map { sc -> Triple((sc.optString("icon") + "  " + sc.optString("name")).trim(), "") { runScene(sc) } }.toMutableList<Triple<String, String, () -> Unit>>()
    items.add(Triple("@scene|" + t("Tạo / sửa cảnh...", "Make / edit scenes..."), t("Một nút, nhiều việc", "One button, several steps")) {
        if (!needsAdb()) managing.open(host, "auto")
    })
    menu(t("Cảnh", "Scenes"), -1, items)
}

internal fun MainActivity.runScene(sc: JSONObject) {
    val steps = sc.optJSONArray("steps") ?: JSONArray()
    val tv = tv
    val b = basic
    val target = if (tv != null && tv.ready()) Scenes.Shell({ tv.shell(it) }) else if (b != null) Headless.keysOnly(b) else return
    toast(t("Đang chạy: ", "Running: ") + sc.optString("name"))
    Thread {
        val failed = Scenes.run(steps, target)
        ui.post {
            if (isFinishing || (failed != null && offerSetup(java.io.IOException(failed)))) return@post   // e.g. its timer needs a newer TV Setup
            toast(if (failed == null) "✓ " + sc.optString("name") else t("Có bước không làm được: ", "Some steps did not work: ") + failed)
        }
    }.start()
}

// ---- what the 🎤 hears
private val OPEN_APP = Regex("^(mo|bat|vao|chay|xem|open|launch|start|play) ")

/** Carries out a sentence here when it can (Voice.kt); otherwise the TV's search / Assistant gets it, as before. */
internal fun MainActivity.voice(sentence: String) {
    val scenes = SceneStore.list(this).associate { it.optString("id") to it.optString("name") }
    Voice.parse(sentence, appNames, scenes)?.let { doVoice(it); return }
    val tv = tv
    val ip = host
    if (appNamesFor != ip && ip != null && tv != null && tv.ready() && OPEN_APP.containsMatchIn(Voice.fold(sentence))) {
        Thread {                                                      // "open <app>": the names of this TV's apps, asked once per TV
            val link = AdbLink(filesDir, ip)
            val names = try { RemoteLabels(link, { assets.open("tvr-helper.jar").use { it.readBytes() } }).names() } catch (e: Exception) { emptyMap() } finally { link.close() }
            ui.post {
                if (isFinishing || host != ip) return@post
                appNames = names; appNamesFor = ip
                Voice.parse(sentence, names, scenes)?.let { doVoice(it) } ?: ask(sentence)
            }
        }.start()
        return
    }
    ask(sentence)
}

private fun MainActivity.doVoice(c: Voice.Command) {
    when (c) {
        is Voice.Command.Scene -> SceneStore.find(this, c.id)?.let { runScene(it) }
        is Voice.Command.Timer -> if (!needsAdb()) setTimer(c.minutes)
        is Voice.Command.App -> {
            toast(t("Đang mở ", "Opening ") + c.name)
            val tv = tv
            if (tv != null && tv.ready()) Thread { try { Scenes.launch({ tv.shell(it) }, c.pkg) } catch (e: Exception) { ui.post { toast(e.message ?: "") } } }.start()
            else basic?.launch(c.pkg)
        }
        is Voice.Command.Key -> {
            val code = Scenes.KEYS[c.key] ?: return
            for (i in 0 until c.times) ui.postDelayed({ press(code) }, i * 150L)
            toast(t("Đã bấm: ", "Pressed: ") + c.key.lowercase().replace('_', ' ') + if (c.times > 1) " × ${c.times}" else "")
        }
    }
}
