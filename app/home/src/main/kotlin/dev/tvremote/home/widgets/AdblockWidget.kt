package dev.tvremote.home.widgets

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.LayoutParams
import android.widget.LinearLayout.VERTICAL
import android.widget.Toast
import dev.tvremote.home.Font
import dev.tvremote.home.HomeConfig
import dev.tvremote.home.R
import dev.tvremote.home.widgets.Widgets.Companion.DIM
import dev.tvremote.home.widgets.Widgets.Companion.GREEN
import dev.tvremote.home.widgets.Widgets.Facts
import java.net.InetAddress

/**
 * Private DNS (Android 9+) as an ad blocker: on = `private_dns_mode=hostname` with an ad-blocking resolver ([HomeConfig.ADBLOCK_DNS]),
 * off = what was there before. Writing Settings.Global needs WRITE_SECURE_SETTINGS, which only ADB grants (the clients' grant step,
 * HomeConfig.GRANT_WIFI). DNS-level only: it blocks ad domains, not the ads YouTube streams inside its videos.
 */
internal object Adblock {
    private const val MODE = "private_dns_mode"; private const val HOST = "private_dns_specifier"
    private fun prefs(c: Context) = c.getSharedPreferences("adblock", Context.MODE_PRIVATE)

    fun supported() = Build.VERSION.SDK_INT >= HomeConfig.ADBLOCK_MIN_SDK
    fun canWrite(c: Context) = c.checkSelfPermission(HomeConfig.WRITE_SECURE) == PackageManager.PERMISSION_GRANTED
    private fun get(c: Context, k: String) = try { Settings.Global.getString(c.contentResolver, k) } catch (e: Exception) { null }

    /** The ad-blocking host Private DNS names now, else null. */
    fun host(c: Context): String? = if (get(c, MODE) == "hostname") get(c, HOST)?.takeIf { HomeConfig.adblockHost(it) } else null
    fun on(c: Context) = host(c) != null

    /** Writes at once (so a switch shows it), then checks names still resolve; if not, the old setting is put back and [done] gets false. */
    @Throws(SecurityException::class)
    fun set(c: Context, on: Boolean, done: (Boolean) -> Unit) {
        val cr = c.contentResolver; val p = prefs(c)
        if (on) {
            val now = host(c)
            if (now == null) p.edit().putString("prev_mode", get(c, MODE).orEmpty()).putString("prev_host", get(c, HOST).orEmpty()).apply()
            else p.edit().putString("last", now).apply()
            val target = p.getString("last", null)?.takeIf { HomeConfig.adblockHost(it) } ?: HomeConfig.ADBLOCK_DNS[0]
            Settings.Global.putString(cr, HOST, target)
            Settings.Global.putString(cr, MODE, "hostname")
            p.edit().putString("last", target).apply()
            Thread({ // a resolver that does not answer leaves the TV without Internet: names must resolve within a few seconds, else back
                val ok = resolves(c, target)
                if (!ok) restore(c)
                done(ok)
            }, "adblock-check").apply { isDaemon = true }.start()
        } else { restore(c); done(true) }
    }

    /** What was there before the widget turned it on ("automatic" when nothing was kept: Android's own default). */
    private fun restore(c: Context) {
        val cr = c.contentResolver; val p = prefs(c)
        val host = p.getString("prev_host", null)?.takeIf { it.isNotEmpty() && !HomeConfig.adblockHost(it) }
        // the person's own Private DNS (NextDNS…) comes back; only an ad-block one, or a hostname with no host, falls back to automatic
        val mode = p.getString("prev_mode", null)?.takeIf { it.isNotEmpty() && (it != "hostname" || host != null) } ?: "opportunistic"
        try {
            Settings.Global.putString(cr, MODE, mode)
            Settings.Global.putString(cr, HOST, host)                       // null when there was none: no ad-block name left behind in Settings
        } catch (e: SecurityException) { }
    }

