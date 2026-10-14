package dev.tvremote.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Collections
import java.util.Locale
import java.util.regex.Pattern

/**
 * Managing a TV over ADB for the page both apps serve: installed apps, files, APK installs and a health snapshot.
 * The paths and the answers are the ones the computer's server gives (app/web/server.py), because all three serve the same page
 * (app/shared/web). Whoever owns the HttpServer checks first that the caller may use it.
 */
class Manage(private val link: AdbLink, private val labels: Labels?, private val dir: java.io.File?) {
    /** Names and icons for packages, where the app can ask the system for them (the TV app); otherwise package names are shown. */
    interface Labels {
        fun label(pkg: String): String?
        fun icons(): Boolean
    }

    private val jobs: MutableMap<String, JSONObject> = Collections.synchronizedMap(LinkedHashMap<String, JSONObject>())
    private var nextJob = 1

    /** null when the path is not ours. */
    @Throws(Exception::class)
    fun handle(r: HttpServer.Request): HttpServer.Response? {
        if (!r.path.startsWith("/api/")) return null
        val what = r.path.substring(5)
        val post = r.method == "POST"
        try {
            when (what) {
                "apps" -> return HttpServer.Response.json(apps(r.q("system") == "1"))
                "files" -> return HttpServer.Response.json(files(r.q("path")))
                "file" -> return download(r)
                "health" -> return HttpServer.Response.json(health())
                "stats" -> return HttpServer.Response.json(stats(r.q("days").toIntOrNull() ?: 7))
                "tailscale" -> { val t = Tailscale(link.get(), dir); return HttpServer.Response.json(JSONObject().put("installed", t.installed()).put("ip", t.ip())) }
                "job" -> {
                    val j = jobs[r.q("id")]
                    return if (j == null) HttpServer.Response.error(404, "no such job") else HttpServer.Response.json(JSONObject(j.toString()))
                }
            }
            if (!post) return null
            return when (what) {
                "app" -> { open(r.json().optString("link")); HttpServer.Response.json(JSONObject().put("ok", true).put("via", "adb")) }
                "app/launch", "app/stop", "app/uninstall" -> app(r.json().optString("pkg"), what.substring(4))
                "adb/connect" -> HttpServer.Response.json(JSONObject().put("state", adbState()))
                "shell" -> shell(r.json().optString("cmd"))
                "search" -> {
                    val q = r.json().optString("query").trim()
                    if (q.isEmpty()) throw IllegalArgumentException("empty query")
                    if (sh("am start -a android.search.action.GLOBAL_SEARCH --es query " + Adb.q(q) + " 2>&1").contains("Error")) throw IllegalArgumentException("this TV has no Google search / Assistant")
                    HttpServer.Response.ok()
                }
                "upload" -> HttpServer.Response.json(upload(r))
                "file/delete", "file/rename", "file/mkdir", "file/open" -> fileOp(what.substring(5), r.json())
                "file/install" -> HttpServer.Response.json(installPath(safePath(r.json().optString("path"))))
                "free" -> HttpServer.Response.json(free())
                "setup" -> { val d = r.json(); HttpServer.Response.json(setup(d.optString("mode") == "undo", d.optString("lang") != "en")) }
                "fetch" -> HttpServer.Response.json(fetch(r.json().optString("url"), false))
                "install" -> HttpServer.Response.json(fetch(r.json().optString("url"), true))
                "tailscale/start" -> HttpServer.Response.json(job("tailscale") { j ->
                    val t = Tailscale(link.get(), dir)
                    val url = t.start { what, pct -> j.put("msg", what).put("pct", pct) }
                    if (url.isEmpty()) { j.put("ip", t.ip()); "in" } else { j.put("url", url); "sign in" }
                })
                else -> null
            }
        } catch (e: IllegalArgumentException) {
            return HttpServer.Response.error(400, e.message.toString())
        } catch (e: IOException) {
            return HttpServer.Response.error(503, if (link.waitingForUser()) "adb:allow" else "adb: " + e.message)
        }
    }

    /** ready, unauthorized (the TV is asking) or closed: the words the page knows. */
    fun adbState(): String = link.state()

    @Throws(IOException::class)
    private fun sh(cmd: String): String = link.get().shell(cmd)

