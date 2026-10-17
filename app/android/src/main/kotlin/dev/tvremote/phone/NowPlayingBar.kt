package dev.tvremote.phone

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import dev.tvremote.core.ontv.NowPlaying
import dev.tvremote.core.remote.Remote
import dev.tvremote.core.ontv.ScreenOff
import dev.tvremote.core.picture.Tv
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * The now playing strip above the remote's keys, the same as the web page's (js/playing.js): what the TV plays, from its media
 * session (NowPlaying, tvr-helper's Playing on the TV), with ⏮ ⏯ ⏭ and a bar that seeks. Asked once a second while the remote
 * is on screen with ADB; hidden when nothing plays.
 */
internal class PlayingBar(private val a: MainActivity) {
    val view = a.col()
    private val title = a.label("", 15, TEXT)
    private val sub = a.label("", 12, MUTED)
    private val play = a.icon("@pause", t("Phát / Dừng", "Play / Pause"), ACCENT)
    private val seek = SeekBar(a)
    private val pos = a.label("0:00", 12, MUTED)
    private val dur = a.label("", 12, MUTED)
    private val times = LinearLayout(a)
    private val work = Executors.newSingleThreadExecutor()            // NowPlaying starts its helper over ADB: never on the UI thread
    private var np: NowPlaying? = null
    private var npFor: Tv? = null
    private var o = JSONObject().put("state", "none")
    private var at = 0L                                               // when [o] came
    private var dragging = false
    private var asking = false
    private val spins = HashMap<android.view.View, android.widget.ProgressBar>()

    init {
        view.background = a.box(CARD, 16)
        view.setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(6))
        view.visibility = View.GONE
        val head = LinearLayout(a)
        head.gravity = Gravity.CENTER_VERTICAL
        val words = a.col()
        for (w in listOf(title, sub)) { w.maxLines = 1; w.ellipsize = android.text.TextUtils.TruncateAt.END; words.addView(w) }
        title.typeface = android.graphics.Typeface.DEFAULT_BOLD
        head.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        val prev = a.icon("@prev", t("Bài trước", "Previous"), BTN)
        val next = a.icon("@next", t("Bài sau", "Next"), BTN)
        prev.setOnClickListener { send("prev", 0, prev) }
        next.setOnClickListener { send("next", 0, next) }
        play.setOnClickListener { send(if (o.optString("state") == "playing") "pause" else "play", 0, play) }   // the icon turns when the TV says so
        for (b in listOf(prev, play, next)) {
            val spin = android.widget.ProgressBar(a).apply { isIndeterminate = true; visibility = View.GONE; spins[b] = this }
            val cell = FrameLayout(a)
            cell.addView(b, FrameLayout.LayoutParams(-1, -1))
            cell.addView(spin, FrameLayout.LayoutParams(a.dp(20), a.dp(20), Gravity.CENTER))
            head.addView(cell, LinearLayout.LayoutParams(a.dp(44), a.dp(40)).apply { leftMargin = a.dp(6) })
        }
        view.addView(head)
        times.gravity = Gravity.CENTER_VERTICAL
        seek.max = 1000
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, user: Boolean) { if (user) pos.text = clock(p / 1000.0 * o.optLong("dur")) }
            override fun onStartTrackingTouch(s: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(s: SeekBar) {
                dragging = false
                val ms = (s.progress / 1000.0 * o.optLong("dur")).toLong()
                if (busy) return
                o.put("pos", ms); at = System.currentTimeMillis()
                send("seek", ms, null)
            }
        })
        times.addView(pos)
        times.addView(seek, LinearLayout.LayoutParams(0, -2, 1f))
        times.addView(dur)
        view.addView(times, LinearLayout.LayoutParams(-1, -2).apply { topMargin = a.dp(4) })
        a.ui.post { tick.run() }                                      // declared below the init block
        ListenService.control = { what ->                             // the buttons of the listening notification
            if (what == ListenService.STOP) a.stopListen(null)
            else if (a.tv != null) {
                send(what, 0, null)
            }
        }
    }

    /** Where it is now: the last answer moved on by the time since, as NowPlaying does between the helper's lines. */
    private fun now(): Long {
        var p = o.optLong("pos")
        if (o.optString("state") == "playing") p += ((System.currentTimeMillis() - at) * o.optDouble("speed", 1.0)).toLong()
        val d = o.optLong("dur")
        return if (d > 0) minOf(p, d) else p
    }

    private val tick = object : Runnable {
        override fun run() {
            val tv = a.tv
            val looking = a.foreground && a.remote.visibility == View.VISIBLE && a.basic == null && tv != null && tv.ready()
            val listening = a.listen && a.basic == null && tv != null && tv.ready()   // the phone's media controls show it too
            if (!looking && !listening) { o = JSONObject().put("state", "none"); draw(); a.ui.postDelayed(this, 2000); return }
            if (!asking) {
                asking = true
                work.execute {
                    val got = try { player(tv!!).state() } catch (e: Exception) { JSONObject().put("state", "none") }
                    a.ui.post { asking = false; o = got; at = System.currentTimeMillis(); draw(); ListenService.show(o, a.title(a.host ?: "")) }
                }
            }
            draw()
            checkBlack()
            a.ui.postDelayed(this, 1000)
        }
    }

    /** As the web page's (screen.js): some TVs draw a video on a plane no capture sees (the FPT Play box), and protected
     *  films are never captured, so the picture is black but for subtitles and logos. Said once a film is open and the
     *  picture has stayed nearly all black for 4 s, so nobody takes it for a fault. PixelCopy reads a SurfaceView from 7.0 on. */
    private var blackFor = 0
    private val blackBmp by lazy { android.graphics.Bitmap.createBitmap(32, 18, android.graphics.Bitmap.Config.ARGB_8888) }
    private val blackHint by lazy {
        a.label(t("TV không cho chiếu hình lên đây\n(do chip của TV hoặc phim có bản quyền)",
            "The TV does not let its picture show here\n(the TV's chip, or a copy-protected film)"), 13, TEXT).apply {
            gravity = Gravity.CENTER; background = a.box(PILL, 10); setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(8)); visibility = View.GONE
            a.frame.addView(this, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER).apply { leftMargin = a.dp(24); rightMargin = a.dp(24) })
        }
    }
    private fun checkBlack() {
        val shown = o.optString("state", "none") != "none" && a.stage.visibility == View.VISIBLE && a.surface.visibility == View.VISIBLE &&
            a.surface.holder.surface.isValid && android.os.Build.VERSION.SDK_INT >= 24
        if (!shown) { blackFor = 0; blackHint.visibility = View.GONE; return }
        try {
            android.view.PixelCopy.request(a.surface, blackBmp, { r ->
                var dark = 0
                if (r == android.view.PixelCopy.SUCCESS) for (y in 0 until 18) for (x in 0 until 32) {
                    val c = blackBmp.getPixel(x, y)
                    if ((c shr 16 and 255) + (c shr 8 and 255) + (c and 255) < 60) dark++
                }
                blackFor = if (r == android.view.PixelCopy.SUCCESS && dark > 32 * 18 * 9 / 10) blackFor + 1 else 0
                blackHint.visibility = if (blackFor >= 4) View.VISIBLE else View.GONE
            }, a.ui)
        } catch (e: Exception) { blackFor = 0 }
    }

    private fun player(tv: Tv): NowPlaying = synchronized(this) {
        if (npFor !== tv) { np = NowPlaying({ tv.adb() }) { a.assets.open("tvr-helper.jar").use { it.readBytes() } }; npFor = tv }
        np!!
    }

    @Volatile private var busy = false                                // a press is out until the TV's own state shows it done

    private fun mark(b: View?, on: Boolean) {
        if (b == null) return
        b.alpha = if (on) 0.25f else 1f
        spins[b]?.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun sig(j: JSONObject) = "${j.optString("state")}|${j.opt("title")}|${j.optString("pkg")}"

    /** Sends [what] and keeps [btn] busy (further presses are ignored) until the TV's state or track has changed, 2.5 s at most,
     *  so presses cannot pile up faster than its media session follows. Nothing is flipped before the TV says so. */
    private fun send(what: String, ms: Long, btn: View?) {
        val tv = a.tv ?: return
        if (busy) return
        busy = true; mark(btn, true)
        val before = sig(o)
        work.execute {
            var failed = false
            try {
                player(tv).command(what, ms)
                val end = System.currentTimeMillis() + if (what == "seek") 600 else 2500
                while (System.currentTimeMillis() < end) {
                    Thread.sleep(400)
                    val got = try { player(tv).state() } catch (e: Exception) { continue }
                    a.ui.post { o = got; at = System.currentTimeMillis(); draw() }
                    if (what == "seek" || sig(got) != before) break
                }
            } catch (e: Exception) { failed = true }
            a.ui.post {
                busy = false; mark(btn, false)
                if (failed) a.say(t("TV không nhận lệnh. Thử lại.", "The TV did not take that. Try again."), true)
                else ListenService.show(o, a.title(a.host ?: ""))
            }
        }
    }

    private fun draw() {
        val text = { k: String -> if (o.isNull(k)) "" else o.optString(k) }   // optString of a JSON null is "null"
        val name = text("title").ifEmpty { text("app") }              // a player with no title (FPT Play): its app's name
        val on = o.optString("state", "none") != "none" && name.isNotEmpty()
        view.visibility = if (on) View.VISIBLE else View.GONE
        if (!on) return
        title.text = name
        sub.text = text("sub").takeIf { text("title").isNotEmpty() } ?: ""
        sub.visibility = if (sub.text.isEmpty()) View.GONE else View.VISIBLE
        play.setIcon(if (o.optString("state") == "paused") "@play" else "@pause", TEXT)
        val d = o.optLong("dur")
        times.visibility = if (d > 0) View.VISIBLE else View.GONE
        seek.isEnabled = o.optBoolean("seek")
        if (d > 0 && !dragging) { seek.progress = (now() * 1000 / d).toInt(); pos.text = clock(now().toDouble()); dur.text = clock(d.toDouble()) }
    }

    private fun clock(ms: Double): String {
        val s = (ms / 1000).toLong()
        return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) else String.format("%d:%02d", s / 60, s % 60)
    }
}

