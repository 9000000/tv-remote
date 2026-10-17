package dev.tvremote.core.remote
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import dev.tvremote.core.B64
import dev.tvremote.core.WireLog

/**
 * Samsung Tizen TVs (2016 on): the remote channel Samsung's SmartThings app uses, JSON over a WebSocket on port 8002 (TLS,
 * with a token) or 8001 (older sets, no token). The first time, the TV asks "allow this device?"; the token it then hands
 * over is kept and sent again, so it lets us straight in later. Keys are KEY_ names; there is no volume level and no picture.
 */
class Samsung(private val dir: File, private val host: String, private val ports: List<Pair<Int, Boolean>> = PORTS, private val rest: Int = 8001) : Remote {
    override val kind = Remote.Kind.SAMSUNG
    @Volatile override var model = ""
        private set
    override val vendor = "Samsung"
    @Volatile override var version = ""
        private set
    @Volatile override var isOn: Boolean? = null
        private set
    override val volume: Triple<Int, Int, Boolean>? get() = null      // the channel does not tell it
    @Volatile override var app = ""
        private set
    @Volatile override var mac = ""
        private set
    @Volatile override var asking = false
        private set
    override val keys: Set<Int> get() = KEYS.keys

    private val saved = TvKeys(dir, "samsung")
    @Volatile private var ws: WebSocket? = null
    @Volatile private var live = false
    @Volatile private var closed = false
    private val out = Executors.newSingleThreadExecutor { Thread(it, "samsung-out").apply { isDaemon = true } }
    @Volatile private var appsWait: CountDownLatch? = null
    @Volatile private var appsGot: JSONArray? = null
    private val icons = ConcurrentHashMap<String, String>()           // app id -> its picture's path on the TV
    private val deep = ConcurrentHashMap<String, Boolean>()           // app id -> launched as DEEP_LINK (app_type 2), from the app list
    private val iconWait = ConcurrentHashMap<String, Pair<CountDownLatch, Array<ByteArray?>>>()
    @Volatile private var keyboardNew = true                          // the TV's keyboard just came up: it wants a hello first
    @Volatile private var playing = false

    override fun ready(): Boolean = live && !closed

    /** The TV's own description (no pairing needed): name, model, network card, power state. Null when it is not a Samsung TV. */
    fun probe(): JSONObject? = try { get("/api/v2/")?.let { JSONObject(String(it, Charsets.UTF_8)).optJSONObject("device") } } catch (e: Exception) { null }

    private fun get(path: String, method: String = "GET"): ByteArray? {
        val c = dev.tvremote.core.Lan.open(URL("http://$host:$rest$path")) as HttpURLConnection
        c.connectTimeout = 2500; c.readTimeout = 4000; c.requestMethod = method
        return try { if (c.responseCode !in 200..299) null else c.inputStream.use { it.readBytes() } } finally { c.disconnect() }
    }

    /**
     * Blocks until the TV takes keys: at once with a token it gave before, else after the person accepts on the TV ([asking]
     * meanwhile; the TV gives up by itself after about 30 s). [Remote.Refused]: they chose Deny.
     */
    override fun connect(onClosed: () -> Unit) {
        probe().also { WireLog.all.add("←", "Samsung $host /api/v2/ " + (it ?: "no answer")) }?.let { d ->
            model = d.optString("modelName").ifEmpty { d.optString("name") }
            version = listOf(d.optString("OS"), d.optString("firmwareVersion").takeIf { it != "Unknown" } ?: "").filter { it.isNotEmpty() }.joinToString(" ")
            mac = d.optString("wifiMac").uppercase()
            d.optString("PowerState").takeIf { it.isNotEmpty() }?.let { isOn = it == "on" }
        }
        val name = B64.encode("TV Remote".toByteArray())
        val token = saved.get(host)
        var last: IOException? = null
        for ((p, tls) in ports) {
            val path = "/api/v2/channels/samsung.remote.control?name=$name" + (if (token.isNotEmpty()) "&token=$token" else "")
            val s = try {
                WebSocket.open(host, p, path, tls)                     // 8001 ignores the token
            } catch (e: IOException) { last = e; WireLog.all.add("·", "Samsung $host:$p $e"); continue }
            WireLog.all.add("·", "Samsung $host:$p open $path")
            try { hello(s, onClosed); return } catch (e: Remote.Refused) { WireLog.all.add("·", "Samsung $host refused: ${e.message}"); s.close(); throw e }
                catch (e: IOException) { WireLog.all.add("·", "Samsung $host:$p $e"); s.close(); last = e }
        }
        throw last ?: IOException("no port")
    }