    // ---------------------------------------------------------------- apps
    private fun apps(system: Boolean): JSONObject {
        val pkgs = ArrayList<String>()
        for (line in sh("pm list packages" + (if (system) "" else " -3")).jsplit("\n"))
            if (line.startsWith("package:")) pkgs.add(line.substring(8).trim())
        pkgs.sort()
        val out = JSONArray()
        val icons = labels != null && labels.icons()
        for (p in pkgs) {
            val label = labels?.label(p)
            out.put(JSONObject().put("pkg", p).put("meta", JSONObject().put("label", label ?: p.substring(p.lastIndexOf('.') + 1)).put("icon", icons).put("banner", icons)))
        }
        return JSONObject().put("apps", out)
    }

    /** "Open on the TV": the settings, an app by its package name, or a link for whatever app takes it. */
    private fun open(what: String) {
        val w = what.trim()
        if (w == "settings") { if (sh("am start -a android.settings.SETTINGS 2>&1").contains("Error")) throw IllegalArgumentException("the TV did not open its settings") }
        else if (PKG.matcher(w).matches()) app(w, "launch")
        else openLink(w)
    }

    private fun app(pkg: String, action: String): HttpServer.Response {
        if (!PKG.matcher(pkg).matches()) throw IllegalArgumentException("bad package name")
        when (action) {
            "launch" -> {
                for (cat in arrayOf("LEANBACK_LAUNCHER", "LAUNCHER")) {   // a TV app's own screen first, then a phone-style one
                    val lines = sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.$cat $pkg 2>/dev/null").trim().jsplit("\n")
                    val component = lines[lines.size - 1].trim()
                    if (!component.matches(Regex("[\\w.]+/[\\w.\$]+")) || !component.startsWith("$pkg/")) continue
                    val out = sh("am start -n " + Adb.q(component) + " 2>&1")
                    if (!out.contains("Error")) return HttpServer.Response.ok()
                }
                throw IllegalArgumentException("this app cannot be opened (no launcher screen)")
            }
            "stop" -> { sh("am force-stop $pkg"); return HttpServer.Response.ok() }
            "uninstall" -> {
                val out = sh("pm uninstall $pkg 2>&1").trim()
                if (!out.contains("Success")) throw IllegalArgumentException(if (out.isEmpty()) "uninstall failed" else out)
                return HttpServer.Response.ok()
            }
            else -> throw IllegalArgumentException("unknown action")
        }
    }

    // ---------------------------------------------------------------- terminal
    /** Runs one command as the TV's shell user and returns what it printed (at most 30 s and 200 KB). */
    private fun shell(cmd: String): HttpServer.Response {
        if (cmd.trim().isEmpty()) throw IllegalArgumentException("empty command")
        val s = link.get().open("shell:$cmd 2>&1")
        val out = ByteArrayOutputStream()
        val b = ByteArray(16384)
        val until = System.currentTimeMillis() + 30000
        var cut = false
        try {
            while (true) {
                val left = until - System.currentTimeMillis()
                if (left <= 0 || out.size() > 200000) { cut = true; break }
                val n = s.read(b, 0, b.size, left)
                if (n < 0) break
                out.write(b, 0, n)
            }
        } finally {
            s.close()
        }
        return HttpServer.Response.json(JSONObject().put("out", out.toString("UTF-8").replace("\r\n", "\n")).put("cut", cut))
    }

    // ---------------------------------------------------------------- files
    private fun files(raw: String): JSONObject {
        val path = safePath(raw)
        val q = Adb.q("$path/")                                       // the trailing / follows the /sdcard symlink
        val part = sh("ls -lA $q 2>&1 | cat; echo '#--'; df -k $q 2>/dev/null | tail -1; echo '#--'; ls /storage 2>/dev/null").jsplit("#--", -1)
        val items = ArrayList<JSONObject>()
        for (line in part[0].jsplit("\n")) {
            val m = LS.matcher(line.trim())
            if (m.matches()) {
                val name = m.group(4)!!.jsplit(" -> ")[0]
                items.add(JSONObject().put("name", name).put("dir", m.group(1) != "-").put("size", m.group(2)!!.toLong()).put("mtime", m.group(3)))
            } else if (line.contains("No such file") || line.contains("Permission denied")) throw IllegalArgumentException(line.trim())
        }
        items.sortWith { a, b ->
            if (a.optBoolean("dir") != b.optBoolean("dir")) (if (a.optBoolean("dir")) -1 else 1)
            else a.optString("name").lowercase(Locale.ROOT).compareTo(b.optString("name").lowercase(Locale.ROOT))
        }
        val out = JSONObject().put("path", path).put("items", JSONArray(items))
        val df = if (part.size > 1) part[1].trim().jsplit("\\s+") else emptyArray<String>()
        if (df.size > 3 && df[1].matches(DIGITS) && df[3].matches(DIGITS)) out.put("space", JSONObject().put("total", df[1].toLong() * 1024).put("free", df[3].toLong() * 1024))
        val usb = JSONArray()
        if (part.size > 2) for (v in part[2].trim().jsplit("\\s+"))
            if (v.isNotEmpty() && v != "emulated" && v != "self" && !v.startsWith("sdcard")) usb.put(v)
        return out.put("volumes", usb)
    }

