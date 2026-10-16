package dev.tvremote.core

import dev.tvremote.update.Release
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import java.util.regex.Pattern

/**
 * Managing a TV over ADB for the page both apps serve: installed apps, files, APK installs and a health snapshot.
 * The paths and the answers are the ones the computer's server gives (app/desktop Server.kt), because all three serve the same page
 * (app/shared/web). Whoever owns the HttpServer checks first that the caller may use it.
 */
class Manage(private val link: AdbLink, private val labels: Labels?, private val dir: java.io.File?, private val helper: (() -> ByteArray)? = null) {
    /** Names and icons for packages, where the app can ask the system for them (the TV app); otherwise package names are shown. */
    interface Labels {
        fun label(pkg: String): String?
        fun icons(): Boolean
        /** What the TV's hardware can do, as [Caps] prints it; null where it cannot be asked. */
        fun caps(): String? = null
    }

    /** Told when a device was let in through the TV's question (TV Setup shows it on the TV). */
    var allowed: ((String) -> Unit)? = null
    /** Pairing through Wireless debugging needs TLS 1.3 from a library the TV app does not carry: only the Phone and Computer apps turn this on. */
    var canPair = false
    private val promptLock = Any()

    private val playing = helper?.let { NowPlaying({ link.get() }, it) }      // the now playing bar: null where tvr-helper.jar is not at hand
    private val draw = Draw { link.get() }                           // strokes for TV Setup to draw over the screen
    private val jobs: MutableMap<String, JSONObject> = Collections.synchronizedMap(LinkedHashMap<String, JSONObject>())
    private val nextJob = java.util.concurrent.atomic.AtomicInteger(1)

    /** The Stop of every transfer or install under way, by the id the page knows it by (a job's id, or the upload's `uid`). */
    private val stops = java.util.concurrent.ConcurrentHashMap<String, Cancel>()
    /** The Stop of the work the current thread is doing (set by [job], by an upload request, by [install] / [cast]). */
    private val current = ThreadLocal<Cancel?>()

    private val working = java.util.concurrent.ConcurrentHashMap<Any, String>()
    /** Told when work starts or ends (the phone keeps itself running meanwhile, and asks before leaving the TV). */
    @Volatile var onWork: ((String) -> Unit)? = null

    /** What is under way, as upload / install / update / tailscale / work; "" when nothing. */
    fun working(): String = working.values.firstOrNull() ?: ""

    /** How far the newest running job is, 0..100, or -1 when none tells (the phone's notification bar). */
    fun progress(): Int = synchronized(jobs) { jobs.values.lastOrNull { it.optString("status") == "running" }?.optInt("pct", -1) ?: -1 }

    private inline fun <T> at(what: String, f: () -> T): T {
        val k = Any()
        working[k] = what
        onWork?.invoke(working())
        try { return f() } finally { working.remove(k); onWork?.invoke(working()) }
    }

