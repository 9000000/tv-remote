package dev.tvremote.channels

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.tvremote.tvui.TvUi

/** A channel's shows as a list, a day's title before each day; the one on now marked, gone ones dimmed. The player and the list share it. */
class GuideList(private val c: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var rows: List<Any> = emptyList()                         // String: a day's title; Show: a programme
    var onNow = -1; private set

    fun set(shows: List<Show>) {
        val out = ArrayList<Any>()
        val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
        val t = System.currentTimeMillis()
        val today = day.format(java.util.Date(t))
        var last = ""
        for (s in shows) {
            val d = c.getString(if (day.format(java.util.Date(s.start)) <= today) R.string.today else R.string.tomorrow)
            if (d != last) { last = d; out += d }
            out += s
        }
        rows = out
        onNow = rows.indexOfFirst { it is Show && t >= it.start && t < it.end }
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size
    override fun getItemViewType(i: Int) = if (rows[i] is String) 0 else 1

    override fun onCreateViewHolder(p: ViewGroup, type: Int): RecyclerView.ViewHolder {
        val d = { v: Number -> TvUi.dp(c, v).toInt() }
        val v: View = if (type == 0) TvUi.text(c, "", 13f, TvUi.ACCENT, true).apply { setPadding(d(12), d(14), d(12), d(6)) }
        else LinearLayout(c).apply {
            gravity = Gravity.CENTER_VERTICAL; isFocusable = true
            setPadding(d(12), d(9), d(12), d(9))
            background = TvUi.states(c, Color.TRANSPARENT, TvUi.dp(c, 12), edge = false)
            addView(TvUi.text(c, "", 15f, TvUi.DIM).apply { minWidth = d(62) })
            addView(TvUi.text(c, "", 15f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        v.layoutParams = RecyclerView.LayoutParams(-1, -2)
        return object : RecyclerView.ViewHolder(v) {}
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, i: Int) {
        val r = rows[i]
        if (r is String) { (h.itemView as TextView).text = r; return }
        val s = r as Show
        val row = h.itemView as LinearLayout
        val on = i == onNow
        (row.getChildAt(0) as TextView).apply { text = Guide.hm(s.start); setTextColor(if (on) TvUi.ACCENT else TvUi.DIM) }
        val bell = Reminders.mark(c, s.ch?.id.orEmpty(), s.start)
        (row.getChildAt(1) as TextView).text = bell + s.title
        row.setOnClickListener { remind(s, i) }                       // OK on a programme still to come: a reminder, OK again takes it away
        row.isActivated = on
        row.alpha = if (s.end <= System.currentTimeMillis()) 0.5f else 1f
    }

    private fun remind(s: Show, i: Int) {
        val ch = s.ch ?: return
        val msg = when (Reminders.toggle(c, ch, s)) {
            true -> c.getString(R.string.remind_set, Guide.hm(Reminders.fireAt(s.start, System.currentTimeMillis())!!))
            false -> c.getString(R.string.remind_off)
            null -> if (s.start <= System.currentTimeMillis() && s.end > System.currentTimeMillis()) return else c.getString(R.string.remind_past)
        }
        android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_SHORT).show()
        if (msg != c.getString(R.string.remind_off) && !ReminderNotice.canOverlay(c) && Store.raw(c, "overlay_hint") == null) {   // once: why a reminder may not show over other apps
            Store.putRaw(c, "overlay_hint", "1")
            android.widget.Toast.makeText(c, R.string.remind_nooverlay, android.widget.Toast.LENGTH_LONG).show()
        }
        notifyItemChanged(i)
    }

    companion object {
        /** A list of [shows] scrolled to the one on now, which takes the focus. */
        fun view(c: Context, shows: List<Show>): RecyclerView {
            val a = GuideList(c).apply { set(shows) }
            return RecyclerView(c).apply {
                layoutManager = LinearLayoutManager(c); adapter = a; itemAnimator = null
                isFocusable = false; descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                focusOn(this, a.onNow)
            }
        }

        fun focusOn(r: RecyclerView, at: Int) {
            if (at < 0) return
            (r.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(at, TvUi.dp(r.context, 90).toInt())
            r.post { r.findViewHolderForAdapterPosition(at)?.itemView?.requestFocus() }
        }
    }
}
