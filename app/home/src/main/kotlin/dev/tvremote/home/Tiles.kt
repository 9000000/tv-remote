package dev.tvremote.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Live tiles: a tile another app provides (TV Setup's "Free RAM"), drawn in the app grid at an app tile's size with its own render,
 * pressed like an app. This file is the small API the rest of TV Home (and the "Add to home screen" browser) uses, apart from any view:
 *   [available]  what the provider offers now: [Info] (key, title, kind), from the last [refresh]; [status] says why the list is empty
 *   [fetch]      one tile's state now (the provider's JSON: mode live|working|done|failed, big, small, progress, accent, result)
 *   [run]        its action (the press); [press] is fetch-and-follow for a screen
 * A tile's key is "tile:<provider package>/<id>" ([HomeConfig.tileKey]) and sits in the config's "tiles" (which are on the grid) and
 * in dock / hidden / order like an app's package name. The provider answers only TV Home, by its signing key (TV Setup's HomeInfo).
 * Calls to it are binder calls to another app: never on the main thread.
 */
object Tiles {
    class Info(val key: String, val title: String, val kind: String)

    const val OK = "ok"                                                 // the provider answered with its list
    const val MISSING = "missing"                                       // no TV Setup, or one older than the tiles
    const val UNTRUSTED = "untrusted"                                   // TV Setup is there but signed by another key
    const val EVERY_MS = 5000L                                          // how often a tile on screen is read again
    const val FOLLOW_MS = 350L                                          // ... while its action runs

    private fun prefs(c: Context) = c.getSharedPreferences("home_tiles", Context.MODE_PRIVATE)

    /** The provider's content address by its package: "<package>.home", as TV Setup's HomeInfo is "dev.tvremote.setup.home" ([HomeConfig.USAGE]). */
    fun uri(provider: String): Uri = Uri.parse("content://$provider.home")

    /** TV Setup, the one provider so far; others would be asked the same way. */
    val PROVIDERS = listOf("dev.tvremote.setup")

    private fun call(c: Context, provider: String, method: String, arg: String?): android.os.Bundle? = try {
        c.contentResolver.call(uri(provider), method, arg, android.os.Bundle().apply { putString("lang", Locale.getDefault().language) })
    } catch (e: Exception) { null }                                     // no such provider, or one that does not know this word

    /** Asks every provider for its tiles and keeps the answer; returns whether it changed (the grid and the config are then read again). */
    fun refresh(c: Context): Boolean {
        val all = JSONArray()
        var state = MISSING
        for (p in PROVIDERS) {
            val b = call(c, p, "tiles", null)
            when {
                b == null -> {}
                b.getString("result") == "untrusted" -> if (state == MISSING) state = UNTRUSTED
                else -> try {
                    val l = JSONArray(b.getString("json") ?: "[]")
                    for (i in 0 until l.length()) l.getJSONObject(i).let { all.put(JSONObject().put("key", HomeConfig.tileKey(p, it.getString("id")))
                        .put("title", it.optString("title")).put("kind", it.optString("kind"))) }
                    state = OK
                } catch (e: Exception) { }
            }
        }
        val old = prefs(c).getString("list", "") + prefs(c).getString("state", "")
        val now = all.toString() + state
        prefs(c).edit().putString("list", all.toString()).putString("state", state).apply()
        return old != now
    }

    fun status(c: Context): String = prefs(c).getString("state", MISSING) ?: MISSING

    /** What the providers offered at the last [refresh]; empty when [status] is not [OK]. */
    fun available(c: Context): List<Info> = try {
        val l = JSONArray(prefs(c).getString("list", "[]"))
        List(l.length()) { l.getJSONObject(it).let { o -> Info(o.getString("key"), o.optString("title"), o.optString("kind")) } }
    } catch (e: Exception) { emptyList() }

    /** The name to show for [key]: the provider's, else (a tile set from a phone before the provider was seen) its id. */
    fun title(c: Context, key: String): String = available(c).firstOrNull { it.key == key }?.title?.takeIf { it.isNotEmpty() } ?: HomeConfig.tileId(key)