    /**
     * Whether names resolve through [target]. The system takes the new setting a moment later, and a lookup before that went the
     * old way and passed (FPT box: every Private DNS fails there, yet the widget stayed on and the TV lost the Internet): first wait
     * until the network says it uses [target], then past InetAddress's own 2 s cache, then ask.
     */
    @Suppress("DEPRECATION")
    private fun resolves(c: Context, target: String): Boolean {
        val cm = c.getSystemService(android.net.ConnectivityManager::class.java)
        val taken = System.currentTimeMillis() + 6000
        while (System.currentTimeMillis() < taken) {
            val lp = cm?.activeNetwork?.let { cm.getLinkProperties(it) }
            if (lp == null || lp.privateDnsServerName.equals(target, true)) break
            try { Thread.sleep(300) } catch (e: InterruptedException) { return false }
        }
        try { Thread.sleep(2500) } catch (e: InterruptedException) { return false }
        // a lookup through a resolver that does not answer blocks well past any deadline (FPT box: 10 s+ on one name), so the
        // names are asked on their own thread and this one waits 7 s at most
        val ok = java.util.concurrent.atomic.AtomicBoolean(false)
        val done = java.util.concurrent.CountDownLatch(1)
        val until = System.currentTimeMillis() + 7000
        Thread({
            while (!ok.get() && System.currentTimeMillis() < until) {
                for (n in listOf("example.com", "one.one.one.one", "dns.google")) if (try { InetAddress.getByName(n); true } catch (e: Exception) { false }) { ok.set(true); break }
                if (!ok.get()) try { Thread.sleep(800) } catch (e: InterruptedException) { break }
            }
            done.countDown()
        }, "adblock-ask").apply { isDaemon = true }.start()
        try { done.await(7, java.util.concurrent.TimeUnit.SECONDS) } catch (e: InterruptedException) { }
        return ok.get()
    }
}

/** OK on the widget (and its menu's switch): turn it on / off, or say in one line how the permission comes. */
internal fun Widgets.adblockToggle() {
    if (!Adblock.supported()) return
    if (!Adblock.canWrite(a)) { Toast.makeText(a, a.getString(R.string.adb_need), Toast.LENGTH_LONG).show(); return }
    try {
        Adblock.set(a, !Adblock.on(a)) { ok ->
            a.runOnUiThread { if (!ok) Toast.makeText(a, a.getString(R.string.adb_failed), Toast.LENGTH_LONG).show(); onWeather?.invoke() }
        }
    } catch (e: SecurityException) { Toast.makeText(a, a.getString(R.string.adb_need), Toast.LENGTH_LONG).show() }
    onWeather?.invoke()
}

private fun Widgets.stateLine(host: String?, dns: String?) = if (host != null) a.getString(R.string.adb_on, dnsName(host)) else a.getString(R.string.adb_off)
private fun Widgets.hintLine(host: String?, dns: String?) = when {
    host != null -> a.getString(R.string.adb_hint_on)
    dns != null -> a.getString(R.string.adb_other, dnsName(dns))
    else -> a.getString(R.string.adb_hint_off)
}

/** The glass look: a shield (green while on), the state, and what OK does. [full]: with its title. */
internal fun Widgets.adblockGlass(widget: LinearLayout, f: Facts, full: Boolean) {
    val host = f.dns?.takeIf { HomeConfig.adblockHost(it) }
    widget.gravity = Gravity.CENTER_VERTICAL
    if (full) widget.addView(head(a.getString(R.string.w_adblock), ""))
    val row = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    row.addView(tile(if (host != null) GREEN else Widgets.GREY, R.drawable.ic_shield_ok), LayoutParams(dp(if (full) 56 else 44), dp(if (full) 56 else 44)))
    val words = LinearLayout(a).apply { orientation = VERTICAL }
    // small: "On · AdGuard" was cut after "Ad": the name goes to the second line there
    val state = if (full || host == null) stateLine(host, f.dns) else a.getString(R.string.home_ov_on)
    val hint = if (full || host == null) hintLine(host, f.dns) else dnsName(host)   // "AdGuard · OK to turn off" was cut too
    words.addView(text(state, if (full) 22f else 18f, Color.WHITE).apply { typeface = Font.medium })
    words.addView(text(hint, 12f, DIM))
    row.addView(words, LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
    widget.addView(row, if (full) LayoutParams(-1, 0, 1f) else LayoutParams(-1, -2))
}

/** The tonal look: the state as the figure, what OK does as the caption. */
internal fun Widgets.adblockTonal(widget: LinearLayout, f: Facts, width: Int, compact: Boolean) {
    val host = f.dns?.takeIf { HomeConfig.adblockHost(it) }
    tonalBody(widget, a.getString(R.string.w_adblock), if (host != null) dnsName(host) else a.getString(R.string.adb_off), hintLine(host, f.dns), R.drawable.ic_shield_ok, host != null, width, compact)
}
