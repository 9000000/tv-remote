package dev.tvremote.phone

import dev.tvremote.core.T
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Typeface
import android.hardware.ConsumerIrManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.util.concurrent.Executors

// The infrared remote: the phone's IR LED (ConsumerIrManager) sends a TV's or box's own remote codes (Ir.kt, IrCodes.kt, assets/ir/codes.json).
// It needs no network and no picked TV, so it is reached from the list of TVs: a TV that lost its Wi-Fi still has its IR window.
// Phone app only (the Computer app and the TV web page have no IR LED). A debug build without an LED logs the pattern ("TvrIr") instead.

private const val TAG = "TvrIr"
private const val PROFILE = "ir:profile"                           // the chosen profile's id; "ir:profile:<ip>" for a saved TV
private const val TARGET = "ir:tv"                                  // the saved TV the codes are for, "" for none
private val REPEAT = setOf("UP", "DOWN", "LEFT", "RIGHT", "VOL_UP", "VOL_DOWN", "CH_UP", "CH_DOWN")   // held: the code goes on being sent

private object IrState {
    var catalog: List<IrProfile>? = null
    var toggle = false                                              // RC5 / RC6: the bit that flips on every new press
    var onFind = false                                              // which of the two screens the back button leaves
    var page = ""                                                   // "auto" while Dò tự động is on screen: its steps redraw it
    @Volatile var stop = false
    @Volatile var link: dev.tvremote.core.adb.AdbLink? = null
    var auto = ""                                                   // the TV Dò tự động listens on
    val io = Executors.newSingleThreadExecutor { r -> Thread(r, "ir").apply { isDaemon = true } }
}

internal fun MainActivity.irCatalog(): List<IrProfile> = IrState.catalog ?: try {
    IrCodes.parse(assets.open("ir/codes.json").bufferedReader().use { it.readText() })
} catch (e: Exception) { emptyList() }.also { IrState.catalog = it }

private fun MainActivity.irManager(): ConsumerIrManager? = try { getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager } catch (e: Exception) { null }

/** The entry is shown only on a phone with an IR LED; a debug build shows it anyway, to be looked at on an emulator. */
internal fun MainActivity.irAvailable(): Boolean =
    irManager()?.hasIrEmitter() == true || applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

/** The carrier the LED can send that is nearest to [hz]: some drivers refuse a frequency outside their ranges. */
private fun nearest(m: ConsumerIrManager, hz: Int): Int {
    val r = try { m.carrierFrequencies } catch (e: Exception) { null } ?: return hz
    return r.minByOrNull { if (hz in it.minFrequency..it.maxFrequency) 0 else minOf(Math.abs(hz - it.minFrequency), Math.abs(hz - it.maxFrequency)) }
        ?.let { hz.coerceIn(it.minFrequency, it.maxFrequency) } ?: hz
}

private fun MainActivity.irSend(code: String, repeat: Boolean) {
    if (!repeat) IrState.toggle = !IrState.toggle
    val s = Ir.encode(code, IrState.toggle) ?: return
    val m = irManager()
    if (m?.hasIrEmitter() != true) { Log.i(TAG, "send $code hz=${s.hz} us=${s.micros} pattern=${s.pattern.joinToString(",")}"); return }
    IrState.io.execute {
        try { m.transmit(nearest(m, s.hz), s.pattern) } catch (e: Exception) {
            Log.w(TAG, "transmit failed", e)
            ui.post { if (!isFinishing) Toast.makeText(this, T("ir.failed"), Toast.LENGTH_SHORT).show() }
        }
    }
}

