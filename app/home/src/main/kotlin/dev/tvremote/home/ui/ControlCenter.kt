package dev.tvremote.home.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.view.InputDevice
import dev.tvremote.home.apps.Inputs
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
import dev.tvremote.tvui.Motion
import dev.tvremote.tvui.TvGlass
import dev.tvremote.tvui.TvUi
import org.json.JSONObject
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Control Center (Trung tâm điều khiển): one glass panel under the top bar with the TV's everyday switches, after webOS's, drawn as
 * Customize is (TvGlass panel, 460 dp, 16 dp rows with an icon, a name and a line of state; app/DESIGN-TV.md). Tiles first, then the
 * power actions as one row of round buttons (power off, restart, screen off, standby, clear RAM: the top bar has no power button) and a
 * footer with CPU and RAM, read every [TICK_MS] only while it is open. It is the top bar's settings button: only what people use often is here (the owner,
 * 2026-10-25: a 24-hour clock or the sound screen is set once in a TV's life), the rest stays in TV settings and Customize. TV Home does the
 * network line and the settings screens itself; the sleep timer, kids mode, the power buttons and the CPU go through TV Setup's HomeInfo (it answers only TV Home): named words, never a
 * command line from here. Without TV Setup, or an old one, those say "Needs TV Setup" greyed. No brightness slider: measured on a real box,
 * the system brightness does nothing on TVs.
 */
object ControlCenter {
    private const val TICK_MS = 2000L
    private const val R_TILE = 16                                      // a row of the glass panel: Customize's and the long-press menu's
    private val SLEEP_STEPS = intArrayOf(30, 60, 90, 120)             // minutes: HomeControl.SLEEP on TV Setup's side

    /**
     * One tile, Customize's own: a glass row ([TvGlass.rowBg], 16 dp), the icon (26), the name (15 sp) and, when it has one, a line of state
     * (12 sp). [need]: it needs TV Setup, which is missing or too old.
     */
    private class CcTile(c: Context, val dp: (Number) -> Int, icon: Int, name: String) : LinearLayout(c) {
        private val pic = ImageView(c)
        private val title = Font.on(TextView(c))
        private val sub = Font.on(TextView(c))
        var need = false
        var press: () -> Unit = {}

