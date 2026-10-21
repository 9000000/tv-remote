package dev.tvremote.channels

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Favourites and groups for a phone or computer that has ADB on this TV (core/ontv/Channels.kt). Only the shell can send this
 * broadcast (the manifest asks for DUMP). Extras: "set" (base64 JSON, [Edit]: the whole state, saved first), "list" (a country
 * code: its channels too, from the catalog; "fresh" reads them from the network again), "countries" (the names of countries).
 * The answer, base64 JSON in the result data: {version, country, favs: [brief], groups: [{id, name, ch: [brief]}], list: {cc,
 * path (a file of [brief]), n, empty}, countries: [{cc, name, flag}]} (a channel once, however many streams it has),
 * or {error}. The catalog is read off the main thread (goAsync): a country not read yet comes from the network.
 */
class ChannelsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val pending = goAsync()
        Thread {
            val answer = try { run(c, i) } catch (e: Throwable) { JSONObject().put("error", e.message ?: e.javaClass.simpleName) }
            pending.resultCode = Activity.RESULT_OK
            pending.resultData = Base64.encodeToString(answer.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            pending.finish()
        }.start()
    }

    private fun run(c: Context, i: Intent): JSONObject {
        i.getStringExtra("set")?.takeIf { it.isNotEmpty() }?.let { apply(c, JSONObject(String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8))) }
        val out = JSONObject().put("version", c.packageManager.getPackageInfo(c.packageName, 0).versionName).put("country", Store.country(c))
            .put("favs", briefs(Store.favourites(c)))
            .put("groups", JSONArray().apply { Store.groups(c).forEach { g -> put(JSONObject().put("id", g.id).put("name", g.name).put("ch", briefs(g.channels))) } })
        if (i.getBooleanExtra("countries", false)) out.put("countries", JSONArray().apply {
            Catalog.countries(c).forEach { put(JSONObject().put("cc", it.code).put("name", it.name).put("flag", it.flag)) } })
        i.getStringExtra("list")?.lowercase()?.takeIf { it.matches(Regex("[a-z]{2}")) }?.let { cc ->
            val all = Catalog.channels(c, cc, i.getBooleanExtra("fresh", false))
            // a big country is ~200 KB: more than a broadcast's answer may carry (~250 KB base64 failed), so it goes in a file the shell reads
            val f = java.io.File(c.getExternalFilesDir(null), "lists/$cc.json")
            f.parentFile?.mkdirs()
            java.io.File(f.path + ".tmp").apply { writeText(briefs(all).toString()); renameTo(f) }
            out.put("list", JSONObject().put("cc", cc).put("empty", all.isEmpty()).put("path", f.path).put("n", all.byChannel().size))
        }
        return out
    }

    /** One entry per channel, in the list's order. */
    private fun briefs(l: List<Channel>) = JSONArray().apply { l.byChannel().forEach { put(Edit.brief(it.first())) } }

    private fun apply(c: Context, req: JSONObject) {
        val (favIds, groupPlans) = Edit.clean(req) { java.lang.Long.toString(System.nanoTime(), 36).takeLast(8) }
        val haveFavs = Store.favourites(c)
        val haveGroups = Store.groups(c)
        val have = HashMap<String, List<Channel>>()
        (haveFavs + haveGroups.flatMap { it.channels }).groupBy { it.id }.forEach { (id, l) -> have[id] = l.distinctBy { it.url } }
        val cache = HashMap<String, List<Channel>>()                  // a country's list, read once for this edit
        val resolve = { id: String ->
            val cc = Ids.country(id).ifEmpty { Store.country(c) }
            cache.getOrPut(cc) { Catalog.channels(c, cc) }.filter { it.id == id }
        }
        val favs = if (favIds == null) haveFavs else Edit.channels(favIds, have, resolve)
        val groups = if (groupPlans == null) haveGroups else groupPlans.map { Store.Group(it.id, it.name, Edit.channels(it.ids, have, resolve)) }
        Store.replace(c, favs, groups)
    }
}
