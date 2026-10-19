package dev.tvremote.phone

import dev.tvremote.core.T
import android.content.Intent
import android.widget.LinearLayout
import dev.tvremote.core.remote.Remote
import dev.tvremote.core.scenes.RemoteSpec
import dev.tvremote.core.scenes.Scenes
import dev.tvremote.core.picture.Tv

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
/** The groups an entry opens ("do": "menu", "arg": its name): their titles (REMOTE_GROUPS on the page). */
private val remoteGroups = mapOf("get" to "rkey.open_get", "send" to "rkey.open_send")

internal val phoneActions: Map<String, MainActivity.(String?) -> Unit> = mapOf(
    "shot" to act { shot() },
    "open" to act { quickOpen() },
    "apps" to act { if (kind() != dev.tvremote.core.remote.Remote.Kind.ANDROID) openInstalled() else if (!needsAdb()) managing.open(host, "apps") },   // an Android TV's apps: the Apps tab, tiles with a search
    "menu" to act { m -> menu(T(remoteGroups[m] ?: return@act), -1, remoteMenu(m!!)) },
    "timer" to act { timerMenu() },
    "scenes" to act { scenesMenu() },
    "notify" to act { notifyDialog() },
    "draw" to act { if (drawing) stopDrawing() else startDrawing() },
    "search" to act { askText(T("remote.search_play_store"), T("phone_remote.e_g_vlc"), T("remote.search")) { openLink("market://search?c=apps&q=" + java.net.URLEncoder.encode(it, "UTF-8")) } },   // the TV's Play Store takes searches only this way
    "package" to act { askText(T("remote.package_id"), "org.videolan.vlc", T("apps.open")) { storePage(it) } },
    "link" to act { askText(T("remote.link"), "https://youtu.be/...", T("apps.open")) { openLink(if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(it)) it else "https://$it") } },
    "media" to act { pickMedia() },
    "file" to act { pickFile(arrayOf("audio/*", "video/*", "image/*")) },
    "cast" to act {
        try { startActivity(Intent("android.settings.CAST_SETTINGS")) }
        catch (e: Exception) { say(T("phone_remote.this_phone_has_no"), true) }
    },
    "store" to act { storePage(it ?: "") },
    "timerPlus" to act { timerPlus() },
    "timerCancel" to act { setTimer(0) },
    "timerCustom" to act { timerCustom() },
    "automation" to act { if (!needsAdb()) managing.open(host, "auto") },
    "clip" to act { clipDialog() },
    "screenOff" to act { screenOff() },
    "dim" to act { dimDialog() },
    "share" to act { shareDialog() },
    "inputs" to act { inputsMenu() },
)

/** Things an action's button needs to know about itself once drawn (the ✏️ is lit while drawing). */
internal val phoneButtonHooks: Map<String, MainActivity.(IconButton) -> Unit> = mapOf(
    "draw" to hook { drawBtn = it; drawLit() },
    "shot" to hook { shotBtn = it },
)

internal fun MainActivity.storePage(id: String) = openLink("https://play.google.com/store/apps/details?id=$id")

/** What the connected TV can do: an LG or Samsung TV lacks ADB, the Play Store and some keys (Remote.Kind). */
internal fun MainActivity.kind(): String = basic?.kind ?: Remote.Kind.ANDROID
internal fun MainActivity.caps(): Set<String> = Remote.Kind.caps(kind())

/** The buttons drawn from remote.json, to grey those this TV cannot do ([greyRows], from layoutRemote). */
private val drawn = ArrayList<Pair<IconButton, RemoteSpec.Entry>>()

/** The tag of a button this TV cannot do: greyed, not disabled, so a press can tell what it would need. */
internal const val KIND_OFF = "kind-off"

internal fun MainActivity.kindOff() =
    android.widget.Toast.makeText(this, T("core.only_android"), android.widget.Toast.LENGTH_SHORT).show()

internal fun MainActivity.greyRows() {
    val caps = caps()
    val keys = basic?.takeIf { kind() != Remote.Kind.ANDROID }?.keys
    for ((b, e) in drawn) {
        val ok = e.can(caps) && (keys == null || e.key == null || Scenes.KEYS[e.key] in keys)
        b.tag = if (ok) null else KIND_OFF                            // still pressable: a press says why (kindOff)
        b.alpha = if (ok) 1f else 0.3f
    }
}

/** A row of the remote's keys from remote.json: one icon button each, the key held / tapped like the hand-made ones, or the action run. */
internal fun MainActivity.remoteRow(name: String, index: Int, height: Int, minKeys: Int = 4): LinearLayout {
    val row = LinearLayout(this)
    row.weightSum = maxOf(minKeys.toFloat(), remoteSpec().rows[name]!![index].size.toFloat())   // a short row keeps the others' key width
    remoteSpec().rows[name]!![index].forEachIndexed { i, e ->
        val b = icon("@" + e.icon, e.words(), BTN)
        val action = e.action
        if (action != null) {
            val run = phoneActions[action] ?: error("remote.json: no phone handler for \"$action\"")
            b.setOnClickListener { if (b.tag === KIND_OFF) kindOff() else run(this, e.arg) }
            phoneButtonHooks[action]?.invoke(this, b)
        } else {
            val code = Scenes.KEYS[e.key] ?: error("remote.json: unknown key ${e.key}")
            if (code in Tv.OPENERS) {
                val held = box(HELD, 16)
                b.setOnClickListener { if (b.tag === KIND_OFF) kindOff() else { flash(b, held); opens(code) } }
            } else bindKey(b, code, box(HELD, 16))
        }
        row.addView(b, cell(height, i > 0))
        drawn.removeAll { it.second.id == e.id }; drawn.add(b to e)
    }
    for (i in row.childCount until minKeys) row.addView(android.view.View(this), cell(height, true))   // empty cells: their gaps too, or the short row's keys come out wider
    return captioned(row)
}

/** A menu from remote.json for [menu]: [conditions] says which "when" entries show, [lists] stands in for each "expand" entry. */
internal fun MainActivity.remoteMenu(name: String, conditions: Set<String> = emptySet(), lists: Map<String, () -> List<Triple<String, String, () -> Unit>>> = emptyMap()): List<Triple<String, String, () -> Unit>> {
    val items = ArrayList<Triple<String, String, () -> Unit>>()
    val caps = caps()
    for (e in remoteSpec().menus[name]!!) {
        if (e.whenCond != null && e.whenCond !in conditions || !e.can(caps)) continue   // what this TV cannot do is left out
        if (e.expand != null) { lists[e.expand]?.let { items.addAll(it()) }; continue }
        if (e.action == "menu" && remoteMenu(e.arg ?: "").isEmpty()) continue   // a group with nothing in it on this TV
        val run = phoneActions[e.action!!] ?: error("remote.json: no phone handler for \"${e.action}\"")
        items.add(Triple("@" + e.icon + "|" + e.words(), e.subWords()) { run(this, e.arg) })
    }
    return items
}