/** The saved TV the codes are for: each keeps its own remote, and one ADB reaches can find its code by itself (IrAuto.kt). */
private fun MainActivity.irTarget(): String = pref(TARGET).takeIf { it in savedIps() } ?: ""
private fun MainActivity.irProfileKey(): String = irTarget().let { if (it.isEmpty()) PROFILE else "$PROFILE:$it" }
private fun MainActivity.irProfile(): IrProfile? = pref(irProfileKey()).ifEmpty { pref(PROFILE) }.let { id -> irCatalog().firstOrNull { it.id == id } }
private fun MainActivity.irSave(p: IrProfile) = prefs.edit().putString(irProfileKey(), p.id).apply()
private fun MainActivity.irTvName(ip: String) = pref("name:$ip").ifEmpty { ip }

// ---------------------------------------------------------------- the screen
internal fun MainActivity.buildIr(): FrameLayout {
    val host = FrameLayout(this)
    host.visibility = View.GONE
    return host
}

/** [tv]: the saved TV it is opened for (from that TV's remote: its own code), null from the list of TVs (the phone's one code). */
internal fun MainActivity.irOpen(tv: String? = null) {
    prefs.edit().putString(TARGET, tv ?: "").apply()
    irRemote(); irHost.visibility = View.VISIBLE
}

/** The back button: from the code finder to the remote, from the remote to the list of TVs. True when it was ours. */
internal fun MainActivity.irBack(): Boolean {
    if (irHost.visibility != View.VISIBLE) return false
    if (IrState.onFind) { irStop(); irRemote() } else irHost.visibility = View.GONE
    return true
}

private fun MainActivity.irPage(title: String, back: () -> Unit, body: LinearLayout.() -> Unit) {
    val box = col()
    box.setPadding(dp(16), dp(20), dp(16), dp(24))
    val head = label(title, 24, TEXT)
    head.typeface = Typeface.DEFAULT_BOLD
    val top = LinearLayout(this)
    top.gravity = Gravity.CENTER_VERTICAL
    top.addView(head, LinearLayout.LayoutParams(0, -2, 1f))
    val done = icon("@check", T("drawing.done"), ACCENT)
    done.setOnClickListener { back() }
    top.addView(done, LinearLayout.LayoutParams(dp(52), dp(44)))
    box.addView(top)
    box.body()
    val sv = ScrollView(this)
    sv.setBackgroundColor(BG)
    sv.isClickable = true                                           // touches do not fall through to the list of TVs below
    sv.addView(box)
    irHost.removeAllViews()
    irHost.addView(sv, FrameLayout.LayoutParams(-1, -1))
}

internal fun MainActivity.irRemote() {
    IrState.onFind = false
    IrState.page = ""
    val p = irProfile()
    irPage(T("ir.title"), { irHost.visibility = View.GONE }) {
        val line = LinearLayout(this@irRemote)
        val pick = button("@tv|" + (p?.title ?: T("ir.choose_brand")), BTN)
        pick.setOnClickListener { irBrands() }
        line.addView(pick, LinearLayout.LayoutParams(0, dp(48), 1f))
        val find = button("@search|" + T("ir.find"), SOFT)
        find.setInk(ACCENT_TEXT)
        find.setOnClickListener { irFindStart() }
        line.addView(find, LinearLayout.LayoutParams(-2, dp(48)).apply { leftMargin = dp(8) })
        addView(line, gap(14))
        addView(note(if (p == null) T("ir.pick_first") else T("ir.aim")))
        if (p != null) irKeys(this, p)
    }
}

/** Dò mã: by itself on a TV ADB reaches (the one this remote is for, else the one picked), by hand on any other. */
private fun MainActivity.irFindStart() {
    val android = { ip: String -> kindOf(ip) == dev.tvremote.core.remote.Remote.Kind.ANDROID }
    val t = irTarget()
    if (t.isNotEmpty()) { if (android(t)) { IrState.auto = t; irAuto() } else irFind(); return }
    val tvs = savedIps().filter(android)
    if (tvs.isEmpty()) { irFind(); return }
    menu(T("ir.find_on"), -1, tvs.map { ip -> Triple("@tv|" + irTvName(ip), T("ir.tv_sub")) { IrState.auto = ip; irAuto() } } +
        Triple("@search|" + T("ir.other_tv"), T("ir.other_tv_sub")) { irFind() })
}