    /** null when the path is not ours. */
    @Throws(Exception::class)
    fun handle(r: HttpServer.Request): HttpServer.Response? {
        if (!r.path.startsWith("/api/")) return null
        val what = r.path.substring(5)
        val post = r.method == "POST"
        try {
            when (what) {
                "apps" -> return HttpServer.Response.json(apps(r.q("system") == "1"))
                "apps/report" -> return HttpServer.Response.json(appsReport())
                "files" -> return HttpServer.Response.json(files(r.q("path")))
                "file" -> return download(r)
                "health" -> return HttpServer.Response.json(health())
                "procs" -> return HttpServer.Response.json(procs())
                "crashes" -> return HttpServer.Response.json(JSONObject().put("text", sh(CRASHES).replace("\r\n", "\n").takeLast(500000)))
                "stats" -> return HttpServer.Response.json(stats(r.q("days").toIntOrNull() ?: 7))
                "rules" -> if (!post) return HttpServer.Response.json(rules(null))
                "tvset" -> if (!post) return text(TvShell.TVSET_READ, 60000)
                "log" -> return text(TvShell.log(r.q("pkg"), r.q("since")), 20000)
                "wirelog" -> return HttpServer.Response.of("text/plain; charset=utf-8", WireLog.all.report().toByteArray())   // ours, not the TV's: "Gửi nhật ký"
                "apps/boot" -> return text(TvShell.BOOT, 20000)
                "app/info" -> return text(TvShell.appInfo(r.q("pkg")), 20000)
                "settings" -> if (!post) return text(TvShell.SETTINGS_READ, 20000)
                "inputs" -> if (!post) return HttpServer.Response.json(Inputs.json(inputs(r.q("fresh") == "1")))   // HDMI 1 · PlayStation...
                "caps" -> return HttpServer.Response.json(JSONObject().put("text", labels?.caps() ?: ""))
                "playing" -> if (!post) return HttpServer.Response.json(playing?.state() ?: JSONObject().put("state", "none"))
                "app/backup" -> return backup(r.q("pkg"))
                "tailscale" -> { val t = Tailscale(link.get(), dir); return HttpServer.Response.json(JSONObject().put("installed", t.installed()).put("ip", t.ip())) }
                "setup/version" -> return HttpServer.Response.json(setupVersion())
                "setup/need" -> { SetupNeed.check(link.get(), r.q("f")); return HttpServer.Response.json(JSONObject().put("ok", true)) }
                "setup/plan" -> return HttpServer.Response.json(Setup(link.get(), dir, true) { }.plan { labels?.label(it) })
                "adb/prompt" -> {                                     // the TV's "Allow USB debugging?" question, if it is up
                    val p = if (link.state() == "ready") AdbPrompt.read(link.get()) else null
                    return HttpServer.Response.json(JSONObject().put("fingerprint", p?.fingerprint ?: ""))
                }
                "adb/discover" -> if (canPair) return discoverPairing()     // the phone and computer only: the TV web page has its TV on localhost
                "space" -> return HttpServer.Response.json(space(r.q("path"), r.q("size").toLongOrNull() ?: 0L, r.q("apk") == "1"))
                "job" -> {
                    val j = jobs[r.q("id")]
                    return if (j == null) HttpServer.Response.error(404, "no such job") else HttpServer.Response.json(JSONObject(j.toString()))
                }
            }
            if (!post) return null
            return when (what) {
                "app" -> { open(r.json().optString("link")); HttpServer.Response.json(JSONObject().put("ok", true).put("via", "adb")) }
                "app/launch", "app/stop", "app/uninstall", "app/disable", "app/enable", "app/clear" -> app(r.json().optString("pkg"), what.substring(4))
                "clean" -> text(TvShell.CLEAN, 90000)
                "app/perm" -> {
                    val d = r.json(); val pkg = d.optString("pkg")
                    if (Setup.PROTECTED.matches(pkg)) throw IllegalArgumentException("protected: the TV needs this app")
                    text(TvShell.perm(pkg, d.optString("perm"), d.optBoolean("on")), 20000)
                }
                "tvset" -> { val d = r.json(); text(TvShell.tvset(d.optString("key"), d.optString("value")), 60000) }
                "settings" -> { val d = r.json(); text(TvShell.setting(d.optString("table"), d.optString("name"), d.optString("value")), 20000) }
                "reboot" -> {
                    sh(TvShell.REBOOT_PREP)
                    try { link.get().open("reboot:").use { it.readAll(3000) } } catch (ignored: Exception) { }   // the link drops as the TV goes down
                    link.close()                                      // so the page's next state() dials again instead of trusting it
                    HttpServer.Response.ok()
                }
                "input" -> {
                    val id = r.json().optString("id")
                    val via = Inputs.switch({ sh(it) }, id, inputCache.peek())
                    HttpServer.Response.json(JSONObject().put("ok", true).put("via", via))
                }
                "adb/pair" -> { if (canPair && post) return pairing(r.json()); HttpServer.Response.error(404, "not here") }
                "adb/connect" -> HttpServer.Response.json(JSONObject().put("state", link.reconnect()))
                "shell" -> shell(r.json().optString("cmd"))
                "rules" -> HttpServer.Response.json(rules(r.json()))
                "notify" -> { Notify.send({ sh(it) }, r.json()); HttpServer.Response.json(JSONObject().put("ok", true)) }
                "share" -> HttpServer.Response.json(Invite.share({ sh(it) }, link.host, r.json().optString("scope")))   // a QR another phone scans (Invite.kt)
                "search" -> {
                    val to = sh(TvShell.search(r.json().optString("query"))).trim()
                    if (to != "yt" && to != "gs") throw IllegalArgumentException("this TV has no YouTube and no Google search")
                    HttpServer.Response.json(JSONObject().put("to", if (to == "yt") "youtube" else "assistant"))
                }
                "upload" -> HttpServer.Response.json(upload(r))
                "cancel" -> { stops[r.json().optString("id")]?.stop(); HttpServer.Response.json(JSONObject().put("ok", true)) }
                "file/delete", "file/rename", "file/mkdir", "file/open" -> fileOp(what.substring(5), r.json())
                "file/install" -> HttpServer.Response.json(installPath(safePath(r.json().optString("path"))))
                "free" -> HttpServer.Response.json(free())
                "procs/kill" -> { val pkg = r.json().optString("pkg"); keepCheck(pkg); sh("am force-stop " + Adb.q(pkg)); HttpServer.Response.ok() }
                "setup" -> { val d = r.json(); HttpServer.Response.json(setup(d.optString("mode") == "undo", d.optString("lang") != "en", choice(d))) }
                "fetch" -> HttpServer.Response.json(fetch(r.json().optString("url"), false))
                "install" -> HttpServer.Response.json(fetch(r.json().optString("url"), true))
                "setup/update" -> HttpServer.Response.json(setupUpdate())
                "playing" -> { val d = r.json(); (playing ?: throw IllegalStateException("not here")).command(d.optString("do"), d.optLong("ms")); HttpServer.Response.json(JSONObject().put("ok", true)) }
                "screen/off" -> { ScreenOff.off(link.get(), (helper ?: throw IllegalStateException("not here"))()); HttpServer.Response.json(JSONObject().put("ok", true)) }
                "draw" -> { draw.send(r.json(Draw.MAX_BODY).getJSONArray("ops")); HttpServer.Response.json(JSONObject().put("ok", true).put("pic", draw.pics)) }
                "adb/allow" -> {                                      // only for the key named: see AdbPrompt
                    val d = r.json()
                    val fp = d.optString("fingerprint").uppercase()
                    if (!fp.matches(Regex("(?:[0-9A-F]{2}:){15}[0-9A-F]{2}"))) throw IllegalArgumentException("bad fingerprint")
                    val result = synchronized(promptLock) { AdbPrompt.allow(link.get(), fp, d.optLong("wait", 0).coerceIn(0, 40) * 1000, d.optBoolean("others")) }
                    if (result == "allowed") allowed?.invoke(fp)
                    HttpServer.Response.json(JSONObject().put("result", result))
                }
                "adb/deny" -> HttpServer.Response.json(JSONObject().put("ok", synchronized(promptLock) { AdbPrompt.deny(link.get(), r.json().optString("fingerprint")) }))
                "tailscale/start" -> HttpServer.Response.json(job("tailscale") { j ->
                    val t = Tailscale(link.get(), dir)
                    val url = t.start { what, pct -> j.put("msg", what).put("pct", pct) }
                    if (url.isEmpty()) { j.put("ip", t.ip()); "in" } else { j.put("url", url); "sign in" }
                })
                else -> null
            }
        } catch (e: Cancelled) {                                      // before IOException: stopping is not a broken link
            return HttpServer.Response.error(410, "stopped")
        } catch (e: IllegalArgumentException) {
            return HttpServer.Response.error(400, e.message.toString())
        } catch (e: SetupNeed.Missing) {                              // the page asks to update or install TV Setup (needSetup in js/core.js)
            return HttpServer.Response.error(409, e.message.toString())
        } catch (e: IOException) {
            return HttpServer.Response.error(503, if (link.waitingForUser()) "adb:allow" else "adb: " + e.message)
        }
    }

    private val inputCache = Inputs.Cache()

    /** The TV's inputs, kept a minute (reading two dumpsys takes a second or two on a slow TV). */
    private fun inputs(fresh: Boolean): List<Inputs.Input> = inputCache.get(fresh) { Inputs.parse(sh(Inputs.READ)) }
    /** The pairing and connect ports heard on the network (mDNS), for the page to prefill; empty lists when nothing answers. */
    private fun discoverPairing(): HttpServer.Response {
        fun list(l: List<Mdns.Service>) = org.json.JSONArray().also { a -> l.forEach { a.put(JSONObject().put("ip", it.ip).put("port", it.port)) } }
        val (pairing, connect) = AdbTls.discover()
        return HttpServer.Response.json(JSONObject().put("host", link.host).put("pairing", list(pairing)).put("connect", list(connect)))
    }

    /** Wireless debugging: pairs with the 6-digit code, then connects through the TLS port (found by mDNS unless the page sent it). */
    private fun pairing(d: JSONObject): HttpServer.Response {
        val host = d.optString("host").trim().ifEmpty { link.host }
        var cp = d.optInt("connectPort", 0)
        if (d.optString("code").isNotBlank() || cp <= 0) {            // no code, only a connect port: pairing was done, the port was missing
            val port = d.optInt("port", 0)
            val code = AdbTls.cleanCode(d.optString("code")) ?: throw IllegalArgumentException("the pairing code is 6 digits")
            if (port !in 1..65535) throw IllegalArgumentException("the pairing port is a number")
            AdbTls.pair(Adb(link.keyDir), host, port, code)
        }
        var tries = 0
        while (cp <= 0 && tries++ < 3) cp = AdbTls.discoverConnect(host)
        val out = JSONObject().put("ok", true).put("paired", true)
        if (cp <= 0) return HttpServer.Response.json(out.put("needPort", true))   // paired, but the connect port is still to be typed
        AdbTls.save(link.keyDir, host, cp)
        return HttpServer.Response.json(out.put("port", cp).put("state", link.reconnect()))
    }

    /** ready, unauthorized (the TV is asking) or closed: the words the page knows. */
    fun adbState(): String = link.state()

    @Throws(IOException::class)
    private fun sh(cmd: String): String = link.get().shell(cmd)

