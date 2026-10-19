package dev.tvremote.phone

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import dev.tvremote.core.FleetStatus
import dev.tvremote.core.T
import dev.tvremote.core.adb.AdbLink
import dev.tvremote.core.picture.Thumb
import dev.tvremote.core.remote.Remote
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

// 📺 The saved TVs at a glance, on the list of TVs (the computer's page: js/tvstatus.js): under each TV whether it is on, in standby
// or not answering, its model and the app on screen, read every 10 s while the list shows (FleetStatus.kt: one light shell per TV on
// a link kept for it, a TCP connect first so a TV that is off costs a second). "Show screens" adds a small picture of each TV
// that is on, every 5 s; off until asked, it costs the TVs' CPU. Paused with the list (onPause) and while a TV is open.

private const val EVERY_MS = 10_000L
private const val SHOT_MS = 5_000L

/** What the list keeps between rounds; the readings themselves are the Board's. */
internal object TvStatus {
    @Volatile var shots = false
    val last = ConcurrentHashMap<String, FleetStatus.Status>()
    val thumbs = HashMap<String, ImageView>()                         // UI thread only
    private val links = ConcurrentHashMap<String, Pair<String, AdbLink>>()   // saved TV -> (the address dialled, its link)
    private val used = ConcurrentHashMap<String, Long>()
    @Volatile var board: FleetStatus.Board? = null
    @Volatile var resolve: (String) -> Triple<String, String, java.io.File>? = { null }   // saved TV -> (address now, kind, key folder)
    val busy = java.util.concurrent.atomic.AtomicBoolean()
    val gate = Semaphore(2)                                           // a picture costs a TV a PNG: two TVs at a time
    val pool = Executors.newCachedThreadPool { Thread(it, "tv-status").apply { isDaemon = true } }
    private val sweep = Executors.newSingleThreadScheduledExecutor { Thread(it, "tv-status-sweep").apply { isDaemon = true } }.also {
        it.scheduleWithFixedDelay({ idle() }, 30, 30, java.util.concurrent.TimeUnit.SECONDS)
    }

    fun link(ip: String, addr: String, keys: java.io.File): AdbLink {
        used[ip] = System.currentTimeMillis()
        val have = links[ip]
        if (have != null && have.first == addr) return have.second
        have?.second?.close()
        return AdbLink(keys, addr).also { links[ip] = addr to it }
    }

    /** A link the list has not used for 40 s (the list is closed, or a TV is open) goes: no connection kept for a screen nobody looks at. */
    private fun idle() {
        val now = System.currentTimeMillis()
        for ((ip, t) in used) if (now - t > 40_000) { used.remove(ip); links.remove(ip)?.second?.close() }
    }

    fun read(ip: String): FleetStatus.Status {
        val (addr, kind, keys) = resolve(ip) ?: return FleetStatus.Status(ip)
        val link = if (kind == Remote.Kind.ANDROID) link(ip, addr, keys) else null
        return FleetStatus.read(addr, kind, { FleetStatus.reach(addr, it) }, link).copy(host = ip)
    }

    fun board(): FleetStatus.Board = board ?: FleetStatus.Board({ read(it) }).also { board = it }
}

/** The list shows (called by each probe): reads now if the last round was long ago, then every 10 s on its own. */
internal fun MainActivity.statusStart() {
    TvStatus.resolve = { ip -> Triple(route[ip] ?: ip, kindOf(ip), filesDir) }
    if (TvStatus.busy.get() || host != null) return
    ui.removeCallbacks(statusTick)
    statusTick.run()
}

private val tickFor = java.util.WeakHashMap<MainActivity, Runnable>()
private val shotFor = java.util.WeakHashMap<MainActivity, Runnable>()
private val MainActivity.statusTick: Runnable get() = tickFor.getOrPut(this) { Runnable { statusRound() } }
private val MainActivity.shotTick: Runnable get() = shotFor.getOrPut(this) { Runnable { shotRound() } }

/** The list is left (the app is paused or a TV opens): the loops stop; the links close by themselves a little later. */
internal fun MainActivity.statusPause() { ui.removeCallbacks(statusTick); ui.removeCallbacks(shotTick) }

private fun MainActivity.statusRound() {
    if (host != null || isFinishing) return
    val ips = savedIps()
    if (ips.isEmpty() || !TvStatus.busy.compareAndSet(false, true)) { ui.postDelayed(statusTick, EVERY_MS); return }
    Thread {
        val out = try { TvStatus.board().round(ips, 9000) } catch (e: Exception) { emptyList() } finally { TvStatus.busy.set(false) }
        ui.post {
            if (isFinishing) return@post
            for (s in out) if (s.state != FleetStatus.UNKNOWN) TvStatus.last[s.host] = s
            for (ip in ips) showReach(ip, reach[ip])
            showThumbs()
            if (host == null) {
                ui.removeCallbacks(statusTick); ui.postDelayed(statusTick, EVERY_MS)
                if (TvStatus.shots) { ui.removeCallbacks(shotTick); ui.post(shotTick) }
            }
        }
    }.start()
}