private fun MainActivity.irBrands() {
    val all = irCatalog()
    val now = irProfile()?.id
    val items = all.map { it.brand }.distinct().map { b ->
        val of = all.filter { it.brand == b }
        Triple("@tv|" + b, T("ir.n_remotes", "n" to of.size) + (if (of.any { it.id == now }) "  ✓" else "")) { irProfiles(b, of) }
    }
    menu(T("ir.choose_brand"), -1, items)
}

private fun MainActivity.irProfiles(brand: String, of: List<IrProfile>) {
    val now = irProfile()?.id
    menu(brand, -1, of.map { p -> Triple("@tv|" + p.name + (if (p.id == now) "  ✓" else ""), "") { irSave(p); irRemote() } })
}

/** [bind]: a key of [p] that is there answers the finger (held: it repeats); one that is not is greyed, as the page greys a key the TV lacks. */
private fun MainActivity.irBind(b: Button, p: IrProfile, key: String, held: android.graphics.drawable.Drawable) {
    val code = p.keys[key]
    if (code == null) { b.alpha = 0.3f; b.isEnabled = false; return }
    val normal = b.background
    val again = object : Runnable { override fun run() { irSend(code, true); ui.postDelayed(this, 140) } }
    val first = Runnable { ui.post(again) }
    b.setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.background = held
                buzz()
                irSend(code, false)
                if (key in REPEAT) ui.postDelayed(first, 400)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { ui.removeCallbacks(first); ui.removeCallbacks(again); v.background = normal }
        }
        true
    }
}

private fun MainActivity.irButton(p: IrProfile, key: String, symbol: String, say: String): Button {
    val b = icon(symbol, say, BTN)
    irBind(b, p, key, box(HELD, 16))
    return b
}

private fun MainActivity.irRow(p: IrProfile, h: Int, vararg keys: Triple<String, String, String>): LinearLayout {
    val row = LinearLayout(this)
    keys.forEachIndexed { i, (k, sym, say) -> row.addView(irButton(p, k, sym, say), cell(h, i > 0)) }
    return row
}

private fun MainActivity.irKeys(box: LinearLayout, p: IrProfile) {
    fun k(key: String, sym: String, say: String) = Triple(key, sym, say)
    box.addView(irRow(p, 52, k("POWER", "@power", T("remote.power")), k("INPUT", "@input", T("rkey.input")), k("MUTE", "@mute", T("rkey.mute"))), gap(16))
    box.addView(irDpad(p), gap(14))
    box.addView(irRow(p, 52, k("BACK", "@back", T("rkey.back")), k("HOME", "@home", T("rkey.home")), k("MENU", "@menu", T("rkey.menu"))), gap(14))
    val pills = LinearLayout(this)
    pills.addView(irPill(p, T("remote.ch_2"), Triple("CH_UP", "@up", T("remote.ch")), Triple("CH_DOWN", "@down", T("remote.ch_3"))), LinearLayout.LayoutParams(0, dp(124), 1f).apply { rightMargin = dp(8) })
    pills.addView(irPill(p, "VOL", Triple("VOL_UP", "@plus", T("remote.volume_up")), Triple("VOL_DOWN", "@minus", T("remote.volume_down"))), LinearLayout.LayoutParams(0, dp(124), 1f).apply { leftMargin = dp(8) })
    box.addView(pills, gap(14))
    if (p.keys.keys.any { it in "0".."9" }) {
        for (r in listOf("123", "456", "789", " 0 ")) {
            val row = LinearLayout(this)
            r.forEachIndexed { i, c ->
                val b = if (c == ' ') View(this) else button(c.toString(), BTN).also { it.textSize = 18f; irBind(it, p, c.toString(), box(HELD, 14)) }
                row.addView(b, cell(48, i > 0))
            }
            box.addView(row, gap(if (r == "123") 14 else 6))
        }
    }
    val media = listOf(k("REWIND", "@rewind", T("rkey.rewind")), k("PLAY_PAUSE", "@play_pause", T("rkey.playpause")), k("FFWD", "@ffwd", T("rkey.forward")), k("STOP", "@stop", T("rkey.stop")))
    val more = listOf(k("SETTINGS", "@gear", T("rkey.settings")), k("INFO", "@info", T("rkey.info")), k("GUIDE", "@guide", T("rkey.guide")), k("SUBTITLE", "@subtitle", T("rkey.subtitles")))
    for (keys in listOf(media, more)) if (keys.any { it.first in p.keys }) box.addView(irRow(p, 48, *keys.toTypedArray()), gap(14))
}

