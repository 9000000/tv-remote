package dev.tvremote.core

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Locale
import java.util.Random
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * One session with a TV: ADB over the network, scrcpy-server started on the TV for the picture (H.264, decoded here
 * straight onto a Surface) and for input (keys, touch, text). Everything runs off the UI thread.
 * With no Surface the TV is asked for input only: no picture is encoded, so the TV does no extra work.
 */
class Tv(
    ctx: Context,
    private val host: String,
    private val surface: Surface?,
    /** index into QUALITY */
    quality: Int,
    /** longest side of the phone screen, there is no point sending more pixels than it shows */
    maxSide: Int,
    /** turn the screen on first (the TV answered but is in standby) */
    private val wake: Boolean,
    private val listener: Listener,
    /** instead of a Surface: the encoded picture is handed over as it arrives (to pass on to a browser) */
    private val sink: Sink? = null,
) {
    /** One H.264 packet as scrcpy sends it (Annex B). config: SPS/PPS, needed before the first frame; key: a key frame. */
    fun interface Sink { fun video(config: Boolean, key: Boolean, data: ByteArray) }

    interface Listener {
        fun onStatus(key: String, detail: String)                     // connecting | allow | starting | live | closed | error
        fun onVideoSize(w: Int, h: Int)
        fun onNetwork(mac: String, lan: String)                       // the TV's home-network address, for Wake-on-LAN later
    }

    companion object {
        private const val VER = "5.0.1"
        private const val REMOTE = "/data/local/tmp/scrcpy-server.jar"

        /**
         * Picture quality: longest side in pixels, bits per second, frames per second. The bit rate is what decides the data
         * used while the picture moves (a still picture sends almost nothing); the size mostly decides the load on the TV,
         * whose software encoder costs about 0.7 of 4 cores at 960 px and 2 Mbit/s, far more uncapped.
         */
        /** Keys that should bring up a screen of their own: Menu, TV settings, Input, Guide, Live TV. See [pressSeen]. */
        val OPENERS = setOf(82, 176, 178, 172, 170)

        val QUALITY = arrayOf(intArrayOf(480, 300_000, 8), intArrayOf(720, 800_000, 10), intArrayOf(960, 2_000_000, 10),
            intArrayOf(360, 200_000, 8), intArrayOf(240, 120_000, 8))   // added later, at the end: the index is what people have saved

        /** The levels from lightest to sharpest, as indexes into QUALITY. */
        val LEVELS = intArrayOf(4, 3, 0, 1, 2)
    }

    private val ctx: Context = ctx.applicationContext
    private val video: String                                         // scrcpy video options
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var stopped = false
    @Volatile private var adb: Adb? = null
    @Volatile private var control: Adb.Stream? = null
    @Volatile private var width = 960
    @Volatile private var height = 540
    private var codec: MediaCodec? = null
    private val wantVideo = surface != null || sink != null

    init {
        val q = QUALITY[quality.coerceIn(0, QUALITY.size - 1)]
        video = "video_bit_rate=" + q[1] + " max_fps=" + q[2] + " max_size=" + maxOf(160, minOf(q[0], maxSide) / 8 * 8)
        Thread({ run() }, "tv-session").apply { isDaemon = true }.start()
    }

    private fun run() {
        try {
            listener.onStatus("connecting", host)
            val a = Adb(ctx.filesDir)
            adb = a
            a.connect(host, 5555) { listener.onStatus("allow", "") }
            if (stopped) return
            listener.onStatus("starting", a.shell("getprop ro.product.model").trim())
            if (wake) a.shell("input keyevent KEYCODE_WAKEUP")
            reportNetwork(a)
            pushServer(a)
            val scid = String.format("%08x", Random().nextInt(0x7fffffff))
            val log = a.open("shell:CLASSPATH=" + REMOTE + " app_process / com.genymobile.scrcpy.Server " + VER + " scid=" + scid
                    + " tunnel_forward=true audio=false cleanup=false control=true send_dummy_byte=true send_device_meta=false "
                    + (if (wantVideo) "video=true $video" else "video=false")
                    // scrcpy-server does not always notice that we left (a still picture sends nothing), and a leftover one keeps
                    // loading the TV. So a shell loop on the TV waits for our heartbeat and kills the server when it stops.
                    + " & P=\$!; while read -t 25 x; do :; done; kill \$P")
            Thread({
                try { while (!stopped) { log.write("\n".toByteArray()); Thread.sleep(8000) } } catch (gone: Exception) { }
            }, "scrcpy-heartbeat").apply { isDaemon = true }.start()
            Thread({ try { log.readAll() } catch (ignored: IOException) { } }, "scrcpy-log").apply { isDaemon = true }.start()
            var first: Adb.Stream? = null                             // the video socket, or the control socket when there is no picture
            val one = ByteArray(1)
            var i = 0
            while (i < 80 && first == null && !stopped) {             // until the server listens, the TV refuses the socket
                try {
                    val s = a.open("localabstract:scrcpy_$scid")
                    if (s.read(one, 0, 1, 4000) == 1 && one[0].toInt() == 0) first = s else s.close()
                } catch (notYet: IOException) {
                    if (a.isDead()) throw notYet
                }
                if (first == null) Thread.sleep(250)
                i++
            }
            if (first == null) throw IOException("scrcpy-server did not start on the TV")
            if (!wantVideo) {
                control = first
                listener.onStatus("live", "")
                try { first.readAll() } catch (ignored: IOException) { }   // returns when the TV closes the session
            } else {
                val fromTv = a.open("localabstract:scrcpy_$scid")
                control = fromTv
                Thread({ try { fromTv.readAll() } catch (ignored: IOException) { } }, "scrcpy-ctl").apply { isDaemon = true }.start()   // clipboard etc: not needed
                play(first)
            }
            if (!stopped) listener.onStatus("closed", "")
        } catch (e: Throwable) {
            if (!stopped) listener.onStatus("error", e.message ?: e.javaClass.simpleName)
        } finally {
            control = null
            releaseCodec()
            adb?.close()                                              // the heartbeat stops, the loop on the TV kills scrcpy-server
            io.shutdownNow()
        }
    }

    /** MAC and IP of the interface that is on the home network (not Tailscale's 100.x, which has no MAC). */
    private fun reportNetwork(a: Adb) {
        try {
            for (block in Pattern.compile("(?m)^\\d+: ").split(a.shell("ip addr show"))) {
                val ip = Pattern.compile("inet ([0-9.]+)/").matcher(block)
                val mac = Pattern.compile("link/ether ([0-9a-f:]{17})").matcher(block)
                if (!ip.find() || !mac.find()) continue
                val lan = ip.group(1)!!
                if (lan.startsWith("192.168.") || lan.startsWith("10.") || lan.matches(Regex("172\\.(1[6-9]|2\\d|3[01])\\..*"))) {
                    listener.onNetwork(mac.group(1)!!.uppercase(Locale.ROOT), lan)
                    return
                }
            }
        } catch (ignored: IOException) {
        }
    }

    /** Copies scrcpy-server from the APK to the TV unless the same file is already there. */
    @Throws(IOException::class)
    private fun pushServer(a: Adb) {
        val jar = ctx.assets.open("scrcpy-server.jar").use { it.readBytes() }
        val check = "[ -f $REMOTE ] && wc -c < $REMOTE || echo 0"
        if (a.shell(check).trim() == jar.size.toString()) return
        val s = a.open("exec:cat > $REMOTE")
        s.write(jar)
        s.close()
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
                if (surface != null) startCodec()
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
                if (first && !config) { first = false; listener.onStatus("live", "") }
                continue
            }
            val c = codec ?: continue
            var idx = -1
            while (idx < 0 && !stopped) { idx = c.dequeueInputBuffer(20000); render(c) }
            if (idx < 0) break
            val input = c.getInputBuffer(idx)!!
            input.clear()
            input.put(data)
            c.queueInputBuffer(idx, 0, size, if (config) 0L else flags and ((1L shl 61) - 1), if (config) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0)
            var tries = 0
            while (tries < 6 && !render(c)) tries++                   // the TV only sends a frame when the picture changes: show it now
            if (first && !config) { first = false; listener.onStatus("live", "") }
        }
    }

    private fun render(c: MediaCodec): Boolean {
        val info = MediaCodec.BufferInfo()
        var shown = false
        while (true) {
            val out = c.dequeueOutputBuffer(info, if (shown) 0 else 15000)
            if (out < 0) break
            c.releaseOutputBuffer(out, true)
            shown = true
        }
        return shown
    }

    @Throws(IOException::class)
    private fun startCodec() {
        releaseCodec()
        val f = MediaFormat.createVideoFormat("video/avc", width, height)
        f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2 shl 20)
        if (Build.VERSION.SDK_INT >= 30) f.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        val c = MediaCodec.createDecoderByType("video/avc")
        codec = c
        c.configure(f, surface, null, 0)
        c.start()
    }

    private fun releaseCodec() {
        val c = codec ?: return
        try { c.stop() } catch (ignored: Exception) { }
        try { c.release() } catch (ignored: Exception) { }
        codec = null
    }

    fun stop() {
        stopped = true
        adb?.close()
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

    fun text(t: String) {
        val raw = t.toByteArray(Charsets.UTF_8)
        val ascii = raw.size == t.length
        if (ascii) {                                                  // typed as key events
            send(ByteBuffer.allocate(5 + raw.size).put(1.toByte()).putInt(raw.size).put(raw).array())
            return
        }
        // accents, CJK...: key events cannot type them, so put the text on the TV clipboard and press Ctrl+V
        send(ByteBuffer.allocate(14 + raw.size).put(9.toByte()).putLong(0L).put(0.toByte()).putInt(raw.size).put(raw).array())
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
    fun search(query: String) {
        val a = adb
        if (a == null || query.trim().isEmpty()) return
        Thread { try { a.shell("am start -a android.search.action.GLOBAL_SEARCH --es query " + Adb.q(query.trim())) } catch (ignored: IOException) { } }.start()
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

    fun launch(component: String) {
        val a = adb
        if (a == null || !component.matches(Regex("[\\w.]+/[\\w.\$]+"))) return
        Thread { try { a.shell("am start -n '$component'") } catch (ignored: IOException) { } }.start()
    }
}
