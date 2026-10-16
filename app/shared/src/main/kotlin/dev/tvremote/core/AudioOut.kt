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
 * Kept close to live: when the network hands over a burst after a stall, what would play late is dropped instead. How much
 * is kept queued adapts (Android 7+): 0.15 s at home, more each time the sound ran dry (4G, Tailscale through a relay:
 * packets come in bursts), back down after a calm half minute.
 */
class AudioOut {
    private var codec: MediaCodec? = null
    private var track: AudioTrack? = null
    private var written = 0L                                          // frames given to the track, to tell how far behind it plays
    private var room = 0                                              // the track's buffer in frames: it starts playing only once full
    private var target = BASE                                         // frames kept queued
    private var dry = 0                                               // the track's underruns seen so far
    private var calm = 0L                                             // since when it has not run dry

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
                if (android.os.Build.VERSION.SDK_INT >= 24) adapt(t)
                val behind = written - (t.playbackHeadPosition.toLong() and 0xffffffffL)
                if (behind < maxOf(target, room)) {                   // less than a full buffer would never start
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

    @android.annotation.TargetApi(24)
    private fun adapt(t: AudioTrack) {
        val now = System.currentTimeMillis()
        val u = t.underrunCount
        if (u > dry && written > room) {                              // it ran dry: keep more from now on, and a cushion now
            target = minOf(target * 3 / 2, MAX)
            room = t.setBufferSizeInFrames(target + RATE / 20).coerceAtLeast(0)
            val pad = target / 2
            t.write(ByteArray(pad * 4), 0, pad * 4)
            written += pad
            calm = now
        } else if (target > BASE && now - calm > 30000) {
            target = maxOf(BASE, target * 4 / 5)
            room = t.setBufferSizeInFrames(target + RATE / 20).coerceAtLeast(0)
            calm = now
        }
        dry = u
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
        val size = maxOf(min * 2, if (android.os.Build.VERSION.SDK_INT >= 24) (MAX + RATE / 10) * 4 else RATE / 5 * 4)   // room to grow into
        val t = AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, size, AudioTrack.MODE_STREAM)
        room = size / 4
        target = BASE; dry = 0; calm = System.currentTimeMillis()
        if (android.os.Build.VERSION.SDK_INT >= 24) room = t.setBufferSizeInFrames(maxOf(min / 4 * 2, RATE / 5)).coerceAtLeast(0).let { if (it > 0) it else size / 4 }   // starts as before: 0.2 s
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

    private companion object {
        const val RATE = 48000
        const val BASE = RATE * 15 / 100                              // 0.15 s
        const val MAX = RATE                                          // 1 s: later than that the sound is too far behind the picture
    }
}