    /** Makes the TV open a link (a video, a web page) with whatever app takes it. */
    fun openLink(url: String) {
        val u = url.trim()
        if (!u.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:\\S+$"))) throw IllegalArgumentException("not a link")
        val out = sh("am start -a android.intent.action.VIEW -d " + Adb.q(u) + " 2>&1")
        if (out.contains("Error") || out.lowercase(Locale.ROOT).contains("unable to resolve")) throw IllegalArgumentException("the TV has no app that can open this")
    }

    /** Puts a file from this device into the TV's Download folder and has the TV open it: a photo, a video, a song. */
    @Throws(IOException::class)
    fun cast(input: InputStream, length: Long, name: String) {
        val path = CAST_DIR + "/" + safeName(name)
        sh("mkdir -p " + Adb.q(CAST_DIR))
        push(input, length, path, null)
        fileOp("open", JSONObject().put("path", path))
    }

    private fun fileOp(op: String, d: JSONObject): HttpServer.Response {
        val path = safePath(d.optString("path"))
        var out: String
        when (op) {
            "delete" -> {
                if (root(path)) throw IllegalArgumentException("refusing to delete a storage root")
                out = sh("rm -rf " + Adb.q(path) + " 2>&1")
            }
            "mkdir" -> {
                val name = path.substring(path.lastIndexOf('/') + 1)
                if (root(path) || name != safeName(name)) throw IllegalArgumentException("bad name")
                out = sh("mkdir " + Adb.q(path) + " 2>&1")
            }
            "rename" -> {
                val name = d.optString("name")
                if (root(path) || name != safeName(name)) throw IllegalArgumentException("bad name")
                val dst = Adb.q(path.substring(0, path.lastIndexOf('/')) + "/" + name)
                out = sh("if [ -e $dst ]; then echo 'a file with that name already exists'; else mv " + Adb.q(path) + " $dst 2>&1; fi")
            }
            else -> {                                                 // open on the TV with whatever app handles the type
                val real = if (path.startsWith("/sdcard")) "/storage/emulated/0" + path.substring(7) else path
                out = sh("am start -a android.intent.action.VIEW -d " + Adb.q("file://" + real.replace(" ", "%20")) + " -t " + Adb.q(mime(path)) + " 2>&1")
                out = if (out.contains("Error") || out.lowercase(Locale.ROOT).contains("unable to resolve")) "the TV has no app that can open this" else ""
            }
        }
        if (out.trim().isNotEmpty()) throw IllegalArgumentException(out.trim())
        return HttpServer.Response.ok()
    }

    private fun download(r: HttpServer.Request): HttpServer.Response {
        val path = safePath(r.q("path"))
        val adb = link.get()
        val size = adb.shell("stat -c %s " + Adb.q(path) + " 2>/dev/null").trim()
        if (!size.matches(DIGITS)) throw IllegalArgumentException("cannot read this file")
        val s = adb.exec("cat " + Adb.q(path))
        val inline = r.q("inline") == "1"
        val out = HttpServer.Response.stream(if (inline) mime(path).replace("*/*", "application/octet-stream") else "application/octet-stream", size.toLong()) { o ->
            try {
                val b = ByteArray(65536)
                while (true) {
                    val n = s.read(b, 0, b.size, 0)
                    if (n < 0) break
                    o.write(b, 0, n)
                }
            } finally {
                s.close()
            }
        }
        if (!inline) out.headers["Content-Disposition"] = "attachment; filename*=UTF-8''" + URLEncoder.encode(path.substring(path.lastIndexOf('/') + 1), "UTF-8").replace("+", "%20")
        return out
    }

    /** Copies `length` bytes into a file on the TV. */
    @Throws(IOException::class)
    private fun push(input: InputStream, length: Long, dest: String, job: JSONObject?) {
        val adb = link.get()
        val s = adb.exec("cat > " + Adb.q(dest))
        try {
            val b = ByteArray(65536)
            var done = 0L
            while (length < 0 || done < length) {
                val n = input.read(b, 0, if (length < 0) b.size else minOf(b.size.toLong(), length - done).toInt())
                if (n < 0) break
                s.write(b, 0, n)
                done += n
                if (job != null && length > 0) try { job.put("pct", (done * 90 / length).toInt()) } catch (ignored: Exception) { }
            }
            if (length >= 0 && done < length) throw IOException("upload cut short")
        } finally {
            s.close()
        }
        val want = if (length >= 0) length.toString() else null       // cat finishes writing shortly after the stream closes
        for (i in 0 until 40) {
            val got = adb.shell("stat -c %s " + Adb.q(dest) + " 2>/dev/null").trim()
            if (if (want == null) got.matches(DIGITS) && i > 1 else got == want) return
            try { Thread.sleep(150) } catch (e: InterruptedException) { break }
        }
        if (want != null) throw IOException("the TV did not store the whole file (is its storage full?)")
    }

    /**
     * A file from the browser, into a folder on the TV or (kind=install) straight into the installer. The file arrives in the
     * request body and has to be read before we answer, so the answer is a job that is already finished.
     */
    private fun upload(r: HttpServer.Request): JSONObject {
        val name = safeName(r.q("name"))
        val done = { title: String, msg: String -> JSONObject().put("id", "u" + nextJob++).put("title", title).put("status", "done").put("pct", 100).put("msg", msg) }
        if (r.q("kind") == "install") { installUpload(r); return done("install $name", "installed") }
        val dest = safePath(r.q("dest")) + "/" + name
        push(r.body, r.length, dest, null)
        return done("upload $name", "saved $dest")
    }

    // ---------------------------------------------------------------- installs and downloads (jobs the page polls)
    private fun interface Work { fun run(job: JSONObject): String }

    private fun job(title: String, work: Work): JSONObject {
        val j = JSONObject().put("id", (nextJob++).toString()).put("title", title).put("status", "running").put("pct", 0).put("msg", "")
        jobs[j.getString("id")] = j
        while (jobs.size > 20) jobs.remove(jobs.keys.iterator().next())
        Thread({
            try {
                val msg = work.run(j)
                j.put("status", "done").put("pct", 100).put("msg", msg)
            } catch (e: Exception) {
                try { j.put("status", "error").put("msg", e.message ?: e.javaClass.simpleName) } catch (ignored: Exception) { }
            }
        }, "job").apply { isDaemon = true }.start()
        return JSONObject(j.toString())
    }

    @Throws(IOException::class)
    private fun pmInstall(path: String): String {
        val adb = link.get()
        val q = Adb.q(path)
        if (!adb.shell("head -c 2 $q 2>/dev/null").startsWith("PK")) throw IOException("not an APK file")
        val size = adb.shell("stat -c %s $q").trim()
        val out = adb.shell("cat $q | pm install -r -S $size 2>&1")
        if (!out.contains("Success")) throw IOException(if (out.trim().isEmpty()) "install failed" else out.trim())
        return "installed"
    }

    private fun installPath(path: String): JSONObject =
        job("install " + path.substring(path.lastIndexOf('/') + 1)) { j -> j.put("pct", 50); pmInstall(path) }

    /** The APK arrives in the request body: it has to be read before we answer, so this one is not a background job. */
    private fun installUpload(r: HttpServer.Request) {
        if (r.length <= 0) throw IllegalArgumentException("empty upload")
        push(r.body, r.length, TMP_APK, null)
        try {
            pmInstall(TMP_APK)
        } catch (e: IOException) {
            throw IllegalArgumentException(e.message)
        } finally {
            sh("rm -f $TMP_APK")
        }
    }

    /** Closes the apps that are only waiting in the background (what is on screen stays); says how much memory came back, in MB. */
    private fun free(): JSONObject {
        val avail = { (find("MemAvailable:\\s+(\\d+)", sh("cat /proc/meminfo"))?.toLong() ?: 0L) / 1024 }
        val before = avail()
        sh("am kill-all")
        Thread.sleep(1500)
        val after = avail()
        return JSONObject().put("before", before).put("after", after).put("freed", maxOf(0L, after - before))
    }

    /** "Optimize TV" or its undo; the job's message grows a line per step. */
    private fun setup(undo: Boolean, vi: Boolean): JSONObject =
        job(if (undo) "undo" else "optimize") { j ->
            val lines = StringBuilder()
            val s = Setup(link.get(), dir, vi) { line -> lines.append(line).append('\n'); try { j.put("log", lines.toString().trim()) } catch (ignored: Exception) { } }
            if (undo) s.undo() else s.apply()
            "ok"
        }

    /** This device downloads the link and passes it on to the TV, into Download or straight into the installer. */
    private fun fetch(url: String, install: Boolean): JSONObject {
        if (!url.matches(Regex("(?i)https?://\\S+"))) throw IllegalArgumentException("only http/https links")
        return job((if (install) "install " else "download ") + (if (url.length > 60) url.substring(0, 60) else url)) { j ->
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "tv-remote")
            try {
                if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
                val len = if (c.contentLength >= 0) c.contentLength.toLong() else -1L
                var name: String? = null
                val cd = c.getHeaderField("Content-Disposition")
                val m = if (cd == null) null else Pattern.compile("filename\\*?=(?:UTF-8'')?\"?([^\";]+)").matcher(cd)
                if (m != null && m.find()) name = URLDecoder.decode(m.group(1), "UTF-8")
                if (name == null) { val p = c.url.path; name = URLDecoder.decode(p.substring(p.lastIndexOf('/') + 1), "UTF-8") }
                name = safeName(name)
                val dest = if (install) TMP_APK else "/sdcard/Download/$name"
                j.put("msg", name)
                c.inputStream.use { push(it, len, dest, j) }
                if (!install) "saved $dest"
                else try { pmInstall(dest) } finally { sh("rm -f $TMP_APK") }
            } finally {
                c.disconnect()
            }
        }
    }

