package dev.tvremote.phone

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import dev.tvremote.core.AdbLink
import dev.tvremote.core.Errors
import dev.tvremote.core.Fleet
import dev.tvremote.core.Manage
import dev.tvremote.core.RemoteLabels
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
    @Volatile var state = 0                                           // 0 working, 1 done, 2 failed
    @Volatile var link: AdbLink? = null
    val job = JSONObject()                                            // Manage.install puts the copy's progress here
}

/** The TV could not be reached at all: said as is, not through Errors (whose "network is slow" would mislead here). */
private class Unreachable(msg: String) : Exception(msg)

private fun MainActivity.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

/** Beside the heading of the saved TVs once there are two of them: small, in the accent colour, so it is seen without a whole row. */
internal fun MainActivity.fleetButton(): android.widget.Button {
    val b = button("@tv|" + t("Nhiều TV  ›", "Several TVs  ›"), SOFT)
    b.setInk(ACCENT_TEXT)
    b.textSize = 14f
    b.typeface = android.graphics.Typeface.DEFAULT_BOLD
    b.setPadding(dp(12), 0, dp(12), 0)
    b.contentDescription = t("Làm trên nhiều TV cùng lúc: cài APK, gỡ app, khởi động lại", "Several TVs at once: install an APK, remove apps, restart")
    b.setOnClickListener { fleetMenu() }
    return b
}

private fun MainActivity.fleetMenu() = menu(t("Nhiều TV cùng lúc", "Several TVs at once"), 0, listOf(
    Triple("@package|" + t("Cài APK lên các TV", "Install an APK on the TVs"),
        "") {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/vnd.android.package-archive", "application/octet-stream")), PICK_FLEET)
        } catch (e: Exception) { toast(t("Điện thoại không có app chọn tệp.", "This phone has no file picker.")) }
    },
    Triple("@close|" + t("Gỡ app", "Remove apps"), "") {
        fleetPick(t("Gỡ app trên TV nào?", "Remove apps on which TVs?")) { fleetUninstall(it) }
    },
    Triple("@power|" + t("Khởi động lại", "Restart"), "") {
        fleetPick(t("Khởi động lại TV nào?", "Restart which TVs?")) { ips ->
            fleetRun(t("Khởi động lại", "Restart"), ips) { link, _ -> Fleet.reboot(link.get()); t("đang khởi động lại", "restarting") }
        }
    }))

/** The saved TVs to tick; the ones that answered (or not checked yet) come ticked. */
private fun MainActivity.fleetPick(title: String, then: (List<String>) -> Unit) {
    val ips = savedIps()
    val names = ips.map { title(it) + if (reach[it] == false) t(" · không thấy", " · no answer") else "" }.toTypedArray()
    val on = BooleanArray(ips.size) { reach[ips[it]] != false }
    AlertDialog.Builder(this).setTitle(title)
        .setMultiChoiceItems(names, on) { _, i, c -> on[i] = c }
        .setPositiveButton(t("Tiếp", "Next")) { _, _ ->
            val chosen = ips.filterIndexed { i, _ -> on[i] }
            if (chosen.isEmpty()) toast(t("Chọn ít nhất một TV", "Pick at least one TV")) else then(chosen)
        }.setNegativeButton(t("Hủy", "Cancel"), null).show()
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
            if (!ok) { toast(t("Không đọc được tệp này.", "Could not read this file.")); return@post }
            fleetPick(t("Cài $name lên TV nào?", "Install $name on which TVs?")) { ips ->
                fleetRun(t("Cài ", "Install ") + name, ips) { link, p ->
                    FileInputStream(apk).use { Manage(link, null, cacheDir).install(it, apk.length(), p.job) }
                    t("đã cài", "installed")
                }
            }
        }
    }.start()
}

