package dev.tvremote.karaoke

import android.view.KeyEvent
import dev.tvremote.karaoke.KeyRules.Do
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyRulesTest {
    private fun d(player: Boolean, key: Int, paused: Boolean = false, playing: Boolean = true) = KeyRules.decide(player, key, paused, playing)

    @Test fun constantsAreAndroidsOwn() {            // KeyRules keeps plain numbers (no Android in it): they must be the real key codes
        assertEquals(KeyEvent.KEYCODE_BACK, 4); assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, 22); assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, 23)
        assertEquals(KeyEvent.KEYCODE_ENTER, 66); assertEquals(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 85); assertEquals(KeyEvent.KEYCODE_MEDIA_NEXT, 87)
        assertEquals(KeyEvent.KEYCODE_MEDIA_PLAY, 126); assertEquals(KeyEvent.KEYCODE_MEDIA_PAUSE, 127); assertEquals(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, 90)
        assertEquals(KeyEvent.KEYCODE_NUMPAD_ENTER, 160)
    }

    @Test fun onThePlayerOkPausesRightSkipsBackOpensTheQueue() {
        assertEquals(Do.PAUSE, d(true, KeyEvent.KEYCODE_DPAD_CENTER)); assertEquals(Do.PAUSE, d(true, KeyEvent.KEYCODE_ENTER))
        assertEquals(Do.NEXT, d(true, KeyEvent.KEYCODE_DPAD_RIGHT)); assertEquals(Do.QUEUE, d(true, KeyEvent.KEYCODE_BACK))
        assertEquals(Do.NONE, d(true, KeyEvent.KEYCODE_DPAD_LEFT)); assertEquals(Do.NONE, d(true, KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test fun backOnTheQueueLeavesAndOkIsLeftToTheButtons() {
        assertEquals(Do.EXIT, d(false, KeyEvent.KEYCODE_BACK)); assertEquals(Do.NONE, d(false, KeyEvent.KEYCODE_DPAD_CENTER)); assertEquals(Do.NONE, d(false, KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test fun mediaKeysWorkEverywhereButOnlyWhileSomethingPlays() {
        assertEquals(Do.PAUSE, d(false, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)); assertEquals(Do.RESUME, d(false, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, paused = true))
        assertEquals(Do.NEXT, d(false, KeyEvent.KEYCODE_MEDIA_NEXT)); assertEquals(Do.NONE, d(false, KeyEvent.KEYCODE_MEDIA_NEXT, playing = false))
        assertEquals(Do.NONE, d(true, KeyEvent.KEYCODE_MEDIA_PLAY, paused = false)); assertEquals(Do.RESUME, d(true, KeyEvent.KEYCODE_MEDIA_PLAY, paused = true))
        assertEquals(Do.NONE, d(true, KeyEvent.KEYCODE_MEDIA_PAUSE, paused = true))
    }

    @Test fun pausedPlayerResumesOnOkAndEmptyPlayerGoesToTheQueue() {
        assertEquals(Do.RESUME, d(true, KeyEvent.KEYCODE_DPAD_CENTER, paused = true)); assertEquals(Do.QUEUE, d(true, KeyEvent.KEYCODE_DPAD_CENTER, playing = false))
        assertEquals(Do.NONE, d(true, KeyEvent.KEYCODE_DPAD_RIGHT, playing = false))
    }
}
