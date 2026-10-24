package dev.tvremote.home.widgets

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Market card's data: gold (SJC and ring gold 9999, VND per lượng), the bank's exchange rates (VND) and optionally Bitcoin, each part
 * fetched straight from a public source named in the signed list ([Sources]) on a thread of its own and kept in prefs with its time.
 * A part that fails keeps its last value (the card shows the time of the last update); nothing here is shown as an error.
 */
object Market {
    /** [buy] is what the bank pays (bank transfer), [sell] what it asks; 0 = the source has no figure. */
    class Rate(val code: String, val buy: Double, val sell: Double)
    /** VND per lượng (37.5 g): the shops list per chỉ (a tenth of it), so the figures are the ones people say aloud ("141,7 triệu"). */
    class Gold(val src: String, val sjcBuy: Long, val sjcSell: Long, val ringBuy: Long, val ringSell: Long)
    class Btc(val usd: Double, val pct: Double)
    class Snap(val rates: List<Rate>, val gold: Gold?, val btc: Btc?, val ratesAt: Long, val goldAt: Long, val btcAt: Long) {
        /** When the newest part came, for the "updated 14:05" line. */
        val at get() = maxOf(ratesAt, goldAt, btcAt)
        val empty get() = rates.isEmpty() && gold == null && btc == null
    }

    const val FRESH_MS = 20 * 60_000L                                  // a part is asked again after this, while the home is shown
    const val RETRY_MS = 5 * 60_000L                                   // after a failure (the bank asks for one request in 5 minutes)
    val CODES = listOf("USD", "EUR", "JPY", "KRW", "CNY", "GBP")       // in the order shown

    private fun num(s: String?): Double = s?.trim()?.replace(",", "")?.toDoubleOrNull() ?: 0.0

    /** Vietcombank's `<Exrate CurrencyCode="USD" Buy=".." Transfer="25,640.00" Sell=".." />` list: the codes of [CODES] that have a sell figure. */
    fun parseVcb(xml: String): List<Rate> {
        val row = Regex("<Exrate\\s+([^>]*?)/?>")
        val attr = Regex("(\\w+)=\"([^\"]*)\"")
        val got = HashMap<String, Rate>()
        for (m in row.findAll(xml)) {
            val a = attr.findAll(m.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
            val code = a["CurrencyCode"] ?: continue
            val sell = num(a["Sell"])
            if (code in CODES && sell > 0) got[code] = Rate(code, num(a["Transfer"]), sell)
        }
        return CODES.mapNotNull { got[it] }
    }

    /** BTMC's list: rows are `@n_<k>` (name) with `@pb_<k>` (what the shop pays) and `@ps_<k>` (what it asks), per chỉ. SJC bars and the plain 999.9 ring. */
    fun parseBtmc(json: String, name: String = "BTMC"): Gold? = try {
        val rows = JSONObject(json).getJSONObject("DataList").getJSONArray("Data")
        fun find(match: (String) -> Boolean): Pair<Long, Long>? {
            for (i in 0 until rows.length()) {
                val r = rows.getJSONObject(i); val k = r.optString("@row")
                val n = r.optString("@n_$k").uppercase()
                if (!match(n) || !r.optString("@h_$k").startsWith("999.9")) continue
                val b = r.optString("@pb_$k").toLongOrNull() ?: continue; val s = r.optString("@ps_$k").toLongOrNull() ?: continue
                if (b > 0 && s > 0) return b * 10 to s * 10
            }
            return null
        }
        val sjc = find { it.startsWith("VÀNG MIẾNG SJC") }
        val ring = find { it.startsWith("NHẪN TRÒN TRƠN") }
        if (sjc == null || ring == null) null else Gold(name, sjc.first, sjc.second, ring.first, ring.second)
    } catch (e: Exception) { null }

    /** PNJ's list (thousands of VND per chỉ): "SJC" bars and "N24K", the plain 999.9 ring. */
    fun parsePnj(json: String, name: String = "PNJ"): Gold? = try {
        val rows = JSONObject(json).getJSONArray("data")
        fun find(id: String): Pair<Long, Long>? {
            for (i in 0 until rows.length()) {
                val r = rows.getJSONObject(i)
                if (r.optString("masp") != id) continue
                val b = r.optLong("giamua"); val s = r.optLong("giaban")
                if (b > 0 && s > 0) return b * 10_000 to s * 10_000
            }
            return null
        }
        val sjc = find("SJC"); val ring = find("N24K")
        if (sjc == null || ring == null) null else Gold(name, sjc.first, sjc.second, ring.first, ring.second)
    } catch (e: Exception) { null }

    /** Binance's 24 h ticker (`lastPrice`, `priceChangePercent`). */
    fun parseBinance(json: String): Btc? = try {
        val o = JSONObject(json)
        o.optString("lastPrice").toDoubleOrNull()?.takeIf { it > 0 }?.let { Btc(it, o.optString("priceChangePercent").toDoubleOrNull() ?: 0.0) }
    } catch (e: Exception) { null }

    fun toJson(s: Snap): JSONObject = JSONObject()
        .put("rates", JSONArray(s.rates.map { JSONObject().put("c", it.code).put("b", it.buy).put("s", it.sell) })).put("ratesAt", s.ratesAt)
        .put("goldAt", s.goldAt).put("btcAt", s.btcAt)
        .also { o ->
            s.gold?.let { o.put("gold", JSONObject().put("src", it.src).put("sb", it.sjcBuy).put("ss", it.sjcSell).put("rb", it.ringBuy).put("rs", it.ringSell)) }
            s.btc?.let { o.put("btc", JSONObject().put("usd", it.usd).put("pct", it.pct)) }
        }

    fun fromJson(s: String): Snap? = try {
        val o = JSONObject(s)
        val a = o.optJSONArray("rates")
        val g = o.optJSONObject("gold"); val b = o.optJSONObject("btc")
        Snap((0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it).let { r -> Rate(r.getString("c"), r.optDouble("b"), r.optDouble("s")) } },
            g?.let { Gold(it.optString("src"), it.getLong("sb"), it.getLong("ss"), it.getLong("rb"), it.getLong("rs")) },
            b?.let { Btc(it.getDouble("usd"), it.optDouble("pct")) }, o.optLong("ratesAt"), o.optLong("goldAt"), o.optLong("btcAt"))
    } catch (e: Exception) { null }

