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
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The cards between the clock and the favourites (HomeConfig "widgets"), only to look at: no focus stops on them. Read off the
 * main thread once a minute while the screen is shown ([HomeActivity]), the network graph every [SAMPLE_MS]; nothing runs
 * while an app is in front.
 *   system  memory, storage and the Wi-Fi signal as rings, the network's speed as a moving graph, the address, ADB, time on
 *   usage   a column per day of the last week, the most used apps with their share, today's app limits: from TV Setup (its
 *           HomeInfo provider), the count the limits themselves hold to
 *   weather  the sky now, today's low and high and the next days ([Weather]: Open-Meteo, asked on a thread of its own, the last answer kept)
 *   calendar the month, a solar and a lunar day in every cell, today filled ([Month], one View; the lunar days from [Lunar], once a day)
 * and, smaller, stacked in a column beside them:
 *   lunar   the lunar date, the next full moon or new month, the days to Tết ([Lunar], worked out here)
 *   timer   the sleep timer or bedtime and the time left (TV Setup's rules, through the same provider); none while neither is set
 *   note    the family's note (HomeConfig "note"), while there is one
 */
class Widgets(private val a: Activity, private val dp: (Number) -> Int) : LinearLayout(a) {
    /** What a card needs, gathered on a background thread. */
    class Facts(val ram: Pair<Long, Long>, val disk: Pair<Long, Long>, val ip: String, val eth: Boolean, val rssi: Int?, val up: Long,
                val adb: Boolean, val usage: JSONObject?, val icons: Map<String, Drawable>, val timer: JSONObject?, val moon: Moon?, val cpu: Float?, val cores: Int, val cal: Cal? = null, val wx: Weather.Forecast? = null)

    /** Today in the lunar calendar and the days until the next full moon (15th), new month (1st) and Tết. */
    class Moon(val day: Lunar.Day, val full: Int, val new: Int, val tet: Int)

    /** The month shown: its grid, today's cell, and the lunar day and month of every cell (a lunar day 1 shows its month too). */
    class Cal(val grid: MonthGrid.Grid, val today: Int, val lunDay: IntArray, val lunMonth: IntArray, val moon: Lunar.Day)

    companion object {
        const val SAMPLE_MS = 2000L
        private const val POINTS = 40                                  // 80 s of the network graph
        private val CYAN = intArrayOf(0xFF62D2FF.toInt(), 0xFF4F7CFF.toInt())
        private val VIOLET = intArrayOf(0xFFC59BFF.toInt(), 0xFF7B5CFF.toInt())
        private val GREEN = intArrayOf(0xFF6BE8A6.toInt(), 0xFF22B07D.toInt())
        private val AMBER = intArrayOf(0xFFFFD27A.toInt(), 0xFFFF8A4C.toInt())
        private val ROSE = intArrayOf(0xFFFF7A7A.toInt(), 0xFFE0457B.toInt())
        private const val DIM = 0xA6FFFFFF.toInt()
        private val BIG = setOf("system", "usage", "calendar", "weather")
        private const val PILLS = 300                                  // the small cards' column, dp
        private const val WX_WEIGHT = 0.9f; private const val CAL_WEIGHT = 1.35f                           // the month needs seven columns: a little more of the row than the others

        private var calKey = 0; private var calMemo: Cal? = null

        /** The month of [now] with its lunar days: worked out once a day (42 conversions), the minute's refresh takes the memo. */
        fun month(now: Calendar, first: Int): Cal {
            val key = (now.get(Calendar.YEAR) * 12 + now.get(Calendar.MONTH)) * 40 + now.get(Calendar.DAY_OF_MONTH) + first * 100000000
            calMemo?.let { if (key == calKey) return it }
            val y = now.get(Calendar.YEAR); val m = now.get(Calendar.MONTH) + 1; val d = now.get(Calendar.DAY_OF_MONTH)
            val g = MonthGrid.of(y, m, first)
            val ld = IntArray(42); val lm = IntArray(42)
            val c = Calendar.getInstance().apply { clear(); set(g.sy, g.sm - 1, g.sd) }
            for (i in 0 until g.rows * 7) {
                val l = Lunar.of(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))
                ld[i] = l.day; lm[i] = if (l.leap) -l.month else l.month
                c.add(Calendar.DAY_OF_MONTH, 1)
            }
            return Cal(g, g.offset + d - 1, ld, lm, Lunar.of(d, m, y)).also { calKey = key; calMemo = it }
        }

        /** The lunar facts of [today], counted forward day by day (a year at most: cheap, and asked once a minute). */
        fun moon(today: Calendar): Moon {
            val c = today.clone() as Calendar
            fun at(c: Calendar) = Lunar.of(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))
            val day = at(c)
            var full = -1; var new = -1; var tet = -1
            for (i in 0..400) {
                val d = if (i == 0) day else at(c)
                if (full < 0 && d.day == 15) full = i
                if (new < 0 && d.day == 1) new = i
                if (tet < 0 && d.day == 1 && d.month == 1 && !d.leap) tet = i
                if (full >= 0 && new >= 0 && tet >= 0) break
                c.add(Calendar.DAY_OF_MONTH, 1)
            }
            return Moon(day, full, new, tet)
        }
    }

    /** Set by the screen: the weather arrived (or failed) on its own thread, the cards are read again. */
    var onWeather: (() -> Unit)? = null

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
        fun ask(m: String) = try { a.contentResolver.call(Uri.parse(HomeConfig.USAGE), m, null, null)?.getString("json")?.let { JSONObject(it) } }
            catch (e: Exception) { null }                                  // no TV Setup, or one older than this card
        val usage = if ("usage" in want) ask("usage") else null
        val icons = HashMap<String, Drawable>()
        usage?.let { u -> for (k in listOf("week", "limits")) u.optJSONArray(k)?.let { l -> for (i in 0 until l.length()) l.optJSONObject(i)?.optString("pkg")?.let { p ->
            if (p !in icons) try { icons[p] = a.packageManager.getApplicationIcon(p) } catch (e: Exception) { }
        } } }
        val timer = if ("timer" in want) ask("timer") else null
        val moon = if ("lunar" in want) moon(Calendar.getInstance()) else null
        val cal = if ("calendar" in want) month(Calendar.getInstance(), Calendar.getInstance(resources.configuration.locales[0]).firstDayOfWeek) else null
        // the CPU: apps may read neither /proc/stat nor loadavg, so TV Setup's shell measures it (null without TV Setup 0.14.1 or its ADB)
        val cpu = if ("system" in want) ask("cpu") else null
        val typed = Store.get(a).optString("place")
        val wx = if ("weather" in want) {
            if (Weather.due(a, typed)) Weather.refresh(a, typed, resources.configuration.locales[0].language) { onWeather?.invoke() }
            Weather.cached(a)
        } else null
        return Facts((mem.totalMem - mem.availMem) to mem.totalMem, disk, ip, eth, rssi, SystemClock.elapsedRealtime(), adb, usage, icons, timer, moon,
            cpu?.optDouble("busy", -1.0)?.takeIf { it >= 0 }?.toFloat(), cpu?.optInt("cores") ?: 0, cal, wx)
    }

    fun show(want: List<String>, f: Facts, note: String, label: (String) -> String) {
        removeAllViews(); spark = null; speed = null
        val bigs = want.filter { it in BIG }
        val small = ArrayList<View>()
        if ("lunar" in want) f.moon?.let { small += lunar(it) }
        if ("timer" in want) timer(f.timer)?.let { small += it }
        if (note.isNotEmpty()) small += pill(tile(AMBER, R.drawable.ic_message), note, "", 1).apply {   // the words are the card: large, no "Note" over them
            ((getChildAt(1) as LinearLayout).getChildAt(0) as TextView).apply { textSize = 16f; maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END }
        }
        // each big card's width, known before they are built: a narrow one leaves out its widest part rather than cutting words
        val pills = if (bigs.size >= 3) PILLS - 60 else PILLS           // three cards and the column: the column gives way a little
        val row = (if (width > 0) width else a.resources.displayMetrics.widthPixels - dp(96)) - (if (small.isNotEmpty() && bigs.isNotEmpty()) dp(pills + 20) else 0)
        val weights = bigs.map { when (it) { "calendar" -> CAL_WEIGHT; "weather" -> WX_WEIGHT; else -> 1f } }
        val unit = (row - dp(20) * (bigs.size - 1)) / weights.sum()
        bigs.forEachIndexed { i, w ->
            val each = (unit * weights[i]).toInt()
            val card = card()
            when (w) {
                "system" -> system(card, f, each)
                "calendar" -> calendar(card, f, each)
                "weather" -> weather(card, f, each)
                else -> usage(card, f, label, each < dp(340))
            }
            addView(card, LayoutParams(0, -1, weights[i]).apply { if (i > 0) leftMargin = dp(20) })
        }
        if (small.isNotEmpty() && bigs.isNotEmpty()) {                 // a column beside the cards, sharing their height
            val col = LinearLayout(a).apply { orientation = VERTICAL }
            if (small.size == 1) stand(small[0])                         // alone, as tall as the cards: stood up, not a line in an empty box
            small.forEachIndexed { i, v -> col.addView(v, LayoutParams(-1, 0, 1f).apply { if (i > 0) topMargin = dp(12) }) }
            addView(col, LayoutParams(dp(pills), -1).apply { leftMargin = dp(20) })
        } else if (small.isNotEmpty()) for (i in 0 until 3) {            // alone: a third of the row each, so one does not stretch across it
            addView(small.getOrNull(i) ?: View(a), LayoutParams(0, if (i < small.size) -2 else 0, 1f).apply { if (i > 0) leftMargin = dp(16) })
        }
        visibility = if (childCount == 0) View.GONE else View.VISIBLE
    }

    private fun lunar(m: Moon): View {
        val r = a.resources
        val d = m.day
        val month = r.getStringArray(R.array.lunar_month)[d.month - 1] + if (d.leap) a.getString(R.string.lunar_leap) else ""
        val dayWord = if (d.day <= 10) a.getString(R.string.lunar_day_early, d.day) else d.day.toString()
        val (can, chi) = Lunar.canChi(d.year)
        val date = a.getString(R.string.lunar_title, dayWord, month)
        val year = a.getString(R.string.lunar_year, r.getStringArray(R.array.lunar_can)[can], r.getStringArray(R.array.lunar_chi)[chi])
        val title = if (date.length + year.length <= 26) "$date · $year" else date   // the year only where it fits: "Mùng 10 tháng Mười Một âm lịch" fills the line
        val near = when {
            d.month == 1 && d.day <= 3 && !d.leap -> a.getString(R.string.l_tet_now)
            m.full == 0 -> a.getString(R.string.l_full_today)
            m.new == 0 -> a.getString(R.string.l_new_today)
            m.full in 1 until m.new || m.new < 0 -> a.getString(R.string.l_full_in, m.full)
            else -> a.getString(R.string.l_new_in, m.new)
        }
        val sub = if (m.tet > 0 && !(d.month == 1 && d.day <= 3)) near + " · " + a.getString(R.string.l_tet_in, m.tet) else near
        val t = text(d.day.toString(), 19f, Color.WHITE).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); gravity = Gravity.CENTER }
        return pill(tile(ROSE, null, t), title, sub, 1)
    }

    /** The sleep timer first (it acts sooner), else the bedtime; null when neither is set. Without an answer, TV Setup is too old. */
    private fun timer(o: JSONObject?): View? {
        if (o == null) return pill(tile(VIOLET, R.drawable.ic_moon), a.getString(R.string.w_timer), a.getString(R.string.w_need_setup), 1)
        val now = System.currentTimeMillis()
        val at = o.optLong("sleep_at")
        if (at > now) return pill(tile(VIOLET, R.drawable.ic_timer), a.getString(R.string.t_sleep, hm(at)), a.getString(R.string.w_left, span(at - now + 59_999)), 1)
        val bed = o.optJSONObject("bedtime") ?: return null
        if (!bed.optBoolean("on")) return null
        val ranges = bed.optJSONArray("ranges") ?: return null
        val snooze = o.optLong("snooze_until")
        if (o.optBoolean("in_bedtime")) {
            if (snooze > now) return pill(tile(VIOLET, R.drawable.ic_moon), a.getString(R.string.t_snoozed), a.getString(R.string.t_until, hm(snooze)), 1)
            val to = (0 until ranges.length()).mapNotNull { ranges.optJSONObject(it) }.map { it.optString("to") }.firstOrNull().orEmpty()
            return pill(tile(VIOLET, R.drawable.ic_moon), a.getString(R.string.t_bed_now), if (to.isNotEmpty()) a.getString(R.string.t_until, hm(next(to, now))) else "", 1)
        }
        val start = (0 until ranges.length()).mapNotNull { ranges.optJSONObject(it)?.optString("from") }.filter { it.isNotEmpty() }.minOfOrNull { next(it, now) } ?: return null
        return pill(tile(VIOLET, R.drawable.ic_moon), a.getString(R.string.t_bed_at, hm(start)), a.getString(R.string.w_left, span(start - now + 59_999)), 1)
    }

    /** The next time the clock shows [hhmm], from [now]. */
    private fun next(hhmm: String, now: Long): Long {
        val p = hhmm.split(":"); val c = Calendar.getInstance().apply { timeInMillis = now }
        c.set(Calendar.HOUR_OF_DAY, p[0].toIntOrNull() ?: 0); c.set(Calendar.MINUTE, p.getOrNull(1)?.toIntOrNull() ?: 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    private fun hm(t: Long) = SimpleDateFormat(if (Store.get(a).optBoolean("clock24", true)) "HH:mm" else "h:mm a", resources.configuration.locales[0]).format(Date(t))

    /** A small coloured square for a pill: an icon, or [inner] (the lunar day's number). */
    private fun tile(colors: IntArray, icon: Int?, inner: View? = null) = FrameLayout(a).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = dp(12).toFloat() }
        if (icon != null) addView(ImageView(a).apply { setImageResource(icon); imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE) }, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        if (inner != null) addView(inner, FrameLayout.LayoutParams(-1, -1))
    }

    /** A small card turned upright: the square larger on top, the words bigger under it, all to the left. */
    private fun stand(v: View) {
        val p = v as? LinearLayout ?: return
        val square = p.getChildAt(0); val words = p.getChildAt(1) as LinearLayout
        p.orientation = VERTICAL; p.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        p.setPadding(dp(20), dp(14), dp(20), dp(14))
        square.layoutParams = LayoutParams(dp(56), dp(56))
        ((square as FrameLayout).getChildAt(0) as? TextView)?.textSize = 26f   // the lunar day's number
        (square.getChildAt(0) as? ImageView)?.layoutParams = FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER)
        words.layoutParams = LayoutParams(-1, -2).apply { topMargin = dp(14) }
        (words.getChildAt(0) as TextView).apply { textSize = 18f; if (maxLines > 1) maxLines = 4 }
        (words.getChildAt(1) as? TextView)?.apply { textSize = 14f; maxLines = 2; setPadding(0, dp(3), 0, 0) }
    }

    /** One of the small cards: a coloured square, a title, a line under it ([lines] of it). */
    private fun pill(square: View, title: String, sub: String, lines: Int) = LinearLayout(a).apply {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x2EFFFFFF, 0x14FFFFFF)).apply {
            cornerRadius = dp(18).toFloat(); setStroke(dp(1), 0x33FFFFFF)
        }
        setPadding(dp(12), dp(8), dp(14), dp(8))
        addView(square, LayoutParams(dp(40), dp(40)))
        addView(LinearLayout(a).apply {
            orientation = VERTICAL
            addView(text(title, 14f, Color.WHITE).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
            if (sub.isNotEmpty()) addView(text(sub, 12f, DIM).apply { maxLines = lines })
        }, LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
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

    /** As many rings as [width] holds, the liveliest first (memory, CPU, storage, signal). Narrow: no graph, the address and ADB under the rings. */
    private fun system(card: LinearLayout, f: Facts, width: Int) {
        val narrow = width < dp(420)
        card.addView(head(a.getString(R.string.w_system), (if (narrow) "" else a.getString(R.string.w_up) + " ") + span(f.up)))   // narrow: the title whole, not "HỆ T…"
        val body = LinearLayout(a).apply { orientation = HORIZONTAL }
        val gb = 1024.0 * 1024 * 1024
        fun part(p: Pair<Long, Long>) = if (p.second > 0) p.first.toFloat() / p.second else 0f
        val all = ArrayList<View>()
        all += gauge(part(f.ram), a.getString(R.string.w_ram), "%.1f / %.0f GB".format(f.ram.first / gb, Math.ceil(f.ram.second / gb)), CYAN)
        f.cpu?.let { u -> all += gauge(u, a.getString(R.string.w_cpu), a.getString(R.string.w_cpu_cores, f.cores), AMBER) }
        all += gauge(part(f.disk), a.getString(R.string.w_disk), "%.0f / %.0f GB".format(f.disk.first / gb, f.disk.second / gb), VIOLET)
        f.rssi?.let { r -> all += gauge(((r + 100) * 2).coerceIn(0, 100) / 100f, a.getString(R.string.w_signal), "$r dBm", GREEN) }
        val room = width - dp(40) - if (narrow) 0 else dp(180)          // the card's padding; the network column's least width
        val rings = LinearLayout(a).apply { orientation = HORIZONTAL }
        all.take(((room + dp(10)) / dp(94)).coerceAtLeast(1)).forEachIndexed { i, g -> rings.addView(g, LayoutParams(-2, -2).apply { if (i > 0) leftMargin = dp(10) }) }
        body.addView(rings)
        val adbChip = a.getString(R.string.w_adb) + " " + a.getString(if (f.adb) R.string.w_on else R.string.w_off) to if (f.adb) AMBER[0] else Color.WHITE
        if (narrow) {
            card.addView(body, LayoutParams(-1, -2).apply { topMargin = dp(10) })
            val adbShort = a.getString(R.string.w_adb) to if (f.adb) AMBER[0] else DIM   // no room for "ADB on": its colour says it
            card.addView(chips(listOf("IP " + f.ip.ifEmpty { "—" } to Color.WHITE, adbShort)), LayoutParams(-1, -2).apply { topMargin = dp(10) })
            return
        }
        val net = LinearLayout(a).apply { orientation = VERTICAL }
        val top = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(text(a.getString(R.string.w_net), 12f, DIM))
        speed = text("↓ —   ↑ —", 12f, Color.WHITE).apply { gravity = Gravity.END }   // not wrap_content: a new speed redraws the line, not the screen's layout
            .also { top.addView(it, LayoutParams(0, -2, 1f)) }
        net.addView(top)
        spark = Spark(a).also { net.addView(it, LayoutParams(-1, dp(46)).apply { topMargin = dp(4) }) }
        net.addView(chips(listOf(
            "IP " + f.ip.ifEmpty { "—" } to Color.WHITE,                   // cable or Wi-Fi: the pill above already says
            adbChip)),
            LayoutParams(-1, -2).apply { topMargin = dp(8) })
        body.addView(net, LayoutParams(0, -2, 1f).apply { leftMargin = dp(20) })
        card.addView(body, LayoutParams(-1, -2).apply { topMargin = dp(10) })
        lastRx = -1; sample()
    }

    /** The month: the title and the lunar month and year of today, then one View drawing the weekdays and every day ([narrow]: no lunar line). */
    private fun calendar(card: LinearLayout, f: Facts, width: Int) {
        val c = f.cal ?: return
        val r = a.resources
        val (can, chi) = Lunar.canChi(c.moon.year)
        val month = r.getStringArray(R.array.lunar_month)[c.moon.month - 1] + if (c.moon.leap) a.getString(R.string.lunar_leap) else ""
        val year = a.getString(R.string.lunar_year, r.getStringArray(R.array.lunar_can)[can], r.getStringArray(R.array.lunar_chi)[chi])
        val lunar = a.getString(R.string.cal_lunar, month, year)
        val title = r.getStringArray(R.array.cal_month)[c.grid.month - 1] + ", " + c.grid.year
        val view = Month(a, c, Calendar.getInstance(resources.configuration.locales[0]).firstDayOfWeek, (width - dp(40)) / 7f)
        card.addView(head(title, if (width - dp(40) >= dp(320)) lunar else ""))
        card.addView(view, LayoutParams(-1, view.wanted).apply { topMargin = dp(6) })
    }

    /** The sky now with today's low and high, and the next days when the card is wide enough; without an answer yet, a short line. */
    private fun weather(card: LinearLayout, f: Facts, width: Int) {
        val w = f.wx
        if (w == null) {
            card.addView(head(a.getString(R.string.w_weather), ""))
            card.addView(note(a.getString(R.string.wx_none)))
            return
        }
        val stale = System.currentTimeMillis() - w.at > 3 * 3_600_000L        // offline for hours: say when this was
        card.addView(head(w.place.ifEmpty { a.getString(R.string.wx_near) }, if (stale) hm(w.at) else ""))
        val body = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val now = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        now.addView(Sky(a, w.kind, w.night), LayoutParams(dp(56), dp(56)))
        val words = LinearLayout(a).apply { orientation = VERTICAL }
        words.addView(text("${w.temp}°", 30f, Color.WHITE).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
        words.addView(text(a.getString(kindWords(w.kind)), 13f, Color.WHITE))
        val today = w.days[0]
        words.addView(text(a.getString(R.string.wx_hi_lo, today.max, today.min), 12f, DIM))
        now.addView(words, LayoutParams(-2, -2).apply { leftMargin = dp(12) })
        body.addView(now, LayoutParams(0, -2, 1f))
        val next = w.days.drop(1)
        val side = width - dp(40) - dp(200) >= dp(62) * next.size              // beside "now" when wide, else a row under it
        if (next.isNotEmpty() && (side || width - dp(40) >= dp(62) * next.size)) {
            val days = LinearLayout(a).apply { orientation = HORIZONTAL }
            val names = a.resources.getStringArray(R.array.cal_dow)
            next.forEachIndexed { i, d ->
                val col = LinearLayout(a).apply { orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
                val dow = try { Calendar.getInstance().apply { time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d.date)!! }.get(Calendar.DAY_OF_WEEK) - 1 } catch (e: Exception) { -1 }
                col.addView(text(names.getOrElse(dow) { "" }, 12f, DIM).apply { gravity = Gravity.CENTER })
                col.addView(Sky(a, d.kind, false), LayoutParams(dp(28), dp(28)).apply { topMargin = dp(2) })
                col.addView(text("${d.max}° ${d.min}°", 12f, Color.WHITE).apply { gravity = Gravity.CENTER })
                days.addView(col, if (side) LayoutParams(dp(62), -2).apply { if (i > 0) leftMargin = dp(2) } else LayoutParams(0, -2, 1f))
            }
            if (side) body.addView(days, LayoutParams(-2, -2).apply { leftMargin = dp(8) })
            else { card.addView(body, LayoutParams(-1, -2).apply { topMargin = dp(6) }); card.addView(days, LayoutParams(-1, -2).apply { topMargin = dp(8) }); return }
        }
        card.addView(body, LayoutParams(-1, 0, 1f).apply { topMargin = dp(6) })
    }

    private fun kindWords(k: Weather.Kind) = when (k) {
        Weather.Kind.CLEAR -> R.string.wx_clear; Weather.Kind.PARTLY -> R.string.wx_partly; Weather.Kind.CLOUD -> R.string.wx_cloud
        Weather.Kind.FOG -> R.string.wx_fog; Weather.Kind.DRIZZLE -> R.string.wx_drizzle; Weather.Kind.RAIN -> R.string.wx_rain
        Weather.Kind.SNOW -> R.string.wx_snow; Weather.Kind.STORM -> R.string.wx_storm
    }

    /** [narrow]: the apps only, no columns of the days. */
    private fun usage(card: LinearLayout, f: Facts, label: (String) -> String, narrow: Boolean) {
        val u = f.usage
        val week = u?.optJSONArray("week")
        val days = u?.optJSONArray("days")
        var total = 0L
        if (days != null) for (i in 0 until days.length()) total += days.optLong(i)
        card.addView(head(a.getString(R.string.w_week).let { if (narrow) it.substringBefore(" · ") else it }, if (total > 0) span(total * 1000) else ""))
        if (u == null) { card.addView(note(a.getString(R.string.w_need_setup))); return }
        val body = LinearLayout(a).apply { orientation = HORIZONTAL }
        if (!narrow && days != null && days.length() == 7) body.addView(Columns(a, (6 downTo 0).map { days.optLong(it) }), LayoutParams(dp(150), dp(96)))
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
        line.addView(text(span(sec * 1000), 13f, Color.WHITE).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
        col.addView(line)
        col.addView(Bar(a, part, if (limit != null) AMBER else CYAN), LayoutParams(-1, dp(4)).apply { topMargin = dp(3) })
        if (limit != null) {                                          // under the bar, not beside the name: a narrow card cut the name to "…"
            val left = (limit.optLong("min") * 60 - limit.optLong("used")).coerceAtLeast(0)
            col.addView(text(if (left <= 0) a.getString(R.string.w_used_up) else a.getString(R.string.w_left, span(left * 1000)), 11f, AMBER[0]),
                LayoutParams(-2, -2).apply { topMargin = dp(2) })
        }
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

    /** A sky in one drawn square (sun or moon, cloud, rain, snow, bolt, fog) in the cards' colours; paints made once, drawing allocates nothing. */
    private inner class Sky(c: Context, val kind: Weather.Kind, val night: Boolean) : View(c) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val path = Path(); private val cut = Path(); private val box = RectF()
        private val cloudColor = 0xFFE4ECFA.toInt()

        private fun cloud(cv: Canvas, s: Float, dx: Float, dy: Float, color: Int = cloudColor) {
            fill.color = color
            box.set((0.12f + dx) * s, (0.46f + dy) * s, (0.88f + dx) * s, (0.72f + dy) * s); cv.drawRoundRect(box, 0.13f * s, 0.13f * s, fill)
            cv.drawCircle((0.38f + dx) * s, (0.50f + dy) * s, 0.17f * s, fill)
            cv.drawCircle((0.61f + dx) * s, (0.44f + dy) * s, 0.21f * s, fill)
        }

        private fun body(cv: Canvas, s: Float, cx: Float, cy: Float, r: Float) {         // the sun, or the moon at night
            if (!night) {
                fill.color = AMBER[0]; cv.drawCircle(cx * s, cy * s, r * s, fill)
                line.color = AMBER[0]; line.strokeWidth = 0.055f * s
                for (i in 0 until 8) {
                    val t = Math.toRadians(i * 45.0); val c = Math.cos(t).toFloat(); val n = Math.sin(t).toFloat()
                    cv.drawLine((cx + c * r * 1.4f) * s, (cy + n * r * 1.4f) * s, (cx + c * r * 1.75f) * s, (cy + n * r * 1.75f) * s, line)
                }
            } else {
                path.rewind(); path.addCircle(cx * s, cy * s, r * 1.15f * s, Path.Direction.CW)
                cut.rewind(); cut.addCircle((cx + r * 0.7f) * s, (cy - r * 0.45f) * s, r * 0.95f * s, Path.Direction.CW)
                path.op(cut, Path.Op.DIFFERENCE); fill.color = 0xFFFFE9A8.toInt(); cv.drawPath(path, fill)
            }
        }

        override fun onDraw(cv: Canvas) {
            val s = minOf(width, height).toFloat()
            line.strokeWidth = 0.055f * s
            when (kind) {
                Weather.Kind.CLEAR -> body(cv, s, 0.5f, 0.5f, 0.2f)
                Weather.Kind.PARTLY -> { body(cv, s, 0.36f, 0.36f, 0.15f); cloud(cv, s, 0.06f, 0.1f) }
                Weather.Kind.CLOUD -> cloud(cv, s, 0f, 0f)
                Weather.Kind.FOG -> {
                    cloud(cv, s, 0f, -0.14f, 0xFFB9C4D8.toInt())
                    line.color = DIM; line.strokeWidth = 0.06f * s
                    cv.drawLine(0.2f * s, 0.74f * s, 0.8f * s, 0.74f * s, line); cv.drawLine(0.3f * s, 0.88f * s, 0.7f * s, 0.88f * s, line)
                }
                Weather.Kind.DRIZZLE, Weather.Kind.RAIN -> {
                    cloud(cv, s, 0f, -0.14f, 0xFFB9C4D8.toInt())
                    line.color = CYAN[0]; line.strokeWidth = 0.06f * s
                    val n = if (kind == Weather.Kind.RAIN) 3 else 2
                    for (i in 0 until n) { val x = (if (n == 3) 0.3f + 0.2f * i else 0.4f + 0.2f * i) * s; cv.drawLine(x, 0.74f * s, x - 0.06f * s, 0.9f * s, line) }
                }
                Weather.Kind.SNOW -> {
                    cloud(cv, s, 0f, -0.14f, 0xFFB9C4D8.toInt())
                    fill.color = Color.WHITE
                    for (i in 0 until 3) cv.drawCircle((0.3f + 0.2f * i) * s, (if (i == 1) 0.88f else 0.8f) * s, 0.04f * s, fill)
                }
                Weather.Kind.STORM -> {
                    cloud(cv, s, 0f, -0.14f, 0xFF9AA6BE.toInt())
                    path.rewind(); path.moveTo(0.54f * s, 0.62f * s); path.lineTo(0.4f * s, 0.8f * s); path.lineTo(0.5f * s, 0.8f * s)
                    path.lineTo(0.44f * s, 0.96f * s); path.lineTo(0.62f * s, 0.74f * s); path.lineTo(0.52f * s, 0.74f * s); path.close()
                    fill.color = AMBER[0]; cv.drawPath(path, fill)
                }
            }
        }
    }

    /**
     * The weekdays and the days in ONE view. Three shapes by the room a day has ([slot], px): wide, the solar number with the lunar day
     * beside it on one line; middle, the lunar day under the solar one; thin, the solar numbers only. Today filled, Sundays rose;
     * a lunar 1st shows its month ("1/10", the Tết rose), the 15th amber. Paints made once: drawing allocates nothing.
     */
    private inner class Month(c: Context, val cal: Cal, first: Int, slot: Float) : View(c) {
        private val wide = slot >= dp(64); private val two = !wide && slot >= dp(24)
        private val rowH = dp(if (wide) 23 else if (two) 26 else 21); private val headH = dp(17)
        val wanted = headH + rowH * cal.grid.rows
        private val tiny = slot < dp(30)
        private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = (if (tiny) 12.5f else 14f) * sp; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
        private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = (if (tiny) 8.5f else 9.5f) * sp }
        private val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = (if (tiny) 10f else 11f) * sp; color = DIM }
        private val back = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CYAN[1] }
        private val box = RectF()
        private val dows = IntArray(7) { (first - 1 + it) % 7 }            // 0 = Sunday
        private val names = a.resources.getStringArray(if (slot < dp(38)) R.array.cal_dow_short else R.array.cal_dow)
        private val n = cal.grid.rows * 7
        private val inMonth = BooleanArray(n) { it >= cal.grid.offset && it < cal.grid.offset + cal.grid.days }
        private val solar = Array(n) { "" }; private val lunarText = Array(n) { "" }
        private val gap = dp(4).toFloat()
        init {
            val k = Calendar.getInstance().apply { clear(); set(cal.grid.sy, cal.grid.sm - 1, cal.grid.sd) }
            for (i in 0 until n) {
                solar[i] = k.get(Calendar.DAY_OF_MONTH).toString()
                lunarText[i] = if (cal.lunDay[i] == 1) "1/" + Math.abs(cal.lunMonth[i]) else cal.lunDay[i].toString()
                k.add(Calendar.DAY_OF_MONTH, 1)
            }
            big.textAlign = if (wide) Paint.Align.LEFT else Paint.Align.CENTER
            small.textAlign = big.textAlign
        }
        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), wanted)
        override fun onDraw(cv: Canvas) {
            val slot = width / 7f
            for (col in 0 until 7) {
                head.color = if (dows[col] == 0) ROSE[0] else DIM
                cv.drawText(names[dows[col]], slot * col + slot / 2, headH - dp(5).toFloat(), head)
            }
            for (i in 0 until n) {
                val col = i % 7; val row = i / 7
                val cx = slot * col + slot / 2; val top = headH + rowH * row.toFloat()
                val today = i == cal.today
                if (today) { box.set(slot * col + dp(2), top + dp(1), slot * (col + 1) - dp(2), top + rowH - dp(1)); cv.drawRoundRect(box, dp(8).toFloat(), dp(8).toFloat(), back) }
                val alpha = if (inMonth[i]) 0xFF else 0x4D
                big.color = if (!today && dows[col] == 0) ROSE[0] else Color.WHITE
                big.alpha = alpha
                val l = cal.lunDay[i]
                small.color = when { today -> 0xCCFFFFFF.toInt(); !inMonth[i] -> DIM; l == 1 && Math.abs(cal.lunMonth[i]) == 1 -> ROSE[0]; l == 1 || l == 15 -> AMBER[0]; else -> DIM }
                if (!inMonth[i]) small.alpha = 0x4D
                if (wide) {
                    val bw = big.measureText(solar[i]); val sw = small.measureText(lunarText[i])
                    val x = cx - (bw + gap + sw) / 2; val base = top + rowH / 2f + big.textSize * 0.35f
                    cv.drawText(solar[i], x, base, big); cv.drawText(lunarText[i], x + bw + gap, base, small)
                } else if (two) {
                    cv.drawText(solar[i], cx, top + rowH * 0.5f, big)
                    cv.drawText(lunarText[i], cx, top + rowH - dp(4).toFloat(), small)
                } else cv.drawText(solar[i], cx, top + rowH / 2f + big.textSize * 0.35f, big)
            }
        }
    }
}
