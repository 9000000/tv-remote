package dev.tvremote.home.widgets

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.LayoutParams
import android.widget.LinearLayout.VERTICAL
import dev.tvremote.home.Font
import dev.tvremote.home.R
import dev.tvremote.home.widgets.Widgets.Companion.DIM
import dev.tvremote.home.widgets.Widgets.Facts
import dev.tvremote.tvui.Motion
import java.util.Locale

/** The Market card (gold, the bank's exchange rates, Bitcoin) and the News card (RSS headlines), drawn like the other glass cards. */

private val UP = 0xFF6BE8A6.toInt()
private val DOWN = 0xFFFF8A80.toInt()
private const val SHADOW = 0x80000000.toInt()
private const val THUMB = 0x26FFFFFF                                     // a picture's place while it loads

private fun Widgets.vnd(v: Double): String = String.format(Locale.getDefault(), if (v >= 1000) "%,.0f" else "%.2f", v)
private fun Widgets.million(v: Long): String = String.format(Locale.getDefault(), "%.1f", v / 1_000_000.0)

/** One line of the card: a dim name on the left, the figure on the right, shrunk before it is cut. */
private fun Widgets.row(name: String, value: String, size: Float, tint: Int = Color.WHITE) = LinearLayout(a).apply {
    orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    addView(text(name, size, DIM), LayoutParams(-2, -2))
    addView(Shrink(a, size, 9f).apply {
        text = value; setTextColor(tint); maxLines = 1; typeface = Font.medium; gravity = Gravity.END; includeFontPadding = false
        setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(), SHADOW)
    }, LayoutParams(0, -2, 1f).apply { leftMargin = dp(10) })
}

/** The lines the card can show, in order: [kind] "gold" / "rate" / "btc", the name, the figure, a colour. */
private class Line(val kind: String, val name: String, val value: String, val tint: Int = Color.WHITE)

private fun Widgets.lines(m: Market.Snap, show: String, btc: Boolean): List<Line> {
    val out = ArrayList<Line>()
    val unit = a.getString(R.string.mk_unit)
    if (show != "rates") m.gold?.let { g ->
        out += Line("gold", a.getString(R.string.mk_sjc), "${million(g.sjcBuy)} / ${million(g.sjcSell)} $unit")
        out += Line("gold", a.getString(R.string.mk_ring), "${million(g.ringBuy)} / ${million(g.ringSell)} $unit")
    }
    if (show != "gold") for (r in m.rates.take(if (show == "rates") 5 else 3))
        out += Line("rate", r.code, if (r.buy > 0) "${vnd(r.buy)} / ${vnd(r.sell)}" else vnd(r.sell))
    if (btc) m.btc?.let { b -> out += Line("btc", "BTC", "$" + String.format(Locale.getDefault(), "%,.0f", b.usd) + "  " + (if (b.pct >= 0) "▲" else "▼") + String.format(Locale.getDefault(), "%.1f%%", Math.abs(b.pct)), if (b.pct >= 0) UP else DOWN) }
    return out
}

private fun Widgets.updated(m: Market.Snap) = a.getString(R.string.mk_upd, hm(m.at))

/** The card at its full height: the title with the time of the last update, a line per price. */
internal fun Widgets.marketFull(card: LinearLayout, f: Facts) {
    val m = f.market
    if (m == null || m.empty) { card.addView(head(a.getString(R.string.w_market), "")); card.addView(note(a.getString(R.string.mk_none))); return }
    card.gravity = Gravity.CENTER_VERTICAL
    card.addView(head(a.getString(R.string.w_market), hm(m.at)))
    val l = lines(m, opt("market", "show"), opt("market", "btc") == "on")
    val box = LinearLayout(a).apply { orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL }
    for (x in l) box.addView(row(x.name, x.value, 13f, x.tint), LayoutParams(-1, 0, 1f))
    card.addView(box, LayoutParams(-1, 0, 1f).apply { topMargin = dp(4) })
}

/** Half the height, or a narrow column: the first two prices and the time. */
internal fun Widgets.marketHalf(card: LinearLayout, f: Facts) {
    val m = f.market
    card.gravity = Gravity.CENTER_VERTICAL
    if (m == null || m.empty) { card.addView(head(a.getString(R.string.w_market), "")); card.addView(note(a.getString(R.string.mk_none))); return }
    val l = lines(m, opt("market", "show"), opt("market", "btc") == "on")
    // the headline pair: the gold sell price and the dollar, whichever the options leave
    val pick = (l.filter { it.kind == "gold" }.take(1) + l.filter { it.kind == "rate" }.take(1) + l.filter { it.kind == "btc" }).ifEmpty { l }.take(2)
    for ((i, x) in pick.withIndex()) card.addView(row(x.name, x.value, 12f, x.tint), LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(2) })
    card.addView(text(updated(m), 11f, DIM), LayoutParams(-1, -2).apply { topMargin = dp(2) })
}

