package dev.tvremote.home.wall

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Process
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.ImageView
import android.media.MediaPlayer
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import java.io.File

/**
 * A video (or a GIF made into one) as the background: a looping MediaPlayer on a SurfaceView behind the grid, with the same veil a
 * photo gets over it. The platform's player on purpose: it is hardware-decoded and costs no library (ExoPlayer is in TV Channels only).
 * A SurfaceView and not a TextureView: the decoder's frames go to the display compositor (an overlay where the box has one) and never
 * through Home's own GPU pass, so Home draws no frame for the video at all (dev/decisions/tv-home.md "TV Home: video", measured).
 *
 * A SurfaceView is a hole in the window, black until its first frame: an ImageView with the poster (the first frame at the screen's
 * size, Home's own window background) covers it until the player shows one, and again whenever the player is let go, so Home never flashes black.
 * The widgets' frost uses the same poster, blurred, as for a photo.
 *
 * It measures itself: [Verdict.WINDOW_MS] after the first frame it reports [Measure] (frames shown against the clip's rate, its CPU)
 * and HomeActivity keeps the verdict ([Videos.measured]); too slow twice and Home shows the poster alone.
 *
 * The loop: `isLooping` stood still ~0.45 s a turn on the emulator and seconds on the FPT box, mostly the audio clock (the player
 * waits for the audio to start again, even muted): so the picture alone is played ([Silent]), and a second player made ready
 * [LEAD_MS] before the end (parsed, no surface) takes the surface when the first ends (completion -> first frame ~60 ms). A net video
 * plays from [NetVideo]'s file once it is there, so the turns after the first read no network (dev/decisions/tv-home.md "TV Home: video").
 *
 * It plays only while Home is on screen: [stop] (Home stopped: another app in front, screen off, memory short) releases the player and
 * with it the hardware decoder, which the app in front may need; [start] makes it again. No timer, no polling.
 */
