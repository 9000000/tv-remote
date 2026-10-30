package dev.tvremote.setup

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.VideoView
import java.io.File

/**
 * "Send to TV > open file": plays a video or song from the clients over ADB. The TV's own player (FPT's gallery3d, ...) stretches a
 * portrait video over the whole screen and plays it once; this one keeps the picture's shape (black bars) and loops. OK = pause /
 * play, left / right = 10 s back / forward, Back closes. The file is a copy in this app's own folder (`files/play`, made by the
 * client's shell: TV Setup has no storage permission), so the file is read by path. Started by explicit component from the shell.
 */
class PlayActivity : Activity() {
    private lateinit var video: VideoView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val path = intent.data?.path
        val file = path?.let { File(it) }
        if (file == null || !file.canRead()) { finish(); return }
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        video = VideoView(this)
        video.setOnPreparedListener { it.isLooping = true; video.start() }
        video.setOnErrorListener { _, what, extra -> Log.w("Play", "error $what/$extra"); finish(); true }
        video.setVideoPath(file.path)                                    // a VideoView keeps the picture's aspect, centred
        root.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        if ((intent.type ?: "").startsWith("audio/")) {                  // a song has no picture: its name
            val t = TextView(this)
            t.text = dev.tvremote.tvui.Marks.text(this, "♪  " + file.nameWithoutExtension)   // a line icon: a TV font may lack ♪
            t.setTextColor(Color.WHITE); t.textSize = 28f; t.gravity = Gravity.CENTER
            root.addView(t, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(root)
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e)
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ->
                if (video.isPlaying) video.pause() else video.start()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> video.seekTo(maxOf(0, video.currentPosition - 10_000))
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> video.seekTo(video.currentPosition + 10_000)
            else -> return super.dispatchKeyEvent(e)
        }
        return true
    }

    /** Left by Home or an incoming app: over, not playing on behind. */
    override fun onStop() { super.onStop(); finish() }
}