    private fun hello(s: WebSocket, onClosed: () -> Unit) {
        s.timeout(1500)
        val until = System.nanoTime() / 1_000_000 + 40_000
        while (!closed) {
            val text = try { s.read() } catch (e: java.net.SocketTimeoutException) {
                if (System.nanoTime() / 1_000_000 > until) throw IOException("nobody accepted on the TV")
                asking = true                                         // no answer at once: the TV is asking the person
                continue
            } ?: throw IOException("the TV closed the connection")
            WireLog.all.add("←", "Samsung $host $text")
            val m = try { JSONObject(text) } catch (e: Exception) { continue }
            when (m.optString("event")) {
                "ms.channel.connect" -> {
                    asking = false
                    m.optJSONObject("data")?.optString("token")?.takeIf { it.isNotEmpty() }?.let { saved.put(host, it) }
                    ws = s
                    live = true
                    if (isOn == null) isOn = true
                    s.timeout(0)
                    Thread({ read(s, onClosed) }, "samsung-in").apply { isDaemon = true }.start()
                    return
                }
                "ms.channel.unauthorized" -> { asking = false; throw Remote.Refused("the TV said no") }
                "ms.channel.timeOut", "ms.channel.timeout" -> { asking = false; throw IOException("nobody accepted on the TV") }
            }
        }
        throw IOException("closed")
    }