    // ---------------------------------------------------------------- history
    /** CPU load in percent over one second, or null. */
    fun cpuNow(): Int? = try {
        val f = sh("head -1 /proc/stat; sleep 1; head -1 /proc/stat").trim().jsplit("\n").map { it.trim().jsplit("\\s+").drop(1).map { n -> n.toLong() } }
        val total = f[1].sum() - f[0].sum()
        val idle = f[1][3] + f[1][4] - f[0][3] - f[0][4]
        if (total > 0) Math.round(100.0 * (total - idle) / total).toInt() else null
    } catch (e: Exception) { null }

    /** Lets the TV Setup app on the TV read which app is on screen (it cannot ask for this itself). */
    fun allowUsage(pkg: String) { try { sh("appops set $pkg GET_USAGE_STATS allow") } catch (ignored: Exception) { } }

    /** What the TV Setup app on the TV has recorded (Stats.kt there), for the last [days] days. */
    private fun stats(days: Int): JSONObject {
        val d = days.coerceIn(1, 90)
        val from = System.currentTimeMillis() / 1000 - d * 86400L
        val usage = try { JSONObject(sh("cat $STATS/usage.json 2>/dev/null")) } catch (e: Exception) { null }
        val samples = JSONArray()
        for (line in sh("tail -n " + (d * 300) + " $STATS/samples.jsonl 2>/dev/null").jsplit("\n"))
            try { val a = JSONArray(line); if (a.getLong(0) >= from) samples.put(a) } catch (ignored: Exception) { }
        val names = JSONObject()
        if (usage != null && labels != null)
            for (day in usage.keys()) for (pkg in usage.getJSONObject(day).keys()) if (!names.has(pkg)) labels.label(pkg)?.let { names.put(pkg, it) }
        return JSONObject().put("days", d).put("collector", usage != null || samples.length() > 0).put("usage", usage ?: JSONObject()).put("samples", samples).put("labels", names)
    }

