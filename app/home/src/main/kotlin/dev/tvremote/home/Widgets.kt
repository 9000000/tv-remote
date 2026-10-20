package dev.tvremote.home

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.Inet4Address
import java.text.SimpleDateFormat
import java.util.Date

/**
 * The cards between the clock and the favourites (HomeConfig "widgets"), only to look at: no focus stops on them. Read off the
 * main thread once a minute while the screen is shown ([HomeActivity]), the network graph every [SAMPLE_MS]; nothing runs
 * while an app is in front.
 *   system  memory, storage and the Wi-Fi signal as rings, the network's speed as a moving graph, the address, ADB, time on
 *   usage   a column per day of the last week, the most used apps with their share, today's app limits: from TV Setup (its
 *           HomeInfo provider), the count the limits themselves hold to
 */
class Widgets(private val a: Activity, private val dp: (Number) -> Int) : LinearLayout(a) {
    /** What a card needs, gathered on a background thread. */
    class Facts(val ram: Pair<Long, Long>, val disk: Pair<Long, Long>, val ip: String, val eth: Boolean, val rssi: Int?, val up: Long,
                val adb: Boolean, val usage: JSONObject?, val icons: Map<String, Drawable>)

    companion object {
        const val SAMPLE_MS = 2000L
        private const val POINTS = 40                                  // 80 s of the network graph
        private val CYAN = intArrayOf(0xFF62D2FF.toInt(), 0xFF4F7CFF.toInt())
        private val VIOLET = intArrayOf(0xFFC59BFF.toInt(), 0xFF7B5CFF.toInt())
        private val GREEN = intArrayOf(0xFF6BE8A6.toInt(), 0xFF22B07D.toInt())
        private val AMBER = intArrayOf(0xFFFFD27A.toInt(), 0xFFFF8A4C.toInt())
        private const val DIM = 0xA6FFFFFF.toInt()
    }

    private val sp = a.resources.displayMetrics.scaledDensity
    private val down = FloatArray(POINTS); private val up = FloatArray(POINTS)
    private var lastRx = -1L; private var lastTx = -1L; private var lastAt = 0L
    private var spark: Spark? = null; private var speed: TextView? = null

    init {
        orientation = HORIZONTAL; clipChildren = false; clipToPadding = false
        setLayerType(LAYER_TYPE_HARDWARE, null)                        // drawn once into a texture: the dock's focus animation only copies it
    }

