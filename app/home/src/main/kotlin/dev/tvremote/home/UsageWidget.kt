package dev.tvremote.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.LayoutParams
import android.widget.LinearLayout.VERTICAL
import dev.tvremote.home.Widgets.Companion.AMBER
import dev.tvremote.home.Widgets.Companion.CYAN
import dev.tvremote.home.Widgets.Companion.DIM
import dev.tvremote.home.Widgets.Facts
import java.text.SimpleDateFormat
import java.util.Date
import org.json.JSONObject

/** The two most used apps side by side, with their limits; no columns of the days. */
internal fun Widgets.usageHalf(card: LinearLayout, f: Facts, label: (String) -> String) {
    val u = f.usage
    val week = u?.optJSONArray("week")
    val days = u?.optJSONArray("days")
    var total = 0L
    if (days != null) for (i in 0 until days.length()) total += days.optLong(i)
    card.addView(head(a.getString(R.string.w_week).substringBefore(" · "), if (total > 0) span(total * 1000) else ""))
    if (u == null) { card.addView(note(a.getString(R.string.w_need_setup))); return }
    val limits = HashMap<String, JSONObject>()
    u.optJSONArray("limits")?.let { l -> for (i in 0 until l.length()) l.optJSONObject(i)?.let { limits[it.optString("pkg")] = it } }
    val rows = ArrayList<Pair<String, Long>>()
    if (week != null) for (i in 0 until minOf(2, week.length())) week.getJSONObject(i).let { rows += it.optString("pkg") to it.optLong("sec") }
    if (rows.isEmpty()) { card.addView(note(a.getString(R.string.w_none))); return }
    val max = rows.maxOf { it.second }.coerceAtLeast(1)
    val line = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    rows.forEachIndexed { i, (p, sec) -> line.addView(appRow(p, label(p), f.icons[p], sec, sec.toFloat() / max, limits[p]), LayoutParams(0, -2, 1f).apply { if (i > 0) leftMargin = dp(18) }) }
    card.addView(line, LayoutParams(-1, 0, 1f))
}

/** [narrow]: the apps only, no columns of the days. */
internal fun Widgets.usage(card: LinearLayout, f: Facts, label: (String) -> String, narrow: Boolean) {
    val u = f.usage
    val week = u?.optJSONArray("week")
    val days = u?.optJSONArray("days")
    var total = 0L
    if (days != null) for (i in 0 until days.length()) total += days.optLong(i)
    card.addView(head(a.getString(R.string.w_week).let { if (narrow) it.substringBefore(" · ") else it }, if (total > 0) span(total * 1000) else ""))
    if (u == null) { card.addView(note(a.getString(R.string.w_need_setup))); return }
    val body = LinearLayout(a).apply { orientation = HORIZONTAL }
    if (!narrow && days != null && days.length() == 7) body.addView(Columns(this@usage, a, (6 downTo 0).map { days.optLong(it) }), LayoutParams(dp(150), dp(96)))
    val list = LinearLayout(a).apply { orientation = VERTICAL }
    val limits = HashMap<String, JSONObject>()
    u.optJSONArray("limits")?.let { l -> for (i in 0 until l.length()) l.optJSONObject(i)?.let { limits[it.optString("pkg")] = it } }
    val rows = ArrayList<Pair<String, Long>>()
    if (week != null) for (i in 0 until minOf(4, week.length())) week.getJSONObject(i).let { rows += it.optString("pkg") to it.optLong("sec") }
    for (p in limits.keys) if (rows.none { it.first == p } && rows.size < 4) rows += p to (limits[p]!!.optLong("used"))       // a limited app not among the most used
    val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    if (rows.isEmpty()) list.addView(note(a.getString(R.string.w_none)))
    rows.forEachIndexed { i, (p, sec) -> list.addView(appRow(p, label(p), f.icons[p], sec, sec.toFloat() / max, limits[p]), LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(7) }) }
    body.addView(list, LayoutParams(0, -2, 1f).apply { if (body.childCount > 0) leftMargin = dp(20) })
    card.addView(body, LayoutParams(-1, -2).apply { topMargin = dp(10) })
}

