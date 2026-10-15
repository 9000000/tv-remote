package dev.tvremote.phone

import android.app.AlertDialog
import android.content.ContentValues
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import dev.tvremote.core.Tv
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

// The remote screen: picture, keys, direction pad / touchpad, number keys; and laying it out for the current mode.

@Suppress("DEPRECATION")
internal fun MainActivity.layoutRemote() {
    val video = video && basic == null                            // keys only: there is no picture to show
    videoBtn.visibility = if (basic == null) View.VISIBLE else View.GONE
    listenBtn.visibility = videoBtn.visibility
    listenBtn.background = box(if (listen) ACCENT else BTN, 16)
    listenBtn.contentDescription = if (listen) t("Đang nghe trên điện thoại · bấm để dừng", "Listening on the phone · tap to stop") else t("Nghe tiếng TV trên điện thoại", "Hear the TV on the phone")
    basicBar.visibility = if (basic == null) View.GONE else View.VISIBLE
    val full = video && host != null && (fullscreen || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)   // landscape: the picture fills the screen
    (controls.parent as View).visibility = if (full) View.GONE else View.VISIBLE
    topBar.visibility = if (full) View.GONE else View.VISIBLE
    stage.layoutParams = if (full) LinearLayout.LayoutParams(-1, 0, 1f) else LinearLayout.LayoutParams(-1, -2)   // weight: room for the drawing tools under it
    if (!video && drawing) stopDrawing()                              // nothing to draw on
    frame.layoutParams = FrameLayout.LayoutParams(-1, if (full) -1 else -2)
    stage.visibility = if (video) View.VISIBLE else View.GONE
    fullBtn.visibility = if (video) View.VISIBLE else View.GONE
    qualBtn.visibility = fullBtn.visibility
    // four buttons leave the words a sliver: then the words get their own line and the buttons go under them, to the right
    val stacked = video
    topBar.orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
    topBar.gravity = if (stacked) Gravity.END else Gravity.CENTER_VERTICAL
    (status.parent as View).layoutParams = if (stacked) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f)
    qualBtn.text = Tv.QUALITY[prefs.getInt("quality", 1).coerceIn(0, Tv.QUALITY.size - 1)][0].toString() + "p"
    exitBtn.visibility = if (full) View.VISIBLE else View.GONE
    window.decorView.systemUiVisibility = if (full) View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else 0
    videoBtn.lit = video                                              // lit while the picture shows, like every on/off button
    videoBtn.background = box(if (video) ACCENT else BTN, 16)
    listenBtn.lit = listen
    videoBtn.setIcon(if (video) "@eye_off" else "@eye", TEXT)
    videoBtn.contentDescription = if (video) t("Ẩn hình TV", "Hide the TV picture") else t("Xem hình TV", "Show the TV picture")
}

