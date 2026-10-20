package dev.tvremote.home

import org.junit.Assert.assertEquals
import org.junit.Test

class WallTest {
    @Test fun wantedSize() {
        assertEquals(1920 to 1080, Wall.want(1920, 1080))
        assertEquals(1920 to 1080, Wall.want(3840, 2160))                 // a 4K panel: the same photo, scaled
        assertEquals(1280 to 720, Wall.want(1280, 720))
        assertEquals(1080 to 1080, Wall.want(1080, 1080))
    }

    @Test fun sampleKeepsTheCover() {
        assertEquals(2, Wall.sample(4000, 2250, 1920, 1080))              // 2000x1125 still covers; 1000x562 would not
        assertEquals(1, Wall.sample(1920, 1080, 1920, 1080))
        assertEquals(4, Wall.sample(8000, 4500, 1920, 1080))
    }

    @Test fun coverNeverGrows() {
        assertEquals(1920 to 1080, Wall.cover(2000, 1125, 1920, 1080))
        assertEquals(1920 to 1080, Wall.cover(1920, 1080, 1920, 1080))
        assertEquals(800 to 600, Wall.cover(800, 600, 1920, 1080))        // smaller than the screen: left alone, the view scales it
        assertEquals(1920 to 1440, Wall.cover(2560, 1920, 1920, 1080))    // a 4:3 photo: the width fits, the height crops
    }
}
