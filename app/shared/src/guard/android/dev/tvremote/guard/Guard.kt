package dev.tvremote.guard

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last line of defence of every Android app of the project (one file, compiled into each; why the looper is kept: DECISIONS.md).
 * A throw nobody caught is saved to filesDir (guard-last.txt) and shown in a dialog with Copy (and Share on the phone), so what
 * broke is on the screen instead of the app vanishing. [install] goes in Application.onCreate.
 *  - other threads: saved + shown, the process lives;
 *  - the main thread: the looper is kept alive, except in an Activity's creation / resume (GuardLogic.inLifecycle): it dies there,
 *    saved, and the next launch says so once ("Lần trước app bị lỗi").
 */
object Guard {
    private const val LAST = "guard-last.txt"
    private const val DIED = "guard-died"             // exists after a real death; the next start shows LAST once and removes it
    private const val BURST = 10                       // throws within BURST_MS on the main thread: the loop is not recovering, let it die
    private const val BURST_MS = 2000L

    private lateinit var app: Application
    private lateinit var words: (Context) -> GuardWords   // given the front Activity: its context carries the app's own chosen language
    private var share = false
    private var top = WeakReference<Activity>(null)
    private var dialog: AlertDialog? = null
    private var shownKey = ""
    private var repeats = 0
    private var pending: String? = null                // "last time" text, shown at the first resumed Activity
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val stamps = ArrayDeque<Long>()
    private var debuggable = false
    private var bodyView: TextView? = null              // the open dialog's text, to add the "report sent" line to

    /**
     * Where an error is reported to (kind, message, stack, done: true = it was sent). Set by the apps that have the anonymous stats switch
     * (core/CensusAndroid.kt), which decides whether anything goes; TV Home, Channels and Games have no such switch, so this stays null
     * there and nothing is ever sent. Called off the main thread's work: it must return at once.
     */
    @Volatile var crash: ((String, String, String, (Boolean) -> Unit) -> Unit)? = null
    private var prev: Thread.UncaughtExceptionHandler? = null

