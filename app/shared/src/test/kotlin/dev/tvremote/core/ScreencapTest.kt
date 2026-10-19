package dev.tvremote.core

import dev.tvremote.core.picture.Tv
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** screencap's PNG as the TV sends it: some MediaTek TVs (Xiaomi) print a line before it, which broke the picture. */
class ScreencapTest {
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0, 0, 13)

    @Test fun aPlainPngIsKept() = assertArrayEquals(png, Tv.png(png))

    @Test fun aLineBeforeItIsLeftOut() =
        assertArrayEquals(png, Tv.png("Init wrapper sys mutex successful\n".toByteArray() + png))

    @Test fun noPngIsNull() {
        assertNull(Tv.png(ByteArray(0)))
        assertNull(Tv.png("screencap: permission denied\n".toByteArray()))
    }
}
