package dev.tvremote.home.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
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
import org.json.JSONObject
import java.net.Inet4Address
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Control Center (Trung tâm điều khiển): one glass panel under the top bar with the TV's everyday switches, after webOS's. Large tiles
 * (icon, name, one line of state), a row of round buttons (power off, restart, screen off, clear RAM) and a footer with CPU and RAM,
 * read every [TICK_MS] only while it is open. What TV Home does itself (the clock, motion, the network line, the settings screens) is done
 * here; the sleep timer, kids mode, the power buttons and the CPU go through TV Setup's HomeInfo (it answers only TV Home): named words, never a
 * command line from here. Without TV Setup, or an old one, those say "Needs TV Setup" greyed. No brightness slider: measured on a real box,
 * the system brightness does nothing on TVs.
 */
object ControlCenter {
    private const val TICK_MS = 2000L
    private const val DIM = 0xB3FFFFFF.toInt()
    private const val INK = 0xFF111318.toInt()
    private const val INK_DIM = 0xFF5B5F6B.toInt()
    private const val TILE_FILL = 0x1AFFFFFF
    private const val TILE_EDGE = 0x1FFFFFFF
    private const val ARMED = 0xFFE5484D.toInt()
    private const val ROUND_FILL = 0x24FFFFFF
    private const val CC_TOP = 0xFF343842.toInt()
    private const val CC_BOTTOM = 0xFF181A20.toInt()
    private const val CC_EDGE = 0x40FFFFFF
    private val SLEEP_STEPS = intArrayOf(30, 60, 90, 120)             // minutes: HomeControl.SLEEP on TV Setup's side
    private val PICTURE = listOf(Settings.ACTION_DISPLAY_SETTINGS)   // vendors' own picture screens: none found on the stock images, add them here as they turn up (an unresolved one is skipped)
    private val SOUND = listOf(Settings.ACTION_SOUND_SETTINGS)

