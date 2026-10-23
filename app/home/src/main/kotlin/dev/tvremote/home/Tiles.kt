package dev.tvremote.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
 * The face of a live tile: its title, a big value, a line under it and a bar, in the accent the provider names. A press shows the
 * outcome as the bar dropping and the number counting down (about a second), once per outcome; with motion off or calm (a weak
 * box) the numbers just change.
 */
class LiveFace(c: Context, private val w: Int, private val h: Int, private val title: String, private val say: (String) -> String) : View(c) {
    private companion object {
        const val TITLE_INK = 0xB3FFFFFF.toInt(); const val LINE_INK = 0xCCFFFFFF.toInt(); const val TRACK = 0x33FFFFFF   // white at 70 / 80 / 20 %
        const val GOOD = 0xFF4CC38A.toInt(); const val WARN = 0xFFF2B84B.toInt(); const val BAD = 0xFFE5646B.toInt()    // the accents a provider names
        const val FACE_TOP = 0xFF1B2538.toInt(); const val FACE_BOTTOM = 0xFF111827.toInt()                          // the tile's dark glass
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private var big = ""; private var small = ""; private var mode = "live"; private var accent = "ok"
    private var progress = 0f                                           // what the bar shows now (moves during the animation)
    private var shownPct = -1f                                          // what the number shows now
    private var seenAt = 0L                                             // the outcome already animated
    private var anim: ValueAnimator? = null
    private val u = h / 100f

    /** Shows [s] (a provider's state; none = not answered); [animate]: motion is on and not calm. [say] gives the line for a provider that is missing or untrusted. */
    fun set(s: JSONObject?, animate: Boolean) {
        val m = s?.optString("mode").orEmpty()
        if (s == null || m == Tiles.MISSING || m == Tiles.UNTRUSTED || m.isEmpty()) {
            anim?.cancel(); mode = m.ifEmpty { Tiles.MISSING }; big = "—"; small = say(mode); progress = 0f; shownPct = -1f; invalidate(); return
        }
        accent = s.optString("accent", "ok"); small = s.optString("small"); mode = m
        val p = s.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f)
        val r = s.optJSONObject("result")
        if (m == "done" && r != null && r.optLong("at") != seenAt) {
            seenAt = r.optLong("at")
            val from = r.optInt("before") / 100f
            if (animate && r.optInt("before") != r.optInt("after")) { drop(from, p); return }
        }
        if (anim?.isRunning == true) return                             // the count-down is on: it ends on the same numbers
        progress = p; shownPct = p * 100f; big = s.optString("big"); invalidate()
    }

    private fun drop(from: Float, to: Float) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(from, to).apply {
            duration = 1100; interpolator = android.view.animation.DecelerateInterpolator(1.6f)
            addUpdateListener { progress = it.animatedValue as Float; shownPct = progress * 100f; big = "${Math.round(shownPct)}%"; invalidate() }
            start()
        }
    }

    private fun tint(): Int = when (accent) { "bad" -> BAD; "warn" -> WARN; else -> GOOD }

    override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(cv: Canvas) {
        val r = 10f * u
        box.set(0f, 0f, w.toFloat(), h.toFloat())
        paint.shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), FACE_TOP, FACE_BOTTOM, android.graphics.Shader.TileMode.CLAMP)
        cv.drawRoundRect(box, r, r, paint); paint.shader = null
        val pad = 7f * u
        paint.typeface = Font.medium; paint.color = TITLE_INK; paint.textSize = 11f * u
        cv.drawText(fit(title, w - 2 * pad), pad, pad + 10f * u, paint)
        paint.color = Color.WHITE; paint.textSize = 36f * u; paint.typeface = Font.bold
        cv.drawText(big, pad, pad + 54f * u, paint)
        paint.typeface = Font.medium; paint.textSize = 11f * u
        paint.color = if (mode == "done" || mode == "working") tint() else LINE_INK
        cv.drawText(fit(small, w - 2 * pad), pad, h - pad - 12f * u, paint)
        val by = h - pad - 5f * u
        paint.color = TRACK
        box.set(pad, by, w - pad, by + 5f * u); cv.drawRoundRect(box, 3f * u, 3f * u, paint)
        paint.color = tint()
        box.set(pad, by, pad + (w - 2 * pad) * progress, by + 5f * u); cv.drawRoundRect(box, 3f * u, 3f * u, paint)
    }

    private fun fit(t: String, room: Float): String {
        if (paint.measureText(t) <= room) return t
        var s = t
        while (s.length > 1 && paint.measureText("$s…") > room) s = s.dropLast(1)
        return "$s…"
    }
}
