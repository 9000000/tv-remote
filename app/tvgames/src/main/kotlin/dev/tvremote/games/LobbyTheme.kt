package dev.tvremote.games

import android.graphics.Bitmap
import android.graphics.Color

/**
 * What the lobby takes from the game's picture, computed once when the picture arrives (a 64 x 36 copy, so a weak 32-bit box does it
 * in a few ms and nothing runs per frame): a blurred, darkened backdrop and one accent colour.
 */
object LobbyTheme {
    const val DEFAULT = 0xFF4F8CFF.toInt()

    /** The picture centre-cropped to 16:9, shrunk to [w] x [h] (the blur then costs nothing), blurred by a few box passes. */
    fun backdrop(src: Bitmap, w: Int = 64, h: Int = 36, radius: Int = 3): Bitmap {
        val sw = src.width; val sh = src.height
        val cw: Int; val ch: Int
        if (sw * 9 > sh * 16) { ch = sh; cw = sh * 16 / 9 } else { cw = sw; ch = sw * 9 / 16 }
        val crop = Bitmap.createBitmap(src, (sw - cw) / 2, (sh - ch) / 2, cw, ch)
        val small = Bitmap.createScaledBitmap(crop, w, h, true)
        val px = IntArray(w * h); small.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) { blur(px, w, h, radius) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun blur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        for (pass in 0..1) {                                           // horizontal, then vertical
            val n = if (pass == 0) w else h; val lines = if (pass == 0) h else w
            for (l in 0 until lines) for (i in 0 until n) {
                var a = 0; var rr = 0; var g = 0; var b = 0; var c = 0
                for (k in -r..r) {
                    val j = (i + k).coerceIn(0, n - 1)
                    val p = if (pass == 0) px[l * w + j] else px[j * w + l]
                    a += p ushr 24; rr += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255; c++
                }
                val v = ((a / c) shl 24) or ((rr / c) shl 16) or ((g / c) shl 8) or (b / c)
                if (pass == 0) tmp[l * w + i] = v else px[i * w + l] = v
            }
            if (pass == 0) System.arraycopy(tmp, 0, px, 0, px.size)
        }
    }

    /** The most vivid hue of the picture (24 hue buckets weighted by saturation x value), lifted so it reads on a dark room. */
    fun accent(b: Bitmap): Int {
        val w = 48; val h = 27
        val s = Bitmap.createScaledBitmap(b, w, h, true)
        val px = IntArray(w * h); s.getPixels(px, 0, w, 0, 0, w, h)
        val weight = FloatArray(24); val sumH = FloatArray(24)
        val hsv = FloatArray(3)
        for (p in px) {
            Color.colorToHSV(p, hsv)
            if (hsv[1] < 0.25f || hsv[2] < 0.25f) continue             // greys, near-black (box art's dark bars) say nothing
            val wt = hsv[1] * hsv[2]; val k = (hsv[0] / 15f).toInt().coerceIn(0, 23)
            weight[k] += wt; sumH[k] += hsv[0] * wt
        }
        var best = -1; var top = 0f
        for (k in 0 until 24) { val w3 = weight[k] + 0.5f * (weight[(k + 1) % 24] + weight[(k + 23) % 24]); if (w3 > top) { top = w3; best = k } }
        if (best < 0 || top < 2f) return DEFAULT
        return Color.HSVToColor(floatArrayOf(sumH[best] / weight[best], 0.72f, 1f))
    }

    /** Black or near-black text, whichever reads on [c]. */
    fun onAccent(c: Int) = if ((0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) > 140) 0xFF0B0D12.toInt() else Color.WHITE
}
