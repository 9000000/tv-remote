package dev.tvremote.home.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketTest {
    private fun res(n: String) = javaClass.getResourceAsStream("/info/$n")!!.readBytes().toString(Charsets.UTF_8)

    @Test fun bankRatesInTheOrderShown() {
        val r = Market.parseVcb(res("vcb.xml"))
        assertEquals(listOf("USD", "EUR", "JPY", "KRW", "CNY", "GBP").filter { c -> r.any { it.code == c } }, r.map { it.code })
        val usd = r.first { it.code == "USD" }
        assertEquals(25640.0, usd.buy, 0.01); assertEquals(26020.0, usd.sell, 0.01)
        assertTrue(r.all { it.sell > 0 })
    }

    @Test fun btmcPerLuong() {
        val g = Market.parseBtmc(res("btmc.json"))!!
        assertEquals(137_100_000L, g.sjcBuy); assertEquals(141_700_000L, g.sjcSell)
        assertTrue(g.ringBuy > 0 && g.ringSell >= g.ringBuy)
    }

    @Test fun pnjPerLuong() {
        val g = Market.parsePnj(res("pnj.json"))!!
        assertEquals(138_700_000L, g.sjcBuy); assertEquals(141_700_000L, g.sjcSell)
    }

    @Test fun binance() {
        val b = Market.parseBinance(res("binance.json"))!!
        assertEquals(80459.22, b.usd, 0.01); assertEquals(-1.068, b.pct, 0.001)
    }

    @Test fun notTheShapeGivesNull() {
        assertNull(Market.parseBtmc("{}")); assertNull(Market.parsePnj("nope")); assertNull(Market.parseBinance("{\"code\":-1}"))
        assertTrue(Market.parseVcb("<html>blocked</html>").isEmpty())
    }

    @Test fun savedAndReadBack() {
        val s = Market.Snap(Market.parseVcb(res("vcb.xml")), Market.parseBtmc(res("btmc.json")), Market.parseBinance(res("binance.json")), 1, 2, 3)
        val b = Market.fromJson(Market.toJson(s).toString())!!
        assertEquals(s.rates.size, b.rates.size); assertEquals(s.gold!!.sjcSell, b.gold!!.sjcSell); assertNotNull(b.btc); assertEquals(3L, b.at)
    }
}
