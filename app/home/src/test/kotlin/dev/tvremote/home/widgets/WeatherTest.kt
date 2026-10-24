package dev.tvremote.home.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherTest {
    private val answer = """{"current":{"time":"2026-10-21T02:30","temperature_2m":25.4,"weather_code":0,"is_day":0},
        "daily":{"time":["2026-10-21","2026-10-22","2026-10-23","2026-10-24"],"weather_code":[53,51,63,95],
        "temperature_2m_max":[31.8,32.0,32.3,32.7],"temperature_2m_min":[24.5,23.9,24.7,25.1]}}"""

    @Test fun codesComeDownToEightSkies() {
        assertEquals(Weather.Kind.CLEAR, Weather.kind(0))
        assertEquals(Weather.Kind.PARTLY, Weather.kind(2))
        assertEquals(Weather.Kind.CLOUD, Weather.kind(3))
        assertEquals(Weather.Kind.FOG, Weather.kind(48))
        assertEquals(Weather.Kind.DRIZZLE, Weather.kind(53))
        assertEquals(Weather.Kind.RAIN, Weather.kind(81))
        assertEquals(Weather.Kind.SNOW, Weather.kind(75))
        assertEquals(Weather.Kind.STORM, Weather.kind(99))
        assertEquals(Weather.Kind.CLOUD, Weather.kind(1234))                // a code not known yet is not an error
    }

    @Test fun theForecastIsParsedAndRounded() {
        val f = Weather.parse("Hà Nội", answer, 7)!!
        assertEquals("Hà Nội", f.place)
        assertEquals(25, f.temp)
        assertEquals(true, f.night)
        assertEquals(Weather.Kind.CLEAR, f.kind)
        assertEquals(4, f.days.size)
        assertEquals(32, f.days[0].max); assertEquals(25, f.days[0].min)    // 24.5 rounds up
        assertEquals(Weather.Kind.STORM, f.days[3].kind)
        assertEquals(7L, f.at)
    }

    @Test fun anErrorOrHalfAnAnswerIsNoForecast() {
        assertNull(Weather.parse("x", """{"error":true,"reason":"Latitude must be in range"}""", 0))
        assertNull(Weather.parse("x", "<html>captive portal</html>", 0))
        assertNull(Weather.parse("x", """{"current":{"temperature_2m":1,"weather_code":0},"daily":{"time":[],"weather_code":[],"temperature_2m_max":[],"temperature_2m_min":[]}}""", 0))
    }

    @Test fun whatIsKeptReadsBackTheSame() {
        val f = Weather.parse("Huế", answer, 99)!!
        val g = Weather.fromJson(Weather.toJson(f).toString())!!
        assertEquals(f.place, g.place); assertEquals(f.temp, g.temp); assertEquals(f.days.map { it.max }, g.days.map { it.max }); assertEquals(99L, g.at)
        assertNull(Weather.fromJson("{}"))
    }

    @Test fun placesFromTheTwoLookups() {
        val g = Weather.parseGeocode("""{"results":[{"name":"Đà Nẵng","latitude":16.06778,"longitude":108.22083}]}""")!!
        assertEquals("Đà Nẵng", g.name); assertEquals(16.06778, g.lat, 1e-6)
        assertNull(Weather.parseGeocode("""{"generationtime_ms":0.1}"""))     // no results: no key at all
        val a = Weather.parseIp("""{"success":true,"city":"Ho Chi Minh City","latitude":10.82,"longitude":106.62}""")!!
        assertEquals("Ho Chi Minh City", a.name)
        val b = Weather.parseIp("""{"city":"Hanoi","latitude":"21.0245","longitude":"105.8412"}""")!!   // the second service: numbers as strings
        assertEquals(21.0245, b.lat, 1e-6)
        assertNull(Weather.parseIp("""{"success":false,"message":"Invalid IP address"}"""))
        assertNull(Weather.parseIp("""{"latitude":0,"longitude":0}"""))
    }

    private class Fake(val geo: Weather.Place?, val dev: Weather.Place?, val ip: Weather.Place?) : Weather.Sources {
        val asked = ArrayList<String>()
        override fun geocode(text: String): Weather.Place? { asked += "geo:$text"; return geo }
        override fun device(): Weather.Place? { asked += "device"; return dev }
        override fun ip(): Weather.Place? { asked += "ip"; return ip }
    }
    private val city = Weather.Place("A", 1.0, 1.0); private val here = Weather.Place("B", 2.0, 2.0); private val net = Weather.Place("C", 3.0, 3.0)

    @Test fun aTypedCityComesFirstAndIsNeverGuessedAround() {
        val s = Fake(city, here, net)
        assertEquals("A", Weather.resolve(" Hà Nội ", s)!!.name)
        assertEquals(listOf("geo:Hà Nội"), s.asked)
        val none = Fake(null, here, net)
        assertNull(Weather.resolve("Nowhere", none))                         // not found: not another city's weather
        assertEquals(listOf("geo:Nowhere"), none.asked)
    }

    @Test fun otherwiseTheDeviceThenTheInternetAddress() {
        assertEquals("B", Weather.resolve("", Fake(null, here, net))!!.name)
        val s = Fake(null, null, net)
        assertEquals("C", Weather.resolve("  ", s)!!.name)                   // a TV with no location provider
        assertEquals(listOf("device", "ip"), s.asked)
        assertNull(Weather.resolve("", Fake(null, null, null)))
    }

    @Test fun theAirIsParsedAndBanded() {
        val a = Weather.parseAir("""{"current":{"time":"2026-10-23T20:00","us_aqi":91,"pm2_5":25.4}}""", 9)!!
        assertEquals(91, a.aqi); assertEquals(25, a.pm25); assertEquals(9L, a.at)
        assertEquals(1, Weather.airLevel(91))
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5), listOf(0, 50, 51, 100, 101, 150, 151, 200, 201, 300, 301).map { Weather.airLevel(it) })
        assertNull(Weather.parseAir("""{"error":true}""", 0))
        assertNull(Weather.parseAir("""{"current":{"us_aqi":null,"pm2_5":3}}""", 0))
        assertEquals(91, Weather.airFromJson(Weather.airToJson(a).toString())!!.aqi)
    }
}
