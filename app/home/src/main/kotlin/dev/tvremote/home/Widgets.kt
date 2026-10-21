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
 *   calendar the month, today a filled circle, a lunar day only on its 1st and 15th ([Month], one View; the lunar days from [Lunar], once a day)
 * and, smaller, stacked in a column beside them:
 *   lunar   the lunar date, the next full moon or new month, the days to Tết ([Lunar], worked out here)
 *   timer   the sleep timer or bedtime and the time left (TV Setup's rules, through the same provider); none while neither is set
 *   note    the family's note (HomeConfig "note"), while there is one
 */
class Widgets(private val a: Activity, private val dp: (Number) -> Int) : LinearLayout(a) {
    /** What a card needs, gathered on a background thread. */
    class Facts(val ram: Pair<Long, Long>, val disk: Pair<Long, Long>, val ip: String, val eth: Boolean, val rssi: Int?, val up: Long,
                val adb: Boolean, val usage: JSONObject?, val icons: Map<String, Drawable>, val timer: JSONObject?, val moon: Moon?, val cpu: Float?, val cores: Int, val cal: Cal? = null, val wx: Weather.Forecast? = null,
                val link: Int? = null, val dns: String? = null, val adbPort: Int = 0)

    /** Today in the lunar calendar and the days until the next full moon (15th), new month (1st) and Tết. */
    class Moon(val day: Lunar.Day, val full: Int, val new: Int, val tet: Int)

    /** The month shown: its grid, today's cell, and today's lunar date (the title's). */
    class Cal(val grid: MonthGrid.Grid, val today: Int, val moon: Lunar.Day)

    companion object {
        const val SAMPLE_MS = 2000L
        private const val POINTS = 40                                  // 80 s of the network graph
        private val CYAN = intArrayOf(0xFF62D2FF.toInt(), 0xFF4F7CFF.toInt())
        private val VIOLET = intArrayOf(0xFFC59BFF.toInt(), 0xFF7B5CFF.toInt())
        private val GREEN = intArrayOf(0xFF6BE8A6.toInt(), 0xFF22B07D.toInt())
        private val AMBER = intArrayOf(0xFFFFD27A.toInt(), 0xFFFF8A4C.toInt())
        private val ROSE = intArrayOf(0xFFFF7A7A.toInt(), 0xFFE0457B.toInt())
        private const val DIM = 0xA6FFFFFF.toInt()
        private const val ACCENT = 0xFFFF453A.toInt()                  // the calendar's red: the month's name and today
        private val MEDIUM get() = Font.medium
        private val BOLD get() = Font.bold
        private val BIG = setOf("system", "usage", "calendar", "weather")
        const val BIG_H = 180                                          // dp: the cards' height, fixed: the favourites stay on screen
        private const val FULL_MIN = 180                              // dp: a big card in a column narrower than this shows its small form (the row of three big cards gives 190 to 260)

        private var calKey = 0; private var calMemo: Cal? = null

        /** The month of [now]: worked out once a day, the minute's refresh takes the memo. */
        fun month(now: Calendar, first: Int): Cal {
            val key = (now.get(Calendar.YEAR) * 12 + now.get(Calendar.MONTH)) * 40 + now.get(Calendar.DAY_OF_MONTH) + first * 100000000
            calMemo?.let { if (key == calKey) return it }
            val y = now.get(Calendar.YEAR); val m = now.get(Calendar.MONTH) + 1; val d = now.get(Calendar.DAY_OF_MONTH)
            val g = MonthGrid.of(y, m, first)
            return Cal(g, g.offset + d - 1, Lunar.of(d, m, y)).also { calKey = key; calMemo = it }
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
    private var spark: Spark? = null; private var speed: TextView? = null; private var speedUp: TextView? = null

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
        val link = if (rssi == null) null else try {
            @Suppress("DEPRECATION") (a.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo?.linkSpeed?.takeIf { it > 0 }
        } catch (e: Exception) { null }
        // Private DNS (DNS over TLS), as Settings → Network shows it: only a named server counts, "automatic" is the network's own
        val dns = try {
            if (Settings.Global.getString(a.contentResolver, "private_dns_mode") == "hostname") Settings.Global.getString(a.contentResolver, "private_dns_specifier")?.takeIf { it.isNotBlank() } else null
        } catch (e: Exception) { null }
        val adbPort = if (!adb) 0 else try {                            // ADB over the network (what the clients use), 0 = USB only
            (Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, "service.adb.tcp.port") as String).toIntOrNull()?.takeIf { it > 0 } ?: 0
        } catch (e: Throwable) { 0 }
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
            cpu?.optDouble("busy", -1.0)?.takeIf { it >= 0 }?.toFloat(), cpu?.optInt("cores") ?: 0, cal, wx, link, dns, adbPort)
    }

    /**
     * The cards in the order of [want], sized by [sizes] (HomeConfig "wsize"). [HomeConfig.pack] makes the columns: a big card is a
     * column, two small cards in a row stack in one, a small card alone stands in a narrow one; what does not fit waits. The height
     * is fixed so the favourites below stay in view. A column's width is its weight's share of the row.
     */
    fun show(want: List<String>, f: Facts, note: String, label: (String) -> String, sizes: JSONObject = JSONObject()) {
        removeAllViews(); spark = null; speed = null; speedUp = null
        val timerView = if ("timer" in want) timer(f.timer) else null
        val noteView = if (note.isNotEmpty()) pill(tile(AMBER, R.drawable.ic_message), note, "", 1).apply {   // the words are the card: large, no "Note" over them
            ((getChildAt(1) as LinearLayout).getChildAt(0) as TextView).apply { textSize = 16f; maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END }
        } else null
        val tall = layoutParams
        if (tall != null && tall.height != dp(BIG_H)) { tall.height = dp(BIG_H); layoutParams = tall }
        val keys = want.filter { (it != "timer" || timerView != null) && (it != "lunar" || f.moon != null) }   // a card with nothing to show takes no place
        val big = HomeConfig.bigs(JSONObject().put("wsize", sizes))
        val packed = HomeConfig.pack(keys, big, noteView != null)
        if (packed.cols.isEmpty()) { visibility = View.GONE; return }
        fun plain(k: String): View? = when (k) { "lunar" -> f.moon?.let { lunar(it) }; "timer" -> timerView; else -> noteView }
        val gap = dp(20)
        val room = (if (width > 0) width else a.resources.displayMetrics.widthPixels - dp(96)) - gap * (packed.cols.size - 1)
        val sum = maxOf(HomeConfig.FLOOR, packed.cols.sumOf { it.weight })
        packed.cols.forEachIndexed { i, col ->
            val w = room * col.weight / sum
            val box = LinearLayout(a).apply { orientation = VERTICAL }
            col.keys.forEachIndexed { j, k ->
                val alone = col.keys.size == 1                         // a lone card has the column's whole height
                val v: View = if (k in BIG) card().also { c ->
                    if (alone && k in big && w >= dp(FULL_MIN)) when (k) {
                        "system" -> system(c, f, w)
                        "calendar" -> calendar(c, f, w, dp(BIG_H) - dp(26) - dp(22))   // minus the card's padding, the title and its gap
                        "weather" -> weather(c, f, w, fixed = true)
                        else -> usage(c, f, label, w < dp(340))
                    } else {                                       // the small form: half the height, or a narrow column
                        c.setPadding(dp(16), dp(8), dp(16), dp(8))
                        when (k) {
                            "system" -> systemHalf(c, f, w - dp(32))
                            "calendar" -> calendarHalf(c, f, w - dp(32))
                            "weather" -> weatherHalf(c, f, w - dp(32))
                            else -> usageHalf(c, f, label)
                        }
                    }
                } else plain(k)!!.also { if (alone) stand(it) }
                box.addView(v, LayoutParams(-1, 0, 1f).apply { if (j > 0) topMargin = dp(12) })
            }
            addView(box, LayoutParams(w, -1).apply { if (i > 0) leftMargin = gap })
        }
        visibility = View.VISIBLE
    }

    /** Rings only: as many as fit, no title, no graph. */
    private fun systemHalf(card: LinearLayout, f: Facts, room: Int) {
        fun part(p: Pair<Long, Long>) = if (p.second > 0) p.first.toFloat() / p.second else 0f
        val all = ArrayList<View>()
        all += ring(part(f.ram), a.getString(R.string.w_ram), CYAN)
        f.cpu?.let { all += ring(it, a.getString(R.string.w_cpu), AMBER) }
        all += ring(part(f.disk), a.getString(R.string.w_disk), VIOLET)
        f.rssi?.let { r -> all += ring(((r + 100) * 2).coerceIn(0, 100) / 100f, a.getString(R.string.w_signal), GREEN) }
        val row = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        all.take((room / dp(78)).coerceAtLeast(1)).forEach { row.addView(it, LayoutParams(0, -2, 1f)) }
        card.gravity = Gravity.CENTER_VERTICAL
        card.addView(row, LayoutParams(-1, -2))
    }

    /** A small ring with its percent inside and its name under it. */
    private fun ring(part: Float, name: String, colors: IntArray) = LinearLayout(a).apply {
        orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        addView(FrameLayout(a).apply {
            addView(Ring(a, part, colors))
            addView(text("${(part * 100).toInt()}%", 13f, Color.WHITE).apply { typeface = Font.medium; gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(-1, -1))
        }, LayoutParams(dp(50), dp(50)))
        addView(text(name, 11f, Color.WHITE).apply { gravity = Gravity.CENTER }, LayoutParams(-1, -2).apply { topMargin = dp(2) })
    }

    /** Today as a date: the day in a square, the weekday and date, the lunar date under it (the month grid needs the large slot). */
    private fun calendarHalf(card: LinearLayout, f: Facts, room: Int) {
        val c = f.cal ?: return
        val r = a.resources
        val loc = resources.configuration.locales[0]
        val now = Date()
        val d = c.moon
        val month = r.getStringArray(R.array.lunar_month)[d.month - 1] + if (d.leap) a.getString(R.string.lunar_leap) else ""
        val dayWord = if (d.day <= 10) a.getString(R.string.lunar_day_early, d.day) else d.day.toString()
        val (can, chi) = Lunar.canChi(d.year)
        val lunar = a.getString(R.string.lunar_title, dayWord, month) +   // the year's name only where it fits whole
            if (room < dp(240)) "" else " · " + a.getString(R.string.lunar_year, r.getStringArray(R.array.lunar_can)[can], r.getStringArray(R.array.lunar_chi)[chi])
        val num =text(SimpleDateFormat("d", loc).format(now), 24f, Color.WHITE).apply { typeface = Font.medium; gravity = Gravity.CENTER }
        val row = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(tile(CYAN, null, num), LayoutParams(dp(52), dp(52)))
        val words = LinearLayout(a).apply { orientation = VERTICAL }
        words.addView(text(SimpleDateFormat("EEEE", loc).format(now), 16f, Color.WHITE).apply { typeface = Font.medium })
        words.addView(text(SimpleDateFormat("d MMMM yyyy", loc).format(now), 12f, DIM))
        words.addView(text(lunar, 12f, DIM))
        row.addView(words, LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
        card.gravity = Gravity.CENTER_VERTICAL
        card.addView(row, LayoutParams(-1, -2))
    }

    /** The sky now: the picture, the temperature, the place, the words and today's low and high; no days to come. */
    private fun weatherHalf(card: LinearLayout, f: Facts, room: Int) {
        val w = f.wx
        card.gravity = Gravity.CENTER_VERTICAL
        if (w == null) { card.addView(head(a.getString(R.string.w_weather), "")); card.addView(note(a.getString(R.string.wx_none))); return }
        val row = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val narrow = room < dp(240)                                    // no room for the picture beside the words: the words keep whole
        if (!narrow) row.addView(Sky(a, w.kind, w.night), LayoutParams(dp(52), dp(52)))
        row.addView(text("${w.temp}°", 30f, Color.WHITE).apply { typeface = Font.medium }, LayoutParams(-2, -2).apply { if (!narrow) leftMargin = dp(10) })
        val words = LinearLayout(a).apply { orientation = VERTICAL }
        words.addView(text(w.place.ifEmpty { a.getString(R.string.wx_near) }, 14f, Color.WHITE).apply { typeface = Font.medium; if (narrow) maxLines = 2 })   // a long city wraps rather than cuts
        words.addView(text(a.getString(kindWords(w.kind)), 12f, Color.WHITE))
        words.addView(text(a.getString(R.string.wx_hi_lo, w.days[0].max, w.days[0].min), 12f, DIM))
        row.addView(words, LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
        card.addView(row, LayoutParams(-1, -2))
    }

    /** The two most used apps side by side, with their limits; no columns of the days. */
    private fun usageHalf(card: LinearLayout, f: Facts, label: (String) -> String) {
        val u = f.usage
        val week = u?.optJSONArray("week")
        val days = u?.optJSONArray("days")
        var total = 0L
        if (days != null) for (i in 0 until days.length()) total += days.optLong(i)
        card.addView(head(a.getString(R.string.w_week).substringBefore(" · "), if (total > 0) span(total * 1000) else ""))
        if (u == null) { card.addView(note(a.getString(R.string.w_need_setup))); return }
        val limits = HashMap<String, JSONObject>()
        u.optJSONArray("limits")?.let { l -> for (i in 0 until l.length()) l.optJSONObject(i)?.let { limits[it.optString("pkg")] = it } }
        val rows = ArrayList<Pair<String, Long>>()
        if (week != null) for (i in 0 until minOf(2, week.length())) week.getJSONObject(i).let { rows += it.optString("pkg") to it.optLong("sec") }
        if (rows.isEmpty()) { card.addView(note(a.getString(R.string.w_none))); return }
        val max = rows.maxOf { it.second }.coerceAtLeast(1)
        val line = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        rows.forEachIndexed { i, (p, sec) -> line.addView(appRow(p, label(p), f.icons[p], sec, sec.toFloat() / max, limits[p]), LayoutParams(0, -2, 1f).apply { if (i > 0) leftMargin = dp(18) }) }
        card.addView(line, LayoutParams(-1, 0, 1f))
    }

    private fun lunar(m: Moon): View {
        val r = a.resources
        val d = m.day
        val date = a.getString(R.string.lunar_short, d.day, d.month) + if (d.leap) a.getString(R.string.lunar_leap) else ""   // "12 tháng 9": the month's name and the year made it wrap
        val near = when {
            d.month == 1 && d.day <= 3 && !d.leap -> a.getString(R.string.l_tet_now)
            m.full == 0 -> a.getString(R.string.l_full_today)
            m.new == 0 -> a.getString(R.string.l_new_today)
            m.full in 1 until m.new || m.new < 0 -> a.getString(R.string.l_full_in, m.full)
            else -> a.getString(R.string.l_new_in, m.new)
        }
        val tetNow = d.month == 1 && d.day <= 3 && !d.leap
        val sub = if (m.tet > 0 && !tetNow) near + "\n" + a.getString(R.string.l_tet_in, m.tet) else near   // a line each: "·" was left hanging at a line's end
        // the whole card counts the days in columns instead: "3 ngày / tới Rằm", "108 ngày / tới Tết" (the line wrapped and left the right empty)
        val counts = ArrayList<Pair<Int, Int>>()
        val today = tetNow || m.full == 0 || m.new == 0
        if (!today) counts += if (m.full in 1 until m.new || m.new < 0) m.full to R.string.l_to_full else m.new to R.string.l_to_new
        if (m.tet > 0 && !tetNow) counts += m.tet to R.string.l_to_tet
        val t = text(d.day.toString(), 19f, Color.WHITE).apply { typeface = Font.medium; gravity = Gravity.CENTER }
        val (can, chi) = Lunar.canChi(d.year)
        val year = a.getString(R.string.lunar_year, r.getStringArray(R.array.lunar_can)[can], r.getStringArray(R.array.lunar_chi)[chi])
        return pill(tile(ROSE, null, t), date, sub, 3).apply { tag = Night((d.day - 0.5f) / 29.53f, year, if (today) near else null, counts) }   // a whole card: the moon and the year fill its right
    }

    /** What a standing lunar card shows on the right of its day: tonight's moon and the year's name. */
    private class Night(val phase: Float, val year: String, val line: String?, val counts: List<Pair<Int, Int>>)

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
        (p.tag as? Night)?.let { sky ->                                // the lunar card: its day, the date and the year, the moon on the right
            p.removeView(square)
            val title = words.getChildAt(0) as TextView
            words.removeView(title)
            p.addView(LinearLayout(a).apply {
                orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(square, LayoutParams(dp(56), dp(56)))
                addView(LinearLayout(a).apply {
                    orientation = VERTICAL
                    addView(title.apply { textSize = 18f; maxLines = 1 })
                    addView(text(sky.year, 13f, DIM), LayoutParams(-2, -2).apply { topMargin = dp(2) })
                }, LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
                addView(MoonFace(a, sky.phase), LayoutParams(dp(44), dp(44)))
            }, 0, LayoutParams(-1, -2))
        }
        words.layoutParams = LayoutParams(-1, -2).apply { topMargin = dp(14) }
        (p.tag as? Night)?.takeIf { it.counts.isNotEmpty() }?.let { n ->
            val sub = words.getChildAt(0) as? TextView
            if (n.line != null) sub?.text = n.line else sub?.let { words.removeView(it) }   // "Hôm nay rằm" stays a line; counts go in columns
            words.addView(LinearLayout(a).apply {
                orientation = HORIZONTAL
                n.counts.forEach { (days, label) ->
                    addView(LinearLayout(a).apply {
                        orientation = VERTICAL
                        addView(text(a.resources.getQuantityString(R.plurals.l_days, days, days), 17f, Color.WHITE).apply { typeface = Font.medium })
                        addView(text(a.getString(label), 12f, DIM))
                    }, LayoutParams(0, -2, 1f))
                }
            }, LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        if (p.tag !is Night) (words.getChildAt(0) as TextView).apply { textSize = 18f; maxLines = 4 }       // a narrow column wraps a long title, never cuts it
        (words.getChildAt(if (p.tag is Night) 0 else 1) as? TextView)?.apply { textSize = 14f; maxLines = 2; setPadding(0, dp(3), 0, 0) }
    }

    /** One of the small cards: a coloured square, a title, a line under it ([lines] of it). */
    private fun pill(square: View, title: String, sub: String, lines: Int) = LinearLayout(a).apply {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        Frost.on(this, dp(18).toFloat(), dp(1))
        setPadding(dp(12), dp(8), dp(14), dp(8))
        addView(square, LayoutParams(dp(40), dp(40)))
        addView(LinearLayout(a).apply {
            orientation = VERTICAL
            addView(text(title, 14f, Color.WHITE).apply { typeface = Font.medium; maxLines = 2 })   // a narrow column wraps, never cuts
            if (sub.isNotEmpty()) addView(text(sub, 12f, DIM).apply { maxLines = maxOf(lines, 2) })
        }, LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
    }

    /** One point of the network graph: bytes since the last one, all the TV's apps together. Cheap, on the main thread. */
    fun sample() {
        if (spark == null && speed == null) return
        val rx = TrafficStats.getTotalRxBytes(); val tx = TrafficStats.getTotalTxBytes(); val now = SystemClock.elapsedRealtime()
        if (rx < 0 || tx < 0) return                                   // the device does not count
        if (lastRx >= 0 && now > lastAt) {
            val secs = (now - lastAt) / 1000f
            System.arraycopy(down, 1, down, 0, POINTS - 1); down[POINTS - 1] = ((rx - lastRx).coerceAtLeast(0) / secs)
            System.arraycopy(up, 1, up, 0, POINTS - 1); up[POINTS - 1] = ((tx - lastTx).coerceAtLeast(0) / secs)
            val t = if (spark != null) "↓ ${rate(down[POINTS - 1])}   ↑ ${rate(up[POINTS - 1])}" else "↓ " + rate(down[POINTS - 1])
            if (speed?.text?.toString() != t) speed?.text = t
            val u = "↑ " + rate(up[POINTS - 1])
            if (speedUp != null && speedUp?.text?.toString() != u) speedUp?.text = u
            spark?.invalidate()
        }
        lastRx = rx; lastTx = tx; lastAt = now
    }

    /**
     * The TV at a glance: a grid of figures, two rows of 2 to 4 (as many as [width] holds, the most telling first), each a line
     * icon, the figure large and its name small under it, a thin bar under a share. Colour only where it says something: the
     * Private DNS a filter is set (green), ADB on (amber). The model, Android and the CPU in the title's right.
     */
    private fun system(card: LinearLayout, f: Facts, width: Int) {
        val room = width - dp(40)
        val gb = 1024.0 * 1024 * 1024
        fun part(p: Pair<Long, Long>) = if (p.second > 0) p.first.toFloat() / p.second else 0f
        fun pct(x: Float) = "${(x * 100).toInt()}%"
        val cores = f.cores.takeIf { it > 0 } ?: Runtime.getRuntime().availableProcessors()
        val ghz = maxGhz()
        val cpu = Stat(R.drawable.ic_cpu, f.cpu?.let(::pct) ?: ghz?.let { "%.2f GHz".format(it) } ?: "—",
            a.getString(R.string.w_cpu) + " · " + a.getString(R.string.w_cpu_cores, cores), f.cpu)
        val ram = Stat(R.drawable.ic_memory, pct(part(f.ram)), "%s %.1f/%.1fG".format(a.getString(R.string.w_ram), f.ram.first / gb, f.ram.second / gb), part(f.ram))
        val disk = Stat(R.drawable.ic_drive, pct(part(f.disk)), "%s %.0f/%.0fG".format(a.getString(R.string.w_disk), f.disk.first / gb, f.disk.second / gb), part(f.disk))
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
            else -> listOf(listOf(Stat(ram.icon, ram.value, a.getString(R.string.w_ram), ram.bar), Stat(disk.icon, disk.value, a.getString(R.string.w_disk), disk.bar)),
                listOf(ip, adb(false)))                              // narrow: the names without the sizes
        }
        // each row shares its width on its own: alike when every figure fits its equal part (CPU, RAM, disk), else by what each
        // needs (an address is longer than "ON"); as many columns as fit once [Shrink] has taken the figures down to 15 sp
        val big = Paint().apply { textSize = 19f * sp; typeface = Font.medium }
        val small = Paint().apply { typeface = Font.regular; textSize = 10f * sp; letterSpacing = 0.08f }
        val k0 = 15f / 19
        fun needs(row: List<Stat>, cols: Int) = FloatArray(cols) { k -> (row.getOrNull(k)?.let { maxOf(big.measureText(if (it.live) "↓ 999 KB/s" else it.value), small.measureText(it.label.uppercase())) * k0 } ?: 0f) + dp(29) }
        fun fits(rows: List<List<Stat>>, c: Int) = rows.all { needs(it, c).sum() + dp(10) * (c - 1) <= room }
        val cols = (4 downTo 3).firstOrNull { fits(layout(it), it) } ?: 2
        val rows = layout(cols)
        val about = listOfNotNull(android.os.Build.MODEL.takeIf { cols >= 3 }, "Android " + android.os.Build.VERSION.RELEASE,
            if (cols >= 4 && ghz != null) "$cores×%.1fGHz".format(ghz) else null).joinToString(" · ")
        card.addView(head(a.getString(R.string.w_system), about))
        val grid = LinearLayout(a).apply { orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL }   // the rows in the middle of the card, not stuck under the title
        rows.forEachIndexed { r, row ->
            val line = LinearLayout(a).apply { orientation = HORIZONTAL }
            val need = needs(row, cols); val gaps = dp(10) * (cols - 1)
            val alike = need.max() * cols + gaps <= room
            val whole = need.sum() + gaps <= room                    // each its least, the rest shared alike; else all by need
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
    private class Stat(val icon: Int, val value: String, val label: String, val bar: Float? = null, val tint: Int? = null, val live: Boolean = false)

    private fun stat(s: Stat): View = LinearLayout(a).apply {
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
        val l = Shrink(a, 10f, 8f).apply { text = s.label.uppercase(); setTextColor(DIM); maxLines = 1; letterSpacing = 0.08f }
        col.addView(l)
        if (s.live) { speed = v; speedUp = l.apply { letterSpacing = 0f } }
        s.bar?.let { col.addView(Bar(a, it, CYAN), LayoutParams(-1, dp(3)).apply { topMargin = dp(6); rightMargin = dp(8) }) }
        addView(col, LayoutParams(0, -2, 1f).apply { leftMargin = dp(9) })
    }

    /** A line that gets smaller (down to [least] sp) rather than cut: "192.168.100.154" whole in a narrow column. */
    private class Shrink(c: Context, private val most: Float, private val least: Float) : TextView(c) {
        init { typeface = Font.regular }
        override fun onMeasure(w: Int, h: Int) {
            val room = MeasureSpec.getSize(w) - paddingLeft - paddingRight
            if (MeasureSpec.getMode(w) != MeasureSpec.UNSPECIFIED && room > 0) {
                var size = most
                textSize = size
                while (size > least && paint.measureText(text.toString()) > room) { size -= 1f; textSize = size }
                ellipsize = TextUtils.TruncateAt.END                 // still too long at the least size: cut, as a last resort
            }
            super.onMeasure(w, h)
        }
    }

    /** The moon at [phase] (0 new, 0.5 full, 1 new again), lit on the right while it grows: a half disc and the terminator's half ellipse. */
    private class MoonFace(c: Context, private val phase: Float) : View(c) {
        private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
        private val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF6E7C1.toInt() }
        private val path = android.graphics.Path(); private val box = RectF()
        override fun onDraw(cv: Canvas) {
            val r = minOf(width, height) / 2f; val cx = width / 2f; val cy = height / 2f
            cv.drawCircle(cx, cy, r, dark)
            val p = phase.coerceIn(0f, 1f); val waxing = p <= 0.5f
            val q = if (waxing) p else 1f - p                         // waning: the same shape, mirrored
            val w = r * Math.cos(2 * Math.PI * q).toFloat()          // the terminator's half-width: r at new, 0 at the quarter, -r at full
            path.reset()
            box.set(cx - r, cy - r, cx + r, cy + r); path.arcTo(box, -90f, 180f)
            box.set(cx - Math.abs(w), cy - r, cx + Math.abs(w), cy + r); path.arcTo(box, 90f, if (w > 0) -180f else 180f)
            path.close()
            cv.save(); if (!waxing) cv.scale(-1f, 1f, cx, cy)
            cv.drawPath(path, lit); cv.restore()
        }
    }

    /** A Private DNS host by its service's name when it is a well known one ("dns.adguard-dns.com" → AdGuard), else the host. */
    private fun dnsName(host: String): String {
        val h = host.lowercase()
        return listOf("adguard" to "AdGuard", "nextdns" to "NextDNS", "cloudflare" to "Cloudflare", "one.one.one.one" to "Cloudflare", "quad9" to "Quad9",
            "google" to "Google", "controld" to "Control D", "mullvad" to "Mullvad", "opendns" to "OpenDNS", "cleanbrowsing" to "CleanBrowsing")
            .firstOrNull { h.contains(it.first) }?.second ?: host.removePrefix("dns.")
    }

    /** The fastest core's top speed, read once (the kernel's own file; some TVs hide it, an emulator makes it up: null). */
    private fun maxGhz(): Float? = ghzMemo ?: try {
        (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { java.io.File("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq").takeIf { f -> f.canRead() }?.readText()?.trim()?.toLongOrNull() }
            .filter { it >= 200_000 }.maxOrNull()?.let { it / 1_000_000f }?.also { ghzMemo = it }   // kHz; an emulator says 2: unknown, not "0.0GHz"
    } catch (e: Exception) { null }
    private var ghzMemo: Float? = null

    /** The month: the title and the lunar month and year of today, then one View drawing the weekdays and every day (solar only: a lunar line under each was too busy). */
    private fun calendar(card: LinearLayout, f: Facts, width: Int, maxH: Int = Int.MAX_VALUE) {
        val c = f.cal ?: return
        val r = a.resources
        val (can, chi) = Lunar.canChi(c.moon.year)
        val month = r.getStringArray(R.array.lunar_month)[c.moon.month - 1] + if (c.moon.leap) a.getString(R.string.lunar_leap) else ""
        val year = a.getString(R.string.lunar_year, r.getStringArray(R.array.lunar_can)[can], r.getStringArray(R.array.lunar_chi)[chi])
        val lunar = a.getString(R.string.cal_lunar, month, year)
        val inner = width - dp(40)
        val title = LinearLayout(a).apply { orientation = HORIZONTAL; gravity = Gravity.BOTTOM }
        title.addView(text(r.getStringArray(R.array.cal_month)[c.grid.month - 1], 16f, ACCENT).apply { typeface = BOLD })
        title.addView(text(" " + c.grid.year, 16f, 0x80FFFFFF.toInt()).apply { typeface = MEDIUM })
        if (inner >= dp(320)) title.addView(text(lunar, 12f, 0x99FFFFFF.toInt()).apply { gravity = Gravity.END }, LayoutParams(0, -2, 1f))
        val view = Month(a, c, Calendar.getInstance(resources.configuration.locales[0]).firstDayOfWeek, inner / 7f, maxH - dp(8))
        card.addView(title)
        // in a fixed-height card the month takes all that is left under the title, and the card gives it some of its padding:
        // six weeks of a long month need every dp there is
        val fixed = maxH < Int.MAX_VALUE
        if (fixed) card.setPadding(card.paddingLeft, dp(10), card.paddingRight, dp(8))
        card.addView(view, (if (fixed) LayoutParams(-1, 0, 1f) else LayoutParams(-1, view.wanted)).apply { topMargin = dp(if (fixed) 3 else 6) })
    }

    /** The sky now with today's low and high, and the next days when the card is wide enough; without an answer yet, a short line. */
    /** [fixed]: in a card of the layouts' fixed height, where the days under "now" are drawn tighter or their temperatures fell off the bottom. */
    private fun weather(card: LinearLayout, f: Facts, width: Int, fixed: Boolean = false) {
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
        val temp = text("${w.temp}°", if (fixed) 28f else 30f, Color.WHITE).apply { typeface = Font.medium }
        val sky = text(a.getString(kindWords(w.kind)), 13f, Color.WHITE)
        val today = w.days[0]
        val hiLo = text(a.getString(R.string.wx_hi_lo, today.max, today.min), 12f, DIM)
        // the sky and today's high / low beside the temperature, two short lines as tall as its digits, when they fit across:
        // one row instead of three leaves the next days room under it
        val lines = maxOf(sky.paint.measureText(sky.text.toString()), hiLo.paint.measureText(hiLo.text.toString()))
        val tempW = temp.paint.measureText(temp.text.toString())
        val beside = dp(44) + dp(10) + tempW + dp(10) + lines <= width - dp(40)   // with a smaller picture of the sky
        val icon = if (beside) 44 else 56
        if (!beside && hiLo.paint.measureText(hiLo.text.toString()) > width - dp(40) - dp(icon + 12))   // a narrow card cut it to "Cao 32° · Th…"
            hiLo.text = "↑${today.max}°  ↓${today.min}°"
        val nowW = (dp(icon) + dp(if (beside) 10 else 12) + (if (beside) tempW + dp(10) + lines else maxOf(tempW, lines))).toInt()
        now.addView(Sky(a, w.kind, w.night), LayoutParams(dp(icon), dp(icon)))
        val words = LinearLayout(a).apply { orientation = if (beside) HORIZONTAL else VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        words.addView(temp)
        if (beside) words.addView(LinearLayout(a).apply { orientation = VERTICAL; addView(sky); addView(hiLo) }, LayoutParams(-2, -2).apply { leftMargin = dp(10) })
        else { words.addView(sky); words.addView(hiLo) }
        now.addView(words, LayoutParams(-2, -2).apply { leftMargin = dp(if (beside) 10 else 12) })
        body.addView(now, LayoutParams(0, -2, 1f))
        val next = w.days.drop(1)
        val room = width - dp(40) - nowW - dp(24)                             // beside "now" when wide (a day at least 52 dp), else a row under it
        val side = room >= dp(52) * next.size
        val dayW = if (side) minOf(dp(62), room / maxOf(1, next.size)) else 0
        if (next.isNotEmpty() && (side || width - dp(40) >= dp(62) * next.size)) {
            val days = LinearLayout(a).apply { orientation = HORIZONTAL }
            val names = a.resources.getStringArray(R.array.cal_dow)
            next.forEachIndexed { i, d ->
                val col = LinearLayout(a).apply { orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
                val dow = try { Calendar.getInstance().apply { time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d.date)!! }.get(Calendar.DAY_OF_WEEK) - 1 } catch (e: Exception) { -1 }
                col.addView(text(names.getOrElse(dow) { "" }, 12f, DIM).apply { gravity = Gravity.CENTER })
                col.addView(Sky(a, d.kind, false), LayoutParams(dp(if (fixed && !beside) 22 else 28), dp(if (fixed && !beside) 22 else 28)).apply { topMargin = dp(if (fixed && !beside) 0 else 2) })
                col.addView(text("${d.max}° ${d.min}°", 12f, Color.WHITE).apply { gravity = Gravity.CENTER })
                days.addView(col, if (side) LayoutParams(dayW, -2).apply { if (i > 0) leftMargin = dp(2) } else LayoutParams(0, -2, 1f))
            }
            if (side) body.addView(days, LayoutParams(-2, -2).apply { leftMargin = dp(8) })
            else {
                // "now" over the next days, the two together in the middle of what is left under the title (not stuck to it)
                val both = LinearLayout(a).apply { orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL }
                both.addView(body, LayoutParams(-1, -2))
                both.addView(days, LayoutParams(-1, -2).apply { topMargin = dp(if (fixed && !beside) 2 else 8) })
                card.addView(both, if (fixed) LayoutParams(-1, 0, 1f) else LayoutParams(-1, -2).apply { topMargin = dp(6) })
                return
            }
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
        line.addView(text(span(sec * 1000), 13f, Color.WHITE).apply { typeface = Font.medium })
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
            addView(text("${(part * 100).toInt()}%", 15f, Color.WHITE).apply { typeface = Font.medium; gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(-1, -1))
        }, LayoutParams(dp(62), dp(62)))
        addView(text(name, 12f, Color.WHITE).apply { gravity = Gravity.CENTER }, LayoutParams(dp(84), -2).apply { topMargin = dp(5) })
        addView(text(detail, 11f, DIM).apply { gravity = Gravity.CENTER }, LayoutParams(dp(84), -2))
    }

    private fun chips(items: List<Pair<String, Int>>) = Fit(a).apply {
        orientation = HORIZONTAL
        items.forEachIndexed { i, (t, c) ->
            addView(text(t, 12f, c).apply {
                background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(0x1FFFFFFF) }
                setPadding(dp(9), dp(2), dp(9), dp(2))
            }, LayoutParams(-2, -2).apply { if (i > 0) leftMargin = dp(6) })
        }
    }

    /** A row that leaves out the chips after the first that do not fit whole: a narrow card showed a sliver of "ADB". */
    private class Fit(c: Context) : LinearLayout(c) {
        override fun onMeasure(w: Int, h: Int) {
            val room = MeasureSpec.getSize(w) - paddingLeft - paddingRight
            var used = 0
            for (i in 0 until childCount) {
                val v = getChildAt(i)
                v.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), h)
                val need = v.measuredWidth + ((v.layoutParams as? MarginLayoutParams)?.leftMargin ?: 0)
                val fits = i == 0 || MeasureSpec.getMode(w) == MeasureSpec.UNSPECIFIED || used + need <= room
                if (fits) used += need
                val vis = if (fits) View.VISIBLE else View.GONE
                if (v.visibility != vis) v.visibility = vis               // the same answer next time: no layout loop
            }
            super.onMeasure(w, h)
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
        Frost.on(this, dp(22).toFloat(), dp(1))                         // the dock band's glass, its blurred photo too
        setPadding(dp(20), dp(12), dp(20), dp(14))
    }

    private fun head(left: String, right: String) = LinearLayout(a).apply {
        orientation = HORIZONTAL
        addView(text(left.uppercase(), 11f, 0xB3FFFFFF.toInt()).apply { letterSpacing = 0.12f }, LayoutParams(0, -2, 1f))
        if (right.isNotEmpty()) addView(text(right, 12f, 0xB3FFFFFF.toInt()))
    }

    private fun note(t: String) = text(t, 13f, 0xB3FFFFFF.toInt()).apply { setPadding(0, dp(10), 0, 0); maxLines = 2 }

    private fun text(t: String, size: Float, color: Int) = Font.on(TextView(a)).apply {
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
        private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Font.regular; textSize = 11 * sp; textAlign = Paint.Align.CENTER; color = DIM }
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
     * The weekdays and the days in ONE view, as the system calendars draw them: dim weekday initials, plain numbers, today a filled
     * circle, the days of the neighbouring months only a ghost. A lunar day shows only where it means something: the 1st (with its
     * month, "1/10"; the Tết rose) and the 15th, small under the number; a slot too thin for it keeps the numbers alone.
     * Paints made once: drawing allocates nothing.
     */
    private inner class Month(c: Context, val cal: Cal, first: Int, slot: Float, maxH: Int) : View(c) {
        private val tiny = slot < dp(30)
        private val headH = dp(22)
        private val rowMax = dp(30)
        private var rowH = minOf(rowMax, ((maxH - headH) / cal.grid.rows).coerceAtLeast(dp(16)))   // a six-week month in the fixed-height slot: thinner rows
        val wanted = headH + rowH * cal.grid.rows
        private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Font.regular; textSize = (if (tiny) 13f else 14f) * sp }   // left-aligned: each number is placed by its own ink
        private val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = (if (tiny) 10f else 11f) * sp; color = 0x8CFFFFFF.toInt(); typeface = MEDIUM }
        private val back = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
        private val dows = IntArray(7) { (first - 1 + it) % 7 }            // 0 = Sunday
        private val names = a.resources.getStringArray(R.array.cal_dow_short)
        private val n = cal.grid.rows * 7
        private val inMonth = BooleanArray(n) { it >= cal.grid.offset && it < cal.grid.offset + cal.grid.days }
        private val solar = Array(n) { "" }
        private val ink = android.graphics.Rect()
        init {
            val k = Calendar.getInstance().apply { clear(); set(cal.grid.sy, cal.grid.sm - 1, cal.grid.sd) }
            for (i in 0 until n) { solar[i] = k.get(Calendar.DAY_OF_MONTH).toString(); k.add(Calendar.DAY_OF_MONTH, 1) }
        }
        override fun onMeasure(w: Int, h: Int) =
            setMeasuredDimension(MeasureSpec.getSize(w), if (MeasureSpec.getMode(h) == MeasureSpec.EXACTLY) MeasureSpec.getSize(h) else wanted)
        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            rowH = minOf(rowMax, ((h - headH) / cal.grid.rows).coerceAtLeast(dp(16)))
        }
        override fun onDraw(cv: Canvas) {
            val slot = width / 7f
            for (col in 0 until 7) {
                head.color = if (dows[col] == 0) 0xB3FFB4B4.toInt() else 0x8CFFFFFF.toInt()   // Sunday: a faint rose, not a shout
                cv.drawText(names[dows[col]], slot * col + slot / 2, headH - dp(8).toFloat(), head)
            }
            val r = minOf(rowH / 2f - dp(1), slot / 2f - dp(2))
            for (i in 0 until n) {
                val cx = slot * (i % 7) + slot / 2; val cy = headH + rowH * (i / 7) + rowH / 2f
                val today = i == cal.today
                big.typeface = if (today) BOLD else MEDIUM
                big.color = if (today) Color.WHITE else if (inMonth[i]) 0xF2FFFFFF.toInt() else 0x30FFFFFF
                // the digits' own ink in the middle of the cell, not the run's advance: "21" drawn centred leans right ("1" has room on its left)
                big.getTextBounds(solar[i], 0, solar[i].length, ink)
                if (today) cv.drawCircle(cx, cy, r, back)
                cv.drawText(solar[i], cx - ink.exactCenterX(), cy - ink.exactCenterY(), big)
            }
        }
    }
}