/** One scrolling line: every price after the other. The marquee runs only where motion is on and not calm (a weak box would redraw the cards each frame). */
internal fun Widgets.marketTicker(card: LinearLayout, f: Facts, big: Boolean) {
    val m = f.market
    card.gravity = Gravity.CENTER_VERTICAL
    if (m == null || m.empty) { card.addView(note(a.getString(R.string.mk_none))); return }
    val l = lines(m, opt("market", "show"), opt("market", "btc") == "on")
    val moving = Motion.on && !Motion.calm
    val sep = "   ·   "
    val line = l.joinToString(sep) { it.name + " " + it.value.substringAfter(" / ", it.value).removeSuffix(" " + a.getString(R.string.mk_unit)) }       // the sell figures only: a line to glance at
    val tv = text(if (moving) line + sep + line else line, if (big) 20f else 15f, Color.WHITE).apply {
        typeface = Font.medium
        if (moving) { ellipsize = TextUtils.TruncateAt.MARQUEE; marqueeRepeatLimit = -1; isSelected = true; isFocusable = false }
    }
    card.addView(text(a.getString(R.string.w_market) + " · " + hm(m.at), 11f, DIM), LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
    card.addView(tv, LayoutParams(-1, -2))
}

// ---- news ----

/** "5 min ago": the time of a headline in TV Home's words. */
internal fun Widgets.ago(at: Long): String {
    if (at <= 0) return ""
    val m = ((System.currentTimeMillis() - at) / 60_000).coerceAtLeast(0)
    return when { m < 60 -> a.getString(R.string.ago_m, maxOf(1, m).toInt()); m < 1440 -> a.getString(R.string.ago_h, (m / 60).toInt()); else -> a.getString(R.string.ago_d, (m / 1440).toInt()) }
}

private fun Widgets.thumb(url: String, w: Int, h: Int): View = ImageView(a).apply {
    scaleType = ImageView.ScaleType.CENTER_CROP
    background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(THUMB) }
    clipToOutline = true
    outlineProvider = object : android.view.ViewOutlineProvider() { override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(10).toFloat()) } }
    if (url.isNotEmpty()) Pics.into(a, this, url, w)
}

private fun Widgets.byline(i: News.Item) = listOf(i.src, ago(i.at)).filter { it.isNotEmpty() }.joinToString(" · ")

/** The full card: the picture of the chosen headline on the left, three headlines on the right (the chosen one bright, its source and time under it). */
internal fun Widgets.newsFull(card: LinearLayout, f: Facts, width: Int) {
    val items = f.news
    card.addView(head(a.getString(R.string.w_news), ""))
    if (items.isEmpty()) { card.addView(note(a.getString(R.string.nw_none))); return }
    newsCount = minOf(items.size, 3); newsAt = newsAt.coerceIn(0, newsCount - 1); newsShown = newsAt
    val sel = newsAt
    val body = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val pw = minOf(dp(120), (width - dp(40)) * 2 / 5)
    body.addView(thumb(items[sel].image, pw, pw), LayoutParams(pw, LayoutParams.MATCH_PARENT).apply { topMargin = dp(6); bottomMargin = dp(2) })
    val col = LinearLayout(a).apply { orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL }
    for ((i, it) in items.take(3).withIndex()) {
        val on = i == sel
        col.addView(text(it.title, 13f, if (on) Color.WHITE else DIM).apply {
            maxLines = if (on) 3 else 1; if (on) typeface = Font.medium; ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(5) })
        if (on) col.addView(text(byline(it), 11f, DIM), LayoutParams(-1, -2).apply { topMargin = dp(1) })
    }
    body.addView(col, LayoutParams(0, -1, 1f).apply { leftMargin = dp(12) })
    card.addView(body, LayoutParams(-1, 0, 1f))
}

/** Half the height: the picture, then the headline and its source (the headline turns every two minutes, the chosen one when the card is focused). */
internal fun Widgets.newsHalf(card: LinearLayout, f: Facts) {
    val items = f.news
    card.gravity = Gravity.CENTER_VERTICAL
    if (items.isEmpty()) { card.addView(head(a.getString(R.string.w_news), "")); card.addView(note(a.getString(R.string.nw_none))); return }
    newsCount = minOf(items.size, 3); newsAt = newsAt.coerceIn(0, newsCount - 1)
    val i = if (newsFocused) newsAt else ((System.currentTimeMillis() / 120_000) % items.size).toInt()
    newsShown = i
    val row = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    row.addView(thumb(items[i].image, dp(64), dp(64)), LayoutParams(dp(64), dp(56)))
    val col = LinearLayout(a).apply { orientation = VERTICAL }
    col.addView(text(items[i].title, 12f, Color.WHITE).apply { maxLines = 3; typeface = Font.medium })
    col.addView(text(byline(items[i]), 11f, DIM))
    row.addView(col, LayoutParams(0, -2, 1f).apply { leftMargin = dp(10) })
    card.addView(row, LayoutParams(-1, -2))
}
