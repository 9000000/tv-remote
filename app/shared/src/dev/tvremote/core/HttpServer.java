package dev.tvremote.core;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A very small HTTP server (one request per connection, no TLS) for the pages the apps serve themselves.
 * It only knows how to read a request and write a reply; what the paths mean is up to the Handler.
 */
public final class HttpServer {
    public static final class Request {
        public String method, path;
        public final Map<String, String> query = new HashMap<>(), headers = new HashMap<>();
        /** The request body: read it as a stream (uploads), or call json() for a small JSON body. */
        public InputStream in;
        public long length;
        private byte[] small;

        public String q(String name) { String v = query.get(name); return v == null ? "" : v; }

        public JSONObject json() {
            try {
                if (small == null) {
                    if (length > 65536) return new JSONObject();
                    small = new byte[(int) length];
                    for (int off = 0; off < small.length; ) {
                        int n = in.read(small, off, small.length - off);
                        if (n < 0) break;
                        off += n;
                    }
                }
                return new JSONObject(new String(small, "UTF-8"));
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public String cookie(String name) {
            for (String part : String.valueOf(headers.get("cookie")).split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(name)) return kv[1];
            }
            return "";
        }
    }

    /** Writes a long reply (a file) straight to the browser. */
    public interface Body { void write(OutputStream out) throws IOException; }

    public static final class Response {
        public int status = 200;
        public String type = "application/json; charset=utf-8";
        public byte[] body = new byte[0];
        public Body stream;                                           // instead of body; length < 0 = unknown, sent until the connection closes
        public long length = -1;
        public final Map<String, String> headers = new HashMap<>();

        public static Response json(int status, JSONObject o) { Response r = new Response(); r.status = status; r.body = bytes(o.toString()); return r; }
        public static Response json(JSONObject o) { return json(200, o); }
        public static Response ok() { return of("application/json; charset=utf-8", bytes("{\"ok\":true}")); }
        public static Response error(int status, String message) {
            try { return json(status, new JSONObject().put("error", message)); } catch (Exception e) { return new Response(); }
        }
        public static Response of(String type, byte[] body) { Response r = new Response(); r.type = type; r.body = body; return r; }
        public static Response stream(String type, long length, Body body) { Response r = new Response(); r.type = type; r.length = length; r.stream = body; return r; }
    }

    public interface Handler { Response handle(Request r) throws Exception; }

    private final Handler handler;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile ServerSocket server;

    public HttpServer(Handler handler) { this.handler = handler; }

    /** localOnly: reachable from this device alone (127.0.0.1); otherwise from every network the device is on. */
    public int start(int port, boolean localOnly) throws IOException {
        final ServerSocket s = new ServerSocket();
        s.setReuseAddress(true);
        s.bind(localOnly ? new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), port) : new InetSocketAddress(port));
        server = s;
        Thread t = new Thread(() -> {
            while (!s.isClosed()) {
                try {
                    final Socket c = s.accept();
                    pool.execute(() -> serve(c));
                } catch (IOException e) {
                    if (s.isClosed()) return;
                }
            }
        }, "http-accept");
        t.setDaemon(true);
        t.start();
        return s.getLocalPort();
    }

    public void stop() {
        ServerSocket s = server;
        server = null;
        try { if (s != null) s.close(); } catch (IOException ignored) { }
    }

    public boolean running() { return server != null; }

    private void serve(Socket c) {
        try (Socket sock = c) {
            sock.setSoTimeout(30000);
            Request r = read(sock.getInputStream());
            Response out;
            if (r == null) out = Response.error(400, "bad request");
            else {
                try { out = handler.handle(r); } catch (Exception e) { out = Response.error(500, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
            }
            long len = out.stream == null ? out.body.length : out.length;
            StringBuilder h = new StringBuilder("HTTP/1.1 ").append(out.status).append(out.status == 200 ? " OK" : " Error").append("\r\n")
                    .append("Content-Type: ").append(out.type).append("\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\n");
            if (len >= 0) h.append("Content-Length: ").append(len).append("\r\n");
            for (Map.Entry<String, String> e : out.headers.entrySet()) h.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            OutputStream o = sock.getOutputStream();
            o.write(bytes(h.append("\r\n").toString()));
            if (out.stream != null) out.stream.write(o); else o.write(out.body);
            o.flush();
        } catch (IOException ignored) {
            // the browser went away
        }
    }

    /** Request line and headers; the body stays in the stream for the handler. */
    private static Request read(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {                                           // until the blank line
            int b = in.read();
            if (b < 0 || head.size() > 16384) return null;
            head.write(b);
            state = (b == '\r' && (state == 0 || state == 2)) || (b == '\n' && (state == 1 || state == 3)) ? state + 1 : b == '\r' ? 1 : 0;
        }
        String[] lines = head.toString("ISO-8859-1").split("\r\n");
        String[] first = lines[0].split(" ");
        if (first.length < 2) return null;
        Request r = new Request();
        r.method = first[0];
        String target = first[1];
        int q = target.indexOf('?');
        r.path = q < 0 ? target : target.substring(0, q);
        if (q >= 0) for (String kv : target.substring(q + 1).split("&")) {
            String[] p = kv.split("=", 2);
            r.query.put(URLDecoder.decode(p[0], "UTF-8"), p.length > 1 ? URLDecoder.decode(p[1], "UTF-8") : "");
        }
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) r.headers.put(lines[i].substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT), lines[i].substring(colon + 1).trim());
        }
        try { r.length = Long.parseLong(String.valueOf(r.headers.get("content-length"))); } catch (NumberFormatException e) { r.length = 0; }
        if (r.length < 0) return null;
        r.in = in;
        return r;
    }

    public static byte[] bytes(String s) {
        try { return s.getBytes("UTF-8"); } catch (java.io.UnsupportedEncodingException e) { return new byte[0]; }
    }
}
