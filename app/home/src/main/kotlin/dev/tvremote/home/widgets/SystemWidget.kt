package dev.tvremote.home.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.net.TrafficStats
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.LinearLayout.HORIZONTAL
import android.widget.LinearLayout.LayoutParams
import android.widget.LinearLayout.VERTICAL
import android.widget.TextView
import dev.tvremote.home.Font
import dev.tvremote.home.R
import dev.tvremote.home.widgets.Widgets.Companion.AMBER
import dev.tvremote.home.widgets.Widgets.Companion.CYAN
import dev.tvremote.home.widgets.Widgets.Companion.DIM
import dev.tvremote.home.widgets.Widgets.Companion.GREEN
import dev.tvremote.home.widgets.Widgets.Companion.POINTS
import dev.tvremote.home.widgets.Widgets.Companion.levelColors
import dev.tvremote.home.widgets.Widgets.Facts

/** Rings only: as many as fit, no title, no graph. */
internal fun Widgets.systemHalf(card: LinearLayout, f: Facts, room: Int) {
    fun part(p: Pair<Long, Long>) = if (p.second > 0) p.first.toFloat() / p.second else 0f
    val all = ArrayList<View>()
    all += ring(part(f.ram), a.getString(R.string.w_ram), levelColors(part(f.ram)))
    f.cpu?.let { all += ring(it, a.getString(R.string.w_cpu), AMBER) }
    all += ring(part(f.disk), a.getString(R.string.w_disk), levelColors(part(f.disk)))
    f.rssi?.let { r -> all += ring(((r + 100) * 2).coerceIn(0, 100) / 100f, a.getString(R.string.w_signal), GREEN) }
    val row = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    all.take((room / dp(78)).coerceAtLeast(1)).forEach { row.addView(it, LayoutParams(0, -2, 1f)) }
    card.gravity = Gravity.CENTER_VERTICAL
    card.addView(row, LayoutParams(-1, -2))
    if (f.cpu == null && f.cpuWhy != 0) card.addView(note(a.getString(f.cpuWhy)).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })       // the missing ring, said where it would be
}

/** A small ring with its percent inside and its name under it. */
internal fun Widgets.ring(part: Float, name: String, colors: IntArray) = LinearLayout(a).apply {
    orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
    addView(FrameLayout(a).apply {
        addView(Ring(this@ring, a, part, colors))
        addView(text("${Math.round(part * 100)}%", 13f, Color.WHITE).apply { typeface = Font.medium; gravity = Gravity.CENTER },
            FrameLayout.LayoutParams(-1, -1))
    }, LayoutParams(dp(50), dp(50)))
    addView(text(name, 12f, Color.WHITE).apply { gravity = Gravity.CENTER }, LayoutParams(-1, -2).apply { topMargin = dp(2) })
}

/** One point of the network graph: bytes since the last one, all the TV's apps together. Cheap, on the main thread. */
fun Widgets.sample() {
    if (speed == null) return
    val rx = TrafficStats.getTotalRxBytes(); val tx = TrafficStats.getTotalTxBytes(); val now = SystemClock.elapsedRealtime()
    if (rx < 0 || tx < 0) return                                       // the device does not count
    if (lastRx >= 0 && now > lastAt) {
        val secs = (now - lastAt) / 1000f
        System.arraycopy(down, 1, down, 0, POINTS - 1); down[POINTS - 1] = ((rx - lastRx).coerceAtLeast(0) / secs)
        System.arraycopy(up, 1, up, 0, POINTS - 1); up[POINTS - 1] = ((tx - lastTx).coerceAtLeast(0) / secs)
        val t = "↓ " + rate(down[POINTS - 1])
        if (speed?.text?.toString() != t) speed?.text = t
        val u = "↑ " + rate(up[POINTS - 1])
        if (speedUp != null && speedUp?.text?.toString() != u) speedUp?.text = u
    }
    lastRx = rx; lastTx = tx; lastAt = now
}

/**
 * The TV at a glance: a grid of figures, two rows of 2 to 4 (as many as [width] holds, the most telling first), each a line
 * icon, the figure large and its name small under it, a thin bar under a share. Colour only where it says something: the
 * Private DNS a filter is set (green), ADB on (amber). The model, Android and the CPU in the title's right.
 */
