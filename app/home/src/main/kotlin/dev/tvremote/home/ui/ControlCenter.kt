package dev.tvremote.home.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.tvremote.home.Font
import dev.tvremote.home.HomeConfig
import dev.tvremote.home.Power
import dev.tvremote.home.R
import dev.tvremote.home.Store
import dev.tvremote.home.apps.Tiles
import dev.tvremote.home.widgets.memoryUsage
import dev.tvremote.tvui.Motion
import dev.tvremote.tvui.TvGlass
import dev.tvremote.tvui.TvUi
import org.json.JSONObject
import java.net.Inet4Address
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Control Center (Trung tâm điều khiển): one glass panel under the top bar with the TV's everyday switches, after webOS's, drawn as
 * Customize is (TvGlass panel, 460 dp, 16 dp rows with an icon, a name and a line of state; app/DESIGN-TV.md). Tiles first, then the
 * power actions (power off, restart, screen off, standby: the top bar has no power button) and Clear RAM, whose line is CPU and RAM,
 * read every [TICK_MS] only while it is open. It is the top bar's settings button: only what people use often is here (the owner,
 * 2026-10-25: a 24-hour clock or the sound screen is set once in a TV's life), the rest stays in TV settings and Customize. TV Home does the
 * network line and the settings screens itself; the sleep timer, kids mode, the power buttons and the CPU go through TV Setup's HomeInfo (it answers only TV Home): named words, never a
 * command line from here. Without TV Setup, or an old one, those say "Needs TV Setup" greyed. No brightness slider: measured on a real box,
 * the system brightness does nothing on TVs.
 */
object ControlCenter {
    private const val TICK_MS = 2000L
    private const val ARMED = 0xFFE5484D.toInt()                       // an action waiting for its second press (power off, restart)
    private const val R_TILE = 16                                      // a row of the glass panel: Customize's and the long-press menu's
    private val SLEEP_STEPS = intArrayOf(30, 60, 90, 120)             // minutes: HomeControl.SLEEP on TV Setup's side

    /**
     * One tile, Customize's own: a glass row ([TvGlass.rowBg], 16 dp), the icon (26), the name (15 sp) and, when it has one, a line of state
     * (12 sp). [need]: it needs TV Setup, which is missing or too old. [armed] (isSelected) turns it red until pressed again or the focus leaves.
     */
    private class CcTile(c: Context, val dp: (Number) -> Int, icon: Int, val name: String) : LinearLayout(c) {
        private val pic = ImageView(c)
        private val title = Font.on(TextView(c))
        private val sub = Font.on(TextView(c))
        var need = false
        var base = ""                                                 // the state line when no message holds it
        var press: () -> Unit = {}

