package dev.tvremote.home

import android.content.Context
import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.graphics.Bitmap
import android.graphics.Color
import android.service.dreams.DreamService
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * TV Home's screensaver: the big clock, the day and the lunar date over the wallpaper (the same one the home screen has). Made
 * for weak boxes: views built once, the picture drifts with ONE slow property animation (no redraw in between, on the GPU), the
 * clock text changes once a minute, and it is not animated at all when motion is off. Against burn-in the clock moves to
 * another place every minute, fading while it goes, and everything is a little dim.
 */
class HomeDream : DreamService() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor()
    private lateinit var block: LinearLayout
    private lateinit var date: TextView
    private lateinit var lunar: TextView
    private var wall: ImageView? = null
    private var drift: ObjectAnimator? = null
    private var motion = true
    private var shown: Bitmap? = null
    private val minute = object : Runnable { override fun run() { refresh(); moveClock(); ui.postDelayed(this, untilNextMinute()) } }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Font.load(this)
        isInteractive = false; isFullscreen = true; isScreenBright = false
        Store.advance(this, false)                                   // a timed rotation that fell due while Home was not on screen
        val conf = Store.get(this)
        Motion.read(this, conf)
        motion = Motion.on
        val dm = resources.displayMetrics
        val root = FrameLayout(this)
        root.background = wallDrawable(wallColors(conf.optString("wall")))
        val id = conf.optString("wall")
        if (Walls.isPicture(id) || WallArt.has(id)) {                 // a photo, Bing's picture or a drawn scene: all become one bitmap here
            val v = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
            wall = v
            root.addView(v, FrameLayout.LayoutParams(-1, -1))
            bg.execute {
                val (bw, bh) = Wall.want(dm.widthPixels, dm.heightPixels)
                val b = if (WallArt.has(id)) Walls.small(this, id, bw, bh, false) else Walls.bitmap(this, id, dm.widthPixels, dm.heightPixels)   // the dim below is its veil
                ui.post { if (b != null && shown == null && wall != null) { shown = b; v.setImageBitmap(b); v.animate().alpha(1f).setDuration(if (motion) 900 else 0).start(); startDrift(v) } }
            }
        }
        root.addView(View(this).apply { setBackgroundColor(0x70000000) }, FrameLayout.LayoutParams(-1, -1))   // dim: night, and words stay readable over a photo

        val clock = TextClock(this).apply {
            textSize = 120f; setTextColor(0xE6FFFFFF.toInt()); typeface = Font.light
            includeFontPadding = false; gravity = Gravity.CENTER
            format24Hour = if (conf.optBoolean("clock24", true)) "HH:mm" else "h:mm"
            format12Hour = format24Hour
        }
        date = line(26f, 0xCCFFFFFF.toInt()); lunar = line(20f, 0x99FFFFFF.toInt())
        block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(40), dp(20), dp(40), dp(20)) }
        block.addView(clock); block.addView(date); block.addView(lunar)
        root.addView(block, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(root)
        refresh()
        ui.postDelayed(minute, untilNextMinute())
    }

    override fun onDetachedFromWindow() {
        ui.removeCallbacksAndMessages(null)
        drift?.cancel(); drift = null
        wall?.setImageDrawable(null); wall = null; shown = null
        bg.shutdownNow()
        super.onDetachedFromWindow()
    }

    private fun dp(v: Number) = (v.toFloat() * resources.displayMetrics.density).toInt()

    private fun line(size: Float, color: Int) = Font.on(TextView(this)).apply { textSize = size; setTextColor(color); gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) }

    /** The day and the lunar date: once a minute, which is when the day can change at all. */
    private fun refresh() {
        date.text = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), Date()).toString().replaceFirstChar { it.uppercase() }
        val c = Calendar.getInstance()
        val d = Lunar.of(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))
        val r = resources
        val month = r.getStringArray(R.array.lunar_month)[d.month - 1] + if (d.leap) getString(R.string.lunar_leap) else ""
        val dayWord = if (d.day <= 10) getString(R.string.lunar_day_early, d.day) else d.day.toString()
        val (can, chi) = Lunar.canChi(d.year)
        lunar.text = getString(R.string.lunar_title, dayWord, month) + " · " +
            getString(R.string.lunar_year, r.getStringArray(R.array.lunar_can)[can], r.getStringArray(R.array.lunar_chi)[chi])
    }

    /** The clock block to another place on the screen (never the same twice in a row, always fully inside), fading out and in on the way. */
    private fun moveClock() {
        val dm = resources.displayMetrics
        val rx = maxOf(0, (dm.widthPixels - block.width) / 2); val ry = maxOf(0, (dm.heightPixels - block.height) / 2)
        val x = ((Math.random() * 2 - 1) * rx).toFloat(); val y = ((Math.random() * 2 - 1) * ry * 0.9).toFloat()
        if (!motion) { block.translationX = x; block.translationY = y; return }
        block.animate().alpha(0f).setDuration(500).withEndAction {
            block.translationX = x; block.translationY = y
            block.animate().alpha(1f).setDuration(700).start()
        }.start()
    }

    /** One slow push-in and sideways drift, back and forth for good: a single animator on one layer, nothing allocated per frame. */
    private fun startDrift(v: ImageView) {
        if (!motion) return
        v.pivotX = 0f; v.pivotY = v.height.toFloat()
        v.scaleX = 1.0f; v.scaleY = 1.0f
        v.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val a = ObjectAnimator.ofFloat(v, "scaleX", 1.0f, 1.12f)
        a.addUpdateListener { v.scaleY = it.animatedValue as Float }
        a.duration = 90_000; a.repeatMode = ObjectAnimator.REVERSE; a.repeatCount = ObjectAnimator.INFINITE
        a.interpolator = LinearInterpolator() as TimeInterpolator
        a.start(); drift = a
    }

    private fun untilNextMinute() = 60_000L - System.currentTimeMillis() % 60_000L + 50
}