internal fun Widgets.system(card: LinearLayout, f: Facts, width: Int) {
    val room = width - dp(40)
    val gb = 1024.0 * 1024 * 1024
    fun part(p: Pair<Long, Long>) = if (p.second > 0) p.first.toFloat() / p.second else 0f
    fun pct(x: Float) = "${Math.round(x * 100)}%"
    val cores = f.cores.takeIf { it > 0 } ?: Runtime.getRuntime().availableProcessors()
    val ghz = maxGhz()
    val cpu = Stat(R.drawable.ic_cpu, f.cpu?.let(::pct) ?: ghz?.let { "%.2f GHz".format(it) } ?: "—",
        if (f.cpu == null && f.cpuWhy != 0) a.getString(f.cpuWhy) else a.getString(R.string.w_cpu) + " · " + a.getString(R.string.w_cpu_cores, cores), f.cpu)       // the reason alone: with "CPU · " in front it was cut with an ellipsis
    val ram = Stat(R.drawable.ic_memory, pct(part(f.ram)), "%s %.1f/%.1fG".format(a.getString(R.string.w_ram), f.ram.first / gb, f.ram.second / gb), part(f.ram), level = true)
    val disk = Stat(R.drawable.ic_drive, pct(part(f.disk)), "%s %.0f/%.0fG".format(a.getString(R.string.w_disk), f.disk.first / gb, f.disk.second / gb), part(f.disk), level = true)
    val up = Stat(R.drawable.ic_clock, span(f.up), a.getString(R.string.w_up))
    val ip = Stat(R.drawable.ic_router, f.ip.ifEmpty { "—" }, "IP · " + if (f.eth) "LAN" else if (f.ip.isEmpty()) a.getString(R.string.w_offline) else "Wi-Fi")
    val signal = f.rssi?.let { Stat(R.drawable.ic_wifi, "$it dBm", f.link?.let { l -> "$l Mbps" } ?: a.getString(R.string.w_signal)) }
    val dns = f.dns?.let { Stat(R.drawable.ic_shield_ok, dnsName(it), "DNS · DoT", tint = GREEN[0]) }
    val net = Stat(R.drawable.ic_updown, "↓ —", "↑ —", live = true)
    fun adb(port: Boolean) = Stat(R.drawable.ic_usb, (if (f.adb) a.getString(R.string.w_on) else a.getString(R.string.w_off)).uppercase() +
        if (port && f.adb && f.adbPort > 0) " :${f.adbPort}" else "", "ADB", tint = if (f.adb) AMBER[0] else null)
    fun layout(cols: Int) = when (cols) {
        4 -> listOf(listOf(cpu, ram, disk, up), listOf(ip) + listOfNotNull(signal, dns, net).take(2) + adb(true))
        3 -> listOf(listOf(cpu, ram, disk), listOf(ip, dns ?: signal ?: net, adb(true)))
        else -> listOf(listOf(Stat(ram.icon, ram.value, a.getString(R.string.w_ram), ram.bar, level = true), Stat(disk.icon, disk.value, a.getString(R.string.w_disk), disk.bar, level = true)),
            listOf(ip, adb(false)))                                  // narrow: the names without the sizes
    }
    // each row shares its width on its own: alike when every figure fits its equal part (CPU, RAM, disk), else by what each
    // needs (an address is longer than "ON"); as many columns as fit once [Shrink] has taken the figures down to 15 sp
    val big = Paint().apply { textSize = 19f * sp; typeface = Font.medium }
    val small = Paint().apply { typeface = Font.regular; textSize = 12f * sp; letterSpacing = 0.08f }
    val k0 = 15f / 19
    fun needs(row: List<Stat>, cols: Int) = FloatArray(cols) { k -> (row.getOrNull(k)?.let { maxOf(big.measureText(if (it.live) "↓ 999 KB/s" else it.value), small.measureText(it.label.uppercase())) * k0 } ?: 0f) + dp(29) }
    fun fits(rows: List<List<Stat>>, c: Int) = rows.all { needs(it, c).sum() + dp(10) * (c - 1) <= room }
    val cols = (4 downTo 3).firstOrNull { fits(layout(it), it) } ?: 2
    val rows = layout(cols)
    val about = listOfNotNull(android.os.Build.MODEL.takeIf { cols >= 3 }, "Android " + android.os.Build.VERSION.RELEASE,
        if (cols >= 4 && ghz != null) "$cores×%.1fGHz".format(ghz) else null).joinToString(" · ")
    card.addView(head(a.getString(R.string.w_system), about))
    val grid = LinearLayout(a).apply { orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL }       // the rows in the middle of the card, not stuck under the title
    rows.forEachIndexed { r, row ->
        val line = LinearLayout(a).apply { orientation = HORIZONTAL }
        val need = needs(row, cols); val gaps = dp(10) * (cols - 1)
        val alike = need.max() * cols + gaps <= room
        val whole = need.sum() + gaps <= room                        // each its least, the rest shared alike; else all by need
        for (k in 0 until cols) line.addView(row.getOrNull(k)?.let(::stat) ?: View(a), when {
            alike -> LayoutParams(0, -2, 1f)
            whole -> LayoutParams(need[k].toInt(), -2, 1f)
            else -> LayoutParams(0, -2, need[k])
        }.apply { if (k > 0) leftMargin = dp(10) })
        grid.addView(line, LayoutParams(-1, -2).apply { if (r > 0) topMargin = dp(14) })
    }
    card.addView(grid, LayoutParams(-1, 0, 1f).apply { topMargin = dp(4) })
    if (speed != null) { lastRx = -1; sample() }
}