private fun MainActivity.irPill(p: IrProfile, name: String, up: Triple<String, String, String>, down: Triple<String, String, String>): LinearLayout {
    val pill = col()
    pill.background = box(BTN, 26)
    val a = icon(up.second, up.third, 0)
    irBind(a, p, up.first, box(HELD, 26))
    val b = icon(down.second, down.third, 0)
    irBind(b, p, down.first, box(HELD, 26))
    val l = label(name, 13, MUTED)
    l.gravity = Gravity.CENTER
    l.typeface = Typeface.DEFAULT_BOLD
    pill.addView(a, LinearLayout.LayoutParams(-1, 0, 1f))
    pill.addView(l, LinearLayout.LayoutParams(-1, -2))
    pill.addView(b, LinearLayout.LayoutParams(-1, 0, 1f))
    return pill
}

/** The round pad of the native remote (RemoteScreen.kt dpad), sending IR codes. */
private fun MainActivity.irDpad(p: IrProfile): View {
    val pad = FrameLayout(this)
    pad.background = oval(CARD)
    val arrows = listOf(Triple("UP", "@up", Gravity.TOP or Gravity.CENTER_HORIZONTAL), Triple("DOWN", "@down", Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL),
        Triple("LEFT", "@left", Gravity.START or Gravity.CENTER_VERTICAL), Triple("RIGHT", "@right", Gravity.END or Gravity.CENTER_VERTICAL))
    val says = mapOf("UP" to T("remote.up"), "DOWN" to T("remote.down"), "LEFT" to T("remote.left"), "RIGHT" to T("remote.right"))
    for ((key, sym, gravity) in arrows) {
        val b = icon(sym, says.getValue(key), 0)
        b.setIcon(sym, MUTED)
        b.size = 30f
        b.background = oval(0)
        irBind(b, p, key, oval(HELD))
        pad.addView(b, FrameLayout.LayoutParams(dp(80), dp(80), gravity).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
    }
    val ok = button("OK", ACCENT)
    ok.typeface = Typeface.DEFAULT_BOLD
    ok.textSize = 18f
    ok.setPadding(0, 0, 0, 0)
    ok.background = oval(ACCENT)
    ok.elevation = dp(6).toFloat()
    irBind(ok, p, "OK", oval(HELD))
    pad.addView(ok, FrameLayout.LayoutParams(dp(92), dp(92), Gravity.CENTER))
    val wrap = LinearLayout(this)
    wrap.gravity = Gravity.CENTER
    wrap.addView(pad, LinearLayout.LayoutParams(dp(248), dp(248)))
    return wrap
}

// ---------------------------------------------------------------- Dò mã: a device that is in no list
/**
 * Sends one key's code of every different remote in turn, one tap at a time; the one the device answers to is kept. Remotes that share
 * that code count as one step (a hundred Samsung models send the same power code).
 */
private fun MainActivity.irFind(key0: String = "POWER", at0: Int = -1) {
    IrState.onFind = true
    IrState.page = ""
    var key = key0
    var idx = at0
    val steps = IrCodes.distinct(irCatalog(), key)
    irPage(T("ir.find"), { irRemote() }) {
        val chips = LinearLayout(this@irFind)
        chips.isBaselineAligned = false                               // a two-line chip pulled itself down
        val names = listOf("POWER" to T("remote.power"), "HOME" to T("rkey.home"), "VOL_UP" to T("ir.vol"), "MUTE" to T("rkey.mute"))
        names.forEachIndexed { i, (k, words) ->
            val c = button(words, BTN)
            c.textSize = 13f
            c.maxLines = 1; c.ellipsize = android.text.TextUtils.TruncateAt.END   // four in a row: "Tắt tiếng" went to two lines
            c.setPadding(dp(4), 0, dp(4), 0)
            c.setOn(k == key)
            c.setOnClickListener { if (k != key) irFind(k, -1) }
            chips.addView(c, cell(40, i > 0))
        }
        addView(note(T("ir.find_key")), gap(10))
        addView(chips, gap(6))
        addView(note(T("ir.find_hint")), gap(8))
        val card = col()
        card.background = box(CARD, 14)
        card.setPadding(dp(16), dp(14), dp(16), dp(14))
        val step = label(if (idx < 0) T("ir.ready") else T("ir.step", "n" to idx + 1, "total" to steps.size), 20, TEXT)
        step.typeface = Typeface.DEFAULT_BOLD
        card.addView(step)
        if (idx in steps.indices) {
            val (p, same) = steps[idx]
            card.addView(label(p.title + (if (same > 1) "  " + T("ir.same", "n" to same - 1) else ""), 14, MUTED), gap(4))
        }
        addView(card, gap(14))
        val row = LinearLayout(this@irFind)
        val again = button(T("ir.again"), BTN)
        val next = button(if (idx < 0) T("ir.send") else T("ir.next"), BTN)
        again.isEnabled = idx in steps.indices; again.alpha = if (again.isEnabled) 1f else 0.4f
        again.setOnClickListener { steps.getOrNull(idx)?.let { s -> buzz(); irSend(s.first.keys.getValue(key), false) } }
        next.setOnClickListener {
            buzz()
            if (idx + 1 >= steps.size) { Toast.makeText(this@irFind, T("ir.end"), Toast.LENGTH_LONG).show(); return@setOnClickListener }
            irFind(key, idx + 1)
            steps[idx + 1].let { s -> irSend(s.first.keys.getValue(key), false) }
        }
        row.addView(again, cell(52, false))
        row.addView(next, cell(52, true))
        addView(row, gap(14))
        val ok = button(T("ir.reacted"), ACCENT)
        ok.isEnabled = idx in steps.indices; ok.alpha = if (ok.isEnabled) 1f else 0.4f
        ok.setOnClickListener {
            val s = steps[idx].first
            irSave(s)
            Toast.makeText(this@irFind, T("ir.found", "name" to s.title), Toast.LENGTH_LONG).show()
            irRemote()
        }
        addView(ok, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(10) })
    }
}

