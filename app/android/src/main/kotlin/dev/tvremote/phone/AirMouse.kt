package dev.tvremote.phone

import dev.tvremote.core.T
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.tvremote.core.remote.Remote
import kotlin.math.abs
import kotlin.math.roundToInt

// Air Mouse: the phone held like a pointer, its gyroscope moves a real mouse pointer on the TV. Android TVs get a virtual HID mouse
// through the scrcpy control socket (Tv.mouseOpen, no picture needed), LG its Magic Remote pointer (Remote.pointer).

private object Air {
    var host: FrameLayout? = null
    var sensors: SensorManager? = null
    var listener: SensorEventListener? = null
    var paused = false
    var buttons = 0                                                  // 1 while the pointer's button is held (a drag)
    var clickHeld = false                                            // the big Click button is down
    var padRest = false                                              // a finger rests on the pad without moving it
    var holdToMove = false                                           // the gyroscope moves the pointer only while a finger rests on the pad
    var freezeUntil = 0L                                             // a click lands where it was aimed: no motion ~80 ms around press / release
    var touching = false                                             // a finger on the pad: the gyroscope waits, so the two never fight
    var thread: android.os.HandlerThread? = null                     // the sensor's callbacks run here, not on the screen's thread
    var synth: Thread? = null
    var sub = false                                                  // true when the game rotation vector stands in for a missing gyroscope
}

private const val GAIN = 700f             // HID counts per radian turned: about a screen width for a wrist's sweep
private const val DEAD = 0.025f           // rad/s below which the hand's tremor is ignored
private const val TOUCH_GAIN = 1.3f   // HID counts per dp of finger travel at walking speed
private const val TOUCH_BOOST = 0.9f  // extra per dp/ms, like a laptop touchpad's curve
private const val BOOST = 0.45f           // extra speed per rad/s, so slow moves stay fine and fast ones reach the corner

internal fun MainActivity.airOpen() {
    val t = tv
    val b = basic
    val androidOk = t != null && t.ready()
    if (!androidOk && (b == null || !b.ready() || "mouse" !in Remote.Kind.caps(b.kind))) { say(T("air.needs_adb"), true); return }
    val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    val rot = if (gyro == null) sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) else null
    airClose()
    Air.paused = false; Air.touching = false; Air.buttons = 0; Air.clickHeld = false; Air.padRest = false
    Air.sub = gyro == null
    Air.sensors = sm
    if (androidOk) { t!!.mouseOpen(); t.mouseStats = { android.util.Log.i("airstat", it) } }
    airSynth()
    val view = airView()
    (findViewById<ViewGroup>(android.R.id.content)).addView(view, FrameLayout.LayoutParams(-1, -1))
    Air.host = view
    (gyro ?: rot)?.let { airListen(sm, it) }                          // no sensor: the pad alone still drives the pointer
}

/** Leaves the screen: sensor off, the TV's mouse (and its pointer) gone. True when it was open (the Back button). */
internal fun MainActivity.airClose(): Boolean {
    val view = Air.host ?: return false
    if (Air.buttons != 0) airButton(false)
    Air.listener?.let { Air.sensors?.unregisterListener(it) }
    Air.listener = null
    Air.thread?.quitSafely(); Air.thread = null
    Air.synth?.interrupt(); Air.synth = null
    tv?.mouseStats = null
    (view.parent as? ViewGroup)?.removeView(view)
    Air.host = null
    tv?.mouseClose()
    return true
}

internal fun MainActivity.airBack(): Boolean = airClose()

private fun MainActivity.airMove(dx: Float, dy: Float, carry: FloatArray) {
    carry[0] += dx; carry[1] += dy                                   // the fractions a count cannot carry add up to the next one
    val ix = carry[0].toInt(); val iy = carry[1].toInt()
    if (ix == 0 && iy == 0) return
    carry[0] -= ix; carry[1] -= iy
    val t = tv
    if (t != null && t.ready()) t.mouseMove(ix, iy, Air.buttons) else basic?.pointer(ix, iy)   // the held button rides along: that is the drag
}

