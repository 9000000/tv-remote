package dev.tvremote.core.picture
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
