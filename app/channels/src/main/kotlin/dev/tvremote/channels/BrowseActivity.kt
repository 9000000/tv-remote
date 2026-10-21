package dev.tvremote.channels

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.Normalizer
import java.util.concurrent.Executors

/**
 * The list: a side column (favourites and the person's own groups, the country, its categories) and a grid of channel cards.
 * OK plays; holding OK or Menu opens what can be done with a channel (favourite, a group, pin to the Home screen) or a group
 * (pin, rename, delete). Everything is read from iptv-org in the background.
 */
class BrowseActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var rail: LinearLayout
    private lateinit var grid: RecyclerView
    private lateinit var title: TextView
    private lateinit var note: TextView
    private lateinit var retry: TextView
    private lateinit var content: View                                 // sidebar + grid: hidden while the one full-screen state (loading / error) shows
    private lateinit var wait: View
    private lateinit var spin: View
    private val last = HashMap<String, Int>()                          // per group: the card that had focus, for OK on the group
    private var loading = true
    private lateinit var overlay: View
    private lateinit var waitText: TextView
    private lateinit var waitButtons: View
    private val autoOpen = Runnable { pending?.let { if (it != group) select(it) } }
    private var pending: String? = null
    private lateinit var find: EditText
    private val cards = Cards()

    private var cc = ""
    private var all: List<List<Channel>> = emptyList()
    private var shown: List<List<Channel>> = emptyList()
    private var group = ALL
    private var watching: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null   // favourites / groups edited from a phone or computer
    private var failed = false
    private var countryName = ""

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = { v: Number -> Ui.dp(this, v).toInt() }
        val root = LinearLayout(this).apply { setBackgroundColor(0xFF0E1116.toInt()) }

        rail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(16), d(24), d(12), d(24)) }
        root.addView(ScrollView(this).apply { addView(rail); isVerticalScrollBarEnabled = false },
            LinearLayout.LayoutParams(d(250), -1))

        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(d(8), d(24), d(24), 0) }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        title = Ui.text(this, "", 22f, bold = true)
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        find = EditText(this).apply {
            hint = getString(R.string.search); setHintTextColor(Ui.DIM); setTextColor(Ui.TEXT); textSize = 15f; isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            background = Ui.states(this@BrowseActivity, Ui.CARD, Ui.dp(this@BrowseActivity, 22))
            setPadding(d(18), d(8), d(18), d(8))
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0); compoundDrawablePadding = d(8)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { filter() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        head.addView(find, LinearLayout.LayoutParams(d(300), d(44)))
        right.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = d(12) })

        val body = FrameLayout(this)
        grid = RecyclerView(this).apply {
            adapter = cards; clipToPadding = false; setPadding(d(6), d(10), d(6), d(40)); itemAnimator = null
            setHasFixedSize(true); setItemViewCacheSize(10); recycledViewPool.setMaxRecycledViews(0, 24)   // size does not depend on the cards; scrolling back finds them bound
            layoutManager = GridLayoutManager(this@BrowseActivity, columns())
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        body.addView(grid, FrameLayout.LayoutParams(-1, -1))
        val empty = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        note = Ui.text(this, "", 17f, Ui.DIM).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f) }   // an empty group / no match, inside the grid's area
        val say2 = Ui.text(this, "", 17f, Ui.DIM).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f) }
        val button = { label: String, go: () -> Unit -> Ui.text(this, label, 16f, bold = true).apply {
            setPadding(d(24), d(10), d(24), d(10)); isFocusable = true; isClickable = true
            background = Ui.states(this@BrowseActivity, Ui.CARD, Ui.dp(this@BrowseActivity, 22))
            setOnClickListener { go() }
        } }
        empty.addView(note, LinearLayout.LayoutParams(d(520), -2))
        body.addView(empty, FrameLayout.LayoutParams(-1, -1))
        right.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(right, LinearLayout.LayoutParams(0, -1, 1f))
        content = root

        // the whole screen's own state, centred on the screen (not on the grid): loading, or no list and why
        val screen = FrameLayout(this).apply { setBackgroundColor(0xFF0E1116.toInt()) }
        wait = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        spin = android.widget.ProgressBar(this).apply { isIndeterminate = true; indeterminateTintList = android.content.res.ColorStateList.valueOf(Ui.ACCENT) }
        retry = button(getString(R.string.retry)) { load(true) }
        val pickCountry = button(getString(R.string.country)) { countries() }
        (wait as LinearLayout).apply {
            addView(spin, LinearLayout.LayoutParams(d(44), d(44)).apply { bottomMargin = d(16) })
            addView(say2, LinearLayout.LayoutParams(d(520), -2))
            addView(LinearLayout(this@BrowseActivity).apply {
                gravity = Gravity.CENTER
                addView(retry); addView(pickCountry, LinearLayout.LayoutParams(-2, -2).apply { marginStart = d(12) })
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = d(16) })
        }
        waitText = say2; waitButtons = (wait as LinearLayout).getChildAt(2)
        screen.addView(wait, FrameLayout.LayoutParams(-1, -1))
        setContentView(FrameLayout(this).apply { addView(root, FrameLayout.LayoutParams(-1, -1)); addView(screen, FrameLayout.LayoutParams(-1, -1)) })
        overlay = screen

        cc = countryOf(intent) ?: Store.country(this)
        groupOf(intent)?.let { group = it }
        intent.getStringExtra(QUERY)?.let { find.setText(it) }
        load(false)
        watching = Store.watch(this) { runOnUiThread {
            val g = mine(group)
            if (g != null && g != Store.FAV && Store.group(this, g) == null) group = ALL      // the open group was deleted
            buildRail(); filter()
        } }
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        countryOf(i)?.takeIf { it != cc }?.let { cc = it; group = ALL; load(false) }
        groupOf(i)?.let { find.setText(""); pick(it) }
        i.getStringExtra(QUERY)?.let { group = ALL; find.setText(it); buildRail(); filter() }
    }

    override fun onResume() { super.onResume(); cards.notifyDataSetChanged(); if (mine(group) != null) { buildRail(); filter() } }

    /** A group link (a pin on the home screen): the favourites or one of the groups, when it still exists. */
    private fun groupOf(i: Intent?) = Pins.groupId(i?.data)?.let { if (it == Store.FAV) FAVS else if (Store.group(this, it) != null) MINE + it else null }

    /** The group of the person's that [g] shows ([Store.FAV] for the favourites), or null for a category of the country. */
    private fun mine(g: String) = if (g == FAVS) Store.FAV else if (g.startsWith(MINE)) g.removePrefix(MINE) else null
    private fun mineName(gid: String) = if (gid == Store.FAV) getString(R.string.favourites) else Store.group(this, gid)?.name ?: ""

    private fun countryOf(i: Intent?) = i?.data?.takeIf { it.scheme == "tvchannels" && it.host == "country" }
        ?.pathSegments?.firstOrNull()?.lowercase()?.takeIf { it.length == 2 }

    private fun columns() = (resources.displayMetrics.widthPixels / resources.displayMetrics.density - 280).let { (it / 130).toInt().coerceIn(2, 7) }

    private fun load(fresh: Boolean) {
        Store.setCountry(this, cc)
        all = emptyList(); failed = false; loading = true
        chrome()
        io.execute {
            val list = Catalog.channels(this, cc, fresh)
            val name = Catalog.countries(this).firstOrNull { it.code == cc }?.let { "${it.flag} ${it.name}" } ?: cc.uppercase()
            val ok = list.isNotEmpty() || java.io.File(filesDir, "m3u-$cc.m3u").exists()
            main.post {
                if (isDestroyed) return@post
                all = list.byChannel(); failed = !ok; countryName = name; loading = false
                if (mine(group) == null && group != ALL && all.none { it.first().group == group }) group = ALL
                buildRail(); filter()
                if (!find.hasFocus()) grid.post { (grid.getChildAt(0) ?: rail.getChildAt(0))?.requestFocus() }
            }
        }
    }

    private fun buildRail() {
        val hadFocus = rail.hasFocus()
        val at = (0 until rail.childCount).firstOrNull { rail.getChildAt(it).hasFocus() } ?: -1
        main.removeCallbacks(autoOpen)
        rail.removeAllViews()
        val dp = { n: Int -> Ui.dp(this, n).toInt() }
        rail.addView(LinearLayout(this).apply {                       // the app's own tile beside its name
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), 0, 0, dp(18))
            addView(ImageView(this@BrowseActivity).apply { setImageResource(R.drawable.icon) }, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(10) })
            addView(Ui.text(this@BrowseActivity, getString(R.string.app_name), 20f, Ui.ACCENT, true))
        })
        val favs = Store.favourites(this).byChannel().size
        add(Ui.row(this, R.drawable.ic_star, getString(R.string.favourites), if (favs > 0) "$favs" else "") { pick(FAVS) }.held(Store.FAV), group == FAVS, FAVS)
        for (g in Store.groups(this)) {
            val n = g.channels.byChannel().size
            add(Ui.row(this, R.drawable.ic_folder, g.name, if (n > 0) "$n" else "") { pick(MINE + g.id) }.held(g.id), group == MINE + g.id, MINE + g.id)
        }
        add(Ui.row(this, R.drawable.ic_plus, getString(R.string.new_group)) { ask(getString(R.string.new_group), "") { pick(MINE + Store.addGroup(this, it)) } }, false)
        add(Ui.row(this, R.drawable.ic_globe, countryName.ifEmpty { getString(R.string.country) }) { countries() }, false)
        add(Ui.row(this, R.drawable.ic_refresh, getString(R.string.refresh)) { load(true) }, false)
        rail.addView(View(this).apply { setBackgroundColor(0x22FFFFFF) }, LinearLayout.LayoutParams(-1, 1).apply {
            val m = Ui.dp(this@BrowseActivity, 10).toInt(); topMargin = m; bottomMargin = m })
        if (all.isNotEmpty()) {
        add(Ui.row(this, R.drawable.ic_tv, getString(R.string.all), "${all.size}") { pick(ALL) }, group == ALL, ALL)
        all.groupingBy { it.first().group }.eachCount().entries.sortedByDescending { it.value }.forEach { (g, n) ->
            add(Ui.row(this, 0, groupName(g), "$n") { pick(g) }, group == g, g)
        }
        }
        if (hadFocus && at in 0 until rail.childCount) rail.getChildAt(at).requestFocus()   // a rebuilt list keeps the person's place
    }

    /** A row of one of the person's groups: Menu or a long press opens [groupMenu]. */
    private fun View.held(gid: String) = apply { tag = "grp:$gid"; isLongClickable = true; setOnLongClickListener { groupMenu(gid); true } }

    /** [g] set: a group or category, opened by moving onto it (after a pause); null: an action, only on OK. */
    private fun add(v: View, on: Boolean, g: String? = null) {
        v.isActivated = on
        if (g != null) {
            v.setTag(TAG_GROUP, g)
            v.setOnFocusChangeListener { _, has ->
                if (has) { pending = g; main.removeCallbacks(autoOpen); if (g != group) main.postDelayed(autoOpen, 250) }
            }
        }
        rail.addView(v, LinearLayout.LayoutParams(-1, -2))
    }

    /** Opens [g] with the list's focus where it is: only the grid and the marked row change, nothing is rebuilt. */
    private fun select(g: String) {
        main.removeCallbacks(autoOpen)
        group = g
        for (i in 0 until rail.childCount) rail.getChildAt(i).let { v -> (v.getTag(TAG_GROUP) as? String)?.let { v.isActivated = it == g } }
        filter(); grid.scrollToPosition(0)
    }

    private fun pick(g: String) {
        group = g; buildRail(); filter()
        main.removeCallbacks(autoOpen)
        intoGrid()
    }

    /** Focus to the card this group had last, else the first. */
    private fun intoGrid() {
        val n = last[group]?.takeIf { it in shown.indices } ?: 0
        (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(n, grid.height / 3)
        grid.post { (grid.findViewHolderForAdapterPosition(n)?.itemView ?: grid.getChildAt(0))?.requestFocus() }
    }

    private fun filter() {
        val gid = mine(group)
        val base = if (gid != null) Store.channels(this, gid).byChannel() else if (group == ALL) all else all.filter { it.first().group == group }
        val q = fold(find.text.toString())
        shown = if (q.isEmpty()) base else base.filter { fold(it.first().name).contains(q) }
        cards.notifyDataSetChanged()
        title.text = when (group) {
            ALL -> countryName
            else -> if (gid != null) mineName(gid) else "$countryName · ${groupName(group)}"
        }
        chrome()
        when {
            shown.isNotEmpty() || all.isEmpty() && gid == null -> say("", false)
            q.isNotEmpty() -> say(getString(R.string.no_match), false)
            gid == Store.FAV -> say(getString(R.string.no_favs), false)
            gid != null -> say(getString(R.string.no_in_group), false)
            else -> say(getString(R.string.none_here), true)
        }
    }

    private fun say(s: String, again: Boolean) { note.text = s; Ui.show(note, s.isNotEmpty()) }

    /** One state for the whole screen while there is no list to show: loading (a spinner), offline or empty (the words, Retry, Country). */
    private fun chrome() {
        val on = loading || all.isEmpty() && mine(group) == null
        Ui.show(overlay, on); Ui.show(content, !on)
        if (!on) return
        Ui.show(spin, loading); Ui.show(waitButtons, !loading)
        waitText.text = if (loading) getString(R.string.loading) else getString(if (failed) R.string.offline else R.string.none_here)
        if (!loading && !overlay.hasFocus()) retry.post { retry.requestFocus() }
    }


    fun play(ch: List<Channel>) {
        Lineup.set(shown, title.text.toString())
        startActivity(Intent(Intent.ACTION_VIEW, Pins.link(ch.first().id)).setClass(this, PlayActivity::class.java))
    }

    /** What can be done with one channel: on a TV the Menu key or a long OK, on a phone a long press. */
    fun actions(ch: List<Channel>) {
        val c = ch.first()
        val dlg = Dialog(this, R.style.Dialog)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(this@BrowseActivity, 14).toInt(); setPadding(p, p, p, p)
            background = Ui.round(0xF21B2028.toInt(), Ui.dp(this@BrowseActivity, 20), 1, 0x40FFFFFF)
        }
        box.addView(Ui.text(this, c.name, 18f, bold = true).apply { setPadding(Ui.dp(this@BrowseActivity, 12).toInt(), 0, 0, Ui.dp(this@BrowseActivity, 10).toInt()) })
        box.addView(Ui.row(this, R.drawable.ic_play, getString(R.string.play)) { dlg.dismiss(); play(ch) })
        val fav = Store.isFavourite(this, c.id)
        box.addView(Ui.row(this, R.drawable.ic_star, getString(if (fav) R.string.remove_fav else R.string.add_fav)) {
            dlg.dismiss(); Store.toggleFavourite(this, ch); buildRail(); filter()
        })
        box.addView(Ui.row(this, R.drawable.ic_folder, getString(R.string.to_group)) { dlg.dismiss(); groupsFor(ch) })
        box.addView(Ui.row(this, R.drawable.ic_guide, getString(R.string.guide)) { dlg.dismiss(); guideOf(ch) })
        if (Pins.can(this)) box.addView(Ui.row(this, R.drawable.ic_home, getString(R.string.pin)) {
            dlg.dismiss()
            io.execute {
                val logo = c.logo.takeIf { it.isNotEmpty() }?.let { try { Logos.load(it, 280) } catch (e: Throwable) { null } }
                val ok = Pins.pin(this, c, logo)
                main.post { if (!ok) Toast.makeText(this, R.string.pin_refused, Toast.LENGTH_LONG).show() }
            }
        })
        val why = listOfNotNull(c.quality.takeIf { it.isNotEmpty() }, groupName(c.group),
            getString(R.string.geo).takeIf { c.geo }, getString(R.string.not_always).takeIf { c.notAlways },
            getString(R.string.down).takeIf { Store.alive(this, c.url) == false && ch.size == 1 })
        box.addView(Ui.text(this, why.joinToString(" · "), 13f, Ui.DIM).apply {
            setPadding(Ui.dp(this@BrowseActivity, 12).toInt(), Ui.dp(this@BrowseActivity, 10).toInt(), 0, 0) })
        dlg.setContentView(box, ViewGroup.LayoutParams(Ui.dp(this, 380).toInt(), -2))
        dlg.show()
    }

    /** A channel's programme guide, the show on now focused. */
    private fun guideOf(ch: List<Channel>) {
        io.execute {
            val shows = Guide.of(this, ch)
            main.post {
                if (isDestroyed) return@post
                if (shows.isEmpty()) { Toast.makeText(this, R.string.guide_none, Toast.LENGTH_SHORT).show(); return@post }
                val (dlg, box) = sheet(480)
                heading(box, "${getString(R.string.guide)} · ${ch.first().name}")
                val list = GuideList.view(this, shows)
                box.addView(list, LinearLayout.LayoutParams(-1, Ui.dp(this, 440).toInt()))
                dlg.show()
                cards.notifyDataSetChanged()                          // the cards' "on now" lines, now known
            }
        }
    }

    /** A dialog box, the look all of them share. */
    private fun sheet(w: Int = 380): Pair<Dialog, LinearLayout> {
        val dlg = Dialog(this, R.style.Dialog)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(this@BrowseActivity, 14).toInt(); setPadding(p, p, p, p)
            background = Ui.round(0xF21B2028.toInt(), Ui.dp(this@BrowseActivity, 20), 1, 0x40FFFFFF)
        }
        dlg.setContentView(box, ViewGroup.LayoutParams(Ui.dp(this, w).toInt(), -2))
        return dlg to box
    }

    private fun heading(box: LinearLayout, s: String) =
        box.addView(Ui.text(this, s, 18f, bold = true).apply { setPadding(Ui.dp(this@BrowseActivity, 12).toInt(), 0, 0, Ui.dp(this@BrowseActivity, 10).toInt()) })

    /** Which of the person's groups [ch] is in: a tick on each, OK puts it in or takes it out; and a new group with it in. */
    private fun groupsFor(ch: List<Channel>) {
        val (dlg, box) = sheet()
        val id = ch.first().id
        heading(box, ch.first().name)
        for (g in Store.groups(this)) {
            val row = Ui.row(this, if (Store.has(this, g.id, id)) R.drawable.ic_check else R.drawable.ic_folder, g.name) {}
            row.setOnClickListener {
                val on = Store.toggle(this, g.id, ch)
                (row.getChildAt(0) as ImageView).setImageResource(if (on) R.drawable.ic_check else R.drawable.ic_folder)
                buildRail(); if (group == MINE + g.id) filter()
            }
            box.addView(row)
        }
        box.addView(Ui.row(this, R.drawable.ic_plus, getString(R.string.new_group)) {
            dlg.dismiss()
            ask(getString(R.string.new_group), "") { name -> Store.toggle(this, Store.addGroup(this, name), ch); buildRail(); filter() }
        })
        dlg.show()
        box.post { box.getChildAt(1)?.requestFocus() }
    }

    /** One group's menu: pin it to the Home screen; the person's own groups can also be renamed and deleted. */
    private fun groupMenu(gid: String) {
        val (dlg, box) = sheet()
        val name = mineName(gid)
        heading(box, name)
        box.addView(Ui.row(this, R.drawable.ic_play, getString(R.string.open)) { dlg.dismiss(); pick(if (gid == Store.FAV) FAVS else MINE + gid) })
        if (Pins.can(this)) box.addView(Ui.row(this, R.drawable.ic_home, getString(R.string.pin)) {
            dlg.dismiss()
            val logos = Store.channels(this, gid).byChannel().map { it.first().logo }.filter { it.isNotEmpty() }.take(4)
            io.execute {
                val bits = logos.mapNotNull { try { Logos.load(it, 150) } catch (e: Throwable) { null } }
                val ok = Pins.pinGroup(this, gid, name, bits)
                main.post { if (!ok) Toast.makeText(this, R.string.pin_refused, Toast.LENGTH_LONG).show() }
            }
        })
        if (gid != Store.FAV) {
            box.addView(Ui.row(this, R.drawable.ic_pen, getString(R.string.rename)) {
                dlg.dismiss()
                ask(getString(R.string.rename), name) { Store.renameGroup(this, gid, it); Pins.renamed(this, gid, it); buildRail(); filter() }
            })
            box.addView(Ui.row(this, R.drawable.ic_trash, getString(R.string.delete_group)) {
                dlg.dismiss()
                val (sure, b) = sheet()                               // asked again, Keep first: a stray OK loses nothing
                heading(b, getString(R.string.delete_ask, name))
                b.addView(Ui.row(this, R.drawable.ic_close, getString(R.string.keep)) { sure.dismiss() })
                b.addView(Ui.row(this, R.drawable.ic_trash, getString(R.string.delete_group)) {
                    sure.dismiss()
                    Store.deleteGroup(this, gid); Pins.deleted(this, gid)
                    if (group == MINE + gid) group = ALL
                    buildRail(); filter()
                })
                sure.show()
            })
        }
        dlg.show()
    }

    /** A name, typed: OK keeps it (not when empty), Back leaves it. */
    private fun ask(title: String, now: String, done: (String) -> Unit) {
        val (dlg, box) = sheet(420)
        heading(box, title)
        val f = EditText(this).apply {
            setText(now); setSelection(now.length); hint = getString(R.string.group_name); setHintTextColor(Ui.DIM); setTextColor(Ui.TEXT)
            isSingleLine = true; textSize = 16f; imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            background = Ui.states(this@BrowseActivity, 0x14FFFFFF, Ui.dp(this@BrowseActivity, 18))
            val p = Ui.dp(this@BrowseActivity, 14).toInt(); setPadding(p, p / 2, p, p / 2)
        }
        val ok = { val t = f.text.toString().trim(); if (t.isNotEmpty()) { dlg.dismiss(); done(t.take(40)) } }
        f.setOnEditorActionListener { _, _, _ -> ok(); true }
        box.addView(f, LinearLayout.LayoutParams(-1, Ui.dp(this, 46).toInt()).apply { bottomMargin = Ui.dp(this@BrowseActivity, 8).toInt() })
        box.addView(Ui.row(this, R.drawable.ic_check, getString(R.string.ok)) { ok() })
        dlg.show()
        f.requestFocus()
    }

    private fun countries() {
        val dlg = Dialog(this, R.style.Dialog)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(this@BrowseActivity, 14).toInt(); setPadding(p, p, p, p)
            background = Ui.round(0xF21B2028.toInt(), Ui.dp(this@BrowseActivity, 20), 1, 0x40FFFFFF)
        }
        var preBack: () -> Boolean = { false }
        var swallowUp = false                                      // the Back that left the field must not close the modal on its key-up
        val q = object : EditText(this) {                         // Back while the keyboard is up goes to the keyboard first: caught here, before it
            override fun onKeyPreIme(k: Int, e: KeyEvent) = if (k == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_DOWN && preBack()) true else super.onKeyPreIme(k, e)
        }.apply {
            hint = getString(R.string.country); setHintTextColor(Ui.DIM); setTextColor(Ui.TEXT); isSingleLine = true; textSize = 15f
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            background = Ui.states(this@BrowseActivity, 0x14FFFFFF, Ui.dp(this@BrowseActivity, 18))
            val p = Ui.dp(this@BrowseActivity, 14).toInt(); setPadding(p, p / 2, p, p / 2)
        }
        box.addView(q, LinearLayout.LayoutParams(-1, Ui.dp(this, 42).toInt()).apply { bottomMargin = Ui.dp(this@BrowseActivity, 8).toInt() })
        val list = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@BrowseActivity) }
        val none = Ui.text(this, getString(R.string.country_none), 17f, Ui.DIM).apply { gravity = Gravity.CENTER; visibility = View.GONE }
        val lh = (resources.displayMetrics.heightPixels * 0.62).toInt()
        box.addView(FrameLayout(this).apply {
            addView(list, FrameLayout.LayoutParams(-1, -1)); addView(none, FrameLayout.LayoutParams(-1, -1))
        }, LinearLayout.LayoutParams(-1, lh))
        dlg.setContentView(box, ViewGroup.LayoutParams(Ui.dp(this, 420).toInt(), -2))
        io.execute {
            val every = Catalog.countries(this)
            main.post {
                if (!dlg.isShowing) return@post
                var now = every.sortedBy { it.code != cc }
                val ad = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    override fun getItemCount() = now.size
                    override fun onCreateViewHolder(p: ViewGroup, t: Int) = object : RecyclerView.ViewHolder(
                        Ui.row(this@BrowseActivity, 0, "") {}.apply { layoutParams = RecyclerView.LayoutParams(-1, -2) }) {}
                    override fun onBindViewHolder(h: RecyclerView.ViewHolder, i: Int) {
                        val k = now[i]; val row = h.itemView as LinearLayout
                        (row.getChildAt(0) as TextView).text = "${k.flag}  ${k.name}"
                        row.isActivated = k.code == cc
                        row.setOnClickListener { dlg.dismiss(); if (k.code != cc) { cc = k.code; group = ALL; find.setText(""); load(false) } }
                    }
                }
                list.adapter = ad
                // Back / the keyboard's action key leave the field for the results (the selected country, else the first); Down and Up cross between them
                val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                val toList = fun(): Boolean {
                    if (now.isEmpty()) return false
                    val at = now.indexOfFirst { it.code == cc }.coerceAtLeast(0)
                    list.scrollToPosition(at)
                    list.post { (list.findViewHolderForAdapterPosition(at)?.itemView ?: list.getChildAt(0))?.requestFocus() }
                    return true
                }
                q.setOnEditorActionListener { _, _, _ -> imm.hideSoftInputFromWindow(q.windowToken, 0); toList(); true }
                preBack = { if (q.text.isNotEmpty() && now.isNotEmpty()) { swallowUp = true; imm.hideSoftInputFromWindow(q.windowToken, 0); toList() } else false }
                dlg.setOnKeyListener { _, k, e ->
                    val down = e.action == KeyEvent.ACTION_DOWN
                    if (k == KeyEvent.KEYCODE_BACK && !down && swallowUp) { swallowUp = false; return@setOnKeyListener true }
                    when {
                        k == KeyEvent.KEYCODE_BACK && q.hasFocus() && q.text.isNotEmpty() && now.isNotEmpty() -> {
                            if (down) { imm.hideSoftInputFromWindow(q.windowToken, 0); toList() }; true }
                        k == KeyEvent.KEYCODE_DPAD_DOWN && q.hasFocus() && now.isNotEmpty() -> { if (down) toList(); true }
                        k == KeyEvent.KEYCODE_DPAD_UP && down && list.hasFocus() &&
                            (list.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition() <= 0 &&
                            list.findContainingViewHolder(dlg.currentFocus ?: q)?.bindingAdapterPosition == 0 -> { q.requestFocus(); true }
                        else -> false
                    }
                }
                q.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        val f = fold(s.toString())
                        now = if (f.isEmpty()) every.sortedBy { it.code != cc } else every.filter { fold(it.name).contains(f) || it.code == f }
                        ad.notifyDataSetChanged()
                        none.visibility = if (now.isEmpty()) View.VISIBLE else View.GONE
                    }
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                })
                list.post { list.getChildAt(0)?.requestFocus() }
            }
        }
        dlg.show()
    }

    override fun onKeyDown(k: Int, e: KeyEvent): Boolean {
        val v0 = currentFocus
        if (k == KeyEvent.KEYCODE_BACK && e.repeatCount == 0 && v0 != null && content.visibility == View.VISIBLE && !rail.hasFocus()) {
            if (find.text.isNotEmpty()) { find.setText(""); if (find.hasFocus()) grid.getChildAt(0)?.requestFocus(); return true }   // leave the search first
            (0 until rail.childCount).map { rail.getChildAt(it) }.firstOrNull { it.isActivated }?.let { it.requestFocus(); return true }
        }
        if (k == KeyEvent.KEYCODE_DPAD_RIGHT && v0 != null && rail.hasFocus() && shown.isNotEmpty()) {   // into the grid, the open group made sure of
            (v0.getTag(TAG_GROUP) as? String)?.let { if (it != group) select(it) }
            intoGrid(); return true
        }
        if (k == KeyEvent.KEYCODE_DPAD_LEFT) {                        // from the grid's first column: back to the list on show, not the nearest row
            val at = currentFocus?.let { grid.findContainingViewHolder(it) }?.bindingAdapterPosition ?: -1
            if (at >= 0 && at % (grid.layoutManager as GridLayoutManager).spanCount == 0)
                (0 until rail.childCount).map { rail.getChildAt(it) }.firstOrNull { it.isActivated }?.let { it.requestFocus(); return true }
        }
        if (k == KeyEvent.KEYCODE_MENU || (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) && e.isLongPress) {
            val v = currentFocus ?: return super.onKeyDown(k, e)
            val at = grid.findContainingViewHolder(v)?.bindingAdapterPosition ?: -1
            if (at in shown.indices) { actions(shown[at]); return true }
            (v.tag as? String)?.takeIf { k == KeyEvent.KEYCODE_MENU && it.startsWith("grp:") }?.let { groupMenu(it.removePrefix("grp:")); return true }
        }
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) e.startTracking()
        return super.onKeyDown(k, e)
    }

    override fun onKeyLongPress(k: Int, e: KeyEvent) = onKeyDown(k, e)

    private val guides = java.util.concurrent.Executors.newFixedThreadPool(2) { r ->
        Thread { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND + 4); r.run() }
    }
    private val asking = HashSet<String>()

    /** The card's "on now" line: asked for the cards drawn, two at a time, then the card is drawn again. */
    private fun guideFor(ch: List<Channel>) {
        val id = ch.first().id
        if (!asking.add(id)) return
        guides.execute {
            val shows = Guide.of(this, ch)
            main.post {
                asking.remove(id)
                if (!isDestroyed && Guide.now(shows) != null) shown.indexOfFirst { it.first().id == id }.takeIf { it >= 0 }?.let { cards.notifyItemChanged(it, cards.LINE) }
            }
        }
    }

    override fun onDestroy() { main.removeCallbacks(autoOpen); watching?.let { Store.unwatch(this, it) }; io.shutdownNow(); guides.shutdownNow(); super.onDestroy() }

    /** Stable ids: a list drawn again (a favourite or a group changed) keeps the focus on the same channel, not on whatever card took its place. */
    private inner class Cards : RecyclerView.Adapter<Cards.H>() {
        val LINE = "line"
        private val lightTile by lazy { Ui.round(LogoFit.LIGHT_TILE, Ui.dp(this@BrowseActivity, 12)) }   // lazy: the activity has no resources yet while its fields are built
        private val darkTile by lazy { Ui.round(LogoFit.DARK_TILE, Ui.dp(this@BrowseActivity, 12)) }   // lighter than the card, so its edge shows
        init { setHasStableIds(true) }
        override fun getItemId(i: Int) = shown[i].first().id.hashCode().toLong()   // one card per id ([byChannel])
        inner class H(v: View) : RecyclerView.ViewHolder(v) {
            val logo = v.findViewWithTag<ImageView>("logo")
            val letters = v.findViewWithTag<TextView>("letters")
            val name = v.findViewWithTag<TextView>("name")
            val sub = v.findViewWithTag<TextView>("sub")
            val star = v.findViewWithTag<ImageView>("star")
            val q = v.findViewWithTag<TextView>("q")
        }

        override fun getItemCount() = shown.size

        override fun onCreateViewHolder(p: ViewGroup, t: Int): H {
            val c = this@BrowseActivity
            val d = { v: Number -> Ui.dp(c, v).toInt() }
            val card = object : LinearLayout(c) {
                override fun hasOverlappingRendering() = false           // a dimmed (dead) card is not drawn into an off-screen layer
            }.apply {
                orientation = LinearLayout.VERTICAL; isFocusable = true; isClickable = true; isLongClickable = true
                setPadding(d(6), d(6), d(6), d(8))
                background = Ui.states(c, Color.TRANSPARENT, Ui.dp(c, 16))
                setOnFocusChangeListener { v, has ->
                    if (has) grid.getChildAdapterPosition(v).let { if (it >= 0) last[group] = it }
                    v.animate().scaleX(if (has) 1.06f else 1f).scaleY(if (has) 1.06f else 1f).setDuration(120).start() }
            }
            val tile = object : FrameLayout(c) {                         // 16:9 from its own width: a card made before the grid is measured must not take its logo's height
                override fun onMeasure(wm: Int, hm: Int) {
                    val w = MeasureSpec.getSize(wm)
                    super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(w * 9 / 16, MeasureSpec.EXACTLY))
                }
            }.apply { background = Ui.round(Ui.TILE, Ui.dp(c, 12)) }
            tile.addView(TextView(c).apply {
                tag = "letters"; gravity = Gravity.CENTER; textSize = 26f; setTextColor(0xFF5F6368.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }, FrameLayout.LayoutParams(-1, -1))
            tile.addView(LogoView(c).apply { tag = "logo" }, FrameLayout.LayoutParams(-1, -1))
            tile.addView(ImageView(c).apply { tag = "star"; setImageResource(R.drawable.ic_star); imageTintList = android.content.res.ColorStateList.valueOf(0xFFF9AB00.toInt()) },
                FrameLayout.LayoutParams(d(18), d(18), Gravity.TOP or Gravity.END).apply { topMargin = d(6); rightMargin = d(6) })
            tile.addView(Ui.text(c, "", 9f, Color.WHITE, true).apply {                // a corner: logos sit in the middle
                tag = "q"; setPadding(d(4), d(1), d(4), d(1)); background = Ui.round(0x8C000000.toInt(), Ui.dp(c, 4))
            }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { topMargin = d(5); leftMargin = d(5) })
            card.addView(tile, LinearLayout.LayoutParams(-1, -2))
            card.addView(Ui.text(c, "", 14f).apply { tag = "name"; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(d(4), d(8), d(4), 0) })
            card.addView(Ui.text(c, "", 12f, Ui.DIM).apply { tag = "sub"; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(d(4), d(4), d(4), 0) })
            card.layoutParams = RecyclerView.LayoutParams(-1, -2).apply { val m = d(6); setMargins(m, m, m, m) }
            return H(card)
        }

        override fun onBindViewHolder(h: H, i: Int) {
            val ch = shown[i]; val c = ch.first()
            h.name.text = c.name
            h.letters.text = Ui.initials(c.name)
            Logos.into(h.logo, c.logo, 280) { got ->
                h.letters.visibility = if (got) View.INVISIBLE else View.VISIBLE
                val dark = got && Logos.isLight(c.logo)                // pale lettering would vanish on the light tile
                (h.logo.parent as View).background = if (dark) darkTile else lightTile
            }
            line(h, ch)
            h.q.text = c.quality.removeSuffix("p").let { if (it.isEmpty()) "" else if (it.toIntOrNull()?.let { n -> n >= 720 } == true) if (it == "2160") "4K" else "HD" else "SD" }
            Ui.show(h.q, c.quality.isNotEmpty())
            Ui.show(h.star, group != FAVS && Store.isFavourite(this@BrowseActivity, c.id))
            h.itemView.setOnClickListener { play(ch) }
            h.itemView.setOnLongClickListener { actions(ch); true }
            ch.forEach { s -> Probe.check(this@BrowseActivity, s) { val at = shown.indexOf(ch); if (at >= 0) notifyItemChanged(at, LINE) } }
            val ahead = columns()                                     // the next two rows' logos start now, behind the ones on screen
            for (k in i + 1..minOf(i + 2 * ahead, shown.size - 1)) Logos.prefetch(this@BrowseActivity, shown[k].first().logo)
        }

        /** Only the line under the name and the dimming: what a stream check or the guide changes (the logo is not touched). */
        override fun onBindViewHolder(h: H, i: Int, payloads: MutableList<Any>) {
            if (payloads.isEmpty()) onBindViewHolder(h, i) else line(h, shown[i])
        }

        override fun onViewRecycled(h: H) { Logos.cancel(h.logo) }

        private fun line(h: H, ch: List<Channel>) {
            val c = ch.first()
            val dead = ch.all { Store.alive(this@BrowseActivity, it.url) == false }
            val on = Guide.known(c.id)?.let { Guide.now(it) }
            h.sub.text = (on?.title ?: groupName(c.group)).let { if (dead) "● $it" else it }
            if (on == null && Guide.known(c.id) == null) guideFor(ch)
            h.sub.setTextColor(if (dead) Ui.BAD else Ui.DIM)
            h.itemView.alpha = if (dead) 0.6f else 1f
        }
    }

    companion object {
        private const val TAG_GROUP = 0x7f0f0001                      // a rail row's group (any id: no layout uses it)
        const val QUERY = "query"                                    // text for the search box (a find link that matched nothing)
        const val ALL = ""
        const val FAVS = "\u0000favs"
        const val MINE = "\u0000g:"                                  // + a group's id: one of the person's groups on show

        fun fold(s: String) = Normalizer.normalize(s.lowercase().replace('đ', 'd'), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()

        /** iptv-org's group names in the app's language where we have them; its "Undefined" (no category given) is "Other". */
        fun groupName(g: String) = if (java.util.Locale.getDefault().language == "vi") VI[g.lowercase()] ?: g else if (g.equals("undefined", true)) "Other" else g

        private val VI = mapOf(
            "general" to "Tổng hợp", "news" to "Tin tức", "entertainment" to "Giải trí", "movies" to "Phim", "series" to "Phim bộ",
            "sports" to "Thể thao", "kids" to "Thiếu nhi", "music" to "Âm nhạc", "documentary" to "Tài liệu", "education" to "Giáo dục",
            "culture" to "Văn hóa", "religious" to "Tôn giáo", "lifestyle" to "Đời sống", "cooking" to "Ẩm thực", "travel" to "Du lịch",
            "business" to "Kinh tế", "comedy" to "Hài", "family" to "Gia đình", "legislative" to "Quốc hội", "outdoor" to "Ngoài trời",
            "science" to "Khoa học", "shop" to "Mua sắm", "weather" to "Thời tiết", "auto" to "Xe", "animation" to "Hoạt hình",
            "classic" to "Kinh điển", "relax" to "Thư giãn", "public" to "Công cộng", "undefined" to "Khác",
        )
    }
}
