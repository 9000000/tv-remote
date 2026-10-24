package dev.tvremote.home.apps

import android.app.Activity
import android.content.Context
import android.net.Uri
import dev.tvremote.home.HomeConfig
import org.json.JSONObject

/**
 * One app's lock, daily limit, maintenance and use, asked of TV Setup (HomeInfo "app": it keeps the rules and the ADB link; TV Home has
 * neither). A lock or a limit asks the parent's code, the one TV Setup's PIN screen takes (a PIN, or the Authenticator's code), and TV
 * Setup checks it and counts the wrong tries: this side only asks and passes it on. The code is asked ONCE, on entering Lock & limits
 * ([verify]); it is kept for that panel session only ([forget] when the panel closes) and goes with each change. There is no way to make the
 * first PIN from here: a child holding the remote could lock apps and forget the code (the phone or computer makes it). An older TV Setup
 * does not know "app": [read] is then null and the menu shows no Lock & limits group. Every answer reaches the screen as words in the
 * panel ([PinPad]), never as a toast alone (a TV box may not show one).
 */
object AppRules {
    private val bg = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "home-app-rules").apply { isDaemon = true } }
    private val poll = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "home-app-poll").apply { isDaemon = true } }
    private var code = ""                                               // the parent's code, right this panel session (memory only)

    /** The code was right in this panel session. */
    val unlocked get() = code.isNotEmpty()
    fun forget() { code = "" }

    /** TV Setup's answer for [op] ("" reads), or null when it does not answer. Off the main thread. */
    fun call(c: Context, pkg: String, op: String = "", with: JSONObject.() -> Unit = {}): JSONObject? = try {
        val arg = JSONObject().put("pkg", pkg).put("op", op).apply(with).toString()
        c.contentResolver.call(Uri.parse(HomeConfig.USAGE), "app", arg, null)?.getString("json")?.let { JSONObject(it) }
    } catch (e: Exception) { null }

    private fun later(a: Activity, then: () -> Unit) = a.runOnUiThread { if (!a.isDestroyed) then() }

    /** Reads [pkg]'s state on a thread of its own and hands it to [then] on the main thread (null: no TV Setup, or an older one). */
    fun read(a: Activity, pkg: String, then: (JSONObject?) -> Unit) {
        bg.execute { val st = call(a, pkg); later(a) { then(st) } }
    }

    /** An act with no code ("stop", "manage", "week"...): TV Setup's whole answer (null: it did not answer) on the main thread. */
    fun ask(a: Activity, pkg: String, op: String, then: (JSONObject?) -> Unit) {
        bg.execute { val r = call(a, pkg, op); later(a) { then(r) } }
    }

    /** Asks whether [typed] is the parent's code; a right one is kept for this panel session. [then] gets TV Setup's answer (null: none). */
    fun verify(a: Activity, pkg: String, typed: String, then: (JSONObject?) -> Unit) {
        bg.execute {
            val r = call(a, pkg, "verify") { put("code", typed) }
            later(a) { if (r?.optString("result") == "ok") code = typed; then(r) }
        }
    }

    /**
     * Changes a lock or a limit ([with] puts the op's fields) with the kept code. [then] gets TV Setup's answer; "ok" has the new state,
     * "wrong" / "code" / "wait" / "nopin" mean the code no longer opens it (the screen asks again) and the kept code is dropped.
     */
    fun change(a: Activity, pkg: String, op: String, with: JSONObject.() -> Unit, then: (JSONObject?) -> Unit) {
        val typed = code
        bg.execute {
            val r = call(a, pkg, op) { with(); put("code", typed) }
            later(a) { if (r?.optString("result") != "ok") code = ""; then(r) }
        }
    }

    /**
     * Clear cache: TV Setup runs it on a thread (it can take seconds), this polls its state every second until it is done or the person
     * cancels. [progress] on the main thread with each answer ("running", "ok", "partial", "failed", "noadb"); the returned function cancels.
     */
    fun cache(a: Activity, pkg: String, progress: (JSONObject?) -> Unit): () -> Unit {
        var live = true
        poll.execute {
            var r = call(a, pkg, "cache")
            while (live && r?.optString("result") == "running") {
                Thread.sleep(1000)
                if (!live) break
                r = call(a, pkg, "cache_state")
            }
            if (live) { live = false; later(a) { progress(r) } }
        }
        later(a) { progress(JSONObject().put("result", "running")) }
        return { if (live) { live = false; bg.execute { call(a, pkg, "cache_cancel") } } }
    }
}
