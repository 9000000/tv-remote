package dev.tvremote.karaoke

/** What a TV remote key does, decided apart from the Activity so it can be tested. The key codes are android.view.KeyEvent's. */
object KeyRules {
    enum class Do { NONE, PAUSE, NEXT, QUEUE, EXIT, RESUME }

    private const val BACK = 4
    private const val DPAD_RIGHT = 22
    private const val DPAD_CENTER = 23
    private const val ENTER = 66
    private const val MEDIA_PLAY_PAUSE = 85
    private const val MEDIA_NEXT = 87
    private const val MEDIA_PLAY = 126
    private const val MEDIA_PAUSE = 127
    private const val MEDIA_FF = 90
    private const val NUMPAD_ENTER = 160

    /**
     * On the player screen: OK pauses / resumes, right (or media next / fast-forward) skips to the next song, Back goes to the queue screen.
     * On the queue screen the buttons take the keys (focus + OK) and Back leaves the app; only the media keys act. [playing] is whether a song is on.
     */
    fun decide(player: Boolean, key: Int, paused: Boolean, playing: Boolean): Do = when {
        key == MEDIA_PLAY_PAUSE -> if (!playing) Do.NONE else if (paused) Do.RESUME else Do.PAUSE
        key == MEDIA_PLAY -> if (playing && paused) Do.RESUME else Do.NONE
        key == MEDIA_PAUSE -> if (playing && !paused) Do.PAUSE else Do.NONE
        key == MEDIA_NEXT || key == MEDIA_FF -> if (playing) Do.NEXT else Do.NONE
        player && key == BACK -> Do.QUEUE
        !player && key == BACK -> Do.EXIT
        player && (key == DPAD_CENTER || key == ENTER || key == NUMPAD_ENTER) -> if (!playing) Do.QUEUE else if (paused) Do.RESUME else Do.PAUSE
        player && key == DPAD_RIGHT -> if (playing) Do.NEXT else Do.NONE
        else -> Do.NONE
    }
}
