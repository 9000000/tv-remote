package dev.tvremote.home

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

/**
 * Opening an app: a plate grows from the tile to the whole screen (corners going square, darkening so no light frame shows) while
 * the tile's picture glides, at its own size, to the middle; the app starts part way through and fades in over it, Home held still.
 * Until the app draws, the picture pulses: a slow app (a cold start, the first one after an update) shows it is loading, not a
 * black screen. Back on Home the plate shrinks into the tile and the picture glides back into it.
 * Shape after ledat100398/owntv Zoom.java, MIT (own code: one plain view).
 * Motion off: no plate. Calm: shorter, the pulse only fades, and the way back is a short fade, not a shrink.
 */
class Zoom(private val a: Activity) {
    private val plate = Plate(a)
    private val ui = Handler(Looper.getMainLooper())
    private var anim: ValueAnimator? = null
    private var tile: Tile? = null
    private var held = false                                         // the plate fills the screen, waiting for Home to come back
    private var left = false                                         // Home went to the background since the plate filled the screen
    private var launch: Runnable? = null
    private var guard: Runnable? = null
    private var pulse: ValueAnimator? = null

    val busy get() = anim != null && !held

    /** Grows from [t] and runs [go] (with the animation options to start the app with) at [START] of the way; false: nothing drawn, [go] not run. */
    fun open(t: Tile, go: (Bundle?) -> Unit): Boolean {
        if (!Motion.on || busy || held || !t.isAttachedToWindow || t.pane.width == 0) return false
        end(false)
        tile = t
        plate.take(t)
        val ms = Motion.ms(GROW_MS)
        val content = a.findViewById<ViewGroup>(android.R.id.content)
        if (plate.parent == null) content.addView(plate, FrameLayout.LayoutParams(-1, -1))
        plate.visibility = View.VISIBLE
        plate.set(0f, 1f)
        t.alpha = 0f                                                  // same frame as the plate's first draw
        left = false                                                  // before the launch: Home pauses during the growth, and a reset after it made a slow app look failed
        launch = Runnable { launch = null; go(options()) }
        ui.postDelayed(launch!!, (ms * START).toLong())               // not at the end: a slow app must not wait for the plate
        run(ms) { f -> plate.set(GROW.getInterpolation(f), 1f) } .also { onEnd = { held = true; breathe(); arm() } }
        return true
    }

    private var onEnd: (() -> Unit)? = null

    private fun options(): Bundle {
        val o = ActivityOptions.makeCustomAnimation(a, R.anim.zoom_in, R.anim.zoom_hold)
        // Android 13+: an app's own starting window is often plain black (TV Games, most TV apps); its icon on it instead
        if (android.os.Build.VERSION.SDK_INT >= 33) o.setSplashScreenStyle(android.window.SplashScreen.SPLASH_SCREEN_STYLE_ICON)
        return o.toBundle()
    }

    /** The picture in the middle fades (and, not calm, shrinks a little) in and out until the app covers it or Home is back. */
    private fun breathe() {
        stopBreath()
        pulse = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PULSE_MS; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = EASE
            addUpdateListener { plate.breath(it.animatedValue as Float, !Motion.calm) }
            start()
        }
    }

    private fun stopBreath() { pulse?.let { pulse = null; it.cancel() }; plate.breath(0f, false) }

    /** Home's window is gone (the app covers it): nothing to pulse for. */
    fun hidden() = stopBreath()

    /** Home is back (or never left): shrinks into the tile, or fades when it is gone or motion is calm. */
    fun back() {
        if (!held) return
        held = false; guard?.let { ui.removeCallbacks(it) }
        stopBreath()
        val t = tile?.takeIf { it.isAttachedToWindow && it.pane.width > 0 }
        if (t == null || Motion.calm) {
            run(if (Motion.calm) 120L else 180L) { f -> plate.alpha = 1f - f }.also { onEnd = { end(true) } }
            return
        }
        plate.take(t, false)
        val ms = Motion.ms(SHRINK_MS)
        run(ms) { f -> plate.set(1f - SHRINK.getInterpolation(f), 1f) }.also { onEnd = { end(true) } }
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

    /** The one view that draws the plate: a round rect from the tile's rect to the screen, in the tile's colour going near black. */
    private class Plate(c: Context) : View(c) {
        private val from = RectF(); private val rect = RectF(); private val at = RectF()
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pic = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private var snap: Bitmap? = null
        private var edge = 0xFF1E2230.toInt(); private var corner = 0f
        private var grow = 0f; private var logo = 1f
        private var dim = 0f; private var shrink = 0f                 // the pulse: 0 = still
        private val clip = android.graphics.Path()

        init { visibility = GONE }

        fun take(t: Tile, picture: Boolean = true) {
            val p = t.pane
            val s = (p.parent as? View)?.scaleX ?: 1f
            val xy = IntArray(2); p.getLocationInWindow(xy)
            from.set(xy[0].toFloat(), xy[1].toFloat(), xy[0] + p.width * s, xy[1] + p.height * s)
            corner = t.corner * s
            if (!picture) return
            snap = null
            try {
                val b = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888)
                p.draw(Canvas(b)); snap = b
                val one = Bitmap.createScaledBitmap(b, 1, 1, true)
                edge = one.getPixel(0, 0) or 0xFF000000.toInt()
            } catch (e: RuntimeException) { edge = 0xFF1E2230.toInt() }  // a banner held as a hardware bitmap cannot be drawn in software: the plain colour stands in
        }

        fun set(g: Float, picture: Float) { grow = g; logo = picture.coerceIn(0f, 1f); invalidate() }
        fun breath(f: Float, scale: Boolean) { dim = f * 0.55f; shrink = if (scale) f * 0.06f else 0f; invalidate() }

        override fun onDraw(c: Canvas) {
            val g = grow
            rect.set(lerp(from.left, 0f, g), lerp(from.top, 0f, g), lerp(from.right, width.toFloat(), g), lerp(from.bottom, height.toFloat(), g))
            val r = corner * (1f - g)
            val x = 1f - (g / 0.75f).coerceIn(0f, 1f)                 // darkens ahead of its growth: a light tile never becomes a bright screen
            fill.color = mix(edge, DEEP, 1f - x * x)
            c.drawRoundRect(rect, r, r, fill)
            val b = snap ?: return
            // the picture keeps the tile's size and glides from the tile to the middle (a stretched logo would blur and look broken)
            val w = from.width() * (1f - shrink); val h = from.height() * (1f - shrink)
            val cx = lerp(from.centerX(), width / 2f, g); val cy = lerp(from.centerY(), height / 2f, g)
            at.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
            pic.alpha = (255 * logo * (1f - dim)).toInt()
            val save = c.save()
            clip.rewind(); clip.addRoundRect(at, corner, corner, android.graphics.Path.Direction.CW)
            c.clipPath(clip)
            c.drawBitmap(b, null, at, pic)
            c.restoreToCount(save)
        }

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
        private fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(s: Int) = Math.round(lerp(((a shr s) and 0xFF).toFloat(), ((b shr s) and 0xFF).toFloat(), t))
            return 0xFF shl 24 or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }

    companion object {
        const val GROW_MS = 380L; const val SHRINK_MS = 340L; const val PULSE_MS = 650L   // one way: a breath of 1.3 s
        const val START = 0.6f
        private const val DEEP = 0xFF07080C.toInt()
        private val GROW = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)
        private val SHRINK = PathInterpolator(0.3f, 0f, 0.1f, 1f)
        private val EASE = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
    }
}
