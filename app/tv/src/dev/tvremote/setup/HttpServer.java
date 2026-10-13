package dev.tvremote.setup;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A very small HTTP server (one request per connection, no TLS) for the remote page served by the TV itself.
 * It only knows how to read a request and write a reply; what the paths mean is up to the Handler.
 */
final class HttpServer {
    static final class Request {
        String method, path;
        final Map<String, String> query = new HashMap<>(), headers = new HashMap<>();
        byte[] body = new byte[0];

        JSONObject json() {
            try { return new JSONObject(new String(body, "UTF-8")); } catch (Exception e) { return new JSONObject(); }
        }

        String cookie(String name) {
            for (String part : String.valueOf(headers.get("cookie")).split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(name)) return kv[1];
            }
            return "";
        }
    }

    static final class Response {
        int status = 200;
        String type = "application/json; charset=utf-8";
        byte[] body = new byte[0];
        final Map<String, String> headers = new HashMap<>();

        static Response json(int status, JSONObject o) { Response r = new Response(); r.status = status; r.body = bytes(o.toString()); return r; }
        static Response json(JSONObject o) { return json(200, o); }
        static Response error(int status, String message) {
            try { return json(status, new JSONObject().put("error", message)); } catch (Exception e) { return new Response(); }
        }
        static Response of(String type, byte[] body) { Response r = new Response(); r.type = type; r.body = body; return r; }
    }

    interface Handler { Response handle(Request r) throws Exception; }

    private final Handler handler;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile ServerSocket server;

    HttpServer(Handler handler) { this.handler = handler; }

    void start(int port) throws IOException {
        ServerSocket s = new ServerSocket();
        s.setReuseAddress(true);
        s.bind(new InetSocketAddress(port));                          // every interface: home Wi-Fi and Tailscale
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
    }

    void stop() {
        ServerSocket s = server;
        server = null;
        try { if (s != null) s.close(); } catch (IOException ignored) { }
    }

    boolean running() { return server != null; }

    private void serve(Socket c) {
        try (Socket sock = c) {
            sock.setSoTimeout(15000);
            InputStream in = sock.getInputStream();
            Request r = read(in);
            Response out;
            if (r == null) out = Response.error(400, "bad request");
            else {
                try { out = handler.handle(r); } catch (Exception e) { out = Response.error(500, e.getClass().getSimpleName() + ": " + e.getMessage()); }
            }
            StringBuilder h = new StringBuilder("HTTP/1.1 ").append(out.status).append(out.status == 200 ? " OK" : " Error").append("\r\n")
                    .append("Content-Type: ").append(out.type).append("\r\nContent-Length: ").append(out.body.length)
                    .append("\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\n");
            for (Map.Entry<String, String> e : out.headers.entrySet()) h.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
            OutputStream o = sock.getOutputStream();
            o.write(bytes(h.append("\r\n").toString()));
            o.write(out.body);
            o.flush();
        } catch (IOException ignored) {
            // the browser went away
        }
    }

    /** Request line, headers, and a body of at most 64 KB. */
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
        int len = 0;
        try { len = Integer.parseInt(String.valueOf(r.headers.get("content-length"))); } catch (NumberFormatException ignored) { }
        if (len < 0 || len > 65536) return null;
        r.body = new byte[len];
        for (int off = 0; off < len; ) {
            int n = in.read(r.body, off, len - off);
            if (n < 0) return null;
            off += n;
        }
        return r;
    }

    static byte[] bytes(String s) {
        try { return s.getBytes("UTF-8"); } catch (java.io.UnsupportedEncodingException e) { return new byte[0]; }
    }
}
