package dev.tvremote.phone

import dev.tvremote.core.T
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.tvremote.core.adb.AdbLink
import dev.tvremote.core.Errors
import dev.tvremote.core.Fleet
import dev.tvremote.core.web.Manage
import dev.tvremote.core.ontv.RemoteLabels
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors

// 📦 Several TVs at once, from the list of saved TVs (homestay, hotel, a house of TVs): one APK installed on each TV ticked,
// one app removed, or a restart, with a line per TV saying how it goes. The computer's page has the same on its own list of
// TVs (app/desktop Fleet.kt); TV Setup has no list, it manages only the TV it runs on.

private const val PARALLEL = 3                                        // an APK goes over the home Wi-Fi to each TV: a few at a time

/** One TV in a run: what its line shows, read by the ticker on the UI thread. */
private class Part(val ip: String, val line: TextView) {
    @Volatile var step = ""
    @Volatile var state = 0                                           // 0 working, 1 done, 2 failed, 3 stopped by the person
    val cancel = dev.tvremote.core.web.Cancel()                           // the Stop button of the run reaches every TV through this
    @Volatile var link: AdbLink? = null
    val job = JSONObject()                                            // Manage.install puts the copy's progress here
}

/** The TV could not be reached at all: said as is, not through Errors (whose "network is slow" would mislead here). */
private class Unreachable(msg: String) : Exception(msg)

private fun MainActivity.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

/** Beside the heading of the saved TVs once there are two of them: small, in the accent colour, so it is seen without a whole row. */
internal fun MainActivity.fleetButton(): android.widget.Button {
    val b = button("@tv|" + T("many_tvs.several_tvs"), SOFT)
    b.setInk(ACCENT_TEXT)
    b.textSize = 14f
    b.typeface = android.graphics.Typeface.DEFAULT_BOLD
    b.setPadding(dp(12), 0, dp(12), 0)
    b.contentDescription = T("many_tvs.several_tvs_at_once")
    b.setOnClickListener { fleetMenu() }
    return b
}

private fun MainActivity.fleetMenu() = menu(T("many_tvs.several_tvs_at_once_2"), 0, listOf(
    Triple("@package|" + T("many_tvs.install_an_apk_on"),
        "") {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/vnd.android.package-archive", "application/octet-stream")), PICK_FLEET)
        } catch (e: Exception) { toast(T("media.this_phone_has_no")) }
    },
    Triple("@close|" + T("many_tvs.remove_apps"), "") {
        fleetPick(T("many_tvs.remove_apps_on_which")) { fleetUninstall(it) }
    },
    Triple("@power|" + T("fleet.menu_restart"), "") {
        fleetPick(T("many_tvs.restart_which_tvs")) { ips ->
            fleetRun(T("fleet.menu_restart"), ips) { link, _ -> Fleet.reboot(link.get()); T("many_tvs.restarting") }
        }
    }))

/** The saved TVs to tick; the ones that answered (or not checked yet) come ticked. */
private fun MainActivity.fleetPick(title: String, then: (List<String>) -> Unit) {
    val ips = savedIps()
    val names = ips.map { title(it) + if (reach[it] == false) T("many_tvs.no_answer") else "" }.toTypedArray()
    val on = BooleanArray(ips.size) { reach[ips[it]] != false }
    Popup.Builder(this).setTitle(title)
        .setMultiChoiceItems(names, on) { _, i, c -> on[i] = c }
        .setPositiveButton(T("draw.next")) { _, _ ->
            val chosen = ips.filterIndexed { i, _ -> on[i] }
            if (chosen.isEmpty()) toast(T("fleet.pick_one")) else then(chosen)
        }.setNegativeButton(T("common.cancel"), null).show()
}