    /** The value to compare with: the last one seen on an earlier day than [today] (kept as [ref] until a newer day replaces it). */
    fun refStep(day: String, cur: Double?, ref: Double?, today: String): Double? = if (day != today && cur != null) cur else ref

    /** Percent change of [v] since yesterday's last value; null until one exists (the first day has nothing to compare with). */
    fun trend(c: Context, key: String, v: Double, today: String = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())): Double? {
        val p = prefs(c)
        val day = p.getString("d_$key", "") ?: ""
        val cur = p.getString("c_$key", null)?.toDoubleOrNull(); val old = p.getString("r_$key", null)?.toDoubleOrNull()
        val ref = refStep(day, cur, old, today)
        if (day != today || cur != v || ref != old) p.edit().putString("d_$key", today).putString("c_$key", v.toString()).also { e -> ref?.let { e.putString("r_$key", it.toString()) } }.apply()
        return if (ref != null && ref > 0 && v > 0) (v - ref) / ref * 100 else null
    }

    // ---- the network and the prefs, the thin part ----

    private fun prefs(c: Context) = c.getSharedPreferences("market", Context.MODE_PRIVATE)
    fun cached(c: Context): Snap? = prefs(c).getString("snap", null)?.let { fromJson(it) }

    /** True when something should be asked again now: nothing yet, or a part older than [FRESH_MS], and not just after a failure. */
    fun due(c: Context, btc: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        val tried = prefs(c).getLong("tried", 0)
        if (now - tried < RETRY_MS && now >= tried) return false
        val s = cached(c) ?: return true
        return now - s.ratesAt > FRESH_MS || now - s.goldAt > FRESH_MS || (btc && now - s.btcAt > FRESH_MS)
    }

    private var busy = false

    /** Asks the sources (a thread of its own); [done] is called on it when something changed. A part that fails keeps its last value. */
    fun refresh(c: Context, btc: Boolean, done: () -> Unit) {
        synchronized(this) { if (busy) return; busy = true }
        Thread {
            try {
                val ctx = c.applicationContext; val now = System.currentTimeMillis(); val p = prefs(ctx)
                val src = Sources.now
                val old = cached(ctx)
                val rates = src.rates?.let { s -> Net.text(s.url)?.let { parseVcb(it) } }?.takeIf { it.isNotEmpty() }
                var gold: Gold? = null
                for (g in src.gold) { gold = Net.text(g.url)?.let { if (g.kind == "btmc") parseBtmc(it, g.name) else parsePnj(it, g.name) }; if (gold != null) break }
                val coin = if (btc) src.crypto?.let { s -> Net.text(s.url)?.let { parseBinance(it) } } else null
                val snap = Snap(rates ?: old?.rates.orEmpty(), gold ?: old?.gold, coin ?: old?.btc,
                    if (rates != null) now else old?.ratesAt ?: 0, if (gold != null) now else old?.goldAt ?: 0, if (coin != null) now else old?.btcAt ?: 0)
                val failed = (src.rates != null && rates == null) || (src.gold.isNotEmpty() && gold == null) || (btc && src.crypto != null && coin == null)
                p.edit().putLong("tried", if (failed) now else 0).also { if (!snap.empty) it.putString("snap", toJson(snap).toString()) }.apply()
                if (rates != null || gold != null || coin != null) done()
            } catch (e: Exception) {
            } finally { synchronized(this) { busy = false } }
        }.start()
    }
}
