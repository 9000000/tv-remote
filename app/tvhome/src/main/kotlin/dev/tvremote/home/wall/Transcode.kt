package dev.tvremote.home.wall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Makes a GIF or a heavy video light, on the TV itself with the platform's own codecs (no library: Media3's Transformer would add
 * megabytes to a launcher that is 2 MB): H.264 in an mp4, the screen's size at most, the frame rate and size the person chose (Settings: Original by default), ~0.065 bit per pixel
 * ([Videos.bitrate]), no B-frames (the encoder's default profile), a key frame every 2 s. The decoder (or a GIF's frame) is drawn
 * by OpenGL into the encoder's input surface, which is also what scales it, so the hardware encoder does the work where there is one.
 * The video's AAC sound is copied as it is (the "Sound" setting needs it); another sound is dropped.
 * Blocking, off the main thread. Any failure returns null and leaves no half file.
 */
object Transcode {
    class Result(val ms: Long)
    private const val GIF_FPS = 30                                  // a GIF's frames are sampled this often at most (few have more)

    /** [cap] frames a second at most (0 = the source's own); the size is [w] x [h] (Videos.target). */
    fun run(c: Context, src: File, dst: File, p: Videos.Probe, w: Int, h: Int, cap: Int, progress: (Int) -> Unit): Result? = try {
        if (p.kind == "gif") gif(src, dst, w, h, if (cap in 1 until GIF_FPS) cap else GIF_FPS, progress) else video(src, dst, w, h, cap, if (cap > 0) cap else p.fps.takeIf { it > 0 } ?: 30, progress)
    } catch (e: Throwable) { android.util.Log.w("TvHomeVideo", "transcode failed", e); dst.delete(); null }

    /** The AVC encoder [encoder] gets says it does [w] x [h] at [fps]: a BRAVIA's MTK encoder asked for 1080p60 wrote 27 Mbps of broken frames. */
    fun encodes(w: Int, h: Int, fps: Int): Boolean = try {
        val e = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        try { e.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities?.areSizeAndRateSupported(w, h, fps.toDouble()) != false } finally { e.release() }
    } catch (e: Throwable) { true }

    /**
     * What came out is a video this TV plays: near the bitrate asked (an encoder past what it can do ignores it and writes garbage,
     * measured on a BRAVIA), and its first [frames] frames decode. A false result is made again lighter, never shown.
     */
    fun sane(f: File, w: Int, h: Int, fps: Int, frames: Int = 60): Boolean {
        val p = Videos.probe(f) ?: return false
        if (p.bitrate > Videos.bitrate(w, h, fps) * 2L + 1_000_000L) { android.util.Log.i("TvHomeVideo", "made ${w}x$h@$fps at ${p.bitrate} bps: the encoder ignored the rate"); return false }
        val ex = MediaExtractor()
        var dec: MediaCodec? = null
        return try {
            ex.setDataSource(f.path)
            val vt = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("video/") }
            val vf = ex.getTrackFormat(vt)
            ex.selectTrack(vt)
            val d = MediaCodec.createDecoderByType(vf.getString(MediaFormat.KEY_MIME)!!).also { dec = it }
            d.configure(vf, null, null, 0); d.start()
            val info = MediaCodec.BufferInfo()
            var out = 0; var fed = false; var idle = 0
            while (out < frames && idle < 200) {                       // ~2 s without a frame: the decoder choked
                if (!fed) {
                    val i = d.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(d.getInputBuffer(i)!!, 0)
                        if (n < 0) { d.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); fed = true }
                        else { d.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = d.dequeueOutputBuffer(info, 10_000)
                if (o >= 0) {
                    if (info.size > 0) out++
                    d.releaseOutputBuffer(o, false); idle = 0
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                } else idle++
            }
            (out >= minOf(frames, 10)).also { if (!it) android.util.Log.i("TvHomeVideo", "made ${w}x$h@$fps: $out frames decoded") }
        } catch (e: Throwable) { android.util.Log.w("TvHomeVideo", "made video does not play", e); false }
        finally { try { dec?.stop() } catch (e: Throwable) { }; try { dec?.release() } catch (e: Throwable) { }; ex.release() }
    }

