package dev.tvremote.channels

import dev.tvremote.tvui.pill
import android.app.Activity
import android.graphics.Color
import android.os.Parcelable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.tvremote.tvui.TvUi

/**
 * The home screen of a country that has ready-made categories ([Categories]): a thin top bar (search, a menu with everything
 * advanced) over a vertical list of shelves, each a title above a row of big channel cards. Built for a weak box: the shelves are a
 * RecyclerView of rows, each row a horizontal RecyclerView, all sharing ONE [RecyclerView.RecycledViewPool] of cards; the cards have
 * fixed sizes (nothing is measured again when the focus moves) and the focus is only a ring (no scale, no animation); logos load off
 * the main thread ([Logos]). The D-pad is done here, not by the focus search, so ▲▼ keep the column and nothing is guessed from geometry.
 */
class Shelves(private val a: Activity, private val host: Host) {
    interface Host {
        fun play(ch: List<Channel>, lineup: List<List<Channel>>, title: String)
        fun actions(ch: List<Channel>)
        fun search()
        fun menu()
    }

    /** One shelf. [id] is stable (a link to a group finds its shelf by it). */
    class Row(val id: String, val title: String, val items: List<List<Channel>>)

    private val d = { v: Number -> TvUi.dp(a, v).toInt() }
    private val tileW = d(176); private val tileH = tileW * 9 / 16
    private val nameH = d(48)                                          // two lines of 16 sp: a long name is whole, and every card is as tall as the next
    private val cardH = d(6) + tileH + nameH + d(4)
    private val mx = a.resources.getDimension(R.dimen.tv_margin_x).toInt(); private val my = a.resources.getDimension(R.dimen.tv_margin_y).toInt()
    private val lightTile by lazy { TvUi.round(LogoFit.LIGHT_TILE, TvUi.dp(a, 12)) }
    private val darkTile by lazy { TvUi.round(LogoFit.DARK_TILE, TvUi.dp(a, 12)) }
    private val pool = RecyclerView.RecycledViewPool().apply { setMaxRecycledViews(0, 24) }
    private val saved = HashMap<String, Parcelable?>()                 // a shelf's scroll, kept while it is recycled
    private var rows: List<Row> = emptyList()
    private var col = 0                                                // the column ▲▼ keep
    private var jumping = false                                        // a ▲▼ move is landing: it must not reset [col] when the row is shorter
    private val topPad = d(6)

    /** Not scrolled by the focus itself while [own] (a ▲▼ move scrolls the shelves its own way, one smooth step). */
    private class Rv(c: android.content.Context) : RecyclerView(c) {
        var own = false
        override fun requestChildRectangleOnScreen(child: View, rect: android.graphics.Rect, immediate: Boolean) =
            if (own) false else super.requestChildRectangleOnScreen(child, rect, immediate)
    }

    private val outer = Rv(a).apply {
        layoutManager = LinearLayoutManager(a); itemAnimator = null; setHasFixedSize(true); setItemViewCacheSize(2)
        clipToPadding = false; setPadding(0, topPad, 0, d(40)); isVerticalScrollBarEnabled = false
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }
    private val outerLm get() = outer.layoutManager as LinearLayoutManager

    private val searchBtn = a.pill(a.getString(R.string.search), TvUi.SP_BUTTON, lead = R.drawable.ic_search, animate = { false }) { host.search() }
    private val menuBtn = a.pill("⋯", TvUi.SP_BUTTON, animate = { false }) { host.menu() }.apply { contentDescription = a.getString(R.string.more) }