        init {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_selected), TvGlass.glass(c, R_TILE, ARMED, Color.WHITE))
                addState(intArrayOf(android.R.attr.state_focused), TvGlass.glass(c, R_TILE, TvGlass.ROW_FOCUS, Color.WHITE))
                addState(intArrayOf(), TvGlass.glass(c, R_TILE, TvGlass.ROW_FILL, TvGlass.ROW_EDGE))
            }
            addView(pic.apply { setImageResource(icon); imageTintList = ColorStateList.valueOf(Color.WHITE) }, LayoutParams(dp(26), dp(26)))
            val words = LinearLayout(c).apply { orientation = VERTICAL }
            words.addView(title.apply { text = name; textSize = 15f; setTextColor(Color.WHITE); maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            words.addView(sub.apply { textSize = 12f; setTextColor(TvGlass.HINT); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; visibility = GONE })
            addView(words, LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
            setOnClickListener { press() }
            onFocusChangeListener = OnFocusChangeListener { v, f -> Motion.pop(v, f) }
        }

        fun icon(res: Int) = pic.setImageResource(res)

        /** An armed tile asks in its own name line ("Press again"): a second line would make its row grow under the person's finger. */
        fun ask(text: String?) { title.text = text ?: name; isSelected = text != null }

        /** The state line; empty hides it (an action's tile is then one line). */
        fun say(text: String) { sub.text = text; sub.visibility = if (text.isEmpty()) GONE else VISIBLE }

        fun state(text: String, needSetup: Boolean = false) {
            need = needSetup; base = text; say(text)
            alpha = if (needSetup) 0.45f else 1f                    // greyed, still focusable: pressing it says why
        }
    }

    /** Opens the panel over [anchor] (the top bar's capsule), its right edge on the anchor's. [open] starts a settings action, [inputs] is the list of sources, [customize] TV Home's own settings. */
    fun show(a: Activity, anchor: View, open: (String) -> Unit, inputs: () -> Unit, customize: () -> Unit): Dialog = Panel(a, anchor, open, inputs, customize).dialog

    private class Panel(val a: Activity, val anchor: View, val open: (String) -> Unit, val inputs: () -> Unit, val customize: () -> Unit) {
        private val d = a.resources.displayMetrics.density
        private fun dp(v: Number) = (v.toFloat() * d).toInt()
        private val dm = a.resources.displayMetrics
        val dialog = Dialog(a, R.style.Menu)
        private val ui = Handler(Looper.getMainLooper())
        private val busy = AtomicBoolean()
        private var ctl: JSONObject? = null                           // TV Setup's {sleep_at, kids, pin}; null = no TV Setup, or one without it
        private val tiles = ArrayList<CcTile>()
        private var hold = 0L                                         // a message on the RAM tile (confirm, clearing) keeps its place until then
        private var footText = ""
        private var armed: CcTile? = null
        private lateinit var net: CcTile
        private lateinit var kids: CcTile; private lateinit var sleep: CcTile
        private lateinit var ram: CcTile

        private fun toast(id: Int) = Toast.makeText(a, id, Toast.LENGTH_SHORT).show()
        private fun hasSetup() = try { a.packageManager.getPackageInfo("dev.tvremote.setup", 0); true } catch (e: Exception) { false }
        private fun resolves(action: String) = Intent(action).resolveActivity(a.packageManager) != null
        private fun go(actions: List<String>) { actions.firstOrNull { resolves(it) }?.let { dialog.dismiss(); open(it) } }
        private fun say(text: String, ms: Long = 0) { ram.say(text); hold = if (ms > 0) System.currentTimeMillis() + ms else 0L }
        private fun disarm() { armed?.ask(null); armed = null; hold = 0L; ram.say(footText) }

        private fun tile(icon: Int, name: Int, press: () -> Unit) = CcTile(a, ::dp, icon, a.getString(name)).also { t -> t.press = { if (t.need) toast(R.string.cc_need_setup) else press() }; tiles.add(t) }

        /** An action TV Setup runs: greyed without it; [confirm] (its words) asks for a second press, shown red on the tile itself. */
        private fun action(icon: Int, name: Int, confirm: Int = 0, run: () -> Unit) = CcTile(a, ::dp, icon, a.getString(name)).also { t ->
            t.press = {
                if (!hasSetup()) toast(R.string.cc_need_setup)
                else if (confirm != 0 && armed !== t) { disarm(); armed = t; t.ask(a.getString(confirm)); ui.postDelayed({ if (armed === t) disarm() }, 4000) }
                else { disarm(); run() }
            }
            val ask = t.onFocusChangeListener
            t.onFocusChangeListener = View.OnFocusChangeListener { v, f -> ask.onFocusChange(v, f); if (!f && armed === t) disarm() }
            t.alpha = if (hasSetup()) 1f else 0.45f
            tiles.add(t)
        }

        /** TV Setup's HomeInfo [method]; null when it is missing, too old, or does not trust this app. */
        private fun ask(method: String, arg: String?): JSONObject? = try {
            a.contentResolver.call(Uri.parse(HomeConfig.USAGE), method, arg, null)?.getString("json")?.let { JSONObject(it) }
        } catch (e: Exception) { null }

        /** The sleep timer / kids mode word to TV Setup on a thread of its own, then the state again. */
        private fun control(word: String) {
            Thread({
                val out = ask("control", word)
                a.runOnUiThread { if (dialog.isShowing) { ctl = out; paintCtl(); if (out?.optString("error") == "pin") toast(R.string.cc_kids_nopin) } }
            }, "home-cc-control").apply { isDaemon = true }.start()
        }

        private fun paintNet(n: Pair<Int, String>) { net.icon(if (n.first == 2) R.drawable.ic_router else R.drawable.ic_wifi); net.state(when (n.first) {
            2 -> a.getString(R.string.cc_net_eth, n.second); 1 -> a.getString(R.string.cc_net_wifi, n.second); else -> a.getString(R.string.cc_net_none) }) }

        private fun paintCtl() {
            val c = ctl
            if (c == null) { kids.state(a.getString(R.string.cc_need_setup), true); sleep.state(a.getString(R.string.cc_need_setup), true); return }
            kids.state(a.getString(if (c.optBoolean("kids")) R.string.cc_on else if (c.optBoolean("pin")) R.string.cc_off else R.string.cc_kids_nopin))
            val left = (c.optLong("sleep_at") - System.currentTimeMillis()).let { if (it > 0) (it + 59_999) / 60_000 else 0L }
            sleep.state(if (left > 0) a.getString(R.string.cc_sleep_left, left.toString()) else a.getString(R.string.cc_off))
        }

        /** One read of everything that moves, on a thread of its own (TV Setup is a binder call): the network, RAM, CPU, the timer. */
        private fun refresh() {
            if (!busy.compareAndSet(false, true)) return
            Thread({
                try {
                    val n = network(a)
                    val mem = memoryUsage()
                    val cpu = ask("cpu", null)?.optDouble("busy", -1.0)?.takeIf { it >= 0 }
                    val c = ask("control", null)
                    a.runOnUiThread {
                        if (!dialog.isShowing) return@runOnUiThread
                        paintNet(n); ctl = c; paintCtl()
                        val mm = mem?.let { String.format(Locale.US, "%.1f/%.1f GB", it.first / 1e9, it.second / 1e9) }
                        footText = if (mm == null) "" else if (cpu != null) a.getString(R.string.cc_footer, "${Math.round(cpu * 100)}%", mm)
                            else if (hasSetup()) a.getString(R.string.cc_footer, "…", mm) else a.getString(R.string.cc_footer_ram, mm)
                        if (System.currentTimeMillis() >= hold) { hold = 0L; ram.say(footText) }
                    }
                } finally { busy.set(false) }
            }, "home-cc").apply { isDaemon = true }.start()
        }

        private val tick = object : Runnable { override fun run() { if (dialog.isShowing) { refresh(); ui.postDelayed(this, TICK_MS) } } }

        init {
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            val bold: (TextView) -> Unit = { it.typeface = Font.bold }
            val col = LinearLayout(a).apply {                         // the glass panel of Customize and the long-press menu: padding 10, then the page's 12 / 8
                orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10)); background = TvGlass.panelBg(a)
                clipChildren = false; clipToPadding = false
            }
            col.addView(LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(Font.on(TvUi.text(a, a.getString(R.string.cc_title), 20f, Color.WHITE, bold = true)).also(bold).apply { maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(Font.on(TvUi.text(a, a.getString(R.string.cc_android, Build.VERSION.RELEASE), 14f, TvGlass.HINT)).apply { maxLines = 1 })
            }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), dp(12), dp(16), dp(12)) })

            net = tile(R.drawable.ic_wifi, R.string.cc_net) { go(listOf(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_SETTINGS)) }
            tile(R.drawable.ic_input, R.string.inputs) { dialog.dismiss(); inputs() }.state("HDMI · AV")
            sleep = tile(R.drawable.ic_timer, R.string.cc_sleep) {
                val left = ((ctl?.optLong("sleep_at") ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0).let { (it + 59_999) / 60_000L }   // rounded up, as the tile says it: 29.9 is "30 left", the next step is 60
                control("sleep:" + (if (left <= 0) SLEEP_STEPS.first() else SLEEP_STEPS.firstOrNull { it > left } ?: 0))   // off -> 30 -> 60 -> 90 -> 120 -> off
            }
            kids = tile(R.drawable.ic_lock, R.string.cc_kids) {
                val c = ctl
                if (c != null) { if (c.optBoolean("kids")) toast(R.string.cc_kids_unlock) else if (!c.optBoolean("pin")) toast(R.string.cc_kids_nopin) else control("kids:on") }
            }
            tile(R.drawable.ic_settings, R.string.tv_settings) { go(listOf(Settings.ACTION_SETTINGS)) }.state(a.getString(R.string.cc_open_settings))
            tile(R.drawable.ic_sliders, R.string.cc_customize) { dialog.dismiss(); customize() }.state(a.getString(R.string.cc_customize_sub))
            paintCtl(); paintNet(network(a))
            val info = tiles.size

            // the power button's three actions (screen off, standby, power off) and the restart, all through TV Setup (Power.kt); the hard ones ask again
            action(R.drawable.ic_power, R.string.power_off, R.string.cc_confirm_off) { dialog.dismiss(); Power.run(a, Power.POWER_OFF) }
            action(R.drawable.ic_refresh, R.string.cc_restart, R.string.cc_confirm_restart) { dialog.dismiss(); Power.run(a, Power.REBOOT) }
            action(R.drawable.ic_eye_off, R.string.power_screen_off) { dialog.dismiss(); Power.run(a, Power.SCREEN_OFF) }
            action(R.drawable.ic_moon, R.string.power_standby) { dialog.dismiss(); Power.run(a, Power.STANDBY) }
            ram = action(R.drawable.ic_eraser, R.string.cc_clear_ram) {
                say(a.getString(R.string.cc_working), 60_000)
                Thread({
                    // the same press as the Free RAM tile: TV Setup closes what waits in the background and measures what came free
                    Tiles.press(a, HomeConfig.tileKey("dev.tvremote.setup", "ram")) { s ->
                        val line = s.optString("small")
                        a.runOnUiThread { if (dialog.isShowing && line.isNotEmpty()) say(line, if (s.optString("mode") == "working") 60_000 else 6000) }
                    }
                }, "home-cc-ram").apply { isDaemon = true }.start()
            }                                                         // its line is CPU and RAM, read every tick: the footer folded into the tile

            val grid = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(dp(12), dp(8), dp(12), dp(8)) }
            var rows = 0
            fun add(vararg t: CcTile) {
                val row = LinearLayout(a).apply { clipChildren = false }
                for ((i, v) in t.withIndex()) row.addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) leftMargin = dp(8) })
                if (t.size == 1 && t[0] !== ram) row.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = dp(8) })
                grid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (rows++ > 0) topMargin = dp(8) })
            }
            for (i in 0 until info step 2) add(*tiles.subList(i, minOf(i + 2, info)).toTypedArray())
            for (i in info until tiles.size - 1 step 2) add(*tiles.subList(i, minOf(i + 2, tiles.size - 1)).toTypedArray())
            add(ram)
            val at = IntArray(2).also { anchor.getLocationOnScreen(it) }
            val maxH = dm.heightPixels - at[1] - dp(12) - dp(70)      // the title and the panel's padding stay on the screen
            col.addView(object : ScrollView(a) {
                override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH.coerceAtLeast(dp(140)), MeasureSpec.AT_MOST))
            }.apply { isVerticalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(grid) }, LinearLayout.LayoutParams(-1, -2))

            dialog.setContentView(col)
            val sw = dm.widthPixels
            val w = minOf(dp(460), sw - dp(32))                       // Customize's panel width
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setGravity(Gravity.TOP or Gravity.END)
                setDimAmount(Drop.MENU_DIM)
                attributes = attributes.apply {
                    x = (sw - (at[0] + anchor.width)).coerceAtLeast(dp(16)); y = at[1]   // its right edge on the capsule's, its top on the bar's
                    width = w; height = WindowManager.LayoutParams.WRAP_CONTENT
                }
            }
            val bar = anchor.parent as? TopBar
            bar?.holdWord(true)                                       // the button's word would show through the dim
            dialog.setOnDismissListener { ui.removeCallbacksAndMessages(null); bar?.holdWord(false) }
            dialog.setOnShowListener { tiles.first().requestFocus(); refresh(); ui.postDelayed(tick, TICK_MS) }   // the first tile focused; the clock ticks only while open
            dialog.show()
        }
    }

    /** (0 none | 1 Wi-Fi | 2 Ethernet, the IPv4 address) of the network the TV is using, from the system's own list: no location, no network name. A cable wins, as the TV routes it. */
    internal fun network(a: Context): Pair<Int, String> {
        var kind = 0; var ip = ""
        try {
            val cm = a.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION") for (n in cm.allNetworks) {
                val c = cm.getNetworkCapabilities(n) ?: continue
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                val v4 = cm.getLinkProperties(n)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress } ?: continue
                val wired = c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                if (kind == 0 || wired) { ip = v4.hostAddress ?: ""; kind = if (wired) 2 else 1 }
            }
        } catch (e: Exception) { }
        return kind to ip
    }
}
