package dev.tvremote.setup

import android.app.Activity
import dev.tvremote.core.T
import dev.tvremote.tvui.TvUi
import dev.tvremote.core.Words
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import dev.tvremote.core.adb.Adb
import dev.tvremote.core.ontv.Setup
import dev.tvremote.core.ontv.SetupUpdate
import dev.tvremote.core.ontv.Tailscale
import dev.tvremote.update.Release
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One screen, driven with the TV remote. Left: where things stand (ADB, the TV's addresses) and six actions as tiles.
 * Right: how a phone or computer takes over (the QR code of the web remote this TV serves, the phone app). Below: what happened.
 * Words follow the TV's language (Vietnamese, otherwise English).
 */
class MainActivity : Activity() {
    companion object {
        @Volatile var shown = false                                   // on screen: no update in the background meanwhile (AutoUpdate)
    }

    private lateinit var dot: View
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var spinner: ProgressBar
    private lateinit var tiles: List<Tile>
    private lateinit var connTile: Tile
    private var tsStatus = ""                                         // Tailscale's line in the Connection menu

    /** The remote's buttons the stock launcher answered (KeyFix): on, and brought back now, only while one is dead. */
    private fun keysSub() = if (!KeyFix.wanted(this)) T("keyfix.off") else if (!KeyFix.can(this)) T("keyfix.need_adb")
        else if (KeyFix.current(this).isNotEmpty()) T("keyfix.working") else T("keyfix.ok")
    private lateinit var updateTile: Tile
    private lateinit var webCol: LinearLayout
    private lateinit var webBtn: Button
    private lateinit var rulesCard: LinearLayout                     // timers and limits set from a phone or computer (Rules.kt)
    private var lanIp = ""
    private var tsIp = ""
    private var adb: Adb? = null
    private var setup: Setup? = null
    private var busy = false
    private var focusedOnce = false

    private val TILE = Color.rgb(34, 39, 47)
    private val MUTED = Color.rgb(140, 150, 163)
    private val ACCENT = Color.rgb(59, 115, 246)
    private val OK = Color.rgb(70, 210, 131)
    private val WARN = Color.rgb(240, 179, 74)
    private val BAD = Color.rgb(255, 112, 112)
    private val HOWTO = Color.rgb(138, 180, 248)                       // what to do next: a light blue, apart from white info and the three states

    /** An action: an icon, a title, and one line beside it saying what it does or where it stands. */
    private inner class Tile(icon: Int, title: String, sub: String, run: () -> Unit) {
        val view = LinearLayout(this@MainActivity)
        private val subView: TextView
        private val badge = ImageView(this@MainActivity)
        private val wheel = ProgressBar(this@MainActivity)              // in place of the icon while this tile's work runs
        init {
            view.gravity = Gravity.CENTER_VERTICAL
            view.setPadding(dp(10), dp(4), dp(10), dp(4))
            view.isFocusable = true
            view.isClickable = true
            view.background = box(TILE, 14, 0)
            badge.setImageResource(icon)
            badge.setColorFilter(TvUi.TEXT)
            badge.background = box(TvUi.CARD, 10, 0)
            badge.setPadding(dp(5), dp(5), dp(5), dp(5))
            val slot = android.widget.FrameLayout(this@MainActivity)
            slot.addView(badge, android.widget.FrameLayout.LayoutParams(dp(26), dp(26)))
            wheel.isIndeterminate = true
            wheel.indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            wheel.visibility = View.GONE
            slot.addView(wheel, android.widget.FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
            view.addView(slot, LinearLayout.LayoutParams(dp(26), dp(26)))
            val words = LinearLayout(this@MainActivity)
            words.orientation = LinearLayout.VERTICAL
            words.setPadding(dp(10), 0, 0, 0)
            val head = text(title, 13, TvUi.TEXT, true)
            head.maxLines = 2                                         // four tiles across: a long name wraps, not "Manage on th…"
            head.ellipsize = TextUtils.TruncateAt.END
            words.addView(head)
            subView = text(sub, 11, MUTED, false)
            subView.maxLines = 2
            subView.ellipsize = TextUtils.TruncateAt.END
            words.addView(subView)
            view.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
            view.setOnClickListener { if (!busy) run() else log(T("tv_main.busy")) }
            view.setOnFocusChangeListener { v, f ->
                v.background = if (f) TvUi.ring(this@MainActivity, TILE, dp(14).toFloat()) else box(TILE, 14, 0)
                badge.background = box(if (f) Color.argb(60, 255, 255, 255) else TvUi.CARD, 10, 0)
                subView.setTextColor(if (f) Color.WHITE else MUTED)
                v.animate().scaleX(if (f) 1.06f else 1f).scaleY(if (f) 1.06f else 1f).setDuration(120).start()
            }
        }
        private var idle = sub
        private var working = false
        fun say(s: String) { idle = s; if (!working) subView.text = s }
        fun progress(s: String) { if (working) subView.text = s }
        fun work(on: Boolean) {
            working = on
            subView.text = if (on) T("setup.ts_working") else idle
            wheel.visibility = if (on) View.VISIBLE else View.GONE
            badge.imageAlpha = if (on) 0 else 255
        }
        fun enable(on: Boolean) { view.isEnabled = on; view.alpha = if (on) 1f else 0.4f }
    }

    /**
     * A phone (TV Setup runs there too, see README): one scrolling column instead of the TV's two, whichever way it is held.
     * Not the screen size alone: a 1080p TV is only 540dp on its short side.
     */
    private val phone by lazy {
        !packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) && resources.configuration.smallestScreenWidthDp < 600
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        dev.tvremote.core.adb.Adb.holdFirstAsk(10000)                       // a phone turning ADB on asks first, then allows us too (AdbPrompt.helpSetup)
        val page = LinearLayout(this)
        page.setBackgroundColor(TvUi.BG)
        if (phone) {
            page.orientation = LinearLayout.VERTICAL
            page.setPadding(dp(16), dp(20), dp(16), dp(24))
        } else page.setPadding(resources.getDimension(R.dimen.tv_margin_x).toInt(), resources.getDimension(R.dimen.tv_margin_y).toInt(),
            resources.getDimension(R.dimen.tv_margin_x).toInt(), resources.getDimension(R.dimen.tv_margin_y).toInt())   // app/DESIGN-TV.md

        // ---- left: header, status, actions, activity
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.clipChildren = false                                     // a focused tile grows 3%: drawn into the page's margin, not cut
        page.clipChildren = false; page.clipToPadding = false

        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.icon)
        logo.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, resources.getDimension(R.dimen.tv_head_radius)) }
        }
        logo.clipToOutline = true
        val ic = if (phone) dp(38) else resources.getDimension(R.dimen.tv_head_icon).toInt()
        head.addView(logo, LinearLayout.LayoutParams(ic, ic))
        val names = LinearLayout(this)
        names.orientation = LinearLayout.VERTICAL
        names.setPadding(resources.getDimension(R.dimen.tv_head_gap).toInt(), 0, 0, 0)
        names.addView(text("TV Setup", if (phone) 20 else 24, TvUi.TEXT, true))
        names.addView(text(T("tv_main.version_n", "v" to version()) + released().let { if (it.isEmpty()) "" else " · $it" } +
            "   ·   Hoang Tran   ·   github.com/HoangTran0410/tv-remote", 12, MUTED, false))
        head.addView(names)
        left.addView(head)

        val status = LinearLayout(this)
        status.gravity = Gravity.CENTER_VERTICAL
        status.background = box(TvUi.CARD, 16, 0)
        status.setPadding(dp(16), dp(10), dp(16), dp(10))
        dot = View(this)
        status.addView(dot, LinearLayout.LayoutParams(dp(10), dp(10)))
        val st = LinearLayout(this)
        st.orientation = LinearLayout.VERTICAL
        st.setPadding(dp(14), 0, 0, 0)
        statusTitle = text("", 15, TvUi.TEXT, true)
        statusDetail = text("", 12, MUTED, false)
        st.addView(statusTitle)
        st.addView(statusDetail)
        status.addView(st, LinearLayout.LayoutParams(0, -2, 1f))
        rulesCard = LinearLayout(this)                               // on the right of the status: the page keeps its height, the log its room
        rulesCard.orientation = LinearLayout.VERTICAL
        rulesCard.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        status.addView(rulesCard, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(16) })
        left.addView(status, margins(top = 12))

        left.addView(section(T("tv_main.actions")), margins(top = 14))
        tsStatus = T("tv_main.away_from_home")
        updateTile = Tile(R.drawable.ic_download, T("setup.update_ok"), T("tv_main.ver_short", "v" to version())) { updateMenu() }
        connTile = Tile(R.drawable.ic_refresh, T("tv_main.connection"), T("tv_main.connection_sub")) { connectionMenu() }
        // four tiles, not nine: the rest sit in short menus. The code below counts these by place: 0 needs ADB, 1 and 2 work without
        tiles = listOf(
            Tile(R.drawable.ic_zap, T("tv_main.optimize_tv"), T("tv_main.group_optimize_sub")) { optimizeMenu() },
            connTile,
            updateTile)
        // the web UI right here, for when no phone or computer is at hand (ManageActivity): first, as it does most
        val manage = Tile(R.drawable.ic_apps, T("tv_main.manage_on_this_tv"), T("tv_main.apps_files_health_automation")) {
            RemoteService.start(this)                                 // serves 127.0.0.1 even with the web remote off
            startActivity(android.content.Intent(this, ManageActivity::class.java).putExtra(ManageActivity.PASS, RemoteService.selfPass))
        }
        val grid = listOf(manage) + tiles                              // Manage first, in the grid: no ragged last row
        val cols = if (phone) 2 else 4                                 // TV: one row of four; phone: 2 x 2
        for (row in grid.chunked(cols)) {
            val r = LinearLayout(this)
            r.clipChildren = false
            row.forEachIndexed { i, tile -> r.addView(tile.view, LinearLayout.LayoutParams(0, dp(76), 1f).apply { if (i > 0) leftMargin = dp(10) }) }
            left.addView(r, margins(top = 8))
        }

        val actHead = LinearLayout(this)
        actHead.gravity = Gravity.CENTER_VERTICAL
        actHead.addView(section(T("tv_main.activity")))
        spinner = ProgressBar(this)
        spinner.isIndeterminate = true
        spinner.visibility = View.INVISIBLE
        actHead.addView(spinner, LinearLayout.LayoutParams(dp(16), dp(16)).apply { leftMargin = dp(10) })
        left.addView(actHead, margins(top = 18))
        logView = text(T("tv_main.activity_empty"), 14, MUTED, false)   // until the first line
        logView.setLineSpacing(dp(3).toFloat(), 1f)
        scroll = ScrollView(this)
        scroll.background = box(TvUi.CARD, 14, 0)
        scroll.setPadding(dp(16), dp(10), dp(16), dp(10))
        scroll.isFocusable = true                                     // D-pad can scroll back to an error that went by
        scroll.onFocusChangeListener = View.OnFocusChangeListener { v, on -> v.background = if (on) TvUi.ring(this@MainActivity, TvUi.CARD, dp(14).toFloat()) else box(TvUi.CARD, 14, 0) }
        scroll.addView(logView)
        // ---- right: taking over from a phone or a computer
        webCol = LinearLayout(this)
        webCol.orientation = LinearLayout.VERTICAL
        webCol.background = box(TvUi.CARD, 18, 0)
        webCol.setPadding(dp(18), dp(14), dp(18), dp(14))
        webBtn = smallButton("") { RemoteService.setEnabled(this, !RemoteService.enabled(this)); showWeb(); webBtn.requestFocus() }
        if (phone) {                                                  // header, status, then the card (what a phone is mostly here for), the tiles, the log
            left.addView(webCol, 2, margins(top = 16))
            scroll.isNestedScrollingEnabled = true
            left.addView(scroll, LinearLayout.LayoutParams(-1, dp(240)).apply { topMargin = dp(8) })
            page.addView(left, LinearLayout.LayoutParams(-1, -2))
            val outer = ScrollView(this)
            outer.setBackgroundColor(TvUi.BG)
            outer.isFillViewport = true
            outer.addView(page)
            setContentView(outer)
        } else {
            left.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
            page.addView(left, LinearLayout.LayoutParams(0, -1, 1f))
            page.addView(webCol, LinearLayout.LayoutParams(dp(260), -1).apply { leftMargin = dp(20) })
            setContentView(page)
        }

        if (RemoteService.enabled(this)) RemoteService.start(this)
        showWeb()
        connect()
        Thread {                                                      // asks GitHub for the latest release, nothing else is sent
            try {
                val r = Release.newer(version(), "tv", "tv-setup.apk")
                if (r != null) ui { updateTile.say(T("tv_main.new_version_select", "v" to r.version)) }
            } catch (ignored: Exception) { }                          // offline: stay quiet, the tile still checks on demand
        }.start()
    }

    /** Opened by touch or by another app, nothing has the focus yet: the first arrow key lands on the first tile that works. */
    override fun dispatchKeyEvent(e: android.view.KeyEvent): Boolean {
        val arrow = e.keyCode in intArrayOf(android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
            android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT, android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        if (arrow && e.action == android.view.KeyEvent.ACTION_DOWN && (currentFocus == null || window.decorView.isInTouchMode)) {
            (tiles.firstOrNull { it.view.isEnabled } ?: tiles[1]).view.let { it.isFocusableInTouchMode = true; it.requestFocus(); it.isFocusableInTouchMode = false }
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    /** The day this build was made, as dd/mm/yyyy ("" when unknown). */
    private fun released(): String = try {
        getString(resources.getIdentifier("released", "string", packageName)).split("-").let { it[2] + "/" + it[1] + "/" + it[0] }
    } catch (e: Exception) { "" }

    private fun version(): String =
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "0" } catch (e: Exception) { "0" }

    private fun autoText() = if (AutoUpdate.on(this)) T("tv_main.automatic_updates_on") else T("tv_main.automatic_updates_off")

    /**
     * Downloads this app's APK from the latest GitHub release and installs it through the TV's own ADB (no "unknown sources"
     * needed), with SetupUpdate's script: it opens this screen again once the new version runs, or the old one if it does not.
     */
    /** The Update tile: update now, automatic updates on or off, and removing this app, kept out of the way here. */
    private fun updateMenu() {
        if (busy) return
        val items = arrayOf(T("tv_main.update_now"), autoText(), T("tv_main.remove_tv_setup"))
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("TV Setup " + version())
            .setItems(items) { _, i ->
                when (i) {
                    0 -> if (adb == null) log(T("tv_main.needs_adb_turn_on")) else update()
                    1 -> { AutoUpdate.set(this, !AutoUpdate.on(this)); log(autoText()) }
                    2 -> askRemove()
                }
            }.show()
    }

    private fun update() {
        job { _ ->
            log(T("tv_main.checking_for_a_new"))
            val r = Release.newer(version(), "tv", "tv-setup.apk")
            if (r == null) { log(T("tv_main.newest_version", "v" to version())); return@job }
            val a = adb ?: throw java.io.IOException("ADB")
            if (SetupUpdate.bad(SetupUpdate.state(a), r.version)) {
                log(T("tv_main.version_bad_back", "v" to r.version))
                return@job
            }
            log(T("tv_main.downloading_version", "v" to r.version))
            val apk = File(getExternalFilesDir("update"), "tv-setup-" + r.version + ".apk")
            var last = 0
            r.download(apk, Release.Progress { pct -> if (pct >= last + 25) { last = pct; log("  $pct%") } })
            log(T("tv_main.installing_tv_setup_opens"))
            SetupUpdate.start(a, apk.absolutePath, r.version, true)
        }
    }

    /** How the last update went, said once on this screen when it is recent: a version that was undone is worth knowing about. */
    private fun lastUpdate(a: Adb) {
        val s = SetupUpdate.state(a)
        if (System.currentTimeMillis() - s.optLong("at") > 24 * 3600_000L) return
        val seen = getSharedPreferences("remote", MODE_PRIVATE)
        val key = s.optString("state") + s.optLong("at")
        if (seen.getString("update_seen", "") == key) return
        val line = when (s.optString("state")) {
            "ok" -> if (s.optString("version") == version()) T("tv_main.updated_to", "v" to version()) else null
            "rolledback" -> T("tv_main.rolled_back", "v" to s.optString("version"), "old" to s.optString("from"))
            "broken" -> T("tv_main.version_shaky", "v" to s.optString("version"))
            "failed" -> T("tv_main.install_failed", "v" to s.optString("version"), "detail" to s.optString("detail"))
            else -> null
        } ?: return
        seen.edit().putString("update_seen", key).apply()
        log(line)
    }

    override fun onPause() { shown = false; super.onPause() }

    override fun onResume() {
        super.onResume()
        shown = true
        showWeb()
        val s = setup
        if (s != null && !busy) Thread { try { showStatus(s) } catch (ignored: Exception) { } }.start()   // back from Tailscale / Play Store: refresh the IPs
        else if (setup == null && retryOnResume && !connecting) { retryOnResume = false; connect() }   // back from Developer options: ADB may be on now
    }

    override fun onDestroy() {
        val a = adb                                                   // this screen's own connection; RemoteService has its own
        adb = null; setup = null
        Thread { try { a?.close() } catch (ignored: Exception) { } }.start()
        super.onDestroy()
    }

    private val APP_LINK = "https://github.com/HoangTran0410/tv-remote/releases/latest/download/tv-remote.apk"

    /** This TV's address on the home network, read from the network interfaces (ADB not needed). */
    private fun wifiIp(): String = RemoteService.lanIp()

    /** The right-hand card: the web remote's QR code and address, pairing, and the phone app. */
    private fun showWeb() {
        val on = RemoteService.enabled(this)
        val ip = lanIp.ifEmpty { wifiIp() }
        val focused = currentFocus
        webCol.removeAllViews()
        webCol.addView(text(T("tv_main.control_from_a_phone"), 16, TvUi.TEXT, true).apply { gravity = Gravity.CENTER_HORIZONTAL },   // over the centred QR and address
            LinearLayout.LayoutParams(-1, -2))
        if (on && ip.isNotEmpty()) {
            val secure = RemoteService.secure()                       // HTTPS once its certificate exists; plain HTTP before and on an older browser's bookmark
            val scheme = if (secure) "https://" else "http://"
            val port = if (secure) RemoteService.HTTPS_PORT else RemoteService.PORT
            val link = scheme + ip + ":" + port
            qr(link)?.let { webCol.addView(it, LinearLayout.LayoutParams(dp(136), dp(136)).apply { topMargin = dp(10); gravity = Gravity.CENTER_HORIZONTAL }) }
            val addr = text(link.removePrefix(scheme), 15, TvUi.TEXT, true)
            addr.gravity = Gravity.CENTER
            webCol.addView(addr, margins(top = 8))
            if (secure) Tls.shortFingerprintIfReady(this)?.let {      // to compare with what the browser shows for the certificate
                webCol.addView(text(T("tv_main.fingerprint", "fp" to it), 12, MUTED, false).apply { gravity = Gravity.CENTER })
            }
            if (tsIp.isNotEmpty()) {
                val ts = text(T("tv_main.away_addr", "addr" to "$tsIp:$port"), 13, MUTED, false)
                ts.gravity = Gravity.CENTER
                webCol.addView(ts)
            }
        } else {
            webCol.addView(text(if (on) T("tv_main.the_tv_has_no")
                else T("tv_main.off"), 14, MUTED, false), margins(top = 8))
        }
        webBtn.text = if (on) T("tv_main.web_remote_on") else T("tv_main.web_remote_off")
        (webBtn.parent as? LinearLayout)?.removeView(webBtn)
        webCol.addView(webBtn, margins(top = 10))
        if (on) webCol.addView(smallButton(T("tv_main.paired_devices_n", "n" to RemoteService.pairedCount(this))) { pairedMenu() }, margins(top = 6))
        showRules()

        webCol.addView(View(this), if (phone) LinearLayout.LayoutParams(1, dp(16)) else LinearLayout.LayoutParams(1, 0, 1f))
        val app = LinearLayout(this)
        app.gravity = Gravity.CENTER_VERTICAL
        qr(APP_LINK)?.let { app.addView(it, LinearLayout.LayoutParams(dp(60), dp(60))) }
        val words = LinearLayout(this)
        words.orientation = LinearLayout.VERTICAL
        words.setPadding(dp(12), 0, 0, 0)
        words.addView(text(T("tv_main.tv_remote_android_app"), 14, TvUi.TEXT, true))
        words.addView(text(T("tv_main.scan_to_download"), 12, MUTED, false))
        app.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        webCol.addView(app)
        if (focused != null && focused.parent == null) webBtn.requestFocus()   // the focused button was rebuilt: keep the focus on this card
    }

    /** The timers and limits, on the right of the status card: nothing there when none is set. */
    private fun showRules() {
        rulesCard.removeAllViews()
        val (line, sleeping) = rulesLine() ?: run { rulesCard.visibility = View.GONE; return }
        rulesCard.visibility = View.VISIBLE
        rulesCard.addView(text(line, 14, TvUi.TEXT, false).apply { setLineSpacing(dp(3).toFloat(), 1f); gravity = Gravity.END })
        if (sleeping) rulesCard.addView(smallButton(T("tv_main.cancel_the_sleep_timer")) {
            Rules.change(this, org.json.JSONObject().put("do", "sleep").put("minutes", 0)); showWeb()
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6); gravity = Gravity.END })
    }

    /** The devices let in, one row each with when it was paired and its own Forget: the same list the TV web page shows (Paired.kt). */
    private fun pairedMenu() {
        val fmt = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
        val now = System.currentTimeMillis()
        val rows = LinearLayout(this)
        rows.orientation = LinearLayout.VERTICAL
        rows.setPadding(dp(24), dp(4), dp(24), dp(4))
        var dlg: android.app.AlertDialog? = null
        var firstUnpair: View? = null                                  // the dialog opens with the focus on a button, not on the scroll view
        val list = Paired.list(this)
        for (d in list) {
            val days = ((now - d.seen) / (24 * 3600_000L)).toInt()
            val seen = if (days <= 0) T("tv_main.today") else if (days == 1) T("tv_main.yesterday") else T("tv_main.days_ago", "n" to days)
            val name = d.name.ifEmpty { T("tv_main.older_device") }
            val row = LinearLayout(this)
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(8), 0, dp(8))
            val words = LinearLayout(this)
            words.orientation = LinearLayout.VERTICAL
            words.addView(text(name, 15, TvUi.TEXT, true))
            words.addView(text(listOf(if (d.created > 0) T("tv_main.paired_at", "at" to fmt.format(java.util.Date(d.created))) else "", T("tv_main.used_when", "when" to seen), d.ip)
                .filter { it.isNotEmpty() }.joinToString("   ·   "), 12, MUTED, false))
            row.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
            val un = smallButton(T("setup.unpair")) { confirmForget(name) { Paired.forget(this, d.id); dlg?.dismiss(); pairedMenu() } }
            if (firstUnpair == null) firstUnpair = un
            row.addView(un, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
            rows.addView(row)
        }
        if (list.isEmpty()) rows.addView(text(T("tv_main.no_device_yet"), 14, MUTED, false))
        val scroll = ScrollView(this)
        scroll.addView(rows)
        val b = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(T("tv_main.paired_devices_2"))
            .setView(scroll)
            .setNegativeButton(T("common.close"), null)
        if (list.size > 1) b.setNeutralButton(T("tv_main.unpair_all")) { _, _ -> confirmForget(T("tv_main.every_device")) { RemoteService.forgetAll(this) } }
        dlg = b.show()
        (firstUnpair ?: dlg?.getButton(android.app.AlertDialog.BUTTON_NEGATIVE))?.requestFocus()
    }

    private fun confirmForget(what: String, forget: () -> Unit) {
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setMessage(T("tv_main.unpair_what_it_will", "what" to what))
            .setPositiveButton(T("setup.unpair")) { _, _ -> forget(); log(T("tv_main.unpaired_what", "what" to what)); showWeb() }
            .setNegativeButton(T("common.cancel"), null).show()
    }

    /** One line per timer or limit that is set, and whether the sleep timer is running; null when nothing is set. */
    private fun rulesLine(): Pair<String, Boolean>? {
        val o = Rules.load(this)
        val lines = ArrayList<String>()
        val at = o.optLong("sleep_at")
        val sleeping = at > System.currentTimeMillis()
        if (sleeping) lines.add("😴 " + T("tv_main.sleep_at", "at" to java.text.SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(at))))
        Rules.bedtime(o).let { b -> if (b.optBoolean("on")) lines.add("🌙 " + T("tv_main.bedtime_ranges", "ranges" to b.getJSONArray("ranges").let { l -> (0 until l.length()).joinToString(", ") { l.getJSONObject(it).let { r -> r.optString("from") + " – " + r.optString("to") } } })) }
        val n = o.optJSONObject("limits")?.length() ?: 0
        if (n > 0) lines.add("⏳ " + T("tv_main.daily_limits", "n" to n))
        return if (lines.isEmpty()) null else Pair(lines.joinToString("\n"), sleeping)
    }

    private fun qr(link: String): View? {
        val bmp = dev.tvremote.core.web.Qr.bitmap(link, 6) ?: return null
        val img = ImageView(this)
        img.setImageBitmap(bmp)
        img.background = box(Color.WHITE, 10, 0)
        img.setPadding(dp(6), dp(6), dp(6), dp(6))
        return img
    }

    @Volatile private var connecting = false
    private var watching = false

    private fun questionUp(a: dev.tvremote.core.adb.Adb) = (a.shell("dumpsys activity activities | grep -c 'Hist.*UsbDebuggingActivity'", 10000).trim().toIntOrNull() ?: 0) > 0

    /** While this screen is in front, never hide the TV's question: one left behind it (it opened over one, or one queued
     *  came up just as it returned) keeps the TV from showing any other, a phone's included, and nobody sees why. With ADB
     *  at hand this screen looks every 3 s: it steps aside for such a question, and answers a leftover copy of its own. */
    private fun unblock(a: dev.tvremote.core.adb.Adb) = Thread {
        val mine = a.fingerprint()
        while (adb === a) {
            try {
                Thread.sleep(3000)
                if (!shown || !questionUp(a)) continue
                ui { if (shown) moveTaskToBack(true) }
                val r = dev.tvremote.core.adb.AdbPrompt.allow(a, mine, 6000, false)   // a copy of its own: already trusted
                dev.tvremote.core.WireLog.all.add("·", "TV Setup stepped aside for a question: $r")
                if (r == "allowed" && !questionUp(a)) a.shell("am start -n $packageName/.MainActivity", 10000)
            } catch (e: InterruptedException) { return@Thread } catch (ignored: Exception) { }
        }
    }.apply { isDaemon = true }.start()
    @Volatile private var backed = false                              // stepped aside for the TV's question (uncover)

    /** The TV's question comes up over this screen. Still in front 4 s into asking: the TV dropped it, as another question
     *  (a dead connection's, from before this screen opened) is up behind this one. Stepping aside shows it; once it is
     *  answered, Adb asks again and ours comes up. Then this screen comes back. */
    private fun uncover() {
        if (watching) return
        watching = true
        var askingFor = 0; var hinted = false
        val v = window.decorView
        val tick = object : Runnable {
            override fun run() {
                if (!connecting) { watching = false; return }
                askingFor = if (dev.tvremote.core.adb.Adb.askState() != "ask") 0 else if (shown) askingFor + 1 else askingFor   // in front, no question over it
                if (askingFor == 2) { backed = true; moveTaskToBack(true) }
                // still no answer: the TV's ADB can stay sure a question is up when none is on screen; off and on clears that
                if (askingFor >= 20 && !hinted) {
                    hinted = true
                    setStatus(WARN, T("tv_main.no_question_on_the"),
                        T("tv_main.turn_usb_debugging_off"))
                    tiles[1].view.requestFocus()
                }
                v.postDelayed(this, 2000)
            }
        }
        v.postDelayed(tick, 2000)
    }

    private fun connect() {
        setEnabled(false)
        connecting = true
        setStatus(WARN, T("tv_main.connecting_adb"), "")
        Thread {
            try {
                adb?.close()
                val a = Adb(filesDir)
                a.connect("127.0.0.1", 5555, Adb.Prompt {
                    ui { setStatus(WARN, T("allow.the_tv_is_asking"),
                        T("tv_main.tick_always_allow_then"))
                        uncover() }
                })
                adb = a
                // back in front: Android lets an app in the background start no screen, the shell it just got may; but
                // never over a question still up (another key's, queued): that one would then block every later one
                if (backed) { backed = false; try { if (!questionUp(a)) a.shell("am start -n $packageName/.MainActivity", 10000) } catch (ignored: Exception) { } }
                unblock(a)
                val s = Setup(a, getExternalFilesDir(null), Words.lang, Setup.Log { log(it) })
                setup = s
                try { lastUpdate(a) } catch (ignored: Exception) { }
                showStatus(s)
                connecting = false
                ui { setEnabled(true) }
            } catch (e: Exception) {
                adb = null; setup = null
                connecting = false
                ui {
                    setStatus(BAD, T("tv_main.no_adb_connection"), T("tv_main.turn_on_adb_debugging"))
                    val refused = (e.message ?: "").contains("refused", ignoreCase = true)   // on this very TV a refused port is ADB off, never a slow network
                    log("✗ " + if (refused) T("tv_main.adb_refused") else reason(e))
                    if (android.os.Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) howto(T("tv_main.xiaomi_adb"))
                    else if (android.os.Build.MANUFACTURER.equals("TCL", ignoreCase = true)) howto(T("setup.tcl_hidden_adb"))   // TCL China: a key sequence, no Developer options   // China's PatchWall has no Developer options: ADB is under Account & Security
                    howto(T("tv_main.enable_adb_howto"))
                    howto(T("tv_main.then_come_back_here"))
                    tiles[1].enable(true)
                    tiles[1].view.requestFocus()
                    val off = (e.message ?: "").lowercase().let { it.contains("refused") }   // ADB is off; not: the person said no to the prompt
                    if (!openedDev && off) {                          // once: straight to where ADB is turned on, no hunting through menus
                        openedDev = true
                        log(T("tv_main.opening_developer_options_to"))
                        devOptions()
                    }
                }
            }
        }.start()
    }

    @Throws(Exception::class)
    private fun showStatus(setup: Setup) {
        val name = setup.describe()
        val ts = setup.tailscaleIp()
        val lan = setup.lanIp()
        val tsInstalled = setup.has(Setup.TS_PKG)
        lanIp = lan; tsIp = ts
        ui {
            showWeb()
            setStatus(OK, T("tv_main.adb_connected"), name + "\n" + T("tv_main.adb_networks", "lan" to lan.ifEmpty { "?" }, "ts" to (if (ts.isNotEmpty()) ts else if (tsInstalled) T("tv_main.signed_out") else T("tv_main.not_installed"))))
            tsStatus = if (ts.isNotEmpty()) T("tv_main.connected_ip", "ip" to ts) else if (tsInstalled) T("tv_main.signed_out_select_to")
                else T("tv_main.not_installed_select_to")
        }
    }

    private fun setStatus(color: Int, title: String, detail: String) {
        dot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        statusTitle.text = title
        statusTitle.setTextColor(if (color == OK) TvUi.TEXT else color)
        statusDetail.text = detail
        statusDetail.visibility = if (detail.isEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * Opens the TV's Developer options straight away. Some operator boxes hide Settings on their home screen, or ask for a
     * password of their own when "Build" is pressed seven times; the screen itself often opens when asked for by name.
     */
    private var openedDev = false
    private var retryOnResume = false                                // one more try when the person comes back from the screen we sent them to

    private fun keepText() = if (KeepAdb.on(this)) T("tv_main.turn_back_on_when")
        else T("tv_main.turn_back_on_when_2")

    /** Connection: the TV's ADB again, Developer options and whether TV Setup turns ADB debugging back on (KeepAdb), Tailscale. */
    private fun connectionMenu() {
        if (busy) return
        val items = arrayOf(T("tv_main.reconnect") + " · " + T("tv_main.the_tv_s_adb"), T("tv_main.open_developer_options"), keepText(),
            "Tailscale · " + tsStatus)
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(T("tv_main.connection"))
            .setItems(items) { _, i ->
                when (i) {
                    0 -> connect()
                    1 -> devOptions()
                    2 -> { KeepAdb.set(this, !KeepAdb.on(this)); log(keepText()) }
                    3 -> if (adb == null) log(T("tv_main.needs_adb_turn_on")) else tailscale()
                }
            }.show()
    }

    /** Optimize TV: the clean-up and its undo, the home screen to use, the remote buttons the stock launcher answered (KeyFix). */
    private fun optimizeMenu() {
        if (busy) return
        val items = arrayOf(T("tv_main.optimize_tv") + " · " + T("tv_main.fewer_extra_apps"),
            T("setup.undo_ok") + " · " + T("tv_main.as_before_optimize"),
            T("launcher.tile") + " · " + T("launcher.tile_sub"),
            T("keyfix.tile") + " · " + keysSub(),
            T("hold.tile") + " · " + T(if (HoldKeys.on(this)) "hold.on" else "hold.off") + "\n" + T("hold.sub"))   // says the remote on the phone / web is the way round
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(T("tv_main.optimize_tv"))
            .setItems(items) { _, i ->
                if (i < 3 && adb == null) { log(T("tv_main.needs_adb_turn_on")); return@setItems }
                when (i) {
                    0 -> chooseOptimize()
                    1 -> job { it.undo() }
                    2 -> chooseLauncher()
                    3 -> { KeyFix.want(this, !KeyFix.wanted(this)); log(T("keyfix.tile") + " · " + keysSub()) }
                    4 -> {                                            // HoldKeys: stored as a rule, so the phone and web switch it too
                        val on = !HoldKeys.on(this)
                        Rules.change(this, org.json.JSONObject().put("do", "holdkeys").put("on", on))
                        log(T("hold.tile") + " · " + T(if (on) "hold.on" else "hold.off") + (if (on && !KeyFix.can(this)) " · " + T("keyfix.need_adb") else ""))
                    }
                }
            }.show()
    }

    private fun devOptions() {
        retryOnResume = true
        // Android TV's own Developer options page only says "not available for this user" when they are still off, on a second
        // profile, or locked (a box seen from FPT): say which, and when they are only off open About for the 7 presses first.
        val um = getSystemService(android.os.UserManager::class.java)
        if (um != null && (um.hasUserRestriction(android.os.UserManager.DISALLOW_DEBUGGING_FEATURES) || !um.isSystemUser)) {
            log(T("tv_main.dev_locked")); return
        }
        val off = android.os.Build.TYPE == "user" &&
            android.provider.Settings.Global.getInt(contentResolver, android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 1
        val dev = T("tv_main.developer_options")
        val tries = if (off) listOf(
            T("tv_main.about_press_ok_7") to android.content.Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS),
            T("page.settings") to android.content.Intent(android.provider.Settings.ACTION_SETTINGS)) else listOf(
            dev to android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            dev to android.content.Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.system.development.DevelopmentActivity"),
            dev to android.content.Intent().setClassName("com.android.settings", "com.android.settings.DevelopmentSettings"),
            T("tv_main.about_press_ok_7") to android.content.Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS),
            T("page.settings") to android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
        fun attempt(i: Int) {
            if (i >= tries.size) { log(T("tv_main.this_tv_does_not")); return }
            val (name, intent) = tries[i]
            try {
                startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {                                  // not on this TV, or not allowed: the next one
                attempt(i + 1)
                return
            }
            // a screen that is not allowed to open closes itself at once and leaves us in front: then try the next one
            window.decorView.postDelayed({
                if (hasWindowFocus()) attempt(i + 1)
                else howto(if (off) T("tv_main.dev_off_opened", "name" to name) else T("tv_main.opened_turn_on", "name" to name))
            }, 1200)
        }
        attempt(0)
    }

    private fun interface Job { @Throws(Exception::class) fun run(setup: Setup) }

    /** Runs one piece of work on the ADB connection in the background, tiles greyed out and a spinner meanwhile. */
    /** "Optimize TV": first what this TV has, then the person ticks what to do (suggested apps ticked, other apps not). */
    private fun chooseOptimize() {
        val s = setup ?: return
        if (busy) return
        log(T("tv_main.looking_at_what_this"))
        Thread {
            val plan = try {
                s.plan { pkg -> try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { null } }
            } catch (e: Exception) { ui { log("✗ " + reason(e)) }; return@Thread }
            ui { showOptimize(plan) }
        }.start()
    }

    /** The choices, in this app's own look: rows to tick with the remote's OK, sections, and the two buttons at the bottom. */
    private fun showOptimize(plan: org.json.JSONObject) {
        val d = android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        val root = android.widget.FrameLayout(this)
        root.setBackgroundColor(Color.argb(215, 5, 7, 10))
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = box(TvUi.CARD, 22, 0)
        card.setPadding(dp(32), dp(26), dp(32), dp(22))

        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val badge = ImageView(this)
        badge.setImageResource(R.drawable.ic_zap)
        badge.setColorFilter(Color.WHITE)
        badge.background = box(ACCENT, 12, 0)
        badge.setPadding(dp(9), dp(9), dp(9), dp(9))
        head.addView(badge, LinearLayout.LayoutParams(dp(44), dp(44)))
        val words = LinearLayout(this)
        words.orientation = LinearLayout.VERTICAL
        words.setPadding(dp(14), 0, 0, 0)
        words.addView(text(T("tv_main.optimize_tv"), 22, TvUi.TEXT, true))
        words.addView(text(T("tv_main.turning_an_app_off"), 13, MUTED, false))
        head.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(head)

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val rows = ArrayList<Pair<String?, BooleanArray>>()             // (package or null, ticked)
        val count = text("", 13, MUTED, false)
        fun recount() {
            val n = rows.count { it.first != null && it.second[0] }
            count.text = if (n == 0) T("tv_main.no_apps_turned_off") else T("tv_main.apps_off_n", "n" to n)
        }
        fun row(title: String, sub: String, on: Boolean, pkg: String?): View {
            val ticked = booleanArrayOf(on)
            rows.add(pkg to ticked)
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(14), dp(10), dp(14), dp(10))
            r.isFocusable = true
            r.isClickable = true
            val mark = TextView(this)
            mark.gravity = Gravity.CENTER
            mark.textSize = 16f
            mark.typeface = Typeface.DEFAULT_BOLD
            r.addView(mark, LinearLayout.LayoutParams(dp(26), dp(26)))
            val w = LinearLayout(this)
            w.orientation = LinearLayout.VERTICAL
            w.setPadding(dp(14), 0, 0, 0)
            val tt = text(title, 16, TvUi.TEXT, true)
            tt.isSingleLine = true
            tt.ellipsize = TextUtils.TruncateAt.END
            w.addView(tt)
            val st = text(sub, 12, MUTED, false)
            st.isSingleLine = true
            st.ellipsize = TextUtils.TruncateAt.END
            if (sub.isNotEmpty()) w.addView(st)
            r.addView(w, LinearLayout.LayoutParams(0, -2, 1f))
            fun paint() {
                val f = r.isFocused
                r.background = if (f) box(ACCENT, 12, 2) else box(TILE, 12, 0)
                mark.text = if (ticked[0]) "✓" else ""
                mark.setTextColor(if (f) ACCENT else Color.WHITE)
                mark.background = if (ticked[0]) box(if (f) Color.WHITE else ACCENT, 7, 0) else GradientDrawable().apply { cornerRadius = dp(7).toFloat(); setStroke(dp(2), if (f) Color.WHITE else MUTED) }
                st.setTextColor(if (f) Color.WHITE else MUTED)
            }
            r.setOnClickListener { ticked[0] = !ticked[0]; paint(); recount() }
            r.setOnFocusChangeListener { _, _ -> paint() }
            paint()
            return r
        }
        fun add(v: View) = list.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        fun header(s: String) = list.addView(section(s), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        val usingHome = plan.optString("home") == Setup.HOME_PKG
        header(T("tv_main.general"))
        add(row(T("setup.anim_off"),
            if (plan.optBoolean("anim", true)) T("tv_main.faster") else T("setup.anim_already"), true, null))
        add(row(if (usingHome) T("setup.tvhome_in_use") else T("setup.tvhome_use"),
            (if (plan.optBoolean("tvhome")) T("tv_main.light_no_ads") else T("tv_main.light_no_ads_download")), true, null))
        val sug = plan.optJSONArray("suggest") ?: org.json.JSONArray()
        if (sug.length() > 0) header(T("tv_main.suggested_apps_to_turn"))
        for (i in 0 until sug.length()) { val a = sug.getJSONObject(i); add(row(a.optString("label"), a.optString("pkg"), true, a.optString("pkg"))) }
        val more = plan.optJSONArray("apps") ?: org.json.JSONArray()
        if (more.length() > 0) header(T("tv_main.turn_off_other_apps"))
        for (i in 0 until more.length()) { val a = more.getJSONObject(i); add(row(a.optString("label"), a.optString("pkg"), false, a.optString("pkg"))) }
        recount()

        val most = (resources.displayMetrics.heightPixels * 0.62).toInt()  // the list grows with its rows, then scrolls
        val scroll = object : ScrollView(this) {
            override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(most, View.MeasureSpec.AT_MOST))
        }
        scroll.addView(list)
        card.addView(scroll, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        val foot = LinearLayout(this)
        foot.gravity = Gravity.CENTER_VERTICAL
        foot.setPadding(0, dp(18), 0, 0)
        val go = smallButton(T("tv_main.optimize")) {
            val ticks = rows.map { it.second[0] }
            d.dismiss()
            job { it.apply(Setup.Choice(ticks[0], ticks[1], rows.filter { r -> r.first != null && r.second[0] }.map { r -> r.first!! })) }
        }
        go.textSize = 16f
        go.setPadding(dp(22), dp(12), dp(22), dp(12))
        go.id = View.generateViewId()
        go.background = box(Color.rgb(36, 58, 108), 10, 0)                   // dim until focused: only one button ever looks "on"
        go.setOnFocusChangeListener { v, f -> v.background = if (f) box(ACCENT, 10, 2) else box(Color.rgb(36, 58, 108), 10, 0) }
        list.getChildAt(list.childCount - 1)?.nextFocusDownId = go.id
        val cancel = smallButton(T("common.cancel")) { d.dismiss() }
        cancel.textSize = 16f
        cancel.setPadding(dp(22), dp(12), dp(22), dp(12))
        foot.addView(go)
        foot.addView(cancel, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
        foot.addView(count, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(18) })
        card.addView(foot)

        root.addView(card, android.widget.FrameLayout.LayoutParams(dp(820), -2, Gravity.CENTER))
        d.setContentView(root)
        d.show()
        list.getChildAt(1)?.requestFocus()                                // the first choice, under its heading
    }

    /** The launcher gallery on the TV itself: signed list, each card focusable with the remote, install checked and put back by [LauncherInstall]. */
    private fun chooseLauncher() {
        val a = adb ?: return
        if (busy) return
        log(T("launcher.loading"))
        Thread {
            val g: dev.tvremote.core.ontv.Launchers.Gallery
            val facts: dev.tvremote.core.ontv.Launchers.Facts
            val have: Set<String>
            val home: String
            try {
                g = dev.tvremote.core.ontv.Launchers.fetch()
                facts = dev.tvremote.core.ontv.Launchers.facts { a.shell(it) }
                have = a.shell("pm list packages").split("\n").map { it.removePrefix("package:").trim() }.toSet()
                home = dev.tvremote.core.ontv.LauncherSwap({ a.shell(it) }).homeNow().substringBefore('/')
            } catch (e: Exception) { ui { log("✗ " + reason(e)) }; return@Thread }
            val pics = g.entries.associate { e -> e.id to try { dev.tvremote.core.ontv.Launchers.thumb(g, e).let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) } } catch (x: Exception) { null } }
            ui { showLaunchers(a, g, facts, have, home, pics) }
        }.start()
    }

    private fun showLaunchers(a: Adb, g: dev.tvremote.core.ontv.Launchers.Gallery, facts: dev.tvremote.core.ontv.Launchers.Facts, have: Set<String>, home: String,
                              pics: Map<String, android.graphics.Bitmap?>) {
        val d = android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        val root = android.widget.FrameLayout(this)
        root.setBackgroundColor(Color.argb(215, 5, 7, 10))
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = box(TvUi.CARD, 22, 0)
        card.setPadding(dp(32), dp(26), dp(32), dp(22))
        card.addView(text(T("launcher.title"), 22, TvUi.TEXT, true))
        val status = text("", 13, MUTED, false)
        card.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        var stopped = false
        var running = false
        fun why(e: Exception): String {
            val m = e.message ?: ""
            val w = m.substringAfter("launcher:rolledback:", "")
            return if (w.isNotEmpty()) T("core.fr_launcher_back", "why" to when (w) {
                "crash" -> T("launcher.why_crash"); "home" -> T("launcher.why_home"); "timeout" -> T("launcher.why_timeout")
                "interrupted" -> T("launcher.why_interrupted"); else -> T("launcher.why_user") })
            else reason(e)
        }
        fun install(e: dev.tvremote.core.ontv.Launchers.Entry) {
            running = true; stopped = false
            status.text = T("launcher.step_download", "pct" to 0)
            Thread {
                try {
                    val inst = dev.tvremote.core.ontv.LauncherInstall(a, cacheDir, g, Words.lang)
                    val state = inst.run(e, dev.tvremote.core.ontv.LauncherSwap.CONFIRM_SEC, { step, pct ->
                        ui { status.text = when (step) { "download" -> T("launcher.step_download", "pct" to pct); "install" -> T("launcher.step_install"); else -> T("launcher.step_swap") } }
                    }, { stopped })
                    if (state == "pending") {
                        inst.askOnTv(e)                                   // the TV's own question (Keep / Go back), over this screen
                        val end = inst.await({ left -> ui { status.text = T("launcher.step_confirm", "s" to left) } }, { stopped })
                        if (end != "kept") throw java.io.IOException("launcher:rolledback:" + (dev.tvremote.core.ontv.LauncherSwap({ a.shell(it) }).state()?.why ?: "timeout"))
                    }
                    ui { log("✓ " + e.name); d.dismiss() }
                } catch (x: Exception) { ui { running = false; status.text = "✗ " + why(x); log("✗ " + why(x)) } }
            }.start()
        }
        fun card(e: dev.tvremote.core.ontv.Launchers.Entry): View {
            val fit = dev.tvremote.core.ontv.Launchers.fits(e, facts)
            val using = e.pkg == home
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(14), dp(10), dp(14), dp(10))
            r.isFocusable = true
            r.isClickable = true
            val pic = ImageView(this)
            pic.scaleType = ImageView.ScaleType.CENTER_CROP
            pics[e.id]?.let { pic.setImageBitmap(it) }
            r.addView(pic, LinearLayout.LayoutParams(dp(128), dp(72)))
            val w = LinearLayout(this)
            w.orientation = LinearLayout.VERTICAL
            w.setPadding(dp(16), 0, 0, 0)
            w.addView(text(if (e.kind == "play") e.name + "  ▶ " + T("launcher.badge_play") else e.name, 17, TvUi.TEXT, true))
            val ds = text(e.desc(Words.lang), 12, MUTED, false)
            ds.maxLines = 2
            ds.ellipsize = TextUtils.TruncateAt.END
            w.addView(ds)
            val tag = if (using) T("launcher.in_use") else if (!fit.ok) when (fit.why) {
                "sdk" -> T("launcher.why_sdk", "android" to e.minSdk); "abi" -> T("launcher.why_abi"); "fire" -> T("launcher.why_fire")
                "atv" -> T("launcher.why_atv"); "gtv" -> T("launcher.why_gtv"); else -> T("launcher.why_not_tv") }
                else if (e.kind == "play" && e.pkg !in have) T("launcher.play") else if (e.pkg in have) T("launcher.use") else T("launcher.install")
            val st = text(tag, 13, if (!fit.ok) WARN else if (using) OK else ACCENT, true)
            w.addView(st, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(3) })
            r.addView(w, LinearLayout.LayoutParams(0, -2, 1f))
            val tagColor = if (!fit.ok) WARN else if (using) OK else ACCENT
            fun paint() {                                                 // on the blue focus the words turn white: blue on blue is lost
                val f = r.isFocused
                r.background = if (f) box(ACCENT, 12, 2) else box(TILE, 12, 0)
                st.setTextColor(if (f) Color.WHITE else tagColor); ds.setTextColor(if (f) Color.WHITE else MUTED)
            }
            r.setOnFocusChangeListener { _, _ -> paint() }
            paint()
            r.setOnClickListener {
                if (running || using || !fit.ok) return@setOnClickListener
                if (e.kind == "play" && e.pkg !in have) {             // once it is on the TV it goes through the same safe swap as the others
                    a.shell("am start -a android.intent.action.VIEW -d '" + e.playUrl + "'")
                    status.text = T("launcher.play_opened")
                } else install(e)
            }
            return r
        }
        for (e in g.entries) list.addView(card(e), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val most = (resources.displayMetrics.heightPixels * 0.62).toInt()
        val scroll = object : ScrollView(this) {
            override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(most, View.MeasureSpec.AT_MOST))
        }
        scroll.addView(list)
        card.addView(scroll, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val foot = LinearLayout(this)
        foot.setPadding(0, dp(16), 0, 0)
        val back = smallButton(T("launcher.back")) {
            if (running) return@smallButton
            Thread { try { dev.tvremote.core.ontv.LauncherSwap({ a.shell(it) }).rollback("user"); ui { log("✓ " + T("launcher.back")); d.dismiss() } } catch (x: Exception) { ui { status.text = "✗ " + reason(x) } } }.start()
        }
        val close = smallButton(T("common.close")) { if (running) stopped = true else d.dismiss() }
        foot.addView(back)
        foot.addView(close, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
        card.addView(foot)
        root.addView(card, android.widget.FrameLayout.LayoutParams(dp(820), -2, Gravity.CENTER))
        d.setContentView(root)
        d.setOnDismissListener { stopped = true }                      // Back while installing stops it, same as Close
        d.show()
        list.getChildAt(0)?.requestFocus()
    }

    /** Removing this app leaves the TV as it was before it: apps Optimize TV turned off come back on first (Undo). */
    private fun askRemove() {
        if (busy) return
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(T("tv_main.remove_tv_setup_2"))
            .setMessage(T("tv_main.apps_turned_off_by"))
            .setPositiveButton(T("fleet.remove")) { _, _ ->
                val s = setup
                val a = adb
                if (s == null || a == null) {                         // no ADB here: Android's own way
                    try {
                        if (KeysAddon.installed(this)) startActivity(android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:${dev.tvremote.setup.keys.KeysProtocol.ADDON}")))   // its key add-on first
                    } catch (e: Exception) { }
                    try { startActivity(android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:$packageName"))) } catch (e: Exception) { log("✗ " + reason(e)) }
                    return@setPositiveButton
                }
                busy = true
                spinner.visibility = View.VISIBLE
                log(T("tv_main.removing_tv_setup"))
                Thread {
                    try {
                        if (s.optimized()) s.undo()
                        a.shell("pm uninstall ${dev.tvremote.setup.keys.KeysProtocol.ADDON} 2>&1")   // its key add-on (KeysAddon) goes too: it is useless alone
                        a.shell("pm uninstall $packageName 2>&1")              // the end of this app: nothing after this runs
                    } catch (e: Exception) { ui { log("✗ " + reason(e)) } }
                    busy = false
                    ui { spinner.visibility = View.INVISIBLE }
                }.start()
            }
            .setNegativeButton(T("common.cancel"), null).show()
    }

    private fun job(job: Job) {
        val s = setup
        if (s == null || busy) return
        busy = true
        val running = currentFocus
        tiles.forEach { it.view.alpha = if (it.view === running) 1f else 0.4f; if (it.view === running) it.work(true) }   // focus stays where it is; the others wait
        spinner.visibility = View.VISIBLE
        Thread {
            try {
                job.run(s)
                showStatus(s)
            } catch (e: Exception) {
                log("✗ " + reason(e))
            }
            busy = false
            ui { tiles.forEach { it.work(false) }; setEnabled(true); spinner.visibility = View.INVISIBLE }
        }.start()
    }

    /**
     * One press: installs the official Tailscale when missing (through this TV's own ADB, no Play Store), lets it make its VPN
     * without asking, switches it on, and when it still has to be signed in, leaves its sign-in screen up: a QR code that a phone
     * signed in to Tailscale scans to add the TV. Nobody types an account on the TV.
     */
    private fun tailscale() {
        job { _ ->
            val ts = Tailscale(adb ?: return@job, getExternalFilesDir(null))
            if (!ts.installed()) log(T("tv_main.downloading_and_installing_the"))
            val url = ts.start { what, pct ->
                ui { connTile.progress(when (what) {
                    "download" -> T("tv_main.downloading_pct", "pct" to ((pct - 5) * 100 / 55).coerceIn(0, 100))
                    "install" -> T("tv_main.installing")
                    else -> T("tv_main.switching_on") }) }
                if (what == "start") log(T("tv_main.switching_tailscale_on"))
            }
            if (url.isEmpty()) log(T("tv_main.in_tailscale", "ip" to ts.ip()))
            else log(T("tv_main.tailscale_qr", "url" to url))
        }
    }

    private var logUsed = false
    /** A step the person has to do on the TV: its own colour, so it stands out from the plain lines around it. */
    private fun howto(line: String) = log(line, HOWTO)

    private fun log(line: String, kind: Int? = null) {
        val at = java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date())   // when it happened, not when it is drawn
        ui {
            if (!logUsed) { logUsed = true; logView.setText("", android.widget.TextView.BufferType.EDITABLE); logView.setTextColor(TvUi.TEXT) }
            // one colour per kind, read from the line's own mark before a font without it drops the mark: one look tells a failure from a step
            val tint = kind ?: when (line.trimStart().firstOrNull()) { '✗', '❌' -> BAD; '⚠' -> WARN; '✓', '✅' -> OK; else -> null }
            val row = android.text.SpannableString("$at  ${drawable(line)}\n")
            row.setSpan(android.text.style.ForegroundColorSpan(TvUi.DIM), 0, at.length, 0)   // the time stays quiet
            if (tint != null) row.setSpan(android.text.style.ForegroundColorSpan(tint), at.length, row.length, 0)
            logView.append(row)
            if (logView.lineCount > 300) logView.editableText?.let { e ->        // the oldest go, the colours of the rest stay
                var cut = 0; repeat(100) { cut = e.indexOf('\n', cut) + 1 }
                e.delete(0, cut)
            }
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    /** [line] without the symbols this TV's font has no glyph for (✗ ✓ ⚠ came out as empty boxes on a Chinese Xiaomi): the words say it anyway. */
    private fun drawable(line: String): String {
        if (android.os.Build.VERSION.SDK_INT < 23) return line
        val p = logView.paint
        val sb = StringBuilder(); var i = 0
        while (i < line.length) {
            val n = Character.charCount(line.codePointAt(i)); val ch = line.substring(i, i + n)
            if (line.codePointAt(i) < 0x2000 || p.hasGlyph(ch)) sb.append(ch) else if (i + n < line.length && line[i + n] == ' ') i++   // its space goes with it
            i += n
        }
        return sb.toString()
    }

    private fun reason(e: Exception): String = dev.tvremote.core.Errors.say(e, Words.lang)

    private fun ui(r: Runnable) { runOnUiThread(r) }

    /** Tiles that need ADB follow it; "Developer options" always works. Focus stays where it was, or goes to a tile that works. */
    private fun setEnabled(on: Boolean) {
        val had = currentFocus
        tiles.forEachIndexed { i, tile -> tile.enable(on || i >= 1) }   // without ADB: Connection (Debugging turns it on) and Update (removing works without ADB)
        if (on && !focusedOnce) { focusedOnce = true; tiles[0].view.requestFocus() }   // ready for the first time: start on the first action
        else if (had == null || !had.isEnabled) (if (on) tiles[0] else tiles[1]).view.requestFocus()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun margins(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }

    private fun box(color: Int, radius: Int, stroke: Int): GradientDrawable {
        val d = GradientDrawable()
        d.cornerRadius = dp(radius).toFloat()
        d.setColor(color)
        if (stroke > 0) d.setStroke(dp(stroke), Color.WHITE)
        return d
    }

    private fun section(s: String): TextView {
        val v = text(s, 12, MUTED, true)
        v.letterSpacing = 0.08f
        return v
    }

    private fun text(s: String, sp: Int, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = sp.toFloat()
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    /** The smaller buttons of the right-hand card; the focused one is unmistakable on a TV. */
    private fun smallButton(label: String, l: View.OnClickListener): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 13f
        b.isAllCaps = false
        b.setOnClickListener(l)
        b.isFocusable = true
        b.setTextColor(Color.WHITE)
        b.background = box(TILE, 10, 0)
        b.setOnFocusChangeListener { v, f -> v.background = if (f) box(ACCENT, 10, 2) else box(TILE, 10, 0) }
        b.minHeight = 0
        b.minimumHeight = 0
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(dp(12), dp(7), dp(12), dp(7))
        b.stateListAnimator = null                                   // no raised shadow, and none of the theme's extra room around it
        return b
    }
}
