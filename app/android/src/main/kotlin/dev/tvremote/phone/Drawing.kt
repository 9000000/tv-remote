package dev.tvremote.phone

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import dev.tvremote.core.Errors
import org.json.JSONArray
import org.json.JSONObject

// ✏️ Drawing on the TV's screen, as on the web page (draw.js): while it is on, the finger on the picture draws instead of
// touching the TV, and TV Setup draws the same strokes over whatever the TV shows, for the person watching (DrawLayer.kt there,
// Draw.kt for the way). The strokes stay on the TV until cleared, or 10 minutes; here they fade once the finger lifts, since
// the TV's own picture shows them by then.

internal val DRAW_COLORS = intArrayOf(0xffff3b30.toInt(), 0xffffd60a.toInt(), 0xff32d74b.toInt(), 0xff40c8ff.toInt(), Color.WHITE)

internal fun MainActivity.startDrawing() {
    if (basic != null || tv == null) {
        Toast.makeText(this, t("Vẽ lên TV cần ADB, như xem hình TV.", "Drawing on the TV needs ADB, like the TV picture."), Toast.LENGTH_LONG).show()
        return
    }
    if (!video) toggleVideo()                                         // the strokes go where the picture shows
    drawing = true
    drawBar.visibility = View.VISIBLE
}

internal fun MainActivity.stopDrawing() {
    drawing = false
    drawBar.visibility = View.GONE
    drawView.clear()
    synchronized(drawQ) { drawQ.clear() }
    drawIo.execute { drawer.close() }
}

/** The colours, thickness, undo, clear and done: a row under the picture, hidden until drawing starts. */
internal fun MainActivity.drawTools(): LinearLayout {
    val bar = LinearLayout(this)
    bar.gravity = Gravity.CENTER_VERTICAL
    bar.setPadding(dp(8), dp(8), dp(8), dp(8))
    bar.visibility = View.GONE
    val swatches = ArrayList<View>()
    val ring = { v: View, on: Boolean ->
        (v.background as GradientDrawable).setStroke(dp(if (on) 3 else 1), if (on) TEXT else 0x66ffffff)
    }
    for (c in DRAW_COLORS) {
        val s = View(this)
        s.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }
        ring(s, c == drawColor)
        s.contentDescription = t("Màu", "Colour")
        s.setOnClickListener { drawColor = c; swatches.forEach { ring(it, it === s) } }
        swatches.add(s)
        bar.addView(s, LinearLayout.LayoutParams(dp(26), dp(26)).also { it.rightMargin = dp(6) })
    }
    bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
    val thick = button("━", BTN)
    thick.contentDescription = t("Nét to", "Thick line")
    thick.setOnClickListener {
        drawWidth = if (drawWidth == 60) 140 else 60
        thick.background = box(if (drawWidth > 60) ACCENT else BTN, 14)
    }
    val undo = icon("@undo", t("Bỏ nét vừa vẽ", "Take back the last line"), BTN)
    undo.setOnClickListener { drawSend(JSONObject().put("o", "u")) }
    val clear = icon("@trash", t("Xóa hết", "Clear all"), BTN)
    clear.setOnClickListener { drawView.clear(); drawSend(JSONObject().put("o", "x")) }
    val done = icon("@check", t("Xong", "Done"), ACCENT)
    done.setOnClickListener { stopDrawing() }
    for (b in listOf(thick, undo, clear, done)) bar.addView(b, LinearLayout.LayoutParams(dp(40), dp(40)).also { it.leftMargin = dp(4) })
    return bar
}

/** The finger on the picture, in 0..10000 of the TV's screen (the picture is the TV's screen, letterboxed by its frame). */
internal fun MainActivity.drawTouch(e: MotionEvent, v: View) {
    val x = (e.x / v.width * 10000).toInt().coerceIn(0, 10000)
    val y = (e.y / v.height * 10000).toInt().coerceIn(0, 10000)
    when (e.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            val id = ++drawId
            drawView.start(drawColor, drawWidth, x, y)
            drawSend(JSONObject().put("o", "s").put("id", id).put("c", String.format("#%06x", drawColor and 0xffffff)).put("w", drawWidth).put("p", JSONArray().put(x).put(y)))
        }
        MotionEvent.ACTION_MOVE -> if (drawView.more(x, y)) drawSend(JSONObject().put("o", "a").put("id", drawId).put("p", JSONArray().put(x).put(y)))
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drawView.lift()
    }
}

