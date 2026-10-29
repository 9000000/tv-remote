package dev.tvremote.guard

import java.io.PrintWriter
import java.io.StringWriter

/** The words of the dialog, in the app's language when it is shown (the shared apps read i18n/<lang>.json, the others strings.xml). */
class GuardWords(val title: String, val lastTime: String, val copy: String, val copied: String, val share: String, val close: String,
                 val sent: String = "", val repeat: (Int) -> String)

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

    /**
     * True when [t] is a dialog shown or closed after its screen went away (BadTokenException from Dialog.show, "not attached to
     * window manager" from Dialog.dismiss): a late job finding its Activity gone. Nothing is lost, there was nothing to show it
     * on, so it is neither shown nor sent; one check here instead of a guard around each of the apps' dialogs.
     */
    fun goneWindow(t: Throwable): Boolean {
        val bad = t.javaClass.name.endsWith("\$BadTokenException") ||        // WindowManager's, by name: no Android in this file
            (t is IllegalArgumentException && t.message?.contains("not attached to window manager") == true)
        return bad && t.stackTrace.any { it.className == "android.app.Dialog" && (it.methodName == "show" || it.methodName.startsWith("dismiss")) }
    }

    /**
     * True when [t] is Android's own fault inside a call it made to the app, nothing the app did or can change: the system refusing
     * to start the TV's assistant while it asked the screen for assist data (census 4ccc4845), a TextClock unregistering a receiver
     * the system had already dropped while its screen was closed (630851a6). Logged, not shown or sent, and the app goes on.
     */
    fun systemFault(t: Throwable): Boolean {
        val f = t.stackTrace
        val assist = t is IllegalStateException && f.any { it.className == "android.app.ActivityThread" && it.methodName == "handleRequestAssistContextExtras" }
        val clock = t is IllegalArgumentException && t.message?.startsWith("Receiver not registered") == true &&
            f.any { it.className == "android.widget.TextClock" && it.methodName == "onDetachedFromWindow" }
        return assist || clock
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

    /** The stack without its first line (that is "class: message", sent as kind and msg): what Census.crash takes. */
    fun stack(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString().substringAfter('\n', "").trim()
    }

    /** [report]'s text back into (kind, message, stack): the error of the last run that died, sent at the next start. null = not a report. */
    fun split(report: String): Triple<String, String, String>? {
        val trace = report.substringAfter("\n\n", "").trim()
        if (trace.isEmpty()) return null
        val first = trace.lineSequence().first()
        return Triple(first.substringBefore(": ").trim(), if (": " in first) first.substringAfter(": ").trim() else "", trace.substringAfter('\n', "").trim())
    }

    /** Two throws are "the same error" when they are the same class thrown from the same place: counted, not shown twice. */
    fun key(t: Throwable): String = t.javaClass.name + "@" + (t.stackTrace.firstOrNull()?.toString() ?: "")
}