    val view: View = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        val bar = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(mx, my, mx, d(8)) }
        val ic = a.resources.getDimension(R.dimen.tv_head_icon).toInt(); val rad = a.resources.getDimension(R.dimen.tv_head_radius)
        bar.addView(ImageView(a).apply {
            setImageResource(R.drawable.icon)
            outlineProvider = object : android.view.ViewOutlineProvider() { override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, rad) } }
            clipToOutline = true
        }, LinearLayout.LayoutParams(ic, ic).apply { marginEnd = a.resources.getDimension(R.dimen.tv_head_gap).toInt() })
        bar.addView(TvUi.text(a, a.getString(R.string.app_name), 0f, TvUi.TEXT, true).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, a.resources.getDimension(R.dimen.tv_head_name)); maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(searchBtn, LinearLayout.LayoutParams(-2, d(40)).apply { marginEnd = d(12) })
        bar.addView(menuBtn, LinearLayout.LayoutParams(-2, d(40)))
        addView(bar, LinearLayout.LayoutParams(-1, -2))
        addView(outer, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    init { outer.adapter = Outer() }

    /**
     * New shelves (a favourite or group changed). The focus stays on its card (stable ids); when its card or shelf is gone (the last
     * favourite was removed) it goes to the same place in the shelf now there, not to the top bar.
     */
    fun set(list: List<Row>) {
        val f = outer.findFocus()
        val at = f?.let { (it.parent as? RecyclerView)?.let { inner -> outer.getChildAdapterPosition(inner.parent as View) } } ?: -1
        val id = rows.getOrNull(at)?.id
        rows = list; outer.adapter!!.notifyDataSetChanged()
        if (at < 0) return
        outer.post {
            if (outer.hasFocus() && channelAt(view.findFocus()) != null) return@post
            val r = list.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: at.coerceAtMost(list.size - 1)
            if (r >= 0) { outerLm.scrollToPositionWithOffset(r, topPad); outer.post { focusIn(r, col) } } else searchBtn.requestFocus()
        }
    }

    /** The first card of the first shelf (or the search button when there is none). */
    fun focusFirst() { col = 0; if (rows.isEmpty()) searchBtn.requestFocus() else if (!focusRow(rows[0].id)) outer.post { focusRow(rows[0].id) } }

    /** The first card of shelf [id], scrolled to; false when there is no such shelf. */
    fun focusRow(id: String): Boolean {
        val r = rows.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return false
        col = 0
        outerLm.scrollToPositionWithOffset(r, topPad)
        outer.post { focusIn(r, 0) }
        return true
    }

    /** Back: to the very first card, scrolled to the top; false when it is already there. */
    fun toTop(): Boolean {
        val f = view.findFocus()
        if (f === searchBtn || f === menuBtn) return false
        val inner = f?.parent as? RecyclerView
        if (f != null && inner != null && outer.getChildAdapterPosition(inner.parent as View) == 0 && inner.getChildAdapterPosition(f) == 0) return false
        col = 0; outerLm.scrollToPositionWithOffset(0, topPad); outer.post { focusIn(0, 0) }
        return true
    }

    /** The cards drawn again (a stream's answer, a changed favourite): their logos stay. */
    fun refreshCards() { for (i in 0 until outer.childCount) ((outer.getChildViewHolder(outer.getChildAt(i)) as? RowH)?.cards?.notifyDataSetChanged()) }

    fun channelAt(v: View?): List<Channel>? {
        @Suppress("UNCHECKED_CAST") return v?.getTag(TAG_CH) as? List<Channel>
    }

    // ---- the D-pad ----

    /** ▲▼◀▶ on a card (and ▼ from the top bar); true when handled here. Called on a key down. */
    fun key(k: Int): Boolean {
        val f = view.findFocus() ?: return false
        if (channelAt(f) == null) return when (k) {                    // the top bar: ◀▶ between its buttons are the focus search's
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (rows.isNotEmpty()) moveTo(0); true }
            KeyEvent.KEYCODE_DPAD_UP -> true
            else -> false
        }
        val inner = f.parent as? RecyclerView ?: return false
        val pos = inner.getChildAdapterPosition(f); val r = outer.getChildAdapterPosition(inner.parent as View)
        if (pos < 0 || r < 0) return true
        when (k) {
            KeyEvent.KEYCODE_DPAD_DOWN -> if (r + 1 < rows.size) moveTo(r + 1)
            KeyEvent.KEYCODE_DPAD_UP -> if (r > 0) moveTo(r - 1) else searchBtn.requestFocus()
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (pos + 1 < rows[r].items.size) card(inner, pos + 1) {}
            KeyEvent.KEYCODE_DPAD_LEFT -> if (pos > 0) card(inner, pos - 1) {}
            else -> return false
        }
        return true
    }

    /** Focus to column [col] of shelf [r], the shelf scrolled into full view first. */
    private fun moveTo(r: Int) {
        val vh = outer.findViewHolderForAdapterPosition(r)
        if (vh == null) { outerLm.scrollToPositionWithOffset(r, topPad); outer.post { focusIn(r, col) }; return }
        val v = vh.itemView
        val bottom = outer.height - d(20)
        val dy = if (v.top < topPad) v.top - topPad else if (v.bottom > bottom) v.bottom - bottom else 0
        if (dy != 0) outer.smoothScrollBy(0, dy, DecelerateInterpolator(), 170)
        focusIn(r, col)
    }

    private fun focusIn(r: Int, wanted: Int) {
        val vh = outer.findViewHolderForAdapterPosition(r) as? RowH ?: return
        val n = rows.getOrNull(r)?.items?.size ?: return
        jumping = true; outer.own = true
        card(vh.inner, wanted.coerceAtMost(n - 1).coerceAtLeast(0)) { jumping = false; outer.own = false }
    }

    private fun card(inner: RecyclerView, pos: Int, done: () -> Unit) {
        val v = inner.findViewHolderForAdapterPosition(pos)?.itemView
        if (v != null) { v.requestFocus(); done(); return }
        inner.scrollToPosition(pos)                                    // not drawn yet: after the scroll's layout
        inner.post { inner.findViewHolderForAdapterPosition(pos)?.itemView?.requestFocus(); done() }
    }

    // ---- the rows ----

    private class RowH(v: View, val inner: RecyclerView, val title: TextView, val count: TextView, val cards: Cards) : RecyclerView.ViewHolder(v)

    private inner class Outer : RecyclerView.Adapter<RowH>() {
        init { setHasStableIds(true) }
        override fun getItemCount() = rows.size
        override fun getItemId(i: Int) = rows[i].id.hashCode().toLong()
        override fun onCreateViewHolder(p: ViewGroup, t: Int): RowH {
            val head = LinearLayout(a).apply { gravity = Gravity.BOTTOM; setPadding(mx + d(6), d(8), mx, d(6)) }
            val title = TvUi.text(a, "", 20f, TvUi.TEXT, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
            val count = TvUi.text(a, "", 15f, TvUi.DIM).apply { setPadding(d(12), 0, 0, d(1)) }
            head.addView(title, LinearLayout.LayoutParams(-2, -2)); head.addView(count, LinearLayout.LayoutParams(-2, -2))
            val cards = Cards()
            val inner = RecyclerView(a).apply {
                layoutManager = LinearLayoutManager(a, LinearLayoutManager.HORIZONTAL, false).apply { initialPrefetchItemCount = 6 }
                adapter = cards; itemAnimator = null; setHasFixedSize(true); setItemViewCacheSize(4); setRecycledViewPool(pool)
                clipToPadding = false; setPadding(mx, 0, mx, 0); isHorizontalScrollBarEnabled = false
                descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            }
            val box = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                addView(head, LinearLayout.LayoutParams(-1, -2)); addView(inner, LinearLayout.LayoutParams(-1, cardH))
                layoutParams = RecyclerView.LayoutParams(-1, -2)
            }
            return RowH(box, inner, title, count, cards)
        }
        override fun onBindViewHolder(h: RowH, i: Int) {
            val r = rows[i]
            h.title.text = r.title; h.count.text = "${r.items.size}"
            h.cards.row = r; h.cards.notifyDataSetChanged()
            val lm = h.inner.layoutManager as LinearLayoutManager
            val s = saved[r.id]
            if (s != null) lm.onRestoreInstanceState(s) else lm.scrollToPositionWithOffset(0, 0)
        }
        override fun onViewRecycled(h: RowH) {
            val id = h.cards.row?.id ?: return
            saved[id] = (h.inner.layoutManager as LinearLayoutManager).onSaveInstanceState()
        }
    }

    private class H(v: View, val logo: ImageView, val letters: TextView, val name: TextView, val tile: View) : RecyclerView.ViewHolder(v)

    private inner class Cards : RecyclerView.Adapter<H>() {
        var row: Row? = null
        private val items get() = row?.items.orEmpty()
        init { setHasStableIds(true) }
        override fun getItemCount() = items.size
        override fun getItemId(i: Int) = items[i].first().id.hashCode().toLong()

        override fun onCreateViewHolder(p: ViewGroup, t: Int): H {
            val card = object : LinearLayout(a) {
                override fun hasOverlappingRendering() = false           // a dimmed (dead) card is not drawn into an off-screen layer
            }.apply {
                orientation = LinearLayout.VERTICAL; isFocusable = true; isClickable = true; isLongClickable = true
                setPadding(d(6), d(6), d(6), d(4))
                background = TvUi.states(a, Color.TRANSPARENT, TvUi.dp(a, 16))
                setOnFocusChangeListener { v, has ->                     // only [col] is kept: no animation, no layout on a focus move
                    if (has && !jumping) (v.parent as? RecyclerView)?.getChildAdapterPosition(v)?.let { if (it >= 0) col = it }
                }
            }
            val tile = FrameLayout(a).apply { background = TvUi.round(TvUi.TILE, TvUi.dp(a, 12)) }
            val letters = TextView(a).apply {
                gravity = Gravity.CENTER; textSize = 28f; setTextColor(LETTERS); typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            tile.addView(letters, FrameLayout.LayoutParams(-1, -1))
            val logo = LogoView(a)
            tile.addView(logo, FrameLayout.LayoutParams(-1, -1))
            card.addView(tile, LinearLayout.LayoutParams(tileW, tileH))
            val name = TvUi.text(a, "", 16f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(d(4), d(8), d(4), 0) }
            card.addView(name, LinearLayout.LayoutParams(tileW, nameH))
            card.layoutParams = RecyclerView.LayoutParams(tileW + d(12), cardH).apply { marginEnd = d(8) }
            return H(card, logo, letters, name, tile)
        }

        override fun onBindViewHolder(h: H, i: Int) {
            val ch = items[i]; val c = ch.first()
            h.name.text = c.name
            h.letters.text = TvUi.initials(c.name)
            Logos.into(h.logo, c.logo, 280) { got ->
                h.letters.visibility = if (got) View.INVISIBLE else View.VISIBLE
                h.tile.background = if (got && Logos.isLight(c.logo)) darkTile else lightTile   // pale lettering would vanish on the light tile
            }
            h.itemView.setTag(TAG_CH, ch)
            dim(h, ch)
            val title = row?.title.orEmpty(); val all = items
            h.itemView.setOnClickListener { host.play(ch, all, title) }
            h.itemView.setOnLongClickListener { host.actions(ch); true }
            ch.forEach { s -> Probe.check(a, s) { val at = items.indexOf(ch); if (at >= 0) notifyItemChanged(at, DIM) } }
            for (k in i + 1..minOf(i + 4, all.size - 1)) Logos.prefetch(a, all[k].first().logo)   // the next cards' logos start now, behind the ones on screen
        }

        override fun onBindViewHolder(h: H, i: Int, payloads: MutableList<Any>) {
            if (payloads.isEmpty()) onBindViewHolder(h, i) else dim(h, items[i])
        }

        override fun onViewRecycled(h: H) { Logos.cancel(h.logo) }

        private fun dim(h: H, ch: List<Channel>) { h.itemView.alpha = if (ch.all { Store.alive(a, it.url) == false }) 0.5f else 1f }
    }

    companion object {
        private const val LETTERS = 0xFF5F6368.toInt()                    // a logo-less card's initials: the grid's colour
        private const val TAG_CH = 0x7f0f0002                          // a card's channel (any id: no layout uses it)
        private const val DIM = "dim"
    }
}