private fun MainActivity.airListen(sm: SensorManager, sensor: Sensor) {
    val carry = FloatArray(2)
    var last = 0L
    val prev = FloatArray(3)                                         // game rotation vector: the last yaw / pitch
    var have = false
    val l = object : SensorEventListener {
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        override fun onSensorChanged(e: SensorEvent) {
            val live = Air.clickHeld || (if (Air.holdToMove) Air.padRest else !Air.touching)
            if (Air.paused || !live || System.currentTimeMillis() < Air.freezeUntil) { last = 0; have = false; return }
            var wz: Float; var wx: Float
            if (e.sensor.type == Sensor.TYPE_GYROSCOPE) {
                if (last == 0L) { last = e.timestamp; return }
                val dt = (e.timestamp - last) / 1e9f; last = e.timestamp
                if (dt <= 0f || dt > 0.2f) return
                // z is the way the screen faces, x the phone's width: held flat, turning left (z+) is the pointer going left, tipping the top up (x+) going up
                wz = -e.values[2]; wx = -e.values[0]
                val w = kotlin.math.hypot(wz, wx)
                if (w < DEAD) return
                val k = GAIN * dt * (1f + BOOST * w)
                airMove(wz * k, wx * k, carry)
            } else {
                val m = FloatArray(9); val o = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(m, e.values)
                SensorManager.getOrientation(m, o)
                if (!have) { prev[0] = o[0]; prev[1] = o[1]; have = true; return }
                var dyaw = o[0] - prev[0]
                if (dyaw > Math.PI) dyaw -= (2 * Math.PI).toFloat() else if (dyaw < -Math.PI) dyaw += (2 * Math.PI).toFloat()
                val dpitch = o[1] - prev[1]
                prev[0] = o[0]; prev[1] = o[1]
                wz = dyaw; wx = dpitch
                if (kotlin.math.hypot(wz, wx) < 0.0008f) return
                airMove(wz * GAIN, -wx * GAIN, carry)                // rotation vector's azimuth grows clockwise, its pitch grows when the top goes down
            }
        }
    }
    Air.listener = l
    val th = android.os.HandlerThread("air-sensor").apply { start() }; Air.thread = th
    sm.registerListener(l, sensor, 5_000, android.os.Handler(th.looper))   // 200 Hz asked, off the main thread; the system gives what it can
}

