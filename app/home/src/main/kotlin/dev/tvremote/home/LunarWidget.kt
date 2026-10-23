package dev.tvremote.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import dev.tvremote.home.Widgets.Companion.ROSE
import dev.tvremote.home.Widgets.Moon

internal fun Widgets.lunar(m: Moon): View {
    val r = a.resources
    val d = m.day
    val date = a.getString(R.string.lunar_short, d.day, d.month) + if (d.leap) a.getString(R.string.lunar_leap) else ""       // "12 tháng 9": the month's name and the year made it wrap
    val near = when {
        d.month == 1 && d.day <= 3 && !d.leap -> a.getString(R.string.l_tet_now)
        m.full == 0 -> a.getString(R.string.l_full_today)
        m.new == 0 -> a.getString(R.string.l_new_today)
        m.full in 1 until m.new || m.new < 0 -> a.getString(R.string.l_full_in, m.full)
        else -> a.getString(R.string.l_new_in, m.new)
    }
    val tetNow = d.month == 1 && d.day <= 3 && !d.leap
    val sub = if (m.tet > 0 && !tetNow) near + "\n" + a.getString(R.string.l_tet_in, m.tet) else near       // a line each: "·" was left hanging at a line's end
    // the whole card counts the days in columns instead: "3 ngày / tới Rằm", "108 ngày / tới Tết" (the line wrapped and left the right empty)
    val counts = ArrayList<Pair<Int, Int>>()
    val today = tetNow || m.full == 0 || m.new == 0
    if (!today) counts += if (m.full in 1 until m.new || m.new < 0) m.full to R.string.l_to_full else m.new to R.string.l_to_new
    if (m.tet > 0 && !tetNow) counts += m.tet to R.string.l_to_tet
    val t = text(d.day.toString(), 19f, Color.WHITE).apply { typeface = Font.medium; gravity = Gravity.CENTER }
    val (can, chi) = Lunar.canChi(d.year)
    val year = a.getString(R.string.lunar_year, r.getStringArray(R.array.lunar_can)[can], r.getStringArray(R.array.lunar_chi)[chi])
    return pill(tile(ROSE, null, t), date, sub, 3).apply { tag = Night((d.day - 0.5f) / 29.53f, year, if (today) near else null, counts) }       // a whole card: the moon and the year fill its right
}

/** What a standing lunar card shows on the right of its day: tonight's moon and the year's name. */
internal class Night(val phase: Float, val year: String, val line: String?, val counts: List<Pair<Int, Int>>)

/** The moon at [phase] (0 new, 0.5 full, 1 new again), lit on the right while it grows: a half disc and the terminator's half ellipse. */
internal class MoonFace(c: Context, private val phase: Float) : View(c) {
    private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
    private val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF6E7C1.toInt() }
    private val path = android.graphics.Path(); private val box = RectF()
    override fun onDraw(cv: Canvas) {
        val r = minOf(width, height) / 2f; val cx = width / 2f; val cy = height / 2f
        cv.drawCircle(cx, cy, r, dark)
        val p = phase.coerceIn(0f, 1f); val waxing = p <= 0.5f
        val q = if (waxing) p else 1f - p                             // waning: the same shape, mirrored
        val w = r * Math.cos(2 * Math.PI * q).toFloat()              // the terminator's half-width: r at new, 0 at the quarter, -r at full
        path.reset()
        box.set(cx - r, cy - r, cx + r, cy + r); path.arcTo(box, -90f, 180f)
        box.set(cx - Math.abs(w), cy - r, cx + Math.abs(w), cy + r); path.arcTo(box, 90f, if (w > 0) -180f else 180f)
        path.close()
        cv.save(); if (!waxing) cv.scale(-1f, 1f, cx, cy)
        cv.drawPath(path, lit); cv.restore()
    }
}