/** An app's line: icon, name, its share of the week as a bar, its time; an amber "x left" when it has a limit today. */
internal fun Widgets.appRow(pkg: String, name: String, icon: Drawable?, sec: Long, part: Float, limit: JSONObject?) = LinearLayout(a).apply {
    orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    addView(ImageView(a).apply { setImageDrawable(icon); scaleType = ImageView.ScaleType.FIT_CENTER }, LayoutParams(dp(22), dp(22)))
    val col = LinearLayout(a).apply { orientation = VERTICAL }
    val line = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    line.addView(text(name, 13f, Color.WHITE), LayoutParams(0, -2, 1f))
    line.addView(text(span(sec * 1000), 13f, Color.WHITE).apply { typeface = Font.medium })
    col.addView(line)
    col.addView(Bar(this@appRow, a, part, if (limit != null) AMBER else CYAN), LayoutParams(-1, dp(4)).apply { topMargin = dp(3) })
    if (limit != null) {                                              // under the bar, not beside the name: a narrow card cut the name to "…"
        val left = (limit.optLong("min") * 60 - limit.optLong("used")).coerceAtLeast(0)
        col.addView(text(if (left <= 0) a.getString(R.string.w_used_up) else a.getString(R.string.w_left, span(left * 1000)), 12f, AMBER[0]),
            LayoutParams(-2, -2).apply { topMargin = dp(2) })
    }
    addView(col, LayoutParams(0, -2, 1f).apply { leftMargin = dp(10) })
}

/** A ring for a share, its percent inside, its name and amount under it. */
internal fun Widgets.gauge(part: Float, name: String, detail: String, colors: IntArray) = LinearLayout(a).apply {
    orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
    addView(FrameLayout(a).apply {
        addView(Ring(this@gauge, a, part, colors))
        addView(text("${(part * 100).toInt()}%", 15f, Color.WHITE).apply { typeface = Font.medium; gravity = Gravity.CENTER },
            FrameLayout.LayoutParams(-1, -1))
    }, LayoutParams(dp(62), dp(62)))
    addView(text(name, 12f, Color.WHITE).apply { gravity = Gravity.CENTER }, LayoutParams(dp(84), -2).apply { topMargin = dp(5) })
    addView(text(detail, 12f, DIM).apply { gravity = Gravity.CENTER }, LayoutParams(dp(84), -2))
}

internal fun Widgets.chips(items: List<Pair<String, Int>>) = Fit(a).apply {
    orientation = HORIZONTAL
    items.forEachIndexed { i, (t, c) ->
        addView(text(t, 12f, c).apply {
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0x1FFFFFFF) }
            setPadding(dp(9), dp(2), dp(9), dp(2))
        }, LayoutParams(-2, -2).apply { if (i > 0) leftMargin = dp(6) })
    }
}

/** A row that leaves out the chips after the first that do not fit whole: a narrow card showed a sliver of "ADB". */
internal class Fit(c: Context) : LinearLayout(c) {
    override fun onMeasure(w: Int, h: Int) {
        val room = MeasureSpec.getSize(w) - paddingLeft - paddingRight
        var used = 0
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            v.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), h)
            val need = v.measuredWidth + ((v.layoutParams as? MarginLayoutParams)?.leftMargin ?: 0)
            val fits = i == 0 || MeasureSpec.getMode(w) == MeasureSpec.UNSPECIFIED || used + need <= room
            if (fits) used += need
            val vis = if (fits) View.VISIBLE else View.GONE
            if (v.visibility != vis) v.visibility = vis                   // the same answer next time: no layout loop
        }
        super.onMeasure(w, h)
    }
}

/** A column per day, oldest first, today's in colour, the day's letter under each. */
internal class Columns(private val host: Widgets, c: Context, val secs: List<Long>) : View(c) {
    private fun dp(n: Number) = host.dp(n)
    private val sp get() = host.sp
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Font.regular; textSize = 11 * sp; textAlign = Paint.Align.CENTER; color = DIM }
    private val names = (6 downTo 0).map { d -> SimpleDateFormat("EEEEE", resources.configuration.locales[0]).format(Date(System.currentTimeMillis() - d * 86_400_000L)) }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1AFFFFFF }
    private val past = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
    private val backs = List(7) { RectF() }; private val fills = List(7) { RectF() }
    private var rad = 0f
    override fun onSizeChanged(w: Int, ht: Int, ow: Int, oh: Int) {       // the columns are laid out once, drawn from here
        val h = ht - 16 * sp; val slot = w / 7f; val bw = slot * 0.52f; rad = bw / 2.5f
        val max = (secs.maxOrNull() ?: 0L).coerceAtLeast(1)
        for (i in 0 until 7) {
            val x = slot * i + (slot - bw) / 2
            backs[i].set(x, 0f, x + bw, h)
            fills[i].set(x, (h - (h - dp(4)) * secs[i] / max).coerceAtMost(h - bw / 2), x + bw, h)
        }
        p.shader = LinearGradient(0f, fills[6].top, 0f, h, CYAN[0], CYAN[1], Shader.TileMode.CLAMP)
    }
    override fun onDraw(cv: Canvas) {
        val slot = width / 7f
        for (i in 0 until 7) {
            cv.drawRoundRect(backs[i], rad, rad, track)
            if (secs[i] > 0) cv.drawRoundRect(fills[i], rad, rad, if (i == 6) p else past)
            t.color = if (i == 6) Color.WHITE else DIM
            cv.drawText(names[i], slot * i + slot / 2, height - 3 * sp, t)
        }
    }
}
