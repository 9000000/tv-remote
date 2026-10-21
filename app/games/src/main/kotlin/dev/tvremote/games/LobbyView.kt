package dev.tvremote.games

import android.app.Activity
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The lobby the TV shows before a game starts: the game's picture and title, the join QR, four seats (name, colour, ping, Ready, and the
 * button a player is trying) and the hint under them. Drawn at a fixed size of [DW] x [DH] dp and scaled to the screen, so a 720p TV
 * with a big density clips nothing. GameCards owns the rules; this only draws what it is told.
 */
class LobbyView(
    private val act: Activity, private val server: GameServer, title: String, art: Thumbs.Src?,
    private val max: Int, private val min: Int,
) : FrameLayout(act) {
    private class Row(val box: LinearLayout, val dot: View, val name: TextView, val lit: TextView, val ping: TextView, val ready: TextView)

    private val panel = FrameLayout(act)
    private val rows = ArrayList<Row>()
    private val held = Array(4) { LinkedHashSet<String>() }
    private val warnTv = act.label("", 18f, 0xFFFBBF24.toInt())
    private val countBox = LinearLayout(act)
    private val countN = act.label("", 150f)
    private val grey = 0xFFAAB0C0.toInt()

    init {
        setBackgroundColor(0xFF0B0D12.toInt())
        isClickable = true
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            val img = ImageView(act).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xFF1A1F2B.toInt())
                layoutParams = LinearLayout.LayoutParams(act.dp(256), act.dp(144))
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, act.dp(14).toFloat()) } }
            }
            val ph = act.label(title.trim().take(1).uppercase(), 64f, 0xFF4B556B.toInt()).apply { gravity = Gravity.CENTER }   // until (or without) a picture
            addView(FrameLayout(act).apply { addView(img); addView(ph, LayoutParams(-1, -1)) })
            if (art != null) Thumbs.load(act, art) { b, _ -> if (!act.isDestroyed) { img.setImageBitmap(b); ph.visibility = View.GONE } }
            addView(act.label(title, 24f).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; maxWidth = act.dp(270); setPadding(0, act.dp(10), 0, act.dp(2)) })
            addView(act.label(need(), 16f, 0xFF7DD3FC.toInt()).apply { setPadding(0, 0, 0, act.dp(8)) })
            addView(act.qrView(server.url(), 118))
            addView(act.label(act.getString(R.string.scan_play), 15f, grey).apply { setPadding(0, act.dp(6), 0, 0) })
            addView(act.label(server.url().substringAfter("//").substringBefore('/'), 15f, grey).apply { maxLines = 1 })
        }
        val right = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            addView(act.label(act.getString(R.string.lobby_title), 28f).apply { (layoutParams as LinearLayout.LayoutParams).apply { gravity = Gravity.START; bottomMargin = act.dp(10) } })
            for (p in 1..4) {
                val dot = act.dot(p, 20)
                val name = act.label("", 22f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER_VERTICAL or Gravity.START }
                val lit = act.label("", 18f).apply { setPadding(act.dp(10), act.dp(2), act.dp(10), act.dp(2)); minWidth = act.dp(44); visibility = View.INVISIBLE }
                val ping = act.label("", 15f).apply { setPadding(act.dp(10), 0, act.dp(10), 0) }
                val ready = act.label("", 16f).apply { setPadding(act.dp(14), act.dp(6), act.dp(14), act.dp(6)); minWidth = act.dp(150) }
                val box = LinearLayout(act).apply {
                    gravity = Gravity.CENTER_VERTICAL; setPadding(act.dp(18), 0, act.dp(14), 0)
                    addView(dot); addView(name, LinearLayout.LayoutParams(0, -2, 1f)); addView(lit); addView(ping); addView(ready)
                }
                addView(box, LinearLayout.LayoutParams(-1, act.dp(62)).apply { bottomMargin = act.dp(10) })
                rows += Row(box, dot, name, lit, ping, ready)
            }
        }
        val top = LinearLayout(act).apply {
            addView(col, LinearLayout.LayoutParams(act.dp(290), -1))
            addView(right, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = act.dp(30) })
        }
        val foot = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            addView(warnTv)
            addView(act.label(act.getString(R.string.lobby_hint), 18f, grey).apply { maxLines = 2 })
        }
        panel.addView(top, LayoutParams(act.dp(DW), act.dp(DH - 62), Gravity.TOP))
        panel.addView(foot, LayoutParams(act.dp(DW), act.dp(62), Gravity.BOTTOM))
        addView(panel, LayoutParams(act.dp(DW), act.dp(DH), Gravity.CENTER))
        countBox.apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(0xE60B0D12.toInt()); visibility = View.GONE
            addView(act.label(act.getString(R.string.lobby_count), 28f, grey)); addView(countN)
        }
        addView(countBox, LayoutParams(-1, -1))
        render()
    }

    private fun need() = when { max == 1 -> act.getString(R.string.need_one); min >= max -> act.getString(R.string.need_n, min); else -> act.getString(R.string.need_range, min, max) }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val s = minOf(w * 0.94f / act.dp(DW), h * 0.94f / act.dp(DH)).coerceIn(0.3f, 1.3f)
        panel.scaleX = s; panel.scaleY = s
    }

    private fun pill(c: Int, r: Int = 99) = GradientDrawable().apply { setColor(c); cornerRadius = act.dp(r).toFloat() }

    /** Seats from the server: who is in, their names, pings and Ready. */
    fun render() {
        val ps = server.lobbyPlayers().associateBy { it.slot }
        rows.forEachIndexed { i, r ->
            val p = ps[i + 1]
            if (p == null) {
                r.box.background = GradientDrawable().apply { setColor(0xFF10141D.toInt()); cornerRadius = act.dp(14).toFloat(); setStroke(act.dp(2), 0xFF2A3042.toInt(), act.dp(8).toFloat(), act.dp(6).toFloat()) }
                (r.dot.background as GradientDrawable).setColor(0xFF39404F.toInt())
                r.name.text = act.getString(R.string.lobby_waiting); r.name.setTextColor(0xFF6B7488.toInt())
                r.ping.text = ""; r.ready.text = ""; r.ready.background = null; r.lit.visibility = View.INVISIBLE
                return@forEachIndexed
            }
            val c = PLAYER_COLORS[i]
            r.box.background = GradientDrawable().apply { setColor(0xFF151A25.toInt()); cornerRadius = act.dp(14).toFloat(); setStroke(act.dp(2), if (p.ready) 0xFF22C55E.toInt() else (c and 0x00FFFFFF) or 0x66000000) }
            (r.dot.background as GradientDrawable).setColor(c)
            r.name.text = server.nameOf(p.slot) ?: act.getString(R.string.player_plain, p.slot); r.name.setTextColor(Color.WHITE)
            val ms = server.pingOf(p.slot)
            r.ping.text = if (ms < 0) "" else "$ms ms"
            r.ping.setTextColor(if (ms < 30) 0xFF34D399.toInt() else if (ms < 80) 0xFFFBBF24.toInt() else 0xFFF87171.toInt())
            r.ready.text = act.getString(if (p.ready) R.string.lobby_ready else R.string.lobby_notready)
            r.ready.setTextColor(if (p.ready) 0xFF052E16.toInt() else grey)
            r.ready.background = pill(if (p.ready) 0xFF4ADE80.toInt() else 0xFF222838.toInt())
            r.lit.background = pill(c, 10); lit(i)
        }
    }

    private fun glyph(b: String) = mapOf("UP" to "▲", "DOWN" to "▼", "LEFT" to "◀", "RIGHT" to "▶")[b] ?: b

    private fun lit(i: Int) {
        val r = rows[i]; val h = held[i]
        r.lit.text = h.toList().takeLast(3).joinToString(" ") { glyph(it) }
        r.lit.visibility = if (h.isEmpty()) View.INVISIBLE else View.VISIBLE
    }

    /** A player presses (or lets go of) a button to try it: the seat lights it, the game gets nothing. */
    fun press(player: Int, button: String, down: Boolean) {
        if (player !in 1..4) return
        if (down) held[player - 1].add(button) else held[player - 1].remove(button)
        render()
    }

    fun setPing() = render()

    /** The 3-2-1 over the seats; 0 hides it. */
    fun count(n: Int) { countBox.visibility = if (n > 0) View.VISIBLE else View.GONE; countN.text = if (n > 0) "$n" else "" }

    /** The one-line warning ("1 of 2 players") or "" to clear it. */
    fun warn(text: String) { warnTv.text = text }

    companion object { const val DW = 880; const val DH = 470 }
}