    fun read(want: List<String>): Facts {
        val mem = ActivityManager.MemoryInfo().also { (a.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
        val disk = try { StatFs(Environment.getDataDirectory().path).let { (it.totalBytes - it.availableBytes) to it.totalBytes } } catch (e: Exception) { 0L to 0L }
        var ip = ""; var eth = false
        try {                                                          // the system's networks: NetworkInterface would need INTERNET, which TV Home has not
            val cm = a.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            @Suppress("DEPRECATION") for (n in cm.allNetworks) {
                val c = cm.getNetworkCapabilities(n) ?: continue
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                val v4 = cm.getLinkProperties(n)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress } ?: continue
                val wired = c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                if (ip.isEmpty() || wired) { ip = v4.hostAddress ?: ""; eth = wired }   // a cable wins, as the TV routes it
            }
        } catch (e: Exception) { }
        val rssi = if (eth || ip.isEmpty()) null else try {
            @Suppress("DEPRECATION") (a.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo?.rssi?.takeIf { it in -126..-1 }
        } catch (e: Exception) { null }
        val adb = try { Settings.Global.getInt(a.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 } catch (e: Exception) { false }
        val usage = if ("usage" !in want) null else try {
            a.contentResolver.call(Uri.parse(HomeConfig.USAGE), "usage", null, null)?.getString("json")?.let { JSONObject(it) }
        } catch (e: Exception) { null }                                    // no TV Setup, or one older than this card
        val icons = HashMap<String, Drawable>()
        usage?.let { u -> for (k in listOf("week", "limits")) u.optJSONArray(k)?.let { l -> for (i in 0 until l.length()) l.optJSONObject(i)?.optString("pkg")?.let { p ->
            if (p !in icons) try { icons[p] = a.packageManager.getApplicationIcon(p) } catch (e: Exception) { }
        } } }
        return Facts((mem.totalMem - mem.availMem) to mem.totalMem, disk, ip, eth, rssi, SystemClock.elapsedRealtime(), adb, usage, icons)
    }

    fun show(want: List<String>, f: Facts, label: (String) -> String) {
        removeAllViews(); spark = null; speed = null
        visibility = if (want.isEmpty()) View.GONE else View.VISIBLE
        want.forEachIndexed { i, w ->
            val card = card()
            if (w == "system") system(card, f) else usage(card, f, label)
            addView(card, LayoutParams(0, -1, 1f).apply { if (i > 0) leftMargin = dp(20) })
        }
    }

    /** One point of the network graph: bytes since the last one, all the TV's apps together. Cheap, on the main thread. */
    fun sample() {
        val s = spark ?: return
        val rx = TrafficStats.getTotalRxBytes(); val tx = TrafficStats.getTotalTxBytes(); val now = SystemClock.elapsedRealtime()
        if (rx < 0 || tx < 0) return                                   // the device does not count
        if (lastRx >= 0 && now > lastAt) {
            val secs = (now - lastAt) / 1000f
            System.arraycopy(down, 1, down, 0, POINTS - 1); down[POINTS - 1] = ((rx - lastRx).coerceAtLeast(0) / secs)
            System.arraycopy(up, 1, up, 0, POINTS - 1); up[POINTS - 1] = ((tx - lastTx).coerceAtLeast(0) / secs)
            val t = "↓ ${rate(down[POINTS - 1])}   ↑ ${rate(up[POINTS - 1])}"
            if (speed?.text?.toString() != t) speed?.text = t
            s.invalidate()
        }
        lastRx = rx; lastTx = tx; lastAt = now
    }

    private fun system(card: LinearLayout, f: Facts) {
        card.addView(head(a.getString(R.string.w_system), a.getString(R.string.w_up) + " " + span(f.up)))
        val body = LinearLayout(a).apply { orientation = HORIZONTAL }
        val gb = 1024.0 * 1024 * 1024
        fun part(p: Pair<Long, Long>) = if (p.second > 0) p.first.toFloat() / p.second else 0f
        val rings = LinearLayout(a).apply { orientation = HORIZONTAL }
        rings.addView(gauge(part(f.ram), a.getString(R.string.w_ram), "%.1f / %.0f GB".format(f.ram.first / gb, Math.ceil(f.ram.second / gb)), CYAN))
        rings.addView(gauge(part(f.disk), a.getString(R.string.w_disk), "%.0f / %.0f GB".format(f.disk.first / gb, f.disk.second / gb), VIOLET), LayoutParams(-2, -2).apply { leftMargin = dp(14) })
        f.rssi?.let { r -> rings.addView(gauge(((r + 100) * 2).coerceIn(0, 100) / 100f, a.getString(R.string.w_signal), "$r dBm", GREEN), LayoutParams(-2, -2).apply { leftMargin = dp(14) }) }
        body.addView(rings)
        val net = LinearLayout(a).apply { orientation = VERTICAL }
        val top = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(text(a.getString(R.string.w_net), 12f, DIM))
        speed = text("↓ —   ↑ —", 12f, Color.WHITE).apply { gravity = Gravity.END }   // not wrap_content: a new speed redraws the line, not the screen's layout
            .also { top.addView(it, LayoutParams(0, -2, 1f)) }
        net.addView(top)
        spark = Spark(a).also { net.addView(it, LayoutParams(-1, dp(46)).apply { topMargin = dp(4) }) }
        net.addView(chips(listOf(
            "IP " + f.ip.ifEmpty { "—" } to Color.WHITE,                   // cable or Wi-Fi: the pill above already says
            a.getString(R.string.w_adb) + " " + a.getString(if (f.adb) R.string.w_on else R.string.w_off) to if (f.adb) AMBER[0] else Color.WHITE)),
            LayoutParams(-1, -2).apply { topMargin = dp(8) })
        body.addView(net, LayoutParams(0, -2, 1f).apply { leftMargin = dp(20) })
        card.addView(body, LayoutParams(-1, -2).apply { topMargin = dp(10) })
        lastRx = -1; sample()
    }

    private fun usage(card: LinearLayout, f: Facts, label: (String) -> String) {
        val u = f.usage
        val week = u?.optJSONArray("week")
        val days = u?.optJSONArray("days")
        var total = 0L
        if (days != null) for (i in 0 until days.length()) total += days.optLong(i)
        card.addView(head(a.getString(R.string.w_week), if (total > 0) span(total * 1000) else ""))
        if (u == null) { card.addView(note(a.getString(R.string.w_need_setup))); return }
        val body = LinearLayout(a).apply { orientation = HORIZONTAL }
        if (days != null && days.length() == 7) body.addView(Columns(a, (6 downTo 0).map { days.optLong(it) }), LayoutParams(dp(150), dp(96)))
        val list = LinearLayout(a).apply { orientation = VERTICAL }
        val limits = HashMap<String, JSONObject>()
        u.optJSONArray("limits")?.let { l -> for (i in 0 until l.length()) l.optJSONObject(i)?.let { limits[it.optString("pkg")] = it } }
        val rows = ArrayList<Pair<String, Long>>()
        if (week != null) for (i in 0 until minOf(4, week.length())) week.getJSONObject(i).let { rows += it.optString("pkg") to it.optLong("sec") }
        for (p in limits.keys) if (rows.none { it.first == p } && rows.size < 4) rows += p to (limits[p]!!.optLong("used"))   // a limited app not among the most used
        val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
        if (rows.isEmpty()) list.addView(note(a.getString(R.string.w_none)))
        rows.forEachIndexed { i, (p, sec) -> list.addView(appRow(p, label(p), f.icons[p], sec, sec.toFloat() / max, limits[p]), LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(7) }) }
        body.addView(list, LayoutParams(0, -2, 1f).apply { if (body.childCount > 0) leftMargin = dp(20) })
        card.addView(body, LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    /** An app's line: icon, name, its share of the week as a bar, its time; an amber "x left" when it has a limit today. */
    private fun appRow(pkg: String, name: String, icon: Drawable?, sec: Long, part: Float, limit: JSONObject?) = LinearLayout(a).apply {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(a).apply { setImageDrawable(icon); scaleType = ImageView.ScaleType.FIT_CENTER }, LayoutParams(dp(22), dp(22)))
        val col = LinearLayout(a).apply { orientation = VERTICAL }
        val line = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        line.addView(text(name, 13f, Color.WHITE), LayoutParams(0, -2, 1f))
        var colors = CYAN
        if (limit != null) {
            val allowed = limit.optLong("min") * 60
            val left = (allowed - limit.optLong("used")).coerceAtLeast(0)
            colors = AMBER
            line.addView(text(if (left <= 0) a.getString(R.string.w_used_up) else a.getString(R.string.w_left, span(left * 1000)), 11f, AMBER[0]).apply {
                background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0x33FFB86B) }
                setPadding(dp(6), 0, dp(6), 0)
            }, LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        }
        line.addView(text(span(sec * 1000), 13f, Color.WHITE).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
        col.addView(line)
        col.addView(Bar(a, part, colors), LayoutParams(-1, dp(4)).apply { topMargin = dp(3) })
        addView(col, LayoutParams(0, -2, 1f).apply { leftMargin = dp(10) })
    }

    /** A ring for a share, its percent inside, its name and amount under it. */
    private fun gauge(part: Float, name: String, detail: String, colors: IntArray) = LinearLayout(a).apply {
        orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        addView(FrameLayout(a).apply {
            addView(Ring(a, part, colors))
            addView(text("${(part * 100).toInt()}%", 15f, Color.WHITE).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(-1, -1))
        }, LayoutParams(dp(62), dp(62)))
        addView(text(name, 12f, Color.WHITE).apply { gravity = Gravity.CENTER }, LayoutParams(dp(84), -2).apply { topMargin = dp(5) })
        addView(text(detail, 11f, DIM).apply { gravity = Gravity.CENTER }, LayoutParams(dp(84), -2))
    }

    private fun chips(items: List<Pair<String, Int>>) = LinearLayout(a).apply {
        orientation = HORIZONTAL
        items.forEachIndexed { i, (t, c) ->
            addView(text(t, 12f, c).apply {
                background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0x1FFFFFFF) }
                setPadding(dp(9), dp(2), dp(9), dp(2))
            }, LayoutParams(-2, -2).apply { if (i > 0) leftMargin = dp(6) })
        }
    }

    /** "3d 17h", "5h 12m", "42m". */
    private fun span(ms: Long): String {
        val m = ms / 60_000
        return when {
            m >= 1440 -> "${m / 1440}d ${m % 1440 / 60}h"
            m >= 60 -> "${m / 60}h ${m % 60}m"
            else -> "${m}m"
        }
    }

    private fun rate(b: Float) = when {
        b >= 1_000_000 -> "%.1f MB/s".format(b / 1_000_000)
        b >= 1_000 -> "%.0f KB/s".format(b / 1_000)
        else -> "%.0f B/s".format(b)
    }

    private fun card() = LinearLayout(a).apply {
        orientation = VERTICAL
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x2EFFFFFF, 0x14FFFFFF)).apply {
            cornerRadius = dp(22).toFloat(); setStroke(dp(1), 0x33FFFFFF)                // the dock band's glass
        }
        setPadding(dp(20), dp(12), dp(20), dp(14))
    }

    private fun head(left: String, right: String) = LinearLayout(a).apply {
        orientation = HORIZONTAL
        addView(text(left.uppercase(), 11f, 0xB3FFFFFF.toInt()).apply { letterSpacing = 0.12f }, LayoutParams(0, -2, 1f))
        if (right.isNotEmpty()) addView(text(right, 12f, 0xB3FFFFFF.toInt()))
    }

    private fun note(t: String) = text(t, 13f, 0xB3FFFFFF.toInt()).apply { setPadding(0, dp(10), 0, 0); maxLines = 2 }

    private fun text(t: String, size: Float, color: Int) = TextView(a).apply {
        text = t; textSize = size; setTextColor(color); maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.START
        setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(), 0x80000000.toInt())
    }

    private fun grad(colors: IntArray, w: Float) = LinearGradient(0f, 0f, w, 0f, colors[0], colors[1], Shader.TileMode.CLAMP)

    private inner class Ring(c: Context, val part: Float, val colors: IntArray) : View(c) {
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(6).toFloat(); color = 0x26FFFFFF }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(6).toFloat(); strokeCap = Paint.Cap.ROUND }
        private val r = RectF()
        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {      // drawing makes nothing new: weak TVs redraw often
            val i = dp(4).toFloat(); r.set(i, i, w - i, h - i)
            fill.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), colors[0], colors[1], Shader.TileMode.CLAMP)
        }
        override fun onDraw(cv: Canvas) {
            cv.drawArc(r, 0f, 360f, false, track)
            cv.drawArc(r, -90f, 360f * part.coerceIn(0.01f, 1f), false, fill)
        }
    }

    private inner class Bar(c: Context, val part: Float, val colors: IntArray) : View(c) {
        private val back = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF }
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val all = RectF(); private val done = RectF()
        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            all.set(0f, 0f, w.toFloat(), h.toFloat())
            done.set(0f, 0f, (w * part.coerceIn(0f, 1f)).coerceAtLeast(h.toFloat()), h.toFloat())
            p.shader = grad(colors, done.right)
        }
        override fun onDraw(cv: Canvas) {
            val rad = height / 2f
            cv.drawRoundRect(all, rad, rad, back)
            cv.drawRoundRect(done, rad, rad, p)
        }
    }

    /** The network's speed, down filled in cyan and up as a violet line, scaled to the busiest moment shown. */
    private inner class Spark(c: Context) : View(c) {
        private val area = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); strokeJoin = Paint.Join.ROUND }
        private val base = Paint().apply { color = 0x1FFFFFFF; strokeWidth = 1f }
        private val pa = Path(); private val pd = Path(); private val pu = Path()
        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            area.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0x6662D2FF, 0x0062D2FF, Shader.TileMode.CLAMP)
        }
        private fun trace(into: Path, v: FloatArray, max: Float, w: Float, h: Float) {
            into.rewind()
            for (i in 0 until POINTS) { val x = w * i / (POINTS - 1); val y = h - 2 - (h - 4) * v[i] / max; if (i == 0) into.moveTo(x, y) else into.lineTo(x, y) }
        }
        override fun onDraw(cv: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            cv.drawLine(0f, h - 0.5f, w, h - 0.5f, base)
            var max = 16_000f                                          // a quiet network stays low, not a full-height wiggle
            for (i in 0 until POINTS) max = maxOf(max, down[i], up[i])
            trace(pd, down, max, w, h); trace(pu, up, max, w, h)
            pa.rewind(); pa.addPath(pd); pa.lineTo(w, h); pa.lineTo(0f, h); pa.close()
            cv.drawPath(pa, area)
            line.color = CYAN[0]; cv.drawPath(pd, line)
            line.color = VIOLET[0]; cv.drawPath(pu, line)
        }
    }

    /** A column per day, oldest first, today's in colour, the day's letter under each. */
    private inner class Columns(c: Context, val secs: List<Long>) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11 * sp; textAlign = Paint.Align.CENTER; color = DIM }
        private val names = (6 downTo 0).map { d -> SimpleDateFormat("EEEEE", resources.configuration.locales[0]).format(Date(System.currentTimeMillis() - d * 86_400_000L)) }
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1AFFFFFF }
        private val past = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
        private val backs = List(7) { RectF() }; private val fills = List(7) { RectF() }
        private var rad = 0f
        override fun onSizeChanged(w: Int, ht: Int, ow: Int, oh: Int) {   // the columns are laid out once, drawn from here
            val h = ht - 16 * sp; val slot = w / 7f; val bw = slot * 0.52f; rad = bw / 2.5f
            val max = (secs.maxOrNull() ?: 0L).coerceAtLeast(1)
            for (i in 0 until 7) {
                val x = slot * i + (slot - bw) / 2
                backs[i].set(x, 0f, x + bw, h)
                fills[i].set(x, (h - (h - dp(4)) * secs[i] / max).coerceAtMost(h - bw / 2), x + bw, h)
            }
            p.shader = LinearGradient(0f, fills[6].top, 0f, h, CYAN[0], CYAN[1], Shader.TileMode.CLAMP)
        }
        override fun onDraw(cv: Canvas) {
            val slot = width / 7f
            for (i in 0 until 7) {
                cv.drawRoundRect(backs[i], rad, rad, track)
                if (secs[i] > 0) cv.drawRoundRect(fills[i], rad, rad, if (i == 6) p else past)
                t.color = if (i == 6) Color.WHITE else DIM
                cv.drawText(names[i], slot * i + slot / 2, height - 3 * sp, t)
            }
        }
    }
}