    private fun read(s: WebSocket, onClosed: () -> Unit) {
        try {
            while (true) {
                val text = s.read() ?: break
                WireLog.all.add("←", "Samsung $host $text")
                val m = try { JSONObject(text) } catch (e: Exception) { continue }
                when (m.optString("event")) {
                    "ed.installedApp.get" -> {
                        appsGot = m.optJSONObject("data")?.optJSONArray("data") ?: JSONArray()
                        appsWait?.countDown()
                    }
                    "ms.remote.imeStart", "ms.remote.imeEnd" -> { keyboardNew = true; typed = "" }   // a new field: nothing typed in it yet
                    "ed.apps.icon" -> m.optJSONObject("data")?.let { d ->
                        iconWait.remove(d.optString("iconPath"))?.let { (w, got) ->
                            got[0] = try { B64.decode(d.optString("imageBase64")) } catch (e: Exception) { null }
                            w.countDown()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            WireLog.all.add("·", "Samsung $host read: $e")
        }
        WireLog.all.add("·", "Samsung $host closed")
        val was = live
        live = false
        appsWait?.countDown()
        if (was && !closed) onClosed()
    }

    private fun send(m: JSONObject) {
        val t = m.toString()
        WireLog.all.add("→", "Samsung $host $t")
        try { ws?.send(t) } catch (e: IOException) { WireLog.all.add("·", "Samsung $host send: $e") }
    }

    private fun press(name: String, cmd: String) = send(JSONObject().put("method", "ms.remote.control").put("params", JSONObject()
        .put("Cmd", cmd).put("DataOfCmd", name).put("Option", "false").put("TypeOfRemote", "SendRemoteKey")))

    override fun key(code: Int, direction: Int) {
        var name = KEYS[code] ?: return
        if (closed) return
        if (code == 85 && direction != Atv.UP) { playing = !playing; name = if (playing) "KEY_PLAY" else "KEY_PAUSE" }   // no toggle key: the two in turn
        out.execute {
            if (!ready()) return@execute
            when (direction) {
                Atv.DOWN -> press(name, "Press")
                Atv.UP -> press(if (code == 85) (if (playing) "KEY_PLAY" else "KEY_PAUSE") else name, "Release")
                else -> press(name, "Click")
            }
        }
    }

    /** Into the focused field (the TV's keyboard has to be up); Enter after, as the TV's own keyboard wants. */
    override fun text(t: String) {
        out.execute {
            if (keyboardNew) {                                        // some TVs take no text before this (samsungtvws does the same)
                keyboardNew = false
                send(JSONObject().put("method", "ms.channel.emit").put("params", JSONObject().put("event", "custom.remote.textReceived").put("to", "broadcast")))
            }
            send(JSONObject().put("method", "ms.remote.control").put("params", JSONObject()
                .put("Cmd", B64.encode(t.toByteArray(Charsets.UTF_8))).put("DataOfCmd", "base64").put("TypeOfRemote", "SendInputString")))
        }
    }

    /** The TV's keyboard takes the whole text each time: what live typing has put there so far is kept here. */
    @Volatile private var typed = ""
    override fun type(del: Int, add: String, enter: Boolean) {
        val now = typed.dropLast(del) + add
        if (now != typed) { typed = now; text(now) }
        if (enter) { typed = ""; key(66, Atv.SHORT) }
    }

    /** An app by its id ("111299001912" YouTube, "3201907018807" Netflix), or a link through the TV's browser. */
    override fun launch(link: String) {
        out.execute {
            val web = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(link)
            val yt = Regex("(?:youtube\\.com/watch\\?v=|youtu\\.be/)([\\w-]{6,})").find(link)?.groupValues?.get(1)
            val (id, type, meta) = when {
                yt != null -> Triple(YOUTUBE, "DEEP_LINK", yt)
                web -> Triple(BROWSER, "NATIVE_LAUNCH", link)
                else -> Triple(link, if (deep[link] ?: !link.contains('.')) "DEEP_LINK" else "NATIVE_LAUNCH", "")   // the list says; else Tizen web apps are numbers
            }
            send(JSONObject().put("method", "ms.channel.emit").put("params", JSONObject().put("event", "ed.apps.launch").put("to", "host")
                .put("data", JSONObject().put("appId", id).put("action_type", type).put("metaTag", meta))))
            app = id
        }
    }

    override fun apps(): List<Remote.App> {
        if (!ready()) return emptyList()
        val w = CountDownLatch(1)
        appsWait = w; appsGot = null
        send(JSONObject().put("method", "ms.channel.emit").put("params", JSONObject().put("event", "ed.installedApp.get").put("to", "host")))
        w.await(6, TimeUnit.SECONDS)
        val list = appsGot ?: return emptyList()
        val o = ArrayList<Remote.App>()
        for (i in 0 until list.length()) {
            val a = list.optJSONObject(i) ?: continue
            val id = a.optString("appId"); if (id.isEmpty()) continue
            val icon = a.optString("icon")
            if (icon.isNotEmpty()) icons[id] = icon
            if (a.has("app_type")) deep[id] = a.optInt("app_type") == 2
            o.add(Remote.App(id, a.optString("name").ifEmpty { id }, icon))
        }
        return o
    }

    /** The app list names a file on the TV: the TV sends it back over the remote channel, as SmartThings asks for it. */
    override fun icon(id: String): ByteArray? {
        val path = icons[id] ?: return null
        if (!ready()) return null
        val w = CountDownLatch(1); val got = arrayOfNulls<ByteArray>(1)
        iconWait[path] = w to got
        send(JSONObject().put("method", "ms.channel.emit").put("params", JSONObject().put("event", "ed.apps.icon").put("to", "host")
            .put("data", JSONObject().put("iconPath", path))))
        w.await(4, TimeUnit.SECONDS)
        iconWait.remove(path)
        return got[0]
    }

    override fun close() {
        closed = true
        live = false
        out.shutdownNow()
        val s = ws
        Thread { s?.close() }.start()
    }

    companion object {
        val PORTS = listOf(8002 to true, 8001 to false)
        const val YOUTUBE = "111299001912"
        const val BROWSER = "org.tizen.browser"

        /** Android key codes to the TV's KEY_ names; what has none (the Assistant, the TV's own settings) is greyed. */
        val KEYS: Map<Int, String> = HashMap<Int, String>().apply {
            putAll(mapOf(19 to "KEY_UP", 20 to "KEY_DOWN", 21 to "KEY_LEFT", 22 to "KEY_RIGHT", 23 to "KEY_ENTER", 66 to "KEY_ENTER",
                4 to "KEY_RETURN", 3 to "KEY_HOME", 82 to "KEY_MENU", 176 to "KEY_MENU", 24 to "KEY_VOLUP", 25 to "KEY_VOLDOWN",
                164 to "KEY_MUTE", 166 to "KEY_CHUP", 167 to "KEY_CHDOWN", 85 to "KEY_PLAY",
                89 to "KEY_REWIND", 90 to "KEY_FF", 86 to "KEY_STOP", 165 to "KEY_INFO", 172 to "KEY_GUIDE", 170 to "KEY_TV",
                175 to "KEY_CAPTION", 178 to "KEY_SOURCE", 26 to "KEY_POWER", 223 to "KEY_POWER", 67 to "KEY_DEL"))
            for (d in 0..9) put(7 + d, "KEY_$d")
        }
    }
}