/** The APK chosen in the file picker: copied here once, since it goes to several TVs, then the TVs to put it on. */
internal fun MainActivity.fleetApk(uri: Uri) {
    var name = "app.apk"
    try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME).let { if (it >= 0) c.getString(it)?.let { n -> name = n } }
        }
    } catch (ignored: Exception) { }
    val apk = File(cacheDir, "fleet.apk")
    Thread {
        val ok = try { contentResolver.openInputStream(uri)!!.use { i -> apk.outputStream().use { i.copyTo(it) } }; true } catch (e: Exception) { false }
        ui.post {
            if (isFinishing) return@post
            if (!ok) { toast(T("many_tvs.could_not_read_this")); return@post }
            fleetPick(T("many_tvs.install_on_which_tvs", "name" to name)) { ips ->
                fleetRun(T("many_tvs.install", "name" to name), ips, stoppable = true) { link, p ->
                    FileInputStream(apk).use { Manage(link, null, cacheDir).install(it, apk.length(), p.job, p.cancel) }
                    T("many_tvs.installed")
                }
            }
        }
    }.start()
}

/** Asks each TV picked for its apps, then which one to remove from the TVs that have it. */
private fun MainActivity.fleetUninstall(ips: List<String>) {
    val wait = Popup.Builder(this).setMessage(T("fleet.asking_each")).setCancelable(false).show()
    val apps = HashMap<String, MutableList<String>>()                 // package -> the TVs that have it
    val names = HashMap<String, String>()
    val pool = Executors.newFixedThreadPool(PARALLEL)
    for (ip in ips) pool.execute {
        val link = AdbLink(filesDir, route[ip] ?: ip)
        try {
            val info = Fleet.info(link.get())
            val label = try { RemoteLabels(link, { assets.open("tvr-helper.jar").use { it.readBytes() } }).names() } catch (e: Exception) { emptyMap() }
            synchronized(apps) { info.apps.forEach { apps.getOrPut(it) { ArrayList() }.add(ip) }; names.putAll(label) }
        } catch (ignored: Exception) {                                // a TV that does not answer has nothing to remove
        } finally { link.close() }
    }
    pool.shutdown()
    Thread {
        pool.awaitTermination(3, java.util.concurrent.TimeUnit.MINUTES)
        ui.post {
            if (isFinishing) return@post
            wait.dismiss()
            val list = apps.filterKeys { Fleet.canUninstall(it) == null && it != "com.tailscale.ipn" }.toList()
                .sortedWith(compareBy({ -it.second.size }, { names[it.first] ?: it.first }))
            if (list.isEmpty()) { toast(T("fleet.no_apps")); return@post }
            // one row per app, ticked by a tap anywhere on it: its name, and under it, small, the package and how many TVs have it
            val picked = BooleanArray(list.size)
            val rows = col()
            rows.setPadding(dp(12), dp(4), dp(20), dp(4))
            lateinit var box: Popup
            val sync = {
                val n = picked.count { it }
                box.getButton(Popup.BUTTON_POSITIVE)?.let { it.isEnabled = n > 0; it.text = T("fleet.remove") + if (n > 0) " ($n)" else "" }
            }
            list.forEachIndexed { i, (pkg, on) ->
                val check = android.widget.CheckBox(this)
                val text = col()
                text.addView(label(names[pkg] ?: pkg, 16, TEXT))
                text.addView(label(pkg + " · " + on.size + "/" + ips.size + " TV", 12, MUTED))
                val row = LinearLayout(this)
                row.gravity = android.view.Gravity.CENTER_VERTICAL
                row.setPadding(0, dp(6), 0, dp(6))
                row.addView(check)
                row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
                row.setOnClickListener { check.isChecked = !check.isChecked }
                check.setOnCheckedChangeListener { _, c -> picked[i] = c; sync() }
                rows.addView(row)
            }
            val sv = ScrollView(this)
            sv.addView(rows)
            box = Popup.Builder(this).setTitle(T("many_tvs.remove_which_apps")).setView(sv)
                .setPositiveButton(T("fleet.remove")) { _, _ ->
                    val chosen = list.filterIndexed { i, _ -> picked[i] }
                    val pkgs = chosen.map { it.first }
                    val on = chosen.flatMap { it.second }.distinct()
                    val what = if (pkgs.size == 1) names[pkgs[0]] ?: pkgs[0] else T("many_tvs.apps", "n" to pkgs.size)
                    Popup.Builder(this).setMessage(T("many_tvs.remove_from_these_tvs", "what" to what) +
                        (if (pkgs.size > 1) "\n" + pkgs.joinToString("\n") { "• " + (names[it] ?: it) } else "") + "\n\n" + T("many_tvs.tvs") + "\n" + on.joinToString("\n") { "• " + title(it) })
                        .setPositiveButton(T("fleet.remove")) { _, _ ->
                            fleetRun(T("many_tvs.remove", "what" to what), on) { link, _ -> val n = Fleet.uninstall(link.get(), pkgs); T("many_tvs.removed_apps", "n" to n) }
                        }.setNegativeButton(T("common.cancel"), null).show()
                }.setNegativeButton(T("common.cancel"), null).show()
            sync()
        }
    }.start()
}

