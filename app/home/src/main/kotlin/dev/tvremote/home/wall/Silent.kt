package dev.tvremote.home.wall

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * A video's picture alone, for a wall with the sound off. The platform player paces the picture by the audio clock even when muted,
 * and at each turn of the loop it waits for the audio to start again: the picture stood still ~0.45 s per turn on the emulator (the
 * Pexels fireplace, 30 fps: one frame, then 273 ms of nothing) and seconds on the FPT box; the same file without its audio track
 * turned in one frame's time (dev/DECISIONS.md "TV Home: video"). Copied, not re-encoded (MediaExtractor -> MediaMuxer: seconds,
 * no codec), into the cache, keyed by the file and its size; the file is played as it is until its copy is there.
 */
object Silent {
    private const val KEEP = 4
    private val none = HashSet<String>()                         // files found with no audio track: played as they are
    @Volatile private var busy = ""

    private fun dir(c: Context) = File(c.cacheDir, "silent").also { it.mkdirs() }
    private fun key(f: File) = Integer.toHexString((f.path + "|" + f.length()).hashCode())   // not the date: the caches touch theirs on each use
    private fun copy(c: Context, f: File) = File(dir(c), key(f) + ".mp4")

    /** What to play for [f] with the sound off: its copy without audio when there is one, else [f] (and the copy is made for the next turn). */
    fun of(c: Context, f: File): File {
        val k = key(f)
        if (synchronized(none) { k in none }) return f
        copy(c, f).takeIf { it.isFile && it.length() > 0 }?.let { it.setLastModified(System.currentTimeMillis()); return it }
        if (busy != k) {
            busy = k
            val app = c.applicationContext
            Thread({
                try { if (!make(f, copy(app, f))) synchronized(none) { none.add(k) } else trim(app) } finally { busy = "" }
            }, "silent").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
        }
        return f
    }

    /** Writes [f]'s video track alone to [out]; false when [f] has no audio track (nothing to gain) or cannot be read. */
    private fun make(f: File, out: File): Boolean {
        val ex = MediaExtractor()
        val tmp = File(out.path + ".part")
        var mux: MediaMuxer? = null
        return try {
            ex.setDataSource(f.path)
            val formats = (0 until ex.trackCount).map { ex.getTrackFormat(it) }
            val v = formats.indexOfFirst { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            if (v < 0 || formats.none { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }) return false
            val fmt = formats[v]
            ex.selectTrack(v)
            val m = MediaMuxer(tmp.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { mux = it }
            if (fmt.containsKey(MediaFormat.KEY_ROTATION)) m.setOrientationHint(fmt.getInteger(MediaFormat.KEY_ROTATION))
            val t = m.addTrack(fmt)
            m.start()
            val buf = ByteBuffer.allocateDirect(if (fmt.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) maxOf(fmt.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE), 1 shl 20) else 4 shl 20)
            val info = MediaCodec.BufferInfo()
            var n = 0
            while (true) {
                val size = ex.readSampleData(buf, 0)
                if (size < 0) break
                info.set(0, size, ex.sampleTime, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                m.writeSampleData(t, buf, info); n++
                ex.advance()
            }
            m.stop(); m.release(); mux = null
            n > 0 && tmp.renameTo(out)
        } catch (e: Throwable) { android.util.Log.w("TvHomeVideo", "silent copy: $e"); false } finally {
            try { mux?.release() } catch (e: Throwable) { }
            ex.release()
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun trim(c: Context) {
        val all = dir(c).listFiles { x -> x.name.endsWith(".mp4") }?.sortedByDescending { it.lastModified() } ?: return
        all.drop(KEEP).forEach { it.delete() }
    }
}
