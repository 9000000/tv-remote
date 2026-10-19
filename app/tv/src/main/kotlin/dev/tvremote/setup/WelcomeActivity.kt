package dev.tvremote.setup

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.VideoView
import dev.tvremote.core.ontv.Welcome

/**
 * The welcome screen: the video (once, with its sound) or the picture (for the seconds set) over the whole TV. Any key ends it,
 * except the volume keys, which a guest may want while it plays. When it ends by itself or by a key the TV goes to its home screen
 * ([home]: not when the owner tried it from a page, who is then back where they were). Started by [WelcomeState]; also by the
 * shell (DUMP: no app can), which is how it can be tried with `am start`.
 */
class WelcomeActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private var home = true
    private var ending = false
    private val end = Runnable { close() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        home = intent.getBooleanExtra("home", true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (android.os.Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        val file = WelcomeState.file(this)
        if (file == null) { finish(); return }                          // deleted since it was started
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        val seconds = Welcome.seconds(WelcomeState.load(this).opt("seconds"))
        if (Welcome.kind(file.name) == "video") {
            val v = VideoView(this)
            v.setOnPreparedListener { v.start() }
            v.setOnCompletionListener { close() }
            v.setOnErrorListener { _, what, extra -> Log.w("Welcome", "video error $what/$extra"); close(); true }
            v.setVideoURI(Uri.fromFile(file))
            root.addView(v, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
            ui.postDelayed(end, 10 * 60_000L)                           // a video that never ends must not hold the TV
        } else {
            val img = ImageView(this)
            img.scaleType = ImageView.ScaleType.FIT_CENTER
            val m = resources.displayMetrics
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var s = 1
            while (bounds.outWidth / (s * 2) >= m.widthPixels && bounds.outHeight / (s * 2) >= m.heightPixels) s *= 2
            val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })
            if (bmp == null) { finish(); return }
            img.setImageBitmap(bmp)
            root.addView(img, FrameLayout.LayoutParams(-1, -1))
            ui.postDelayed(end, seconds * 1000L)
        }
        setContentView(root)
    }

    /** Ends the welcome; the home screen comes up if it was shown by itself. */
    private fun close() {
        if (ending) return
        ending = true
        ui.removeCallbacks(end)
        if (home) try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
        finish()
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_VOLUME_UP || e.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || e.keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) return super.dispatchKeyEvent(e)
        if (e.action == KeyEvent.ACTION_DOWN) close()
        return true
    }

    override fun onTouchEvent(e: MotionEvent): Boolean { if (e.action == MotionEvent.ACTION_DOWN) close(); return true }

    /** Left by the Home key, an incoming app or the screen going off: it is over, not waiting behind. */
    override fun onStop() {
        super.onStop()
        if (!ending) { ending = true; ui.removeCallbacks(end); finish() }
    }

    override fun onDestroy() { ui.removeCallbacks(end); super.onDestroy() }
}