// ---------------------------------------------------------------- remote screen
internal fun MainActivity.buildRemote() {
    remote = col()
    status = label("", 13, MUTED)
    status.setPadding(dp(10), dp(8), dp(8), dp(8))
    videoBtn = icon("@eye_off", "", BTN)
    videoBtn.setOnClickListener { toggleVideo() }
    basicBar = button(t("Chỉ bấm phím · Bật ADB để dùng đủ chức năng  ›", "Keys only · Turn on ADB for everything  ›"), ACCENT)
    basicBar.textSize = 13f
    basicBar.setOnClickListener { adbHelp() }
    topBar = LinearLayout(this)
    val bar = topBar
    bar.gravity = Gravity.CENTER_VERTICAL
    val head = LinearLayout(this)                                     // the logo stays beside the words, stacked or not
    head.gravity = Gravity.CENTER_VERTICAL
    head.addView(logo(28), LinearLayout.LayoutParams(dp(28), dp(28)).apply { leftMargin = dp(10) })
    head.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
    bar.addView(head, LinearLayout.LayoutParams(0, -2, 1f))
    topTools = LinearLayout(this)                                     // the buttons; under the words when there are many (layoutRemote)
    val tools = topTools
    tools.gravity = Gravity.CENTER_VERTICAL or Gravity.END
    qualBtn = button("", BTN)                                     // picture quality: a menu of the levels, lightest first
    qualBtn.textSize = 13f
    qualBtn.typeface = Typeface.DEFAULT_BOLD
    qualBtn.setPadding(0, 0, 0, 0)
    qualBtn.minHeight = 0
    qualBtn.minimumHeight = 0
    qualBtn.minWidth = 0
    qualBtn.minimumWidth = 0
    qualBtn.contentDescription = t("Chất lượng hình", "Picture quality")
    qualBtn.setOnClickListener {
        val menu = android.widget.PopupMenu(this, qualBtn)
        for (level in Tv.LEVELS) menu.menu.add(0, level, 0, Tv.QUALITY[level][0].toString() + "p" + (if (level == prefs.getInt("quality", 1)) "  ✓" else ""))
        menu.setOnMenuItemClickListener { item ->
            if (item.itemId != prefs.getInt("quality", 1)) {
                prefs.edit().putInt("quality", item.itemId).apply()
                stopSession()                                     // the size is fixed when the session starts
                layoutRemote()
                startSession()
            }
            true
        }
        menu.show()
    }
    listenBtn = icon("@headphones", t("Nghe tiếng TV trên điện thoại", "Hear the TV on the phone"), BTN)   // 🎧 Listen.kt
    listenBtn.setOnClickListener { toggleListen() }
    val hp = LinearLayout.LayoutParams(dp(52), dp(40))
    hp.rightMargin = dp(8)
    tools.addView(listenBtn, hp)
    val qp = LinearLayout.LayoutParams(dp(60), dp(40))
    qp.rightMargin = dp(8)
    tools.addView(qualBtn, qp)
    fullBtn = icon("@expand", t("Toàn màn hình", "Full screen"), BTN)
    fullBtn.setOnClickListener { setFullscreen(true) }
    val fb = LinearLayout.LayoutParams(dp(52), dp(40))
    fb.rightMargin = dp(8)
    tools.addView(fullBtn, fb)
    val vb = LinearLayout.LayoutParams(dp(52), dp(40))
    vb.rightMargin = dp(10)
    tools.addView(videoBtn, vb)
    bar.addView(tools, LinearLayout.LayoutParams(-2, -2))
    bar.setPadding(0, dp(8), 0, dp(10))                               // room between the buttons and the picture
    remote.addView(bar)

    frame = VideoFrame(this)
    frame.setBackgroundColor(Color.BLACK)
    surface = SurfaceView(this)
    surface.holder.addCallback(this)
    frame.addView(surface, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
    surface.setOnTouchListener { v, e ->
        val tv = tv
        if (tv == null || !tv.ready()) return@setOnTouchListener true
        if (drawing) { drawTouch(e, v); return@setOnTouchListener true }
        val action = when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> 0
            MotionEvent.ACTION_MOVE -> 2
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> 1
            else -> -1
        }
        if (action >= 0) tv.touch(action, e.x / v.width, e.y / v.height)
        when (action) { 0 -> tapMark.down(e.x, e.y); 2 -> tapMark.move(e.x, e.y); 1 -> tapMark.up() }
        true
    }
    tapMark = TapMark(this)
    frame.addView(tapMark, FrameLayout.LayoutParams(-1, -1))
    drawView = DrawView(this)
    frame.addView(drawView, FrameLayout.LayoutParams(-1, -1))
    stage = FrameLayout(this)
    stage.setBackgroundColor(Color.BLACK)
    stage.addView(frame, FrameLayout.LayoutParams(-1, -2))
    exitBtn = icon("@close", t("Thoát toàn màn hình", "Leave full screen"), 0x66000000)   // the only thing on the picture, and only in full screen
    exitBtn.alpha = 0.5f
    exitBtn.setOnClickListener { setFullscreen(false) }
    val fp = FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP or Gravity.END)
    fp.setMargins(dp(8), dp(8), dp(8), dp(8))
    stage.addView(exitBtn, fp)
    remote.addView(stage, LinearLayout.LayoutParams(-1, -2))
    drawBar = drawTools()
    remote.addView(drawBar, LinearLayout.LayoutParams(-1, -2))

    controls = col()
    controls.setPadding(dp(14), dp(12), dp(14), dp(20))
    val bb = LinearLayout.LayoutParams(-1, dp(44))
    bb.bottomMargin = dp(12)
    controls.addView(basicBar, bb)
    setupBar = button("", GREEN)                                      // SetupBanner.kt
    setupBar.textSize = 13f
    setupBar.visibility = View.GONE
    controls.addView(setupBar, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(12) })
    val first = LinearLayout(this)
    val power = icon("@power", t("Nguồn", "Power"), BTN)
    power.setOnClickListener {
        if (basic != null) { press(26); return@setOnClickListener }
        val tv = tv
        if (tv == null || !tv.ready()) return@setOnClickListener
        Thread {
            val awake = try { !tv.shell("dumpsys power | grep -m1 mWakefulness=").contains("Asleep") } catch (e: Exception) { true }
            ui.post {
                if (isFinishing || this.tv !== tv) return@post
                if (!awake) {
                    tv.press(224)
                    Toast.makeText(this, t("Đang bật TV...", "Waking the TV..."), Toast.LENGTH_SHORT).show()
                } else AlertDialog.Builder(this).setMessage(t("Đưa TV về chế độ chờ?", "Put the TV in standby?"))
                    .setPositiveButton(t("Chế độ chờ", "Standby")) { _, _ -> if (tv.ready()) tv.press(223) }
                    .setNegativeButton(t("Hủy", "Cancel"), null).show()
            }
        }.start()
    }
    first.addView(power, cell(58, false))
    val apps = icon("@apps", t("Quản lý", "Manage"), BTN)          // apps, files, health, automation, setup
    apps.setOnClickListener { if (!needsAdb()) managing.open(host) }
    val mic = icon("@mic", t("Nói", "Speak"), BTN)
    mic.setOnClickListener { speak() }                         // keys-only too: keys, apps and scenes spoken still work (Automation.kt)
    mic.setOnLongClickListener {                                  // the TV's own Assistant screen
        tv?.let { if (it.ready()) it.press(219) }
        true
    }
    first.addView(mic, cell(58, true))
    first.addView(apps, cell(58, true))
    // Everything used less often sits behind this button: one tap shows the extra keys, another hides them.
    val moreBtn = icon("@more", t("Thêm phím", "More keys"), BTN)
    first.addView(moreBtn, cell(58, true))
    controls.addView(captioned(first))
    val more = col()
    more.addView(captioned(keys(58, "@input|" + t("Ngõ vào", "Input"), 178, "@settings|" + t("Cài đặt", "Settings"), 176, "@info|" + t("Thông tin", "Info"), 165, "@guide|" + t("Lịch phát", "Guide"), 172)), gap(8))
    more.addView(captioned(keys(58, "@tv|" + t("TV", "Live TV"), 170, "@subtitle|" + t("Phụ đề", "Subtitles"), 175, "@stop|" + t("Dừng", "Stop"), 86, "@camera|" + t("Chụp màn hình", "Screenshot"), SHOT)), gap(8))
    more.addView(captioned(keys(58, "@send|" + t("Mở trên TV", "Open on the TV"), OPEN, "@timer|" + t("Hẹn giờ tắt", "Sleep timer"), TIMER, "@scene|" + t("Cảnh", "Scenes"), SCENES, "@pen|" + t("Vẽ lên TV", "Draw on the TV"), DRAW)), gap(8))
    val showMore = { on: Boolean ->
        more.visibility = if (on) View.VISIBLE else View.GONE
        moreBtn.background = box(if (on) ACCENT else BTN, 16)
        moreBtn.lit = on
    }
    showMore(prefs.getBoolean("more", false))
    moreBtn.setOnClickListener {
        val on = more.visibility != View.VISIBLE
        prefs.edit().putBoolean("more", on).apply()
        showMore(on)
    }
    controls.addView(more)
    // The arrows, or a touchpad in their place: swipes become arrow keys, a tap is OK. Handy where the TV shows a pointer
    // that follows the arrows (a browser, Downloader), and for long lists.
    val arrows = dpad()
    val tpad = touchpad()
    val swap = button("", BTN)
    swap.textSize = 13f
    swap.setPadding(dp(12), 0, dp(12), 0)
    swap.minHeight = 0
    swap.minimumHeight = 0
    val usePad = { on: Boolean ->
        arrows.visibility = if (on) View.GONE else View.VISIBLE
        tpad.visibility = if (on) View.VISIBLE else View.GONE
        swap.text = if (on) t("Dùng phím mũi tên", "Use the arrows") else t("Dùng touchpad", "Use the touchpad")
        swap.background = box(if (on) ACCENT else BTN, 14)            // lit while the touchpad is on
    }
    usePad(prefs.getBoolean("pad", false))
    swap.setOnClickListener {
        val on = tpad.visibility != View.VISIBLE
        prefs.edit().putBoolean("pad", on).apply()
        usePad(on)
    }
    controls.addView(arrows, gap(16))
    controls.addView(tpad, LinearLayout.LayoutParams(-1, dp(250)).also { it.topMargin = dp(16) })
    val sp2 = LinearLayout.LayoutParams(-2, dp(36))
    sp2.gravity = Gravity.CENTER_HORIZONTAL
    sp2.topMargin = dp(8)
    controls.addView(swap, sp2)
    controls.addView(captioned(keys(58, "@back|" + t("Quay lại", "Back"), 4, "@home|Home", 3, "@menu|Menu", 82)), gap(16))
    // the same rows, in the same order, as the two web remotes
    // Volume on the left, channels on the right, each one tall bar for the thumb; the media keys between them.
    // The same block, in the same order, as the two web remotes.
    val mid = col()
    mid.addView(captioned(keys(58, "@mute|" + t("Tắt tiếng", "Mute"), 164, "@play_pause|" + t("Phát / Dừng", "Play / Pause"), 85)))
    mid.addView(captioned(keys(58, "@prev|" + t("Bài trước", "Previous"), 88, "@next|" + t("Bài sau", "Next"), 87)), gap(8))
    mid.addView(captioned(keys(58, "@rewind|" + t("Tua lại", "Rewind"), 89, "@ffwd|" + t("Tua tới", "Forward"), 90)), gap(8))
    val cluster = LinearLayout(this)
    cluster.addView(pill("Vol", "@plus", 24, t("Tăng âm lượng", "Volume up"), "@minus", 25, t("Giảm âm lượng", "Volume down")), LinearLayout.LayoutParams(0, -1, 1f))
    val mp = LinearLayout.LayoutParams(0, -2, 2.4f)
    mp.leftMargin = dp(10)
    mp.rightMargin = dp(10)
    cluster.addView(mid, mp)
    cluster.addView(pill(t("Kênh", "CH"), "@up", 166, t("Kênh +", "Channel up"), "@down", 167, t("Kênh −", "Channel down")), LinearLayout.LayoutParams(0, -1, 1f))
    controls.addView(cluster, gap(16))

    val line = LinearLayout(this)
    liveBtn = button("⚡", BTN)
    liveBtn.setPadding(0, 0, 0, 0)
    liveBtn.setOnClickListener { setLive(!live) }
    val lb = LinearLayout.LayoutParams(dp(48), dp(48))
    lb.rightMargin = dp(6)
    line.addView(liveBtn, lb)
    textBox = input("")
    textBox.imeOptions = EditorInfo.IME_ACTION_SEND
    textBox.setSingleLine(true)
    textBox.setOnEditorActionListener { _, _, _ ->
        sendText()
        true
    }
    textBox.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = typedLive(s.toString())
    })
    line.addView(textBox, LinearLayout.LayoutParams(0, dp(48), 1f))
    val send = icon("@send", t("Gửi", "Send"), ACCENT)
    send.setOnClickListener { sendText() }
    val lp = LinearLayout.LayoutParams(-2, dp(48))
    lp.leftMargin = dp(6)
    lp.width = dp(60)
    line.addView(send, lp)
    controls.addView(line, gap(12))
    val edit = keys(48, "@backspace|" + t("Xóa", "Delete"), 67, "@enter|Enter", 66)
    val digits = button("123", BTN)                               // shows / hides the number keys (channels, PIN codes)
    digits.contentDescription = t("Bàn phím số", "Number keys")
    val dl = LinearLayout.LayoutParams(dp(64), dp(48))
    dl.rightMargin = dp(8)
    edit.addView(digits, 0, dl)
    controls.addView(edit, gap(8))
    val pad = col()
    pad.addView(keys(48, "1", 8, "2", 9, "3", 10), gap(8))
    pad.addView(keys(48, "4", 11, "5", 12, "6", 13), gap(8))
    pad.addView(keys(48, "7", 14, "8", 15, "9", 16), gap(8))
    pad.addView(keys(48, "", 0, "0", 7, "", 0), gap(8))
    pad.visibility = if (prefs.getBoolean("numpad", false)) View.VISIBLE else View.GONE
    digits.setOnClickListener {
        val show = pad.visibility != View.VISIBLE
        pad.visibility = if (show) View.VISIBLE else View.GONE
        prefs.edit().putBoolean("numpad", show).apply()
    }
    controls.addView(pad)
    setLive(prefs.getBoolean("live", false))

    val bye = button(t("Ngắt kết nối", "Disconnect"), 0)
    bye.setTextColor(RED)
    bye.setOnClickListener { disconnect(null) }
    val bp = LinearLayout.LayoutParams(-1, dp(48))
    bp.topMargin = dp(10)
    controls.addView(bye, bp)

    val sv = ScrollView(this)
    sv.addView(controls)
    remote.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
    layoutRemote()
}

