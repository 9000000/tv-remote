package dev.tvremote.setup

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import org.json.JSONObject

/**
 * The timers and limits ([Rules]) for a phone or computer that has ADB on this TV (it could do anything anyway). Only the shell
 * can send this broadcast (see the manifest). Extra "set": one action as base64 JSON; none reads. The answer, also base64 JSON,
 * is the state the pages show (see RulesClient.kt in the shared code).
 */
class RulesReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val answer = try {
            val set = i.getStringExtra("set")
            val change = if (set.isNullOrEmpty()) JSONObject() else JSONObject(String(Base64.decode(set, Base64.DEFAULT), Charsets.UTF_8))
            val state = Rules.change(c, change)
            if (Rules.active(c) && !RemoteService.isRunning()) {
                try { RemoteService.start(c, true) } catch (e: Exception) { }   // may be refused from the background: RulesClient starts it through the shell then
            }
            state.put("running", RemoteService.isRunning())
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: e.javaClass.simpleName)
        }
        resultCode = Activity.RESULT_OK
        resultData = Base64.encodeToString(answer.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
}