    // ---------------------------------------------------------------- apps
    /**
     * Everything an AI (or a person) needs to say which apps can go: the TV, its memory, the apps the user installed, the system
     * apps still on, the ones already off, and what is using memory right now. The page turns it into a prompt to copy.
     */
    private fun appsReport(): JSONObject {
        // one shell round trip: the parts come back separated by "#~#" lines (meminfo alone takes seconds on a slow TV)
        val p = sh("getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; grep MemTotal /proc/meminfo; " +
            "printf '\\043~\\043\\n'; pm list packages -3 -e; printf '\\043~\\043\\n'; pm list packages -s -e; printf '\\043~\\043\\n'; pm list packages -d; " +
            "printf '\\043~\\043\\n'; dumpsys meminfo").split("#~#\n")
        val part = { i: Int -> p.getOrElse(i) { "" } }
        val list = { i: Int -> part(i).split("\n").filter { it.startsWith("package:") }.map { it.substring(8).trim() }.sorted() }
        val head = part(0).split("\n")
        val user = list(1)
        val o = JSONObject().put("device", (head.getOrElse(0) { "" }.trim() + " " + head.getOrElse(1) { "" }.trim()).trim())
            .put("android", head.getOrElse(2) { "" }.trim())
            .put("ram_mb", (Regex("MemTotal:\\s+(\\d+)").find(part(0))?.groupValues?.get(1)?.toLongOrNull() ?: 0L) / 1024)
            .put("user", JSONArray(user)).put("system", JSONArray(list(2))).put("disabled", JSONArray(list(3)))
            .put("memory", JSONArray(memoryByApp(part(4)).map { JSONObject().put("pkg", it.first).put("mb", it.second) }))
        val names = JSONObject()
        for (p in user) labels?.label(p)?.let { names.put(p, it) }
        return o.put("labels", names)
    }

