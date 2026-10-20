package dev.tvremote.setup

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.SparseArray
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Gives a button back that only the stock launcher answered (see [KeyFix]). Kept on in the secure settings by [KeyFix.sync] only
 * while such a key exists. In the key filter every key waits for this answer, so the hot path is a lookup in a small array
 * and nothing else; a key not in [plan] is passed at once, and anything that goes wrong passes the key too.
 *
 * Keys from any device are taken (the remote's, a keyboard, the phone's through the TV Remote protocol): a key in the plan has no
 * handler left, so nothing else could have used it. `input keyevent` over ADB is injected after the filter and never shows here.
 * Not taken while the screen is off or [KeyCatch] is waiting for the key that wakes it: that key keeps its old way.
 */
class KeyFixService : AccessibilityService() {
    @Volatile private var plan = SparseArray<KeyFix.Rule>()
    private val ui = Handler(Looper.getMainLooper())
    private var power: PowerManager? = null
    private var watching = false

    private val packages = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { refresh() }
    }

    override fun onServiceConnected() {
        power = getSystemService(Context.POWER_SERVICE) as? PowerManager
        refresh()
        if (!watching) try {
            registerReceiver(packages, IntentFilter().apply {         // a launcher turned off or back on, an app installed: the plan follows
                addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            })
            watching = true
        } catch (e: Exception) { }
    }

    private fun refresh() {
        Thread({
            val p = SparseArray<KeyFix.Rule>()
            try { for ((k, v) in KeyFix.current(this)) p.put(k, v) } catch (e: Exception) { }
            plan = p
            if (p.size() == 0) KeyFix.sync(this)                      // nothing left to bring back: out of the key filter
        }, "keyfix").apply { isDaemon = true }.start()
    }

    override fun onKeyEvent(e: KeyEvent): Boolean = try { handle(e) } catch (t: Throwable) { false }

    private fun handle(e: KeyEvent): Boolean {
        val rule = plan.get(e.keyCode) ?: return false
        if (KeyCatch.live || power?.isInteractive != true) return false
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) ui.post { act(rule) }
        return true                                                   // its up and its repeats too, or the app under would see half a press
    }

    private fun act(r: KeyFix.Rule) {
        try {
            when (r.act) {
                KeyFix.INPUT -> inputs()
                KeyFix.APP -> start(packageManager.getLeanbackLaunchIntentForPackage(r.pkgs[0]) ?: packageManager.getLaunchIntentForPackage(r.pkgs[0]))
                KeyFix.HOME -> start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                KeyFix.SETTINGS -> start(Intent(Settings.ACTION_SETTINGS))
            }
        } catch (e: Exception) { }
    }

    private fun start(i: Intent?) { if (i != null) startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    /** The system's input picker; where the TV has none, ours (the TV's inputs as the TV lists them), else the Settings. */
    private fun inputs() {
        try { start(Intent("com.android.tv.action.VIEW_INPUTS")); return } catch (e: Exception) { }
        val have = try { (getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager)?.tvInputList?.isNotEmpty() == true } catch (e: Exception) { false }
        start(if (have) Intent(this, InputsActivity::class.java) else Intent(Settings.ACTION_SETTINGS))
    }

    override fun onUnbind(i: Intent?): Boolean {
        plan = SparseArray()
        if (watching) try { unregisterReceiver(packages) } catch (e: Exception) { }
        watching = false
        return super.onUnbind(i)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) { }
    override fun onInterrupt() { }
}
