package dev.tvremote.home.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.LayoutParams
import android.widget.LinearLayout.VERTICAL
import dev.tvremote.home.Font
import dev.tvremote.home.R
import dev.tvremote.home.widgets.Widgets.Companion.AMBER
import dev.tvremote.home.widgets.Widgets.Companion.CYAN
import dev.tvremote.home.widgets.Widgets.Companion.DIM
import dev.tvremote.home.widgets.Widgets.Facts
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** The sky now: the picture, the temperature, the place, the words and today's low and high; no days to come. */
internal fun Widgets.weatherHalf(card: LinearLayout, f: Facts, room: Int) {
    val w = f.wx
    card.gravity = Gravity.CENTER_VERTICAL
    if (w == null) { card.addView(head(a.getString(R.string.w_weather), "")); card.addView(note(a.getString(R.string.wx_none))); return }
    val row = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val narrow = room < dp(240)                                        // no room for the picture beside the words: the words keep whole
    if (!narrow) row.addView(Sky(a, w.kind, w.night), LayoutParams(dp(52), dp(52)))
    row.addView(text("${w.temp}°", 30f, Color.WHITE).apply { typeface = Font.medium }, LayoutParams(-2, -2).apply { if (!narrow) leftMargin = dp(10) })
    val words = LinearLayout(a).apply { orientation = VERTICAL }
    words.addView(text(w.place.ifEmpty { a.getString(R.string.wx_near) }, 14f, Color.WHITE).apply { typeface = Font.medium; if (narrow) maxLines = 2 })       // a long city wraps rather than cuts
    words.addView(text(a.getString(kindWords(w.kind)), 12f, Color.WHITE))
    words.addView(text(a.getString(R.string.wx_hi_lo, w.days[0].max, w.days[0].min), 12f, DIM))
    row.addView(words, LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
    card.addView(row, LayoutParams(-1, -2))
}

/** The sky now with today's low and high, and the next days when the card is wide enough; without an answer yet, a short line. */
/** [fixed]: in a card of the layouts' fixed height, where the days under "now" are drawn tighter or their temperatures fell off the bottom. */
internal fun Widgets.weather(card: LinearLayout, f: Facts, width: Int, fixed: Boolean = false) {
    val w = f.wx
    if (w == null) {
        card.addView(head(a.getString(R.string.w_weather), ""))
        card.addView(note(a.getString(R.string.wx_none)))
        return
    }
    val stale = System.currentTimeMillis() - w.at > 3 * 3_600_000L            // offline for hours: say when this was
    card.addView(head(w.place.ifEmpty { a.getString(R.string.wx_near) }, if (stale) hm(w.at) else ""))
    val body = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val now = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val temp = text("${w.temp}°", if (fixed) 28f else 30f, Color.WHITE).apply { typeface = Font.medium }
    val sky = text(a.getString(kindWords(w.kind)), 13f, Color.WHITE)
    val today = w.days[0]
    val hiLo = text(a.getString(R.string.wx_hi_lo, today.max, today.min), 12f, DIM)
    // the sky and today's high / low beside the temperature, two short lines as tall as its digits, when they fit across:
    // one row instead of three leaves the next days room under it
    val lines = maxOf(sky.paint.measureText(sky.text.toString()), hiLo.paint.measureText(hiLo.text.toString()))
    val tempW = temp.paint.measureText(temp.text.toString())
    val beside = dp(44) + dp(10) + tempW + dp(10) + lines <= width - dp(40)       // with a smaller picture of the sky
    val icon = if (beside) 44 else 56
    if (!beside && hiLo.paint.measureText(hiLo.text.toString()) > width - dp(40) - dp(icon + 12))       // a narrow card cut it to "Cao 32° · Th…"
        hiLo.text = "↑${today.max}°  ↓${today.min}°"
    val nowW = (dp(icon) + dp(if (beside) 10 else 12) + (if (beside) tempW + dp(10) + lines else maxOf(tempW, lines))).toInt()
    now.addView(Sky(a, w.kind, w.night), LayoutParams(dp(icon), dp(icon)))
    val words = LinearLayout(a).apply { orientation = if (beside) HORIZONTAL else VERTICAL; gravity = Gravity.CENTER_VERTICAL }
    words.addView(temp)
    if (beside) words.addView(LinearLayout(a).apply { orientation = VERTICAL; addView(sky); addView(hiLo) }, LayoutParams(-2, -2).apply { leftMargin = dp(10) })
    else { words.addView(sky); words.addView(hiLo) }
    now.addView(words, LayoutParams(-2, -2).apply { leftMargin = dp(if (beside) 10 else 12) })
    body.addView(now, LayoutParams(0, -2, 1f))
    val next = w.days.drop(1)
    val room = width - dp(40) - nowW - dp(24)                                 // beside "now" when wide (a day at least 52 dp), else a row under it
    val side = room >= dp(52) * next.size
    val dayW = if (side) minOf(dp(62), room / maxOf(1, next.size)) else 0
    if (next.isNotEmpty() && (side || width - dp(40) >= dp(62) * next.size)) {
        val days = LinearLayout(a).apply { orientation = HORIZONTAL }
        val names = a.resources.getStringArray(R.array.cal_dow)
        next.forEachIndexed { i, d ->
            val col = LinearLayout(a).apply { orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            val dow = try { Calendar.getInstance().apply { time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d.date)!! }.get(Calendar.DAY_OF_WEEK) - 1 } catch (e: Exception) { -1 }
            col.addView(text(names.getOrElse(dow) { "" }, 12f, DIM).apply { gravity = Gravity.CENTER })
            col.addView(Sky(a, d.kind, false), LayoutParams(dp(if (fixed && !beside) 22 else 28), dp(if (fixed && !beside) 22 else 28)).apply { topMargin = dp(if (fixed && !beside) 0 else 2) })
            col.addView(text("${d.max}° ${d.min}°", 12f, Color.WHITE).apply { gravity = Gravity.CENTER })
            days.addView(col, if (side) LayoutParams(dayW, -2).apply { if (i > 0) leftMargin = dp(2) } else LayoutParams(0, -2, 1f))
        }
        if (side) body.addView(days, LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        else {
            // "now" over the next days, the two together in the middle of what is left under the title (not stuck to it)
            val both = LinearLayout(a).apply { orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            both.addView(body, LayoutParams(-1, -2))
            both.addView(days, LayoutParams(-1, -2).apply { topMargin = dp(if (fixed && !beside) 2 else 8) })
            card.addView(both, if (fixed) LayoutParams(-1, 0, 1f) else LayoutParams(-1, -2).apply { topMargin = dp(6) })
            return
        }
    }
    card.addView(body, LayoutParams(-1, 0, 1f).apply { topMargin = dp(6) })
}

internal fun Widgets.kindWords(k: Weather.Kind) = when (k) {
    Weather.Kind.CLEAR -> R.string.wx_clear; Weather.Kind.PARTLY -> R.string.wx_partly; Weather.Kind.CLOUD -> R.string.wx_cloud
    Weather.Kind.FOG -> R.string.wx_fog; Weather.Kind.DRIZZLE -> R.string.wx_drizzle; Weather.Kind.RAIN -> R.string.wx_rain
    Weather.Kind.SNOW -> R.string.wx_snow; Weather.Kind.STORM -> R.string.wx_storm
}

/** A sky in one drawn square (sun or moon, cloud, rain, snow, bolt, fog) in the cards' colours; paints made once, drawing allocates nothing. */
internal class Sky(c: Context, val kind: Weather.Kind, val night: Boolean) : View(c) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path(); private val cut = Path(); private val box = RectF()
    private val cloudColor = 0xFFE4ECFA.toInt()

    private fun cloud(cv: Canvas, s: Float, dx: Float, dy: Float, color: Int = cloudColor) {
        fill.color = color
        box.set((0.12f + dx) * s, (0.46f + dy) * s, (0.88f + dx) * s, (0.72f + dy) * s); cv.drawRoundRect(box, 0.13f * s, 0.13f * s, fill)
        cv.drawCircle((0.38f + dx) * s, (0.50f + dy) * s, 0.17f * s, fill)
        cv.drawCircle((0.61f + dx) * s, (0.44f + dy) * s, 0.21f * s, fill)
    }

    private fun body(cv: Canvas, s: Float, cx: Float, cy: Float, r: Float) {             // the sun, or the moon at night
        if (!night) {
            fill.color = AMBER[0]; cv.drawCircle(cx * s, cy * s, r * s, fill)
            line.color = AMBER[0]; line.strokeWidth = 0.055f * s
            for (i in 0 until 8) {
                val t = Math.toRadians(i * 45.0); val c = Math.cos(t).toFloat(); val n = Math.sin(t).toFloat()
                cv.drawLine((cx + c * r * 1.4f) * s, (cy + n * r * 1.4f) * s, (cx + c * r * 1.75f) * s, (cy + n * r * 1.75f) * s, line)
            }
        } else {
            path.rewind(); path.addCircle(cx * s, cy * s, r * 1.15f * s, Path.Direction.CW)
            cut.rewind(); cut.addCircle((cx + r * 0.7f) * s, (cy - r * 0.45f) * s, r * 0.95f * s, Path.Direction.CW)
            path.op(cut, Path.Op.DIFFERENCE); fill.color = 0xFFFFE9A8.toInt(); cv.drawPath(path, fill)
        }
    }

    override fun onDraw(cv: Canvas) {
        val s = minOf(width, height).toFloat()
        line.strokeWidth = 0.055f * s
        when (kind) {
            Weather.Kind.CLEAR -> body(cv, s, 0.5f, 0.5f, 0.2f)
            Weather.Kind.PARTLY -> { body(cv, s, 0.36f, 0.36f, 0.15f); cloud(cv, s, 0.06f, 0.1f) }
            Weather.Kind.CLOUD -> cloud(cv, s, 0f, 0f)
            Weather.Kind.FOG -> {
                cloud(cv, s, 0f, -0.14f, 0xFFB9C4D8.toInt())
                line.color = DIM; line.strokeWidth = 0.06f * s
                cv.drawLine(0.2f * s, 0.74f * s, 0.8f * s, 0.74f * s, line); cv.drawLine(0.3f * s, 0.88f * s, 0.7f * s, 0.88f * s, line)
            }
            Weather.Kind.DRIZZLE, Weather.Kind.RAIN -> {
                cloud(cv, s, 0f, -0.14f, 0xFFB9C4D8.toInt())
                line.color = CYAN[0]; line.strokeWidth = 0.06f * s
                val n = if (kind == Weather.Kind.RAIN) 3 else 2
                for (i in 0 until n) { val x = (if (n == 3) 0.3f + 0.2f * i else 0.4f + 0.2f * i) * s; cv.drawLine(x, 0.74f * s, x - 0.06f * s, 0.9f * s, line) }
            }
            Weather.Kind.SNOW -> {
                cloud(cv, s, 0f, -0.14f, 0xFFB9C4D8.toInt())
                fill.color = Color.WHITE
                for (i in 0 until 3) cv.drawCircle((0.3f + 0.2f * i) * s, (if (i == 1) 0.88f else 0.8f) * s, 0.04f * s, fill)
            }
            Weather.Kind.STORM -> {
                cloud(cv, s, 0f, -0.14f, 0xFF9AA6BE.toInt())
                path.rewind(); path.moveTo(0.54f * s, 0.62f * s); path.lineTo(0.4f * s, 0.8f * s); path.lineTo(0.5f * s, 0.8f * s)
                path.lineTo(0.44f * s, 0.96f * s); path.lineTo(0.62f * s, 0.74f * s); path.lineTo(0.52f * s, 0.74f * s); path.close()
                fill.color = AMBER[0]; cv.drawPath(path, fill)
            }
        }
    }
}
