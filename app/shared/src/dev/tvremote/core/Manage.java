package dev.tvremote.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Managing a TV over ADB for the page both apps serve: installed apps, files, APK installs and a health snapshot.
 * Everything under /api/m/. Whoever owns the HttpServer checks first that the caller may use it.
 */
public final class Manage {
    /** Names and icons for packages, where the app can ask the system for them (the TV app); otherwise package names are shown. */
    public interface Labels {
        String label(String pkg);
        boolean icons();
    }

    private static final Pattern PKG = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    private static final Pattern LS = Pattern.compile("^([dl-])\\S+\\s+\\d+\\s+\\S+\\s+\\S+\\s+(\\d+)\\s+(\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d)\\s+(.+)$");
    private static final String TMP_APK = "/data/local/tmp/tvremote-install.apk";

    private final AdbLink link;
    private final Labels labels;
    private final Map<String, JSONObject> jobs = Collections.synchronizedMap(new LinkedHashMap<String, JSONObject>());
    private int nextJob = 1;

    public Manage(AdbLink link, Labels labels) { this.link = link; this.labels = labels; }

    /** null when the path is not ours. */
    public HttpServer.Response handle(HttpServer.Request r) throws Exception {
        if (!r.path.startsWith("/api/m/")) return null;
        String what = r.path.substring(7);
        boolean post = r.method.equals("POST");
        try {
            switch (what) {
                case "apps": return HttpServer.Response.json(apps(r.q("system").equals("1")));
                case "files": return HttpServer.Response.json(files(r.q("path")));
                case "file": return download(r);
                case "health": return HttpServer.Response.json(health());
                case "job": {
                    JSONObject j = jobs.get(r.q("id"));
                    return j == null ? HttpServer.Response.error(404, "no such job") : HttpServer.Response.json(new JSONObject(j.toString()));
                }
                default:
            }
            if (!post) return HttpServer.Response.error(404, "not found");
            switch (what) {
                case "app": return app(r.json());
                case "shell": return shell(r.json().optString("cmd"));
                case "search": {
                    String q = r.json().optString("query").trim();
                    if (q.isEmpty()) throw new IllegalArgumentException("empty query");
                    if (sh("am start -a android.search.action.GLOBAL_SEARCH --es query " + Adb.q(q) + " 2>&1").contains("Error")) throw new IllegalArgumentException("this TV has no Google search / Assistant");
                    return HttpServer.Response.ok();
                }
                case "upload": return upload(r);
                case "delete": return fileOp("delete", r.json());
                case "rename": return fileOp("rename", r.json());
                case "mkdir": return fileOp("mkdir", r.json());
                case "open": return fileOp("open", r.json());
                case "install": return HttpServer.Response.json(installPath(safePath(r.json().optString("path"))));
                case "install-upload": return installUpload(r);
                case "fetch": return HttpServer.Response.json(fetch(r.json().optString("url"), r.json().optBoolean("install")));
                default: return HttpServer.Response.error(404, "not found");
            }
        } catch (IllegalArgumentException e) {
            return HttpServer.Response.error(400, e.getMessage());
        } catch (IOException e) {
            return HttpServer.Response.error(503, link.waitingForUser() ? "adb:allow" : "adb: " + e.getMessage());
        }
    }

    private String sh(String cmd) throws IOException { return link.get().shell(cmd); }

    // ---------------------------------------------------------------- apps
    private JSONObject apps(boolean system) throws Exception {
        List<String> pkgs = new ArrayList<>();
        for (String line : sh("pm list packages" + (system ? "" : " -3")).split("\n"))
            if (line.startsWith("package:")) pkgs.add(line.substring(8).trim());
        Collections.sort(pkgs);
        JSONArray out = new JSONArray();
        for (String p : pkgs) {
            String label = labels == null ? null : labels.label(p);
            out.put(new JSONObject().put("pkg", p).put("label", label == null ? p.substring(p.lastIndexOf('.') + 1) : label));
        }
        return new JSONObject().put("apps", out).put("icons", labels != null && labels.icons());
    }

