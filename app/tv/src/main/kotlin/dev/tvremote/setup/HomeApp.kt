package dev.tvremote.setup

import android.content.Context
import android.os.PowerManager
import dev.tvremote.core.adb.AdbLink
import dev.tvremote.core.adb.Pkg
import org.json.JSONObject

/**
 * What TV Home's long-press menu of one app may ask of this app (HomeInfo "app"; same signing-key trust as its other calls):
 * the app's lock and daily limit (the same [Rules] storage the web Apps / Automation tabs use, cleaned by [Rules.apply]), and
 * two fixed maintenance acts. `arg` is {pkg, op, ...}; the answer {result, ...state}:
 *   op ""      read: {pin: a code is set, prot: an app the menu may not lock or stop, locked, limit (min a day, 0 none), used (s today), adb: the shell can be reached, kids}
 *   op lock    {on, code}: changing a lock asks the parent's code (PIN, or the Authenticator's), like every other way in
 *   op limit   {minutes, code}: 0 removes it
 *   op pin     {pin}: only while no code is set at all (else the web page's own, with the old code)
 *   op stop / cache: force stop / clear cache over this app's ADB link; no code, they only close or tidy an app
 * result: ok, code (a code is needed), wrong, wait (+ `wait` seconds, the same lockout as the PIN screen), nopin, bad, noadb, failed.
 * A shell line is built here from [Pkg]-checked names only: a caller's own words never reach the shell.
 */
object HomeApp {
    private const val SELF = "dev.tvremote.setup"
    private const val HOME = HomeInfo.HOME

    /** Apps TV Home may not lock, limit or stop: the ones that run the lock and the home screen itself. */
    fun protectedPkg(pkg: String) = pkg == SELF || pkg == HOME || pkg == "dev.tvremote.setup.keys"

    /** The shell line of a maintenance act on [pkg], null for any other word or a bad package name. */
    fun command(op: String, pkg: String): String? {
        if (!Pkg.valid(pkg)) return null
        return when (op) {
            "stop" -> if (protectedPkg(pkg)) null else "am force-stop $pkg"
            "cache" -> "pm clear --cache-only $pkg"                    // Android 8+; an older pm answers an error text, said as failed
            else -> null
        }
    }

    /** The Rules action behind a lock / limit / pin request, null when it is none of those or the package is not allowed. */
    fun rulesAction(d: JSONObject): JSONObject? {
        val pkg = d.optString("pkg")
        return when (d.optString("op")) {
            "lock" -> if (Pkg.valid(pkg) && !protectedPkg(pkg)) JSONObject().put("do", "lock").put("pkg", pkg).put("on", d.optBoolean("on")) else null
            "limit" -> if (Pkg.valid(pkg) && !protectedPkg(pkg)) JSONObject().put("do", "limit").put("pkg", pkg).put("minutes", d.optInt("minutes").coerceIn(0, 24 * 60)) else null
            "pin" -> JSONObject().put("do", "pin").put("pin", d.optString("pin"))
            else -> null
        }
    }

    /** The app's rules as TV Home draws them. [usedSec] is today's use of the app. */
    fun state(o: JSONObject, pkg: String, usedSec: Long, adb: Boolean): JSONObject =
        JSONObject().put("pin", Rules.hasCode(o)).put("locked", pkg in Rules.locked(o))
            .put("limit", o.optJSONObject("limits")?.optInt(pkg) ?: 0).put("used", usedSec)
            .put("adb", adb).put("kids", Rules.kidsMode(o)).put("prot", protectedPkg(pkg))

    fun handle(c: Context, arg: String?): JSONObject {
        val d = try { JSONObject(arg ?: "{}") } catch (e: Exception) { return JSONObject().put("result", "bad") }
        val pkg = d.optString("pkg")
        if (!Pkg.valid(pkg)) return JSONObject().put("result", "bad")
        val op = d.optString("op")
        val now = System.currentTimeMillis()
        fun answer(result: String, extra: JSONObject? = null): JSONObject {
            val o = Rules.load(c)
            val used = try {
                val on = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
                Stats(c).today(now, on).first.optLong(pkg)
            } catch (e: Exception) { 0L }
            val s = state(o, pkg, used, adbReachable(c)).put("result", result)
            extra?.keys()?.forEach { s.put(it, extra.get(it)) }
            return s
        }
        if (op.isEmpty()) return answer("ok")
        if (op == "stop" || op == "cache") {
            val line = command(op, pkg) ?: return answer("bad")
            return answer(shell(c, line))
        }
        val action = rulesAction(d) ?: return answer("bad")
        val o = Rules.load(c)
        if (op == "pin") {                                              // the first code only; changing one needs the web page's way (and the old code)
            if (Rules.hasCode(o)) return answer("code")
            return try { Rules.change(c, action); answer("ok") } catch (e: IllegalArgumentException) { answer("bad") }
        }
        if (!Rules.hasCode(o)) return answer("nopin")
        val gate = check(c, o, d.optString("code"), now, pkg)
        if (gate != null) return answer(gate.first, JSONObject().put("wait", gate.second))
        return try { Rules.change(c, action); answer("ok") } catch (e: IllegalArgumentException) { answer("bad") }
    }

    /** null when [code] opens the rules; else (result, seconds to wait). The same lockout as the PIN screen: 5 wrong in a row = a wait. */
    private fun check(c: Context, o: JSONObject, code: String, now: Long, pkg: String): Pair<String, Long>? {
        val until = Lockout.until(c, now)
        if (until > now) return "wait" to (until - now + 999) / 1000
        if (code.isEmpty()) return "code" to 0L
        if (Rules.pinOk(o, code) || Rules.totpOk(o, code, now, Clock.driftMs(c))) { Lockout.right(c); return null }
        val wait = Lockout.wrong(c, now)
        if (Lockout.streak(c) == 3) RemoteService.wrongCodes(pkg)
        return if (wait > 0) "wait" to (wait + 999) / 1000 else "wrong" to 0L
    }

    /** ADB debugging is on and TV Setup can reach it (the work is the same as the power button's). */
    private fun adbReachable(c: Context): Boolean = try {
        android.provider.Settings.Global.getInt(c.contentResolver, android.provider.Settings.Global.ADB_ENABLED, 0) == 1
    } catch (e: Exception) { false }

    /** "ok", "noadb" or "failed". Blocks a moment: never on the main thread (a binder thread is fine). */
    private fun shell(c: Context, line: String): String {
        val live = try { RemoteService.adb() } catch (e: Exception) { null }
        val own = if (live == null) try { AdbLink(c.filesDir, "127.0.0.1").get() } catch (e: Exception) { return "noadb" } else null
        val adb = live ?: own!!
        return try {
            val out = adb.shell(line, 3000)
            if (out.contains("Exception") || out.contains("Error:") || out.contains("not found")) "failed" else "ok"
        } catch (e: Exception) { "failed" }
        finally { try { own?.close() } catch (e: Exception) { } }
    }
}
