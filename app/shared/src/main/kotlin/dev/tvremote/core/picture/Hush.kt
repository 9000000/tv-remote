package dev.tvremote.core.picture
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The silences of raw PCM left out: scrcpy hands over 48 kHz stereo all the time, zeros while the TV is silent (a menu, a
 * paused film), and the TV's page took 47 KB/s (192 with HQ) for them over Tailscale or 4G. Opus makes almost nothing of a
 * silence by itself. Only after [holdMs] of it, so a short pause inside a song still goes out as it is; the page hears once
 * that the sound has paused (Feeds' kind 6), so the gap is not taken for a stall.
 */
class Hush(private val bytesPerSecond: Int = 48000 * 4, private val holdMs: Int = 300) {
    private var quiet = 0L
    /** Silence is being held back now: a browser joining is told so at once. */
    var held = false
        private set

    /** [SEND] the piece, [START] hold it back and say the sound has paused, or [HELD] hold it back. */
    fun feed(data: ByteArray): Int {
        if (!silent(data)) { quiet = 0; held = false; return SEND }
        quiet += data.size
        if (held) return HELD
        if (quiet * 1000 < holdMs.toLong() * bytesPerSecond) return SEND
        held = true
        return START
    }

    companion object {
        const val SEND = 0
        const val START = 1
        const val HELD = 2
        private const val FLOOR = 4                                   // of 32768: dither, the last bit of a fade

        fun silent(data: ByteArray): Boolean {
            val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            var i = 0
            while (i + 1 < data.size) { val s = b.getShort(i).toInt(); if (s > FLOOR || s < -FLOOR) return false; i += 2 }
            return true
        }
    }
}
