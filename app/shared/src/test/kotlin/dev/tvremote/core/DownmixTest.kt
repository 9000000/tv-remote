package dev.tvremote.core

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DownmixTest {
    private fun pcm(vararg s: Int): ByteArray = ByteBuffer.allocate(s.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { s.forEach { putShort(it.toShort()) } }.array()
    private fun samples(b: ByteArray): IntArray { val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN); return IntArray(b.size / 2) { bb.getShort(it * 2).toInt() } }

    @Test fun fourSamplesBecomeOne() {
        assertArrayEquals(intArrayOf(400, -2000), samples(Downmix().feed(pcm(100, 300, 500, 700, -1000, -1000, -3000, -3000))))
    }

    @Test fun leftoverBytesCarryToTheNextPiece() {
        val d = Downmix()
        assertArrayEquals(intArrayOf(8), samples(d.feed(pcm(8, 8, 8, 8, 4, 4))))
        assertArrayEquals(intArrayOf(4), samples(d.feed(pcm(4, 4))))
    }

    @Test fun loudestSamplesDoNotOverflow() {
        assertArrayEquals(intArrayOf(32767, -32768), samples(Downmix().feed(pcm(32767, 32767, 32767, 32767, -32768, -32768, -32768, -32768))))
    }
}
