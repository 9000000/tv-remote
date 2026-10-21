package dev.tvremote.games

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The lobby the TV shows before a game starts, in the game's own colours: its picture blurred behind everything, an accent taken from
 * it, the picture framed large on the left, the join QR on a white card, a seat per player (avatar, name, state, ping, the button being
 * tried) and a hint bar of remote keys. Drawn on a fixed canvas of [DW] x [DH] dp scaled to 90 % of the screen (5 % overscan on each
 * side), so a 720p TV with a big density clips nothing. GameCards owns the rules; this only draws what it is told. The blur and the
 * accent are computed once, on a 64 x 36 copy; the only thing that moves is a slow fade on empty seats (not at all when the system's
 * animations are off).
 */
class LobbyView(
    private val act: Activity, private val server: GameServer, title: String, art: Thumbs.Src?,
    private val max: Int, private val min: Int, private val kind: String? = null,
) : FrameLayout(act) {
    private class Row(val box: LinearLayout, val avatar: TextView, val name: TextView, val lit: TextView, val ping: TextView, val state: TextView)

    private val panel = FrameLayout(act)
    private val rows = ArrayList<Row>()
    private val held = Array(4) { LinkedHashSet<String>() }
    private val watchTv = act.label("", 20f, 0xFF9AA3B8.toInt())
    private val warnTv = act.label("", 20f, 0xFFFBBF24.toInt())
    private val countBox = FrameLayout(act)
    private val countN = act.label("", 150f).apply { setTypeface(null, Typeface.BOLD) }
    private val countRing = FrameLayout(act)
    private val countCaption = act.label(act.getString(R.string.lobby_count), 32f, 0xFFD5DAE6.toInt())
    private val backdrop = ImageView(act).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val heroFrame = FrameLayout(act)
    private val qrCard = LinearLayout(act)
    private val seatsTv = act.label("", 24f, 0xFF9AA3B8.toInt())
    private val grey = 0xFFAAB0C0.toInt()
    private var accent = LobbyTheme.DEFAULT
    private val pulse = ValueAnimator.ofFloat(0.45f, 1f).apply { duration = 1400; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE }
    private val animOn = try { Settings.Global.getFloat(act.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f } catch (e: Exception) { true }
    private val shapeR = act.dp(14).toFloat()

    private fun bold(t: TextView) = t.apply { setTypeface(null, Typeface.BOLD) }
    private fun round(c: Int, r: Int, stroke: Int = 0, sc: Int = 0) = GradientDrawable().apply {
        setColor(c); cornerRadius = act.dp(r).toFloat(); if (stroke > 0) setStroke(act.dp(stroke), sc)
    }

    init {
        setBackgroundColor(0xFF0B0D12.toInt())
        isClickable = true
        addView(backdrop, LayoutParams(-1, -1))
        addView(View(act).apply {                                      // darkens the picture and keeps the edges quiet
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x8C080A10.toInt(), 0xB8080A10.toInt(), 0xE6080A10.toInt()))
        }, LayoutParams(-1, -1))

        // ---- left: the picture, title, system and players
        val img = ImageView(act).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xFF1A1F2B.toInt()) }
        val ph = act.label(title.trim().take(1).uppercase(), 80f, 0xFF4B556B.toInt())   // until (or without) a picture
        val inner = FrameLayout(act).apply {
            addView(img, LayoutParams(-1, -1)); addView(ph, LayoutParams(-1, -1))
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, act.dp(15).toFloat()) } }
        }
        heroFrame.addView(inner, LayoutParams(act.dp(HERO_W), act.dp(HERO_H)))
        heroFrame.setPadding(act.dp(3), act.dp(3), act.dp(3), act.dp(3))
        if (art != null) Thumbs.load(act, art) { b, _ ->
            if (act.isDestroyed) return@load
            img.setImageBitmap(b); ph.visibility = View.GONE
            accent = LobbyTheme.accent(b); backdrop.setImageBitmap(LobbyTheme.backdrop(b)); applyAccent()
        }
        val metaLine = listOfNotNull(kind, need()).joinToString("  ·  ")
        val left = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            addView(heroFrame, LinearLayout.LayoutParams(-2, -2))
            addView(bold(act.label(title, 34f)).apply {
                maxLines = 2; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.START
                (layoutParams as LinearLayout.LayoutParams).apply { topMargin = act.dp(16) }
            })
            addView(act.label(metaLine, 22f, 0xFFD5DAE6.toInt()).apply { gravity = Gravity.START; (layoutParams as LinearLayout.LayoutParams).topMargin = act.dp(4) })
        }

        // ---- right: QR card, the seats
        val addr = server.url().substringAfter("//").substringBefore('/')
        qrCard.apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(act.dp(12), act.dp(12), act.dp(22), act.dp(12))
            addView(act.qrView(server.url(), 100).apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = act.dp(18) })
            addView(LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                addView(bold(act.label(act.getString(R.string.scan_play), 26f, 0xFF0B0D12.toInt())).apply { gravity = Gravity.START })
                addView(act.label(addr, 20f, 0xFF3B4254.toInt()).apply { gravity = Gravity.START; maxLines = 1; (layoutParams as LinearLayout.LayoutParams).topMargin = act.dp(4) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val seats = max.coerceIn(1, 4)
        val rowH = when (seats) { 1, 2 -> 84; 3 -> 64; else -> 48 }
        val list = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        for (p in 1..seats) {
            val avatar = bold(act.label(p.toString(), 24f, Color.WHITE)).apply { minWidth = act.dp(44); minHeight = act.dp(44); background = GradientDrawable().apply { shape = GradientDrawable.OVAL } }
            val name = act.label("", 24f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER_VERTICAL or Gravity.START }
            val lit = act.label("", 20f).apply { setPadding(act.dp(10), act.dp(2), act.dp(10), act.dp(2)); minWidth = act.dp(44); visibility = View.INVISIBLE }
            val ping = act.label("", 18f).apply { setPadding(act.dp(10), 0, act.dp(10), 0) }
            val state = bold(act.label("", 20f)).apply { setPadding(act.dp(16), act.dp(6), act.dp(16), act.dp(6)); minWidth = act.dp(140) }
            val box = LinearLayout(act).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(act.dp(16), 0, act.dp(14), 0)
                addView(avatar, LinearLayout.LayoutParams(act.dp(44), act.dp(44)).apply { marginEnd = act.dp(16) })
                addView(name, LinearLayout.LayoutParams(0, -2, 1f)); addView(lit); addView(ping); addView(state)
            }
            list.addView(box, LinearLayout.LayoutParams(-1, act.dp(rowH)).apply { bottomMargin = act.dp(6) })
            rows += Row(box, avatar, name, lit, ping, state)
        }
        val head = LinearLayout(act).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(bold(act.label(act.getString(R.string.lobby_title), 32f)).apply { gravity = Gravity.START }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(seatsTv)
        }
        val right = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            addView(qrCard, LinearLayout.LayoutParams(-1, -2))
            addView(head, LinearLayout.LayoutParams(-1, -2).apply { topMargin = act.dp(12); bottomMargin = act.dp(6) })
            addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(watchTv, LinearLayout.LayoutParams(-2, -2))
        }
        val top = LinearLayout(act).apply {
            addView(left, LinearLayout.LayoutParams(act.dp(HERO_W + 6), -1))
            addView(right, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = act.dp(40) })
        }

        // ---- foot: the remote keys
        val foot = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(warnTv)
            addView(LinearLayout(act).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(act.dp(24), act.dp(8), act.dp(24), act.dp(8))
                background = round(0xB3080A10.toInt(), 30, 1, 0x33FFFFFF)
                addView(keyCap(act.getString(R.string.key_ok))); addView(hintText(R.string.hint_start))
                addView(keyCap(act.getString(R.string.key_back))); addView(hintText(R.string.hint_exit))
                addView(act.label("|", 24f, 0x55FFFFFF).apply { setPadding(act.dp(14), 0, act.dp(14), 0) })
                addView(hintText(R.string.hint_ready))
            })
        }
        panel.addView(top, LayoutParams(act.dp(DW), act.dp(DH - FOOT), Gravity.TOP))
        panel.addView(foot, LayoutParams(act.dp(DW), act.dp(FOOT), Gravity.BOTTOM))
        addView(panel, LayoutParams(act.dp(DW), act.dp(DH), Gravity.CENTER))

        // ---- the 3-2-1
        countRing.addView(countN, LayoutParams(-2, -2, Gravity.CENTER))
        countBox.apply {
            setBackgroundColor(0xE60B0D12.toInt()); visibility = View.GONE
            addView(LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                addView(countCaption)
                addView(countRing, LinearLayout.LayoutParams(act.dp(240), act.dp(240)).apply { topMargin = act.dp(14) })
            }, LayoutParams(-2, -2, Gravity.CENTER))
        }
        addView(countBox, LayoutParams(-1, -1))
        applyAccent()
        render()
    }

    private fun keyCap(t: String) = bold(act.label(t, 22f)).apply {
        setPadding(act.dp(14), act.dp(4), act.dp(14), act.dp(4)); background = round(0x40FFFFFF, 8, 2, 0x80FFFFFF.toInt())
        (layoutParams as LinearLayout.LayoutParams).apply { marginEnd = act.dp(10) }
    }
    private fun hintText(id: Int) = act.label(act.getString(id), 22f, 0xFFE5E9F2.toInt()).apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = act.dp(22); maxLines = 1 }

    private fun need() = when { max == 1 -> act.getString(R.string.need_one); min >= max -> act.getString(R.string.need_n, min); else -> act.getString(R.string.need_range, min, max) }

    /** The accent changed (the picture arrived): everything that wears it. */
    private fun applyAccent() {
        heroFrame.background = round((accent and 0x00FFFFFF) or 0x59000000, 18, 1, (accent and 0x00FFFFFF) or 0x80000000.toInt())   // a soft halo, not an outline
        qrCard.background = round(Color.WHITE, 20, 2, (accent and 0x00FFFFFF) or 0x66000000)
        countN.setTextColor(accent); countCaption.setTextColor(0xFFD5DAE6.toInt())
        countRing.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x14FFFFFF); setStroke(act.dp(8), accent) }
        if (rows.isNotEmpty()) render()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val s = minOf(w * 0.9f / act.dp(DW), h * 0.9f / act.dp(DH)).coerceIn(0.3f, 1.6f)
        panel.scaleX = s; panel.scaleY = s
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animOn) { pulse.addUpdateListener { v -> val a = v.animatedValue as Float; rows.forEach { if (it.box.tag == EMPTY) { it.avatar.alpha = a; it.name.alpha = a; it.state.alpha = a } } }; pulse.start() }
    }

    override fun onDetachedFromWindow() { pulse.cancel(); super.onDetachedFromWindow() }

    private fun pill(c: Int, r: Int = 99) = GradientDrawable().apply { setColor(c); cornerRadius = act.dp(r).toFloat() }

    /** Seats from the server: who is in, their names, pings and Ready. */
    fun render() {
        val ps = server.lobbyPlayers().associateBy { it.slot }
        val w = server.watching(); watchTv.text = if (w > 0) act.getString(R.string.lobby_watch, w) else ""
        seatsTv.text = act.getString(R.string.lobby_seats, ps.size.coerceAtMost(rows.size), rows.size)
        rows.forEachIndexed { i, r ->
            val p = ps[i + 1]; val c = PLAYER_COLORS[i]
            r.avatar.text = (server.nameOf(i + 1)?.trim()?.take(1)?.uppercase()?.ifEmpty { null } ?: (i + 1).toString())
            if (p == null) {
                r.box.tag = EMPTY
                r.box.background = GradientDrawable().apply { setColor(0x4D10141D); cornerRadius = shapeR; setStroke(act.dp(1), 0x33FFFFFF) }
                (r.avatar.background as GradientDrawable).setColor(c and 0x00FFFFFF or 0x55000000); r.avatar.setTextColor(0x99FFFFFF.toInt()); r.avatar.text = (i + 1).toString()
                r.name.text = act.getString(R.string.player_plain, i + 1); r.name.setTextColor(0xFF8892A6.toInt())
                r.state.text = act.getString(R.string.lobby_waiting); r.state.setTextColor(0xFF8892A6.toInt()); r.state.background = null
                r.ping.text = ""; r.lit.visibility = View.INVISIBLE
                if (!animOn) { r.avatar.alpha = 0.7f; r.name.alpha = 0.7f; r.state.alpha = 0.7f }
                return@forEachIndexed
            }
            r.box.tag = null; r.avatar.alpha = 1f; r.name.alpha = 1f; r.state.alpha = 1f
            r.box.background = GradientDrawable().apply { setColor(if (p.ready) (accent and 0x00FFFFFF) or 0x40000000 else 0xCC151A25.toInt()); cornerRadius = shapeR; setStroke(act.dp(2), if (p.ready) accent else (c and 0x00FFFFFF) or 0x66000000) }
            (r.avatar.background as GradientDrawable).setColor(c); r.avatar.setTextColor(Color.WHITE)
            r.name.text = server.nameOf(p.slot) ?: act.getString(R.string.player_plain, p.slot); r.name.setTextColor(Color.WHITE)
            val ms = server.pingOf(p.slot)
            r.ping.text = if (ms < 0) "" else "$ms ms"
            r.ping.setTextColor(if (ms < 30) 0xFF34D399.toInt() else if (ms < 80) 0xFFFBBF24.toInt() else 0xFFF87171.toInt())
            r.state.text = act.getString(if (p.ready) R.string.lobby_ready else R.string.lobby_joined)
            r.state.setTextColor(if (p.ready) LobbyTheme.onAccent(accent) else 0xFFD5DAE6.toInt())
            r.state.background = pill(if (p.ready) accent else 0x40FFFFFF)
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

    /** The 3-2-1 over the room; 0 hides it. */
    fun count(n: Int) {
        countBox.visibility = if (n > 0) View.VISIBLE else View.GONE; countN.text = if (n > 0) "$n" else ""
        if (n > 0 && animOn) { countN.scaleX = 1.3f; countN.scaleY = 1.3f; countN.animate().scaleX(1f).scaleY(1f).setDuration(380).start() }
    }

    /** The one-line warning ("1 of 2 players") or "" to clear it. */
    fun warn(text: String) { warnTv.text = text }

    companion object { const val DW = 900; const val DH = 520; const val FOOT = 84; const val HERO_W = 394; const val HERO_H = 222; private const val EMPTY = "empty" }
}
