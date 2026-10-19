package dev.tvremote.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The TV's own internet speed, measured on the TV (run as the shell with app_process, which may use the network): what a phone
 * measures says nothing of the TV's Wi-Fi. Against Cloudflare's speed test (speed.cloudflare.com, the one its page uses): the
 * time to open a connection, then a few connections downloading, then uploading, each for some seconds. One line per step,
 * read by SpeedTest.kt while it runs: "ping <ms>", "down <bytes> <ms>" and "up <bytes> <ms>" as they go, "done", or "error ...".
 */
public final class Speed {
    private Speed() { }

    private static final String HOST = "speed.cloudflare.com";
    private static final int STREAMS = 4;

    public static void main(String[] args) {
        try {
            run(args.length > 0 ? Integer.parseInt(args[0]) : 8);
            say("done");
        } catch (Throwable e) {
            say("error " + e);
        } finally {
            Runtime.getRuntime().halt(0);                             // the transfers' threads would keep it alive
        }
    }

    private static void say(String line) { System.out.println(line); System.out.flush(); }

    private static void run(int seconds) throws Exception {
        long[] ping = new long[5];
        for (int i = 0; i < ping.length; i++) {
            long t = System.nanoTime();
            try (Socket s = new Socket()) { s.connect(new InetSocketAddress(HOST, 443), 5000); }
            ping[i] = (System.nanoTime() - t) / 1_000_000;
        }
        Arrays.sort(ping);
        say("ping " + ping[ping.length / 2]);
        transfer("down", seconds * 1000L, false);
        transfer("up", seconds * 1000L * 3 / 4, true);
    }

    /** [STREAMS] connections for [ms], then a line with every byte moved and the time; a line each half second meanwhile. */
    private static void transfer(String name, long ms, boolean up) throws Exception {
        AtomicLong bytes = new AtomicLong();
        long start = System.currentTimeMillis(), end = start + ms;
        Throwable[] failed = new Throwable[1];
        Thread[] ts = new Thread[STREAMS];
        for (int i = 0; i < STREAMS; i++) {
            ts[i] = new Thread(() -> {
                try {
                    while (System.currentTimeMillis() < end) {
                        if (up) upload(bytes, end); else download(bytes, end);
                    }
                } catch (Throwable e) {
                    failed[0] = e;
                }
            });
            ts[i].setDaemon(true);
            ts[i].start();
        }
        while (System.currentTimeMillis() < end) {
            Thread.sleep(500);
            say(name + " " + bytes.get() + " " + (System.currentTimeMillis() - start));
            if (failed[0] != null && bytes.get() == 0) throw new Exception(failed[0].toString());
        }
        say(name + " " + bytes.get() + " " + (System.currentTimeMillis() - start));
    }

    private static HttpURLConnection open(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://" + HOST + path).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        c.setUseCaches(false);
        return c;
    }

    private static void download(AtomicLong bytes, long end) throws Exception {
        HttpURLConnection c = open("/__down?bytes=50000000");
        try (InputStream in = c.getInputStream()) {
            byte[] b = new byte[64 * 1024];
            for (int n; System.currentTimeMillis() < end && (n = in.read(b)) > 0; ) bytes.addAndGet(n);
        } finally {
            c.disconnect();
        }
    }

    private static void upload(AtomicLong bytes, long end) throws Exception {
        HttpURLConnection c = open("/__up");
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setChunkedStreamingMode(64 * 1024);
        byte[] b = new byte[64 * 1024];
        try (OutputStream out = c.getOutputStream()) {
            for (int i = 0; i < 160 && System.currentTimeMillis() < end; i++) { out.write(b); bytes.addAndGet(b.length); }   // 10 MB a request
        }
        c.getResponseCode();
        c.disconnect();
    }
}
