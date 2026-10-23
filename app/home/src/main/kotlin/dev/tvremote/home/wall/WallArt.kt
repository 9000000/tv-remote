package dev.tvremote.home.wall

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import dev.tvremote.home.HomeConfig
import dev.tvremote.home.WallSpec
import org.json.JSONObject

/**
 * The drawn backgrounds ([HomeConfig.ART]): layers of soft light and hills over a sky, painted from [WallSpec] on any canvas at any
 * size, so nothing is stored and a 4K panel gets as sharp a picture as a small one. The maths is the page's (js/home_walls.js
 * `homeArtPaint`, which says what the fields mean): its previews are this picture. Dithered, or the long soft ramps would band.
 */
object WallArt {
    private class Hill(val y: Float, val amp: Float, val ridge: Boolean, val w: List<FloatArray>, val top: Int, val bot: Int, val ta: Float, val ba: Float)
    private class Glow(val disc: Boolean, val x: Float, val y: Float, val r: Float, val sy: Float, val c: Int, val a: Float)
    private class Spec(val bg: IntArray, val at: FloatArray, val ang: Float, val shade: Float, val layers: List<Any>)

    private val specs: Map<String, Spec> by lazy {
        val all = JSONObject(WallSpec.JSON)
        all.keys().asSequence().associateWith { id -> parse(all.getJSONObject(id)) }
    }

    fun has(id: String) = id in HomeConfig.ART && specs.containsKey(id)

    /** The sky's colours: what stands in while the scene is made, and the tile colour where a scene cannot be drawn. */
    fun colors(id: String) = specs[id]?.bg ?: intArrayOf(0xFF0B0F1A.toInt())

    /** How much of [Backdrop.SHADE] goes over this scene (the light ones need less). */
    fun shade(id: String) = specs[id]?.shade ?: 1f

    private fun color(h: String) = 0xFF000000.toInt() or h.substring(1).toLong(16).toInt()

    private fun parse(o: JSONObject): Spec {
        val bg = o.getJSONArray("bg")
        val layers = ArrayList<Any>()
        val ls = o.getJSONArray("layers")
        for (i in 0 until ls.length()) {
            val l = ls.getJSONObject(i)
            if (l.getString("k") == "hill") {
                val ws = l.getJSONArray("w")
                layers += Hill(l.getDouble("y").toFloat(), l.getDouble("amp").toFloat(), l.optInt("ridge") == 1,
                    List(ws.length()) { j -> ws.getJSONArray(j).let { a -> floatArrayOf(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat()) } },
                    color(l.getString("top")), color(l.getString("bot")), l.optDouble("ta", 1.0).toFloat(), l.optDouble("ba", 1.0).toFloat())
            } else layers += Glow(l.getString("k") == "disc", l.getDouble("x").toFloat(), l.getDouble("y").toFloat(), l.getDouble("r").toFloat(),
                l.optDouble("sy", 1.0).toFloat(), color(l.getString("c")), l.getDouble("a").toFloat())
        }
        return Spec(IntArray(bg.length()) { color(bg.getJSONArray(it).getString(0)) }, FloatArray(bg.length()) { bg.getJSONArray(it).getDouble(1).toFloat() },
            o.getDouble("ang").toFloat(), o.optDouble("shade", 1.0).toFloat(), layers)
    }

    private fun alpha(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24)

    /** The edge of a hill at [u] (0..1 across), as a share of the height: the same sum as the page's `homeHillY`. */
    private fun hillY(l: Hill, u: Float): Float {
        var s = 0f; var t = 0f
        for (w in l.w) {
            s += w[2] * if (l.ridge) Math.abs(Math.sin(Math.PI * w[0] * u + w[1])).toFloat() else Math.sin(2 * Math.PI * w[0] * u + w[1]).toFloat()
            t += w[2]
        }
        return l.y + l.amp * (if (l.ridge) 2 * s / t - 1 else s / t)
    }

    /** Scene [id] over the whole of [c] ([w] x [h]); no veil (the Backdrop adds its own over [shade]). */
    fun paint(c: Canvas, w: Int, h: Int, id: String) {
        val sp = specs[id] ?: return
        val p = Paint(Paint.DITHER_FLAG or Paint.ANTI_ALIAS_FLAG)
        val fw = w.toFloat(); val fh = h.toFloat()
        val rad = Math.toRadians(sp.ang.toDouble()); val dx = Math.sin(rad).toFloat(); val dy = Math.cos(rad).toFloat()
        val half = (Math.abs(dx) * fw + Math.abs(dy) * fh) / 2; val cx = fw / 2; val cy = fh / 2
        p.shader = LinearGradient(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half, sp.bg, sp.at, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, fw, fh, p)
        val path = Path()
        for (l in sp.layers) {
            if (l is Hill) {
                val top = (l.y - l.amp) * fh
                p.shader = LinearGradient(0f, top, 0f, fh, alpha(l.top, l.ta), alpha(l.bot, l.ba), Shader.TileMode.CLAMP)
                path.rewind(); path.moveTo(0f, fh)
                for (i in 0..96) path.lineTo(fw * i / 96, hillY(l, i / 96f) * fh)
                path.lineTo(fw, fh); path.close()
                c.drawPath(path, p)
            } else if (l is Glow) {
                val r = l.r * fw
                val a = l.a
                p.shader = if (l.disc) RadialGradient(0f, 0f, r, intArrayOf(alpha(l.c, a), alpha(l.c, a), alpha(l.c, 0f)), floatArrayOf(0f, 0.82f, 1f), Shader.TileMode.CLAMP)
                else RadialGradient(0f, 0f, r, intArrayOf(alpha(l.c, a), alpha(l.c, a * 0.35f), alpha(l.c, 0f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.save(); c.translate(l.x * fw, l.y * fh); c.scale(1f, l.sy)
                c.drawCircle(0f, 0f, r, p)
                c.restore()
            }
        }
    }
}
