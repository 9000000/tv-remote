package dev.tvremote.core

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Plays the TV's sound on this device: Opus packets from scrcpy, decoded by Android's own decoder, out through an AudioTrack.
 * Kept close to live: when the network hands over a burst after a stall, what would play late is dropped instead.
 */
class AudioOut {
    private var codec: MediaCodec? = null
    private var track: AudioTrack? = null
    private var written = 0L                                          // frames given to the track, to tell how far behind it plays

    /** [config] is scrcpy's first packet, the OpusHead; then one Opus packet at a time. */
    fun feed(config: Boolean, data: ByteArray) {
        if (config) { start(data); return }
        val c = codec ?: return
        val idx = c.dequeueInputBuffer(20000)
        if (idx < 0) return                                           // the decoder is behind: this packet is skipped
        val input = c.getInputBuffer(idx) ?: return
        input.clear()
        input.put(data)
        c.queueInputBuffer(idx, 0, data.size, 0, 0)
        drain(c)
    }

    private fun drain(c: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val out = c.dequeueOutputBuffer(info, 0)
            if (out < 0) break
            val t = track
            val buf = c.getOutputBuffer(out)
            if (t != null && buf != null && info.size > 0) {
                val behind = written - (t.playbackHeadPosition.toLong() and 0xffffffffL)
                if (behind < RATE * 3 / 10) {                         // at most 0.3 s queued; more than that would only add delay
                    val pcm = ByteArray(info.size)
                    buf.position(info.offset)
                    buf.get(pcm)
                    t.write(pcm, 0, pcm.size)
                    written += pcm.size / 4
                }
            }
            c.releaseOutputBuffer(out, false)
        }
    }

    @Suppress("DEPRECATION")
    private fun start(head: ByteArray) {
        release()
        val f = MediaFormat.createAudioFormat("audio/opus", RATE, 2)
        f.setByteBuffer("csd-0", ByteBuffer.wrap(head))
        val preSkip = if (head.size >= 12) (head[10].toInt() and 0xff) or ((head[11].toInt() and 0xff) shl 8) else 312
        f.setByteBuffer("csd-1", le64(preSkip * 1_000_000_000L / RATE))   // in nanoseconds, as MediaCodec wants it
        f.setByteBuffer("csd-2", le64(80_000_000L))                   // the seek pre-roll Opus asks for
        val c = MediaCodec.createDecoderByType("audio/opus")
        c.configure(f, null, null, 0)
        c.start()
        codec = c
        val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, RATE / 5 * 4), AudioTrack.MODE_STREAM)
        t.play()
        track = t
        written = 0
    }

    fun release() {
        codec?.let { try { it.stop() } catch (ignored: Exception) { }; try { it.release() } catch (ignored: Exception) { } }
        codec = null
        track?.let { try { it.stop() } catch (ignored: Exception) { }; try { it.release() } catch (ignored: Exception) { } }
        track = null
    }

    private fun le64(v: Long): ByteBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).apply { flip() }

    private companion object { const val RATE = 48000 }
}

/**
 * 48 kHz stereo 16-bit PCM (scrcpy's raw sound) down to 24 kHz mono for a browser that cannot decode Opus (the TV's page is
 * plain HTTP, where WebCodecs is not offered): 384 kbit/s, an eighth of the PCM, and still clear for speech and music.
 * Each output sample is the mean of two frames' four samples, which also keeps most of the aliasing out.
 */
class Downmix {
    private var carry = ByteArray(0)

    fun feed(data: ByteArray): ByteArray {
        val all = if (carry.isEmpty()) data else carry + data
        val groups = all.size / 8
        val out = ByteArray(groups * 2)
        val b = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until groups) {
            val p = i * 8
            val v = (b.getShort(p) + b.getShort(p + 2) + b.getShort(p + 4) + b.getShort(p + 6)) / 4
            out[i * 2] = v.toByte()
            out[i * 2 + 1] = (v shr 8).toByte()
        }
        carry = all.copyOfRange(groups * 8, all.size)
        return out
    }
}
