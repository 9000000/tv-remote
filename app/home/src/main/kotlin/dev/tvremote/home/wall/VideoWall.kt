package dev.tvremote.home.wall

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import java.io.File

/**
 * A video (or a GIF made into one) as the background: a looping MediaPlayer on a TextureView behind the grid, with the same veil a
 * photo gets over it. The platform's player on purpose: it is hardware-decoded and costs no library (ExoPlayer is in TV Channels only).
 *
 * The poster is NOT drawn here: Home paints it as the window's background ([Backdrop], the first frame at the screen's size), and a
 * TextureView is transparent until its first frame, so the home never flashes black while the player starts, and the cards' frost
 * has its blurred picture like on a photo.
 *
 * It plays only while Home is on screen: [stop] (Home stopped: another app in front, screen off, memory short) releases the player and
 * with it the hardware decoder, which the app in front may need; [start] makes it again. No timer, no polling.
 */
class VideoWall(private val app: Context, private val root: ViewGroup, private val ui: Handler, private val failed: () -> Unit) {
    private val tv = TextureView(app)
    private var veil: View? = null
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var file: File? = null
    private var vw = 0; private var vh = 0
    private var level = 0f
    private var begun = 0L

    init {
        tv.isFocusable = false; tv.isClickable = false
        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) { surface = Surface(s); play() }
            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) { cover() }
            override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean { release(); surface?.release(); surface = null; return true }
            override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
        }
    }

    /** Puts [f] behind the grid (the same file again changes nothing but the veil [user] and the sound). */
    fun show(f: File, user: Float, sound: Boolean, volume: Int) {
        level = if (sound) volume / 100f else 0f
        player?.setVolume(level, level)
        if (veil == null) {
            tv.alpha = 1f
            root.addView(tv, 0, FrameLayout.LayoutParams(-1, -1))
            veil = WebWall.dim(app, user).also { root.addView(it, 1, FrameLayout.LayoutParams(-1, -1)) }
        }
        veil?.background = WebWall.veil(user)
        if (file != f) { release(); file = f; play() }
    }

    /** Home is on screen: play (the player was let go in [stop]). */
    fun start() { if (player == null) play() }

    /** Home is away or memory is short: no player, no decoder; the view and the poster stay. */
    fun stop() = release()

    /** Gone for good: the views are taken out. */
    fun detach() {
        release()
        (tv.parent as? ViewGroup)?.removeView(tv)
        veil?.let { (it.parent as? ViewGroup)?.removeView(it) }
        veil = null; file = null
    }

    private fun play() {
        val f = file ?: return
        val s = surface ?: return                                       // the view is not ready: its listener calls again
        if (player != null || !f.isFile) return
        begun = SystemClock.elapsedRealtime()
        try {
            player = MediaPlayer().also { p ->
                p.setSurface(s)
                p.setDataSource(f.path)
                p.isLooping = true
                p.setVolume(level, level)
                p.setOnVideoSizeChangedListener { _, w, h -> vw = w; vh = h; cover() }
                p.setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) Log.i(TAG, "first frame ${SystemClock.elapsedRealtime() - begun} ms")
                    false
                }
                p.setOnErrorListener { _, w, e -> Log.w(TAG, "error $w/$e"); ui.post { release(); failed() }; true }   // a file the codec cannot play: the poster stays
                p.setOnPreparedListener { it.start() }
                p.prepareAsync()
            }
        } catch (e: Throwable) { release(); ui.post { failed() } }
    }

    private fun release() {
        val p = player ?: return
        player = null
        try { p.setOnErrorListener(null); p.setOnPreparedListener(null); p.stop() } catch (e: Throwable) { }
        try { p.reset() } catch (e: Throwable) { }
        try { p.release() } catch (e: Throwable) { }
    }

    /** A TextureView stretches the picture to its size: scaled up about the middle on one axis so it covers the screen like a photo. */
    private fun cover() {
        val w = tv.width.toFloat(); val h = tv.height.toFloat()
        if (vw <= 0 || vh <= 0 || w <= 0 || h <= 0) return
        val va = vw.toFloat() / vh; val ra = w / h
        tv.setTransform(Matrix().apply { setScale(if (va > ra) va / ra else 1f, if (va < ra) ra / va else 1f, w / 2, h / 2) })
    }

    companion object { const val TAG = "TvHomeVideo" }
}
