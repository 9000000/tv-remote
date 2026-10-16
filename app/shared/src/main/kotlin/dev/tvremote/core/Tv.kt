package dev.tvremote.core

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Locale
import java.util.Random
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * One session with a TV: ADB over the network, scrcpy-server started on the TV for the picture (H.264, shown by a [Screen]
 * or handed to a [Sink]) and for input (keys, touch, text). Everything runs off the UI thread.
 * With neither the TV is asked for input only: no picture is encoded, so the TV does no extra work.
 * Plain JVM, so the computer runs it too; on Android it is made by the Tv(context, …) in TvAndroid.kt.
 */
class Tv(
    /** where the ADB key lives */
    private val dir: File,
    /** scrcpy-server.jar, copied to the TV when it does not have this one */
    private val jar: () -> ByteArray,
    private val host: String,
    private val screen: Screen?,
    /** index into QUALITY */
    quality: Int,
    /** longest side of the phone screen, there is no point sending more pixels than it shows */
    maxSide: Int,
    /** turn the screen on first (the TV answered but is in standby) */
    private val wake: Boolean,
    private val listener: Listener,
    /** instead of a Screen: the encoded picture is handed over as it arrives (to pass on to a browser) */
    private val sink: Sink? = null,
    /**
     * The TV's sound (Android 11+; while it is captured the TV's own speakers are silent), handed to [audioSink]. null: none.
     * "opus": about 8 KB/s. "raw": 48 kHz 16-bit stereo PCM, no encoding at all on the TV.
     */
    private val audio: String? = null,
    private val audioSink: AudioSink? = null,
    /** false: a session that only watches or listens, with no keys or touch */
    private val wantControl: Boolean = true,
    /**
     * true: the TV keeps playing its sound too (scrcpy's playback capture with audio_dup; Android 13+). Older TVs fall back to
     * the private way (TV speakers silent) and say so with onStatus("audio", "noshare"). Apps that refuse playback capture
     * (some with DRM) are silent here; from Android 13 the private way uses it too (the whole output gave silence on some TVs,
     * such as the FPT Play box), so there they keep playing on the TV instead.
     */
    private val audioShare: Boolean = false,
    /** Opus bit rate: 64 kbit/s is plenty for speech and TV, 160 sounds like the source for music */
    private val audioBitRate: Int = 64000,
) {
    /** One H.264 packet as scrcpy sends it (Annex B). config: SPS/PPS, needed before the first frame; key: a key frame. */
    fun interface Sink { fun video(config: Boolean, key: Boolean, data: ByteArray) }

    /** One packet of sound as scrcpy sends it. config: the codec's header (Opus: OpusHead), before the first packet. */
    fun interface AudioSink {
        fun audio(config: Boolean, data: ByteArray)
        /** the sound has stopped for good */
        fun end() {}
    }

    /** Shows the picture: [start] at each new size (the decoder is made again), then one H.264 packet at a time. */
    interface Screen {
        fun start(w: Int, h: Int)
        /** false: the decoder took nothing for a long while, and the session ends */
        fun feed(config: Boolean, pts: Long, data: ByteArray): Boolean
        fun release()
    }

    interface Listener {
        fun onStatus(key: String, detail: String)                     // connecting | allow | starting | live | closed | error; audio (detail "unsupported"); rate
        fun onVideoSize(w: Int, h: Int)
        fun onNetwork(mac: String, lan: String, tailscale: String)    // the TV's home-network address (for Wake-on-LAN), and its 100.x one if any
        fun wantsNetwork(): Boolean = true                            // false: nobody uses the address (TV Setup's own session), so skip the `ip addr show`
        fun wantsPass(): Boolean = false                              // no TV Setup pass for this TV yet: fetch one while ADB works
        fun onPass(token: String) {}                                  // lets TV Setup answer "Allow USB debugging?" for us later, see AdbPrompt
    }

    companion object {
        private const val VER = "5.0.1"
        private const val REMOTE = "/data/local/tmp/scrcpy-server.jar"
        /** Waits for the capture's virtual output to go, then starts Google's remote service: see endCapture */
        private const val REVIVE_REMOTE = "for i in 1 2 3 4 5 6 7 8 9 10; do dumpsys audio | grep -qi remote_submix || break; sleep 0.5; done; " +
            "pm path com.google.android.tv.remote.service >/dev/null 2>&1 && am startservice -n com.google.android.tv.remote.service/.RemoteService >/dev/null 2>&1; true"

        /**
         * Picture quality: longest side in pixels, bits per second, frames per second. The bit rate is what decides the data
         * used while the picture moves (a still picture sends almost nothing); the size mostly decides the load on the TV,
         * whose software encoder costs about 0.7 of 4 cores at 960 px and 2 Mbit/s, far more uncapped.
         */
        /** Keys that should bring up a screen of their own: Menu, TV settings, Input, Guide, Live TV. See [pressSeen]. */
        val OPENERS = setOf(82, 176, 178, 172, 170)

        val QUALITY = arrayOf(intArrayOf(480, 300_000, 8), intArrayOf(720, 800_000, 10), intArrayOf(960, 2_000_000, 10),
            intArrayOf(360, 200_000, 8), intArrayOf(240, 120_000, 8),   // added later, at the end: the index is what people have saved
            intArrayOf(1280, 4_000_000, 30))                          // the computer's "sharpest, heavy on the TV" (not in LEVELS: the phone never offers it)

        /** The levels from lightest to sharpest, as indexes into QUALITY. */
        val LEVELS = intArrayOf(4, 3, 0, 1, 2)
    }

    private val video: String                                         // scrcpy video options
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var stopped = false
    @Volatile private var capture: List<Adb.Stream>? = null           // the sockets while the TV's sound is captured
    @Volatile private var adb: Adb? = null
    @Volatile private var control: Adb.Stream? = null
    @Volatile private var width = 960
    @Volatile private var height = 540
    private val wantVideo = screen != null || sink != null

    init {
        val q = QUALITY[quality.coerceIn(0, QUALITY.size - 1)]
        video = "video_bit_rate=" + q[1] + " max_fps=" + q[2] + " max_size=" + maxOf(160, minOf(q[0], maxSide) / 8 * 8)
        Thread({ run() }, "tv-session").apply { isDaemon = true }.start()
    }

    /** To the listener, and into the log (not the rate: every second). */
    private fun status(key: String, detail: String) {
        WireLog.all.add("·", "picture $host $key $detail")
        listener.onStatus(key, detail)
    }

    private fun run() {
        val socks = arrayListOf<Adb.Stream>()
        try {
            status("connecting", host)
            val a = Adb(dir)
            adb = a
            a.connect(host, 5555) { status("allow", "") }
            if (stopped) return
            status("starting", a.shell("getprop ro.product.model").trim())
            if (wake) a.shell("input keyevent KEYCODE_WAKEUP")
            if (listener.wantsNetwork()) reportNetwork(a)
            if (listener.wantsPass()) try { AdbPrompt.token(a, "phone")?.let { listener.onPass(it) } } catch (ignored: IOException) { }
            pushServer(a)
            var hearing = audio
            val sdk = if (hearing != null) a.shell("getprop ro.build.version.sdk").trim().toIntOrNull() ?: 0 else 0
            val share = hearing != null && audioShare && sdk >= 33
            if (hearing != null && audioShare && !share && sdk >= 30) status("audio", "noshare")
            if (hearing != null && sdk < 30) {                        // scrcpy captures sound from Android 11
                status("audio", "unsupported")
                hearing = null
                if (!wantVideo && !wantControl) throw IOException("this TV cannot send its sound (Android 11 or newer is needed)")
            }
            val scid = String.format("%08x", Random().nextInt(0x7fffffff))
            val log = a.open("shell:CLASSPATH=" + REMOTE + " app_process / com.genymobile.scrcpy.Server " + VER + " scid=" + scid
                    + " tunnel_forward=true cleanup=false control=$wantControl send_dummy_byte=true send_device_meta=false clipboard_autosync=false "   // autosync on, GET_CLIPBOARD answers nothing
                    + (if (wantVideo) "video=true $video" else "video=false")
                    + (if (hearing == null) " audio=false" else " audio=true audio_codec=$hearing" + (if (hearing == "opus") " audio_bit_rate=$audioBitRate" else "") + (if (sdk >= 33) " audio_source=playback" else "") + (if (share) " audio_dup=true" else ""))
                    // scrcpy-server does not always notice that we left (a still picture sends nothing), and a leftover one keeps
                    // loading the TV. So a shell loop on the TV waits for our heartbeat and kills the server when it stops.
                    + " & P=\$!; while read -t 25 x; do :; done; kill \$P")
            Thread({
                try { while (!stopped) { log.write("\n".toByteArray()); Thread.sleep(8000) } } catch (gone: Exception) { }
            }, "scrcpy-heartbeat").apply { isDaemon = true }.start()
            Thread({ try { log.readAll() } catch (ignored: IOException) { } }, "scrcpy-log").apply { isDaemon = true }.start()
            var first: Adb.Stream? = null                             // the first of the sockets: video, then sound, then control, whichever are asked for
            val one = ByteArray(1)
            var i = 0
            while (i < 200 && first == null && !stopped) {             // until the server listens, the TV refuses the socket
                try {
                    val s = a.open("localabstract:scrcpy_$scid")
                    if (s.read(one, 0, 1, 4000) == 1 && one[0].toInt() == 0) first = s else s.close()
                } catch (notYet: IOException) {
                    if (a.isDead()) throw notYet
                }
                if (first == null) Thread.sleep(100)     // app_process takes a second or so; polling often wins the tail of it
                i++
            }
            if (first == null) throw IOException("scrcpy-server did not start on the TV")
            socks.add(first)                                          // the server takes the connections in this order
            val count = (if (wantVideo) 1 else 0) + (if (hearing != null) 1 else 0) + (if (wantControl) 1 else 0)
            while (socks.size < count) socks.add(a.open("localabstract:scrcpy_$scid"))
            var next = 0
            val pictureIn = if (wantVideo) socks[next++] else null
            val soundIn = if (hearing != null) socks[next++] else null
            val ctl = if (wantControl) socks[next] else null
            control = ctl
            if (hearing != null) capture = socks
            if (soundIn != null && (pictureIn != null || ctl != null))
                // a codec error ends the sound, not the app
                Thread({ try { hear(soundIn, hearing!!) } catch (ignored: Throwable) { } }, "scrcpy-audio").apply { isDaemon = true }.start()
            if (pictureIn != null) {
                if (ctl != null) Thread({ try { deviceMessages(ctl) } catch (ignored: IOException) { } }, "scrcpy-ctl").apply { isDaemon = true }.start()
                play(pictureIn)
            } else if (ctl != null) {
                status("live", "")
                try { deviceMessages(ctl) } catch (ignored: IOException) { }   // returns when the TV closes the session
            } else if (soundIn != null) {
                status("live", "")
                hear(soundIn, hearing!!)
            }
            if (!stopped) status("closed", "")
        } catch (e: Throwable) {
            if (!stopped) status("error", e.message ?: e.javaClass.simpleName)
        } finally {
            control = null
            screen?.release()
            if (capture != null) endCapture(socks)
            capture = null
            adb?.close()                                              // the heartbeat stops, the loop on the TV kills scrcpy-server
            io.shutdownNow()
        }
    }

    /**
     * The sound capture ends: closing the sockets lets scrcpy-server let it go itself (killed, Android notices the dead capture
     * seconds later and the TV's own sound stutters meanwhile). Then Google's remote service is started again: on some TVs it
     * crashes on the capture's virtual output and Android waits hours to restart it.
     */
    private fun endCapture(socks: List<Adb.Stream>) {
        val a = adb ?: return
        for (s in socks) try { s.close() } catch (ignored: Exception) { }
        try { a.shell(REVIVE_REMOTE) } catch (ignored: IOException) { }   // waits there for the virtual output to go
    }

    /** MAC and IP of the interface that is on the home network, and the Tailscale 100.x address (no MAC) if the TV has one. */
    private fun reportNetwork(a: Adb) {
        try {
            val all = a.shell("ip addr show")
            val ts = Regex("inet (100\\.(?:6[4-9]|[7-9]\\d|1[01]\\d|12[0-7])\\.[0-9.]+)/").find(all)?.groupValues?.get(1) ?: ""
            for (block in Pattern.compile("(?m)^\\d+: ").split(all)) {
                val ip = Pattern.compile("inet ([0-9.]+)/").matcher(block)
                val mac = Pattern.compile("link/ether ([0-9a-f:]{17})").matcher(block)
                if (!ip.find() || !mac.find()) continue
                val lan = ip.group(1)!!
                if (lan.startsWith("192.168.") || lan.startsWith("10.") || lan.matches(Regex("172\\.(1[6-9]|2\\d|3[01])\\..*"))) {
                    listener.onNetwork(mac.group(1)!!.uppercase(Locale.ROOT), lan, ts)
                    return
                }
            }
        } catch (ignored: IOException) {
        }
    }

    /** Copies scrcpy-server from the APK to the TV unless the same file is already there. */
    @Throws(IOException::class)
    private fun pushServer(a: Adb) {
        val jar = jar()
        val check = "[ -f $REMOTE ] && wc -c < $REMOTE || echo 0"
        if (a.shell(check).trim() == jar.size.toString()) return
        a.put(REMOTE, jar)
        for (i in 0 until 20) {                                       // cat finishes writing shortly after the stream closes
            if (a.shell(check).trim() == jar.size.toString()) return
            try { Thread.sleep(150) } catch (e: InterruptedException) { break }
        }
        throw IOException("could not copy scrcpy-server to the TV")
    }

    /**
     * scrcpy v5 video socket: 4 bytes codec id, then 12-byte headers. Top bit set = session packet (width, height in the
     * last 8 bytes, no payload). Otherwise bit 62 = codec config, bit 61 = key frame, the rest pts; last 4 bytes = size.
     */
    @Throws(IOException::class)
    private fun play(video: Adb.Stream) {
        val head = ByteArray(12)
        video.readFully(head, 0, 4)
        var first = true
        var bytes = 0L
        var since = System.currentTimeMillis()
        while (!stopped) {
            video.readFully(head, 0, 12)
            val h = ByteBuffer.wrap(head)
            val flags = h.long
            val size = h.int
            if (flags < 0) {
                width = (flags and 0xffffffffL).toInt(); height = size
                screen?.start(width, height)
                listener.onVideoSize(width, height)
                continue
            }
            if (size < 0 || size > (8 shl 20)) throw IOException("bad video packet")
            val data = ByteArray(size)
            video.readFully(data, 0, size)
            bytes += size + 12
            val now = System.currentTimeMillis()
            if (now - since >= 3000) {                                // data actually used, shown next to the TV's name
                listener.onStatus("rate", (bytes * 1000 / (now - since) / 1024).toString())
                bytes = 0; since = now
            }
            val config = (flags and (1L shl 62)) != 0L
            if (sink != null) {
                sink.video(config, (flags and (1L shl 61)) != 0L, data)
                if (first && !config) { first = false; status("live", "") }
                continue
            }
            val sc = screen ?: continue
            if (!sc.feed(config, if (config) 0L else flags and ((1L shl 61) - 1), data)) break
            if (first && !config) { first = false; status("live", "") }
        }
    }

    /**
     * scrcpy's sound socket: 4 bytes codec id (0 or 1: the TV could not capture sound), then the same 12-byte headers as the
     * picture, handed to [audioSink].
     */
    @Throws(IOException::class)
    private fun hear(s: Adb.Stream, codec: String) {
        val head = ByteArray(12)
        s.readFully(head, 0, 4)
        val id = ByteBuffer.wrap(head, 0, 4).int
        if (id == 0 || id == 1) { status("audio", "unsupported"); return }   // e.g. the TV refused the capture
        var bytes = 0L
        var since = System.currentTimeMillis()
        try {
            while (!stopped) {
                s.readFully(head, 0, 12)
                val h = ByteBuffer.wrap(head)
                val flags = h.long
                val size = h.int
                if (size < 0 || size > (1 shl 20)) throw IOException("bad audio packet")
                val data = ByteArray(size)
                s.readFully(data, 0, size)
                val config = (flags and (1L shl 62)) != 0L
                audioSink?.audio(config, data)
                bytes += size + 12
                val now = System.currentTimeMillis()
                if (now - since >= 3000) { listener.onStatus("arate", (bytes * 1000.0 / (now - since) / 1024).let { if (it < 10) String.format(java.util.Locale.US, "%.1f", it) else it.toInt().toString() }); bytes = 0; since = now }   // Opus: under 1 KB/s while the TV is quiet
            }
        } finally {
            audioSink?.end()
        }
    }

    fun stop() {
        stopped = true
        val c = capture
        if (c == null) { adb?.close(); return }
        Thread({                                                      // the session's thread ends the capture, then closes ADB
            for (s in c) try { s.close() } catch (ignored: Exception) { }
            Thread.sleep(20000)
            adb?.close()                                              // in case it is stuck
        }, "scrcpy-stop").apply { isDaemon = true }.start()
    }

    fun ready(): Boolean = control != null && !stopped

    // ---- input: scrcpy control messages (big-endian), see ControlMessageReader.java in scrcpy ----
    private fun send(msg: ByteArray) {
        val c = control
        if (c == null || io.isShutdown) return
        try {
            io.execute { try { c.write(msg) } catch (ignored: IOException) { } }
        } catch (shuttingDown: RuntimeException) {
            // session ended between the check and the call
        }
    }

    /** action: 0 down, 1 up. */
    fun key(action: Int, code: Int, repeat: Int, meta: Int) {
        send(ByteBuffer.allocate(14).put(0.toByte()).put(action.toByte()).putInt(code).putInt(repeat).putInt(meta).array())
    }

    fun press(code: Int) { key(0, code, 0, 0); key(1, code, 0, 0) }

    /** A finger on the TV screen. action: 0 down, 1 up, 2 move; x, y in 0..1 of the picture. */
    fun touch(action: Int, x: Float, y: Float) {
        val px = Math.round(x.coerceIn(0f, 1f) * (width - 1))
        val py = Math.round(y.coerceIn(0f, 1f) * (height - 1))
        send(ByteBuffer.allocate(32).put(2.toByte()).put(action.toByte()).putLong(-2L).putInt(px).putInt(py)
                .putShort(width.toShort()).putShort(height.toShort()).putShort((if (action == 1) 0 else 0xffff).toShort()).putInt(0).putInt(0).array())
    }

    /** The mouse wheel at x, y (0..1 of the picture); [dy] as a browser's wheel: 16 is one notch, positive scrolls down. */
    fun scroll(x: Float, y: Float, dy: Float) {
        val px = Math.round(x.coerceIn(0f, 1f) * (width - 1))
        val py = Math.round(y.coerceIn(0f, 1f) * (height - 1))
        val v = Math.round(dy / 16 * 0x7FFF).coerceIn(-0x7FFF, 0x7FFF)
        send(ByteBuffer.allocate(21).put(3.toByte()).putInt(px).putInt(py).putShort(width.toShort()).putShort(height.toShort())
            .putShort(0).putShort(v.toShort()).putInt(0).array())
    }

    // ---- the TV's clipboard ----
    private val clipLock = Object()
    private var clipGot = 0                                           // device CLIPBOARD messages so far (guarded by clipLock)
    private var clip = ""

    /** What scrcpy-server tells (DeviceMessage.java): the TV's clipboard, on change and when asked; the rest is skipped. */
    private fun deviceMessages(ctl: Adb.Stream) {
        val head = ByteArray(4)
        while (true) {
            if (ctl.read(head, 0, 1, 0) < 0) return
            when (head[0].toInt()) {
                0 -> {                                                // CLIPBOARD: int length, UTF-8
                    ctl.readFully(head, 0, 4)
                    val n = ByteBuffer.wrap(head).int
                    if (n < 0 || n > (1 shl 20)) { ctl.readAll(); return }
                    val text = ByteArray(n).also { ctl.readFully(it, 0, n) }
                    synchronized(clipLock) { clip = String(text, Charsets.UTF_8); clipGot++; clipLock.notifyAll() }
                }
                1 -> ctl.readFully(ByteArray(8), 0, 8)                // ACK_CLIPBOARD: the sequence of a set
                2 -> { ctl.readFully(head, 0, 4); val n = ByteBuffer.wrap(head).getShort(2).toInt() and 0xffff; ctl.readFully(ByteArray(n), 0, n) }   // UHID_OUTPUT
                else -> { ctl.readAll(); return }                     // a newer server's message: nothing after it can be read
            }
        }
    }

    /** The text on the TV's clipboard ("" when empty), or null when the TV did not say within [timeoutMs]. Blocks. */
    fun clipboard(timeoutMs: Long = 2500): String? {
        val before = synchronized(clipLock) { clipGot }
        send(byteArrayOf(8, 0))                                       // GET_CLIPBOARD, no copy key pressed first
        val end = System.currentTimeMillis() + timeoutMs
        synchronized(clipLock) {
            while (clipGot == before) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) return null
                clipLock.wait(left)
            }
            return clip
        }
    }

    /** Puts [t] on the TV's clipboard; [paste]: and pastes it where the TV's cursor is. */
    fun setClipboard(t: String, paste: Boolean) {
        val raw = t.toByteArray(Charsets.UTF_8)
        send(ByteBuffer.allocate(14 + raw.size).put(9.toByte()).putLong(0L).put((if (paste) 1 else 0).toByte()).putInt(raw.size).put(raw).array())
    }

    fun text(t: String) {
        val raw = t.toByteArray(Charsets.UTF_8)
        val ascii = raw.size == t.length
        if (ascii) {                                                  // typed as key events
            send(ByteBuffer.allocate(5 + raw.size).put(1.toByte()).putInt(raw.size).put(raw).array())
            return
        }
        // accents, CJK...: key events cannot type them, so put the text on the TV clipboard and press Ctrl+V
        setClipboard(t, false)
        val ctrl = 0x3000                                             // META_CTRL_ON | META_CTRL_LEFT_ON
        key(0, 113, 0, ctrl); key(0, 50, 0, ctrl); key(1, 50, 0, ctrl); key(1, 113, 0, 0)
    }

    // ---- apps ----
    /** "package/activity" of every app that shows on the TV's home screen. */
    @Throws(IOException::class)
    fun apps(): List<String> {
        val out = ArrayList<String>()
        val a = adb ?: return out
        val m = Pattern.compile("(?m)^\\s+([\\w.]+/[\\w.\$]+)\\s*\$").matcher(
                a.shell("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER"))
        while (m.find()) if (!out.contains(m.group(1)!!)) out.add(m.group(1)!!)
        out.sort()
        return out
    }

    /** Hands a sentence to the TV's own search / Assistant, as if it had been spoken to the TV's remote. */
    /** Looks [query] up on the TV ([TvShell.search]); blocks. "yt" (YouTube), "gs" (the TV's search), or null: nothing took it. */
    fun search(query: String): String? {
        val a = adb ?: return null
        return try { a.shell(TvShell.search(query)).trim().takeIf { it == "yt" || it == "gs" } } catch (e: Exception) { null }
    }

    /** Opens the TV's Settings. The settings key of a remote does nothing on many TVs, the app itself always opens. */
    /**
     * For keys that should open something ([OPENERS]; 176 opens the TV's settings): presses the key and says whether the TV's front
     * window changed. False means the TV got the key and the current screen ignored it, so the person is not left guessing
     * whether the remote works. Blocks for about a second.
     */
    fun pressSeen(code: Int): Boolean {
        val a = adb ?: return true
        return try {
            val front = { a.shell("dumpsys window | grep -m1 mCurrentFocus").trim() }
            val before = front()
            if (code == 176) a.shell("am start -a android.settings.SETTINGS") else press(code)
            Thread.sleep(900)
            before.isEmpty() || front() != before
        } catch (e: Exception) { true }
    }

    /** What is on the TV's screen right now, as a PNG; null when it could not be taken. */
    fun screenshot(): ByteArray? {
        val a = adb ?: return null
        return try {
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(65536)
            a.open("exec:screencap -p").use {
                while (true) {
                    val n = it.read(buf, 0, buf.size, 15000)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                }
            }
            if (out.size() > 8) out.toByteArray() else null
        } catch (e: IOException) { null }
    }

    fun settings() {
        val a = adb ?: return
        Thread { try { a.shell("am start -a android.settings.SETTINGS") } catch (ignored: IOException) { } }.start()
    }

    /** Runs a shell command on the TV through this session's ADB connection; "" when not connected. */
    @Throws(IOException::class)
    fun shell(cmd: String): String = adb?.shell(cmd) ?: ""

    /** The ADB connection this session runs on, for helpers beside it (NowPlaying on the phone's remote). */
    fun adb(): Adb = adb ?: throw IOException("not connected")

    /** The ADB connection under this session, for what goes its own way (drawing on the TV's screen: [Draw]). */
    fun link(): Adb? = adb

    fun launch(component: String) {
        val a = adb
        if (a == null || !component.matches(Regex("[\\w.]+/[\\w.\$]+"))) return
        Thread { try { a.shell("am start -n '$component'") } catch (ignored: IOException) { } }.start()
    }
}
