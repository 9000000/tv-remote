package dev.tvremote.report

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import dev.tvremote.core.Report
import dev.tvremote.guard.Guard
import org.json.JSONObject
import java.util.UUID

/**
 * The anonymous stats of TV Home, TV Channels and TV Games (no :shared in them): error reports from the guard, and for TV Home the
 * daily census ping, so a TV with only TV Home on it is counted. The same body and POST as TV Setup's Census (core/Report.kt).
 *
 * The switch: when TV Setup is on the TV and new enough, ITS choice ("Send anonymous stats", asked of its HomeInfo as "census" ->
 * {on, id}) is the one that counts, and its install id is used, so one TV stays one install. Without it, the app's own switch
 * ([own], ON by default; TV Home shows it in Customize, Channels and Games have no screen for it). Nothing is sent while off.
 * Whatever goes wrong stays here; nothing waits on the main thread.
 */
object OwnReport {
    private const val SETUP = "content://dev.tvremote.setup.home"
    private var app: Application? = null
    private var name = ""
    private var version = ""
    private val once = Report.Once(System::currentTimeMillis)
    @Volatile private var url = Report.URL
    private var askedAt = 0L
    private var setupOn: Boolean? = null
    private var setupId: String? = null

    /** From the Application's onCreate: [name] is "home" | "channels" | "games"; [ping] only TV Home. */
    fun start(a: Application, name: String, ping: Boolean = false) {
        app = a; this.name = name
        version = try { a.packageManager.getPackageInfo(a.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
        Guard.crash = { kind, msg, stack, done -> Report.later("report") { done(crashNow(a, "kotlin", kind, msg, stack)) } }
        if (ping) Report.later("report-ping") { while (true) { try { ping(a) } catch (e: Throwable) { }; Thread.sleep(6 * 3600_000L) } }
    }

    private fun prefs(c: Context) = c.getSharedPreferences("report", Context.MODE_PRIVATE)

    /** The app's own switch, used when TV Setup does not hold one. */
    fun own(c: Context): Boolean = try { prefs(c).getString("on", "1") != "0" } catch (e: Throwable) { true }
    fun setOwn(c: Context, on: Boolean) { try { prefs(c).edit().putString("on", if (on) "1" else "0").apply() } catch (e: Throwable) { } }

    private fun ownId(c: Context): String {
        val p = prefs(c)
        p.getString("id", null)?.takeIf { it.length == 36 }?.let { return it }
        return UUID.randomUUID().toString().also { p.edit().putString("id", it).apply() }
    }

    /** What TV Setup says, kept a minute: (its switch, its install id), or (null, null) when it is missing, old or signed by another key. Blocks: not on the main thread. */
    fun setup(c: Context): Pair<Boolean?, String?> = synchronized(this) {
        if (System.currentTimeMillis() - askedAt < 60_000L) return setupOn to setupId
        var on: Boolean? = null
        var id: String? = null
        try {
            val j = c.contentResolver.call(Uri.parse(SETUP), "census", null, null)?.getString("json")?.let { JSONObject(it) }
            if (j != null && j.has("on")) { on = j.getBoolean("on"); id = j.optString("id").ifEmpty { null } }
        } catch (e: Throwable) { }
        askedAt = System.currentTimeMillis(); setupOn = on; setupId = id
        on to id
    }

    /** true when anything may be sent now. */
    fun effective(c: Context): Boolean = Report.decide(setup(c).first, own(c)) && !url.contains("REPLACE")

    private fun device() = JSONObject().put("brand", Build.BRAND).put("model", Build.MODEL).put("device", Build.DEVICE).put("board", Build.BOARD)
        .put("hw", Build.HARDWARE).put("android", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)

    private fun crashNow(c: Context, where: String, kind: String, msg: String, stack: String): Boolean {
        if (!effective(c)) return false
        val b = Report.crashBody(Report.chooseId(setup(c).second, ownId(c)), name, version, where, device(), kind, msg, stack)
        return once.send(url.trimEnd('/') + "/v1/crash", Report.crashKey(b.getString("kind"), b.getString("stack")), Report::httpPost) { b }
    }

    /** TV Home's census ping: this TV's own model, once a day (and when TV Home's version changes). */
    private fun ping(c: Context) {
        if (!effective(c)) return
        val day = System.currentTimeMillis() / 86_400_000L
        val p = prefs(c)
        if (!Report.due(p.getString("ping", null), day, version, 0)) return
        val m = HashMap<String, String>()
        m["ro.product.brand"] = Build.BRAND; m["ro.product.model"] = Build.MODEL; m["ro.product.device"] = Build.DEVICE
        m["ro.product.board"] = Build.BOARD; m["ro.hardware"] = Build.HARDWARE
        m["ro.soc.model"] = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else ""
        m["ro.build.version.release"] = Build.VERSION.RELEASE; m["ro.build.version.sdk"] = Build.VERSION.SDK_INT.toString()
        if (Build.MODEL.isNullOrBlank()) return
        val body = Report.pingBody(Report.chooseId(setup(c).second, ownId(c)), name, version, m, c.packageManager.hasSystemFeature("android.software.leanback"), null, null, null)
        if (Report.httpPost(url.trimEnd('/') + "/v1/ping", body.toString())) p.edit().putString("ping", "$day|$version|0").apply()
    }
}
