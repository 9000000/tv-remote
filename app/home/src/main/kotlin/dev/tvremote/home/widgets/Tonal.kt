package dev.tvremote.home.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.LayoutParams
import android.widget.LinearLayout.VERTICAL
import android.widget.TextView
import dev.tvremote.home.Font
import dev.tvremote.home.R
import dev.tvremote.home.widgets.Widgets.Facts
import dev.tvremote.home.widgets.Widgets.Moon

/**
 * The "tonal" look of the weather, system, lunar and air cards (HomeConfig "wstyle"; app/DESIGN-TV.md "Tonal cards"): an opaque warm
 * card, a label, a very large figure, a caption, and a scalloped badge with the card's icon. One fixed warm tone: TV Home does not
 * pick a colour from the wallpaper. Every part is drawn here so the four cards cannot drift apart.
 */
internal object Tonal {
    val FILL = 0xFFF6E9E0.toInt()                                      // the card
    val INK = 0xFF3B2A22.toInt()                                       // dark brown words
    val SUB = 0xFF6D564A.toInt()                                       // the caption
    val PALE = 0xFFE7CDBD.toInt()                                      // a pale badge (dark icon)
    val PALE_INK = 0xFF4A3024.toInt()
    val OLIVE = 0xFF5D6233.toInt()                                     // a dark badge (light icon)
    val OLIVE_INK = 0xFFF3EDD2.toInt()
    val BAR = 0xFF8A5F47.toInt()                                       // a bar's fill, on [TRACK]
    val TRACK = 0xFFE9D5C8.toInt()
    val WARN = 0xFFB07418.toInt(); val BAD = 0xFFB3402E.toInt()        // a bar from 70 % / 85 %
    const val RADIUS = 28                                              // dp
    const val BADGE_MAX = 100                                          // dp; smaller in a narrow card, see [badge]
    const val BUMPS = 9

    /** The badge's side: a share of the card's inner width, 44..[BADGE_MAX] dp. */
    fun badge(innerDp: Int) = (innerDp * 0.30f).toInt().coerceIn(44, BADGE_MAX)
}

/** A circle with a wavy edge (nine bumps, Material 3's "cookie") in [fill] with the vector [icon] in [ink] on it. */
internal class Scallop(c: Context, fill: Int, icon: Int, ink: Int) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val path = Path()
    private val glyph = c.getDrawable(icon)!!.mutate().apply { setTint(ink) }
    private var sized = 0

    override fun onDraw(cv: Canvas) {
        val s = minOf(width, height)
        if (s != sized) { sized = s; shape(s.toFloat()) }
        cv.drawPath(path, paint)
        val g = (s * 0.46f).toInt(); val x = (width - g) / 2; val y = (height - g) / 2
        glyph.setBounds(x, y, x + g, y + g); glyph.draw(cv)
    }

    /** r(a) = R (1 - k + k cos(9a)): the bumps reach R, the dips 1 - 2k of it; sampled every 3 degrees, so the edge stays smooth. */
    private fun shape(s: Float) {
        val r = s / 2f; val k = 0.055f
        path.rewind()
        for (i in 0..120) {
            val a = i * 2 * Math.PI / 120
            val rr = r * (1 - k + k * Math.cos(Tonal.BUMPS * a)).toFloat()
            val px = r + rr * Math.cos(a).toFloat(); val py = r + rr * Math.sin(a).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }
}

/** A thick rounded bar of [part] on a pale track, a small dot at the track's end. */
internal class TonalBar(c: Context, private val part: Float, private val fill: Int) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    override fun onDraw(cv: Canvas) {
        val h = height.toFloat(); val r = h / 2
        paint.color = Tonal.TRACK; box.set(0f, 0f, width.toFloat(), h); cv.drawRoundRect(box, r, r, paint)
        paint.color = fill; box.set(0f, 0f, maxOf(h, width * part.coerceIn(0f, 1f)), h); cv.drawRoundRect(box, r, r, paint)
        paint.color = if (part >= 0.97f) Tonal.TRACK else fill                              // a full bar swallows the dot: it shows only on the track
        cv.drawCircle(width - r, r, h * 0.13f, paint)
    }
}