// ---------------------------------------------------------------- Dò tự động: the TV says over ADB when a code arrives (IrAuto.kt)
private const val AUTO_KEY = "VOL_UP"                                // heard by every TV, harmless, undone with VOL_DOWN; never POWER (ADB would go)

private fun MainActivity.irStop() {
    IrState.stop = true
    IrState.link?.let { l -> Thread { l.close() }.start() }       // a TV still asking "Allow USB debugging?" lets go too
}

/** The page: [say] what is going on, [sub] the line under it, [found] the match, [running] while the codes go out. */
private fun MainActivity.irAuto(say: String = "", sub: String = "", found: IrProfile? = null, running: Boolean = false) {
    IrState.onFind = true
    IrState.page = "auto"
    val name = irTvName(IrState.auto)
    irPage(T("ir.find"), { irStop(); irRemote() }) {
        addView(note(T("ir.auto_hint", "name" to name)), gap(10))
        val card = col()
        card.background = box(CARD, 14)
        card.setPadding(dp(16), dp(14), dp(16), dp(14))
        val big = label(if (found != null) T("ir.auto_found", "name" to found.title) else say.ifEmpty { T("ir.ready") }, 20, TEXT)
        big.typeface = Typeface.DEFAULT_BOLD
        card.addView(big)
        if (sub.isNotEmpty()) card.addView(label(sub, 14, MUTED), gap(4))
        if (running) card.addView(android.widget.ProgressBar(this@irAuto, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true; progressTintList = android.content.res.ColorStateList.valueOf(ACCENT) }, gap(8))
        addView(card, gap(14))
        val main = when {
            found != null -> button(T("ir.use"), ACCENT).apply { setOnClickListener {
                irSave(found); prefs.edit().putString("$PROFILE:${IrState.auto}", found.id).apply(); Toast.makeText(this@irAuto, T("ir.found", "name" to found.title), Toast.LENGTH_LONG).show(); irRemote() } }
            running -> button(T("ir.stop"), BTN).apply { setOnClickListener { irStop(); irAuto() } }
            else -> button(if (say.isEmpty()) T("ir.auto_start") else T("ir.retry"), ACCENT).apply { setOnClickListener { irAutoRun() } }
        }
        addView(main, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(4) })
        val hand = button("@search|" + T("ir.by_hand"), SOFT)
        hand.setInk(ACCENT_TEXT)
        hand.setOnClickListener { irStop(); irFind() }
        addView(hand, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
    }
}

