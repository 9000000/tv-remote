package dev.tvremote.setup

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.SparseArray
import android.widget.Toast
import android.view.KeyEvent
import dev.tvremote.setup.keys.KeysProtocol

/**
 * Gives a button back that only the stock launcher answered (see [KeyFix]). The accessibility service that sees the keys is the one of
 * the add-on TV Setup Keys (`app/tvkeys`, so that this app declares none: DECISIONS.md); it asks this bound service about every key it may
 * care about ([KeysProtocol.INTEREST]) and does what it answers: taken or pass. Kept on in the secure settings by [KeyFix.sync] only
 * while such a key exists. The hot path is a lookup in a small array and nothing else; a key not in [plan] is passed at once, and
 * anything that goes wrong passes the key too.
 *
 * Keys from any device are taken (the remote's, a keyboard, the phone's through the TV Remote protocol): a key in the plan has no
 * handler left, so nothing else could have used it. `input keyevent` over ADB is injected after the filter and never shows here.
 * Not taken while the screen is off or the add-on's KeyCatch is waiting for the key that wakes it (the add-on checks both): that key keeps its old way.
 *
 * Also the filter for "hold to use Input / Settings" ([HoldKeys]): while that rule is on the service stays on even with no dead key.
 */
class KeysBridge : Service() {
    @Volatile private var plan = SparseArray<KeyFix.Rule>()
    private val ui = Handler(Looper.getMainLooper())
    private var watching = false
    private val gate = HoldKeys.Gate()
    @Volatile private var hold = false                                // the rule "holdkeys", read in refresh(): no disk in the key path
    private var armed = -1
    private var hint: Toast? = null
    private val fire = Runnable { if (synchronized(gate) { gate.timer(armed) }) held(armed) }

    private val packages = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { refresh() }
    }

    /** The add-on's side of [KeysProtocol.TX_KEY], on a binder thread: answered in microseconds, the toast and the action go to the main thread. */
    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != KeysProtocol.TX_KEY) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(KeysProtocol.DESCRIPTOR)
            val key = data.readInt(); val action = data.readInt(); val repeat = data.readInt()
            val down = data.readLong(); val at = data.readLong()
            val eat = try { handle(key, action, repeat, down, at) } catch (t: Throwable) { false }
            reply?.writeNoException()
            reply?.writeInt(if (eat) 1 else 0)
            return true
        }
    }

    override fun onBind(i: Intent?): IBinder = binder

    override fun onCreate() {
        running = this
        refresh()
        if (!watching) try {
            registerReceiver(packages, IntentFilter().apply {         // a launcher turned off or back on, an app installed: the plan follows
                addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            })
            watching = true
        } catch (e: Exception) { }
    }

    companion object {
        @Volatile private var running: KeysBridge? = null
        /** The map of TV Home's keys changed: the plan is made again at once (a bridge that is not up yet reads it when the add-on binds). */
        fun poke() { running?.refresh() }

        /**
         * A key sent by a client (not a finger on the remote): `input keyevent` is injected after the key filter, so the same
         * decision is made here, on request: TV Home's map first, then the dead-key rule. False: nothing to do, press it plainly.
         */
        fun press(c: Context, code: Int): Boolean {
            val rule = try { KeyFix.decide(KeyFix.current(c), code) } catch (e: Exception) { null } ?: return false
            val s = running
            if (s != null) s.ui.post { s.act(rule) } else KeyFix.run(c, rule)   // the bridge is up whenever a key is in the plan; its context may start an activity from the background
            return true
        }
    }

    private fun refresh() {
        Thread({
            val p = SparseArray<KeyFix.Rule>()
            try { if (KeyFix.wanted(this)) for ((k, v) in KeyFix.current(this)) p.put(k, v) } catch (e: Exception) { }   // switched off: the service only holds
            plan = p
            hold = HoldKeys.on(this)
            if (p.size() == 0) KeyFix.sync(this)                      // nothing left to bring back (and no hold): out of the key filter
        }, "keyfix").apply { isDaemon = true }.start()
    }

    private fun handle(code: Int, action: Int, repeat: Int, downTime: Long, eventTime: Long): Boolean {
        when (synchronized(gate) { gate.onKey(hold, code, action, repeat, downTime, eventTime) }) {   // binder threads: one key at a time
            HoldKeys.Step.PASS -> { }
            HoldKeys.Step.ARM -> {                                    // a short press does nothing but say how: the hold acts at HOLD_MS
                armed = code
                ui.post { say(HoldKeys.hint(code)) }
                ui.removeCallbacks(fire); ui.postDelayed(fire, HoldKeys.HOLD_MS)
                return true
            }
            HoldKeys.Step.FIRE -> { ui.post { held(code) }; return true }
            HoldKeys.Step.EAT -> { if (action == KeyEvent.ACTION_UP) ui.removeCallbacks(fire); return true }   // released early: the timer must not act
        }
        val rule = plan.get(code) ?: return false
        if (action == KeyEvent.ACTION_DOWN && repeat == 0) ui.post { act(rule) }
        return true                                                   // its up and its repeats too, or the app under would see half a press
    }

    /** The key was held long enough: what it does when not held back (the dead-key rule if it has one, else the stock launcher's own). */
    private fun held(code: Int) {
        hint?.cancel()
        act(plan.get(code) ?: KeyFix.RULES[code] ?: KeyFix.Rule(KeyFix.INPUT))   // the direct HDMI keys land on the input picker
    }

    private fun say(text: String) {
        hint?.cancel()
        hint = try { Toast.makeText(this, text, Toast.LENGTH_SHORT).also { it.show() } } catch (e: Exception) { null }
    }

    private fun act(r: KeyFix.Rule) = KeyFix.run(this, r)

    override fun onDestroy() {                                        // the add-on let go (its service is off, or it was updated)
        plan = SparseArray()
        hold = false
        ui.removeCallbacks(fire)
        if (running === this) running = null
        if (watching) try { unregisterReceiver(packages) } catch (e: Exception) { }
        watching = false
        super.onDestroy()
    }
}