    private val tint = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(INK, Color.WHITE))
    private val subTint = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(INK_DIM, DIM))

    /** One tile of the grid: a disc with the icon, the name, one line of state. [need]: it needs TV Setup, which is missing or too old. */
    private class CcTile(c: Context, val dp: (Number) -> Int, icon: Int, name: String) : LinearLayout(c) {
        private val pic = ImageView(c)
        private val sub = Font.on(TextView(c))
        var need = false
        var press: () -> Unit = {}

        init {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
            minimumHeight = dp(62); setPadding(dp(14), dp(8), dp(12), dp(8))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.WHITE) })
                addState(intArrayOf(), GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(TILE_FILL); setStroke(dp(1), TILE_EDGE) })
            }
            addView(pic.apply { setImageResource(icon); imageTintList = tint; isDuplicateParentStateEnabled = true }, LayoutParams(dp(26), dp(26)))
            val words = LinearLayout(c).apply { orientation = VERTICAL; isDuplicateParentStateEnabled = true }
            words.addView(Font.on(TextView(c)).apply {
                text = name; textSize = 16f; setTextColor(tint); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; isDuplicateParentStateEnabled = true
                typeface = Font.medium
            })
            words.addView(sub.apply { textSize = 13f; setTextColor(subTint); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; isDuplicateParentStateEnabled = true })
            addView(words, LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
            setOnClickListener { press() }
        }

        fun icon(res: Int) = pic.setImageResource(res)

        fun state(text: String, needSetup: Boolean = false) {
            need = needSetup
            sub.text = text
            alpha = if (needSetup) 0.45f else 1f                    // greyed, still focusable: pressing it says why
        }
    }

    /** A round action: the disc, its name under it. [armed] shows red until pressed again or the focus leaves. */
    private class Round(c: Context, dp: (Number) -> Int, icon: Int, name: String, val id: String) : LinearLayout(c) {
        val disc = ImageView(c)
        var press: () -> Unit = {}
        init {
            orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            disc.apply {
                isFocusable = true; isClickable = true; setImageResource(icon); setPadding(dp(14), dp(14), dp(14), dp(14))
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_selected), GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ARMED) })
                    addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) })
                    addState(intArrayOf(), GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ROUND_FILL) })
                }
                imageTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(Color.WHITE, INK, Color.WHITE))
                contentDescription = name
                setOnClickListener { press() }
            }
            addView(disc, LayoutParams(dp(52), dp(52)))
            addView(Font.on(TextView(c)).apply { text = name; textSize = 12f; setTextColor(DIM); maxLines = 1; gravity = Gravity.CENTER; setPadding(0, dp(5), 0, 0) },
                LayoutParams(-2, -2))
        }
    }

    /** Opens the panel over [anchor] (the top bar's capsule), its right edge on the anchor's. [open] starts a settings action, [inputs] is the Inputs pill's list. */
    fun show(a: Activity, anchor: View, open: (String) -> Unit, inputs: () -> Unit): Dialog = Panel(a, anchor, open, inputs).dialog

    private class Panel(val a: Activity, val anchor: View, val open: (String) -> Unit, val inputs: () -> Unit) {
        private val d = a.resources.displayMetrics.density
        private fun dp(v: Number) = (v.toFloat() * d).toInt()
        private val dm = a.resources.displayMetrics
        val dialog = Dialog(a, R.style.Menu)
        private val ui = Handler(Looper.getMainLooper())
        private val busy = AtomicBoolean()
        private var ctl: JSONObject? = null                           // TV Setup's {sleep_at, kids, pin}; null = no TV Setup, or one without it
        private val tiles = ArrayList<CcTile>()
        private val rounds = ArrayList<Round>()
        private val footer = Font.on(TextView(a)).apply { textSize = 14f; setTextColor(DIM); maxLines = 1; gravity = Gravity.CENTER; ellipsize = TextUtils.TruncateAt.END }
        private var hold = 0L                                         // a message in the footer (confirm, clearing) keeps its place until then
        private var footText = ""
        private var armed: Round? = null
        private lateinit var net: CcTile; private lateinit var clock: CcTile; private lateinit var motion: CcTile
        private lateinit var kids: CcTile; private lateinit var sleep: CcTile

        private fun toast(id: Int) = Toast.makeText(a, id, Toast.LENGTH_SHORT).show()
        private fun hasSetup() = try { a.packageManager.getPackageInfo("dev.tvremote.setup", 0); true } catch (e: Exception) { false }
        private fun resolves(action: String) = Intent(action).resolveActivity(a.packageManager) != null
        private fun go(actions: List<String>) { actions.firstOrNull { resolves(it) }?.let { dialog.dismiss(); open(it) } }
        private fun say(text: String, ms: Long = 0) { footer.text = text; hold = if (ms > 0) System.currentTimeMillis() + ms else 0L }
        private fun disarm() { armed?.disc?.isSelected = false; armed = null; hold = 0L; footer.text = footText }

        private fun tile(icon: Int, name: Int, press: () -> Unit) = CcTile(a, ::dp, icon, a.getString(name)).also { t -> t.press = { if (t.need) toast(R.string.cc_need_setup) else press() }; tiles.add(t) }

        private fun round(icon: Int, name: Int, id: String, confirm: Int = 0, run: () -> Unit) = Round(a, ::dp, icon, a.getString(name), id).also { r ->
            r.press = {
                if (!hasSetup()) toast(R.string.cc_need_setup)
                else if (confirm != 0 && armed !== r) { disarm(); armed = r; r.disc.isSelected = true; say(a.getString(confirm), 4000); ui.postDelayed({ if (armed === r) disarm() }, 4000) }
                else { disarm(); run() }
            }
            r.disc.setOnFocusChangeListener { _, f -> if (!f && armed === r) disarm() }
            r.disc.alpha = if (hasSetup()) 1f else 0.45f
            rounds.add(r)
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

        private fun paintOwn() {
            val c = Store.get(a)
            clock.state(a.getString(if (c.optBoolean("clock24", true)) R.string.cc_clock_24 else R.string.cc_clock_12))
            motion.state(a.getString(if (c.optBoolean("anim", true)) R.string.cc_on else R.string.cc_off))
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
                        paintNet(n); ctl = c; paintCtl(); paintOwn()
                        val ram = mem?.let { String.format(Locale.US, "%.1f/%.1f GB", it.first / 1e9, it.second / 1e9) }
                        footText = if (ram == null) "" else if (cpu != null) a.getString(R.string.cc_footer, "${Math.round(cpu * 100)}%", ram)
                            else if (hasSetup()) a.getString(R.string.cc_footer, "…", ram) else a.getString(R.string.cc_footer_ram, ram)
                        if (System.currentTimeMillis() >= hold) { hold = 0L; footer.text = footText }
                    }
                } finally { busy.set(false) }
            }, "home-cc").apply { isDaemon = true }.start()
        }

        private val tick = object : Runnable { override fun run() { if (dialog.isShowing) { refresh(); ui.postDelayed(this, TICK_MS) } } }

        init {
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            val col = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(14))
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(CC_TOP, CC_BOTTOM)).apply {
                    cornerRadius = dp(24).toFloat(); setStroke(dp(1), CC_EDGE)
                }
            }
            col.addView(LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), 0, dp(4), dp(12))
                addView(Font.on(TextView(a)).apply { text = a.getString(R.string.cc_title); textSize = 22f; typeface = Font.bold; setTextColor(Color.WHITE); maxLines = 1 }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(Font.on(TextView(a)).apply { text = a.getString(R.string.cc_android, Build.VERSION.RELEASE); textSize = 14f; setTextColor(DIM); maxLines = 1 })
            })

            net = tile(R.drawable.ic_wifi, R.string.cc_net) { go(listOf(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_SETTINGS)) }
            tile(R.drawable.ic_settings, R.string.tv_settings) { go(listOf(Settings.ACTION_SETTINGS)) }.state(a.getString(R.string.cc_open_settings))
            if (PICTURE.any(::resolves)) tile(R.drawable.ic_monitor, R.string.picture) { go(PICTURE) }.state(a.getString(R.string.cc_open_settings))
            if (SOUND.any(::resolves)) tile(R.drawable.ic_vol_up, R.string.sound) { go(SOUND) }.state(a.getString(R.string.cc_open_settings))
            clock = tile(R.drawable.ic_clock, R.string.cc_clock) { Store.change(a, JSONObject().put("clock24", !Store.get(a).optBoolean("clock24", true))); paintOwn() }
            motion = tile(R.drawable.ic_sparkle, R.string.cc_motion) { Store.change(a, JSONObject().put("anim", !Store.get(a).optBoolean("anim", true))); paintOwn() }
            tile(R.drawable.ic_input, R.string.inputs) { dialog.dismiss(); inputs() }.state("HDMI · AV")
            kids = tile(R.drawable.ic_lock, R.string.cc_kids) {
                val c = ctl
                if (c != null) { if (c.optBoolean("kids")) toast(R.string.cc_kids_unlock) else if (!c.optBoolean("pin")) toast(R.string.cc_kids_nopin) else control("kids:on") }
            }
            sleep = tile(R.drawable.ic_timer, R.string.cc_sleep) {
                val left = ((ctl?.optLong("sleep_at") ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0).let { (it + 59_999) / 60_000L }   // rounded up, as the tile says it: 29.9 is "30 left", the next step is 60
                control("sleep:" + (if (left <= 0) SLEEP_STEPS.first() else SLEEP_STEPS.firstOrNull { it > left } ?: 0))   // off -> 30 -> 60 -> 90 -> 120 -> off
            }
            if (resolves(Settings.ACTION_BLUETOOTH_SETTINGS)) tile(R.drawable.ic_bluetooth, R.string.cc_bluetooth) { go(listOf(Settings.ACTION_BLUETOOTH_SETTINGS)) }.state(a.getString(R.string.cc_open_settings))
            paintOwn(); paintCtl(); paintNet(network(a))

            val grid = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
            for (i in tiles.indices step 2) {
                val row = LinearLayout(a).apply { clipChildren = false }
                row.addView(tiles[i], LinearLayout.LayoutParams(0, -2, 1f))
                if (i + 1 < tiles.size) row.addView(tiles[i + 1], LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
                else row.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = dp(8) })
                grid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(8) })
            }
            val at = IntArray(2).also { anchor.getLocationOnScreen(it) }
            val maxH = dm.heightPixels - at[1] - dp(12) - dp(200)   // the title, the round buttons and the footer around it stay on the screen
            col.addView(object : ScrollView(a) {
                override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH.coerceAtLeast(dp(140)), MeasureSpec.AT_MOST))
            }.apply { isVerticalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(grid) }, LinearLayout.LayoutParams(-1, -2))

            round(R.drawable.ic_power, R.string.power_off, "off", R.string.cc_confirm_off) { dialog.dismiss(); Power.run(a, Power.POWER_OFF) }
            round(R.drawable.ic_refresh, R.string.cc_restart, "restart", R.string.cc_confirm_restart) { dialog.dismiss(); Power.run(a, Power.REBOOT) }
            round(R.drawable.ic_eye_off, R.string.power_screen_off, "screen") { dialog.dismiss(); Power.run(a, Power.SCREEN_OFF) }
            round(R.drawable.ic_eraser, R.string.cc_clear_ram, "ram") {
                say(a.getString(R.string.cc_working), 60_000)
                Thread({
                    // the same press as the Free RAM tile: TV Setup closes what waits in the background and measures what came free
                    Tiles.press(a, HomeConfig.tileKey("dev.tvremote.setup", "ram")) { s ->
                        val line = s.optString("small")
                        a.runOnUiThread { if (dialog.isShowing && line.isNotEmpty()) say(line, if (s.optString("mode") == "working") 60_000 else 6000) }
                    }
                }, "home-cc-ram").apply { isDaemon = true }.start()
            }
            val actions = LinearLayout(a).apply { gravity = Gravity.CENTER; clipChildren = false }
            for (r in rounds) actions.addView(r, LinearLayout.LayoutParams(0, -2, 1f))
            col.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
            col.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

            dialog.setContentView(col)
            val sw = dm.widthPixels
            val w = minOf(dp(600), sw - dp(32))
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setGravity(Gravity.TOP or Gravity.END)
                setDimAmount(Drop.MENU_DIM)
                attributes = attributes.apply {
                    x = (sw - (at[0] + anchor.width)).coerceAtLeast(dp(16)); y = at[1]   // its right edge on the capsule's, its top on the bar's: it covers the bar, which would not leave room for four rows under it
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
