package dev.tvremote.setup

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What TV Home's "usage" card shows (HomeConfig.USAGE), counted here so it is the same number the app limits hold to:
 *   {week: [{pkg, sec}] the most used apps of the last 7 days with today, days: [sec] each day's total (today first), limits: [{pkg, min (today's, extra included), used (sec)}]}
 * Which apps a family watches is nobody else's business: only TV Home, signed by this project's key (or by this app's own,
 * a build signed with one key for both), gets an answer.
 */
class HomeInfo : ContentProvider() {
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val c = context ?: return null
        if (method != "usage" || !trusted(c, Binder.getCallingUid())) return null
        val now = System.currentTimeMillis()
        val token = Binder.clearCallingIdentity()                      // our own permissions from here (usage access)
        try {
            val on = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
            val today = try { Stats(c).today(now, on).first } catch (e: Exception) { JSONObject() }
            val all = try { JSONObject(File(c.getExternalFilesDir(null), "stats/usage.json").readText()) } catch (e: Exception) { JSONObject() }
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val perDay = (0..6).map { d -> if (d == 0) today else all.optJSONObject(day.format(Date(now - d * 86_400_000L))) ?: JSONObject() }
            val week = HashMap<String, Long>()
            for (o in perDay) for (k in o.keys()) week[k] = (week[k] ?: 0L) + o.optLong(k)
            val homes = try {                                          // a home screen (Google TV's, TV Home) is where people pass, not what they watch
                c.packageManager.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME), 0)
                    .map { it.activityInfo.packageName }.toSet()
            } catch (e: Exception) { emptySet() }
            val pm = c.packageManager
            val opens = { p: String -> (if (Build.VERSION.SDK_INT >= 21) pm.getLeanbackLaunchIntentForPackage(p) else null) ?: pm.getLaunchIntentForPackage(p) }
            val top = JSONArray()                                      // apps one opens: not the system's screens (systemui) nor a home screen
            for ((pkg, s) in week.entries.filter { it.key != c.packageName && it.key !in homes && it.value >= 60 && opens(it.key) != null }.sortedByDescending { it.value }.take(5).map { it.key to it.value })
                top.put(JSONObject().put("pkg", pkg).put("sec", s))
            val counted = { k: String -> k != c.packageName && k !in homes && opens(k) != null }
            val days = JSONArray()                                     // each day's total, today first: the card's columns
            for (o in perDay) days.put(o.keys().asSequence().filter(counted).sumOf { o.optLong(it) })
            val rules = Rules.load(c)
            val limits = JSONArray()
            rules.optJSONObject("limits")?.let { l ->
                for (pkg in l.keys()) limits.put(JSONObject().put("pkg", pkg).put("min", l.optInt(pkg) + Rules.extra(rules, pkg, now)).put("used", today.optLong(pkg)))
            }
            return Bundle().apply { putString("json", JSONObject().put("week", top).put("days", days).put("limits", limits).toString()) }
        } finally { Binder.restoreCallingIdentity(token) }
    }

    private fun trusted(c: Context, uid: Int): Boolean {
        val pm = c.packageManager
        if (pm.getPackagesForUid(uid)?.contains(HOME) != true) return false
        val mine = certs(pm, c.packageName)
        return certs(pm, HOME).any { it == HOME_CERT || it in mine }
    }

    @Suppress("DEPRECATION")
    private fun certs(pm: PackageManager, pkg: String): List<String> = try {
        val sigs = if (Build.VERSION.SDK_INT >= 28) pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            else pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        sigs.orEmpty().map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }
    } catch (e: Exception) { emptyList() }

    override fun onCreate() = true
    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    private companion object {
        const val HOME = "dev.tvremote.home"
        const val HOME_CERT = "657aabacfdccbc22ff1f61138df7efd79fae765df742f7d15d80dac84c7d81b3"   // app/home/signing.jks, alias tvhome
    }
}