class VideoWall(private val app: Context, private val root: ViewGroup, private val ui: Handler,
                private val measured: (Measure) -> Unit, private val failed: () -> Unit) {
    /** What 20 s of playing showed: frames shown per second against the clip's own, this process's CPU (% of one core), errors. */
    class Measure(val fps: Float, val want: Float, val cpu: Int, val errors: Int)

    private val view = SurfaceView(app)
    private var poster: ImageView? = null
    private var veil: View? = null
    private var player: MediaPlayer? = null
    private var next: MediaPlayer? = null                           // the next turn, prepared and waiting for the surface
    private var nextReady = false
    private var ended = 0L                                          // when the last turn ended: the gap to the next first frame is logged
    private val arm = Runnable { armNext() }
    private var surface: Surface? = null
    private var file: File? = null
    private var url: String? = null                                 // a video from the net ([showUrl]): streamed, not in the library, so not measured
    private var vw = 0; private var vh = 0
    private var begun = 0L
    private var errors = 0
    private var t0 = 0L; private var cpu0 = 0L; private var f0 = 0; private var d0 = 0; private var want = 30f
    private var accF = 0; private var accD = 0                     // the counters of the turns' players let go, so the measure spans the turns
    private val measure = Runnable { measureNow() }

    init {
        view.isFocusable = false; view.isClickable = false
        view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) { surface = h.surface; play() }
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
            override fun surfaceDestroyed(h: SurfaceHolder) { release(); surface = null }
        })
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> cover() }
    }

    /** Puts [f] behind the grid (the same file again changes nothing but the veil [user]). */
    fun show(f: File, user: Float) {
        veil(user)
        if (file != f || url != null) { release(); file = f; url = null; play() }
    }

    private fun veil(user: Float) {
        if (veil == null) {
            root.addView(view, 0, FrameLayout.LayoutParams(-1, -1))
            poster = ImageView(app).apply {                                     // a SurfaceView is a hole in the window: the poster fills it until the first frame
                scaleType = ImageView.ScaleType.CENTER_CROP; isFocusable = false
                root.addView(this, 1, FrameLayout.LayoutParams(-1, -1))
            }
            veil = WebWall.dim(app, user).also { root.addView(it, 2, FrameLayout.LayoutParams(-1, -1)) }
        }
        veil?.background = WebWall.veil(user)
    }

    /**
     * A direct video link (a web wall that was only a video, [WebVideo]) behind the grid, played like a library video. No file, no
     * verdict: when the player cannot play it, [failed] runs and the caller falls back to the page in a WebView.
     */
    fun showUrl(u: String, user: Float) {
        veil(user)
        if (url != u) { release(); file = null; url = u; play() }
    }

    /** Home is on screen: play (the player was let go in [stop]). */
    fun start() { if (player == null) play() }

    /** Home is away or memory is short: no player, no decoder; the view and the poster stay. */
    fun stop() = release()

    /** Gone for good: the views are taken out. */
    fun detach() {
        release()
        (view.parent as? ViewGroup)?.removeView(view)
        poster?.let { (it.parent as? ViewGroup)?.removeView(it) }; poster = null
        veil?.let { (it.parent as? ViewGroup)?.removeView(it) }
        veil = null; file = null; url = null
    }

    private fun play() {
        val s = surface ?: return                                       // the view is not ready: its listener calls again
        if (player != null) return
        val src = source() ?: return
        begun = SystemClock.elapsedRealtime(); errors = 0; t0 = 0L; accF = 0; accD = 0; ended = 0L
        covered(true)
        try {
            player = MediaPlayer().also { p ->
                p.setSurface(s)
                p.setDataSource(src)
                wire(p)
                p.setOnPreparedListener { it.start(); armLater(it) }
                p.prepareAsync()
            }
        } catch (e: Throwable) { release(); ui.post { failed() } }
    }

    /** What to play, its picture alone: the library file, a net video's kept file, else its address (and the file is fetched for the next turns). */
    private fun source(): String? {
        file?.let { return if (it.isFile) Silent.of(app, it).path else null }   // a wall has no sound (the user, 2026-10-27): the copy without audio, [Silent]
        val u = url ?: return null
        NetVideo.kept(app, u)?.let { return Silent.of(app, it).path }
        NetVideo.fetch(app, u) { }
        return u
    }

    private fun wire(p: MediaPlayer) {
        val f = file
        p.setVolume(0f, 0f)
        p.setOnVideoSizeChangedListener { _, w, h -> vw = w; vh = h; cover() }
        p.setOnInfoListener { _, what, _ ->
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                if (ended > 0) Log.i(TAG, "loop gap ${SystemClock.elapsedRealtime() - ended} ms") else Log.i(TAG, "first frame ${SystemClock.elapsedRealtime() - begun} ms")
                ended = 0L
                covered(false)
                if (f != null && t0 == 0L) beginMeasure(p, f)
            }
            false
        }
        p.setOnErrorListener { _, w, e -> Log.w(TAG, "error $w/$e"); errors++; ui.post { release(); failed() }; true }   // a file the codec cannot play: the poster stays
        p.setOnCompletionListener { turn(it) }
    }

    /** The next turn is made ready [LEAD_MS] before this one ends: soon enough to be parsed, late enough not to fetch a streamed video twice for long. */
    private fun armLater(p: MediaPlayer) {
        ui.removeCallbacks(arm)
        val left = try { p.duration - p.currentPosition } catch (e: Throwable) { -1 }
        if (left > 0) ui.postDelayed(arm, (left - LEAD_MS).coerceAtLeast(0).toLong())
    }

    private fun armNext() {
        if (player == null || next != null) return
        val src = source() ?: return
        nextReady = false
        try {
            next = MediaPlayer().also { n ->
                n.setDataSource(src)
                n.setOnPreparedListener { if (next === it) nextReady = true }
                n.setOnErrorListener { m, _, _ -> if (next === m) dropNext(); true }
                n.prepareAsync()
            }
        } catch (e: Throwable) { dropNext() }
    }

    private fun dropNext() {
        val n = next ?: return
        next = null; nextReady = false
        try { n.release() } catch (e: Throwable) { }
    }

    /** A turn ended: the ready player takes the surface; none ready (a slow network, an error), the same one goes back to the start. */
    private fun turn(p: MediaPlayer) {
        if (p !== player) return
        ended = SystemClock.elapsedRealtime()
        val n = next.takeIf { nextReady }
        val s = surface
        if (n == null || s == null) {
            dropNext()
            try { p.seekTo(0); p.start(); armLater(p) } catch (e: Throwable) { release(); ui.post { failed() } }
            return
        }
        next = null; nextReady = false
        frames(p)?.let { accF += it.first; accD += it.second }
        player = n
        quit(p)                                                         // the surface is free for the next one (the last frame stays on it)
        try { n.setSurface(s); wire(n); n.start(); armLater(n) } catch (e: Throwable) { release(); ui.post { failed() } }
    }

    private fun quit(p: MediaPlayer) {
        try { p.setOnErrorListener(null); p.setOnPreparedListener(null); p.setOnCompletionListener(null); p.stop() } catch (e: Throwable) { }
        try { p.reset() } catch (e: Throwable) { }
        try { p.release() } catch (e: Throwable) { }
    }

    private fun release() {
        ui.removeCallbacks(measure)                                     // nothing is measured (or kept running) once it does not play
        ui.removeCallbacks(arm)
        covered(true)
        dropNext()
        val p = player ?: return
        player = null
        quit(p)
    }

    /** The poster over the hole: the very picture Home has as its window's background (shared, no second bitmap in memory). */
    private fun covered(on: Boolean) {
        val v = poster ?: return
        if (on) { v.setImageDrawable(root.rootView.background); v.visibility = View.VISIBLE } else { v.visibility = View.GONE; v.setImageDrawable(null) }
    }

    private fun frames(p: MediaPlayer): Pair<Int, Int>? = if (android.os.Build.VERSION.SDK_INT < 26) null else try {   // getMetrics is API 26: no measure before
        p.metrics?.let { m -> (m.get(FRAMES) as? Number)?.let { it.toInt() to ((m.get(DROPPED) as? Number)?.toInt() ?: 0) } }   // numbers of any width: it is a Long here
    } catch (e: Throwable) { null }

    /** The first frame is on screen: note the counters and look again after [Verdict.WINDOW_MS]; the one timer, taken away by [release]. */
    private fun beginMeasure(p: MediaPlayer, f: File) {
        val n = frames(p) ?: return
        t0 = SystemClock.elapsedRealtime().coerceAtLeast(1); cpu0 = Process.getElapsedCpuTime(); f0 = n.first; d0 = n.second
        want = clipFps(f)
        ui.removeCallbacks(measure); ui.postDelayed(measure, Verdict.WINDOW_MS)
    }

    private fun measureNow() {
        val p = player ?: return
        val n = frames(p)?.let { (it.first + accF) to (it.second + accD) } ?: return
        val dt = (SystemClock.elapsedRealtime() - t0).coerceAtLeast(1)
        val shown = ((n.first - f0) - (n.second - d0)).coerceAtLeast(0) * 1000f / dt
        val cpu = ((Process.getElapsedCpuTime() - cpu0) * 100f / dt).toInt()
        Log.i(TAG, "measure frames=${n.first - f0} dropped=${n.second - d0} dt=$dt fps=$shown want=$want cpu=$cpu%")
        measured(Measure(shown, want, cpu, errors))
    }

    /** The clip's own frame rate (made by [Transcode] or sent as it was); 30 when the file does not say. */
    private fun clipFps(f: File): Float = try {
        MediaMetadataRetriever().let { r ->
            try {
                r.setDataSource(f.path)
                val n = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toFloatOrNull()
                val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toFloatOrNull()
                if (n != null && ms != null && ms > 0 && n > 0) n * 1000f / ms else 30f
            } finally { r.release() }
        }
    } catch (e: Throwable) { 30f }

    /** The picture must cover the screen like a photo: the SurfaceView is made bigger than the window on one axis (the window clips it). */
    private fun cover() {
        val w = root.width.toFloat(); val h = root.height.toFloat()
        if (vw <= 0 || vh <= 0 || w <= 0 || h <= 0) return
        val va = vw.toFloat() / vh; val ra = w / h
        val cw = if (va > ra) (h * va).toInt() else w.toInt(); val ch = if (va > ra) h.toInt() else (w / va).toInt()
        val lp = view.layoutParams as? FrameLayout.LayoutParams ?: return
        if (lp.width != cw || lp.height != ch || lp.gravity != Gravity.CENTER) { lp.width = cw; lp.height = ch; lp.gravity = Gravity.CENTER; view.layoutParams = lp }
    }

    companion object {
        const val TAG = "TvHomeVideo"
        private const val LEAD_MS = 4_000
        private const val FRAMES = "android.media.mediaplayer.frames"
        private const val DROPPED = "android.media.mediaplayer.dropped"
    }
}
