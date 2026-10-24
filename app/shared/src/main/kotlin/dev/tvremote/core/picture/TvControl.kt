package dev.tvremote.core.picture

import dev.tvremote.core.web.HttpServer
import org.json.JSONObject

/**
 * POST /api/touch and /api/type on an ADB-connected [Tv], for the computer and TV Setup alike (one body shape, so one reader:
 * js/remote.js sends the same to both).
 */
object TvControl {
    /** `a`: tap (down + up), down, up, move (also 0 / 1 / 2), scroll (the wheel, with `dy`). Anything else: 400. */
    fun touch(t: Tv, d: JSONObject): HttpServer.Response {
        val x = d.optDouble("x", 0.0).toFloat()
        val y = d.optDouble("y", 0.0).toFloat()
        when (d.optString("a")) {
            "tap" -> { t.touch(0, x, y); t.touch(1, x, y) }
            "down", "0" -> t.touch(0, x, y)
            "up", "1" -> t.touch(1, x, y)
            "move", "2" -> t.touch(2, x, y)
            "scroll" -> t.scroll(x, y, d.optDouble("dy", 0.0).toFloat())
            else -> return HttpServer.Response.error(400, "bad action")
        }
        return HttpServer.Response.ok()
    }

    /** Live typing: `del` characters back, then `text` (500 at most), then `fdel` forward, then Enter if `enter`. */
    fun type(t: Tv, d: JSONObject): HttpServer.Response {
        repeat(minOf(d.optInt("del"), 500)) { t.press(67) }
        d.optString("text").take(500).let { if (it.isNotEmpty()) t.text(it) }
        repeat(minOf(d.optInt("fdel"), 500)) { t.press(112) }
        if (d.optBoolean("enter")) t.press(66)
        return HttpServer.Response.ok()
    }
}
