package dev.tvremote.home.widgets

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.LayoutParams
import android.widget.LinearLayout.VERTICAL
import dev.tvremote.home.R
import dev.tvremote.home.apps.Apps
import dev.tvremote.home.widgets.Widgets.Facts

/** Most apps the card names; how many show depends on its width. */
internal const val RECENT_MAX = 6

/**
 * "Recently opened": the last apps the person opened, newest first, as icons (a name under each in the big size). TV Setup says which
 * ([Facts.recent]: its usage events in the order Android gave them, never by the clock: boxes boot with a wrong year). The card is ONE
 * focus stop; Left / Right move the mark between its icons ([Widgets.recentAt]) and leave the card past the first / last, OK opens the marked one.
 * [avail] is the width the icons may take, in px; [labels] the big size; [col] the words' colour, [ring] the mark's.
 */
internal fun Widgets.recentRow(f: Facts, label: (String) -> String, avail: Int, labels: Boolean, col: Int, ring: Int): View {
    val apps = f.recent?.optJSONArray("apps")?.let { l -> List(l.length()) { l.optString(it) } }.orEmpty().filter { it.isNotEmpty() }
    recentApps = emptyList(); recentCells.clear()
    if (apps.isEmpty()) return (if (col == Color.WHITE) text(a.getString(R.string.rc_none), 13f, col) else ink(a.getString(R.string.rc_none), 13f, col)).apply { maxLines = 3 }
    val cell = dp(if (labels) 66 else 52)
    val n = minOf(RECENT_MAX, apps.size, maxOf(1, avail / cell))
    recentApps = apps.take(n)
    if (recentAt >= n) recentAt = n - 1
    val row = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val size = dp(if (labels) 44 else 38)
    recentApps.forEachIndexed { i, p ->
        val c = LinearLayout(a).apply { orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(3), dp(5), dp(3), dp(5)); tag = ring }
        c.addView(ImageView(a).apply {
            setImageDrawable(f.icons[p]); scaleType = ImageView.ScaleType.FIT_CENTER
            clipToOutline = true                                       // rounded as the grid's tiles: a square icon (YouTube's white one) read as a hole in the glass
            outlineProvider = object : android.view.ViewOutlineProvider() { override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, v.width * .22f) } }
        }, LayoutParams(size, size))
        if (labels) c.addView((if (col == Color.WHITE) text(label(p), 11f, col) else ink(label(p), 11f, col)).apply { gravity = Gravity.CENTER; maxLines = 1 }, LayoutParams(-1, -2).apply { topMargin = dp(3) })
        recentCells.add(c)
        row.addView(c, LayoutParams(cell, -2))
    }
    recentMark()
    return row
}

/** The mark on the icon the focus is on (only while the card has it): the glass pane ([Widgets.onMark]) or a veil drawn in place; no card is read or built again. */
internal fun Widgets.recentMark() {
    val pane = onMark.takeIf { dev.tvremote.tvui.Motion.glass }       // the glass look: the pane itself glides from icon to icon, as between tiles
    if (pane != null && recentFocused) recentCells.getOrNull(recentAt)?.let { pane(it) }
    recentCells.forEachIndexed { i, c ->
        c.background = if (pane == null && recentFocused && i == recentAt) GradientDrawable().apply {
            cornerRadius = dp(12).toFloat(); setColor(Widgets.MARK); setStroke(dp(2), (c.tag as? Int) ?: Color.WHITE)
        } else null
    }
}

/** OK on the card: opens the marked app. */
internal fun Widgets.recentOpen() {
    val p = recentApps.getOrNull(recentAt) ?: return
    if (!Apps.launch(a, p, null)) android.widget.Toast.makeText(a, a.getString(R.string.no_open), android.widget.Toast.LENGTH_SHORT).show()
}

/** The glass look: a title (big size) and the icons. */
internal fun Widgets.recentGlass(card: LinearLayout, f: Facts, label: (String) -> String, w: Int, full: Boolean) {
    card.gravity = Gravity.CENTER_VERTICAL
    if (full) card.addView(head(a.getString(R.string.w_recent), ""))
    if (f.recent == null) { card.addView(note(a.getString(R.string.w_need_setup))); return }
    card.addView(recentRow(f, label, w - dp(if (full) 40 else 32), full, Color.WHITE, Color.WHITE), if (full) LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) } else LayoutParams(-1, -2))
}

/** The tonal look: the label over the icons on the warm card. */
internal fun Widgets.recentTonal(card: LinearLayout, f: Facts, label: (String) -> String, w: Int, compact: Boolean) {
    card.gravity = Gravity.CENTER_VERTICAL
    card.addView(ink(a.getString(R.string.w_recent), if (compact) 12f else 14f))
    if (f.recent == null) { card.addView(ink(a.getString(R.string.w_need_setup), 12f, Tonal.SUB).apply { maxLines = 3 }, LayoutParams(-1, -2).apply { topMargin = dp(6) }); return }
    card.addView(recentRow(f, label, w - dp(if (compact) 32 else 40), !compact, Tonal.INK, Tonal.INK), LayoutParams(-1, 0, 1f).apply { topMargin = dp(6) })
}
