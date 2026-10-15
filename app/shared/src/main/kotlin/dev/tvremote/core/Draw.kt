package dev.tvremote.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Drawing on the TV's screen, over whatever is on it, to show the person watching where to go. TV Setup draws (DrawLayer
 * there); a phone or computer with ADB sends the strokes. Each line sent is one JSON operation, coordinates in 0..10000 of
 * the screen's width and height:
 *   {"o":"s","id":7,"c":"#ff3b30","w":60,"p":[x,y,x,y...]}   a stroke begins (w: thickness in 1/10000 of the width)
 *   {"o":"a","id":7,"p":[x,y...]}                              more of it
 *   {"o":"i","id":8,"b":"<base64 PNG or JPEG>","x":5000,"y":5000,"w":4000}   a picture centred at x,y, w wide, as tall as its shape
 *   {"o":"u"} the last stroke or picture away · {"o":"x"} all of them · {"o":"h"} all, and the layer itself
 * The way in: the shell asks TV Setup (a broadcast only the shell may send) for its port on 127.0.0.1 and a secret, then
 * ADB opens that port on the TV ("tcp:"): quick enough to follow a finger, and nothing listens beyond the TV itself.
 */
class Draw(private val adb: () -> Adb) {
    companion object {
        const val RECEIVER = RulesClient.SETUP + "/.DrawReceiver"
        const val MAX_BODY = 6_000_000                                // a picture, sent at most 1280 pixels wide
    }

    private var stream: Adb.Stream? = null
    private var from: Adb? = null
    private var pics = false                                          // this TV Setup draws pictures too (2.21.0 and later)

    /** Sends the operations, opening the way first when needed; once more on a fresh connection if the old one broke. */
    @Synchronized @Throws(IOException::class)
    fun send(ops: JSONArray) {
        val text = StringBuilder()
        for (i in 0 until ops.length()) text.append(ops.getJSONObject(i).toString()).append('\n')
        val bytes = text.toString().toByteArray(Charsets.UTF_8)
        val pic = (0 until ops.length()).any { ops.getJSONObject(it).optString("o") == "i" }
        for (attempt in 0..1) {
            val s = try { open().also { if (pic && !pics) throw IOException("draw:oldpic") } } catch (e: IOException) { if (attempt == 1 || e.message == "draw:oldpic") throw e; close(); continue }
            try { s.write(bytes); return } catch (e: IOException) { close(); if (attempt == 1) throw e }
        }
    }

    @Synchronized fun close() {
        try { stream?.close() } catch (e: Exception) { }
        stream = null
    }

    private fun open(): Adb.Stream {
        val a = adb()
        stream?.let { if (from === a && !a.isDead()) return it }
        close()
        // the layer is a window over other apps: the shell may allow that for TV Setup, where the TV has no screen for it
        val out = a.shell("appops set ${RulesClient.SETUP} SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1; " +
            "am broadcast --include-stopped-packages -n $RECEIVER 2>&1", 20000)
        val m = Regex("data=\"([A-Za-z0-9+/=]+)\"").find(out)
        if (m == null) throw IOException(if (a.shell("pm path ${RulesClient.SETUP} 2>/dev/null").contains("package:")) "draw:old" else "draw:missing")
        val o = JSONObject(String(B64.decode(m.groupValues[1]), Charsets.UTF_8))
        if (!o.optBoolean("overlay")) throw IOException("draw:nooverlay")
        pics = o.optBoolean("pic")
        val s = a.open("tcp:" + o.getInt("port"))
        s.write((o.getString("token") + "\n").toByteArray(Charsets.UTF_8))
        stream = s
        from = a
        return s
    }
}
