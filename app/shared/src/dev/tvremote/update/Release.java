package dev.tvremote.update;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Update check shared by the phone app and the TV app: asks GitHub for the latest release of this project and, if it is
 * newer than the running app, gives the download link of this app's APK. Nothing else is sent anywhere.
 */
public final class Release {
    public static final String REPO = "HoangTran0410/tv-remote";

    public final String version, url, notes;

    private Release(String version, String url, String notes) { this.version = version; this.url = url; this.notes = notes; }

    public interface Progress { void at(int percent); }

    /**
     * The latest release, if it carries a newer version of this app. Each app has its own version number: a release lists
     * them in a small file, versions.json ({"android": "2.1.0", "tv": "2.0.3"}), so an app is only offered an update when
     * it changed itself. Releases without that file (before 2.1) are compared by their tag. null when up to date.
     */
    public static Release newer(String current, String app, String asset) throws Exception {
        HttpURLConnection c = open("https://api.github.com/repos/" + REPO + "/releases/latest");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            if (c.getResponseCode() == 404) return null;              // no release published yet
            if (c.getResponseCode() != 200) throw new IOException("GitHub HTTP " + c.getResponseCode());
            JSONObject o = new JSONObject(text(c));
            String version = o.getString("tag_name").replaceFirst("^v", ""), apk = null, versions = null;
            JSONArray assets = o.getJSONArray("assets");
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (asset.equals(a.getString("name"))) apk = a.getString("browser_download_url");
                if ("versions.json".equals(a.getString("name"))) versions = a.getString("browser_download_url");
            }
            if (apk == null) return null;
            if (versions != null) {
                HttpURLConnection v = open(versions);
                try {
                    if (v.getResponseCode() != 200) throw new IOException("GitHub HTTP " + v.getResponseCode());
                    version = new JSONObject(text(v)).optString(app, version);
                } finally {
                    v.disconnect();
                }
            }
            return compare(version, current) > 0 ? new Release(version, apk, o.optString("body", "").trim()) : null;
        } finally {
            c.disconnect();
        }
    }

    private static String text(HttpURLConnection c) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (InputStream in = c.getInputStream()) { copy(in, buf, 0, null); }
        return buf.toString("UTF-8");
    }

    public static void download(String url, File to, Progress progress) throws IOException {
        HttpURLConnection c = open(url);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(to)) { copy(in, out, c.getContentLength(), progress); }
        } finally {
            c.disconnect();
        }
    }

    /** "1.10.0" is newer than "1.9": compares the numbers between the dots. */
    public static int compare(String a, String b) {
        String[] x = a.trim().split("\\."), y = b.trim().split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int d = Integer.compare(num(x, i), num(y, i));
            if (d != 0) return d;
        }
        return 0;
    }

    private static int num(String[] parts, int i) {
        try { return i < parts.length ? Integer.parseInt(parts[i].replaceAll("\\D.*", "")) : 0; } catch (NumberFormatException e) { return 0; }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "tv-remote");
        return c;
    }

    private static void copy(InputStream in, OutputStream out, int total, Progress progress) throws IOException {
        byte[] b = new byte[65536];
        long done = 0;
        int last = -1;
        for (int n; (n = in.read(b)) > 0; ) {
            out.write(b, 0, n);
            done += n;
            int pct = total > 0 ? (int) (done * 100 / total) : 0;
            if (progress != null && pct != last) progress.at(last = pct);
        }
    }
}
