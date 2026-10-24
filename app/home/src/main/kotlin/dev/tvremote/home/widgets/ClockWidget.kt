package dev.tvremote.home.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.LayoutParams
import android.widget.LinearLayout.VERTICAL
import android.widget.TextView
import dev.tvremote.home.Font
import dev.tvremote.home.HomeConfig
import dev.tvremote.home.Store
import dev.tvremote.home.ui.Frost
import dev.tvremote.home.widgets.Widgets.Companion.DIM
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/**
 * The clock card, [mode] 0: a pill in a stack (the face, the time and the date), 1: standing alone, the face filling the card,
 * 2: big, the face with the time and the date beside it. The look is HomeConfig "wstyle"; the digits follow the face (see [ClockFace]).
 */
internal fun Widgets.clock(mode: Int): View {
    val style = Store.get(a).optJSONObject("wstyle")?.optString("clock")?.takeIf { it in HomeConfig.STYLES["clock"]!! } ?: "classic"
    val locale = resources.configuration.locales[0]
    val h24 = Store.get(a).optBoolean("clock24", true)
    val timeF = SimpleDateFormat(if (h24) "HH:mm" else "h:mm", locale)
    // the small form is one short line, never broken inside the date: "T4, 21/10", "Wed 21 Oct"
    val dateF = SimpleDateFormat(if (mode != 0) android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMM")
        else if (locale.language == "vi") "d/M" else "EEE d MMM", locale)
    val time = text("", if (mode == 2) 34f else 22f, Color.WHITE).apply { typeface = Font.medium }
    val date = text("", if (mode == 2) 15f else 12f, DIM).apply { maxLines = if (mode == 1) 2 else 1; ellipsize = TextUtils.TruncateAt.END }
    // big beside two other big cards: "Chủ Nhật, 25 thg 10" broke in two; it falls to the short form ("CN, 25/10") when it does not fit
    val shortF = SimpleDateFormat(if (locale.language == "vi") "d/M" else "EEE d MMM", locale)
    var shown = Date()
    fun put(now: Date) {
        shown = now
        val full = dateF.format(now).replaceFirstChar { it.uppercase() }
        val fits = mode == 1 || (mode == 2 && (date.width == 0 || date.paint.measureText(full) <= date.width - date.paddingLeft - date.paddingRight))
        date.text = if (fits) full else (if (locale.language == "vi") viDay(now) + ", " else "") + shortF.format(now)
    }
    if (mode == 2) date.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ -> if (r - l != or - ol) date.post { put(shown) } }
    val face = ClockFace(this@clock, a, style) { now -> time.text = timeF.format(now); put(now) }
    return LinearLayout(a).apply {
        Frost.on(this, dp(if (mode == 0) 18 else 22).toFloat(), dp(1))
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        if (mode == 1) {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(face, LayoutParams(-1, -1))
            return@apply
        }
        setPadding(dp(if (mode == 0) 10 else 18), dp(if (mode == 0) 8 else 14), dp(14), dp(if (mode == 0) 8 else 14))
        addView(face, if (mode == 0) LayoutParams(dp(62), dp(62)) else LayoutParams(0, -1, 0.9f).apply { })
        addView(LinearLayout(a).apply {
            orientation = VERTICAL
            addView(time); addView(date, LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }, if (mode == 0) LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) } else LayoutParams(0, -2, 1f).apply { leftMargin = dp(16) })
    }
}

/** "T4" / "CN": the short Vietnamese weekday. */
internal fun Widgets.viDay(d: Date) = Calendar.getInstance().apply { time = d }.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) "CN" else "T$it" }

/**
 * An analog face drawn by hand: ticks, numbers, hour and minute hands. It redraws itself once a minute while it is on screen (a
 * hidden window, an app in front or a screen off draws nothing, so the loop ends by itself). No second hand: its 30 frames a
 * second redrew Home on the thread a panel over it shares, and held Customize's opening on the FPT box.
 * [onMinute] gets the time each time the minute changes (the digits beside it).
 */
