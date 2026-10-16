package dev.tvremote.desktop

import dev.tvremote.core.AdbPrompt
import dev.tvremote.core.Atv
import dev.tvremote.core.HttpServer
import dev.tvremote.core.WireLog
import dev.tvremote.core.HttpServer.Response
import dev.tvremote.core.Remote
import dev.tvremote.core.RemoteSteps
import dev.tvremote.core.Scenes
import dev.tvremote.core.Tv
import dev.tvremote.core.WebPage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * The computer's server on the shared Kotlin core: the "pc" core of the page.
 * The routes that only the computer has (many TVs, the remote buttons without ADB, the first-time setup, the picture and
 * sound for this browser) are here; everything managed over ADB is Manage.kt's, as for TV Setup and the phone.
 */
class Server(private val repo: File, private val keys: File, private val cache: File, private val saved: Saved, first: String?, private val stop: () -> Unit) : HttpServer.Handler {
    private val web = File(repo, "app/shared/web")
    private val helperFile = File(repo, "app/shared/assets/tvr-helper.jar")   // Meta and Caps, started on the TV
    private val helper: () -> ByteArray = { helperFile.readBytes() }
    private val jarFile = File(repo, "app/shared/assets/scrcpy-server.jar")
    private val jar: () -> ByteArray = { jarFile.readBytes() }
    private val version = try { File(repo, "VERSION").readText().trim() } catch (e: IOException) { "" }
    init { WireLog.app = "TV Remote $version (computer)" }

    private val kinds = java.util.concurrent.ConcurrentHashMap<String, String>()   // before cur, which needs it: what an unsaved TV is (scan, ports)

    @Volatile var cur = session(first ?: saved.current)               // --tv: this run only, the saved choice stays
        private set
    private val fleets = Fleets(keys, cache)
    private val assist = Assist { cur }
    val updater = Updater(version, System.getProperty("tvr.res")?.let { File(it) }, stop).also { it.start() }

    /** An LG or Samsung TV gets a session with only its remote link; the kind is saved with the TV. */
    private fun kindOf(host: String): String = saved.find(host)?.optString("kind")?.takeIf { it.isNotEmpty() }?.let { Remote.Kind.of(it) }
        ?: kinds[host] ?: (if (host.isEmpty()) null else Remote.Kind.detect(host)?.also { kinds[host] = it }) ?: Remote.Kind.ANDROID

    private fun session(host: String) = Session(host, keys, cache, helper, jar, kindOf(host))

    /** Another TV: a whole new session; the old one is closed after (it may be in the middle of a request). */
    @Synchronized private fun switch(host: String) {
        val old = cur
        cur = session(host)
        Thread { old.close() }.start()
    }

    fun close() { cur.close(); fleets.close(); timer.shutdownNow(); updater.close() }

    // ---------------------------------------------------------------- the page
    @Volatile private var page: WebPage? = null
    @Volatile private var pageStamp = 0L
    @Volatile private var pageChecked = 0L

    /** Read again from disk when any of its files changed: a new page with the scripts it names. */
    private fun page(): WebPage {
        val p = page
        if (p != null && System.currentTimeMillis() - pageChecked < 1500) return p   // a page load asks for a dozen files at once
        pageChecked = System.currentTimeMillis()
        val stamp = web.walk().filter { it.isFile }.maxOfOrNull { it.lastModified() } ?: 0L
        if (p != null && stamp == pageStamp) return p
        pageStamp = stamp
        return WebPage({ name -> File(web, name).inputStream() }, "pc", (stamp / 1000).toString()).also { page = it }
    }

