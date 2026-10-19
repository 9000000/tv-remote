package dev.tvremote.home

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.drawable.Drawable

/** An app the TV can open from a home screen, with the picture it gives for one: a TV banner (16:9), else its icon. */
class App(val pkg: String, val label: String, val info: ActivityInfo) {
    var banner: Drawable? = null
    var icon: Drawable? = null

    fun load(c: Context) {
        val pm = c.packageManager
        banner = try { info.loadBanner(pm) ?: info.applicationInfo.loadBanner(pm) } catch (e: Exception) { null }
        if (banner == null) icon = try { info.loadIcon(pm) } catch (e: Exception) { null }
    }
}

object Apps {
    /** TV apps first (LEANBACK_LAUNCHER), then phone apps that have a launcher entry, as the TV's own home lists them; not this one. */
    fun list(c: Context): List<App> {
        val pm = c.packageManager
        val out = LinkedHashMap<String, App>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val found = try { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0) } catch (e: Exception) { emptyList() }
            for (ri in found) {
                val p = ri.activityInfo.packageName
                if (p == c.packageName || p in out) continue
                out[p] = App(p, ri.loadLabel(pm).toString().trim(), ri.activityInfo)
            }
        }
        return out.values.sortedBy { it.label.lowercase() }
    }

    fun launch(c: Context, pkg: String): Boolean {
        val pm = c.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        return try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
    }
}