/** A row of equal-width remote keys: label, Android key code, label, key code... "@icon|words" shows an icon. An empty label leaves a gap. */
/** One tall bar with a key at each end and its name in the middle: volume, channels. */
internal fun MainActivity.pill(name: String, upIcon: String, up: Int, sayUp: String, downIcon: String, down: Int, sayDown: String): LinearLayout {
    val p = col()
    p.background = box(BTN, 26)
    val a = icon(upIcon, sayUp, 0)
    bindKey(a, up, box(HELD, 26))
    val b = icon(downIcon, sayDown, 0)
    bindKey(b, down, box(HELD, 26))
    val l = label(name, 13, MUTED)
    l.gravity = Gravity.CENTER
    l.typeface = Typeface.DEFAULT_BOLD
    p.addView(a, LinearLayout.LayoutParams(-1, 0, 1f))
    p.addView(l, LinearLayout.LayoutParams(-1, -2))
    p.addView(b, LinearLayout.LayoutParams(-1, 0, 1f))
    return p
}

/** Shows each icon button's name under its icon. */
internal fun MainActivity.captioned(row: LinearLayout): LinearLayout {
    for (i in 0 until row.childCount) (row.getChildAt(i) as? IconButton)?.let { it.caption = it.contentDescription?.toString() }
    return row
}