private fun MainActivity.irAutoRun() {
    val ip = IrState.auto
    val name = irTvName(ip)
    val steps = IrCodes.distinct(irCatalog(), AUTO_KEY)
    IrState.stop = false
    irAuto(T("ir.auto_connecting", "name" to name), running = true)
    // each step redraws the page, only while it is still the one shown
    val show: (() -> Unit) -> Unit = { f -> ui.post { if (!isFinishing && IrState.page == "auto" && irHost.visibility == View.VISIBLE) f() } }
    Thread({
        var hear: IrHear? = null
        try {
            var adb: dev.tvremote.core.adb.Adb? = null
            for (h in listOf(ip, pref("ts:$ip"), pref("lan:$ip")).filter { it.isNotEmpty() }.distinct()) {
                if (IrState.stop) return@Thread
                val l = dev.tvremote.core.adb.AdbLink(filesDir, h)
                IrState.link = l
                adb = try { l.get() } catch (e: Exception) { l.close(); null }
                if (adb != null) break
            }
            if (IrState.stop) return@Thread
            if (adb == null) { show { irAuto(T("ir.auto_noadb", "name" to name), T("ir.auto_noadb_sub")) }; return@Thread }
            val ear = IrHear(adb).also { hear = it }
            Thread.sleep(600)                                        // getevent lists the devices first
            val hit = irScan(steps, { it.first.keys.getValue(AUTO_KEY) }, { irSend(it, false) }, { ear.last }, { IrState.stop },
                { i -> show { irAuto(T("ir.auto_trying", "n" to i + 1, "total" to steps.size), steps[i].first.title, running = true) } })
            if (IrState.stop) return@Thread
            if (hit == null) { show { irAuto(T("ir.auto_none"), T("ir.auto_none_sub")) }; return@Thread }
            hit.first.keys["VOL_DOWN"]?.let { down -> repeat(2) { Thread.sleep(250); irSend(down, false) } }   // the two VOL_UP it heard
            show { irAuto(found = hit.first) }
        } catch (e: Exception) {
            Log.w(TAG, "auto find", e)
            if (!IrState.stop) show { irAuto(T("ir.auto_noadb", "name" to name), T("ir.auto_noadb_sub")) }
        } finally {
            hear?.close()
            IrState.link?.close(); IrState.link = null
        }
    }, "ir-auto").start()
}
