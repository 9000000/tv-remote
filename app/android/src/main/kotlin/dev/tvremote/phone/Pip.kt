package dev.tvremote.phone

import dev.tvremote.core.T
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout

// The TV's picture stays in sight when the remote screen is left. Out of the app (the PiP button beside full screen): Android's
// Picture-in-Picture. Inside it (Manage, Settings, About): a small floating player, the same live picture. One stream either
// way: the picture's VideoFrame (with its SurfaceView) is moved between the remote's stage and [MiniPlayer]; the surface goes
// and comes back, which the decoder already survives (Tv.pauseScreen / resumeScreen).

internal const val FLOAT_NONE = 0
internal const val FLOAT_MINI = 1
internal const val FLOAT_PIP = 2
private const val PIP_ACTION = "dev.tvremote.phone.PIP"

/** A picture is showing right now: the only time there is something to float. */
internal fun MainActivity.picLive() = video && basic == null && host != null && pic != null && picShown

internal fun MainActivity.canPip() = Build.VERSION.SDK_INT >= 26 && picLive() && !isFinishing &&
    packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

/** The picture moves to where it floats ([FLOAT_MINI], [FLOAT_PIP]) or back to the remote's stage ([FLOAT_NONE]). */
internal fun MainActivity.setFloat(mode: Int) {
    if (mode == floatMode) return
    floatMode = mode
    if (mode == FLOAT_NONE) {
        mini.visibility = View.GONE
        (frame.parent as? ViewGroup)?.removeView(frame)
        stage.addView(frame, 0, FrameLayout.LayoutParams(-1, -2))
        layoutRemote()                                                // sizes the frame for the stage again
    } else {
        if (frame.parent !== mini) {
            (frame.parent as? ViewGroup)?.removeView(frame)
            mini.addView(frame, 0, FrameLayout.LayoutParams(-1, -2))
        }
        mini.mode(mode == FLOAT_PIP)
        mini.visibility = View.VISIBLE
    }
    pipUpdate()
}

/** Tap on the mini player: the screen laid over the remote goes, the remote's picture is back. */
internal fun MainActivity.expandMini() {
    managing.back()
    settings.visibility = View.GONE
    about.visibility = View.GONE
}

/** ✕ on the mini player: the TV encodes nothing more; the remote asks for the picture again when it is back on screen. */
internal fun MainActivity.closeMini() = stopPicture()                 // stopPicture brings the frame home

internal fun MainActivity.pipParams(): PictureInPictureParams? {
    if (Build.VERSION.SDK_INT < 26) return null
    val b = PictureInPictureParams.Builder()
    val w = vidW.coerceAtLeast(1); val h = vidH.coerceAtLeast(1)
    b.setAspectRatio(when {                                           // Android accepts 1:2.39 … 2.39:1 only
        w * 100L > 239L * h -> Rational(239, 100)
        h * 100L > 239L * w -> Rational(100, 239)
        else -> Rational(w, h)
    })
    val r = Rect()
    if (surface.isShown && surface.getGlobalVisibleRect(r) && !r.isEmpty) b.setSourceRectHint(r)   // the animation starts from the picture, not from the screen
    val acts = ArrayList<RemoteAction>()
    if (basic == null) {                                              // the sound needs ADB too
        val ic = if (listen) "mute" else "headphones"
        val t = T(if (listen) "float.mute" else "float.hear")
        acts.add(RemoteAction(Icon.createWithResource(this, resources.getIdentifier("ic_$ic", "drawable", packageName)), t, t, pipIntent(1)))
    }
    val c = T("float.close")
    acts.add(RemoteAction(Icon.createWithResource(this, resources.getIdentifier("ic_close", "drawable", packageName)), c, c, pipIntent(2)))
    b.setActions(acts)
    if (Build.VERSION.SDK_INT >= 31) {
        b.setSeamlessResizeEnabled(false)                             // video resizes by cross-fade, not by stretching
    }
    return b.build()
}

