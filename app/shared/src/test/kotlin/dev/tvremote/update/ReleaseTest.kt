package dev.tvremote.update

import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseTest {
    @Test fun compare() {
        assertTrue(Release.compare("1.10.0", "1.9") > 0)
        assertTrue(Release.compare("0.2.0", "0.1.9") > 0)
        assertTrue(Release.compare("0.1.0", "0.1") == 0)
    }

    @Test fun beforeTheRestart() {                                  // 1.3.0 … 2.24.2 came before 0.1.0: every one of them is older
        assertTrue(Release.compare("0.1.0", "2.24.2") > 0)
        assertTrue(Release.compare("0.1.0", "1.3.0") > 0)
        assertTrue(Release.compare("2.22.3", "0.1.1") < 0)
        assertTrue(Release.compare("2.24.2", "2.21.4") > 0)        // among themselves as before
        assertTrue(Release.compare("1.2.0", "0.9.0") > 0)          // the new numbers reach 1.0 as usual
    }
}
