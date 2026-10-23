package dev.tvremote.home.apps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerTest {
    @Test fun shapes() {
        assertTrue(wide(320, 180))
        assertTrue(wide(-1, -1))                                          // a colour or a shape
        assertFalse(wide(64, 64))                                         // Rockchip's Media Center
        assertFalse(wide(200, 160))
    }
}