/** Saves what is on the TV's screen as a picture in this phone's Download folder. */
internal fun MainActivity.shot() {
    if (needsAdb()) return
    val tv = tv
    if (tv == null || !tv.ready()) return
    Toast.makeText(this, t("Đang chụp màn hình TV...", "Taking a screenshot of the TV..."), Toast.LENGTH_SHORT).show()
    Thread {
        var result: String
        try {
            val png = tv.screenshot() ?: throw java.io.IOException(t("TV không trả ảnh", "the TV sent no picture"))
            val fileName = "tv-" + System.currentTimeMillis() / 1000 + ".png"
            val out: OutputStream
            if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues()
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                v.put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                out = contentResolver.openOutputStream(contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!)!!
                result = t("Đã lưu vào thư mục Download: ", "Saved to Download: ") + fileName
            } else {
                val f = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                out = FileOutputStream(f)
                result = t("Đã lưu: ", "Saved: ") + f.absolutePath
            }
            out.use { it.write(png) }
        } catch (e: Exception) {
            result = t("Không chụp được: ", "Screenshot failed: ") + e.message
        }
        val msg = result
        ui.post { if (!isFinishing) Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
    }.start()
}

/** Menu, Input, TV settings, Guide, Live TV: keys that should open something. When the TV's screen ignores one, say so, so it does not look like a dead button. */
internal fun MainActivity.opens(code: Int) {
    if (basic != null) { if (code == 176) findSettings() else press(code); return }
    val tv = tv
    if (tv == null || !tv.ready()) return
    Thread {
        if (!tv.pressSeen(code)) ui.post {
            if (!isFinishing && this.tv === tv) Toast.makeText(this, t("TV đã nhận nút này, nhưng màn hình hiện tại không phản hồi", "The TV got the key, but the current screen did not react"), Toast.LENGTH_SHORT).show()
        }
    }.start()
}

