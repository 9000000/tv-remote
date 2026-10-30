package dev.tvremote.home.ui

import dev.tvremote.tvui.Motion
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import dev.tvremote.home.Tile
import dev.tvremote.home.R

/**
 * Opening an app: a plate grows from the tile to the whole screen (corners going square, darkening so no light frame shows) while
 * the tile's picture glides, at its own size, to the middle; the app starts early in the growth and fades in over it, Home held
 * still. Until the app draws, the picture pulses: a slow app (a cold start, the first one after an update) shows it is loading.
 * The pulse only changes the picture view's alpha and scale, drawn once: nothing is painted again while the app starts (on a
 * weak TV, repainting the whole screen 60 times a second slowed the app it waited for).
 * Back on Home the plate is simply gone and the system's own crossfade brings Home in: it runs outside Home's thread, so it
 * stays smooth while Home is busy redrawing (a shrink drawn here lost all its frames on a 32-bit FPT box). Android 14 ignores a
 * launcher's windowAnimationStyle for this (tried: a 1.5x scale never showed), and the app's own picture cannot shrink into
 * the tile: no app but the system's launcher may take another app's window.
 * Shape after ledat100398/owntv Zoom.java, MIT (own code: plain views).
 * Motion off: no plate. Calm: shorter, and the pulse only fades.
 */
class Zoom(private val a: Activity) {
    private val plate = Plate(a)
    private val pic = Pic(a)
    private val ui = Handler(Looper.getMainLooper())
    private var anim: ValueAnimator? = null
    private var tile: Tile? = null
    private var held = false                                         // the plate fills the screen, waiting for Home to come back
    private var left = false                                         // Home went to the background since the plate filled the screen
    private var launch: Runnable? = null
    private var guard: Runnable? = null
    private var pulse: Animator? = null

    val busy get() = anim != null && !held

    /** Grows from [t] and runs [go] (with the animation options to start the app with) at [START] of the way; false: nothing drawn, [go] not run. */
    fun open(t: Tile, go: (Bundle?) -> Unit): Boolean {
        if (!Motion.on || busy || held || !t.isAttachedToWindow || t.pane.width == 0) return false
        end(false)
        tile = t
        val snap = plate.take(t)
        val ms = Motion.ms(GROW_MS)
        val content = a.findViewById<ViewGroup>(android.R.id.content)
        if (plate.parent == null) content.addView(plate, FrameLayout.LayoutParams(-1, -1))
        val from = plate.from
        if (pic.parent == null) content.addView(pic, FrameLayout.LayoutParams(1, 1))
        pic.take(snap, from.width().toInt(), from.height().toInt(), plate.corner)
        plate.visibility = View.VISIBLE; plate.alpha = 1f
        plate.set(0f)
        val endX = (content.width - from.width()) / 2f; val endY = (content.height - from.height()) / 2f
        pic.translationX = from.left; pic.translationY = from.top
        t.alpha = 0f                                                  // same frame as the plate's first draw
        left = false                                                  // before the launch: Home pauses during the growth, and a reset after it made a slow app look failed
        launch = Runnable { launch = null; go(options()) }
        ui.postDelayed(launch!!, (ms * START).toLong())               // not at the end: a slow app must not wait for the plate
        run(ms) { f ->
            val g = GROW.getInterpolation(f)
            plate.set(g)
            pic.translationX = from.left + (endX - from.left) * g; pic.translationY = from.top + (endY - from.top) * g
        }.also { onEnd = { held = true; breathe(); arm() } }
        return true
    }

    private var onEnd: (() -> Unit)? = null

    private fun options(): Bundle {
        return ActivityOptions.makeCustomAnimation(a, R.anim.zoom_in, R.anim.zoom_hold).toBundle()
    }

