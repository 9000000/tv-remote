package dev.tvremote.phone;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One session with a TV: ADB over the network, scrcpy-server started on the TV for the picture (H.264, decoded here
 * straight onto a Surface) and for input (keys, touch, text). Everything runs off the UI thread.
 */
final class Tv {
    interface Listener {
        void onStatus(String key, String detail);                     // connecting | allow | starting | live | closed | error
        void onVideoSize(int w, int h);
        void onNetwork(String mac, String lan);                       // the TV's home-network address, for Wake-on-LAN later
    }

    private static final String VER = "5.0.1", REMOTE = "/data/local/tmp/scrcpy-server.jar";
    /** Light on the TV: its software encoder costs about 0.7 of 4 cores at this size and rate, far more uncapped. */
    private static final String VIDEO = "video_bit_rate=2000000 max_fps=10 max_size=960";

    private final Context ctx;
    private final String host;
    private final Surface surface;
    private final Listener listener;
    private final boolean wake;                                       // turn the screen on first (the TV answered but is in standby)
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean stopped;
    private volatile Adb adb;
    private volatile Adb.Stream control;
    private volatile int width = 960, height = 540;
    private MediaCodec codec;

    Tv(Context ctx, String host, Surface surface, boolean wake, Listener listener) {
        this.ctx = ctx.getApplicationContext(); this.host = host; this.surface = surface; this.wake = wake; this.listener = listener;
        Thread t = new Thread(this::run, "tv-session");
        t.setDaemon(true);
        t.start();
    }

    private void run() {
        Adb.Stream server = null;
        try {
            listener.onStatus("connecting", host);
            Adb a = new Adb(ctx.getFilesDir());
            adb = a;
            a.connect(host, 5555, () -> listener.onStatus("allow", ""));
            if (stopped) return;
            listener.onStatus("starting", a.shell("getprop ro.product.model").trim());
            if (wake) a.shell("input keyevent KEYCODE_WAKEUP");
            reportNetwork(a);
            pushServer(a);
            String scid = String.format("%08x", new Random().nextInt(0x7fffffff));
            server = a.open("shell:CLASSPATH=" + REMOTE + " app_process / com.genymobile.scrcpy.Server " + VER + " scid=" + scid
                    + " tunnel_forward=true audio=false cleanup=false video=true control=true send_dummy_byte=true send_device_meta=false " + VIDEO);
            final Adb.Stream log = server;
            Thread drain = new Thread(() -> { try { log.readAll(); } catch (IOException ignored) { } }, "scrcpy-log");
            drain.setDaemon(true);
            drain.start();
            Adb.Stream video = null;
            byte[] one = new byte[1];
            for (int i = 0; i < 80 && video == null && !stopped; i++) {    // until the server listens, the TV refuses the socket
                try {
                    Adb.Stream s = a.open("localabstract:scrcpy_" + scid);
                    if (s.read(one, 0, 1, 4000) == 1 && one[0] == 0) video = s; else s.close();
                } catch (IOException notYet) {
                    if (a.isDead()) throw notYet;
                }
                if (video == null) Thread.sleep(250);
            }
            if (video == null) throw new IOException("scrcpy-server did not start on the TV");
            control = a.open("localabstract:scrcpy_" + scid);
            Adb.Stream fromTv = control;
            Thread ctl = new Thread(() -> { try { fromTv.readAll(); } catch (IOException ignored) { } }, "scrcpy-ctl");   // clipboard etc: not needed
            ctl.setDaemon(true);
            ctl.start();
            play(video);
            if (!stopped) listener.onStatus("closed", "");
        } catch (Throwable e) {
            if (!stopped) listener.onStatus("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            control = null;
            releaseCodec();
            if (adb != null) adb.close();                             // the TV stops scrcpy-server when its sockets go
            io.shutdownNow();
        }
    }

    /** MAC and IP of the interface that is on the home network (not Tailscale's 100.x, which has no MAC). */
    private void reportNetwork(Adb a) {
        try {
            for (String block : a.shell("ip addr show").split("(?m)^\\d+: ")) {
                Matcher ip = Pattern.compile("inet ([0-9.]+)/").matcher(block), mac = Pattern.compile("link/ether ([0-9a-f:]{17})").matcher(block);
                if (!ip.find() || !mac.find()) continue;
                String lan = ip.group(1);
                if (lan.startsWith("192.168.") || lan.startsWith("10.") || lan.matches("172\\.(1[6-9]|2\\d|3[01])\\..*")) {
                    listener.onNetwork(mac.group(1).toUpperCase(java.util.Locale.ROOT), lan);
                    return;
                }
            }
        } catch (IOException ignored) { }
    }

    /** Copies scrcpy-server from the APK to the TV unless the same file is already there. */
    private void pushServer(Adb a) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (InputStream in = ctx.getAssets().open("scrcpy-server.jar")) {
            byte[] b = new byte[65536];
            for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
        }
        byte[] jar = buf.toByteArray();
        String check = "[ -f " + REMOTE + " ] && wc -c < " + REMOTE + " || echo 0";
        if (a.shell(check).trim().equals(String.valueOf(jar.length))) return;
        Adb.Stream s = a.open("exec:cat > " + REMOTE);
        s.write(jar);
        s.close();
        for (int i = 0; i < 20; i++) {                                // cat finishes writing shortly after the stream closes
            if (a.shell(check).trim().equals(String.valueOf(jar.length))) return;
            try { Thread.sleep(150); } catch (InterruptedException e) { break; }
        }
        throw new IOException("could not copy scrcpy-server to the TV");
    }