internal fun MainActivity.keys(height: Int, vararg labelAndCode: Any): LinearLayout {
    val row = LinearLayout(this)
    for (i in labelAndCode.indices step 2) {
        var label = labelAndCode[i] as String
        var say = label
        val bar = label.indexOf('|')
        if (bar > 0) {
            say = label.substring(bar + 1)
            label = label.substring(0, bar)
        }
        val code = labelAndCode[i + 1] as Int
        val v: View
        if (label.isEmpty()) v = View(this)
        else {
            val b = if (bar > 0) icon(label, say, BTN) else button(label, BTN)
            if (bar < 0) b.textSize = 18f
            if (code == SHOT) b.setOnClickListener { shot() }
            else if (code == OPEN) b.setOnClickListener { quickOpen() }
            else if (code == TIMER) b.setOnClickListener { timerMenu() }
            else if (code == SCENES) b.setOnClickListener { scenesMenu() }
            else if (code == DRAW) {
                drawBtn = b as? IconButton
                drawLit()
                b.setOnClickListener { if (drawing) stopDrawing() else startDrawing() }
            }
            else if (code in Tv.OPENERS) {
                val held = box(HELD, 16)
                b.setOnClickListener { flash(b, held); opens(code) }
            } else bindKey(b, code, box(HELD, 16))
            v = b
        }
        row.addView(v, cell(height, i > 0))
    }
    return row
}