    // ---------------------------------------------------------------- requests
    override fun handle(r: HttpServer.Request): Response {
        val host = (r.headers["host"] ?: "").substringBeforeLast(':').removePrefix("[").removeSuffix("]")
        if (host != "127.0.0.1" && host != "localhost") return Response.error(403, "bad host")   // DNS-rebinding guard
        // other websites open in this browser must not drive the TV, not even through a picture or a <video> pointed at /api/...
        val origin = r.headers["origin"]
        if (origin != null && origin.substringAfter("//") != r.headers["host"]) return Response.error(403, "cross-origin")
        val site = r.headers["sec-fetch-site"]
        if (r.path.startsWith("/api/") && site != null && site != "same-origin" && site != "none") return Response.error(403, "cross-origin")
        if (r.method == "POST") {
            val type = (r.headers["content-type"] ?: "").substringBefore(';').trim()
            if (type != "application/json" && type != "application/octet-stream") return Response.error(415, "json only")
            if (type == "application/json" && r.jsonOrNull() == null) return Response.error(400, "bad json")   // never "save nothing"
        }
        if (!r.path.startsWith("/api/")) page().serve(r.path)?.let { return it }
        val s = cur
        return try {
            route(r, s) ?: s.manage?.handle(r) ?: if (r.path.startsWith("/api/") && s.host.isEmpty()) Response.error(400, "no TV selected")
                else Response.error(404, "not found")
        } catch (e: Session.Unavailable) {
            Response.error(503, e.message ?: "TV not connected")
        } catch (e: IllegalArgumentException) {
            Response.error(400, e.message ?: "bad request")
        } catch (e: IllegalStateException) {
            Response.error(400, e.message ?: "bad request")
        } catch (e: IOException) {                                    // nearly always ADB: the words Manage uses, which the page explains
            Response.error(503, if (s.link?.waitingForUser() == true) "adb:allow" else "adb: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    private fun adbReady(s: Session) = s.link?.state() == "ready"

    private fun native(s: Session) = s.kind != Remote.Kind.ANDROID

    /** An LG or Samsung TV's apps, from its remote link, in the shape Manage gives the page (names and pictures included). */
    private fun nativeApps(s: Session): Response {
        val apps = JSONArray()
        for (a in s.need().apps()) apps.put(JSONObject().put("pkg", a.id).put("meta", JSONObject().put("label", a.label).put("icon", a.icon.isNotEmpty())))
        return Response.json(JSONObject().put("apps", apps))
    }

    private fun sh(s: Session, cmd: String): String = (s.link ?: throw IOException("no TV selected")).get().shell(cmd)

    /** The window in front, to tell whether a key that should open something did. */
    private fun front(s: Session) = sh(s, "dumpsys window | grep -m1 mCurrentFocus").trim()

    private fun route(r: HttpServer.Request, s: Session): Response? {
        val post = r.method == "POST"
        if (!post) when (r.path) {
            "/api/state" -> return state(s)
            "/api/about" -> return about(s)
            "/api/wirelog" -> return Response.of("text/plain; charset=utf-8", WireLog.all.report(s.atv?.takeIf { native(s) }).toByteArray())
            "/api/update" -> return Response.json(updater.json())
            "/api/tvs" -> return Response.json(JSONObject().put("current", s.host).put("tvs", saved.tvs))
            "/api/discover" -> return Response.json(Discover.poll(r.query.containsKey("new")))
            "/api/setup/status" -> return setupStatus(s)
            "/api/assist" -> return Response.json(assist.state())
            "/api/screen" -> return screenshot(s)
            "/api/video" -> return video(s, r.q("q"))
            "/api/audio" -> return audio(s, r)
            "/api/stream" -> return Response.error(404, "this server sends H.264: /api/video")
            "/api/scenes" -> return if (r.query.containsKey("download")) Scenes.backup(saved.scenes) else Response.json(JSONObject().put("scenes", saved.scenes))
            "/api/clip" -> return clip(s.control().clipboard())
            "/api/fleet" -> return fleetInfo(s)
            "/api/fleet/job" -> return fleets.jobs[r.q("id")]?.let { Response.json(fleets.snapshot(it)) } ?: Response.error(404, "unknown job")
            "/api/apps" -> if (native(s)) return nativeApps(s)
            "/api/app/icon" -> {
                val png = (if (native(s)) s.atv?.icon(r.q("pkg")) else s.labels?.icon(r.q("pkg"))) ?: return Response.error(404, "no icon")
                return Response.of("image/png", png).also { it.headers["Cache-Control"] = "max-age=86400" }
            }
            "/api/app/meta" -> {
                val pkg = r.q("pkg")
                if (!Assist.PKG.matches(pkg)) return Response.error(404, "not found")
                val label = try { s.labels?.label(pkg) } catch (e: Exception) { null }
                val icons = try { s.labels?.icons() == true } catch (e: Exception) { false }
                return Response.json(JSONObject().put("label", label ?: pkg.substringAfterLast('.')).put("icon", icons).put("banner", icons))
            }
        }
        if (native(s) && post && r.path == "/api/search") {                // LG: YouTube's own search; Samsung cannot
            if (!s.need().search(r.json().optString("query").take(200))) throw IllegalArgumentException("this TV has no YouTube and no Google search")
            return Response.json(JSONObject().put("to", "youtube"))
        }
        if (native(s) && post && r.path == "/api/screen/off") {            // LG's own: Manage's goes through ADB
            if (!s.need().screenOff()) throw IllegalArgumentException("this TV cannot turn its screen off alone")
            return Response.ok()
        }
        if (native(s) && r.path !in OURS && r.path !in NATIVE_TOO)            // LG / Samsung: no ADB, so nothing of Manage's
            return Response.error(400, "unsupported: " + Remote.Kind.name(s.kind))
        if (!post) return null
        if (r.path == "/api/fleet/install") return fleetInstall(r, s)
        if (r.path == "/api/fleet/cancel") { fleets.stop(r.json().optString("id")); return Response.json(org.json.JSONObject().put("ok", true)) }
        if (r.path !in OURS) return null                              // Manage's: uploads among them, whose body is not JSON
        val d = r.json()
        return when (r.path) {
            "/api/key" -> key(s, d)
            "/api/app" -> {
                val link = d.optString("link").trim()
                if (link.isEmpty()) throw IllegalArgumentException("link required")
                if (adbReady(s)) null                                 // Manage: ADB reports whether the TV really opened it
                else if (native(s) && link == "settings") { s.need().key(176, Atv.SHORT); Response.json(JSONObject().put("ok", true).put("via", "remote")) }
                else { s.need().launch(if (link == "settings") "android-app://com.android.tv.settings" else link); Response.json(JSONObject().put("ok", true).put("via", "remote")) }   // best effort: many TVs ignore it
            }
            "/api/volume" -> volume(s, d)
            "/api/text" -> { s.need().text(d.optString("text").take(500)); Response.ok() }
            "/api/touch" -> if (native(s)) Response.error(400, "unsupported: " + Remote.Kind.name(s.kind)) else touch(s, d)
            "/api/type" -> if (native(s)) { s.need().type(minOf(d.optInt("del"), 500), d.optString("text").take(500), d.optBoolean("enter")); Response.ok() } else type(s, d)
            "/api/clip" -> if (native(s)) Response.error(400, "unsupported: " + Remote.Kind.name(s.kind)) else { s.control().setClipboard(d.optString("text").take(100000), d.optBoolean("paste")); Response.ok() }
            "/api/wake" -> wake(s)
            "/api/tv/select" -> {
                val host = d.optString("host").trim()
                if (!IP.matches(host)) throw IllegalArgumentException("not an IP address")
                if (d.has("kind")) kinds[host] = Remote.Kind.of(d.optString("kind"))   // from the scan: no need to look at its ports
                if (saved.find(host) != null) { saved.setCurrent(host); saved.save() }
                if (host != s.host) switch(host)                      // the TV in use again: keep its connections
                Response.ok()
            }
            "/api/tv/save" -> tvSave(s, d)
            "/api/tv/remove" -> {
                val host = d.optString("host")
                saved.remove(host); saved.save(); fleets.drop(host)
                if (host == s.host) switch(saved.current)
                Response.ok()
            }
            "/api/scenes" -> {
                val list = d.optJSONArray("scenes") ?: throw IllegalArgumentException("scenes required")   // a broken import must not wipe them
                saved.scenes = Scenes.clean(list); saved.save(); Response.json(JSONObject().put("scenes", saved.scenes))
            }
            "/api/scene/run" -> {
                val steps = d.optJSONArray("steps") ?: (0 until saved.scenes.length()).map { saved.scenes.getJSONObject(it) }.firstOrNull { it.optString("id") == d.optString("id") }?.optJSONArray("steps")
                    ?: return Response.error(404, "no such scene")
                val clean = Scenes.clean(JSONArray().put(JSONObject().put("name", "x").put("steps", steps))).optJSONObject(0)?.optJSONArray("steps") ?: JSONArray()
                val failed = Scenes.run(clean, if (native(s)) RemoteSteps(s.need()) { wake(s) } else Scenes.Shell({ sh(s, it) }))
                Response.json(JSONObject().put("ok", failed == null).put("failed", failed ?: ""))
            }
            "/api/fleet/do" -> {
                val work = fleets.action(d.optString("action"), d.optString("pkg"))
                val hosts = d.optJSONArray("hosts")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
                Response.json(fleets.run(d.optString("action") + " " + d.optString("pkg"), targets(hosts, s), work = work))
            }
            "/api/assist" -> assist.handle(d)
            "/api/pair/start" -> try {
                s.pairStart(); Response.ok()
            } catch (e: Exception) {
                Response.error(400, "không tới được dịch vụ remote của TV (cổng ${if (native(s)) Remote.Kind.ports(s.kind).joinToString("/") else "6467"}): ${e.message}")
            }
            "/api/pair/finish" -> try {
                s.pairFinish(d.optString("code").trim())
                for (i in 0 until 30) { if (s.available) break; Thread.sleep(200) }
                Response.json(JSONObject().put("ok", true).put("connected", s.available))
            } catch (e: IllegalArgumentException) {
                Response.error(400, "sai mã, thử lại")
            } catch (e: Exception) {
                Response.error(400, "ghép thất bại, bấm Ghép lại: ${e.message}")
            }
            "/api/update" -> when (d.optString("do")) {
                "check" -> { updater.check(); Response.json(updater.json()) }
                "install" -> { updater.install(); Response.json(updater.json()) }
                else -> Response.error(400, "do: check or install")
            }
            "/api/shutdown" -> { Thread { Thread.sleep(300); stop() }.start(); Response.ok() }   // after this reply has gone out
            else -> null
        }
    }

    // ---------------------------------------------------------------- state
    private fun state(s: Session): Response {
        val entry = saved.find(s.host)
        val adb = if (native(s)) "none" else s.link?.state() ?: "closed"   // none: this kind of TV has no ADB at all
        if (adb == "ready" || adb == "unauthorized") keepTrusted(s, adb)
        return Response.json(s.state().put("adb", JSONObject().put("state", adb)).put("name", entry?.optString("name") ?: "").put("saved", entry != null)
            .put("video", "h264"))                                    // the picture comes as H.264 (js/screen.js h264Loop), as from TV Setup
    }

    private val trustAsked = AtomicLong()
    private val timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { Thread(it, "server-timer").apply { isDaemon = true } }
    private fun later(ms: Long, what: () -> Unit) { timer.schedule(what, ms, java.util.concurrent.TimeUnit.MILLISECONDS) }
    private val trustFetched = HashSet<String>()

    /**
     * While ADB works, fetch a TV Setup pass for this TV once; when the TV asks about this computer, have TV Setup answer
     * (only for this computer's own key, see AdbPrompt). In the background: the page's state never waits for it.
     */
    private fun keepTrusted(s: Session, st: String) {
        val entry = saved.find(s.host) ?: return
        val host = s.host
        if (st == "ready" && entry.optString("setup_pass").isEmpty() && synchronized(trustFetched) { trustFetched.add(host) }) Thread({
            val pass = try { AdbPrompt.token(s.link!!.get(), "computer") } catch (e: Exception) { null }
            if (pass != null) saved.edit(host) { it.put("setup_pass", pass) }
            else later(300_000) { synchronized(trustFetched) { trustFetched.remove(host) } }   // TV Setup not there yet: ask again later
        }, "trust").apply { isDaemon = true }.start()
        else if (st == "unauthorized" && entry.optString("setup_pass").isNotEmpty() && System.currentTimeMillis() - trustAsked.get() > 45000) {
            trustAsked.set(System.currentTimeMillis())
            Thread({
                try {
                    val fp = dev.tvremote.core.Adb(keys).fingerprint()
                    when (AdbPrompt.ask(host, entry.optString("setup_pass"), fp, 30)) {
                        "forgotten" -> saved.edit(host) { it.remove("setup_pass") }   // "Unpair all" was pressed on the TV
                        "allowed" -> { println("TV Setup allowed this computer on $host"); s.link?.reconnect() }
                    }
                } catch (ignored: Exception) { }
            }, "trust").apply { isDaemon = true }.start()
        }
    }

    private fun about(s: Session): Response {
        val released = try {
            Regex("^## " + Regex.escape(version) + " - (\\d{4})-(\\d{2})-(\\d{2})", RegexOption.MULTILINE).find(File(repo, "CHANGELOG.md").readText())
                ?.groupValues?.let { "${it[3]}/${it[2]}/${it[1]}" } ?: ""
        } catch (e: IOException) { "" }
        val tvWeb = if (!native(s) && s.host.isNotEmpty() && s.host != "127.0.0.1" && s.host != "localhost" && Discover.portOpen(s.host, 8765)) "http://${s.host}:8765" else ""   // the TV Setup app's page
        return Response.json(JSONObject().put("version", version).put("released", released).put("tv_web", tvWeb).put("stale", false))
    }

    // ---------------------------------------------------------------- keys, volume, text
    private fun key(s: Session, d: JSONObject): Response {
        val k = d.optString("key").uppercase()
        val code = KEYS[k] ?: throw IllegalArgumentException("unknown key")
        val dir = d.optString("dir").ifEmpty { "SHORT" }.uppercase()
        if (dir !in setOf("SHORT", "START_LONG", "END_LONG")) throw IllegalArgumentException("bad dir")
        val adb = adbReady(s)
        if ((k in OPENERS || k in ADB_KEYS) && dir == "SHORT" && adb) {
            try {
                val before = if (k in OPENERS) front(s) else ""           // openers: say so when the screen ignores them
                when {
                    k == "SETTINGS" -> sh(s, "am start -a android.settings.SETTINGS")
                    k in ADB_KEYS -> sh(s, "input keyevent $code")
                    else -> s.need().key(code, Atv.SHORT)
                }
                if (k !in OPENERS) return Response.ok()
                Thread.sleep(900)
                return Response.json(JSONObject().put("ok", true).put("noop", before.isNotEmpty() && front(s) == before))
            } catch (e: IOException) {
                // ADB dropped: just press the key
            }
        }
        if (k == "SETTINGS" && dir != "END_LONG" && adb) {               // the key itself does nothing on many TVs; the Settings app always opens
            try { sh(s, "am start -a android.settings.SETTINGS"); return Response.ok() } catch (e: IOException) { }
        }
        if (!s.available && dir != "END_LONG" && adb) {
            // the remote protocol is down (e.g. Google's remote service crashed while the sound was captured): keys over ADB
            sh(s, (if (dir == "START_LONG") "input keyevent --longpress " else "input keyevent ") + code)
            return Response.json(JSONObject().put("ok", true).put("via", "adb"))
        }
        val a = s.need()
        synchronized(s.held) {
            if (dir != "SHORT") s.held.remove(code)?.cancel(false)
            if (dir == "START_LONG")                                  // safety net: never leave a key held if the page vanishes
                s.later(8000) { synchronized(s.held) { s.held.remove(code) }; if (a.ready()) a.key(code, Atv.UP) }?.let { s.held[code] = it }
        }
        a.key(code, when (dir) { "START_LONG" -> Atv.DOWN; "END_LONG" -> Atv.UP; else -> Atv.SHORT })
        return Response.ok()
    }

    private fun volume(s: Session, d: JSONObject): Response {
        val want = d.opt("level")
        val max = s.atv?.volume?.second?.takeIf { it > 0 } ?: 100
        if (want !is Int || want < 0 || want > max) throw IllegalArgumentException("bad level")
        if (adbReady(s)) {
            try { Scenes.volume({ sh(s, it) }, want); return Response.json(JSONObject().put("ok", true).put("via", "adb")) } catch (e: Exception) { }
        }
        val a = s.need()
        if (native(s) && a.setVolume(want)) return Response.json(JSONObject().put("ok", true).put("via", "remote").put("level", want))
        for (i in 0 until 60) {                                       // no ADB: step with the volume keys
            val now = a.volume?.first ?: break
            if (now == want) break
            a.key(if (now < want) 24 else 25, Atv.SHORT)
            Thread.sleep(200)
        }
        return Response.json(JSONObject().put("ok", true).put("via", "keys").put("level", a.volume?.first ?: JSONObject.NULL))
    }

    private fun touch(s: Session, d: JSONObject): Response {
        val x = d.optDouble("x", 0.0).toFloat()
        val y = d.optDouble("y", 0.0).toFloat()
        val t = s.control()
        when (d.optString("a")) {
            "tap" -> { t.touch(0, x, y); t.touch(1, x, y) }
            "down" -> t.touch(0, x, y)
            "up" -> t.touch(1, x, y)
            "move" -> t.touch(2, x, y)
            "scroll" -> t.scroll(x, y, d.optDouble("dy", 0.0).toFloat())
            else -> throw IllegalArgumentException("bad action")
        }
        return Response.ok()
    }

    /** The TV's clipboard as the page shows it; null: the TV did not answer in time. */
    private fun clip(text: String?) = if (text == null) Response.error(504, "the TV did not answer") else Response.json(JSONObject().put("text", text))

    private fun type(s: Session, d: JSONObject): Response {
        val t = s.control()
        repeat(minOf(d.optInt("del"), 500)) { t.press(67) }             // live typing: what changed is deleted, then typed again
        repeat(minOf(d.optInt("fdel"), 500)) { t.press(112) }
        d.optString("text").take(500).let { if (it.isNotEmpty()) t.text(it) }
        if (d.optBoolean("enter")) t.press(66)
        return Response.ok()
    }

    private fun wake(s: Session): Response {
        val r = Wake.wake(s.host, (saved.find(s.host) ?: JSONObject()).put("kind", s.kind), 60, s.atv?.mac ?: "")
        if (r.ok && !native(s) && s.atv?.isOn != true) {
            Thread.sleep(2000)
            if (s.available) s.atv?.key(26, Atv.SHORT)                 // screen off but the network alive
        }
        return Response.json(JSONObject().put("ok", r.ok).put("log", r.log.takeLast(400)))
    }

    // ---------------------------------------------------------------- picture and sound
    private fun screenshot(s: Session): Response = try {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(65536)
        (s.link ?: throw IOException("no TV selected")).get().open("exec:screencap -p").use {
            while (true) { val n = it.read(buf, 0, buf.size, 15000); if (n <= 0) break; out.write(buf, 0, n) }
        }
        if (out.size() < 8) throw IOException("empty picture")
        Response.of("image/png", out.toByteArray()).also { it.headers["Cache-Control"] = "no-store" }
    } catch (e: IOException) {
        Response.error(503, "screen: ${e.message}")
    }

    private fun video(s: Session, q: String): Response {
        if (s.host.isEmpty()) return Response.error(400, "no TV selected")
        val level = if (q == "hd") HD else q.toIntOrNull() ?: 1
        return s.feeds.video { l, sink -> Tv(keys, jar, s.host, null, level, 4096, false, l, sink) }
    }

    private fun audio(s: Session, r: HttpServer.Request): Response {
        if (!adbReady(s)) return Response.stream("application/octet-stream", -1) { it.write(packet(5, "adb: not ready".toByteArray())) }
        val opus = r.q("codec") == "opus"
        val hq = r.q("hq") == "1"                                     // high quality: Opus 160 kbit/s, or PCM as captured (48 kHz stereo)
        return s.feeds.sound(hq, opus) { l, sink ->
            Tv(keys, jar, s.host, null, 0, 0, false, l, null, if (opus) "opus" else "raw", sink, false, r.q("share") == "1", if (hq) 160000 else 64000)
        }.also { it.headers["Cache-Control"] = "no-store" }
    }

    private fun packet(kind: Int, data: ByteArray) = ByteBuffer.allocate(5 + data.size).put(kind.toByte()).putInt(data.size).put(data).array()

    // ---------------------------------------------------------------- saved TVs, first-time setup
    /** What the first-time setup needs to know about the current TV (needs ADB). */
    private fun tvInfo(s: Session): JSONObject {
        val part = sh(s, "getprop ro.product.manufacturer; echo '#'; getprop ro.product.model; echo '#'; pm path com.tailscale.ipn; echo '#'; " +
            "pm path me.efesser.flauncher; echo '#'; ip addr show").split("#", limit = 5).map { it.trim() } + List(5) { "" }
        var ts = ""; var lan = ""; var mac = ""
        for (block in part[4].split(Regex("(?m)^\\d+: "))) {           // one block per network interface
            val ip = Regex("inet ([0-9.]+)/").find(block)?.groupValues?.get(1) ?: continue
            val ether = Regex("link/ether ([0-9a-f:]{17})").find(block)?.groupValues?.get(1)
            if (ip.startsWith("100.")) ts = ip
            else if (lan.isEmpty() && ether != null && PRIVATE.matches(ip)) { lan = ip; mac = ether.uppercase() }   // the interface on the home network
        }
        return JSONObject().put("model", "${part[0]} ${part[1]}".trim()).put("tailscale_installed", "package:" in part[2]).put("tailscale_ip", ts)
            .put("lan_ip", lan).put("mac", mac).put("flauncher", "package:" in part[3])
    }

    /** The ADB state once a dial has had a moment: the setup steps read it right after a change on the TV. */
    private fun settled(s: Session): String {
        val l = s.link ?: return "closed"
        for (i in 0 until 40) { val st = l.state(); if (st != "unknown") return st; Thread.sleep(100) }
        return l.state()
    }

    private fun setupStatus(s: Session): Response {
        val st = if (native(s)) "none" else settled(s)
        val o = JSONObject().put("host", s.host).put("adb", JSONObject().put("state", st)).put("remote", s.state()).put("saved", saved.find(s.host) ?: JSONObject.NULL)
        if (st == "ready") try { val i = tvInfo(s); for (k in i.keys()) o.put(k, i.get(k)) } catch (e: IOException) { }
        if (native(s)) s.atv?.let { a -> o.put("model", a.model).put("mac", a.mac) }
        return Response.json(o.put("kind", s.kind))
    }

    /** Remember the current TV. Once it has a Tailscale IP that becomes its address, so it works from anywhere. */
    private fun tvSave(s: Session, d: JSONObject): Response {
        val info = if (adbReady(s)) tvInfo(s) else if (native(s)) JSONObject().put("model", s.atv?.model ?: "").put("mac", s.atv?.mac ?: "") else JSONObject()
        val old = s.host
        val host = info.optString("tailscale_ip").ifEmpty { old }
        if (host.isEmpty()) throw IllegalArgumentException("no TV selected")
        val entry = saved.upsert(JSONObject().put("name", d.optString("name").trim().take(60).ifEmpty { info.optString("model").ifEmpty { host } }).put("host", host)
            .put("lan", info.optString("lan_ip")).put("mac", info.optString("mac")).put("kind", s.kind), old)
        saved.save()
        val now = entry.optString("host")                             // another row of this TV may have brought its Tailscale IP
        if (now != old) switch(now)
        return Response.json(JSONObject().put("ok", true).put("tv", entry).put("moved", now != old))
    }

    // ---------------------------------------------------------------- several TVs at once
    /** Only the saved list: never an address typed in a request. */
    private fun targets(hosts: List<String>, s: Session) = hosts.filter { it.isNotEmpty() }.distinct().also { h ->
        if (h.isEmpty() || h.any { saved.find(it) == null || Remote.Kind.of(saved.find(it)?.optString("kind")) != Remote.Kind.ANDROID }) throw IllegalArgumentException("pick saved TVs")
    }.map { it to fleets.link(it) }

    private fun fleetInfo(s: Session): Response {
        val hosts = saved.tvs.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.filter { Remote.Kind.of(it.optString("kind")) == Remote.Kind.ANDROID }   // the others have no ADB
        val pool = java.util.concurrent.Executors.newCachedThreadPool()
        try {
            val rows = hosts.map { t -> pool.submit<JSONObject> { fleets.info(t.optString("host"), fleets.link(t.optString("host"))).put("name", t.optString("name")) } }.map { it.get() }
            return Response.json(JSONObject().put("current", s.host).put("tvs", JSONArray(rows)))
        } finally {
            pool.shutdown()
        }
    }

    private fun fleetInstall(r: HttpServer.Request, s: Session): Response {
        val targets = targets(r.q("hosts").split(","), s)
        if (r.length <= 0) throw IllegalArgumentException("empty upload")
        val name = r.q("name").ifEmpty { "upload.apk" }.replace(Regex("[^\\w.-]"), "_")
        val tmp = File.createTempFile("tvup-", "-$name")
        try {
            tmp.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                var left = r.length
                while (left > 0) {
                    val n = r.body.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) throw IOException("upload cut short")
                    out.write(buf, 0, n); left -= n
                }
            }
        } catch (e: Exception) {
            tmp.delete(); throw e
        }
        return Response.json(fleets.run("install $name", targets, { tmp.delete() }) { link, part -> fleets.install(link, part, tmp) })   // the copy goes once every TV has had it
    }

    companion object {
        /** The key names the page sends, to Android key codes. Over the remote protocol the search key opens the Assistant. */
        val KEYS: Map<String, Int> = Scenes.KEYS + mapOf("ASSIST" to 84, "SEARCH" to 84)
        /** The "More" keys go through ADB when it is open: TVs ignore some of them over the remote protocol (the input key, for one). */
        val ADB_KEYS = setOf("INPUT", "INFO", "GUIDE", "LIVE", "SUBTITLE", "CH_UP", "CH_DOWN", "REWIND", "STOP", "FFWD",
            "SLEEP", "WAKE")                                          // Sony ignores SLEEP sent over the remote protocol
        val OPENERS = setOf("MENU", "SETTINGS", "INPUT", "GUIDE", "LIVE")   // keys that should bring up a screen of their own
        val OURS = setOf("/api/key", "/api/app", "/api/volume", "/api/text", "/api/clip", "/api/touch", "/api/type", "/api/wake", "/api/tv/select", "/api/tv/save",
            "/api/tv/remove", "/api/scenes", "/api/scene/run", "/api/fleet/do", "/api/assist", "/api/pair/start", "/api/pair/finish", "/api/shutdown",
            "/api/update")
        /** What the page reads from an LG or Samsung TV besides [OURS]. */
        val NATIVE_TOO = setOf("/api/state", "/api/about", "/api/wirelog", "/api/update", "/api/tvs", "/api/discover", "/api/setup/status", "/api/scenes", "/api/apps", "/api/app/icon", "/api/app/meta", "/api/setup/need",
            "/api/fleet", "/api/fleet/job", "/api/fleet/install", "/api/fleet/cancel")
        const val HD = 5                                              // Tv.QUALITY's sharpest level, the page's "Nét nhất"
        val IP = Regex("((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)")   // IPv4: what ADB and the TV's page links are written for
        val PRIVATE = Regex("(10\\.|192\\.168\\.|172\\.(1[6-9]|2\\d|3[01])\\.)[0-9.]+")
    }
}