    /** [share]: a Share button too (the phone; the TV has nowhere to share to). */
    fun install(app: Application, words: (Context) -> GuardWords, share: Boolean = false) {
        if (this::app.isInitialized) return
        this.app = app; this.words = words; this.share = share
        prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, t -> onThrow(th, t) }
        debuggable = app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        val died = File(app.filesDir, DIED)
        if (died.exists()) {
            pending = try { File(app.filesDir, LAST).readText() } catch (e: Exception) { null }
            died.delete()
        }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) {
                if (debuggable && File(app.filesDir, "guard-resume-throw").delete()) throw IllegalStateException("guard test: onResume")   // debug trigger: a lifecycle death
                top = WeakReference(a)
                val p = pending ?: return
                pending = null
                show(a, words(a).lastTime, p, GuardLogic.key(Throwable(p)))
                GuardLogic.split(p)?.let { (k, m, st) -> send(k, m, st, GuardLogic.key(Throwable(p))) }
            }
            override fun onActivityPaused(a: Activity) { dialog?.let { if (top.get() === a) { it.dismiss(); dialog = null } } }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        if (debuggable) {     // debug builds only: adb shell am broadcast -a dev.tvremote.GUARD_TEST --es on main|bg|resume|gone
            val r = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (i.getStringExtra("on") == "gone") top.get()?.let { a ->            // a dialog shown a second after its screen closed
                        a.finish(); main.postDelayed({ android.app.Dialog(a).show() }, 1000) }
                    else if (i.getStringExtra("on") == "resume") File(app.filesDir, "guard-resume-throw").writeText("1")   // the next onResume throws
                    else throwOn(i.getStringExtra("on") != "bg")
                }
            }
            val f = android.content.IntentFilter("dev.tvremote.GUARD_TEST")
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, f, Context.RECEIVER_EXPORTED) else app.registerReceiver(r, f)
        }
        // The main thread: an exception ends Looper.loop() and with it the process. Looping again from a message of the first loop
        // keeps the app alive; the first loop never gets control back, which is the point.
        main.post {
            while (true) {
                try { Looper.loop(); break } catch (t: Throwable) { if (!onMainThrow(t)) throw t }
            }
        }
    }

    /** Called with whatever the main loop threw: true = go on looping, false = let it die (already saved). */
    private fun onMainThrow(t: Throwable): Boolean {
        val now = System.currentTimeMillis()
        stamps.addLast(now); while (stamps.size > BURST) stamps.removeFirst()
        val burst = stamps.size >= BURST && now - stamps.first() < BURST_MS
        if (GuardLogic.goneWindow(t)) { Log.w("Guard", "a dialog of a closed screen: $t"); return true }
        if (GuardLogic.inLifecycle(t) || burst) { save(Looper.getMainLooper().thread, t, died = true); return false }
        report(Looper.getMainLooper().thread, t)
        return true
    }

    /** The default handler: other threads live on; the main thread (the loop is not ours yet / gave up) dies as it would have. */
    private fun onThrow(th: Thread, t: Throwable) {
        if (th === Looper.getMainLooper().thread) {
            save(th, t, died = true)
            prev?.uncaughtException(th, t)
            Process.killProcess(Process.myPid())
            return
        }
        report(th, t)
    }

    private fun report(th: Thread, t: Throwable) {
        val text = save(th, t, died = false)
        main.post { top.get()?.let { show(it, words(it).title, text, GuardLogic.key(t), t) } }
        send(t.javaClass.name, t.message ?: "", GuardLogic.stack(t), GuardLogic.key(t))
    }

    /** Hand the error to [crash] (if any); when it was sent, say so in the dialog showing it. Never throws, never waits. */
    private fun send(kind: String, msg: String, stack: String, key: String) {
        val c = crash ?: return
        try { c(kind, msg, stack) { ok -> if (ok) main.post { markSent(key) } } } catch (e: Throwable) { }
    }

    private fun markSent(key: String) {
        val d = dialog ?: return
        val v = bodyView ?: return
        val line = top.get()?.let { words(it).sent }.orEmpty()
        if (d.isShowing && key == shownKey && line.isNotEmpty() && !v.text.endsWith(line)) v.text = v.text.toString() + "\n" + line
    }

    private fun save(th: Thread, t: Throwable, died: Boolean): String {
        val pi = try { app.packageManager.getPackageInfo(app.packageName, 0) } catch (e: Exception) { null }
        val text = GuardLogic.report(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
            "${app.packageName} ${pi?.versionName ?: "?"}", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "${Build.MANUFACTURER} ${Build.MODEL}", th.name, t)
        Log.e("TvrGuard", text)
        try {
            File(app.filesDir, LAST).writeText(text)
            if (died) File(app.filesDir, DIED).writeText("1")
        } catch (e: Exception) { }
        return text
    }

    /** One dialog at a time: the same error again only counts, another one while it is open waits in the file (Log has it). */
    private fun show(a: Activity, title: String, text: String, key: String, t: Throwable? = null) {
        if (a.isFinishing) return
        val w = words(a)
        val d = dialog
        if (d != null && d.isShowing) {
            if (key == shownKey) { repeats++; d.setTitle(title + "  " + w.repeat(repeats + 1)) }
            return
        }
        repeats = 0; shownKey = key
        val body = if (t != null) GuardLogic.head(t) else text.lines().dropWhile { it.isNotBlank() }.filter { it.isNotBlank() }.take(7).joinToString("\n")
        val tv = TextView(a).apply {
            this.text = body; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); typeface = Typeface.MONOSPACE
            val p = (16 * resources.displayMetrics.density).toInt(); setPadding(p, p / 2, p, 0)
        }
        val b = AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title).setView(ScrollView(a).apply { addView(tv) })
            .setPositiveButton(w.copy, null).setNegativeButton(w.close, null)
        if (share) b.setNeutralButton(w.share, null)
        val dlg = b.create()
        dlg.setOnDismissListener { if (dialog === dlg) { dialog = null; bodyView = null } }
        bodyView = tv
        dlg.show()
        dialog = dlg
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {          // listeners set after show(): Copy must not close it
            (a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("error", text))
            (it as TextView).text = w.copied
        }
        dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { dlg.dismiss() }
        if (share) dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            a.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
        }
        dlg.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus()                 // the TV's D-pad starts on Close
    }

    /** The debug trigger above: throws on the main thread / on a background thread. */
    fun throwOn(mainThread: Boolean) {
        if (mainThread) main.post { throw IllegalStateException("guard test: main thread") }
        else Thread({ throw IllegalStateException("guard test: background thread") }, "guard-test").start()
    }
}