    private HttpServer.Response app(JSONObject d) throws Exception {
        String pkg = d.optString("pkg"), action = d.optString("action");
        if (!PKG.matcher(pkg).matches()) throw new IllegalArgumentException("bad package name");
        switch (action) {
            case "launch": {
                for (String cat : new String[]{"LEANBACK_LAUNCHER", "LAUNCHER"}) {   // a TV app's own screen first, then a phone-style one
                    String[] lines = sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category." + cat + " " + pkg + " 2>/dev/null").trim().split("\n");
                    String component = lines[lines.length - 1].trim();
                    if (!component.matches("[\\w.]+/[\\w.$]+") || !component.startsWith(pkg + "/")) continue;
                    String out = sh("am start -n " + Adb.q(component) + " 2>&1");
                    if (!out.contains("Error")) return HttpServer.Response.ok();
                }
                throw new IllegalArgumentException("this app cannot be opened (no launcher screen)");
            }
            case "stop": sh("am force-stop " + pkg); return HttpServer.Response.ok();
            case "uninstall": {
                String out = sh("pm uninstall " + pkg + " 2>&1").trim();
                if (!out.contains("Success")) throw new IllegalArgumentException(out.isEmpty() ? "uninstall failed" : out);
                return HttpServer.Response.ok();
            }
            default: throw new IllegalArgumentException("unknown action");
        }
    }

    // ---------------------------------------------------------------- terminal
    /** Runs one command as the TV's shell user and returns what it printed (at most 30 s and 200 KB). */
    private HttpServer.Response shell(String cmd) throws Exception {
        if (cmd.trim().isEmpty()) throw new IllegalArgumentException("empty command");
        final Adb.Stream s = link.get().open("shell:" + cmd + " 2>&1");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[16384];
        long until = System.currentTimeMillis() + 30000;
        boolean cut = false;
        try {
            while (true) {
                long left = until - System.currentTimeMillis();
                if (left <= 0 || out.size() > 200000) { cut = true; break; }
                int n = s.read(b, 0, b.length, left);
                if (n < 0) break;
                out.write(b, 0, n);
            }
        } finally {
            s.close();
        }
        return HttpServer.Response.json(new JSONObject().put("out", out.toString("UTF-8").replace("\r\n", "\n")).put("cut", cut));
    }