    // ---------------------------------------------------------------- health
    private fun health(): JSONObject {
        val adb = link.get()
        val t0 = System.currentTimeMillis()
        adb.shell(":")
        val ping = System.currentTimeMillis() - t0
        val part = (adb.shell(HEALTH) + "#~##~##~##~##~##~##~##~##~#").jsplit("#~#", -1)
        val props = (part[0].trim() + "\n\n\n\n\n\n\n\n").jsplit("\n", -1)
        val o = JSONObject().put("ping_ms", ping).put("maker", props[0].trim()).put("model", props[1].trim()).put("android", props[2].trim()).put("sdk", props[3].trim())
                .put("patch", props[4].trim()).put("chip", props[5].trim()).put("abi", props[6].trim())
        val cores = find("(\\d+)\\s*\$", props[7].trim())
        o.put("cores", if (cores == null) JSONObject.NULL else cores.toInt() + 1)
        val stat = ArrayList<LongArray>()
        for (line in part[1].trim().jsplit("\n")) {
            val f = line.trim().jsplit("\\s+")
            if (f[0] != "cpu" || f.size < 6) continue
            var total = 0L
            for (i in 1 until f.size) total += f[i].toLong()
            stat.add(longArrayOf(total, f[4].toLong() + f[5].toLong()))
        }
        if (stat.size == 2 && stat[1][0] > stat[0][0]) {
            val total = stat[1][0] - stat[0][0]
            val idle = stat[1][1] - stat[0][1]
            o.put("cpu", Math.round(100.0 * (total - idle) / total))
        }
        val memTotal = find("MemTotal:\\s+(\\d+)", part[2])
        val memFree = find("MemAvailable:\\s+(\\d+)", part[2])
        if (memTotal != null) o.put("mem_total", memTotal.toLong() * 1024)
        if (memFree != null) o.put("mem_free", memFree.toLong() * 1024)
        val df = part[3].trim().jsplit("\\s+")
        if (df.size > 3 && df[1].matches(DIGITS) && df[3].matches(DIGITS)) o.put("disk_total", df[1].toLong() * 1024).put("disk_free", df[3].toLong() * 1024)
        val up = find("^\\s*([\\d.]+)", part[4])
        if (up != null) o.put("uptime", up.toDouble())
        o.put("screen", find("(\\d+x\\d+)", part[5]) ?: "").put("density", num(find("density: (\\d+)", part[5])))
        val fps = find("fps=([\\d.]+)", part[5])
        if (fps != null) o.put("fps", Math.round(fps.toDouble()))
        val ssid = find("SSID: ([^,]*),", part[6])
        if (ssid != null && ssid.trim().isNotEmpty() && !ssid.contains("unknown ssid"))
            o.put("wifi", JSONObject().put("ssid", ssid.trim().replace(Regex("^\"|\"\$"), "")).put("rssi", num(find("RSSI: (-?\\d+)", part[6]))).put("speed", num(find("Link speed: (\\d+)", part[6]))).put("freq", num(find("Frequency: (\\d+)", part[6]))))
        val ips = JSONArray()
        val m = Pattern.compile("(?m)^\\d+: (\\S+)\\s+inet ([\\d.]+)").matcher(part[7])
        while (m.find()) if (m.group(1) != "lo") ips.put(JSONObject().put("if", m.group(1)).put("ip", m.group(2)))
        o.put("ips", ips)
        val top = JSONArray()
        for (line in part[8].jsplit("\n")) {
            val t = Pattern.compile("\\s*([\\d.]+)\\s+([\\d.]+)\\s+(.+)").matcher(line)
            if (!t.matches() || t.group(3)!!.matches(Regex("(top -n|tail |sh -c).*")) || top.length() >= 5) continue
            val name = t.group(3)!!.trim().jsplit("\\s+")[0].replace(Regex("^\\[|\\]\$"), "")
                .replace(Regex("^(android|vendor\\.\\w+)\\.hardware\\."), "").replace(Regex("@[\\d.]+-service.*\$"), "")   // system services: keep the part that says what it is
            val label = if (labels != null && PKG.matcher(name).matches()) labels.label(name) else null
            top.put(JSONObject().put("cpu", t.group(1)!!.toDouble()).put("mem", t.group(2)!!.toDouble()).put("name", label ?: name))
        }
        o.put("top", top)
        val awake = find("mWakefulness=(\\w+)", part[9])
        val app = find("u\\d+ ([\\w.]+)/", part[9])
        o.put("on", if (awake == null) JSONObject.NULL else awake == "Awake")
        if (app != null) o.put("app", labels?.label(app) ?: app)
        // the computer's server reports what its remote-control session sees; here the same facts come from ADB
        o.put("remote", JSONObject().put("connected", awake != null).put("is_on", awake == "Awake").put("app", o.optString("app")))
        return o
    }

