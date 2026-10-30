package dev.tvremote.games

import android.app.Activity
import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Is a TV Games screen in front? After Home the process and the pad server live on, but the phones' presses reach no activity, so the
 * pads must not look healthy: they are told "away" (with whether "open it again" can work) and get "closed" when the TV gave up
 * waiting. Giving up: [AWAY_MS] away with phones connected, [GRACE_MS] away with none; the activities are finished, which stops the
 * server and its service, and a pad then retries quietly until TV Games is opened again.
 */
class Presence(private val app: Application, private val server: GameServer) {
    private val main = Handler(Looper.getMainLooper())
    private val acts = ArrayList<Activity>()
    private var started = 0
    @Volatile var away = false; private set
    private var awaySince = 0L

    private val cb = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(a: Activity, b: Bundle?) { acts.add(a) }
        override fun onActivityDestroyed(a: Activity) { acts.remove(a) }
        override fun onActivityStarted(a: Activity) { if (a !in acts) acts.add(a); started++; main.removeCallbacks(settle); if (away) settle.run() }
        override fun onActivityStopped(a: Activity) { started--; if (started <= 0) main.postDelayed(settle, SETTLE_MS) }   // Library -> game swaps are not "away"
        override fun onActivityResumed(a: Activity) {}
        override fun onActivityPaused(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
    }

    private val settle = Runnable {
        val now = started > 0
        if (now == !away) return@Runnable
        away = !now
        if (away) { awaySince = System.currentTimeMillis(); Log.i(TAG, "away") } else { main.removeCallbacks(giveUp); Log.i(TAG, "back") }
        server.broadcast(state())
        if (away) arm()
    }
    private val giveUp = Runnable {
        if (!away) return@Runnable
        Log.i(TAG, "closing: away for ${(System.currentTimeMillis() - awaySince) / 1000}s")
        acts.toList().forEach { try { it.finishAndRemoveTask() } catch (e: Exception) { } }
        main.post { GameServer.shutdown() }                                // also when no activity was left to finish
    }

    fun attach() { app.registerActivityLifecycleCallbacks(cb); started = 0 }
    fun detach() { app.unregisterActivityLifecycleCallbacks(cb); main.removeCallbacksAndMessages(null) }

    /** The pads are in or out: the waiting time is the long one with phones, the short one without. */
    fun padsChanged() { main.post { if (away) arm() } }
    private fun arm() {
        main.removeCallbacks(giveUp)
        val wait = if (server.padCount() > 0) AWAY_MS else GRACE_MS
        main.postDelayed(giveUp, maxOf(0L, awaySince + wait - System.currentTimeMillis()))
    }

    /** What a pad is told when it joins or when this changes: {"away":bool,"open":bool}. */
    fun state(): String = "{\"away\":$away,\"open\":${canOpen()}}"

    /** Android 10+ blocks an app from starting a screen from the background; only the overlay permission lifts it (measured). */
    @Volatile private var blocked = false                                  // an "open" that did not bring the screen: do not offer it again until it is allowed
    fun canOpen(): Boolean = !blocked && (android.os.Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(app))

    /** The pad's "open TV Games again": the running game's task, else the library. true = it is in front within a moment. */
    fun open() {
        main.post {
            if (!away) return@post
            try { (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).appTasks.firstOrNull()?.moveToFront() } catch (e: Exception) { }
            try { app.startActivity(Intent(app, LibraryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
            main.postDelayed({ if (away) { Log.i(TAG, "open: blocked"); blocked = true; server.broadcast(state()) } }, 1500)
        }
    }

    companion object {
        private const val TAG = "GameServer"
        const val SETTLE_MS = 800L
        var AWAY_MS = 10 * 60_000L                                         // pads connected, TV Games not in front
        const val GRACE_MS = 30_000L                                       // no pads: long enough for a phone that blinked
    }
}