@SuppressLint("ClickableViewAccessibility")
private fun MainActivity.airView(): FrameLayout {
    val root = FrameLayout(this)
    root.setBackgroundColor(BG)
    root.isClickable = true                                          // touches do not fall through to the remote below
    val page = col()
    page.setPadding(dp(16), dp(20), dp(16), dp(20))
    val head = label(T("air.title"), 24, TEXT)
    head.typeface = android.graphics.Typeface.DEFAULT_BOLD
    val top = LinearLayout(this)
    top.gravity = Gravity.CENTER_VERTICAL
    top.addView(head, LinearLayout.LayoutParams(0, -2, 1f))
    val done = icon("@check", T("drawing.done"), ACCENT)
    done.setOnClickListener { airClose() }
    top.addView(done, LinearLayout.LayoutParams(dp(52), dp(44)))
    page.addView(top)
    page.addView(note(T("air.hint")), gap(6))

    val pad = FrameLayout(this)
    pad.background = box(CARD, 18)
    val hint = label(T("air.pad"), 15, MUTED)
    hint.gravity = Gravity.CENTER; hint.setPadding(dp(8), 0, dp(64), 0)   // clear of the scroll strip
    pad.addView(hint, FrameLayout.LayoutParams(-1, -1))
    var downAt = 0L; var sx = 0f; var sy = 0f; var moved = false; var scrollY = 0f; var scrollCarry = 0f
    var lastTapUp = 0L; var dtap = false; var lx = 0f; var ly = 0f; var lt = 0L; var drag = false; var longDone = false; val dragCarry = FloatArray(2)
    val slop = dp(8)
    pad.setOnTouchListener { v, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downAt = ev.eventTime; sx = ev.x; sy = ev.y; lx = ev.x; ly = ev.y; lt = ev.eventTime; moved = false; drag = false; scrollY = 0f; Air.touching = true; Air.padRest = true; dtap = ev.eventTime - lastTapUp < 300 }   // a second touch soon after a tap: a drag if it moves
            MotionEvent.ACTION_POINTER_DOWN -> { moved = true; scrollY = (ev.getY(0) + ev.getY(1)) / 2 }   // two fingers: a wheel, never a click
            MotionEvent.ACTION_POINTER_UP -> { val i = if (ev.actionIndex == 0) 1 else 0; lx = ev.getX(i); ly = ev.getY(i); lt = ev.eventTime }   // one finger left: carry on from where it is, no jump
            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount >= 2) {
                    val y = (ev.getY(0) + ev.getY(1)) / 2
                    scrollCarry += (y - scrollY) / dp(14)            // one wheel notch per 14 dp dragged; drag down = content up, like a touch screen
                    scrollY = y
                    val n = scrollCarry.toInt()
                    if (n != 0) { scrollCarry -= n; airWheel(n) }
                } else {
                    if (!drag && (abs(ev.x - sx) > slop || abs(ev.y - sy) > slop)) { moved = true; drag = true; Air.padRest = false; if (dtap) { airButton(true); buzz() } }
                    if (drag) {                                      // a touchpad: relative moves, faster fingers go further
                        val dt = maxOf(1L, ev.eventTime - lt)
                        val ddx = (ev.x - lx) / dp(1); val ddy = (ev.y - ly) / dp(1)   // dp, so every screen feels the same
                        val speed = kotlin.math.hypot(ddx, ddy) / dt     // dp per ms
                        val k = TOUCH_GAIN * (1f + minOf(speed, 3f) * TOUCH_BOOST)
                        airMove(ddx * k, ddy * k, dragCarry)
                    }
                    lx = ev.x; ly = ev.y; lt = ev.eventTime
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                Air.touching = false; Air.padRest = false
                if (Air.buttons != 0 && !Air.clickHeld) airButton(false)           // the double-tap drag lets go
                if (ev.actionMasked == MotionEvent.ACTION_UP && !moved) {
                    val held = ev.eventTime - downAt
                    if (held < 350) { airClick(); buzz(); lastTapUp = ev.eventTime } else if (held > 600) { press(4); buzz() }   // a long press is Back
                }
                v.performClick()
            }
        }
        true
    }
    // The scroll strip on the pad's right edge: a vertical drag on it is the wheel, so one finger can scroll. It is a child that takes
    // its own touches, so the pad's one-finger moves elsewhere are untouched; two fingers on the pad still scroll.
    val strip = FrameLayout(this)
    strip.background = box(BG, 14)                                // a track darker than the pad: there, not loud
    val up = label("\u25B2", 11, MUTED); up.gravity = Gravity.CENTER
    val dn = label("\u25BC", 11, MUTED); dn.gravity = Gravity.CENTER
    strip.addView(up, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { topMargin = dp(10) })
    strip.addView(dn, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dp(10) })
    var stripY = 0f; var stripT = 0L; var stripCarry = 0f
    strip.setOnTouchListener { v, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { v.parent.requestDisallowInterceptTouchEvent(true); stripY = ev.y; stripT = ev.eventTime; stripCarry = 0f; Air.touching = true; Air.padRest = false }
            MotionEvent.ACTION_MOVE -> {
                val dt = maxOf(1L, ev.eventTime - stripT)
                val dy = (ev.y - stripY) / dp(1)                       // dp
                val speed = abs(dy) / dt                               // dp per ms: a quicker drag scrolls further
                stripCarry += dy / 10f * (1f + minOf(speed, 2f))       // one wheel notch per 10 dp at a slow drag; drag down = content up
                stripY = ev.y; stripT = ev.eventTime
                val n = stripCarry.toInt()
                if (n != 0) { stripCarry -= n; airWheel(n) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { Air.touching = false; Air.padRest = false; v.performClick() }
        }
        true
    }
    pad.addView(strip, FrameLayout.LayoutParams(dp(48), -1, Gravity.END).apply { setMargins(0, dp(8), dp(8), dp(8)) })
    page.addView(pad, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })

    val hold = button("@lock|" + T("air.hold_move"), SOFT)           // the gyroscope moves the pointer only while a finger rests on the pad
    hold.textSize = 14f; hold.maxLines = 1
    hold.setOnClickListener { Air.holdToMove = !Air.holdToMove; hold.setOn(Air.holdToMove, 14) }
    page.addView(hold, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) })
    val click = button("@mouse|" + T("air.click"), ACCENT)             // the thumb's: tap = click, hold = button down while the phone moves
    click.textSize = 18f; click.maxLines = 1
    click.setOnTouchListener { v, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { Air.clickHeld = true; airButton(true); buzz(); v.isPressed = true }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { Air.clickHeld = false; airButton(false); v.isPressed = false; if (ev.actionMasked == MotionEvent.ACTION_UP) v.performClick() }
        }
        true
    }
    page.addView(click, LinearLayout.LayoutParams(-1, dp(76)).apply { topMargin = dp(10) })
    val row = LinearLayout(this)
    val back = button("@back|" + T("rkey.back"), BTN)
    back.setOnClickListener { press(4) }
    val home = button("@home|" + T("rkey.home"), BTN)
    home.setOnClickListener { press(3) }
    val pause = button("@pause|" + T("air.pause"), SOFT)
    pause.setOnClickListener {
        Air.paused = !Air.paused
        pause.setLabel(if (Air.paused) "@play|" + T("air.resume") else "@pause|" + T("air.pause"))
        pause.setOn(Air.paused, 14)
    }
    for ((i, b) in listOf(back, home, pause).withIndex()) {
        b.textSize = 14f; b.maxLines = 1; b.setPadding(dp(8), 0, dp(8), 0)   // "Tạm dừng" wrapped and was cut in a third of the row
        row.addView(b, LinearLayout.LayoutParams(0, dp(52), 1f).apply { if (i > 0) leftMargin = dp(8) })
    }
    page.addView(row, gap(12))
    root.addView(page, FrameLayout.LayoutParams(-1, -1))
    return root
}