    /** The tiles that are on the grid (config "tiles"), as tiles' apps; they show even while their provider is away, saying so. */
    fun apps(c: Context, conf: JSONObject): List<App> {
        val a = conf.optJSONArray("tiles") ?: return emptyList()
        return List(a.length()) { a.getString(it) }.map { App(it, title(c, it), null) }
    }

    /** The config the clients read gains: "tilesOk", "tilesAvail" [{key, title, kind}] and "tilesState" ([OK] / [MISSING] / [UNTRUSTED]). */
    fun report(c: Context, conf: JSONObject): JSONObject {
        val l = JSONArray()
        for (t in available(c)) l.put(JSONObject().put("key", t.key).put("title", t.title).put("kind", t.kind))
        return conf.put("tilesOk", true).put("tilesAvail", l).put("tilesState", status(c))
    }

    private val last = HashMap<String, JSONObject>()                   // the latest state of each tile, so a rebuilt grid starts with it

    fun cached(key: String): JSONObject? = synchronized(last) { last[key] }

    /** One tile's state now (a binder call: a thread of its own); a state with mode "missing" / "untrusted" when its provider does not answer. */
    fun fetch(c: Context, key: String): JSONObject {
        val b = call(c, HomeConfig.tileProvider(key), "tile", HomeConfig.tileId(key))
        val s = when {
            b == null -> JSONObject().put("mode", MISSING)
            b.getString("result") == "untrusted" -> JSONObject().put("mode", UNTRUSTED)
            else -> try { JSONObject(b.getString("json") ?: "") } catch (e: Exception) { JSONObject().put("mode", MISSING) }
        }
        synchronized(last) { last[key] = s }
        return s
    }

    /** Runs the tile's action: true when it started (or was already running), false when the provider is missing, untrusted or refused. */
    fun run(c: Context, key: String): Boolean {
        val b = call(c, HomeConfig.tileProvider(key), "tile_do", HomeConfig.tileId(key)) ?: return false
        if (b.getString("result") == "untrusted") return false
        return try { JSONObject(b.getString("json") ?: return false).let { it.optBoolean("started") || it.optBoolean("busy") } } catch (e: Exception) { false }
    }

    /**
     * A press for a screen: starts the action, then reads the tile every [FOLLOW_MS] until it is no longer "working" (at most
     * [FOLLOW_TRY] times), each state handed to [onState]. Call it from a thread of its own; false when it could not start.
     */
    fun press(c: Context, key: String, onState: (JSONObject) -> Unit): Boolean {
        if (!run(c, key)) { onState(fetch(c, key)); return false }
        Thread.sleep(FOLLOW_MS)
        var n = 0
        while (n++ < FOLLOW_TRY) {
            val s = fetch(c, key); onState(s)
            if (s.optString("mode") != "working") break
            Thread.sleep(FOLLOW_MS)
        }
        return true
    }
    private const val FOLLOW_TRY = 40
}


/**
 * The face of a live tile, a sibling of the app banners next to it: a bright gradient, an icon and the name on the left, a ring
 * gauge with its value in the middle on the right, and one short line under the name. Any provider's state fits it: title, big
 * (the value), small (the line), progress (the ring; none = the value alone), accent (ok / warn / bad: the ring's tint, and the
 * gradient turns amber / red), and optionally icon (a drawable name, "memory"), tone (the gradient family, "clean") and note (a
 * shorter line shown instead of small, e.g. "-37 MB" after a press). A press shows the outcome as the ring running down to the
 * new value (about a second, once per outcome) and, while the provider works, a spinner turning on the ring; with motion off or
 * calm (a weak box) the numbers just change.
 */