private fun MainActivity.pipIntent(n: Int): PendingIntent =
    PendingIntent.getBroadcast(this, n, Intent(PIP_ACTION).setPackage(packageName).putExtra("a", n), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

/** Tells Android the size and the actions the PiP window will have. */
internal fun MainActivity.pipUpdate() {
    if (Build.VERSION.SDK_INT < 26 || !miniReady()) return
    pipBtn.alpha = if (picLive()) 1f else 0.4f
    try { pipParams()?.let { setPictureInPictureParams(it) } } catch (e: Exception) { }
}

/** The PiP button beside full screen. */
internal fun MainActivity.pipEnter() {
    if (!canPip() || isInPictureInPictureMode) return
    try { pipParams()?.let { enterPictureInPictureMode(it) } } catch (e: Exception) { }   // a phone with PiP switched off in its settings refuses
}

private val hidden = ArrayList<Pair<View, Int>>()

internal fun MainActivity.pipChanged(on: Boolean) {
    val root = mini.parent as ViewGroup
    if (on) {
        wasPip = true
        hidden.clear()
        for (i in 0 until root.childCount) { val c = root.getChildAt(i); if (c !== mini) { hidden.add(c to c.visibility); c.visibility = View.GONE } }
        setFloat(FLOAT_PIP)
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.getIntExtra("a", 0)) {
                    1 -> { toggleListen(); }
                    2 -> { stopPicture(); moveTaskToBack(true) }      // closes the window; the app itself stays, as after Home
                }
            }
        }
        pipRx = rx
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(rx, IntentFilter(PIP_ACTION), Context.RECEIVER_NOT_EXPORTED) else registerReceiver(rx, IntentFilter(PIP_ACTION))
    } else {
        pipRx?.let { try { unregisterReceiver(it) } catch (e: Exception) { } }
        pipRx = null
        for ((v, vis) in hidden) v.visibility = vis
        hidden.clear()
        if (pic == null) { setFloat(FLOAT_NONE); return }
        setFloat(if (covered()) FLOAT_MINI else FLOAT_NONE)
        wasCovered = covered()
    }
}

/**
 * The small player over Manage, Settings and About: about 40% of the width, bottom right, dragged anywhere and let go it snaps
 * to the nearest corner. A tap anywhere but ✕ brings the remote back. In PiP the same view fills the window, bare.
 */
internal class MiniPlayer(private val a: MainActivity) : FrameLayout(a) {
    private val m = a.dp(12)
    private val slop = ViewConfiguration.get(a).scaledTouchSlop
    private var dx = 0f; private var dy = 0f; private var drag = false
    private var pip = false
    private val closeBtn = a.icon("@close", T("float.close_mini"), 0xAA000000.toInt())

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, if (pip) 0f else a.dp(14).toFloat()) }
        }
        clipToOutline = true
        elevation = a.dp(10).toFloat()
        setBackgroundColor(0xFF000000.toInt())
        closeBtn.setIcon("@close", 0xFFFFFFFF.toInt())
        addView(closeBtn, LayoutParams(a.dp(34), a.dp(34), Gravity.TOP or Gravity.END).apply { setMargins(a.dp(4), a.dp(4), a.dp(4), a.dp(4)) })
        visibility = GONE
    }

    fun mode(pip: Boolean) {
        this.pip = pip
        closeBtn.visibility = if (pip) GONE else VISIBLE
        val p = parent as? View
        if (pip) {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            translationX = 0f; translationY = 0f; elevation = 0f
            // the frame fills it, exact height: the picture centred at the TV's aspect
            for (i in 0 until childCount) if (getChildAt(i) is VideoFrame) getChildAt(i).layoutParams = LayoutParams(-1, -1)
        } else {
            val w = ((p?.width ?: a.resources.displayMetrics.widthPixels) * 0.4f).toInt()
            layoutParams = FrameLayout.LayoutParams(w, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(m, m, m, m) }
            for (i in 0 until childCount) if (getChildAt(i) is VideoFrame) getChildAt(i).layoutParams = LayoutParams(-1, -2)
            elevation = a.dp(10).toFloat()
            snap(1, 1, false)
        }
        invalidateOutline()
    }

    private fun inClose(e: MotionEvent): Boolean {
        val r = Rect(); closeBtn.getHitRect(r)
        return closeBtn.visibility == VISIBLE && r.contains(e.x.toInt(), e.y.toInt())
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean = !pip && !inClose(e)   // the picture below must not receive touches meant for the TV

    private var downAt = 0L

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (pip) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; drag = false; downAt = System.currentTimeMillis(); animate().cancel(); return true }
            MotionEvent.ACTION_MOVE -> {
                val mx = e.rawX - dx; val my = e.rawY - dy
                if (!drag && Math.hypot(mx.toDouble(), my.toDouble()) > slop) drag = true
                if (drag) { translationX += mx; translationY += my; dx = e.rawX; dy = e.rawY }
            }
            MotionEvent.ACTION_UP -> {
                if (!drag) a.expandMini() else snapNearest()
            }
            MotionEvent.ACTION_CANCEL -> if (drag) snapNearest()
        }
        return true
    }

    /** Translation that puts the card in a corner: [right] / [bottom] 1 = where it was laid out (bottom right). */
    private fun snap(right: Int, bottom: Int, animate: Boolean) {
        val p = parent as? View ?: return
        val tx = if (right == 1) 0f else -(p.width - width - 2 * m).toFloat()
        val ty = if (bottom == 1) 0f else -(p.height - height - 2 * m).toFloat()
        if (animate) animate().translationX(tx).translationY(ty).setDuration(180).start() else { translationX = tx; translationY = ty }
    }

    private fun snapNearest() {
        val p = parent as? View ?: return
        val cx = left + translationX + width / 2f; val cy = top + translationY + height / 2f
        snap(if (cx > p.width / 2f) 1 else 0, if (cy > p.height / 2f) 1 else 0, true)
    }

    init { closeBtn.setOnClickListener { a.closeMini() } }
}