/** One line per TV under its name, until every TV is done; the work goes on if the box is closed. */
private fun MainActivity.fleetRun(title: String, ips: List<String>, stoppable: Boolean = false, work: (AdbLink, Part) -> String) {
    val list = col()
    list.setPadding(dp(20), dp(8), dp(20), dp(8))
    val parts = ips.map { ip ->
        val name = label(title(ip), 16, TEXT)
        name.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val line = label("", 14, MUTED)
        list.addView(name, gap(10))
        list.addView(line)
        Part(ip, line).also { it.step = T("fleet.step_waiting") }
    }
    val sv = ScrollView(this)
    sv.addView(list)
    val box = Popup.Builder(this).setTitle(title).setView(sv).setPositiveButton(T("common.close"), null)
        .setNeutralButton(T("page.stop"), null).show()
    val stopAll = box.getButton(Popup.BUTTON_NEUTRAL)            // set after show(): the dialog must stay open while the TVs wind down
    if (!stoppable) stopAll.visibility = android.view.View.GONE   // a removal or a restart has no half way to stop at
    stopAll.setOnClickListener { parts.forEach { it.cancel.stop() }; stopAll.isEnabled = false }
    val pool = Executors.newFixedThreadPool(PARALLEL)
    for (p in parts) pool.execute {
        if (p.cancel.stopped) { p.step = "■ " + T("files.stopped"); p.state = 3; return@execute }   // never started
        p.step = T("fleet.step_connecting")
        val link = AdbLink(filesDir, route[p.ip] ?: p.ip)
        p.link = link
        try {
            try { link.get() } catch (e: Exception) { throw Unreachable(T("many_tvs.tv_off_on_another")) }
            p.step = T("many_tvs.working")
            p.step = "✓ " + work(link, p)
            p.state = 1
        } catch (e: Exception) {
            if (p.cancel.stopped) { p.step = "■ " + T("files.stopped"); p.state = 3 }
            else { p.step = "✗ " + (if (e is Unreachable) e.message else Errors.say(e, dev.tvremote.core.Words.lang)); p.state = 2 }
        } finally { link.close() }
    }
    pool.shutdown()
    val tick = object : Runnable {
        override fun run() {
            for (p in parts) {
                val pct = p.job.optInt("pct", 0)
                p.line.text = when {
                    p.state != 0 -> p.step
                    p.link?.waitingForUser() == true -> T("many_tvs.the_tv_asks_allow")
                    pct in 1..89 -> T("many_tvs.copying_to_the_tv", "pct" to pct)
                    pct >= 90 -> T("many_tvs.installing")
                    else -> p.step
                }
                p.line.setTextColor(when (p.state) { 1 -> OK_TEXT; 2 -> ERR_TEXT; else -> MUTED })
            }
            if (parts.all { it.state != 0 }) {
                val bad = parts.count { it.state == 2 }
                stopAll.visibility = android.view.View.GONE
                box.setTitle(if (parts.any { it.state == 3 }) T("files.stopped") else if (bad == 0) T("many_tvs.done_on_all_tvs", "n" to parts.size)
                    else T("many_tvs.done_tvs_tvs", "ok" to parts.size - bad, "bad" to bad))
            } else if (!isFinishing) ui.postDelayed(this, 500)
        }
    }
    ui.post(tick)
}