class LiveFace(c: Context, private val w: Int, private val h: Int, private val title: String, private val say: (String) -> String) : View(c) {
    private companion object {
        const val INK = 0xFFFFFFFF.toInt(); const val LINE_INK = 0xE6FFFFFF.toInt(); const val TRACK = 0x40FFFFFF       // white at 100 / 90 / 25 %
        const val RING_OK = 0xFFFFFFFF.toInt(); const val RING_WARN = 0xFFFFF1BF.toInt(); const val RING_BAD = 0xFFFFD9D6.toInt()   // light tints: readable on every gradient below
        val CLEAN = intArrayOf(0xFF16A34A.toInt(), 0xFF0D6B73.toInt())                                                  // clean / memory: green into teal
        val BLUE = intArrayOf(0xFF3B73F6.toInt(), 0xFF1E3A8A.toInt())                                                   // TV Setup's family, for a tone nobody named
        val AMBER = intArrayOf(0xFFD97706.toInt(), 0xFF9A3412.toInt())
        val RED = intArrayOf(0xFFDC2626.toInt(), 0xFF9F1239.toInt())
        val IDLE = intArrayOf(0xFF2A3446.toInt(), 0xFF171E2B.toInt())                                                   // no answer from the provider
        const val SPIN_MS = 1100L
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private var big = ""; private var small = ""; private var mode = "live"; private var accent = "ok"; private var tone = ""
    private var hasRing = true
    private var icon: Drawable? = null; private var iconName = ""
    private var progress = 0f                                           // what the ring shows now (moves during the animation)
    private var seenAt = 0L                                             // the outcome already animated
    private var anim: ValueAnimator? = null
    private var spin: ValueAnimator? = null
    private var turn = 0f                                               // the spinner's angle
    private var motion = false
    private var gradient: LinearGradient? = null; private var gradientFor: IntArray? = null
    private val u = h / 100f

    /** Shows [s] (a provider's state; none = not answered); [animate]: motion is on and not calm. [say] gives the line for a provider that is missing or untrusted. */
    fun set(s: JSONObject?, animate: Boolean) {
        motion = animate
        val m = s?.optString("mode").orEmpty()
        if (s == null || m == Tiles.MISSING || m == Tiles.UNTRUSTED || m.isEmpty()) {
            anim?.cancel(); spinning(false)
            mode = m.ifEmpty { Tiles.MISSING }; big = "—"; small = say(mode); progress = 0f; hasRing = true; tone = "idle"; invalidate(); return
        }
        accent = s.optString("accent", "ok"); mode = m; tone = s.optString("tone")
        small = s.optString("note").ifEmpty { s.optString("small") }
        hasRing = s.has("progress")
        icon(s.optString("icon"))
        spinning(m == "working" && animate)
        val p = s.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f)
        val r = s.optJSONObject("result")
        if (m == "done" && r != null && r.optLong("at") != seenAt) {
            seenAt = r.optLong("at")
            if (animate && r.optInt("before") != r.optInt("after")) { drop(r.optInt("before") / 100f, p); return }
        }
        if (anim?.isRunning == true) return                             // the count-down is on: it ends on the same numbers
        progress = p; big = s.optString("big"); invalidate()
    }

    private fun icon(name: String) {
        if (name == iconName) return
        iconName = name
        val id = if (name.isEmpty()) 0 else resources.getIdentifier("ic_$name", "drawable", context.packageName)
        icon = if (id == 0) null else context.getDrawable(id)?.mutate()?.also { it.setTint(INK) }
    }