    /** The picture in the middle fades (and, not calm, shrinks a little) in and out until the app covers it or Home is back;
     *  not at once: an app that draws within [PULSE_WAIT] never shows it. */
    private fun breathe() {
        stopBreath()
        val small = if (Motion.calm) 1f else 0.94f
        pulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(pic,
            android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.45f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, small),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, small)).apply {
            duration = PULSE_MS; startDelay = PULSE_WAIT; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = EASE
            start()
        }
    }

    private fun stopBreath() { pulse?.let { pulse = null; it.cancel() }; pic.alpha = 1f; pic.scaleX = 1f; pic.scaleY = 1f }

    /** Home's window is gone (the app covers it): nothing to pulse for. */
    fun hidden() = stopBreath()

    /** Home is back: the plate goes before Home's first frame, the window animation does the rest. Home never left (the app
     *  failed, or only a dialog came): Home is idle, the plate fades out here. */
    fun back() {
        if (!held && anim == null) return                             // also mid-growth: a quick Back must not leave the plate up
        guard?.let { ui.removeCallbacks(it) }
        if (left) { end(true); return }
        held = false
        stopBreath()
        run(if (Motion.calm) 120L else 180L) { f -> plate.alpha = 1f - f; pic.alpha = 1f - f }.also { onEnd = { end(true) } }
    }

    fun paused() { left = true }

    /** If the app never came to the front (it failed, or only a dialog), Home takes the plate back. */
    private fun arm() {
        guard?.let { ui.removeCallbacks(it) }
        guard = Runnable { if (held && !left) back() }
        ui.postDelayed(guard!!, 3000)
    }

    /** Hides the plate at once, the tile back. */
    fun end(restore: Boolean = true) {
        anim?.let { anim = null; it.cancel() }
        launch?.let { ui.removeCallbacks(it); launch = null }
        guard?.let { ui.removeCallbacks(it) }
        stopBreath()
        held = false
        plate.visibility = View.GONE; plate.alpha = 1f
        pic.visibility = View.GONE; pic.setLayerType(View.LAYER_TYPE_NONE, null); pic.snap = null
        if (restore) tile?.alpha = 1f
    }

    private fun run(ms: Long, frame: (Float) -> Unit) {
        anim?.let { anim = null; it.cancel() }
        val v = ValueAnimator.ofFloat(0f, 1f).apply { duration = ms; interpolator = LinearInterpolator() }
        v.addUpdateListener { frame(it.animatedValue as Float) }
        v.addListener(object : AnimatorListenerAdapter() {
            var cancelled = false
            override fun onAnimationCancel(p: Animator) { cancelled = true }
            override fun onAnimationEnd(p: Animator) {
                if (cancelled) return
                frame(1f); anim = null; onEnd?.invoke()
            }
        })
        anim = v; v.start()
    }

    /** The plate: a round rect from the tile's rect to the screen, in the tile's colour going near black. Still once grown. */
    private class Plate(c: Context) : View(c) {
        val from = RectF(); private val rect = RectF()
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private var edge = 0xFF1E2230.toInt(); var corner = 0f
        private var grow = 0f

        init { visibility = GONE }

        /** The tile's place, corner and colour; its picture (null: none could be drawn). */
        fun take(t: Tile): Bitmap? {
            val p = t.pane
            val s = (p.parent as? View)?.scaleX ?: 1f
            val xy = IntArray(2); p.getLocationInWindow(xy)
            from.set(xy[0].toFloat(), xy[1].toFloat(), xy[0] + p.width * s, xy[1] + p.height * s)
            corner = t.corner * s
            return try {
                val b = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888)
                p.draw(Canvas(b))
                edge = Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0) or 0xFF000000.toInt()
                b
            } catch (e: RuntimeException) { edge = 0xFF1E2230.toInt(); null }  // a banner held as a hardware bitmap cannot be drawn in software: the plain colour stands in
        }

        fun set(g: Float) { grow = g; invalidate() }

        override fun onDraw(c: Canvas) {
            val g = grow
            rect.set(lerp(from.left, 0f, g), lerp(from.top, 0f, g), lerp(from.right, width.toFloat(), g), lerp(from.bottom, height.toFloat(), g))
            val r = corner * (1f - g)
            val x = 1f - (g / 0.75f).coerceIn(0f, 1f)                 // darkens ahead of its growth: a light tile never becomes a bright screen
            fill.color = mix(edge, DEEP, 1f - x * x)
            c.drawRoundRect(rect, r, r, fill)
        }

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
        private fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(s: Int) = Math.round(lerp(((a shr s) and 0xFF).toFloat(), ((b shr s) and 0xFF).toFloat(), t))
            return 0xFF shl 24 or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }

    /** The tile's picture at the tile's size (a stretched logo would blur and look broken), in a layer with the tile's round
     *  corners as its outline: moving, fading and the pulse are the layer's properties, no new drawing. */
    private class Pic(c: Context) : android.widget.ImageView(c) {
        var snap: Bitmap? = null
            set(b) { field = b; setImageBitmap(b) }
        private var corner = 0f

        init {
            visibility = GONE; scaleType = ScaleType.FIT_XY
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, corner) }
            }
            clipToOutline = true
        }

        fun take(b: Bitmap?, w: Int, h: Int, r: Float) {
            snap = b; corner = r
            layoutParams = (layoutParams as FrameLayout.LayoutParams).also { it.width = w; it.height = h }
            pivotX = w / 2f; pivotY = h / 2f
            alpha = 1f; scaleX = 1f; scaleY = 1f
            visibility = if (b == null) GONE else VISIBLE
            setLayerType(LAYER_TYPE_HARDWARE, null)
            invalidateOutline()
        }
    }

    companion object {
        const val GROW_MS = 380L; const val PULSE_MS = 650L        // one way: a breath of 1.3 s
        const val PULSE_WAIT = 350L
        const val START = 0.25f                                      // the app's start is most of the wait: begin it early
        private const val DEEP = 0xFF07080C.toInt()
        private val GROW = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)
        private val EASE = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
    }
}