/**
 * A tap counts when the finger lifts on the button: touching it sends nothing, and sliding off it (or starting to scroll)
 * cancels. Then the button flashes amber. Keeping the finger on it for 0.4 s is a long press on the TV: the key goes
 * down, repeats, and comes up when the finger lifts or leaves; the button stays amber meanwhile.
 */
internal fun MainActivity.bindKey(b: Button, code: Int, held: Drawable) {
    val normal = b.background
    var holding = false
    var count = 0
    var start: Runnable? = null
    val again = object : Runnable {
        override fun run() {
            if (!holding) return
            keyDown(code, ++count)
            ui.postDelayed(this, 80)
        }
    }
    val stop = {
        start?.let { ui.removeCallbacks(it) }
        start = null
        if (holding) {
            holding = false
            ui.removeCallbacks(again)
            keyUp(code)
            b.background = normal
        }
    }
    b.setOnTouchListener { v, e ->
        val inside = e.x >= 0 && e.y >= 0 && e.x <= v.width && e.y <= v.height
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val go = Runnable {
                    if (!live()) return@Runnable
                    start = null
                    holding = true
                    count = 0
                    b.background = held
                    keyDown(code, 0)
                    ui.postDelayed(again, 80)
                }
                start = go
                ui.postDelayed(go, 400)
            }
            MotionEvent.ACTION_MOVE -> if (!inside) stop()
            MotionEvent.ACTION_CANCEL -> stop()
            MotionEvent.ACTION_UP -> {
                val tap = start != null && inside                 // lifted on the button before it became a hold
                stop()
                if (tap && live()) {
                    press(code)
                    flash(b, held)
                }
            }
        }
        true
    }
}

/** Amber for a moment: the press was taken. */
internal fun MainActivity.flash(b: Button, held: Drawable) {
    val normal = b.background
    if (normal === held) return
    b.background = held
    ui.postDelayed({ b.background = normal }, 160)
}

/** The round direction pad: four arrows on a disc, OK in the middle. */
/**
 * A touchpad made of keys: every bit of a swipe is one arrow key in that direction, a tap is OK, a tap with two fingers
 * is Back. It is not a mouse: it moves whatever the arrows move, which on a TV browser is the pointer.
 */