/** One request at a time; what comes meanwhile goes in the next, so a slow link never falls behind the finger. */
internal fun MainActivity.drawSend(op: JSONObject) {
    synchronized(drawQ) {
        val last = drawQ.lastOrNull()
        if (op.optString("o") == "a" && last != null && last.optString("o") in setOf("s", "a") && last.optInt("id") == op.optInt("id")) {
            val p = op.getJSONArray("p")
            for (i in 0 until p.length()) last.getJSONArray("p").put(p.get(i))
        } else drawQ.add(op)
        if (drawBusy) return
        drawBusy = true
    }
    drawIo.execute {
        while (true) {
            val ops = synchronized(drawQ) {
                if (drawQ.isEmpty()) { drawBusy = false; return@execute }
                JSONArray(drawQ).also { drawQ.clear() }
            }
            try { drawer.send(ops) } catch (e: Exception) {
                synchronized(drawQ) { drawQ.clear(); drawBusy = false }
                val m = e.message ?: ""
                val why = when {
                    "draw:old" in m -> t("TV Setup trên TV cần bản mới hơn để vẽ: cập nhật TV Setup rồi thử lại.", "TV Setup on the TV needs a newer version to draw: update TV Setup, then try again.")
                    "draw:missing" in m -> t("Cần cài TV Setup lên TV để vẽ lên màn hình.", "TV Setup is needed on the TV to draw on its screen.")
                    "draw:nooverlay" in m -> t("TV này không cho app vẽ đè lên màn hình.", "This TV does not let apps draw over its screen.")
                    else -> t("Không vẽ được lên TV: ", "Could not draw on the TV: ") + Errors.say(e, VI)
                }
                ui.post { if (drawing) stopDrawing(); Toast.makeText(this, why, Toast.LENGTH_LONG).show() }
                return@execute
            }
        }
    }
}

/** What the finger draws, here on the phone: each stroke fades soon after it is lifted. */
internal class DrawView(c: Context) : View(c) {
    private class Stroke(val color: Int, val width: Int) { val pts = ArrayList<Int>(); var upAt = 0L }
    private val strokes = ArrayList<Stroke>()
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()

    init { isClickable = false; isFocusable = false }                 // the touches go on to the picture

    fun start(color: Int, width: Int, x: Int, y: Int) { strokes.add(Stroke(color, width).also { it.pts += x; it.pts += y }); invalidate() }

    /** A point more, if it is far enough from the last one to be worth sending. */
    fun more(x: Int, y: Int): Boolean {
        val s = strokes.lastOrNull()?.takeIf { it.upAt == 0L } ?: return false
        if (Math.abs(x - s.pts[s.pts.size - 2]) + Math.abs(y - s.pts[s.pts.size - 1]) < 25) return false
        s.pts += x; s.pts += y
        invalidate()
        return true
    }

    fun lift() { strokes.lastOrNull()?.let { if (it.upAt == 0L) it.upAt = System.currentTimeMillis() }; invalidate() }

    fun clear() { strokes.clear(); invalidate() }

    override fun onDraw(c: Canvas) {
        val now = System.currentTimeMillis()
        strokes.removeAll { it.upAt > 0 && now - it.upAt > 1600 }
        val w = width / 10000f
        val h = height / 10000f
        for (s in strokes) {
            path.reset()
            path.moveTo(s.pts[0] * w, s.pts[1] * h)
            if (s.pts.size == 2) path.lineTo(s.pts[0] * w + 0.1f, s.pts[1] * h)
            var i = 2
            while (i + 1 < s.pts.size) { path.lineTo(s.pts[i] * w, s.pts[i + 1] * h); i += 2 }
            pen.color = s.color
            pen.alpha = if (s.upAt > 0) (255 * (1 - (now - s.upAt) / 1600f)).toInt().coerceIn(0, 255) else 255
            pen.strokeWidth = maxOf(2f, s.width * w)
            c.drawPath(path, pen)
        }
        if (strokes.any { it.upAt > 0 }) postInvalidateOnAnimation()
    }
}
