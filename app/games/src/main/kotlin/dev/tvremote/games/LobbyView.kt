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
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvremote.tvui.dp
import dev.tvremote.tvui.label

/**
 * The lobby the TV shows before a game starts, in the game's own colours (its picture blurred behind, an accent taken from it).
 * One glance says what to do from the sofa: on the left the join QR is the hero (a white card, 320 dp square = ~615 px at 1080p,
 * drawn without smoothing so every module stays sharp), "Scan to play" and the address as text; on the right the game (small picture,
 * title, players needed), the seats as compact chips that fill as phones join, ONE primary action (OK: Start, lit only when enough
 * players are in, else "Needs N more"), and the Back hint small at the bottom. Drawn on a fixed canvas of [DW] x [DH] dp scaled to 90 %
 * of the screen (5 % overscan each side), so a 720p TV with a big density clips nothing (1 canvas dp = 1.92 px at 1080p).
 * Cheap to draw: nothing loops (the old pulse on empty seats re-drew the whole scaled panel every frame; a join gets one short pop),
 * the blur and accent come from a 64 x 36 copy once, the QR bitmap is cached ([qrView]). GameCards owns the rules; this draws.
 */
class LobbyView(
    private val act: Activity, private val server: GameServer, title: String, art: Thumbs.Src?,
    private val max: Int, private val min: Int, private val kind: String? = null,
) : FrameLayout(act) {
    private class Chip(val box: LinearLayout, val avatar: TextView, val name: TextView, val sub: TextView)

    private val panel = FrameLayout(act)
    private val chips = ArrayList<Chip>()
    private val joined = HashSet<Int>()
    /** The seat picked with the D-pad to send its player out (OK), 0 = none: OK starts the game. */
    var picked = 0; private set
    private val held = Array(4) { LinkedHashSet<String>() }
    private val watchTv = act.label("", 16f, 0xFF9AA3B8.toInt())
    private val warnTv = act.label("", 18f, 0xFFFBBF24.toInt())
    private val loadTv = act.label("", 16f).apply { setTextColor(watchTv.currentTextColor) }
    private val loadRow = LinearLayout(act).apply {                  // a spinner and the page's progress while it loads behind the lobby
        gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE
        addView(android.widget.ProgressBar(act).apply { isIndeterminate = true }, LinearLayout.LayoutParams(act.dp(18), act.dp(18)).apply { marginEnd = act.dp(8) })
        addView(loadTv)
    }
    private val countBox = FrameLayout(act)
    private val countN = act.label("", 150f).apply { setTypeface(null, Typeface.BOLD) }
    private val countRing = FrameLayout(act)
    private val countCaption = act.label(act.getString(R.string.lobby_count), 32f, 0xFFD5DAE6.toInt())
    private val backdrop = ImageView(act).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val heroFrame = FrameLayout(act)
    private val qrCard = FrameLayout(act)
    private val action = LinearLayout(act)                           // the one primary action: OK + what it does, or what is missing
    private val actionKey = act.label(act.getString(R.string.key_ok), 22f)
    private val actionTv = act.label("", 26f)
    private val subHint = act.label("", 16f, 0xFF9AA3B8.toInt())
    private var accent = LobbyTheme.DEFAULT
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

        // ---- left: the QR is the hero
        qrCard.apply {
            setPadding(act.dp(14), act.dp(14), act.dp(14), act.dp(14))
            addView(act.qrView(server.url(), QR - 28, 16), LayoutParams(act.dp(QR - 28), act.dp(QR - 28)))
        }
        val addr = server.url().substringAfter("//").substringBefore('/')
        val left = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(qrCard, LinearLayout.LayoutParams(act.dp(QR), act.dp(QR)))
            addView(bold(act.label(act.getString(R.string.scan_play), 30f)), LinearLayout.LayoutParams(-2, -2).apply { topMargin = act.dp(14) })
            addView(act.label(addr, 22f, 0xFFD5DAE6.toInt()).apply { maxLines = 1 }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = act.dp(2) })
        }

        // ---- right: the game, the seats, the action
        val img = ImageView(act).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xFF1A1F2B.toInt()) }
        val ph = act.label(title.trim().take(1).uppercase(), 36f, 0xFF4B556B.toInt())   // until (or without) a picture
        val inner = FrameLayout(act).apply {
            addView(img, LayoutParams(-1, -1)); addView(ph, LayoutParams(-1, -1))
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, act.dp(10).toFloat()) } }
        }
        heroFrame.addView(inner, LayoutParams(act.dp(HERO_W), act.dp(HERO_H)))
        heroFrame.setPadding(act.dp(2), act.dp(2), act.dp(2), act.dp(2))
        if (art != null) Thumbs.load(act, art) { b, _ ->
            if (act.isDestroyed) return@load
            img.setImageBitmap(b); ph.visibility = View.GONE
            accent = LobbyTheme.accent(b); backdrop.setImageBitmap(LobbyTheme.backdrop(b)); applyAccent()
        }
        val metaLine = listOfNotNull(kind, need()).joinToString("  \u00B7  ")
        val titles = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            addView(bold(act.label(title, 30f)).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.START })
            addView(act.label(metaLine, 20f, 0xFFD5DAE6.toInt()).apply { gravity = Gravity.START; maxLines = 1 })
            addView(loadRow, LinearLayout.LayoutParams(-2, -2).apply { topMargin = act.dp(4) })
        }
        val header = LinearLayout(act).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(heroFrame, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = act.dp(16) })
            addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val seats = max.coerceIn(1, 4)
        val grid = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        var rowBox: LinearLayout? = null
        for (p in 1..seats) {
            if (rowBox == null || (p - 1) % 2 == 0) { rowBox = LinearLayout(act); grid.addView(rowBox, LinearLayout.LayoutParams(-1, act.dp(CHIP_H)).apply { topMargin = if (p > 2) act.dp(8) else 0 }) }
            val avatar = bold(act.label(p.toString(), 20f, Color.WHITE)).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL } }
            val name = bold(act.label("", 20f)).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.START }
            val sub = act.label("", 15f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.START }
            val box = LinearLayout(act).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(act.dp(10), 0, act.dp(10), 0)
                addView(avatar, LinearLayout.LayoutParams(act.dp(AVATAR), act.dp(AVATAR)).apply { marginEnd = act.dp(10) })
                addView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; addView(name); addView(sub) }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            rowBox.addView(box, LinearLayout.LayoutParams(0, -1, 1f).apply { if ((p - 1) % 2 == 1) marginStart = act.dp(8) })
            chips += Chip(box, avatar, name, sub)
        }
        if (seats % 2 == 1) rowBox?.addView(View(act), LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = act.dp(8) })   // an odd last chip keeps the width of the others
        action.apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(act.dp(14), 0, act.dp(22), 0)
            addView(bold(actionKey).apply { setPadding(act.dp(12), act.dp(2), act.dp(12), act.dp(2)); background = round(0x30000000, 8, 2, 0x66000000) }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = act.dp(14) })
            addView(bold(actionTv))
        }
        val right = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            addView(header, LinearLayout.LayoutParams(-1, -2))
            addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = act.dp(24) })
            addView(action, LinearLayout.LayoutParams(-2, act.dp(56)).apply { topMargin = act.dp(24) })
            addView(subHint, LinearLayout.LayoutParams(-2, -2).apply { topMargin = act.dp(8) })
            addView(warnTv, LinearLayout.LayoutParams(-2, -2).apply { topMargin = act.dp(4) })
        }
        val top = LinearLayout(act).apply {
            addView(left, LinearLayout.LayoutParams(act.dp(QR), -1))
            addView(right, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = act.dp(36) })
        }

        // ---- foot: Back, small, and who is watching
        val foot = LinearLayout(act).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            addView(watchTv, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = act.dp(16) })
            addView(bold(act.label(act.getString(R.string.key_back), 15f, 0xFFD5DAE6.toInt())).apply { setPadding(act.dp(8), act.dp(1), act.dp(8), act.dp(1)); background = round(0x30FFFFFF, 6, 1, 0x66FFFFFF) }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = act.dp(8) })
            addView(act.label(act.getString(R.string.hint_exit), 15f, 0xFFAAB0C0.toInt()))
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

    private fun need() = when { max == 1 -> act.getString(R.string.need_one); min >= max -> act.getString(R.string.need_n, min); else -> act.getString(R.string.need_range, min, max) }

    /** The accent changed (the picture arrived): everything that wears it. */
    private fun applyAccent() {
        heroFrame.background = round((accent and 0x00FFFFFF) or 0x59000000, 14, 1, (accent and 0x00FFFFFF) or 0x80000000.toInt())   // a soft halo, not an outline
        qrCard.background = round(Color.WHITE, 22, 3, (accent and 0x00FFFFFF) or 0x99000000.toInt())
        countN.setTextColor(accent); countCaption.setTextColor(0xFFD5DAE6.toInt())
        countRing.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x14FFFFFF); setStroke(act.dp(8), accent) }
        if (chips.isNotEmpty()) render()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val s = minOf(w * 0.9f / act.dp(DW), h * 0.9f / act.dp(DH)).coerceIn(0.3f, 1.6f)
        panel.scaleX = s; panel.scaleY = s
    }

    private fun capsule(c: Int, r: Int = 99) = GradientDrawable().apply { setColor(c); cornerRadius = act.dp(r).toFloat() }

    /** Seats from the server: who is in, their names, pings and Ready; and the one action. */
    fun render() {
        val ps = server.lobbyPlayers().associateBy { it.slot }
        if (picked !in ps) picked = 0
        val w = server.watching(); watchTv.text = if (w > 0) act.getString(R.string.lobby_watch, w) else ""
        val ok = !LobbyRule.tooFew(ps.values.toList(), min)
        if (ok) {
            action.background = capsule(accent); actionKey.setTextColor(LobbyTheme.onAccent(accent)); actionTv.setTextColor(LobbyTheme.onAccent(accent))
            actionTv.text = act.getString(if (ps.isEmpty()) R.string.hint_remote else R.string.hint_start)   // nobody in: OK plays with the remote, no QR needed
        } else {
            val missing = min - maxOf(ps.size, 1)
            action.background = round(0x26FFFFFF, 99, 2, 0x55FFFFFF); actionKey.setTextColor(0xFF8892A6.toInt()); actionTv.setTextColor(0xFFFBBF24.toInt())
            actionTv.text = act.resources.getQuantityString(R.plurals.need_more, missing, missing)
        }
        subHint.text = if (ps.isEmpty()) act.getString(R.string.hint_ready) else act.getString(R.string.lobby_kick_hint)
        if (picked > 0) {                                              // OK now sends the picked player out, not the game off
            action.background = capsule(KICK); actionKey.setTextColor(Color.WHITE); actionTv.setTextColor(Color.WHITE)
            actionTv.text = act.getString(R.string.lobby_kick, server.nameOf(picked) ?: act.getString(R.string.player_plain, picked))
            subHint.text = act.getString(R.string.lobby_kick_back)
        }
        chips.forEachIndexed { i, r ->
            val p = ps[i + 1]; val c = PLAYER_COLORS[i]
            if (p == null) {
                joined.remove(i + 1)
                r.box.background = GradientDrawable().apply { setColor(CHIP_EMPTY); cornerRadius = shapeR; setStroke(act.dp(1), 0x33FFFFFF) }
                (r.avatar.background as GradientDrawable).setColor(c and 0x00FFFFFF or 0x55000000); r.avatar.setTextColor(0x99FFFFFF.toInt()); r.avatar.text = (i + 1).toString()
                r.name.text = act.getString(R.string.player_plain, i + 1); r.name.setTextColor(0xFF8892A6.toInt())
                r.sub.text = act.getString(R.string.lobby_waiting); r.sub.setTextColor(0xFF6B7385.toInt())
                return@forEachIndexed
            }
            r.avatar.text = (server.nameOf(i + 1)?.trim()?.take(1)?.uppercase()?.ifEmpty { null } ?: (i + 1).toString())
            r.box.background = GradientDrawable().apply { setColor(if (p.ready) (accent and 0x00FFFFFF) or 0x40000000 else 0xCC151A25.toInt()); cornerRadius = shapeR; if (p.slot == picked) setStroke(act.dp(4), Color.WHITE) else setStroke(act.dp(2), if (p.ready) accent else (c and 0x00FFFFFF) or 0x99000000.toInt()) }
            (r.avatar.background as GradientDrawable).setColor(c); r.avatar.setTextColor(Color.WHITE)
            r.name.text = server.nameOf(p.slot) ?: act.getString(R.string.player_plain, p.slot); r.name.setTextColor(Color.WHITE)
            val ms = server.pingOf(p.slot)
            val h = held[i]
            val sb = android.text.SpannableStringBuilder()
            if (h.isNotEmpty()) sb.append(h.toList().takeLast(3).joinToString(" ") { glyph(it) })      // the button being tried
            else {
                sb.append(act.getString(if (p.ready) R.string.lobby_ready else R.string.lobby_joined))
                if (ms >= 0) { val a = sb.length; sb.append("  $ms ms"); sb.setSpan(android.text.style.ForegroundColorSpan(if (ms < 30) 0xFF34D399.toInt() else if (ms < 80) 0xFFFBBF24.toInt() else 0xFFF87171.toInt()), a, sb.length, 0) }
            }
            r.sub.text = sb; r.sub.setTextColor(if (p.ready) accent else 0xFFD5DAE6.toInt())
            if (joined.add(p.slot) && animOn) r.avatar.animate().cancel().also { r.avatar.scaleX = 1.35f; r.avatar.scaleY = 1.35f; r.avatar.animate().scaleX(1f).scaleY(1f).setDuration(260).start() }   // one pop when a phone joins
        }
    }

    /** A D-pad key moves the pick among the seated players (2 per row); down from the bottom, or Back, drops it. False = not a move. */
    fun move(keyCode: Int): Boolean {
        val seated = server.lobbyPlayers().map { it.slot }.sorted()
        val to = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (picked == 0) seated.lastOrNull { it > 2 } ?: seated.firstOrNull() ?: 0 else if (picked - 2 in seated) picked - 2 else picked
            KeyEvent.KEYCODE_DPAD_DOWN -> if (picked == 0) 0 else if (picked + 2 in seated) picked + 2 else 0
            KeyEvent.KEYCODE_DPAD_LEFT -> if (picked == 0) 0 else seated.lastOrNull { it < picked } ?: picked
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (picked == 0) 0 else seated.firstOrNull { it > picked } ?: picked
            else -> return false
        }
        if (to != picked) { picked = to; render() }
        return true
    }
    fun unpick() { if (picked != 0) { picked = 0; render() } }

    private fun glyph(b: String) = mapOf("UP" to "\u25B2", "DOWN" to "\u25BC", "LEFT" to "\u25C0", "RIGHT" to "\u25B6")[b] ?: b

    /** A player presses (or lets go of) a button to try it: the seat shows it, the game gets nothing. */
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
    /** How the game's page is coming ("" once it is in): the lobby is up while it loads behind. */
    fun loading(text: String) {                                       // once shown its room is kept, so the title does not jump when it goes
        loadTv.text = text
        loadRow.visibility = if (text.isNotEmpty()) View.VISIBLE else if (loadRow.visibility == View.GONE) View.GONE else View.INVISIBLE
    }

    /** Sizes in canvas dp (x 1.92 = px at 1080p): QR card 320 square (~615 px), 2 x 2 chips 56 high, one 56 high action, Back 15 sp. */
    companion object { const val DW = 900; const val DH = 520; const val FOOT = 36; const val QR = 320; const val HERO_W = 128; const val HERO_H = 72; const val CHIP_H = 56; const val AVATAR = 36
        val CHIP_EMPTY = 0x3310141D     // the seat nobody took: a dark veil, not a player colour
        val KICK = 0xFFDC2626.toInt() }  // OK sends a player out: red, not the game's accent
}
