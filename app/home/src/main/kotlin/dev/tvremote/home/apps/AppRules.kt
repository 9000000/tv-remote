package dev.tvremote.home.apps

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.Toast
import dev.tvremote.home.Font
import dev.tvremote.home.HomeConfig
import dev.tvremote.home.R
import dev.tvremote.tvui.TvField
import org.json.JSONObject

/**
 * One app's lock, daily limit and maintenance, asked of TV Setup (HomeInfo "app": it keeps the rules and the ADB link; TV Home has
 * neither). Changing a lock or a limit asks the parent's code first, the one TV Setup's PIN screen takes (a PIN, or the
 * Authenticator's code), and TV Setup checks it and counts the wrong tries: this side only asks and passes it on. With no code set
 * yet, the first PIN is made here (the web page does the same). An older TV Setup does not know "app": [read] is then null and the
 * menu shows no Lock & limits group.
 */
object AppRules {
    private val ui = Handler(Looper.getMainLooper())
    private val bg = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "home-app-rules").apply { isDaemon = true } }
    private const val KEEP_MS = 60_000L                                 // a code just typed holds for the next change in the same menu
    private var code = ""
    private var codeAt = 0L

    /** TV Setup's answer for [op] ("" reads), or null when it does not answer. Off the main thread. */
    fun call(c: Context, pkg: String, op: String = "", with: JSONObject.() -> Unit = {}): JSONObject? = try {
        val arg = JSONObject().put("pkg", pkg).put("op", op).apply(with).toString()
        c.contentResolver.call(Uri.parse(HomeConfig.USAGE), "app", arg, null)?.getString("json")?.let { JSONObject(it) }
    } catch (e: Exception) { null }

    /** Reads [pkg]'s state on a thread of its own and hands it to [then] on the main thread (null: no TV Setup, or an older one). */
    fun read(a: Activity, pkg: String, then: (JSONObject?) -> Unit) {
        bg.execute { val st = call(a, pkg); a.runOnUiThread { if (!a.isDestroyed) then(st) } }
    }

    /** A maintenance act ("stop" / "cache"): no code, the result word on the main thread. */
    fun act(a: Activity, pkg: String, op: String, then: (String) -> Unit) {
        bg.execute { val r = call(a, pkg, op)?.optString("result") ?: "failed"; a.runOnUiThread { if (!a.isDestroyed) then(r) } }
    }

    /**
     * Changes a lock or a limit ([with] puts the op's fields): asks the code (making the first PIN when [st] says there is none),
     * calls TV Setup and hands the new state to [then]. A wrong code asks again; a wait or a refusal is said and ends it.
     */
    fun change(a: Activity, pkg: String, op: String, st: JSONObject, with: JSONObject.() -> Unit, then: (JSONObject) -> Unit) {
        fun send(typed: String, fresh: Boolean) {
            bg.execute {
                val r = call(a, pkg, op) { with(); put("code", typed) }
                a.runOnUiThread {
                    if (a.isDestroyed) return@runOnUiThread
                    val res = r?.optString("result")
                    when (res) {
                        "ok" -> { code = typed; codeAt = System.currentTimeMillis(); then(r!!) }
                        "wrong", "code" -> { code = ""; if (res == "wrong") say(a, a.getString(R.string.am_pin_wrong)); ask(a, a.getString(R.string.am_pin_enter), false) { send(it, false) } }
                        "wait" -> say(a, a.getString(R.string.am_pin_wait, maxOf(1, (r!!.optLong("wait") + 59) / 60).toInt()))
                        "nopin" -> ask(a, a.getString(R.string.am_pin_set), true) { setPin(a, pkg, it) { send(it, true) } }
                        null -> say(a, a.getString(R.string.am_pin_noanswer))
                        else -> say(a, a.getString(R.string.am_failed))
                    }
                }
            }
        }
        val held = if (System.currentTimeMillis() - codeAt < KEEP_MS) code else ""
        if (!st.optBoolean("pin")) ask(a, a.getString(R.string.am_pin_set), true) { setPin(a, pkg, it) { send(it, true) } }
        else if (held.isNotEmpty()) send(held, false)
        else ask(a, a.getString(R.string.am_pin_enter), false) { send(it, false) }
    }

    private fun setPin(a: Activity, pkg: String, pin: String, then: () -> Unit) {
        bg.execute {
            val r = call(a, pkg, "pin") { put("pin", pin) }?.optString("result")
            a.runOnUiThread { if (!a.isDestroyed) if (r == "ok") then() else say(a, a.getString(if (r == null) R.string.am_pin_noanswer else R.string.am_pin_bad)) }
        }
    }

    private fun say(a: Activity, s: String) = Toast.makeText(a, s, Toast.LENGTH_LONG).show()

    /** A digits field in a dialog of the menu's look, with the TV's keyboard; [then] gets the digits (4 to 8 when [making] a PIN). */
    private fun ask(a: Activity, title: String, making: Boolean, then: (String) -> Unit) {
        val box = TvField(a).apply {
            Font.on(this); textSize = 20f; isSingleLine = true; maxLines = 1
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(8))
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
        val d = a.resources.displayMetrics.density
        val dlg = android.app.AlertDialog.Builder(a, R.style.Menu).setTitle(title)
            .setView(FrameLayout(a).apply { setPadding((24 * d).toInt(), (4 * d).toInt(), (24 * d).toInt(), 0); addView(box) })
            .setPositiveButton(R.string.ok, null).setNegativeButton(android.R.string.cancel, null).create()
        fun submit() {
            val s = box.text.toString().trim()
            if (making && s.length !in 4..8) { say(a, a.getString(R.string.am_pin_bad)); return }
            if (s.isEmpty()) return
            dlg.dismiss(); then(s)
        }
        box.setOnEditorActionListener { _, _, _ -> submit(); true }
        dlg.setOnShowListener { dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() } }
        dlg.show()
    }
}