    companion object {
        const val CAST_DIR = "/sdcard/Download"
        private val PKG = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
        private val LS = Pattern.compile("^([dl-])\\S+\\s+\\d+\\s+\\S+\\s+\\S+\\s+(\\d+)\\s+(\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d)\\s+(.+)\$")
        private val DIGITS = Regex("\\d+")
        private const val TMP_APK = "/data/local/tmp/tvremote-install.apk"
        private const val STATS = "/sdcard/Android/data/dev.tvremote.setup/files/stats"

        /** The separator is printed from octal codes, so it does not appear in this command's own line in the `top` output. */
        private const val HEALTH = "getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk; " +
                "getprop ro.build.version.security_patch; getprop ro.board.platform; getprop ro.product.cpu.abi; cat /sys/devices/system/cpu/present; " +
                "printf '\\043~\\043\\n'; head -1 /proc/stat; sleep 0.6; head -1 /proc/stat; printf '\\043~\\043\\n'; grep -E '^(MemTotal|MemAvailable):' /proc/meminfo; printf '\\043~\\043\\n'; df -k /data | tail -1; " +
                "printf '\\043~\\043\\n'; cat /proc/uptime; printf '\\043~\\043\\n'; wm size; wm density; dumpsys display | grep -m1 -o 'fps=[0-9.]*'; printf '\\043~\\043\\n'; dumpsys wifi | grep -m1 mWifiInfo; " +
                "printf '\\043~\\043\\n'; ip -o -4 addr show; printf '\\043~\\043\\n'; top -n 1 -b -m 7 -o %CPU,%MEM,ARGS -s 1 | tail -7; " +
                "printf '\\043~\\043\\n'; dumpsys power | grep -m1 -o 'mWakefulness=[A-Za-z]*'; dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity'"

        /** Java's String.split: trailing empty strings are dropped unless a limit is given. The parsing above counts on it. */
        private fun String.jsplit(regex: String, limit: Int = 0): Array<String> = Pattern.compile(regex).split(this, limit)

        /** Only the shared storage and USB drives; no "..". */
        internal fun safePath(p: String?): String {
            val b = StringBuilder()
            for (part in (if (p.isNullOrEmpty()) "/sdcard/Download" else p).jsplit("/")) {
                if (part.isEmpty() || part == ".") continue
                if (part == "..") throw IllegalArgumentException("bad path")
                b.append('/').append(part)
            }
            val s = b.toString()
            if (!(s == "/sdcard" || s == "/storage" || s.startsWith("/sdcard/") || s.startsWith("/storage/"))) throw IllegalArgumentException("path must be under /sdcard or /storage")
            return s
        }

        internal fun safeName(n: String?): String {
            val s = (n ?: "").replace(Regex(".*[/\\\\]"), "").replace(Regex("[^\\p{L}\\p{N}_.\\- ()\\[\\]]"), "_").replace(Regex("^[. ]+|[. ]+\$"), "")
            return if (s.isEmpty()) "file" else if (s.length > 120) s.substring(0, 120) else s
        }

        private fun root(path: String): Boolean = path == "/sdcard" || path == "/storage" || path.startsWith("/storage/") && path.jsplit("/").size < 4

        internal fun mime(path: String): String = when (path.substring(path.lastIndexOf('.') + 1).lowercase(Locale.ROOT)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"; "gif" -> "image/gif"; "webp" -> "image/webp"
            "mp4", "m4v" -> "video/mp4"; "mkv" -> "video/x-matroska"; "webm" -> "video/webm"; "avi" -> "video/x-msvideo"; "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"; "m4a" -> "audio/mp4"; "flac" -> "audio/flac"; "wav" -> "audio/wav"; "ogg" -> "audio/ogg"
            "apk" -> "application/vnd.android.package-archive"; "pdf" -> "application/pdf"; "txt" -> "text/plain"
            else -> "*/*"
        }

        private fun find(regex: String, input: String): String? { val m = Pattern.compile(regex).matcher(input); return if (m.find()) m.group(1) else null }

        private fun num(s: String?): Any = s?.toLong() ?: JSONObject.NULL
    }
}
