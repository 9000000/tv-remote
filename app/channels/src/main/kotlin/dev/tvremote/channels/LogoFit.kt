package dev.tvremote.channels

import kotlin.math.min
import kotlin.math.sqrt

/**
 * The maths that makes logos look even on the cards, on plain ints so a unit test needs no Bitmap. iptv-org's logos come at every
 * aspect ratio and many carry a border (transparent, or white): the border is cut once when the logo is cached ([bounds]), and the
 * card draws it by [scale], so a wide wordmark and a square icon weigh about the same.
 */
object LogoFit {
    private const val CLEAR = 10                                      // alpha under this is nothing
    private const val NEAR = 12                                       // how far a pixel may be from the edge colour and still be border
    private const val LIGHT = 232                                     // a solid edge is only border when light: a dark plate is part of the logo
    private const val KEEP = 0.02f                                    // fraction of the longest side kept around the logo

    /** left, top, right, bottom (exclusive) of what is left after the border is cut; the whole picture when there is nothing to cut. */
    fun bounds(px: IntArray, w: Int, h: Int): IntArray {
        val all = intArrayOf(0, 0, w, h)
        if (w < 8 || h < 8 || px.size < w * h) return all
        val bg = edgeColour(px, w, h)
        fun empty(p: Int): Boolean {
            if ((p ushr 24) < CLEAR) return true
            if (bg == null) return false
            return Math.abs(((p shr 16) and 255) - bg[0]) <= NEAR && Math.abs(((p shr 8) and 255) - bg[1]) <= NEAR && Math.abs((p and 255) - bg[2]) <= NEAR
        }
        var t = 0; var b = h; var l = 0; var r = w
        fun rowEmpty(y: Int, x0: Int, x1: Int): Boolean { for (x in x0 until x1) if (!empty(px[y * w + x])) return false; return true }
        fun colEmpty(x: Int, y0: Int, y1: Int): Boolean { for (y in y0 until y1) if (!empty(px[y * w + x])) return false; return true }
        while (t < h && rowEmpty(t, 0, w)) t++
        if (t == h) return all                                        // nothing visible: leave it (the card shows its letters if it is blank)
        while (b > t + 1 && rowEmpty(b - 1, 0, w)) b--
        while (l < w && colEmpty(l, t, b)) l++
        while (r > l + 1 && colEmpty(r - 1, t, b)) r--
        val pad = (maxOf(r - l, b - t) * KEEP).toInt() + 1
        l = maxOf(0, l - pad); t = maxOf(0, t - pad); r = min(w, r + pad); b = min(h, b + pad)
        if ((r - l).toLong() * (b - t) < w.toLong() * h / 50) return all   // under 2% of the picture: a speck, not a logo
        return intArrayOf(l, t, r, b)
    }

    /** The edge colour (r, g, b) when the four corners agree and are light and opaque: that is a white plate to cut; else null. */
    private fun edgeColour(px: IntArray, w: Int, h: Int): IntArray? {
        val cs = intArrayOf(px[0], px[w - 1], px[(h - 1) * w], px[h * w - 1])
        if (cs.any { (it ushr 24) < 200 }) return null
        val r = (cs[0] shr 16) and 255; val g = (cs[0] shr 8) and 255; val b = cs[0] and 255
        if (r < LIGHT || g < LIGHT || b < LIGHT) return null
        for (c in cs) if (Math.abs(((c shr 16) and 255) - r) > NEAR || Math.abs(((c shr 8) and 255) - g) > NEAR || Math.abs((c and 255) - b) > NEAR) return null
        return intArrayOf(r, g, b)
    }

    const val LIGHT_TILE = 0xFFF2F4F7.toInt()                         // the card's tiles: what a logo is drawn on
    const val DARK_TILE = 0xFF3A4350.toInt()

    private fun lin(c: Int): Double = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
    private fun wcag(r: Double, g: Double, b: Double) = 0.2126 * lin(r.toInt()) + 0.7152 * lin(g.toInt()) + 0.0722 * lin(b.toInt())
    private fun contrast(a: Double, b: Double) = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)

    /**
     * True when the logo reads better on the dark tile: the WCAG contrast of its alpha-weighted mean colour against the dark tile
     * beats the one against the light tile (so pale yellow / pink lettering counts, not only white). A logo on its own opaque plate
     * (all four corners opaque) is never light: the plate shows.
     */
    fun isLight(px: IntArray, w: Int, h: Int): Boolean {
        if (w < 2 || h < 2 || px.size < w * h) return false
        if (listOf(px[0], px[w - 1], px[(h - 1) * w], px[h * w - 1]).all { (it ushr 24) >= 200 }) return false
        var r = 0.0; var g = 0.0; var b = 0.0; var wt = 0.0; var n = 0
        for (p in px) {
            val a = p ushr 24
            if (a < 40) continue
            r += ((p shr 16) and 255) * a; g += ((p shr 8) and 255) * a; b += (p and 255) * a; wt += a; n++
        }
        if (n < 16) return false
        val l = wcag(r / wt, g / wt, b / wt)
        fun tile(c: Int) = wcag(((c shr 16) and 255).toDouble(), ((c shr 8) and 255).toDouble(), (c and 255).toDouble())
        return contrast(l, tile(DARK_TILE)) > contrast(l, tile(LIGHT_TILE))
    }

    /**
     * How much to scale a [bw] x [bh] logo inside a [boxW] x [boxH] box: as large as fits, but no more than [AREA] of the box's
     * area, so a wide wordmark (fills the width, little height) is not lighter than a square icon (fills the height).
     */
    fun scale(bw: Int, bh: Int, boxW: Int, boxH: Int): Float {
        if (bw <= 0 || bh <= 0 || boxW <= 0 || boxH <= 0) return 1f
        val fit = min(boxW.toFloat() / bw, boxH.toFloat() / bh)
        val weight = sqrt(AREA * boxW * boxH / (bw.toFloat() * bh))
        return min(fit, weight)
    }

    const val AREA = 0.5f
}