        init {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = StateListDrawable().apply {
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

        /** The state line; empty hides it (an action's tile is then one line). */
        fun say(text: String) { sub.text = text; sub.visibility = if (text.isEmpty()) GONE else VISIBLE }

        fun state(text: String, needSetup: Boolean = false) {
            need = needSetup; say(text)
            alpha = if (needSetup) 0.45f else 1f                    // greyed, still focusable: pressing it says why
        }
    }

    /**
     * A round action (DESIGN-TV "Round action"): a 48 dp glass disc with the icon (22) and its short name under it (12 sp), three in one row.
     */
    private class Round(c: Context, val dp: (Number) -> Int, icon: Int, val name: String) : LinearLayout(c) {
        val disc = ImageView(c)
        private val word = Font.on(TextView(c))
        var press: () -> Unit = {}

        init {
            orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false
            disc.apply {
                isFocusable = true; isClickable = true; setImageResource(icon); imageTintList = ColorStateList.valueOf(Color.WHITE)
                setPadding(dp(13), dp(13), dp(13), dp(13)); contentDescription = name
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(TvGlass.ROW_FOCUS); setStroke(dp(1), Color.WHITE) })
                    addState(intArrayOf(), GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(TvGlass.ROW_FILL); setStroke(dp(1), TvGlass.ROW_EDGE) })
                }
                setOnClickListener { press() }
            }
            addView(disc, LayoutParams(dp(48), dp(48)))
            addView(word.apply { text = name; textSize = 12f; setTextColor(TvGlass.HINT); maxLines = 1; gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) }, LayoutParams(-1, -2))
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
        private var hold = 0L                                         // a message in the footer (clearing, what came free) keeps its place until then
        private var footText = ""
        private val footer = Font.on(TextView(a)).apply { textSize = 12f; setTextColor(TvGlass.HINT); maxLines = 1; gravity = Gravity.CENTER; ellipsize = TextUtils.TruncateAt.END }
        private lateinit var net: CcTile
        private var inputTile: CcTile? = null; private var btTile: CcTile? = null
        private lateinit var kids: CcTile; private lateinit var sleep: CcTile

        private fun toast(id: Int) = Toast.makeText(a, id, Toast.LENGTH_SHORT).show()
        private fun hasSetup() = try { a.packageManager.getPackageInfo("dev.tvremote.setup", 0); true } catch (e: Exception) { false }
        private fun resolves(action: String) = Intent(action).resolveActivity(a.packageManager) != null
        private val PAIR = "com.google.android.intent.action.CONNECT_INPUT"   // Android TV Settings' "Pair remote or accessory"
        private fun go(actions: List<String>) { actions.firstOrNull { resolves(it) }?.let { dialog.dismiss(); open(it) } }
        private fun say(text: String, ms: Long = 0) { footer.text = text; hold = if (ms > 0) System.currentTimeMillis() + ms else 0L }

        private fun tile(icon: Int, name: Int, press: () -> Unit) = CcTile(a, ::dp, icon, a.getString(name)).also { t -> t.press = { if (t.need) needSetup(t) else press() }; tiles.add(t) }

        /** An action TV Setup runs: greyed without it, and a tap then says how to get it. */
        private fun action(icon: Int, name: Int, run: () -> Unit) = Round(a, ::dp, icon, a.getString(name)).also { r ->
            r.press = { if (!hasSetup()) needSetup(r.disc) else run() }
            r.disc.setOnFocusChangeListener { v, f -> Motion.pop(v, f) }
            r.alpha = if (hasSetup()) 1f else 0.45f
        }

        /** "Turn off the TV": a small glass dialog with the two big choices (the person picks, nothing is armed); Back closes it, the focus starts on Screensaver. */
        private fun powerMenu() {
            val m = Dialog(a, R.style.Menu)
            m.requestWindowFeature(Window.FEATURE_NO_TITLE)
            val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10)); background = TvGlass.panelBg(a); clipChildren = false; clipToPadding = false }
            box.addView(Font.on(TvUi.text(a, a.getString(R.string.cc_power_title), 20f, Color.WHITE, bold = true)).apply { typeface = Font.bold; setPadding(dp(16), dp(12), dp(16), dp(12)) })
            val col2 = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(dp(12), dp(8), dp(12), dp(8)) }
            fun choice(icon: Int, name: Int, needs: Boolean, run: () -> Unit) = CcTile(a, ::dp, icon, a.getString(name)).apply {
                val grey = needs && !hasSetup(); alpha = if (grey) 0.45f else 1f
                press = { if (grey) needSetup(this) else { m.dismiss(); run() } }
            }
            val saver = choice(R.drawable.ic_clock, R.string.cc_screensaver, false) { dialog.dismiss(); a.startActivity(Intent(a, dev.tvremote.home.ScreensaverActivity::class.java)) }   // TV Home's own: no TV Setup
            val screenOff = choice(R.drawable.ic_eye_off, R.string.power_screen_off, true) { dialog.dismiss(); Power.run(a, Power.SCREEN_OFF) }
            val restart = choice(R.drawable.ic_refresh, R.string.cc_restart, true) { dialog.dismiss(); Power.run(a, Power.REBOOT) }
            val off = choice(R.drawable.ic_power, R.string.cc_power_off, true) { dialog.dismiss(); Power.run(a, Power.POWER_OFF) }
            for ((n, t) in listOf(saver, screenOff, restart, off).withIndex()) col2.addView(t, LinearLayout.LayoutParams(-1, -2).apply { if (n > 0) topMargin = dp(8) })
            box.addView(col2)
            m.setContentView(box)
            m.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); setGravity(Gravity.CENTER); setDimAmount(Drop.MENU_DIM)
                attributes = attributes.apply { width = minOf(dp(380), dm.widthPixels - dp(32)); height = WindowManager.LayoutParams.WRAP_CONTENT }
            }
            m.setOnShowListener { saver.requestFocus() }
            m.show()
        }

        /**
         * Without TV Setup (it runs the power buttons, the timer and the kids lock: TV Home has no ADB) these stay, greyed, and a tap says how to
         * get it, one menu for all: a control that vanished read as a bug. Downloader opens when the TV has it; the code is TV Setup's.
         */
        private fun needSetup(from: View) {
            val dl = a.packageManager.getLaunchIntentForPackage("com.esaba.downloader")
            Drop.show(a, from, listOf(
                Drop.Row(R.drawable.ic_info, a.getString(R.string.cc_setup_need)) { },
                Drop.Row(R.drawable.ic_download, a.getString(R.string.cc_setup_code, dev.tvremote.home.HomeActivity.SETUP_CODE)) { if (dl != null) try { a.startActivity(dl) } catch (e: Exception) { } },
                Drop.Row(R.drawable.ic_phone, a.getString(R.string.cc_setup_phone)) { }))
        }

        /** TV Setup's HomeInfo [method]; null when it is missing, too old, or does not trust this app. */
        private fun ask(method: String, arg: String?): JSONObject? = try {
            a.contentResolver.call(Uri.parse(HomeConfig.USAGE), method, arg, null)?.getString("json")?.let { JSONObject(it) }
        } catch (e: Exception) { null }

        /** The sleep timer / kids mode word to TV Setup on a thread of its own, then the state again. */
        private fun control(word: String) {
            Thread({
                val out = ask("control", word)
                a.runOnUiThread {
                    if (!dialog.isShowing) return@runOnUiThread
                    ctl = out; paintCtl()
                    when (out?.optString("error")) {
                        "pin" -> toast(R.string.cc_kids_nopin)
                        "bad", "failed" -> if (word == "kids:off") toast(R.string.cc_kids_unlock) else if (word == "kids:setup") toast(R.string.cc_kids_nopin)   // a TV Setup older than the word
                        else -> if (word == "kids:setup" && out != null) dialog.dismiss()                // the Manage screen is over us
                    }
                }
            }, "home-cc-control").apply { isDaemon = true }.start()
        }

        private fun paintNet(n: Pair<Int, String>) { net.icon(if (n.first == 2) R.drawable.ic_router else R.drawable.ic_wifi); net.state(when (n.first) {
            2 -> a.getString(R.string.cc_net_eth); 1 -> n.second.ifEmpty { a.getString(R.string.wifi) }; else -> a.getString(R.string.cc_net_none) }) }

        /** The Inputs tile's line, from the one reading the menu uses: what is connected, else how many there are. */
        private fun paintInputs(rows: List<Drop.Row>) {
            val on = rows.filter { it.on != null }
            inputTile?.state(if (rows.isEmpty()) a.getString(R.string.cc_open_settings) else if (on.isNotEmpty()) on.first().label + (if (on.size > 1) " +" + (on.size - 1) else "")
                else a.resources.getQuantityString(R.plurals.cc_inputs_count, rows.size, rows.size))
        }

        /** Bluetooth with no permission: the adapter, then a connected audio device or game pad by name (the remote never counts). */
        private fun btState(): String {
            val on = try { (a.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true } catch (e: Exception) { false }
            if (!on) return a.getString(R.string.cc_off)
            val audio = try {
                (a.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || (Build.VERSION.SDK_INT >= 31 && (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
                }?.productName?.toString()?.takeIf { it.isNotBlank() }
            } catch (e: Exception) { null }
            val pad = audio ?: try {
                InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }.firstOrNull { d ->
                    !d.isVirtual && (d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD || d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) && !d.name.contains("remote", true) && !d.name.contains("virtual", true)
                }?.name
            } catch (e: Exception) { null }
            return pad ?: a.getString(R.string.cc_bt_on)
        }
        private fun paintBt() { btTile?.state(btState()) }

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
                    val ins = if (inputTile != null) Inputs.rows(a) { } else emptyList()
                    val mem = memoryUsage()
                    val cpu = ask("cpu", null)?.optDouble("busy", -1.0)?.takeIf { it >= 0 }
                    val c = ask("control", null)
                    a.runOnUiThread {
                        if (!dialog.isShowing) return@runOnUiThread
                        paintNet(n); ctl = c; paintCtl(); paintBt(); paintInputs(ins)
                        val mm = mem?.let { String.format(Locale.US, "%.1f/%.1f GB", it.first / 1e9, it.second / 1e9) }
                        footText = if (mm == null) "" else if (cpu != null) a.getString(R.string.cc_footer, "${Math.round(cpu * 100)}%", mm)
                            else if (hasSetup()) a.getString(R.string.cc_footer, "…", mm) else a.getString(R.string.cc_footer_ram, mm)
                        if (System.currentTimeMillis() >= hold) { hold = 0L; footer.text = footText }
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
            // Inputs only where the TV lists some (or may keep them outside the TV Input Framework); a box with none has Bluetooth in that slot
            val ins = Inputs.rows(a) { }
            if (Inputs.showTile(ins.size, Inputs.liveApp(a) != null, Inputs.isBox())) { inputTile = tile(R.drawable.ic_input, R.string.inputs) { dialog.dismiss(); inputs() }; paintInputs(ins) }
            else if (a.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) { btTile = tile(R.drawable.ic_bluetooth, R.string.cc_bluetooth) { go(listOf(Settings.ACTION_BLUETOOTH_SETTINGS, PAIR, Settings.ACTION_SETTINGS)) }; paintBt() }   // FPT Play (Android 14) has no BLUETOOTH_SETTINGS: its Settings answers only the pairing screen
            sleep = tile(R.drawable.ic_timer, R.string.cc_sleep) {
                val left = ((ctl?.optLong("sleep_at") ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0).let { (it + 59_999) / 60_000L }   // rounded up, as the tile says it: 29.9 is "30 left", the next step is 60
                control("sleep:" + (if (left <= 0) SLEEP_STEPS.first() else SLEEP_STEPS.firstOrNull { it > left } ?: 0))   // off -> 30 -> 60 -> 90 -> 120 -> off
            }
            kids = tile(R.drawable.ic_lock, R.string.cc_kids) {
                val c = ctl
                if (c != null) control(if (c.optBoolean("kids")) "kids:off" else if (!c.optBoolean("pin")) "kids:setup" else "kids:on")   // off = TV Setup's PIN screen, never from here
            }
            tile(R.drawable.ic_settings, R.string.tv_settings) { go(listOf(Settings.ACTION_SETTINGS)) }.state(a.getString(R.string.cc_open_settings))
            tile(R.drawable.ic_sliders, R.string.cc_customize) { dialog.dismiss(); customize() }.state(a.getString(R.string.cc_customize_sub))
            paintCtl(); paintNet(network(a))
            val info = tiles.size

            // what the old power button did, through TV Setup (Power.kt): screen off, clear RAM, and "Turn off the TV" (power off / restart, asked in a dialog)
            val acts = ArrayList<Round>()
            acts += action(R.drawable.ic_eraser, R.string.cc_clear_ram) {
                say(a.getString(R.string.cc_working), 60_000)
                Thread({
                    // the same press as the Free RAM tile: TV Setup closes what waits in the background and measures what came free
                    Tiles.press(a, HomeConfig.tileKey("dev.tvremote.setup", "ram")) { s ->
                        val line = s.optString("small")
                        a.runOnUiThread { if (dialog.isShowing && line.isNotEmpty()) say(line, if (s.optString("mode") == "working") 60_000 else 6000) }
                    }
                }, "home-cc-ram").apply { isDaemon = true }.start()
            }
            acts += Round(a, ::dp, R.drawable.ic_power, a.getString(R.string.cc_power_tv)).also { r -> r.press = { powerMenu() }; r.disc.setOnFocusChangeListener { v, f -> Motion.pop(v, f) } }   // the menu has Screensaver, which needs no TV Setup

            val grid = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false; setPadding(dp(12), dp(8), dp(12), dp(8)) }
            for (i in 0 until info step 2) {
                val row = LinearLayout(a).apply { clipChildren = false }
                row.addView(tiles[i], LinearLayout.LayoutParams(0, -2, 1f))
                if (i + 1 < info) row.addView(tiles[i + 1], LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
                else row.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = dp(8) })
                grid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(8) })
            }
            val at = IntArray(2).also { anchor.getLocationOnScreen(it) }
            val maxH = dm.heightPixels - at[1] - dp(12) - dp(170)     // the title, the round row and the footer stay on the screen
            col.addView(object : ScrollView(a) {
                override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH.coerceAtLeast(dp(140)), MeasureSpec.AT_MOST))
            }.apply { isVerticalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(grid) }, LinearLayout.LayoutParams(-1, -2))

            val actions = LinearLayout(a).apply { clipChildren = false; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(12), dp(4), dp(12), 0) }
            for (r in acts) actions.addView(r, LinearLayout.LayoutParams(dp(120), -2))
            col.addView(actions, LinearLayout.LayoutParams(-1, -2))
            col.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), dp(12), dp(16), dp(8)) })

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

    /** (0 none | 1 Wi-Fi | 2 Ethernet, the Wi-Fi's name or "" when unknown) of the network the TV is using. A cable wins, as the TV routes it. */
    internal fun network(a: Context): Pair<Int, String> {
        var kind = 0
        try {
            val cm = a.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION") for (n in cm.allNetworks) {
                val c = cm.getNetworkCapabilities(n) ?: continue
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                if (cm.getLinkProperties(n)?.linkAddresses?.any { it.address is Inet4Address && !it.address.isLoopbackAddress } != true) continue
                val wired = c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                if (kind == 0 || wired) kind = if (wired) 2 else 1
            }
        } catch (e: Exception) { }
        val name = if (kind != 1) "" else try {
            @Suppress("DEPRECATION") (a.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo?.ssid
                ?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" } ?: ""
        } catch (e: Exception) { "" }
        return kind to name
    }
}
