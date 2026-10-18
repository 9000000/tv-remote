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
 * is the state the pages show (see RulesClient.kt in the shared code). Extra "get" = "power": the end of stats/power.jsonl
 * instead ({"power": lines}), for TVs whose shell cannot read this app's folder (a TCL on Android 11). "get" = "file" (with
 * `--es name stats/x --ei lines n`): the end of any file in this app's folder, gzipped (SetupFiles.kt in the shared code:
 * 30 days of samples is near a megabyte, more than a broadcast's answer can carry). "power" stays for clients before it.
 */
class RulesReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.getStringExtra("get") == "file") {
            resultCode = Activity.RESULT_OK
            resultData = try { file(c, i.getStringExtra("name") ?: "", i.getIntExtra("lines", 1000)) } catch (e: Exception) { null }
            return
        }
        val answer = if (i.getStringExtra("get") == "power") try {
            val f = java.io.File(c.getExternalFilesDir(null), "stats/power.jsonl")
            JSONObject().put("power", if (f.exists()) f.readLines().takeLast(1500).joinToString("\n") else "")
        } catch (e: Exception) {
            JSONObject().put("error", e.message ?: e.javaClass.simpleName)
        } else try {
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

    /** The last [lines] lines of [name] under this app's external files folder, gzipped and in base64; "" when there is none. */
    private fun file(c: Context, name: String, lines: Int): String {
        val root = c.getExternalFilesDir(null) ?: return ""
        val f = java.io.File(root, name).canonicalFile
        if (!f.path.startsWith(root.canonicalPath + "/")) return ""  // its own folder only: no "..", no links out
        val text = if (f.isFile) f.readLines().takeLast(lines.coerceIn(1, 30000)).joinToString("\n") else ""
        val bytes = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(bytes).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }
}
