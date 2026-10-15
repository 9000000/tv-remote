package dev.tvremote.phone

import android.content.Intent
import android.widget.LinearLayout
import dev.tvremote.core.RemoteSpec
import dev.tvremote.core.Scenes
import dev.tvremote.core.Tv

// The remote's keys and menu entries are in app/shared/web/remote.json, which the web remote draws from too (js/remote_spec.js):
// this file draws them as native views and says what each action name in the file does on the phone. Add, reorder or rename an
// entry in the file; a new action name needs a handler in [phoneActions] here and in REMOTE_DO there (tests/remote.test.js and
// RemoteSpecTest check both).

private var loaded: RemoteSpec.Spec? = null

/** remote.json for this app (it is in the app's assets next to the web page's files), read once. */
internal fun MainActivity.remoteSpec(): RemoteSpec.Spec =
    loaded ?: RemoteSpec.parse(assets.open("remote.json").use { String(it.readBytes(), Charsets.UTF_8) }, "phone").also { loaded = it }

private fun act(f: MainActivity.(String?) -> Unit) = f
private fun hook(f: MainActivity.(IconButton) -> Unit) = f

/** What each "do" in remote.json does here; `it` is the entry's "arg". */
internal val phoneActions: Map<String, MainActivity.(String?) -> Unit> = mapOf(
    "shot" to act { shot() },
    "open" to act { quickOpen() },
    "timer" to act { timerMenu() },
    "scenes" to act { scenesMenu() },
    "draw" to act { if (drawing) stopDrawing() else startDrawing() },
    "search" to act { askText(t("Tìm app trên CH Play", "Search the Play Store"), t("vd: vlc", "e.g. vlc"), t("Tìm", "Search")) { openLink("market://search?c=apps&q=" + java.net.URLEncoder.encode(it, "UTF-8")) } },   // the TV's Play Store takes searches only this way
    "package" to act { askText(t("App id (tên gói)", "App id (package name)"), "org.videolan.vlc", t("Mở", "Open")) { storePage(it) } },
    "link" to act { askText(t("Đường dẫn", "Link"), "https://youtu.be/...", t("Mở", "Open")) { openLink(if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(it)) it else "https://$it") } },
    "media" to act { pickMedia() },
    "file" to act { pickFile(arrayOf("audio/*", "video/*", "image/*")) },
    "cast" to act {
        try { startActivity(Intent("android.settings.CAST_SETTINGS")) }
        catch (e: Exception) { say(t("Điện thoại này không có phần Truyền (Cast) trong Cài đặt. Thử nút Truyền / Smart View ở thanh thông báo.", "This phone has no Cast screen in Settings. Try the Cast / Smart View tile in the notification shade."), true) }
    },
    "store" to act { storePage(it ?: "") },
    "timerPlus" to act { timerPlus() },
    "timerCancel" to act { setTimer(0) },
    "timerCustom" to act { timerCustom() },
    "automation" to act { if (!needsAdb()) managing.open(host, "auto") },
)

/** Things an action's button needs to know about itself once drawn (the ✏️ is lit while drawing). */
internal val phoneButtonHooks: Map<String, MainActivity.(IconButton) -> Unit> = mapOf(
    "draw" to hook { drawBtn = it; drawLit() },
)

internal fun MainActivity.storePage(id: String) = openLink("https://play.google.com/store/apps/details?id=$id")

/** A row of the remote's keys from remote.json: one icon button each, the key held / tapped like the hand-made ones, or the action run. */
internal fun MainActivity.remoteRow(name: String, index: Int, height: Int): LinearLayout {
    val row = LinearLayout(this)
    remoteSpec().rows[name]!![index].forEachIndexed { i, e ->
        val b = icon("@" + e.icon, t(e.vi, e.en), BTN)
        val action = e.action
        if (action != null) {
            val run = phoneActions[action] ?: error("remote.json: no phone handler for \"$action\"")
            b.setOnClickListener { run(this, e.arg) }
            phoneButtonHooks[action]?.invoke(this, b)
        } else {
            val code = Scenes.KEYS[e.key] ?: error("remote.json: unknown key ${e.key}")
            if (code in Tv.OPENERS) {
                val held = box(HELD, 16)
                b.setOnClickListener { flash(b, held); opens(code) }
            } else bindKey(b, code, box(HELD, 16))
        }
        row.addView(b, cell(height, i > 0))
    }
    return captioned(row)
}

/** A menu from remote.json for [menu]: [conditions] says which "when" entries show, [lists] stands in for each "expand" entry. */
internal fun MainActivity.remoteMenu(name: String, conditions: Set<String> = emptySet(), lists: Map<String, () -> List<Triple<String, String, () -> Unit>>> = emptyMap()): List<Triple<String, String, () -> Unit>> {
    val items = ArrayList<Triple<String, String, () -> Unit>>()
    for (e in remoteSpec().menus[name]!!) {
        if (e.whenCond != null && e.whenCond !in conditions) continue
        if (e.expand != null) { items.addAll(lists[e.expand]!!()); continue }
        val run = phoneActions[e.action!!] ?: error("remote.json: no phone handler for \"${e.action}\"")
        items.add(Triple("@" + e.icon + "|" + t(e.vi, e.en), t(e.subVi, e.subEn)) { run(this, e.arg) })
    }
    return items
}