/** Holds the pointer's button down or lets it go; the gyroscope and the finger both drag while it is down. */
private fun MainActivity.airButton(down: Boolean) {
    Air.freezeUntil = System.currentTimeMillis() + 80
    Air.buttons = if (down) 1 else 0
    val t = tv
    if (t != null && t.ready()) t.mouseMove(0, 0, Air.buttons) else basic?.pointerHold(down)
}

private fun MainActivity.airClick() {
    Air.freezeUntil = System.currentTimeMillis() + 80
    val t = tv
    if (t != null && t.ready()) { t.mouseMove(0, 0, 1); t.mouseMove(0, 0, 0) } else basic?.pointerClick()   // down, then up
}

private fun MainActivity.airWheel(n: Int) {
    val t = tv
    if (t != null && t.ready()) t.mouseMove(0, 0, 0, n.coerceIn(-127, 127)) else basic?.pointerScroll(n)
}

/** A test feed: when the file exists (adb shell touch /sdcard/Android/data/dev.tvremote.phone/files/air_synth) a thread sends a
 *  200 Hz circle through the same path as the gyroscope, so the send path can be measured without waving a phone. */
private fun MainActivity.airSynth() {
    val f = java.io.File(getExternalFilesDir(null), "air_synth")
    if (!f.exists()) return
    val th = Thread {
        val carry = FloatArray(2); var n = 0
        try {
            var next = System.nanoTime()
            while (true) {
                val a = n++ * 0.05
                airMove((Math.cos(a) * 4).toFloat(), (Math.sin(a) * 4).toFloat(), carry)
                next += 5_000_000L
                val d = next - System.nanoTime()
                if (d > 0) Thread.sleep(d / 1_000_000, (d % 1_000_000).toInt())
            }
        } catch (e: InterruptedException) { }
    }
    th.isDaemon = true; th.start(); Air.synth = th
}