    private fun encoder(w: Int, h: Int, fps: Int): MediaCodec {
        val f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, Videos.bitrate(w, h, fps))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply { configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE) }
    }

    /** The encoder's output into the muxer; the muxer starts when the encoder has said its format (and the sound's is added with it). */
    private class Sink(val enc: MediaCodec, val mux: MediaMuxer, val audio: MediaFormat?, val audioIn: MediaExtractor?) {
        var track = -1; var aTrack = -1; var started = false; var last = 0L
        private val info = MediaCodec.BufferInfo()
        private val abuf = ByteBuffer.allocate(256 * 1024)
        private val ainfo = MediaCodec.BufferInfo()

        /** Takes what the encoder has; with [eos] waits for its end. */
        fun drain(eos: Boolean) {
            while (true) {
                val i = enc.dequeueOutputBuffer(info, if (eos) 10_000 else 0)
                when {
                    i == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!eos) return
                    i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = mux.addTrack(enc.outputFormat)
                        if (audio != null) aTrack = mux.addTrack(audio)
                        mux.start(); started = true
                    }
                    i >= 0 -> {
                        val b = enc.getOutputBuffer(i)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && started) {
                            b.position(info.offset); b.limit(info.offset + info.size)
                            mux.writeSampleData(track, b, info)
                            last = info.presentationTimeUs
                            copyAudio(last)
                        }
                        enc.releaseOutputBuffer(i, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        /** The sound up to [us], so the file is interleaved (a player reads it front to back without jumping). */
        fun copyAudio(us: Long, all: Boolean = false) {
            val ex = audioIn ?: return
            while (true) {
                val t = ex.sampleTime
                if (t < 0 || (!all && t > us)) return
                ainfo.offset = 0; ainfo.size = ex.readSampleData(abuf, 0)
                if (ainfo.size < 0) return
                ainfo.presentationTimeUs = t; ainfo.flags = ex.sampleFlags
                mux.writeSampleData(aTrack, abuf, ainfo)
                ex.advance()
            }
        }
    }

    // ---- a video ----

    private fun video(src: File, dst: File, w: Int, h: Int, cap: Int, fps: Int, progress: (Int) -> Unit): Result {
        val step = if (cap > 0) 1_000_000L / cap else 0L
        val ex = MediaExtractor().apply { setDataSource(src.path) }
        val vt = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("video/") }
        val vf = ex.getTrackFormat(vt)
        val total = if (vf.containsKey(MediaFormat.KEY_DURATION)) vf.getLong(MediaFormat.KEY_DURATION) else 0L
        ex.selectTrack(vt)
        val ax = MediaExtractor().apply { setDataSource(src.path) }
        val at = (0 until ax.trackCount).firstOrNull { ax.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_AAC }
        if (at != null) ax.selectTrack(at)

        val enc = encoder(w, h, fps)
        val input = enc.createInputSurface()
        enc.start()
        val gl = Gl(input, true)
        val tex = SurfaceTexture(gl.tex)
        val seen = Object(); var fresh = false
        tex.setOnFrameAvailableListener { synchronized(seen) { fresh = true; seen.notifyAll() } }
        val surface = Surface(tex)
        val dec = MediaCodec.createDecoderByType(vf.getString(MediaFormat.KEY_MIME)!!)
        dec.configure(vf, surface, null, 0)
        dec.start()
        val mux = MediaMuxer(dst.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val sink = Sink(enc, mux, at?.let { ax.getTrackFormat(it) }, if (at != null) ax else null)
        var ok = false
        try {
            val info = MediaCodec.BufferInfo()
            val mtx = FloatArray(16)
            var fedAll = false; var done = false; var kept = -step; var lastPts = 0L
            while (!done) {
                if (!fedAll) {
                    val i = dec.dequeueInputBuffer(5_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(dec.getInputBuffer(i)!!, 0)
                        if (n < 0) { dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); fedAll = true }
                        else { dec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = dec.dequeueOutputBuffer(info, 5_000)
                if (o >= 0) {
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val draw = info.size > 0 && (step == 0L || info.presentationTimeUs - kept >= step - 3_000)    // 60 fps in, 30 out
                    dec.releaseOutputBuffer(o, draw)
                    if (draw) {
                        synchronized(seen) { val t0 = System.nanoTime(); while (!fresh && System.nanoTime() - t0 < 3_000_000_000L) seen.wait(100); if (!fresh) throw IllegalStateException("frame"); fresh = false }
                        tex.updateTexImage(); tex.getTransformMatrix(mtx)
                        gl.draw(mtx)
                        gl.present(info.presentationTimeUs * 1000)
                        kept = info.presentationTimeUs; lastPts = kept
                        if (total > 0) progress((kept * 99 / total).toInt().coerceIn(0, 99))
                        sink.drain(false)
                    }
                    if (eos) done = true
                }
            }
            enc.signalEndOfInputStream()
            sink.drain(true)
            sink.copyAudio(0, all = true)
            ok = sink.started
            return Result(lastPts / 1000)
        } finally {
            for (f in listOf({ dec.stop() }, { dec.release() }, { enc.stop() }, { enc.release() }, { surface.release() }, { tex.release() }, { gl.release() }, { input.release() },
                { if (sink.started) mux.stop() }, { mux.release() }, { ex.release() }, { ax.release() })) try { f() } catch (e: Throwable) { }
            if (!ok) dst.delete()
        }
    }

    // ---- a GIF ----

    private fun gif(src: File, dst: File, w: Int, h: Int, fps: Int, progress: (Int) -> Unit): Result {
        val stepUs = 1_000_000L / fps
        val movie = Movie.decodeFile(src.path) ?: throw IllegalArgumentException("gif")
        val ms = movie.duration()
        if (ms <= 0) throw IllegalArgumentException("still")
        val enc = encoder(w, h, fps)
        val input = enc.createInputSurface()
        enc.start()
        val gl = Gl(input, false)
        val mux = MediaMuxer(dst.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val sink = Sink(enc, mux, null, null)
        var ok = false
        // a GIF has no background of its own where it is transparent: white, as browsers show it; drawn at the made size
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        try {
            val frames = maxOf(1L, ms.toLong() * fps / 1000)
            for (i in 0 until frames) {
                movie.setTime((i * 1000 / fps).toInt().coerceAtMost(ms - 1))
                cv.drawColor(-1)
                cv.save(); cv.scale(w.toFloat() / movie.width(), h.toFloat() / movie.height()); movie.draw(cv, 0f, 0f); cv.restore()
                gl.upload(bmp)
                gl.draw(Gl.FLIP)
                gl.present(i * stepUs * 1000)
                progress((i * 99 / frames).toInt())
                sink.drain(false)
            }
            enc.signalEndOfInputStream()
            sink.drain(true)
            ok = sink.started
            return Result(ms.toLong())
        } finally {
            bmp.recycle()
            for (f in listOf({ enc.stop() }, { enc.release() }, { gl.release() }, { input.release() }, { if (sink.started) mux.stop() }, { mux.release() })) try { f() } catch (e: Throwable) { }
            if (!ok) dst.delete()
        }
    }

    /** EGL on the encoder's input surface and one textured square: [oes] the decoder's external texture, else a bitmap. */
    private class Gl(surface: Surface, oes: Boolean) {
        private val dpy: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        private val ctx: EGLContext
        private val win: EGLSurface
        private val prog: Int
        private val uMtx: Int
        val tex: Int
        private val target = if (oes) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D
        private val quad = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f)); position(0)
        }

        init {
            val v = IntArray(2)
            EGL14.eglInitialize(dpy, v, 0, v, 1)
            val cfg = arrayOfNulls<EGLConfig>(1)
            val n = IntArray(1)
            EGL14.eglChooseConfig(dpy, intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, 0x3142, 1, EGL14.EGL_NONE), 0, cfg, 0, 1, n, 0)
            ctx = EGL14.eglCreateContext(dpy, cfg[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            win = EGL14.eglCreateWindowSurface(dpy, cfg[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(dpy, win, win, ctx)
            val ext = if (oes) "#extension GL_OES_EGL_image_external : require\n" else ""
            val sampler = if (oes) "samplerExternalOES" else "sampler2D"
            prog = program("uniform mat4 uMtx; attribute vec4 aPos; attribute vec2 aTex; varying vec2 vTex; void main(){ gl_Position=aPos; vTex=(uMtx*vec4(aTex,0.0,1.0)).xy; }",
                ext + "precision mediump float; varying vec2 vTex; uniform $sampler sTex; void main(){ gl_FragColor=texture2D(sTex,vTex); }")
            uMtx = GLES20.glGetUniformLocation(prog, "uMtx")
            val t = IntArray(1)
            GLES20.glGenTextures(1, t, 0); tex = t[0]
            GLES20.glBindTexture(target, tex)
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        private fun program(vs: String, fs: String): Int {
            fun shader(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
            return GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, shader(GLES20.GL_VERTEX_SHADER, vs)); GLES20.glAttachShader(it, shader(GLES20.GL_FRAGMENT_SHADER, fs)); GLES20.glLinkProgram(it)
            }
        }

        fun upload(b: Bitmap) { GLES20.glBindTexture(target, tex); GLUtils.texImage2D(target, 0, b, 0) }

        fun draw(mtx: FloatArray) {
            GLES20.glUseProgram(prog)
            GLES20.glBindTexture(target, tex)
            GLES20.glUniformMatrix4fv(uMtx, 1, false, mtx, 0)
            val pos = GLES20.glGetAttribLocation(prog, "aPos"); val tc = GLES20.glGetAttribLocation(prog, "aTex")
            quad.position(0); GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, quad); GLES20.glEnableVertexAttribArray(pos)
            quad.position(2); GLES20.glVertexAttribPointer(tc, 2, GLES20.GL_FLOAT, false, 16, quad); GLES20.glEnableVertexAttribArray(tc)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        fun present(ns: Long) { EGLExt.eglPresentationTimeANDROID(dpy, win, ns); EGL14.eglSwapBuffers(dpy, win) }

        fun release() {
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(dpy, win); EGL14.eglDestroyContext(dpy, ctx); EGL14.eglReleaseThread(); EGL14.eglTerminate(dpy)
        }

        companion object {
            /** A bitmap's first row is its top; a texture's is its bottom. */
            val FLIP = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f)
        }
    }
}
