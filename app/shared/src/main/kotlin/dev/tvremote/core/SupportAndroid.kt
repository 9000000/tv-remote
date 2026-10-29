package dev.tvremote.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What support reads in an Android app without a field named for it beforehand (the user, 2026-10-26: a Chromecast's keys and
 * auto-open had none, and every new feature would need one more), TV Setup's and the Phone app's alike (SupportRelay): [prefs],
 * every setting the app keeps, secrets out; [logcat], the app's own log. The computer app compiles without this file.
 */
object SupportAndroid {
    // a name that holds a secret or a person's words, at any depth (the PIN's hash and salt, the TOTP seed, tokens, chats)
    private val SECRET = Regex("(?i)^(?:.*[_-])?(token|tokens|pass|password|pin|code|secret|totp|hash|h|salt|key|cookie|chats|history|paired|recovery|backup|note)$")

    // a value that is a token whatever its name (the phone keeps each TV's web pass as "tvs:<ip>", 2026-10-29): long hex runs
    private val TOKEN = Regex("[0-9a-fA-F]{32,}")

    fun prefs(c: Context): String = buildString {
        val dir = File(c.applicationInfo.dataDir, "shared_prefs")
        for (f in (dir.listFiles() ?: emptyArray()).sortedBy { it.name }) {
            val name = f.name.removeSuffix(".xml")
            if (name.startsWith("WebView") || name.startsWith("Aw")) continue   // the WebView's own, not this app's
            val all = try { c.getSharedPreferences(name, Context.MODE_PRIVATE).all } catch (e: Exception) { continue }
            if (all.isEmpty()) continue
            append("[prefs ").append(name).append("]\n")
            for ((k, v) in all.toSortedMap()) append(k).append('=').append(if (SECRET.matches(k)) "…" else value(v).replace(TOKEN, "…")).append('\n')
        }
    }.let { Scrub.blank(it) }

    private fun value(v: Any?): String = when (v) {
        is String -> (try { if (v.startsWith("{")) clean(JSONObject(v)).toString() else if (v.startsWith("[")) clean(JSONArray(v)).toString() else null } catch (e: Exception) { null }) ?: v.take(2000)
        is Set<*> -> v.joinToString(",", "{", "}")
        else -> v.toString()
    }

    private fun clean(o: Any?): Any? = when (o) {
        is JSONObject -> JSONObject().also { out -> o.keys().forEach { k -> out.put(k, if (SECRET.matches(k)) "…" else clean(o.opt(k)).let { v -> if (v is String) v.replace(TOKEN, "…") else v }) } }
        is JSONArray -> JSONArray().also { out -> for (i in 0 until o.length()) out.put(clean(o.opt(i))) }
        else -> o
    }

    /** This app's own log: no permission needed for its own lines. */
    fun logcat(): String = try {
        val p = ProcessBuilder("logcat", "-d", "-t", "3000", "-v", "time", "--pid=" + android.os.Process.myPid()).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        "[logcat]\n" + Scrub.blank(out)
    } catch (e: Exception) { "[logcat] $e\n" }

    /** The support store in the app's "support" prefs. */
    fun store(c: Context): Census.Store {
        val p = c.applicationContext.getSharedPreferences("support", Context.MODE_PRIVATE)
        return object : Census.Store {
            override fun get(key: String): String? = p.all[key]?.toString()
            override fun put(key: String, value: String) { p.edit().putString(key, value).apply() }
        }
    }
}