    // ---------------------------------------------------------------- files
    /** Only the shared storage and USB drives; no "..". */
    static String safePath(String p) {
        StringBuilder b = new StringBuilder();
        for (String part : (p == null || p.isEmpty() ? "/sdcard/Download" : p).split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) throw new IllegalArgumentException("bad path");
            b.append('/').append(part);
        }
        String s = b.toString();
        if (!(s.equals("/sdcard") || s.equals("/storage") || s.startsWith("/sdcard/") || s.startsWith("/storage/"))) throw new IllegalArgumentException("path must be under /sdcard or /storage");
        return s;
    }

    static String safeName(String n) {
        String s = (n == null ? "" : n).replaceAll(".*[/\\\\]", "").replaceAll("[^\\p{L}\\p{N}_.\\- ()\\[\\]]", "_").replaceAll("^[. ]+|[. ]+$", "");
        return s.isEmpty() ? "file" : s.length() > 120 ? s.substring(0, 120) : s;
    }

    private static boolean root(String path) { return path.equals("/sdcard") || path.equals("/storage") || path.startsWith("/storage/") && path.split("/").length < 4; }

    private JSONObject files(String raw) throws Exception {
        String path = safePath(raw), q = Adb.q(path + "/");            // the trailing / follows the /sdcard symlink
        String[] part = sh("ls -lA " + q + " 2>&1 | cat; echo '#--'; df -k " + q + " 2>/dev/null | tail -1; echo '#--'; ls /storage 2>/dev/null").split("#--", -1);
        List<JSONObject> items = new ArrayList<>();
        for (String line : part[0].split("\n")) {
            Matcher m = LS.matcher(line.trim());
            if (m.matches()) {
                String name = m.group(4).split(" -> ")[0];
                items.add(new JSONObject().put("name", name).put("dir", !m.group(1).equals("-")).put("size", Long.parseLong(m.group(2))).put("mtime", m.group(3)));
            } else if (line.contains("No such file") || line.contains("Permission denied")) throw new IllegalArgumentException(line.trim());
        }
        Collections.sort(items, (a, b) -> a.optBoolean("dir") != b.optBoolean("dir") ? (a.optBoolean("dir") ? -1 : 1)
                : a.optString("name").toLowerCase(Locale.ROOT).compareTo(b.optString("name").toLowerCase(Locale.ROOT)));
        JSONObject out = new JSONObject().put("path", path).put("items", new JSONArray(items));
        String[] df = part.length > 1 ? part[1].trim().split("\\s+") : new String[0];
        if (df.length > 3 && df[1].matches("\\d+") && df[3].matches("\\d+")) out.put("total", Long.parseLong(df[1]) * 1024).put("free", Long.parseLong(df[3]) * 1024);
        JSONArray usb = new JSONArray();
        if (part.length > 2) for (String v : part[2].trim().split("\\s+"))
            if (!v.isEmpty() && !v.equals("emulated") && !v.equals("self") && !v.startsWith("sdcard")) usb.put(v);
        return out.put("usb", usb);
    }

    private HttpServer.Response fileOp(String op, JSONObject d) throws Exception {
        String path = safePath(d.optString("path")), out;
        switch (op) {
            case "delete":
                if (root(path)) throw new IllegalArgumentException("refusing to delete a storage root");
                out = sh("rm -rf " + Adb.q(path) + " 2>&1");
                break;
            case "mkdir":
                out = sh("mkdir " + Adb.q(path + "/" + safeName(d.optString("name"))) + " 2>&1");
                break;
            case "rename": {
                String name = d.optString("name");
                if (root(path) || !name.equals(safeName(name))) throw new IllegalArgumentException("bad name");
                String dst = Adb.q(path.substring(0, path.lastIndexOf('/')) + "/" + name);
                out = sh("if [ -e " + dst + " ]; then echo 'a file with that name already exists'; else mv " + Adb.q(path) + " " + dst + " 2>&1; fi");
                break;
            }
            default: {                                                // open on the TV with whatever app handles the type
                String real = path.startsWith("/sdcard") ? "/storage/emulated/0" + path.substring(7) : path;
                out = sh("am start -a android.intent.action.VIEW -d " + Adb.q("file://" + real.replace(" ", "%20")) + " -t " + Adb.q(mime(path)) + " 2>&1");
                out = out.contains("Error") || out.toLowerCase(Locale.ROOT).contains("unable to resolve") ? "the TV has no app that can open this" : "";
            }
        }
        if (!out.trim().isEmpty()) throw new IllegalArgumentException(out.trim());
        return HttpServer.Response.ok();
    }

    static String mime(String path) {
        String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        switch (ext) {
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png"; case "gif": return "image/gif"; case "webp": return "image/webp";
            case "mp4": case "m4v": return "video/mp4"; case "mkv": return "video/x-matroska"; case "webm": return "video/webm"; case "avi": return "video/x-msvideo"; case "mov": return "video/quicktime";
            case "mp3": return "audio/mpeg"; case "m4a": return "audio/mp4"; case "flac": return "audio/flac"; case "wav": return "audio/wav"; case "ogg": return "audio/ogg";
            case "apk": return "application/vnd.android.package-archive"; case "pdf": return "application/pdf"; case "txt": return "text/plain";
            default: return "*/*";
        }
    }

    private HttpServer.Response download(HttpServer.Request r) throws Exception {
        final String path = safePath(r.q("path"));
        Adb adb = link.get();
        String size = adb.shell("stat -c %s " + Adb.q(path) + " 2>/dev/null").trim();
        if (!size.matches("\\d+")) throw new IllegalArgumentException("cannot read this file");
        final Adb.Stream s = adb.exec("cat " + Adb.q(path));
        boolean inline = r.q("inline").equals("1");
        HttpServer.Response out = HttpServer.Response.stream(inline ? mime(path).replace("*/*", "application/octet-stream") : "application/octet-stream", Long.parseLong(size), o -> {
            try {
                byte[] b = new byte[65536];
                for (int n; (n = s.read(b, 0, b.length, 0)) >= 0; ) o.write(b, 0, n);
            } finally {
                s.close();
            }
        });
        if (!inline) out.headers.put("Content-Disposition", "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(path.substring(path.lastIndexOf('/') + 1), "UTF-8").replace("+", "%20"));
        return out;
    }

    /** Copies `length` bytes into a file on the TV. */
    private void push(InputStream in, long length, String dest, JSONObject job) throws IOException {
        Adb adb = link.get();
        Adb.Stream s = adb.exec("cat > " + Adb.q(dest));
        try {
            byte[] b = new byte[65536];
            long done = 0;
            while (length < 0 || done < length) {
                int n = in.read(b, 0, length < 0 ? b.length : (int) Math.min(b.length, length - done));
                if (n < 0) break;
                s.write(b, 0, n);
                done += n;
                if (job != null && length > 0) try { job.put("pct", (int) (done * 90 / length)); } catch (Exception ignored) { }
            }
            if (length >= 0 && done < length) throw new IOException("upload cut short");
        } finally {
            s.close();
        }
        String want = length >= 0 ? String.valueOf(length) : null;      // cat finishes writing shortly after the stream closes
        for (int i = 0; i < 40; i++) {
            String got = adb.shell("stat -c %s " + Adb.q(dest) + " 2>/dev/null").trim();
            if (want == null ? got.matches("\\d+") && i > 1 : got.equals(want)) return;
            try { Thread.sleep(150); } catch (InterruptedException e) { break; }
        }
        if (want != null) throw new IOException("the TV did not store the whole file (is its storage full?)");
    }

    private HttpServer.Response upload(HttpServer.Request r) throws Exception {
        String dest = safePath(r.q("dir")) + "/" + safeName(r.q("name"));
        push(r.in, r.length, dest, null);
        return HttpServer.Response.ok();
    }

    // ---------------------------------------------------------------- installs and downloads (jobs the page polls)
    private interface Work { String run(JSONObject job) throws Exception; }

    private JSONObject job(String title, final Work work) throws Exception {
        final JSONObject j = new JSONObject().put("id", String.valueOf(nextJob++)).put("title", title).put("state", "running").put("pct", 0).put("msg", "");
        jobs.put(j.getString("id"), j);
        while (jobs.size() > 20) jobs.remove(jobs.keySet().iterator().next());
        Thread t = new Thread(() -> {
            try {
                String msg = work.run(j);
                j.put("state", "done").put("pct", 100).put("msg", msg);
            } catch (Exception e) {
                try { j.put("state", "error").put("msg", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); } catch (Exception ignored) { }
            }
        }, "job");
        t.setDaemon(true);
        t.start();
        return new JSONObject(j.toString());
    }

    private String pmInstall(String path) throws IOException {
        Adb adb = link.get();
        String q = Adb.q(path);
        if (!adb.shell("head -c 2 " + q + " 2>/dev/null").startsWith("PK")) throw new IOException("not an APK file");
        String size = adb.shell("stat -c %s " + q).trim();
        String out = adb.shell("cat " + q + " | pm install -r -S " + size + " 2>&1");
        if (!out.contains("Success")) throw new IOException(out.trim().isEmpty() ? "install failed" : out.trim());
        return "installed";
    }

    private JSONObject installPath(final String path) throws Exception {
        return job("install " + path.substring(path.lastIndexOf('/') + 1), j -> { j.put("pct", 50); return pmInstall(path); });
    }

    /** The APK arrives in the request body: it has to be read before we answer, so this one is not a background job. */
    private HttpServer.Response installUpload(HttpServer.Request r) throws Exception {
        if (r.length <= 0) throw new IllegalArgumentException("empty upload");
        push(r.in, r.length, TMP_APK, null);
        try {
            pmInstall(TMP_APK);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage());
        } finally {
            sh("rm -f " + TMP_APK);
        }
        return HttpServer.Response.ok();
    }

    /** This device downloads the link and passes it on to the TV, into Download or straight into the installer. */
    private JSONObject fetch(final String url, final boolean install) throws Exception {
        if (!url.matches("(?i)https?://\\S+")) throw new IllegalArgumentException("only http/https links");
        return job((install ? "install " : "download ") + (url.length() > 60 ? url.substring(0, 60) : url), j -> {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "tv-remote");
            try {
                if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
                long len = c.getContentLength() >= 0 ? c.getContentLength() : -1;
                String name = null, cd = c.getHeaderField("Content-Disposition");
                Matcher m = cd == null ? null : Pattern.compile("filename\\*?=(?:UTF-8'')?\"?([^\";]+)").matcher(cd);
                if (m != null && m.find()) name = URLDecoder.decode(m.group(1), "UTF-8");
                if (name == null) { String p = c.getURL().getPath(); name = URLDecoder.decode(p.substring(p.lastIndexOf('/') + 1), "UTF-8"); }
                name = safeName(name);
                String dest = install ? TMP_APK : "/sdcard/Download/" + name;
                j.put("msg", name);
                try (InputStream in = c.getInputStream()) { push(in, len, dest, j); }
                if (!install) return "saved " + dest;
                try { return pmInstall(dest); } finally { sh("rm -f " + TMP_APK); }
            } finally {
                c.disconnect();
            }
        });
    }

    // ---------------------------------------------------------------- health
    /** The separator is printed from octal codes, so it does not appear in this command's own line in the `top` output. */
    private static final String HEALTH = "getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk; "
            + "getprop ro.build.version.security_patch; getprop ro.board.platform; getprop ro.product.cpu.abi; cat /sys/devices/system/cpu/present; "
            + "printf '\\043~\\043\\n'; head -1 /proc/stat; sleep 0.6; head -1 /proc/stat; printf '\\043~\\043\\n'; grep -E '^(MemTotal|MemAvailable):' /proc/meminfo; printf '\\043~\\043\\n'; df -k /data | tail -1; "
            + "printf '\\043~\\043\\n'; cat /proc/uptime; printf '\\043~\\043\\n'; wm size; wm density; dumpsys display | grep -m1 -o 'fps=[0-9.]*'; printf '\\043~\\043\\n'; dumpsys wifi | grep -m1 mWifiInfo; "
            + "printf '\\043~\\043\\n'; ip -o -4 addr show; printf '\\043~\\043\\n'; top -n 1 -b -m 7 -o %CPU,%MEM,ARGS -s 1 | tail -7; "
            + "printf '\\043~\\043\\n'; dumpsys power | grep -m1 -o 'mWakefulness=[A-Za-z]*'; dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity'";

    private static String find(String regex, String in) { Matcher m = Pattern.compile(regex).matcher(in); return m.find() ? m.group(1) : null; }

    private static Object num(String s) { return s == null ? JSONObject.NULL : (Object) Long.valueOf(s); }

    private JSONObject health() throws Exception {
        Adb adb = link.get();
        long t0 = System.currentTimeMillis();
        adb.shell(":");
        long ping = System.currentTimeMillis() - t0;
        String[] part = (adb.shell(HEALTH) + "#~##~##~##~##~##~##~##~##~#").split("#~#", -1);
        String[] props = (part[0].trim() + "\n\n\n\n\n\n\n\n").split("\n", -1);
        JSONObject o = new JSONObject().put("ping_ms", ping).put("maker", props[0].trim()).put("model", props[1].trim()).put("android", props[2].trim()).put("sdk", props[3].trim())
                .put("patch", props[4].trim()).put("chip", props[5].trim()).put("abi", props[6].trim());
        String cores = find("(\\d+)\\s*$", props[7].trim());
        o.put("cores", cores == null ? JSONObject.NULL : (Object) (Integer.parseInt(cores) + 1));
        List<long[]> stat = new ArrayList<>();
        for (String line : part[1].trim().split("\n")) {
            String[] f = line.trim().split("\\s+");
            if (!f[0].equals("cpu") || f.length < 6) continue;
            long total = 0;
            for (int i = 1; i < f.length; i++) total += Long.parseLong(f[i]);
            stat.add(new long[]{total, Long.parseLong(f[4]) + Long.parseLong(f[5])});
        }
        if (stat.size() == 2 && stat.get(1)[0] > stat.get(0)[0]) {
            long total = stat.get(1)[0] - stat.get(0)[0], idle = stat.get(1)[1] - stat.get(0)[1];
            o.put("cpu", Math.round(100.0 * (total - idle) / total));
        }
        String memTotal = find("MemTotal:\\s+(\\d+)", part[2]), memFree = find("MemAvailable:\\s+(\\d+)", part[2]);
        if (memTotal != null) o.put("mem_total", Long.parseLong(memTotal) * 1024);
        if (memFree != null) o.put("mem_free", Long.parseLong(memFree) * 1024);
        String[] df = part[3].trim().split("\\s+");
        if (df.length > 3 && df[1].matches("\\d+") && df[3].matches("\\d+")) o.put("disk_total", Long.parseLong(df[1]) * 1024).put("disk_free", Long.parseLong(df[3]) * 1024);
        String up = find("^\\s*([\\d.]+)", part[4]);
        if (up != null) o.put("uptime", Double.parseDouble(up));
        o.put("screen", String.valueOf(find("(\\d+x\\d+)", part[5])).replace("null", "")).put("density", num(find("density: (\\d+)", part[5])));
        String fps = find("fps=([\\d.]+)", part[5]);
        if (fps != null) o.put("fps", Math.round(Double.parseDouble(fps)));
        String ssid = find("SSID: ([^,]*),", part[6]);
        if (ssid != null && !ssid.trim().isEmpty() && !ssid.contains("unknown ssid"))
            o.put("wifi", new JSONObject().put("ssid", ssid.trim().replaceAll("^\"|\"$", "")).put("rssi", num(find("RSSI: (-?\\d+)", part[6]))).put("speed", num(find("Link speed: (\\d+)", part[6]))).put("freq", num(find("Frequency: (\\d+)", part[6]))));
        JSONArray ips = new JSONArray();
        Matcher m = Pattern.compile("(?m)^\\d+: (\\S+)\\s+inet ([\\d.]+)").matcher(part[7]);
        while (m.find()) if (!m.group(1).equals("lo")) ips.put(new JSONObject().put("if", m.group(1)).put("ip", m.group(2)));
        o.put("ips", ips);
        JSONArray top = new JSONArray();
        for (String line : part[8].split("\n")) {
            Matcher t = Pattern.compile("\\s*([\\d.]+)\\s+([\\d.]+)\\s+(.+)").matcher(line);
            if (!t.matches() || t.group(3).matches("(top -n|tail |sh -c).*") || top.length() >= 5) continue;
            String name = t.group(3).trim().split("\\s+")[0].replaceAll("^\\[|\\]$", "");
            String label = labels != null && PKG.matcher(name).matches() ? labels.label(name) : null;
            top.put(new JSONObject().put("cpu", Double.parseDouble(t.group(1))).put("mem", Double.parseDouble(t.group(2))).put("name", label == null ? name : label));
        }
        o.put("top", top);
        String awake = find("mWakefulness=(\\w+)", part[9]), app = find("u\\d+ ([\\w.]+)/", part[9]);
        o.put("on", awake == null ? JSONObject.NULL : (Object) awake.equals("Awake"));
        if (app != null) { String label = labels == null ? null : labels.label(app); o.put("app", label == null ? app : label); }
        return o;
    }
}