internal fun MainActivity.touchpad(): View {
    val pad = FrameLayout(this)
    pad.background = box(CARD, 28)
    val hint = label(t("Vuốt: di chuyển  ·  Chạm: OK  ·  Chạm 2 ngón: Quay lại", "Swipe: move  ·  Tap: OK  ·  Two-finger tap: Back"), 13, MUTED)
    hint.gravity = Gravity.CENTER
    pad.addView(hint, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
    val step = dp(20).toFloat()
    var ax = 0f
    var ay = 0f
    var moved = false
    var fingers = 0
    var downAt = 0L
    pad.setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.parent.requestDisallowInterceptTouchEvent(true)  // the page must not scroll under the finger
                ax = e.x; ay = e.y; moved = false; fingers = 1; downAt = e.eventTime
            }
            MotionEvent.ACTION_POINTER_DOWN -> fingers = maxOf(fingers, e.pointerCount)
            MotionEvent.ACTION_MOVE -> if (fingers == 1 && live()) {
                while (Math.abs(e.x - ax) >= step) { val right = e.x > ax; press(if (right) 22 else 21); ax += if (right) step else -step; moved = true }
                while (Math.abs(e.y - ay) >= step) { val down = e.y > ay; press(if (down) 20 else 19); ay += if (down) step else -step; moved = true }
            }
            MotionEvent.ACTION_UP -> if (!moved && live() && e.eventTime - downAt < 400) {
                press(if (fingers >= 2) 4 else 23)
                val normal = pad.background
                pad.background = box(HELD, 28)
                ui.postDelayed({ pad.background = normal }, 120)
            }
        }
        true
    }
    return pad
}

internal fun MainActivity.dpad(): View {
    val pad = FrameLayout(this)
    pad.background = oval(CARD)
    val arrows = arrayOf(intArrayOf(19, Gravity.TOP or Gravity.CENTER_HORIZONTAL), intArrayOf(20, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL),
        intArrayOf(21, Gravity.START or Gravity.CENTER_VERTICAL), intArrayOf(22, Gravity.END or Gravity.CENTER_VERTICAL))
    val names = arrayOf("@up", "@down", "@left", "@right")
    val says = arrayOf(t("Lên", "Up"), t("Xuống", "Down"), t("Trái", "Left"), t("Phải", "Right"))
    for (i in 0 until 4) {
        val b = icon(names[i], says[i], 0)
        b.setIcon(names[i], MUTED)
        b.size = 30f
        b.background = oval(0)
        bindKey(b, arrows[i][0], oval(HELD))
        val lp = FrameLayout.LayoutParams(dp(80), dp(80), arrows[i][1])
        lp.setMargins(dp(4), dp(4), dp(4), dp(4))
        pad.addView(b, lp)
    }
    val ok = button("OK", ACCENT)
    ok.typeface = Typeface.DEFAULT_BOLD
    ok.textSize = 18f
    ok.setPadding(0, 0, 0, 0)
    ok.background = oval(ACCENT)
    ok.elevation = dp(6).toFloat()
    bindKey(ok, 23, oval(HELD))
    pad.addView(ok, FrameLayout.LayoutParams(dp(92), dp(92), Gravity.CENTER))
    val wrap = LinearLayout(this)
    wrap.gravity = Gravity.CENTER
    wrap.addView(pad, LinearLayout.LayoutParams(dp(248), dp(248)))
    return wrap
}

/** Where the finger is on the TV picture, as on the web page: a dot that follows a drag and spreads into a fading ring when let go. */
internal class TapMark(c: Context) : View(c) {
    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x4DFFFFFF }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d; color = 0xE6FFFFFF.toInt(); setShadowLayer(4 * d, 0f, 0f, 0x80000000.toInt()) }
    private var x = 0f
    private var y = 0f
    private var held = false
    private var gone = 1f                                              // 0..1 through the fade after letting go; 1: nothing to draw
    private var anim: ValueAnimator? = null

    init { isClickable = false; isFocusable = false; setLayerType(LAYER_TYPE_SOFTWARE, null) }   // the touches go on to the picture; software: shadows on the ring

    fun down(x: Float, y: Float) { anim?.cancel(); this.x = x; this.y = y; held = true; gone = 0f; invalidate() }
    fun move(x: Float, y: Float) { if (held) { this.x = x; this.y = y; invalidate() } }
    fun up() {
        if (!held) return
        held = false
        anim = ValueAnimator.ofFloat(0f, 1f).setDuration(450).apply { addUpdateListener { gone = it.animatedValue as Float; invalidate() }; start() }
    }

    override fun onDraw(c: Canvas) {
        if (!held && gone >= 1f) return
        val r = 17 * d * (1 + 0.9f * gone)
        val a = 1 - gone
        fill.alpha = (0x4D * a * (1 - gone)).toInt(); ring.alpha = (0xE6 * a).toInt()
        c.drawCircle(x, y, r, fill)
        c.drawCircle(x, y, r, ring)
    }
}