    private fun apps(system: Boolean): JSONObject {
        val pkgs = ArrayList<String>()
        // the apps turned off are always listed, so "Turn on" is at hand without showing every system app
        val listing = sh("pm list packages" + (if (system) "" else " -3") + "; echo '#~#'; pm list packages -d").split("#~#")
        val off = (listing.getOrElse(1) { "" }).jsplit("\n").filter { it.startsWith("package:") }.map { it.substring(8).trim() }.toSet()
        for (line in listing[0].jsplit("\n") + (if (system) emptyList() else off.map { "package:$it" }))
            if (line.startsWith("package:") && line.substring(8).trim() !in pkgs) pkgs.add(line.substring(8).trim())
        pkgs.sort()
        val out = JSONArray()
        val icons = labels != null && labels.icons()
        for (p in pkgs) {
            val label = labels?.label(p)
            out.put(JSONObject().put("pkg", p).put("off", p in off).put("keep", Setup.PROTECTED.matches(p))
                .put("meta", JSONObject().put("label", label ?: p.substring(p.lastIndexOf('.') + 1)).put("icon", icons).put("banner", icons)))
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
            "launch" -> { Scenes.launch({ sh(it) }, pkg); return HttpServer.Response.ok() }
            "stop" -> { keepCheck(pkg); sh("am force-stop $pkg"); return HttpServer.Response.ok() }
            "uninstall" -> {
                val out = sh("pm uninstall $pkg 2>&1").trim()
                if (!out.contains("Success")) throw IllegalArgumentException(if (out.isEmpty()) "uninstall failed" else out)
                return HttpServer.Response.ok()
            }
            // off and on again, for the system apps nobody uses (Undo-able, unlike uninstalling); never what the TV or this project needs
            "disable" -> {
                if (Setup.PROTECTED.matches(pkg)) throw IllegalArgumentException("protected: the TV needs this app")
                val out = sh("pm disable-user --user 0 $pkg 2>&1")
                if (!out.contains("disabled")) throw IllegalArgumentException(out.trim().ifEmpty { "disable failed" })
                return HttpServer.Response.ok()
            }
            "clear" -> {                                              // back to as just installed: logins, settings, downloads all go
                if (Setup.PROTECTED.matches(pkg)) throw IllegalArgumentException("protected: the TV needs this app")
                val out = sh("pm clear $pkg 2>&1")
                if (!out.contains("Success")) throw IllegalArgumentException(out.trim().ifEmpty { "clear failed" })
                return HttpServer.Response.ok()
            }
            "enable" -> {
                val out = sh("pm enable $pkg 2>&1")
                if (!out.contains("enabled")) throw IllegalArgumentException(out.trim().ifEmpty { "enable failed" })
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
    fun cast(input: InputStream, length: Long, name: String, job: JSONObject? = null, cancel: Cancel? = null) {
        val path = CAST_DIR + "/" + safeName(name)
        sh("mkdir -p " + Adb.q(CAST_DIR))
        current.set(cancel)
        try { push(input, length, path, job) } finally { current.remove() }
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
                val args = "-a android.intent.action.VIEW -d " + Adb.q("file://" + real.replace(" ", "%20")) + " -t " + Adb.q(mime(path))
                // Google TV answers files it cannot show with a do-nothing stub (frameworkpackagestubs): that is "no app" too
                out = if (sh("cmd package resolve-activity --brief $args 2>&1").contains("frameworkpackagestubs")) "Error" else sh("am start $args 2>&1")
                out = if (out.contains("Error") || out.lowercase(Locale.ROOT).contains("unable to resolve")) "the TV has no app that can open this" else ""
            }
        }
        if (out.trim().isNotEmpty()) throw IllegalArgumentException(out.trim())
        return HttpServer.Response.ok()
    }

    /** What a [TvShell] recipe printed, for the page to read. */
    private fun text(cmd: String, quietMs: Long): HttpServer.Response =
        HttpServer.Response.json(JSONObject().put("text", link.get().shell(cmd, quietMs).replace("\r\n", "\n")))

    /** 💾 one app as a .tar, made on the TV and streamed straight through (TvShell.backup says what is in it). */
    private fun backup(pkg: String): HttpServer.Response {
        TvShell.backup(pkg)                                           // checks the name before it reaches a header
        val adb = link.get()
        val out = HttpServer.Response.stream("application/x-tar", -1) { o -> pipe(adb.exec(TvShell.backup(pkg)), o) }
        out.headers["Content-Disposition"] = "attachment; filename*=UTF-8''$pkg-backup.tar"   // the phone's WebView reads this form
        return out
    }

    private fun download(r: HttpServer.Request): HttpServer.Response {
        val path = safePath(r.q("path"))
        val adb = link.get()
        val size = adb.shell("stat -c %s " + Adb.q(path) + " 2>/dev/null").trim()
        if (!size.matches(DIGITS)) throw IllegalArgumentException("cannot read this file")
        val inline = r.q("inline") == "1"
        val out = HttpServer.Response.stream(if (inline) mime(path).replace("*/*", "application/octet-stream") else "application/octet-stream", size.toLong()) { o ->
            pipe(adb.exec("cat " + Adb.q(path)), o)
        }
        if (!inline) out.headers["Content-Disposition"] = "attachment; filename*=UTF-8''" + URLEncoder.encode(path.substring(path.lastIndexOf('/') + 1), "UTF-8").replace("+", "%20")
        return out
    }

    /** A command's output to the browser. Opened only once the reply has started, so a browser that left never leaves it open. */
    private fun pipe(s: Adb.Stream, o: java.io.OutputStream) {
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

    /**
     * Copies `length` bytes into a file on the TV. Refused before a byte goes when the TV has no room for it ([ensureRoom]; `factor`
     * 2 for an APK, which is copied and then installed); stopped on the person's Stop, and then, or when the copy breaks, the
     * part that arrived is deleted, so no half file stays on the TV.
     */
    @Throws(IOException::class)
    private fun push(input: InputStream, length: Long, dest: String, job: JSONObject?, factor: Int = 1) { at("upload") {
        val adb = link.get()
        val cancel = current.get()
        if (length > 0) ensureRoom(dest.substringBeforeLast('/', "/").ifEmpty { "/" }, length, factor)
        try {
            val feed = { s: Adb.Stream ->
                cancel?.onStop { s.close() }                              // a write waiting on a slow TV lets go at once
                val b = ByteArray(65536)
                var done = 0L
                while (length < 0 || done < length) {
                    cancel?.check()
                    val n = input.read(b, 0, if (length < 0) b.size else minOf(b.size.toLong(), length - done).toInt())
                    if (n < 0) break
                    s.write(b, 0, n)
                    done += n
                    if (job != null && length > 0) try { job.put("pct", (done * 90 / length).toInt()) } catch (ignored: Exception) { }
                }
                if (length >= 0 && done < length) throw IOException("upload cut short")
            }
            if (length >= 0) adb.put(dest, length, feed)                  // the size known: the stream stays open until it is all there
            else adb.writeTo(dest).use(feed)
            val want = if (length >= 0) length.toString() else null       // cat finishes writing shortly after the stream closes
            for (i in 0 until 40) {
                cancel?.check()
                val got = adb.shell("stat -c %s " + Adb.q(dest) + " 2>/dev/null").trim()
                if (if (want == null) got.matches(DIGITS) && i > 1 else got == want) return
                try { Thread.sleep(150) } catch (e: InterruptedException) { break }
            }
            if (want != null) throw IOException("the TV did not store the whole file (is its storage full?)")
        } catch (e: Exception) {                                          // stopped, cut, or the TV full: nothing half written is left
            try { Thread.sleep(200); adb.shell("rm -f " + Adb.q(dest)) } catch (ignored: Exception) { }
            if (cancel?.stopped == true && e !is Cancelled) throw Cancelled()   // the Stop broke the stream first: still a stop, not an error
            throw e
        }
    } }

    /** Bytes free on the storage holding `dir`, or null when the TV did not say. */
    private fun freeOn(dir: String): Long? = try { parseDfFree(link.get().shell("df -k " + Adb.q(dir) + " 2>/dev/null | tail -2")) } catch (e: IOException) { null }

    /** Throws [NoRoom] when `size` bytes (times `factor`, plus a reserve) do not fit where `dir` is. An unknown amount lets it pass. */
    private fun ensureRoom(dir: String, size: Long, factor: Int) {
        val free = freeOn(dir) ?: return
        if (!roomFor(size, factor, free)) throw NoRoom(size, free, needed(size, factor), factor > 1)
    }

    /** The page asks before it sends: {ok, free, need}; `error` is the message [NoRoom] carries when not ok. */
    private fun space(path: String, size: Long, apk: Boolean): JSONObject {
        val dir = if (apk) TMP_DIR else safePath(path)
        val factor = if (apk) 2 else 1
        val free = freeOn(dir)
        val o = JSONObject().put("need", needed(size, factor))
        if (free == null) return o.put("ok", true)
        o.put("free", free).put("ok", roomFor(size, factor, free))
        return if (o.getBoolean("ok")) o else o.put("error", NoRoom(size, free, needed(size, factor), apk).message)
    }

    /**
     * A file from the browser, into a folder on the TV or (kind=install) straight into the installer. The file arrives in the
     * request body and has to be read before we answer, so the answer is a job that is already finished.
     */
    private fun upload(r: HttpServer.Request): JSONObject {
        val name = safeName(r.q("name"))
        val done = { title: String, msg: String -> JSONObject().put("id", "u" + nextJob.getAndIncrement()).put("title", title).put("status", "done").put("pct", 100).put("msg", msg) }
        val uid = r.q("uid").take(64)                                 // the page's name for this upload: POST /api/cancel {id} stops it
        val cancel = Cancel()
        if (uid.isNotEmpty()) stops[uid] = cancel
        current.set(cancel)
        try {
            if (r.q("kind") == "install") { installUpload(r); return done("install $name", "installed") }
            if (r.q("kind") == "restore") { restore(r); return done("restore $name", "restored") }
            val dest = safePath(r.q("dest")) + "/" + name
            push(r.body, r.length, dest, null)
            return done("upload $name", "saved $dest")
        } finally {
            current.remove()
            if (uid.isNotEmpty()) stops.remove(uid)
        }
    }

    // ---------------------------------------------------------------- installs and downloads (jobs the page polls)
    private fun interface Work { fun run(job: JSONObject): String }

    private fun job(title: String, work: Work): JSONObject {
        val j = JSONObject().put("id", nextJob.getAndIncrement().toString()).put("title", title).put("status", "running").put("pct", 0).put("msg", "")
        jobs[j.getString("id")] = j
        while (jobs.size > 20) jobs.remove(jobs.keys.iterator().next())
        val what = title.substringBefore(' ').takeIf { it in setOf("install", "update", "tailscale") } ?: "work"
        val cancel = Cancel()
        stops[j.getString("id")] = cancel
        Thread({
            current.set(cancel)
            try {
                val msg = at(what) { work.run(j) }
                j.put("status", "done").put("pct", 100).put("msg", msg)
            } catch (e: Exception) {
                // the person's Stop is not an error, whatever the stopped work threw on its way out
                try { j.put("status", if (cancel.stopped) "cancelled" else "error").put("msg", if (cancel.stopped) "stopped" else e.message ?: e.javaClass.simpleName) } catch (ignored: Exception) { }
            } finally {
                current.remove()
                stops.remove(j.getString("id"))
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
        val info = try { apkInfo(B64.decode(adb.shell("unzip -p $q AndroidManifest.xml 2>/dev/null | base64"))) }
                   catch (e: IllegalArgumentException) { emptyMap() }
        val pkg = info["package"]
        // said before installing, in words a person can act on, rather than pm's INSTALL_FAILED_... after it
        val tv = adb.shell("getprop ro.build.version.sdk; getprop ro.product.cpu.abilist").split("\n") + listOf("", "")
        val abis = apkAbis(adb.shell("unzip -l $q 2>/dev/null | grep -o 'lib/[^ ]*\\.so' || true").split(Regex("\\s+")))
        apkFits(info, abis, tv[0].trim().toIntOrNull() ?: 0, tv[1].trim().split(",").filter { it.isNotEmpty() })?.let { throw IOException(it) }
        val cancel = current.get()
        cancel?.check()
        val s = adb.exec("cat $q | pm install -r -S $size 2>&1")
        cancel?.onStop { s.close() }                                  // closing the stream ends pm, which drops its install session
        val out = try { s.readAll(600000) } finally { s.close() }
        cancel?.check()
        if (!out.contains("Success")) throw IOException(if (out.trim().isEmpty()) "install failed" else out.trim())
        // some boxes (FPT Play...) uninstall the apps they do not allow the moment they land, so "Success" alone proves nothing
        if (pkg != null) {
            Thread.sleep(3000)
            if (!adb.shell("pm path $pkg 2>/dev/null; echo .").contains("package:")) throw IOException("removed by the TV right after install: $pkg")
        }
        return "installed"
    }

    /** An APK from this device, checked and installed as an upload is: the phone's "several TVs at once" (pct 0..90 while it copies). */
    @Throws(IOException::class)
    fun install(input: InputStream, length: Long, job: JSONObject?, cancel: Cancel? = null): String {
        current.set(cancel)
        try {
            push(input, length, TMP_APK, job, 2)
            try { return pmInstall(TMP_APK) } finally { try { sh("rm -f $TMP_APK") } catch (ignored: IOException) { } }
        } finally { current.remove() }
    }

    private fun installPath(path: String): JSONObject =
        job("install " + path.substring(path.lastIndexOf('/') + 1)) { j ->
            j.put("pct", 50)
            sh("stat -c %s " + Adb.q(path) + " 2>/dev/null").trim().toLongOrNull()?.let { ensureRoom("/data", it, 1) }   // pm installs a copy of it
            pmInstall(path)
        }

    /** The APK arrives in the request body: it has to be read before we answer, so this one is not a background job. */
    private fun installUpload(r: HttpServer.Request) {
        if (r.length <= 0) throw IllegalArgumentException("empty upload")
        push(r.body, r.length, TMP_APK, null, 2)
        try {
            pmInstall(TMP_APK)
        } catch (e: Cancelled) {
            throw e                                                   // a Stop, answered 410 like the copy's
        } catch (e: IOException) {
            throw IllegalArgumentException(e.message)
        } finally {
            sh("rm -f $TMP_APK")
        }
    }

    /** 📥 a backup made by 💾, sent to the TV whole, then installed and put back there. Not a background job, as with installUpload. */
    private fun restore(r: HttpServer.Request) {
        if (r.length <= 0) throw IllegalArgumentException("empty upload")
        val tar = "/data/local/tmp/tvr-rs-%08x.tar".format(java.util.Random().nextInt())
        push(r.body, r.length, tar, null, 2)
        val adb = link.get()
        val out = try { adb.shell(TvShell.restore(tar), 600000) } finally { adb.shell("rm -f $tar") }
        if (!out.contains("Success")) throw IllegalArgumentException(out.trim().takeLast(300).ifEmpty { "restore failed" })
        val pkg = out.trim().substringAfterLast('\n').removePrefix("package").trim()   // the last line names it
        if (PKG.matcher(pkg).matches()) {                                       // as pmInstall: some boxes remove what they do not allow
            Thread.sleep(3000)
            if (!adb.shell("pm path $pkg 2>/dev/null; echo .").contains("package:")) throw IllegalArgumentException("removed by the TV right after install: $pkg")
        }
    }

    /**
     * The task manager (Health tab): the apps holding memory now, what each is doing ([Proc.state]) and whether it may be
     * stopped. One shell round trip; meminfo takes a second or more on a slow TV, so the page asks only while it is open.
     */
    private fun procs(): JSONObject {
        val p = sh("grep -E '^(MemTotal|MemAvailable):' /proc/meminfo; printf '\\043~\\043\\n'; dumpsys meminfo; printf '\\043~\\043\\n'; " + KEEP_NOW).split("#~#\n")
        val kb = { k: String -> (Meminfo.kb(p[0], k) ?: 0L) * 1024 }
        val list = JSONArray()
        for (a in procsOf(p.getOrElse(1) { "" }, keepNow(p.getOrElse(2) { "" }))) list.put(JSONObject().put("pkg", a.pkg)
            .put("label", labels?.label(a.pkg) ?: a.pkg).put("mb", a.mb).put("state", a.state).put("keep", a.keep ?: JSONObject.NULL))
        return JSONObject().put("apps", list).put("mem_total", kb("MemTotal")).put("mem_free", kb("MemAvailable"))
    }

    /** Stopping [pkg] would hurt the TV (its home screen, keyboard, a part of Android) or cut this remote off: refused. */
    private fun keepCheck(pkg: String) {
        if (!PKG.matcher(pkg).matches()) throw IllegalArgumentException("bad package name")
        val keep = keepNow(sh(KEEP_NOW))
        if (keepWhy(pkg, keep) != null) throw IllegalArgumentException("protected: the TV needs this app")
    }

    /** Closes the apps that are only waiting in the background (what is on screen stays); says how much memory came back, in MB. */
    private fun free(): JSONObject {
        val avail = { (Meminfo.kb(sh("cat /proc/meminfo"), "MemAvailable") ?: 0L) / 1024 }
        val before = avail()
        sh("am kill-all")
        Thread.sleep(1500)
        val after = avail()
        return JSONObject().put("before", before).put("after", after).put("freed", maxOf(0L, after - before))
    }

    /** The person's choices from the Optimize dialog; a page that sends none gets everything, as before. */
    private fun choice(d: JSONObject): Setup.Choice {
        val list = d.optJSONArray("disable") ?: return Setup.Choice(d.optBoolean("anim", true), d.optBoolean("home", true), null)
        return Setup.Choice(d.optBoolean("anim", true), d.optBoolean("home", true), (0 until list.length()).map { list.optString(it) })
    }

    /** "Optimize TV" or its undo; the job's message grows a line per step. */
    private fun setup(undo: Boolean, vi: Boolean, c: Setup.Choice): JSONObject =
        job(if (undo) "undo" else "optimize") { j ->
            val lines = StringBuilder()
            val s = Setup(link.get(), dir, vi) { line -> lines.append(line).append('\n'); try { j.put("log", lines.toString().trim()) } catch (ignored: Exception) { } }
            if (undo) s.undo() else s.apply(c)
            "ok"
        }

    /** This device downloads the link and passes it on to the TV, into Download or straight into the installer. */
    private fun fetch(url: String, install: Boolean): JSONObject {
        if (!url.matches(Regex("(?i)https?://\\S+"))) throw IllegalArgumentException("only http/https links")
        return job((if (install) "install " else "download ") + (if (url.length > 60) url.substring(0, 60) else url)) { j ->
            // TV Setup itself, the first time: the release's file, checked against its signed SHA256SUMS like every update
            val setup = if (install && url.endsWith("/releases/latest/download/tv-setup.apk"))
                Release.newer("0", "tv", "tv-setup.apk") ?: throw IOException(Release.UNSIGNED) else null
            val c = URL(setup?.url ?: url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "tv-remote")
            current.get()?.onStop { c.disconnect() }                  // a download waiting on a slow server ends at once
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
                val md = MessageDigest.getInstance("SHA-256")
                DigestInputStream(c.inputStream, md).use { push(it, len, dest, j, if (install) 2 else 1) }
                if (setup != null) try { setup.check(md) } catch (e: IOException) { sh("rm -f $TMP_APK"); throw e }
                if (!install) "saved $dest"
                else try { pmInstall(dest) } finally { sh("rm -f $TMP_APK") }
            } finally {
                c.disconnect()
            }
        }
    }

    // ---------------------------------------------------------------- updates of TV Setup on the TV (SetupUpdate)
    @Volatile private var latest: Release? = null
    @Volatile private var latestAt = 0L
    @Volatile private var latestFor = ""

    /** The newest TV Setup for [installed], asked of GitHub at most every half hour (it allows 60 questions an hour). */
    private fun latestSetup(installed: String): Release? {
        if (installed != latestFor || System.currentTimeMillis() - latestAt > 30 * 60_000L) {
            latest = Release.newer(installed, "tv", "tv-setup.apk")
            latestAt = System.currentTimeMillis(); latestFor = installed
        }
        return latest
    }

    /** {installed, latest (when newer and not known to break here), notes, state...: how the last update went}. */
    private fun setupVersion(): JSONObject {
        val adb = link.get()
        val installed = SetupUpdate.installed(adb)
        val o = SetupUpdate.state(adb).put("installed", installed).put("latest", "")
        if (installed.isEmpty()) return o
        val r = try { latestSetup(installed) } catch (e: Exception) { o.put("error", e.message ?: e.javaClass.simpleName); null }
        if (r != null && !SetupUpdate.bad(o, r.version)) o.put("latest", r.version).put("notes", r.notes)
        return o
    }

    /**
     * Brings the newest TV Setup to the TV and installs it with SetupUpdate's script, then follows it: done when the new version
     * runs, an error when it was put back (the job's "msg" says which). Where TV Setup itself serves this, the job goes with it.
     */
    private fun setupUpdate(): JSONObject {
        val adb = link.get()
        val installed = SetupUpdate.installed(adb)
        if (installed.isEmpty()) throw IllegalArgumentException("TV Setup is not installed")
        latestAt = 0                                                  // ask GitHub again: a release may have come out meanwhile
        val r = latestSetup(installed) ?: throw IllegalArgumentException("TV Setup is up to date")
        if (SetupUpdate.busy(SetupUpdate.state(adb)) || !SetupUpdate.lock(adb)) throw IllegalArgumentException("an update of TV Setup is already running")
        return job("update TV Setup " + r.version) { j ->
            try {                                                     // the APK in place under the lock: nobody else's install reads it half written
                j.put("msg", "download").put("version", r.version)
                val c = URL(r.url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                c.setRequestProperty("User-Agent", "tv-remote")
                try {
                    if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
                    val md = MessageDigest.getInstance("SHA-256")
                    DigestInputStream(c.inputStream, md).use { push(it, if (c.contentLength >= 0) c.contentLength.toLong() else -1L, SetupUpdate.APK, j) }
                    try { r.check(md) } catch (e: IOException) { sh("rm -f " + SetupUpdate.APK); throw e }
                } finally {
                    c.disconnect()
                }
                SetupUpdate.start(link.get(), SetupUpdate.APK, r.version, locked = true)
            } catch (e: Exception) {
                try { SetupUpdate.unlock(link.get()) } catch (ignored: Exception) { }
                throw e
            }
            j.put("msg", "installing").put("pct", 92)
            val t0 = System.currentTimeMillis()
            while (System.currentTimeMillis() - t0 < 5 * 60_000L) {
                Thread.sleep(2000)
                val s = try { SetupUpdate.state(link.get()) } catch (e: IOException) { continue }   // the TV's ADB may blink meanwhile
                if (s.optString("version") != r.version) {
                    if (System.currentTimeMillis() - t0 > 30_000L) throw IOException("the update did not start on the TV")
                    continue
                }
                when (s.optString("state")) {
                    "checking" -> j.put("msg", "checking").put("pct", 96)
                    "ok" -> return@job "updated " + r.version
                    "rolledback" -> throw IOException("setup:rolledback " + r.version + " " + s.optString("from"))
                    "broken" -> throw IOException("setup:broken " + r.version)
                    "failed" -> throw IOException(s.optString("detail").ifEmpty { "install failed" })
                }
            }
            throw IOException("setup:slow")
        }
    }

    // ---------------------------------------------------------------- timers and limits (kept by TV Setup on the TV)
    /** The labels of the limited apps are this device's to give; TV Setup knows only package names when asked over ADB. */
    private fun rules(change: JSONObject?): JSONObject {
        val state = RulesClient.call({ sh(it) }, change)
        val limits = state.optJSONArray("limits")
        if (limits != null && labels != null) for (i in 0 until limits.length()) limits.optJSONObject(i)?.let { l -> if (!l.has("label")) labels.label(l.optString("pkg"))?.let { l.put("label", it) } }
        return state
    }

    // ---------------------------------------------------------------- history
    /** CPU load in percent over one second, or null. */
    fun cpuNow(): Int? = try {
        val f = sh("head -1 /proc/stat; sleep 1; head -1 /proc/stat").trim().jsplit("\n")
        Meminfo.cpu(f[0], f[1])
    } catch (e: Exception) { null }

    /** Lets the TV Setup app on the TV read which app is on screen (it cannot ask for this itself); false when it could not be asked (no ADB yet): the caller tries again later. */
    fun allowUsage(pkg: String): Boolean = try { sh("appops set $pkg GET_USAGE_STATS allow"); true } catch (ignored: Exception) { false }

    /** What the TV Setup app on the TV has recorded (Stats.kt there), for the last [days] days. */
    private fun stats(days: Int): JSONObject {
        val d = days.coerceIn(1, 90)
        val from = System.currentTimeMillis() / 1000 - d * 86400L
        val usage = try { JSONObject(sh("cat $STATS/usage.json 2>/dev/null")) } catch (e: Exception) { null }
        val samples = JSONArray()
        for (line in sh("tail -n " + (d * 300) + " $STATS/samples.jsonl 2>/dev/null").jsplit("\n"))
            try { val a = JSONArray(line); if (a.getLong(0) >= from) samples.put(a) } catch (ignored: Exception) { }
        val net = JSONArray()                                         // the network going away and coming back (TV Setup 2.16+)
        for (line in sh("tail -n 400 $STATS/net.jsonl 2>/dev/null || true").jsplit("\n"))
            try { val a = JSONArray(line); if (a.getLong(0) >= from) net.put(a) } catch (ignored: Exception) { }
        val names = JSONObject()
        if (usage != null && labels != null)
            for (day in usage.keys()) for (pkg in usage.getJSONObject(day).keys()) if (!names.has(pkg)) labels.label(pkg)?.let { names.put(pkg, it) }
        return JSONObject().put("days", d).put("collector", usage != null || samples.length() > 0).put("usage", usage ?: JSONObject()).put("samples", samples).put("net", net).put("labels", names)
    }

    // ---------------------------------------------------------------- health
    // what a health snapshot says that hardly changes (model, screen, Wi-Fi, IPs): read once a minute, not on every 5 s tick
    private var slowAt = 0L
    private var slowFor: Any? = null
    private var slowParts: List<String> = emptyList()

    @Synchronized private fun slowFacts(adb: Adb): List<String> {
        val now = System.currentTimeMillis()
        if (slowFor !== adb || now - slowAt > 60_000 || slowParts.isEmpty()) {
            slowParts = (adb.shell(HEALTH_STATIC) + "#~##~##~#").jsplit("#~#", -1).take(4)
            slowFor = adb; slowAt = now
        }
        return slowParts
    }

    private fun health(): JSONObject {
        val adb = link.get()
        val t0 = System.currentTimeMillis()
        adb.shell(":")
        val ping = System.currentTimeMillis() - t0
        val slow = slowFacts(adb)
        val live = (adb.shell(HEALTH_LIVE) + "#~##~##~##~##~##~#").jsplit("#~#", -1)
        // same numbering as before: the facts that hardly change come from the cache, the rest was just read
        val part = listOf(slow[0], live[0], live[1], live[2], live[3], slow[1], slow[2], slow[3], live[4], live[5])
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
        val memTotal = Meminfo.kb(part[2], "MemTotal")
        val memFree = Meminfo.kb(part[2], "MemAvailable")
        if (memTotal != null) o.put("mem_total", memTotal * 1024)
        if (memFree != null) o.put("mem_free", memFree * 1024)
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
        private val PKG = Pattern.compile(Pkg.PATTERN)
        private val LS = Pattern.compile("^([dl-])\\S+\\s+\\d+\\s+\\S+\\s+\\S+\\s+(\\d+)\\s+(\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d)\\s+(.+)\$")
        private val DIGITS = Regex("\\d+")
        private const val TMP_DIR = "/data/local/tmp"
        private const val TMP_APK = "$TMP_DIR/tvremote-install.apk"
        private const val RESERVE = 16L shl 20                          // never fill the TV to the last byte: it needs room to run

        /** Bytes a copy of `size` needs free: `factor` times it (an APK is copied, then installed) and the reserve. */
        internal fun needed(size: Long, factor: Int): Long = size * factor + RESERVE
        internal fun roomFor(size: Long, factor: Int, free: Long): Boolean = free >= needed(size, factor)

        /** The "Available" column of `df -k`'s last line, in bytes (a long device name may wrap onto the line before). */
        internal fun parseDfFree(out: String): Long? {
            val last = out.trim().lines().lastOrNull { it.isNotBlank() }?.trim()?.split(Regex("\\s+")) ?: return null
            val at = if (last.size >= 6 && last[1].matches(DIGITS)) 3 else if (last.size == 5 && last[0].matches(DIGITS)) 2 else return null
            return last[at].takeIf { it.matches(DIGITS) }?.toLong()?.times(1024)
        }
        private const val STATS = "/sdcard/Android/data/dev.tvremote.setup/files/stats"

        /** The separator is printed from octal codes, so it does not appear in this command's own line in the `top` output. */
        private const val HEALTH_STATIC = "getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk; " +
                "getprop ro.build.version.security_patch; getprop ro.board.platform; getprop ro.product.cpu.abi; cat /sys/devices/system/cpu/present; " +
                "printf '\\043~\\043\\n'; wm size; wm density; dumpsys display | grep -m1 -o 'fps=[0-9.]*'; printf '\\043~\\043\\n'; dumpsys wifi | grep -m1 mWifiInfo; " +
                "printf '\\043~\\043\\n'; ip -o -4 addr show"

        private const val HEALTH_LIVE = "head -1 /proc/stat; sleep 0.6; head -1 /proc/stat; printf '\\043~\\043\\n'; grep -E '^(MemTotal|MemAvailable):' /proc/meminfo; printf '\\043~\\043\\n'; df -k /data | tail -1; " +
                "printf '\\043~\\043\\n'; cat /proc/uptime; printf '\\043~\\043\\n'; top -n 1 -b -m 7 -o %CPU,%MEM,ARGS -s 1 | tail -7; " +
                "printf '\\043~\\043\\n'; dumpsys power | grep -m1 -o 'mWakefulness=[A-Za-z]*'; dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity'"

        /** Health → Errors: the dropbox's notes on apps that stopped, froze or crashed natively, cut on the TV to the lines the page
         *  shows (ANR thread dumps are hundreds of KB). */
        private const val CRASHES = "for t in data_app_crash system_app_crash system_server_crash data_app_anr system_app_anr system_server_anr " +
                "data_app_native_crash system_app_native_crash; do dumpsys dropbox --print \$t; done | grep -E '^(=====|[0-9]{4}-[0-9]{2}-[0-9]{2} " +
                "[0-9:]{8} |Process: |Subject: |signal [0-9]|Abort message|Caused by: |[A-Za-z_\$][A-Za-z0-9_.\$]*(Exception|Error)(: |\$)|\tat )'"

        // android:minSdkVersion, versionCode, versionName: what an attribute is when a shrunk manifest drops its name string
        private val ATTR_IDS = mapOf(0x0101020c to "minSdkVersion", 0x0101021b to "versionCode", 0x0101021c to "versionName")

        /**
         * From an APK's binary AndroidManifest.xml: "package", "versionName", "versionCode", "minSdkVersion" (what was found).
         * Reads the <manifest> element and the <uses-sdk> after it. Empty if unreadable. adbtools.apk_info does the same.
         */
        fun apkInfo(m: ByteArray): Map<String, String> {
            val out = HashMap<String, String>()
            try {
                val b = java.nio.ByteBuffer.wrap(m).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                fun u16(o: Int) = b.getShort(o).toInt() and 0xFFFF
                val strings = ArrayList<String>()
                var ids = IntArray(0)
                var o = u16(2)                                            // chunks start after the file header
                while (o + 8 <= m.size) {
                    val kind = u16(o); val size = b.getInt(o + 4)
                    if (kind == 0x0001) {                                 // string pool
                        val utf8 = b.getInt(o + 16) and 0x100 != 0; val start = o + b.getInt(o + 20)
                        for (i in 0 until b.getInt(o + 8)) {
                            var at = start + b.getInt(o + u16(o + 2) + 4 * i)
                            if (utf8) {
                                at += if (m[at].toInt() and 0x80 != 0) 2 else 1  // length in chars, then in bytes
                                var n = m[at].toInt() and 0x7F
                                if (m[at].toInt() and 0x80 != 0) { n = (n shl 8) or (m[at + 1].toInt() and 0xFF); at++ }
                                strings.add(String(m, at + 1, n, Charsets.UTF_8))
                            } else {
                                var n = u16(at)
                                if (n and 0x8000 != 0) { n = ((n and 0x7FFF) shl 16) or u16(at + 2); at += 2 }
                                strings.add(String(m, at + 2, 2 * n, Charsets.UTF_16LE))
                            }
                        }
                    } else if (kind == 0x0180) {                          // resource ids of the attribute names
                        ids = IntArray((size - 8) / 4) { b.getInt(o + 8 + 4 * it) }
                    } else if (kind == 0x0102) {                          // a start tag: <manifest>, then <uses-sdk> if any
                        val tag = strings[b.getInt(o + 20)]
                        val attrs = o + 16 + u16(o + 24); val step = u16(o + 26)
                        for (i in 0 until u16(o + 28)) {
                            val a = attrs + i * step
                            val n = b.getInt(a + 4)
                            val name = (if (n in ids.indices) ATTR_IDS[ids[n]] else null) ?: strings[n]
                            val raw = b.getInt(a + 8); val type = m[a + 15].toInt() and 0xFF; val data = b.getInt(a + 16).toLong() and 0xFFFFFFFFL
                            val value = if (raw != -1) strings[raw] else if (type == 0x10 || type == 0x11) data.toString() else null
                            if (value != null && (tag == "manifest" && name in setOf("package", "versionName", "versionCode") || tag == "uses-sdk" && name == "minSdkVersion"))
                                out[name] = value
                        }
                        if (tag != "manifest" && tag != "uses-sdk") break // <uses-sdk> comes first when it is there at all
                    }
                    o += if (size > 0) size else m.size
                }
            } catch (e: RuntimeException) {
            }
            return out
        }

        /** The package name in an APK's binary AndroidManifest.xml. Null if unreadable. */
        fun apkPackage(m: ByteArray): String? = apkInfo(m)["package"]

        /** The CPU types an APK has native code for, from its file names (lib/<abi>/x.so); empty when it runs anywhere. */
        fun apkAbis(names: List<String>): List<String> =
            names.filter { it.startsWith("lib/") && it.endsWith(".so") && it.count { c -> c == '/' } >= 2 }.map { it.split("/")[1] }.toSortedSet().toList()

        /** Why this APK cannot go on a TV of this Android level and CPU, in the words the page translates; null when it can. */
        fun apkFits(info: Map<String, String>, abis: List<String>, sdk: Int, tvAbis: List<String>): String? {
            val need = info["minSdkVersion"]?.toIntOrNull() ?: 0
            if (sdk > 0 && need > sdk) return "apk needs sdk $need, the tv has sdk $sdk"
            if (abis.isNotEmpty() && tvAbis.isNotEmpty() && abis.none { it in tvAbis }) return "apk is built for ${abis.joinToString(",")}, the tv runs ${tvAbis.joinToString(",")}"
            return null
        }

        /** Java's String.split: trailing empty strings are dropped unless a limit is given. The parsing above counts on it. */
        private fun String.jsplit(regex: String, limit: Int = 0): Array<String> = Pattern.compile(regex).split(this, limit)

        /** "Total PSS by process" of dumpsys meminfo, summed per package (":service" processes count for their app), MB, largest first. */
        internal fun memoryByApp(meminfo: String): List<Pair<String, Int>> {
            val start = meminfo.indexOf("Total PSS by process:")
            if (start < 0) return emptyList()
            val by = HashMap<String, Int>()
            for (line in meminfo.substring(start).split("\n").drop(1)) {
                if (line.isBlank()) break
                val m = Regex("^\\s*([\\d,]+)K: ([^\\s(]+)").find(line) ?: continue
                if (!m.groupValues[2].contains('.')) continue          // apps only: not "system", native daemons, our own app_process
                val pkg = m.groupValues[2].substringBefore(':')
                by[pkg] = (by[pkg] ?: 0) + (m.groupValues[1].replace(",", "").toInt() / 1024)
            }
            return by.entries.filter { it.value >= 5 }.sortedByDescending { it.value }.take(25).map { it.key to it.value }
        }

        /** The home screen and the keyboard in use, the apps the user installed and the ones with a tile, as [keepNow] reads them. */
        private const val KEEP_NOW = "cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1; " +
            "settings get secure default_input_method; pm list packages -3; for c in LEANBACK_LAUNCHER LAUNCHER; do " +
            "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.\$c 2>/dev/null; done"

        /**
         * What may be stopped on this TV: the apps a person installed or can open from a tile (YouTube is a system app on most
         * TVs), but never its home screen or keyboard (they differ per TV). Everything else of the system's stays.
         */
        internal data class Keep(val home: String, val keyboard: String, val people: Set<String>)

        /** [Keep] from the output of [KEEP_NOW]. */
        internal fun keepNow(out: String): Keep {
            val lines = out.split("\n").map { it.trim() }
            val pkg = { i: Int -> lines.getOrElse(i) { "" }.substringBefore('/') }
            val people = lines.drop(2).mapNotNull { if (it.startsWith("package:")) it.substring(8) else if (it.contains('/')) it.substringBefore('/') else null }
            return Keep(pkg(0), pkg(1), people.toSet())
        }

        /** Why [pkg] may not be stopped ("us", "home", "keyboard", "system"), or null when it may. */
        internal fun keepWhy(pkg: String, k: Keep): String? = when {
            pkg.startsWith("dev.tvremote.") || pkg == "com.tailscale.ipn" -> "us"   // this remote, and the way to it from outside
            pkg == k.home -> "home"
            pkg == k.keyboard -> "keyboard"
            Setup.PROTECTED.matches(pkg) || pkg !in k.people -> "system"   // and Android's shared processes (android.process.acore)
            else -> null
        }

        /** One app in the task manager: MB in memory (all its processes), what it is doing, why it must stay (null: may be stopped). */
        internal data class Proc(val pkg: String, val mb: Int, val state: String, val keep: String?)

        /**
         * "Total PSS by OOM adjustment" of dumpsys meminfo: each app once, its processes summed, under the most active of their
         * groups: "front" (on screen), "bg" (working in the background: music, downloads, services), "home", "cached" (kept in
         * memory, doing nothing: Android lets them go first), "system" (Android itself: never stopped). Largest first.
         */
        internal fun procsOf(meminfo: String, k: Keep): List<Proc> {
            val start = meminfo.indexOf("Total PSS by OOM adjustment:")
            if (start < 0) return emptyList()
            val rank = listOf("system", "front", "home", "bg", "cached")
            val mb = HashMap<String, Int>(); val state = HashMap<String, String>()
            var group = "cached"
            for (line in meminfo.substring(start).split("\n").drop(1)) {
                if (line.isBlank()) break
                val g = if (line.contains("(pid ")) null else Regex("^\\s*[\\d,]+K: (.+)$").find(line)   // a group's head: no pid
                if (g != null) {
                    val name = g.groupValues[1].trim()
                    group = when {
                        name == "Native" -> "native"                  // daemons, not apps: left out
                        name == "System" || name.startsWith("Persistent") -> "system"
                        name == "Foreground" || name == "Visible" -> "front"
                        name == "Home" -> "home"
                        name == "Previous" || name.startsWith("Cached") -> "cached"
                        else -> "bg"                                  // Perceptible, A / B Services, Backup, Heavy Weight, ...
                    }
                    continue
                }
                val m = Regex("^\\s+([\\d,]+)K: ([^\\s(]+)").find(line) ?: continue
                val pkg = m.groupValues[2].substringBefore(':')
                if (group == "native" || !pkg.contains('.')) continue   // "system" itself (Android's core) shows in the RAM meter
                if (pkg == "com.google.android.webview" && m.groupValues[2].contains("sandboxed")) continue   // another app's page renderer: counted nowhere rather than wrongly
                mb[pkg] = (mb[pkg] ?: 0) + m.groupValues[1].replace(",", "").toInt() / 1024
                val was = state[pkg]
                if (was == null || rank.indexOf(group) < rank.indexOf(was)) state[pkg] = group   // an app's busiest process says what it is doing
            }
            return mb.entries.filter { it.value >= 1 }.map { (pkg, v) ->
                val st = state[pkg] ?: "cached"
                Proc(pkg, v, st, if (st == "system") "system" else keepWhy(pkg, k))
            }.sortedByDescending { it.mb }
        }

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

        /** A whole storage, never deleted or renamed: /sdcard, /storage, a volume, and /storage/emulated/0 or /storage/self/primary. */
        internal fun root(path: String): Boolean {
            val parts = path.split("/").filter { it.isNotEmpty() }
            return path == "/sdcard" || parts.firstOrNull() == "storage" && (parts.size <= 2 || parts.size == 3 && parts[1] in setOf("emulated", "self"))
        }

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