/** One figure of [system]: [bar] a share drawn under it; [live] the network's speed, its two lines set by [sample]. */
internal class Stat(val icon: Int, val value: String, val label: String, val bar: Float? = null, val tint: Int? = null, val live: Boolean = false, val level: Boolean = false)

internal fun Widgets.stat(s: Stat): View = LinearLayout(a).apply {
    orientation = HORIZONTAL
    addView(ImageView(a).apply {
        setImageResource(s.icon); imageTintList = android.content.res.ColorStateList.valueOf(s.tint ?: 0xD9FFFFFF.toInt())
    }, LayoutParams(dp(20), dp(20)).apply { topMargin = dp(3) })
    val col = LinearLayout(a).apply { orientation = VERTICAL }
    val v = Shrink(a, 19f, 13f).apply {
        text = s.value; setTextColor(s.tint ?: Color.WHITE); maxLines = 1; typeface = Font.medium
        setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(), 0x80000000.toInt())
    }
    col.addView(v)
    val l = Shrink(a, 12f, 9f).apply { text = s.label.uppercase(); setTextColor(DIM); maxLines = 1; letterSpacing = 0.08f }       // one line, shrunk like the figure: a wrapped "BỘ / NHỚ" read as two labels
    col.addView(l)
    if (s.live) { speed = v; speedUp = l.apply { letterSpacing = 0f } }
    s.bar?.let { col.addView(Bar(this@stat, a, it, if (s.level) levelColors(it) else CYAN), LayoutParams(-1, dp(3)).apply { topMargin = dp(6); rightMargin = dp(8) }) }
    addView(col, LayoutParams(0, -2, 1f).apply { leftMargin = dp(9) })
}

/** A line that gets smaller (down to [least] sp) rather than cut: "192.168.100.200" whole in a narrow column. */
internal class Shrink(c: Context, private val most: Float, private val least: Float) : TextView(c) {
    init { typeface = Font.regular }
    override fun onMeasure(w: Int, h: Int) {
        val room = MeasureSpec.getSize(w) - paddingLeft - paddingRight
        if (MeasureSpec.getMode(w) != MeasureSpec.UNSPECIFIED && room > 0) {
            var size = most
            textSize = size
            while (size > least && paint.measureText(text.toString()) > room) { size -= 1f; textSize = size }
            if (paint.measureText(text.toString()) > room) letterSpacing = 0f      // the spaced capitals of a label give their room first
            ellipsize = TextUtils.TruncateAt.END                     // still too long at the least size: cut, as a last resort
        }
        super.onMeasure(w, h)
    }
}

/** A Private DNS host by its service's name when it is a well known one ("dns.adguard-dns.com" → AdGuard), else the host. */
internal fun Widgets.dnsName(host: String): String {
    val h = host.lowercase()
    return listOf("adguard" to "AdGuard", "nextdns" to "NextDNS", "cloudflare" to "Cloudflare", "one.one.one.one" to "Cloudflare", "quad9" to "Quad9",
        "google" to "Google", "controld" to "Control D", "mullvad" to "Mullvad", "opendns" to "OpenDNS", "cleanbrowsing" to "CleanBrowsing")
        .firstOrNull { h.contains(it.first) }?.second ?: host.removePrefix("dns.")
}

/** The fastest core's top speed, read once (the kernel's own file; some TVs hide it, an emulator makes it up: null). */
internal fun Widgets.maxGhz(): Float? = ghzMemo ?: try {
    (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { java.io.File("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq").takeIf { f -> f.canRead() }?.readText()?.trim()?.toLongOrNull() }
        .filter { it >= 200_000 }.maxOrNull()?.let { it / 1_000_000f }?.also { ghzMemo = it }       // kHz; an emulator says 2: unknown, not "0.0GHz"
} catch (e: Exception) { null }

internal fun Widgets.rate(b: Float) = when {
    b >= 1_000_000 -> "%.1f MB/s".format(b / 1_000_000)
    b >= 1_000 -> "%.0f KB/s".format(b / 1_000)
    else -> "%.0f B/s".format(b)
}
