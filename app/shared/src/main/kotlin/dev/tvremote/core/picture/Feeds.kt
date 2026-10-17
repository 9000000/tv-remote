package dev.tvremote.core.picture
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import dev.tvremote.core.web.HttpServer

/**
 * The TV's picture and sound for the page (js/screen.js h264Loop, js/listen.js), each to one browser at a time: a new one
 * takes over. Both are packets of 1 byte kind, 4 bytes length, then the data, sent as long as the browser reads them. The
 * session itself is made by the caller ([Tv] on the TV for TV Setup, on the computer for the desktop server).
 */
class Feeds {
    /** The session that carries the picture, only while a browser watches: touches go to it. */
    @Volatile var viewer: Tv? = null
        private set
    @Volatile private var hearer: Tv? = null

    private val end = ByteArray(0)

    private fun packet(kind: Int, data: ByteArray) = CatchUp.Packet(kind, CatchUp.packet(kind, data), 0)
    private val endPacket = CatchUp.Packet(-1, end, 0)

    /**
     * Kinds: 0 size (width, height), 1 codec config, 2 key frame, 3 frame, 4 nothing new, 5 it fell behind and caught up
     * ([CatchUp]: the page steps the picture down after a few). A still picture sends nothing, so kind 4 goes out now and
     * then: that is how we notice the browser has left, and stop the encoder on the TV.
     */
    fun video(make: (Tv.Listener, Tv.Sink) -> Tv): HttpServer.Response {
        viewer?.stop()
        var self: Tv? = null
        val c = CatchUp(LAG_MS) { self?.resetVideo() }
        val q = c.q
        val v = make(object : Tv.Listener {
            override fun onStatus(key: String, detail: String) { if (key == "closed" || key == "error") c.put(endPacket) }
            override fun onVideoSize(w: Int, h: Int) { c.put(packet(0, ByteBuffer.allocate(8).putInt(w).putInt(h).array())) }
            override fun onNetwork(mac: String, lan: String, tailscale: String) { }
        }, Tv.Sink { config, key, data ->
            if (q.size > 150) { if (q.size < 160) c.put(endPacket); self?.stop() }   // still too much (no key frame came): stop instead of filling memory
            else c.add(if (config) 1 else if (key) 2 else 3, data, System.currentTimeMillis())
        })
        self = v
        viewer = v
        // the system grows a socket's send buffer to megabytes: seconds of picture waiting where CatchUp cannot see them
        return pump("v", q, v, { viewer }) { if (viewer === v) viewer = null }.apply { sendBuffer = 64 * 1024 }
    }

    /**
     * Kinds: 0 the format (codec 1 Opus or 2 PCM, then the rate and the channels), 1 Opus's header, 2 a piece of sound, 3 a
     * notice ("noshare": the TV's speakers go silent meanwhile), 4 nothing new, 5 an error word. With [opus] the session is
     * asked for "opus" (a browser on a secure page decodes it: the computer's 127.0.0.1); otherwise for "raw" and the TV
     * encodes nothing: scrcpy hands over PCM, made 24 kHz mono here ([Downmix], 48 KB/s) unless [hq] (as captured, 48 kHz
     * stereo, fine on the home network).
     */
    fun sound(hq: Boolean, opus: Boolean, make: (Tv.Listener, Tv.AudioSink) -> Tv): HttpServer.Response {
        hearer?.stop()
        val q = LinkedBlockingQueue<CatchUp.Packet>()
        val whole = opus || hq
        q.add(packet(0, ByteBuffer.allocate(6).put((if (opus) 1 else 2).toByte()).putInt(if (whole) 48000 else 24000).put((if (whole) 2 else 1).toByte()).array()))
        val mix = Downmix()
        val h = make(object : Tv.Listener {
            override fun onStatus(key: String, detail: String) {
                if (key == "audio") q.add(if (detail == "noshare") packet(3, detail.toByteArray()) else packet(5, "unsupported".toByteArray()))
                if (key == "closed" || key == "error") { if (key == "error") q.add(packet(5, detail.toByteArray())); q.add(endPacket) }
            }
            override fun onVideoSize(w: Int, h: Int) { }
            override fun onNetwork(mac: String, lan: String, tailscale: String) { }
        }, Tv.AudioSink { config, data ->
            if (config) { if (opus) q.add(packet(1, data)); return@AudioSink }
            if (q.size > 200) { if (q.size < 210) q.add(endPacket) } else q.add(CatchUp.Packet(2, CatchUp.packet(2, if (whole) data else mix.feed(data)), System.currentTimeMillis()))   // the browser cannot keep up: stop instead of filling memory
        })
        hearer = h
        return pump("a", q, h, { hearer }) { if (hearer === h) hearer = null }
    }

    private fun pump(tag: String, q: LinkedBlockingQueue<CatchUp.Packet>, t: Tv, now: () -> Tv?, done: () -> Unit) =
        HttpServer.Response.stream("application/octet-stream", -1) { out ->
            try {
                while (true) {
                    val p = q.poll(5, TimeUnit.SECONDS) ?: packet(4, end)
                    if (p === endPacket || now() !== t) break          // ended, or another browser took over
                    out.write(p.bytes)
                    out.flush()
                    if (Trace.on && p.at > 0) Trace.log("w", "$tag kind=${p.kind} age=${System.currentTimeMillis() - p.at} n=${p.bytes.size - 5}")
                }
            } finally {
                t.stop()
                done()
            }
        }

    fun stop() {
        viewer?.stop()
        hearer?.stop()
    }

    companion object {
        /** How far behind the TV the picture may fall before it jumps to now: 6 frames at the usual 10 a second */
        const val LAG_MS = 600L
    }
}