internal class ClockFace(private val host: Widgets, c: Context, val style: String, val onMinute: (Date) -> Unit) : View(c) {
    private fun dp(n: Number) = host.dp(n)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cal = Calendar.getInstance()
    private var minute = -1
    private val serif = style == "roman"
    private val dark = style != "light"
    private val ink = if (dark) Color.WHITE else 0xFF1C1C1E.toInt()
    private val dim = if (dark) 0x66FFFFFF else 0x55000000
    private val orange = 0xFFFF9500.toInt()
    private val gold = 0xFFE9D8A6.toInt()
    private val frame = Runnable { invalidate() }
    override fun onVisibilityAggregated(v: Boolean) { super.onVisibilityAggregated(v); if (v) invalidate() }
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = minOf(w, h) / 2f; val cx = w / 2f; val cy = h / 2f
        val now = System.currentTimeMillis()
        cal.timeInMillis = now
        if (cal.get(Calendar.MINUTE) != minute) { minute = cal.get(Calendar.MINUTE); val d = Date(now); post { onMinute(d) } }
        val min = cal.get(Calendar.MINUTE).toFloat()
        val hr = cal.get(Calendar.HOUR) + min / 60f
        paint.style = Paint.Style.FILL
        paint.color = when (style) { "light" -> 0xFFF4F4F6.toInt(); "roman" -> 0xFF211C17.toInt(); "minimal" -> 0xFF161618.toInt(); else -> 0xFF1C1C1E.toInt() }
        cv.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = maxOf(1f, r * 0.012f)
        paint.color = if (dark) 0x33FFFFFF else 0x22000000
        cv.drawCircle(cx, cy, r - paint.strokeWidth / 2, paint)
        paint.strokeCap = Paint.Cap.BUTT
        val edge = r * 0.93f
        for (i in 0 until 60) {
            val hour = i % 5 == 0
            if (style == "minimal" && !hour) continue
            if (serif && !hour) continue
            val len = when { style == "minimal" -> r * 0.14f; hour -> r * 0.09f; else -> r * 0.045f }
            paint.strokeWidth = if (style == "minimal") r * 0.035f else if (hour) maxOf(1.5f, r * 0.022f) else maxOf(1f, r * 0.012f)
            paint.color = if (hour) (if (serif) gold else ink) else dim
            if (!dark && !hour) paint.color = 0x44000000
            line(cv, cx, cy, i * 6f, edge - len, edge, paint)
        }
        if (style != "minimal" && r >= dp(34)) {                     // a face this small: ticks only, digits would be specks
            paint.style = Paint.Style.FILL
            paint.typeface = if (serif) Typeface.SERIF else Font.medium
            paint.textAlign = Paint.Align.CENTER
            paint.color = if (serif) gold else ink
            val quarters = arrayOf("XII", "III", "VI", "IX")
            if (serif) paint.textSize = r * 0.2f else paint.textSize = r * 0.17f
            val fm2 = paint.fontMetrics
            val ring = r * (if (serif) 0.66f else 0.69f)
            val count = if (serif) 4 else 12
            for (n in 0 until count) {
                val a = Math.toRadians((if (serif) n * 90.0 else (n + 1) * 30.0) - 90)
                val label = if (serif) quarters[n] else (n + 1).toString()
                cv.drawText(label, cx + (ring * Math.cos(a)).toFloat(), cy + (ring * Math.sin(a)).toFloat() - (fm2.ascent + fm2.descent) / 2, paint)
            }
        }
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        val thin = style == "minimal"
        paint.color = if (serif) gold else ink
        paint.strokeWidth = r * (if (thin) 0.035f else 0.075f)
        line(cv, cx, cy, hr * 30f, 0f, r * (if (serif) 0.45f else 0.5f), paint)
        paint.strokeWidth = r * (if (thin) 0.025f else 0.055f)
        line(cv, cx, cy, min * 6f, 0f, r * (if (serif) 0.68f else 0.74f), paint)
        paint.style = Paint.Style.FILL
        paint.color = if (serif) gold else ink; cv.drawCircle(cx, cy, r * 0.065f, paint)
        paint.color = orange; cv.drawCircle(cx, cy, r * 0.03f, paint)
        removeCallbacks(frame)                                      // one pending frame, however many draws asked
        if (isShown && windowVisibility == VISIBLE) {
            postDelayed(frame, 60_000 - now % 60_000)
        }
    }
    /** A stroke at [deg] clockwise from 12, from [from] to [to] of the centre. */
    private fun line(cv: Canvas, cx: Float, cy: Float, deg: Float, from: Float, to: Float, p: Paint) {
        val a = Math.toRadians(deg - 90.0); val co = Math.cos(a).toFloat(); val si = Math.sin(a).toFloat()
        cv.drawLine(cx + co * from, cy + si * from, cx + co * to, cy + si * to, p)
    }
}