/** The tonal card itself: opaque, 28 dp corners, no edge, no shadow; the same padding as the glass one. */
internal fun Widgets.tonalCard(compact: Boolean = false) = LinearLayout(a).apply {
    orientation = VERTICAL
    background = GradientDrawable().apply { setColor(Tonal.FILL); cornerRadius = dp(Tonal.RADIUS).toFloat() }
    if (compact) setPadding(dp(16), dp(8), dp(16), dp(8)) else setPadding(dp(20), dp(14), dp(20), dp(14))
}

internal fun Widgets.ink(t: String, size: Float, color: Int = Tonal.INK) = Font.on(TextView(a)).apply {
    text = t; textSize = size; setTextColor(color); maxLines = 1; includeFontPadding = false
    ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.START
}

/**
 * The three lines and the badge: [label] on top, [value] large, [caption] under it, the badge at the top right ([olive]: dark with a
 * light icon, else pale with a dark one). [width] is the card's width in px; [compact]: half the height, the figure smaller.
 */
internal fun Widgets.tonalBody(card: LinearLayout, label: String, value: String, caption: String, icon: Int, olive: Boolean, width: Int, compact: Boolean) {
    val side = dp(if (compact) 48 else Tonal.badge(((width - dp(40)) / dp(1)).coerceAtLeast(1)))
    val root = FrameLayout(a)
    val col = LinearLayout(a).apply { orientation = VERTICAL }
    val room = side + dp(8)                                            // the label and the figure stop short of the badge; the caption has the full width
    col.addView(ink(label, if (compact) 12f else 14f), LayoutParams(-1, -2).apply { rightMargin = room })
    col.addView(Shrink(a, if (compact) 26f else 64f, if (compact) 18f else 34f).apply {
        text = value; setTextColor(Tonal.INK); maxLines = 1; typeface = Font.regular; includeFontPadding = false; gravity = Gravity.CENTER_VERTICAL or Gravity.START
    }, LayoutParams(-1, 0, 1f).apply { rightMargin = room })
    col.addView(Shrink(a, if (compact) 12f else 14f, 10f).apply {            // one line, smaller before cut
        text = caption; setTextColor(Tonal.SUB); maxLines = 1; typeface = Font.regular; includeFontPadding = false
    }, LayoutParams(-1, -2))
    root.addView(col, FrameLayout.LayoutParams(-1, -1))
    root.addView(Scallop(a, if (olive) Tonal.OLIVE else Tonal.PALE, icon, if (olive) Tonal.OLIVE_INK else Tonal.PALE_INK),
        FrameLayout.LayoutParams(side, side, Gravity.TOP or Gravity.END))
    card.addView(root, LayoutParams(-1, -1))
}

private fun Widgets.skyIcon(w: Weather.Forecast) = when (w.kind) {
    Weather.Kind.CLEAR -> if (w.night) R.drawable.ic_moon else R.drawable.ic_wake
    Weather.Kind.PARTLY -> R.drawable.ic_weather
    Weather.Kind.STORM -> R.drawable.ic_zap
    else -> R.drawable.ic_cloud
}

internal fun Widgets.weatherTonal(card: LinearLayout, f: Facts, width: Int, compact: Boolean) {
    val w = f.wx
    if (w == null) { tonalBody(card, a.getString(R.string.w_weather), "—", a.getString(R.string.wx_none), R.drawable.ic_weather, false, width, compact); return }
    val d = w.days[0]
    tonalBody(card, w.place.ifEmpty { a.getString(R.string.wx_near) }, "${w.temp}°", a.getString(R.string.ton_sky, a.getString(kindWords(w.kind)), d.max, d.min),
        skyIcon(w), false, width, compact)
}

internal fun Widgets.airTonal(card: LinearLayout, f: Facts, width: Int, compact: Boolean) {
    val x = f.air
    if (x == null) { tonalBody(card, a.getString(R.string.w_air), "—", a.getString(R.string.aq_none), R.drawable.ic_wind, true, width, compact); return }
    tonalBody(card, a.getString(R.string.w_air), x.aqi.toString(), airLine(x), R.drawable.ic_wind, true, width, compact)
}

/** "Trung bình, PM2.5 25". */
internal fun Widgets.airLine(x: Weather.Air) = a.getString(R.string.aq_line, a.getString(airWords(Weather.airLevel(x.aqi))), x.pm25)