/** Asks each TV picked for its apps, then which one to remove from the TVs that have it. */
private fun MainActivity.fleetUninstall(ips: List<String>) {
    val wait = AlertDialog.Builder(this).setMessage(t("Đang hỏi từng TV...", "Asking each TV...")).setCancelable(false).show()
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
            if (list.isEmpty()) { toast(t("Các TV đã chọn không có app tự cài nào.", "The TVs picked have no apps you installed.")); return@post }
            // one row per app, ticked by a tap anywhere on it: its name, and under it, small, the package and how many TVs have it
            val picked = BooleanArray(list.size)
            val rows = col()
            rows.setPadding(dp(12), dp(4), dp(20), dp(4))
            lateinit var box: AlertDialog
            val sync = {
                val n = picked.count { it }
                box.getButton(AlertDialog.BUTTON_POSITIVE)?.let { it.isEnabled = n > 0; it.text = t("Gỡ", "Remove") + if (n > 0) " ($n)" else "" }
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
            box = AlertDialog.Builder(this).setTitle(t("Gỡ app nào?", "Remove which apps?")).setView(sv)
                .setPositiveButton(t("Gỡ", "Remove")) { _, _ ->
                    val chosen = list.filterIndexed { i, _ -> picked[i] }
                    val pkgs = chosen.map { it.first }
                    val on = chosen.flatMap { it.second }.distinct()
                    val what = if (pkgs.size == 1) names[pkgs[0]] ?: pkgs[0] else t("${pkgs.size} app", "${pkgs.size} apps")
                    AlertDialog.Builder(this).setMessage(t("Gỡ $what khỏi các TV này?", "Remove $what from these TVs?") +
                        (if (pkgs.size > 1) "\n" + pkgs.joinToString("\n") { "• " + (names[it] ?: it) } else "") + "\n\n" + t("TV:", "TVs:") + "\n" + on.joinToString("\n") { "• " + title(it) })
                        .setPositiveButton(t("Gỡ", "Remove")) { _, _ ->
                            fleetRun(t("Gỡ ", "Remove ") + what, on) { link, _ -> val n = Fleet.uninstall(link.get(), pkgs); t("đã gỡ $n app", "removed $n apps") }
                        }.setNegativeButton(t("Hủy", "Cancel"), null).show()
                }.setNegativeButton(t("Hủy", "Cancel"), null).show()
            sync()
        }
    }.start()
}

/** One line per TV under its name, until every TV is done; the work goes on if the box is closed. */
private fun MainActivity.fleetRun(title: String, ips: List<String>, work: (AdbLink, Part) -> String) {
    val list = col()
    list.setPadding(dp(20), dp(8), dp(20), dp(8))
    val parts = ips.map { ip ->
        val name = label(title(ip), 16, TEXT)
        name.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val line = label("", 14, MUTED)
        list.addView(name, gap(10))
        list.addView(line)
        Part(ip, line).also { it.step = t("đang chờ lượt", "waiting its turn") }
    }
    val sv = ScrollView(this)
    sv.addView(list)
    val box = AlertDialog.Builder(this).setTitle(title).setView(sv).setPositiveButton(t("Đóng", "Close"), null).show()
    val pool = Executors.newFixedThreadPool(PARALLEL)
    for (p in parts) pool.execute {
        p.step = t("đang kết nối", "connecting")
        val link = AdbLink(filesDir, route[p.ip] ?: p.ip)
        p.link = link
        try {
            try { link.get() } catch (e: Exception) { throw Unreachable(t("TV tắt, khác mạng hoặc chưa bật ADB", "TV off, on another network, or ADB not on")) }
            p.step = t("đang làm", "working")
            p.step = "✓ " + work(link, p)
            p.state = 1
        } catch (e: Exception) {
            p.step = "✗ " + (if (e is Unreachable) e.message else Errors.say(e, VI))
            p.state = 2
        } finally { link.close() }
    }
    pool.shutdown()
    val tick = object : Runnable {
        override fun run() {
            for (p in parts) {
                val pct = p.job.optInt("pct", 0)
                p.line.text = when {
                    p.state != 0 -> p.step
                    p.link?.waitingForUser() == true -> t("TV đang hỏi “Cho phép gỡ lỗi USB?”: bấm Cho phép trên TV", "The TV asks “Allow USB debugging?”: press Allow on the TV")
                    pct in 1..89 -> t("đang chép lên TV ", "copying to the TV ") + pct + "%"
                    pct >= 90 -> t("đang cài...", "installing...")
                    else -> p.step
                }
                p.line.setTextColor(when (p.state) { 1 -> OK_TEXT; 2 -> ERR_TEXT; else -> MUTED })
            }
            if (parts.all { it.state != 0 }) {
                val bad = parts.count { it.state == 2 }
                box.setTitle(if (bad == 0) t("Xong trên cả ${parts.size} TV", "Done on all ${parts.size} TVs")
                    else t("Xong: ${parts.size - bad} TV ✓, $bad TV ✗", "Done: ${parts.size - bad} TVs ✓, $bad TVs ✗"))
            } else if (!isFinishing) ui.postDelayed(this, 500)
        }
    }
    ui.post(tick)
}