// ---------------------------------------------------------------- what a row says
/** The state and the facts under a TV's name, or null to keep the plain "reachable" / "no answer" line: only on and standby add to it. */
internal fun MainActivity.statusLines(ip: String): Pair<String, String>? {
    val s = TvStatus.last[ip] ?: return null
    if (s.state != FleetStatus.ON && s.state != FleetStatus.STANDBY) return null
    val state = if (s.state == FleetStatus.STANDBY) T("tvstat.standby")
        else listOf(T("tvstat.on"), if (s.app.isEmpty()) "" else if (s.home) T("tvstat.home") else T("tvstat.showing", "app" to s.appName)).filter { it.isNotEmpty() }.joinToString(" · ")
    return "● $state" to listOf(s.model, if (s.android.isEmpty()) "" else "Android ${s.android}").filter { it.isNotEmpty() }.joinToString(" · ")
}

/** The picture slot at the end of a TV's row (hidden until "Show screens" is on and the TV is on). */
internal fun MainActivity.addThumb(ip: String, line: LinearLayout, after: View) {
    val v = ImageView(this)
    v.scaleType = ImageView.ScaleType.CENTER_CROP
    v.background = box(BTN, 8)
    v.clipToOutline = true
    v.outlineProvider = object : android.view.ViewOutlineProvider() {
        override fun getOutline(v: View, o: android.graphics.Outline) = o.setRoundRect(0, 0, v.width, v.height, dp(8).toFloat())
    }
    v.visibility = View.GONE
    TvStatus.thumbs[ip] = v
    line.addView(v, line.indexOfChild(after) + 1, LinearLayout.LayoutParams(dp(96), dp(54)).apply { leftMargin = dp(6); gravity = android.view.Gravity.CENTER_VERTICAL })
}

/** Shown for the TVs that are on while "Show screens" is on; the others hide theirs. */
internal fun MainActivity.showThumbs() {
    for ((ip, v) in TvStatus.thumbs) {
        val on = TvStatus.shots && TvStatus.last[ip]?.state == FleetStatus.ON
        v.visibility = if (on) View.VISIBLE else View.GONE
        if (!on) v.setImageDrawable(null)
    }
}

private fun MainActivity.shotRound() {
    if (host != null || isFinishing || !TvStatus.shots) return
    for ((ip, view) in TvStatus.thumbs) {
        if (TvStatus.last[ip]?.state != FleetStatus.ON || view.tag == "busy") continue
        view.tag = "busy"
        TvStatus.pool.execute {
            var bmp: Bitmap? = null
            try {
                TvStatus.gate.acquire()
                try {
                    val (addr, kind, keys) = TvStatus.resolve(ip) ?: return@execute
                    if (kind != Remote.Kind.ANDROID) return@execute
                    val png = Thumb.screen(TvStatus.link(ip, addr, keys).get())?.let { Thumb.small(it, 240) }
                    if (png != null) bmp = BitmapFactory.decodeByteArray(png, 0, png.size)
                } finally { TvStatus.gate.release() }
            } catch (ignored: Exception) {
            } finally {
                ui.post { view.tag = null; bmp?.let { if (TvStatus.shots && view.visibility == View.VISIBLE) view.setImageBitmap(it) } }
            }
        }
    }
    ui.removeCallbacks(shotTick); ui.postDelayed(shotTick, SHOT_MS)
}

/** The "Show screens" button beside the heading of the saved TVs: on (blue edge) while the pictures are asked for. */
internal fun MainActivity.screensButton(): android.widget.Button {
    TvStatus.shots = prefs.getBoolean("tvshots", false)
    val b = button("@eye|" + T("tvstat.show_screens").removePrefix("👁️").trim(), BTN)
    b.textSize = 14f
    b.typeface = android.graphics.Typeface.DEFAULT_BOLD
    b.setPadding(dp(12), 0, dp(12), 0)
    b.contentDescription = T("tvstat.show_screens_tip")
    b.setOn(TvStatus.shots)
    b.setOnClickListener {
        TvStatus.shots = !TvStatus.shots
        prefs.edit().putBoolean("tvshots", TvStatus.shots).apply()
        b.setOn(TvStatus.shots)
        showThumbs()
        ui.removeCallbacks(shotTick)
        if (TvStatus.shots) ui.post(shotTick)
    }
    return b
}
