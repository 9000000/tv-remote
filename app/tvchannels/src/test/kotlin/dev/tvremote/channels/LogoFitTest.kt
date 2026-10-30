package dev.tvremote.channels

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoFitTest {
    private fun img(w: Int, h: Int, bg: Int, fill: Int, x0: Int, y0: Int, x1: Int, y1: Int) =
        IntArray(w * h) { val x = it % w; val y = it / w; if (x in x0 until x1 && y in y0 until y1) fill else bg }

    @Test fun transparentBorderIsCut() {
        val px = img(100, 60, 0x00000000, 0xFFCC0000.toInt(), 30, 20, 70, 40)
        val b = LogoFit.bounds(px, 100, 60)
        assertTrue(b[0] in 27..30 && b[2] in 70..73 && b[1] in 17..20 && b[3] in 40..43)
    }

    @Test fun whitePlateIsCut() {
        val px = img(100, 100, 0xFFFFFFFF.toInt(), 0xFF0000AA.toInt(), 10, 40, 90, 60)
        val b = LogoFit.bounds(px, 100, 100)
        assertTrue(b[0] in 7..10 && b[2] in 90..93 && b[1] in 37..40 && b[3] in 60..63)
    }

    @Test fun darkPlateIsKept() {                                     // a black rectangle is the logo's own background
        val px = img(100, 100, 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 40, 40, 60, 60)
        assertArrayEquals(intArrayOf(0, 0, 100, 100), LogoFit.bounds(px, 100, 100))
    }

    @Test fun blankAndSpeckAreLeftAlone() {
        assertArrayEquals(intArrayOf(0, 0, 50, 50), LogoFit.bounds(IntArray(2500), 50, 50))
        val speck = img(100, 100, 0, 0xFF000000.toInt(), 50, 50, 52, 52)
        assertArrayEquals(intArrayOf(0, 0, 100, 100), LogoFit.bounds(speck, 100, 100))
    }

    @Test fun wideAndSquareWeighAlike() {
        val box = intArrayOf(700, 300)
        val sq = LogoFit.scale(200, 200, box[0], box[1]).let { 200 * it * 200 * it }
        val wide = LogoFit.scale(400, 100, box[0], box[1]).let { 400 * it * 100 * it }
        assertEquals(sq.toDouble(), wide.toDouble(), sq * 0.25)
        assertTrue(LogoFit.scale(400, 100, box[0], box[1]) * 400 <= box[0])           // never past the box
        assertTrue(LogoFit.scale(10, 400, box[0], box[1]) * 400 <= box[1])
    }

    private fun logo(w: Int, h: Int, bg: Int, fill: Int) = img(w, h, bg, fill, 20, 20, w - 20, h - 20)

    @Test fun lightLogos() {
        assertTrue(LogoFit.isLight(logo(100, 60, 0, 0xFFFFFFFF.toInt()), 100, 60))             // white on transparent
        assertTrue(LogoFit.isLight(logo(100, 60, 0, 0xFFE6E6E6.toInt()), 100, 60))
    }

    @Test fun darkAndColourfulLogosKeepTheLightTile() {
        assertFalse(LogoFit.isLight(logo(100, 60, 0, 0xFF222222.toInt()), 100, 60))
        assertFalse(LogoFit.isLight(logo(100, 60, 0, 0xFFCC0000.toInt()), 100, 60))
        assertFalse(LogoFit.isLight(logo(100, 60, 0, 0xFF1E88E5.toInt()), 100, 60))
        assertFalse(LogoFit.isLight(logo(100, 60, 0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 100, 60))   // dark on its own white plate
        assertFalse(LogoFit.isLight(logo(100, 60, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()), 100, 60))   // a plate with nothing: the plate shows
    }

    @Test fun paleColourLogoGetsTheDarkTile() {                       // Bondi Rescue (pluto): 58% white + a red, mean colour (229, 170, 164), mean luminance 0.71
        val px = IntArray(100 * 60)
        for (i in px.indices) { val k = i % 100; px[i] = if (k < 20 || k >= 80) 0 else if (k < 20 + 35) 0xFFFFFFFF.toInt() else 0xFFC13426.toInt() }
        assertTrue(LogoFit.isLight(px, 100, 60))
        assertFalse(LogoFit.isLight(IntArray(100 * 60) { if (it % 100 in 20..79) 0xFFC13426.toInt() else 0 }, 100, 60))   // the red alone: light tile
    }
}
