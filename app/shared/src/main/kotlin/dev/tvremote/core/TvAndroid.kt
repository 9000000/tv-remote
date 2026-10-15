package dev.tvremote.core

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.view.Surface

/**
 * A [Tv] session on Android: the key in the app's files, scrcpy-server from the APK's assets, the picture decoded straight
 * onto [surface] and "opus" sound played here ([AudioOut]) unless an [audioSink] takes it.
 */
fun Tv(
    ctx: Context, host: String, surface: Surface?, quality: Int, maxSide: Int, wake: Boolean, listener: Tv.Listener,
    sink: Tv.Sink? = null, audio: String? = null, audioSink: Tv.AudioSink? = null, wantControl: Boolean = true,
    audioShare: Boolean = false, audioBitRate: Int = 64000,
): Tv {
    val app = ctx.applicationContext
    val hear = audioSink ?: if (audio == "opus") object : Tv.AudioSink {
        private val out = AudioOut()
        override fun audio(config: Boolean, data: ByteArray) = out.feed(config, data)
        override fun end() = out.release()
    } else null
    return Tv(app.filesDir, { app.assets.open("scrcpy-server.jar").use { it.readBytes() } }, host, surface?.let { SurfaceScreen(it) },
        quality, maxSide, wake, listener, sink, audio, hear, wantControl, audioShare, audioBitRate)
}

/** Android's own H.264 decoder, drawing onto a Surface. */
private class SurfaceScreen(private val surface: Surface) : Tv.Screen {
    private var codec: MediaCodec? = null

    override fun start(w: Int, h: Int) {
        release()
        val f = MediaFormat.createVideoFormat("video/avc", w, h)
        f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2 shl 20)
        if (Build.VERSION.SDK_INT >= 30) f.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        val c = MediaCodec.createDecoderByType("video/avc")
        codec = c
        c.configure(f, surface, null, 0)
        c.start()
    }

    override fun feed(config: Boolean, pts: Long, data: ByteArray): Boolean {
        val c = codec ?: return true
        var idx = -1
        var waited = 0
        while (idx < 0 && waited < 500) { idx = c.dequeueInputBuffer(20000); render(c); waited++ }   // 10 s or more: stuck
        if (idx < 0) return false
        val input = c.getInputBuffer(idx)!!
        input.clear()
        input.put(data)
        c.queueInputBuffer(idx, 0, data.size, pts, if (config) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0)
        var tries = 0
        while (tries < 6 && !render(c)) tries++                       // the TV only sends a frame when the picture changes: show it now
        return true
    }

    private fun render(c: MediaCodec): Boolean {
        val info = MediaCodec.BufferInfo()
        var shown = false
        while (true) {
            val out = c.dequeueOutputBuffer(info, if (shown) 0 else 15000)
            if (out < 0) break
            c.releaseOutputBuffer(out, true)
            shown = true
        }
        return shown
    }

    override fun release() {
        val c = codec ?: return
        try { c.stop() } catch (ignored: Exception) { }
        try { c.release() } catch (ignored: Exception) { }
        codec = null
    }
}