internal fun Widgets.airWords(level: Int) = when (level) {
    0 -> R.string.aq_0; 1 -> R.string.aq_1; 2 -> R.string.aq_2; 3 -> R.string.aq_3; 4 -> R.string.aq_4; else -> R.string.aq_5
}

internal fun Widgets.lunarTonal(card: LinearLayout, m: Moon, width: Int, compact: Boolean) {
    val d = m.day; val r = a.resources
    val (can, chi) = Lunar.canChi(d.year)
    val year = a.getString(R.string.ton_year, a.getString(R.string.lunar_year, r.getStringArray(R.array.lunar_can)[can], r.getStringArray(R.array.lunar_chi)[chi]))
    val h = m.hol
    val caption = if (h == null) "" else holidayWords(h.key).let { name ->
        if (h.days == 0) a.getString(R.string.h_today, name) else r.getQuantityString(R.plurals.h_in, h.days, name, h.days)
    }
    tonalBody(card, year + if (d.leap) a.getString(R.string.lunar_leap) else "", "${d.day}/${d.month}", caption, R.drawable.ic_calendar, false, width, compact)
}

internal fun Widgets.holidayWords(key: String) = a.getString(when (key) {
    "newyear" -> R.string.h_newyear; "liberation" -> R.string.h_liberation; "labour" -> R.string.h_labour
    "national" -> R.string.h_national
    "xmas" -> R.string.h_xmas; "tet" -> R.string.h_tet; "hung" -> R.string.h_hung; "buddha" -> R.string.h_buddha
    "duanwu" -> R.string.h_duanwu; "vulan" -> R.string.h_vulan; else -> R.string.h_midautumn
})

/** CPU, memory and storage as rows: a small icon, the name, the figure at the right, a thick bar under it. The half-height card keeps two. */
internal fun Widgets.systemTonal(card: LinearLayout, f: Facts, compact: Boolean) {
    val gb = 1024.0 * 1024 * 1024
    fun part(p: Pair<Long, Long>) = if (p.second > 0) p.first.toFloat() / p.second else 0f
    fun size(x: Double) = if (x < 10) "%.1f".format(x) else "%.0f".format(x)
    fun pair(p: Pair<Long, Long>) = "${size(p.first / gb)} / ${size(p.second / gb)} GB"
    class Row(val icon: Int, val name: String, val value: String, val part: Float)
    val cpu = Row(R.drawable.ic_cpu, a.getString(R.string.w_cpu), f.cpu?.let { "${Math.round(it * 100)}%" } ?: "—", f.cpu ?: 0f)
    val ram = Row(R.drawable.ic_memory, a.getString(R.string.w_ram), pair(f.ram), part(f.ram))
    val disk = Row(R.drawable.ic_drive, a.getString(R.string.w_disk), pair(f.disk), part(f.disk))
    val rows = if (compact) listOf(cpu, ram) else listOf(cpu, ram, disk)
    val box = LinearLayout(a).apply { orientation = VERTICAL }
    for (r in rows) {
        val tone = if (r.part >= 0.85f) Tonal.BAD else if (r.part >= 0.7f) Tonal.WARN else Tonal.BAR
        val line = LinearLayout(a).apply { orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        val top = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(ImageView(a).apply { setImageResource(r.icon); imageTintList = android.content.res.ColorStateList.valueOf(Tonal.INK) }, LayoutParams(dp(if (compact) 16 else 20), dp(if (compact) 16 else 20)))
        top.addView(ink(r.name, if (compact) 12f else 14f).apply { typeface = Font.medium }, LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        top.addView(Shrink(a, if (compact) 12f else 14f, 9f).apply {
            text = r.value; setTextColor(Tonal.INK); maxLines = 1; typeface = Font.medium; includeFontPadding = false; gravity = Gravity.END
        }, LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        line.addView(top, LayoutParams(-1, -2))
        line.addView(TonalBar(a, r.part, tone), LayoutParams(-1, dp(if (compact) 8 else 10)).apply { topMargin = dp(if (compact) 3 else 6) })
        box.addView(line, LayoutParams(-1, 0, 1f))
    }
    card.addView(box, LayoutParams(-1, -1))
}
