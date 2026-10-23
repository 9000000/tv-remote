package dev.tvremote.guard

import java.io.PrintWriter
import java.io.StringWriter

/** The words of the dialog, in the app's language when it is shown (the shared apps read i18n/<lang>.json, the others strings.xml). */
class GuardWords(val title: String, val lastTime: String, val copy: String, val copied: String, val share: String, val close: String,
                 val repeat: (Int) -> String)

/**
 * The deciding and the text of the error guard (Guard.kt), with nothing of Android in it so a unit test and the computer app
 * can use it. Compiled into :shared, :desktop, :home, :channels and :games (one file, like HomeConfig).
 */
object GuardLogic {
    /** Frames that mean an Activity is being created, started or resumed (or a lifecycle transaction is running). */
    private val LIFECYCLE = Regex("^(perform|handle)\\w*Activity\\w*$|^(perform)(Create|Start|Resume|Restart|NewIntent)\\w*$|^callActivityOn\\w+$|^execute(Callbacks|LifecycleState)?$")

    private fun lifecycleFrame(e: StackTraceElement): Boolean {
        val c = e.className
        return when {
            c == "android.app.ActivityThread" -> e.methodName.matches(LIFECYCLE)
            c == "android.app.Activity" -> e.methodName.matches(LIFECYCLE)
            c == "android.app.Instrumentation" -> e.methodName.matches(LIFECYCLE)
            c == "android.app.servertransaction.TransactionExecutor" -> true
            else -> false
        }
    }

    /**
     * True when [t] (or what caused it) was thrown while an Activity was being created / resumed. Keeping the main looper alive
     * there would leave a half-built, blank screen that nothing ever draws into: the app is better off dying, and starting clean.
     */
    fun inLifecycle(t: Throwable): Boolean {
        var c: Throwable? = t
        var n = 0
        while (c != null && n++ < 8) {
            if (c.stackTrace.any(::lifecycleFrame)) return true
            c = c.cause.takeIf { it !== c }
        }
        return false
    }

    /** The whole text saved to the file and put on the clipboard: when, which app and phone, which thread, then the stack. */
    fun report(time: String, app: String, android: String, device: String, thread: String, t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return "time: $time\napp: $app\nandroid: $android\ndevice: $device\nthread: $thread\n\n$sw"
    }

    /** The few first lines of a stack for the dialog (what it was, and where); the rest is one Copy away. */
    fun head(t: Throwable, lines: Int = 6): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString().lineSequence().filter { it.isNotBlank() }.take(lines).joinToString("\n")
    }

    /** Two throws are "the same error" when they are the same class thrown from the same place: counted, not shown twice. */
    fun key(t: Throwable): String = t.javaClass.name + "@" + (t.stackTrace.firstOrNull()?.toString() ?: "")
}
