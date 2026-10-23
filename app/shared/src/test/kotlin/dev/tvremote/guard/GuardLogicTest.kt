package dev.tvremote.guard

import org.junit.Assert.*
import org.junit.Test

class GuardLogicTest {
    private fun thrown(vararg frames: Pair<String, String>, cause: Throwable? = null) =
        RuntimeException("boom", cause).apply { stackTrace = frames.map { StackTraceElement(it.first, it.second, "X.java", 1) }.toTypedArray() }

    @Test fun `a throw while an Activity is launched or resumed is left to die`() {
        assertTrue(GuardLogic.inLifecycle(thrown("dev.tvremote.phone.MainActivity" to "onCreate", "android.app.Activity" to "performCreate",
            "android.app.ActivityThread" to "performLaunchActivity")))
        assertTrue(GuardLogic.inLifecycle(thrown("a.B" to "c", "android.app.ActivityThread" to "handleResumeActivity")))
        assertTrue(GuardLogic.inLifecycle(thrown("a.B" to "c", "android.app.servertransaction.TransactionExecutor" to "execute")))
    }

    @Test fun `the cause counts too`() {
        val inner = thrown("a.B" to "c", "android.app.ActivityThread" to "performLaunchActivity")
        assertTrue(GuardLogic.inLifecycle(thrown("x.Y" to "z", cause = inner)))
    }

    @Test fun `a menu tap or a posted job keeps the app alive`() {
        assertFalse(GuardLogic.inLifecycle(thrown("dev.tvremote.phone.PhoneRemote" to "remoteMenu", "android.view.View" to "performClick",
            "android.os.Handler" to "dispatchMessage", "android.os.Looper" to "loop", "android.app.ActivityThread" to "main")))
        assertFalse(GuardLogic.inLifecycle(thrown("a.B" to "c", "android.app.ActivityThread" to "handleMessage")))
    }

    @Test fun `the report has when, app, android, device, thread and the stack`() {
        val r = GuardLogic.report("2026-10-23 10:00:00", "dev.tvremote.phone 0.1.2", "14 (API 34)", "Google sdk", "main", thrown("a.B" to "c"))
        for (k in listOf("time: 2026-10-23 10:00:00", "app: dev.tvremote.phone 0.1.2", "android: 14 (API 34)", "device: Google sdk", "thread: main",
            "RuntimeException: boom", "at a.B.c(X.java:1)")) assertTrue(k, r.contains(k))
    }

    @Test fun `head is the first lines and the same error has the same key`() {
        val t = thrown("a.B" to "1", "a.B" to "2", "a.B" to "3", "a.B" to "4")
        assertEquals(3, GuardLogic.head(t, 3).lines().size)
        assertTrue(GuardLogic.head(t, 3).startsWith("java.lang.RuntimeException: boom"))
        assertEquals(GuardLogic.key(t), GuardLogic.key(thrown("a.B" to "1", "q.R" to "s")))
        assertNotEquals(GuardLogic.key(t), GuardLogic.key(thrown("a.B" to "2")))
    }
}