    /**
     * scrcpy v5 video socket: 4 bytes codec id, then 12-byte headers. Top bit set = session packet (width, height in the
     * last 8 bytes, no payload). Otherwise bit 62 = codec config, bit 61 = key frame, the rest pts; last 4 bytes = size.
     */
    private void play(Adb.Stream video) throws IOException {
        byte[] head = new byte[12];
        video.readFully(head, 0, 4);
        boolean first = true;
        while (!stopped) {
            video.readFully(head, 0, 12);
            ByteBuffer h = ByteBuffer.wrap(head);
            long flags = h.getLong();
            int size = h.getInt();
            if (flags < 0) {
                width = (int) (flags & 0xffffffffL); height = size;
                startCodec();
                listener.onVideoSize(width, height);
                continue;
            }
            if (size < 0 || size > (8 << 20)) throw new IOException("bad video packet");
            byte[] data = new byte[size];
            video.readFully(data, 0, size);
            boolean config = (flags & (1L << 62)) != 0;
            if (codec == null) continue;
            int idx = -1;
            while (idx < 0 && !stopped) { idx = codec.dequeueInputBuffer(20000); render(); }
            if (idx < 0) break;
            ByteBuffer in = codec.getInputBuffer(idx);
            in.clear();
            in.put(data);
            codec.queueInputBuffer(idx, 0, size, config ? 0 : (flags & ((1L << 61) - 1)), config ? MediaCodec.BUFFER_FLAG_CODEC_CONFIG : 0);
            for (int i = 0; i < 6 && !render(); i++) { }               // the TV only sends a frame when the picture changes: show it now
            if (first && !config) { first = false; listener.onStatus("live", ""); }
        }
    }

    private boolean render() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean shown = false;
        for (int out; (out = codec.dequeueOutputBuffer(info, shown ? 0 : 15000)) >= 0; shown = true) codec.releaseOutputBuffer(out, true);
        return shown;
    }

    private void startCodec() throws IOException {
        releaseCodec();
        MediaFormat f = MediaFormat.createVideoFormat("video/avc", width, height);
        f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2 << 20);
        if (Build.VERSION.SDK_INT >= 30) f.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);
        codec = MediaCodec.createDecoderByType("video/avc");
        codec.configure(f, surface, null, 0);
        codec.start();
    }

    private void releaseCodec() {
        if (codec == null) return;
        try { codec.stop(); } catch (Exception ignored) { }
        try { codec.release(); } catch (Exception ignored) { }
        codec = null;
    }

    void stop() {
        stopped = true;
        Adb a = adb;
        if (a != null) a.close();
    }

    boolean ready() { return control != null && !stopped; }

    // ---- input: scrcpy control messages (big-endian), see ControlMessageReader.java in scrcpy ----
    private void send(final byte[] msg) {
        final Adb.Stream c = control;
        if (c == null || io.isShutdown()) return;
        try {
            io.execute(() -> { try { c.write(msg); } catch (IOException ignored) { } });
        } catch (RuntimeException shuttingDown) {
            // session ended between the check and the call
        }
    }

    /** action: 0 down, 1 up. */
    void key(int action, int code, int repeat, int meta) {
        send(ByteBuffer.allocate(14).put((byte) 0).put((byte) action).putInt(code).putInt(repeat).putInt(meta).array());
    }

    void press(int code) { key(0, code, 0, 0); key(1, code, 0, 0); }

    /** A finger on the TV screen. action: 0 down, 1 up, 2 move; x, y in 0..1 of the picture. */
    void touch(int action, float x, float y) {
        int px = Math.round(Math.max(0, Math.min(1, x)) * (width - 1)), py = Math.round(Math.max(0, Math.min(1, y)) * (height - 1));
        send(ByteBuffer.allocate(32).put((byte) 2).put((byte) action).putLong(-2).putInt(px).putInt(py)
                .putShort((short) width).putShort((short) height).putShort((short) (action == 1 ? 0 : 0xffff)).putInt(0).putInt(0).array());
    }

    void text(String t) {
        byte[] raw;
        try { raw = t.getBytes("UTF-8"); } catch (java.io.UnsupportedEncodingException e) { return; }
        boolean ascii = raw.length == t.length();
        if (ascii) {                                                  // typed as key events
            send(ByteBuffer.allocate(5 + raw.length).put((byte) 1).putInt(raw.length).put(raw).array());
            return;
        }
        // accents, CJK...: key events cannot type them, so put the text on the TV clipboard and press Ctrl+V
        send(ByteBuffer.allocate(14 + raw.length).put((byte) 9).putLong(0).put((byte) 0).putInt(raw.length).put(raw).array());
        int ctrl = 0x3000;                                            // META_CTRL_ON | META_CTRL_LEFT_ON
        key(0, 113, 0, ctrl); key(0, 50, 0, ctrl); key(1, 50, 0, ctrl); key(1, 113, 0, 0);
    }

    // ---- apps ----
    /** "package/activity" of every app that shows on the TV's home screen. */
    List<String> apps() throws IOException {
        List<String> out = new ArrayList<>();
        Adb a = adb;
        if (a == null) return out;
        Matcher m = Pattern.compile("(?m)^\\s+([\\w.]+/[\\w.$]+)\\s*$").matcher(
                a.shell("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER"));
        while (m.find()) if (!out.contains(m.group(1))) out.add(m.group(1));
        java.util.Collections.sort(out);
        return out;
    }

    void launch(final String component) {
        final Adb a = adb;
        if (a == null || !component.matches("[\\w.]+/[\\w.$]+")) return;
        new Thread(() -> { try { a.shell("am start -n '" + component + "'"); } catch (IOException ignored) { } }).start();
    }
}