    private fun drop(from: Float, to: Float) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(from, to).apply {
            duration = 1100; interpolator = android.view.animation.DecelerateInterpolator(1.6f)
            addUpdateListener { progress = it.animatedValue as Float; big = "${Math.round(progress * 100f)}%"; invalidate() }
            start()
        }
    }

    /** The spinner turns only while the provider works (and motion is on); one animator, stopped when the tile goes. */
    private fun spinning(on: Boolean) {
        if (on == (spin?.isRunning == true)) return
        spin?.cancel(); spin = null
        if (!on) { turn = 0f; return }
        spin = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = SPIN_MS; repeatCount = ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { turn = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun ringTint(): Int = when (accent) { "bad" -> RING_BAD; "warn" -> RING_WARN; else -> RING_OK }

    private fun colours(): IntArray = when {
        tone == "idle" -> IDLE
        accent == "bad" -> RED
        accent == "warn" -> AMBER
        tone == "clean" -> CLEAN
        else -> BLUE
    }

    override fun onDetachedFromWindow() { anim?.cancel(); spin?.cancel(); spin = null; super.onDetachedFromWindow() }

    override fun onDraw(cv: Canvas) {
        val r = 10f * u
        val col = colours()
        if (gradientFor !== col) { gradientFor = col; gradient = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), col[0], col[1], Shader.TileMode.CLAMP) }
        box.set(0f, 0f, w.toFloat(), h.toFloat())
        paint.shader = gradient; paint.style = Paint.Style.FILL
        cv.drawRoundRect(box, r, r, paint); paint.shader = null

        val pad = 12f * u
        val ring = if (hasRing) 29f * u else 0f                         // the ring's outer radius; a state without progress gives the room to the words
        val ringX = w - pad - ring
        val textW = if (hasRing) ringX - ring - pad - 6f * u else w - 2 * pad - 40f * u
        // left block: icon, name, line: centred in the tile's height as a whole
        val ic = if (icon != null) 24f * u else 0f
        val gap = if (ic > 0f) 7f * u else 0f
        val lineH = 12f * u
        val block = ic + gap + 17f * u + 4f * u + lineH
        var y = (h - block) / 2f
        icon?.let { it.setBounds(pad.toInt(), y.toInt(), (pad + ic).toInt(), (y + ic).toInt()); it.draw(cv) }
        y += ic + gap
        paint.typeface = Font.bold; paint.textSize = 16f * u; paint.color = INK
        cv.drawText(fit(title, textW), pad, y + 14f * u, paint)
        y += 17f * u + 4f * u
        paint.typeface = Font.medium; paint.textSize = 11.5f * u; paint.color = LINE_INK
        cv.drawText(fit(small, textW + (if (hasRing) 0f else 40f * u)), pad, y + 10f * u, paint)

        val cy = h / 2f
        if (!hasRing) {                                                 // no progress: the value alone, big, on the right
            paint.typeface = Font.bold; paint.textSize = 30f * u; paint.color = INK; paint.textAlign = Paint.Align.RIGHT
            cv.drawText(big, w - pad, cy + 10f * u, paint); paint.textAlign = Paint.Align.LEFT
            return
        }
        val sw = 7f * u
        val rr = ring - sw / 2f
        box.set(ringX - rr, cy - rr, ringX + rr, cy + rr)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = sw; paint.strokeCap = Paint.Cap.ROUND
        paint.color = TRACK; cv.drawArc(box, 0f, 360f, false, paint)
        val working = mode == "working"
        paint.color = ringTint()
        if (working) paint.alpha = if (spin != null) 90 else 160         // the value fades back while the spinner (or, calm, a plain dim) says "busy"
        if (progress > 0f) cv.drawArc(box, -90f, 360f * progress, false, paint)
        if (working && spin != null) { paint.alpha = 255; cv.drawArc(box, turn - 90f, 70f, false, paint) }
        paint.style = Paint.Style.FILL; paint.strokeCap = Paint.Cap.BUTT; paint.alpha = 255
        paint.typeface = Font.bold; paint.color = INK; paint.textAlign = Paint.Align.CENTER
        paint.textSize = 17f * u
        val room = 2f * (rr - sw / 2f) - 2f * u
        while (paint.measureText(big) > room && paint.textSize > 8f * u) paint.textSize -= u
        cv.drawText(big, ringX, cy + paint.textSize * 0.36f, paint); paint.textAlign = Paint.Align.LEFT
    }

    private fun fit(t: String, room: Float): String {
        if (paint.measureText(t) <= room) return t
        var s = t
        while (s.length > 1 && paint.measureText("$s…") > room) s = s.dropLast(1)
        return "$s…"
    }
}
