package dev.tvremote.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** B64 gives what java.util.Base64 gives (only on Android 8+, hence B64), and reads `base64` output with its line breaks. */
class B64Test {
    @Test fun same() {
        val r = java.util.Random(7)
        for (n in 0..300) {
            val b = ByteArray(n).also { r.nextBytes(it) }
            val want = java.util.Base64.getEncoder().encodeToString(b)
            assertEquals(want, B64.encode(b))
            assertArrayEquals(b, B64.decode(want))
            assertArrayEquals(b, B64.decode(java.util.Base64.getMimeEncoder().encodeToString(b).replace("\r\n", "\n") + "\n"))
        }
    }

    @Test(expected = IllegalArgumentException::class) fun bad() { B64.decode("ab*d") }
}
