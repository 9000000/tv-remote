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
    var sub = false                                                  // true when the game rotation vector stands in for a missing gyroscope
}

private const val GAIN = 700f             // HID counts per radian turned: about a screen width for a wrist's sweep
private const val DEAD = 0.025f           // rad/s below which the hand's tremor is ignored
private const val BOOST = 0.45f           // extra speed per rad/s, so slow moves stay fine and fast ones reach the corner

internal fun MainActivity.airOpen() {
    val t = tv
    val b = basic
    val androidOk = t != null && t.ready()
    if (!androidOk && (b == null || !b.ready() || "mouse" !in Remote.Kind.caps(b.kind))) { say(T("air.needs_adb"), true); return }
    val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    val rot = if (gyro == null) sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) else null
    if (gyro == null && rot == null) { say(T("air.no_gyro"), true); return }
    airClose()
    Air.paused = false
    Air.sub = gyro == null
    Air.sensors = sm
    if (androidOk) t!!.mouseOpen()
    val view = airView()
    (findViewById<ViewGroup>(android.R.id.content)).addView(view, FrameLayout.LayoutParams(-1, -1))
    Air.host = view
    airListen(sm, gyro ?: rot!!)
}

/** Leaves the screen: sensor off, the TV's mouse (and its pointer) gone. True when it was open (the Back button). */
internal fun MainActivity.airClose(): Boolean {
    val view = Air.host ?: return false
    Air.listener?.let { Air.sensors?.unregisterListener(it) }
    Air.listener = null
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
    if (t != null && t.ready()) t.mouseMove(ix, iy) else basic?.pointer(ix, iy)
}

private fun MainActivity.airListen(sm: SensorManager, sensor: Sensor) {
    val carry = FloatArray(2)
    var last = 0L
    val prev = FloatArray(3)                                         // game rotation vector: the last yaw / pitch
    var have = false
    val l = object : SensorEventListener {
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        override fun onSensorChanged(e: SensorEvent) {
            if (Air.paused) { last = 0; have = false; return }
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
    sm.registerListener(l, sensor, 16_000)                            // ~60 Hz asked; the system gives what it can
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
    hint.gravity = Gravity.CENTER
    pad.addView(hint, FrameLayout.LayoutParams(-1, -1))
    var downAt = 0L; var sx = 0f; var sy = 0f; var moved = false; var scrollY = 0f; var scrollCarry = 0f
    pad.setOnTouchListener { v, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downAt = ev.eventTime; sx = ev.x; sy = ev.y; moved = false; scrollY = 0f }
            MotionEvent.ACTION_POINTER_DOWN -> { moved = true; scrollY = (ev.getY(0) + ev.getY(1)) / 2 }   // two fingers: a wheel, never a click
            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount >= 2) {
                    val y = (ev.getY(0) + ev.getY(1)) / 2
                    scrollCarry += (y - scrollY) / dp(14)            // one wheel notch per 14 dp dragged; drag down = content up, like a touch screen
                    scrollY = y
                    val n = scrollCarry.toInt()
                    if (n != 0) { scrollCarry -= n; airWheel(n) }
                } else if (abs(ev.x - sx) > dp(12) || abs(ev.y - sy) > dp(12)) moved = true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && ev.eventTime - downAt < 350) { airClick(); buzz() }
                v.performClick()
            }
        }
        true
    }
    page.addView(pad, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })

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

private fun MainActivity.airClick() {
    val t = tv
    if (t != null && t.ready()) { t.mouseMove(0, 0, 1); t.mouseMove(0, 0, 0) } else basic?.pointerClick()   // down, then up
}

private fun MainActivity.airWheel(n: Int) {
    val t = tv
    if (t != null && t.ready()) t.mouseMove(0, 0, 0, n.coerceIn(-127, 127)) else basic?.pointerScroll(n)
}