/** The screen off while the sound plays on (ScreenOff in the shared core): any key, on any remote, turns it back on. */
internal fun MainActivity.screenOff() {
    basic?.takeIf { it.kind != Remote.Kind.ANDROID }?.let { b ->       // LG: by its remote link, no ADB
        Thread {
            val ok = b.screenOff()
            ui.post { say(if (ok) t("Đã tắt màn hình TV. Bấm phím bất kỳ để bật lại.", "The TV’s screen is off. Any key turns it back on.") else t("TV này không tắt được màn hình riêng.", "This TV cannot turn its screen off alone."), !ok) }
        }.start()
        return
    }
    if (needsAdb()) return
    val tv = tv
    if (tv == null || !tv.ready()) return
    Thread {
        val err = try { ScreenOff.off(tv.adb(), assets.open("tvr-helper.jar").use { it.readBytes() }); null } catch (e: Exception) { e }
        ui.post { say(if (err == null) t("Đã tắt màn hình TV. Bấm phím bất kỳ để bật lại.", "The TV’s screen is off. Any key turns it back on.") else t("TV này không tắt được màn hình riêng.", "This TV cannot turn its screen off alone."), err != null) }
    }.start()
}

/** The TV's clipboard (the "clip" entry of Open on the TV): shown in a box, then copied to the phone, set on the TV, or pasted there. */
internal fun MainActivity.clipDialog() {
    if (needsAdb()) return
    val tv = tv
    if (tv == null || !tv.ready()) return
    Thread {
        val text = try { tv.clipboard() } catch (e: Exception) { null }
        ui.post {
            if (isFinishing) return@post
            if (text == null) { say(t("TV không trả lời. Thử lại.", "The TV did not answer. Try again."), true); return@post }
            val box = input(t("Clipboard của TV đang trống. Gõ hoặc dán chữ vào đây để gửi lên TV.", "The TV’s clipboard is empty. Type or paste text here to send it to the TV."))
            box.setText(text)
            box.setSingleLine(false)                                  // a box to read and edit, not input()'s one line
            box.minLines = 4
            box.maxLines = 8
            box.gravity = Gravity.TOP or Gravity.START
            box.background = box(BTN, 12)                             // the dialog is CARD: the box must stand out from it
            val body = col()
            body.setPadding(dp(24), dp(12), dp(24), dp(20))           // no dialog buttons under it: the row is the last thing
            body.addView(box, LinearLayout.LayoutParams(-1, -2))
            val acts = LinearLayout(this)
            body.addView(acts, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(12) })
            val d = AlertDialog.Builder(this).setTitle(t("Clipboard của TV", "The TV’s clipboard")).setView(body).create()
            val toTv = { paste: Boolean ->
                val s = box.text.toString()
                if (s.isEmpty()) say(t("Chưa có chữ", "No text yet"), false)
                else {
                    d.dismiss()
                    Thread {
                        val ok = try { tv.setClipboard(s.take(100000), paste); true } catch (e: Exception) { false }
                        ui.post { say(if (!ok) t("TV không nhận. Thử lại.", "The TV did not take it. Try again.") else if (paste) t("Đã dán trên TV", "Pasted on the TV") else t("Đã chép lên TV", "Copied to the TV"), !ok) }
                    }.start()
                }
            }
            // one row of short words: the title says whose clipboard it is; what each does in full goes to screen readers
            for ((i, b) in listOf(
                Triple(t("Chép về", "Copy"), t("Chép về điện thoại", "Copy to the phone"), {
                    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("TV", box.text.toString()))
                    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(this, t("Đã sao chép vào máy này", "Copied to this device"), Toast.LENGTH_SHORT).show()   // Android 13+ says it itself
                    d.dismiss()
                }),
                Triple(t("Gửi lên", "Send"), t("Chép lên clipboard của TV", "Put on the TV’s clipboard"), { toTv(false) }),
                Triple(t("Dán", "Paste"), t("Dán vào ô đang gõ trên TV", "Paste where the TV is typing"), { toTv(true) }),
            ).withIndex()) {
                val btn = button(b.first, if (i == 2) ACCENT else BTN)
                btn.contentDescription = b.second
                btn.setPadding(dp(4), 0, dp(4), 0)
                btn.setOnClickListener { b.third() }
                acts.addView(btn, LinearLayout.LayoutParams(0, -1, 1f).apply { if (i > 0) leftMargin = dp(8) })
            }
            d.show()
        }
    }.start()
}
