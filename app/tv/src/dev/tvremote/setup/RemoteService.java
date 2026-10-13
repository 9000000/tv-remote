package dev.tvremote.setup;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.tvremote.core.AdbLink;
import dev.tvremote.core.HttpServer;
import dev.tvremote.core.Manage;
import dev.tvremote.core.Tv;

/**
 * The web remote that lives on the TV: an HTTP server on port 8765 serving one page, and an input session to the TV's
 * own ADB (scrcpy-server without picture) that turns the page's button presses into key events.
 * A browser must be paired first with a code this service shows on the TV; after that it holds a cookie.
 */
public final class RemoteService extends Service implements Tv.Listener, HttpServer.Handler {
    public static final int PORT = 8765;
    private static final String PREFS = "remote", COOKIE = "tvr";
    private static final Map<String, Integer> KEYS = new HashMap<>();
    static {
        Object[] k = {"UP", 19, "DOWN", 20, "LEFT", 21, "RIGHT", 22, "OK", 23, "BACK", 4, "HOME", 3, "MENU", 82, "POWER", 26, "WAKE", 224,
                "VOL_UP", 24, "VOL_DOWN", 25, "MUTE", 164, "PLAY_PAUSE", 85, "NEXT", 87, "PREV", 88, "SETTINGS", 176, "INPUT", 178,
                "DEL", 67, "ENTER", 66, "ASSIST", 219, "CH_UP", 166, "CH_DOWN", 167};
        for (int i = 0; i < k.length; i += 2) KEYS.put((String) k[i], (Integer) k[i + 1]);
        for (int d = 0; d <= 9; d++) KEYS.put(String.valueOf(d), 7 + d);
    }

    // pairing: one code at a time, shown on the TV, good for two minutes and five guesses
    private static volatile String code;
    private static volatile long codeUntil;
    private static volatile int guesses;
    private static volatile RemoteService running;
    private static volatile String adbState = "connecting";

    static String pairingCode() { return code != null && System.currentTimeMillis() < codeUntil ? code : null; }
    static void cancelPairing() { code = null; }
    static boolean isRunning() { return running != null; }
    /** connecting | allow | ready | error: the state of the input session, for the app's own screen. */
    static String adbState() { return adbState; }

    static boolean enabled(Context c) { return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("on", true); }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("on", on).apply();
        if (on) start(c); else c.stopService(new Intent(c, RemoteService.class));
    }

    static void start(Context c) {
        Intent i = new Intent(c, RemoteService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    static int pairedCount(Context c) { return c.getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet("tokens", new HashSet<String>()).size(); }

    static void forgetAll(Context c) { c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove("tokens").apply(); }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SecureRandom random = new SecureRandom();
    private final Map<String, byte[]> icons = new ConcurrentHashMap<>();
    private final Map<Integer, Runnable> held = new HashMap<>();
    private HttpServer http;
    private AdbLink link;                                             // for apps, files, installs and health; the keys go through `tv`
    private Manage manage;
    private SharedPreferences prefs;
    private volatile Tv tv;
    private volatile boolean stopping;
    private long lastPairStart;
    private byte[] page;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        running = this;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("remote", "TV Remote", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        Notification.Builder n = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "remote") : new Notification.Builder(this);
        startForeground(1, n.setSmallIcon(R.drawable.ic_power).setContentTitle("TV Remote").setContentText("Web remote: port " + PORT).setOngoing(true).build());
        http = new HttpServer(this);
        link = new AdbLink(getFilesDir(), "127.0.0.1");
        manage = new Manage(link, new Manage.Labels() {
            @Override public String label(String pkg) {
                try { return String.valueOf(getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0))); } catch (Exception e) { return null; }
            }
            @Override public boolean icons() { return true; }
        });
        try { http.start(PORT, false); } catch (Exception e) { adbState = "error"; }
        connect();
    }

    @Override public int onStartCommand(Intent i, int flags, int id) { return START_STICKY; }

    @Override public void onDestroy() {
        stopping = true;
        running = null;
        code = null;
        if (http != null) http.stop();
        if (link != null) link.close();
        Tv t = tv;
        if (t != null) t.stop();
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------------------------------------------------------------- the input session to the TV's own ADB
    private void connect() {
        if (stopping) return;
        adbState = "connecting";
        tv = new Tv(this, "127.0.0.1", null, 0, 0, false, this);
    }

    @Override public void onStatus(String key, String detail) {
        if (stopping) return;
        switch (key) {
            case "allow": adbState = "allow"; break;                  // the TV is asking "Allow USB debugging?"
            case "live": adbState = "ready"; break;
            case "closed": case "error":                              // ADB off, TV just booted, session dropped: keep trying
                adbState = "error".equals(key) ? "error" : "connecting";
                tv = null;
                ui.postDelayed(this::connect, 5000);
                break;
            default:
        }
    }

    @Override public void onVideoSize(int w, int h) { }
    @Override public void onNetwork(String mac, String lan) { }

    // ---------------------------------------------------------------- HTTP
    @Override public HttpServer.Response handle(HttpServer.Request r) throws Exception {
        // a web page elsewhere must not be able to talk to us through the visitor's browser (DNS rebinding): only IPs are served
        String host = String.valueOf(r.headers.get("host")).replaceFirst(":\\d+$", "");
        if (!host.matches("[0-9.]+|localhost|\\[[0-9a-fA-F:]+\\]")) return HttpServer.Response.error(403, "open this page by the TV's IP address");

        if (r.path.equals("/") || r.path.equals("/index.html")) {
            HttpServer.Response page = HttpServer.Response.of("text/html; charset=utf-8", page());
            page.headers.put("Cache-Control", "no-store");
            return page;
        }
        if (r.path.equals("/api/pair/start") && r.method.equals("POST")) return pairStart();
        if (r.path.equals("/api/pair/finish") && r.method.equals("POST")) return pairFinish(r);
        if (!prefs.getStringSet("tokens", new HashSet<String>()).contains(r.cookie(COOKIE)) || r.cookie(COOKIE).isEmpty())
            return HttpServer.Response.json(401, new JSONObject().put("error", "not paired").put("name", Build.MODEL));

        boolean post = r.method.equals("POST");
        switch (r.path) {
            case "/api/state": return HttpServer.Response.json(state());
            case "/api/apps": return HttpServer.Response.json(new JSONObject().put("apps", apps()));
            case "/api/icon": {
                byte[] png = icon(String.valueOf(r.query.get("pkg")));
                if (png == null) return HttpServer.Response.error(404, "no icon");
                HttpServer.Response icon = HttpServer.Response.of("image/png", png);
                icon.headers.put("Cache-Control", "max-age=86400");
                return icon;
            }
            default:
        }
        HttpServer.Response managed = manage.handle(r);
        if (managed != null) return managed;
        if (!post) return HttpServer.Response.error(404, "not found");
        Tv t = tv;
        JSONObject d = r.json();
        switch (r.path) {
            case "/api/volume": {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(0, Math.min(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), d.optInt("level"))), AudioManager.FLAG_SHOW_UI);
                return HttpServer.Response.json(state());
            }
            case "/api/key": {
                Integer keyCode = KEYS.get(d.optString("key").toUpperCase(java.util.Locale.ROOT));
                if (keyCode == null) return HttpServer.Response.error(400, "unknown key");
                if (t == null || !t.ready()) return HttpServer.Response.error(503, "adb:" + adbState);
                key(t, keyCode, d.optString("action", "press"));
                return ok();
            }
            case "/api/text":
                if (t == null || !t.ready()) return HttpServer.Response.error(503, "adb:" + adbState);
                if (!d.optString("text").isEmpty()) t.text(d.optString("text"));
                return ok();
            case "/api/launch":
                if (t == null || !t.ready()) return HttpServer.Response.error(503, "adb:" + adbState);
                t.launch(d.optString("component"));
                return ok();
            default: return HttpServer.Response.error(404, "not found");
        }
    }

    private static HttpServer.Response ok() { return HttpServer.Response.ok(); }

    /**
     * down / up are separate so holding a button in the browser is a long press on the TV. While it is held the key repeats
     * (fast scrolling, volume), and a lost "up" is sent for it after 8 s.
     */
    private void key(final Tv t, final int keyCode, String action) {
        synchronized (held) {
            Runnable old = held.remove(keyCode);
            if (old != null) ui.removeCallbacks(old);
            if (action.equals("down")) {
                t.key(0, keyCode, 0, 0);
                final long until = System.currentTimeMillis() + 8000;
                Runnable repeat = new Runnable() {
                    int count;
                    @Override public void run() {
                        synchronized (held) {
                            if (held.get(keyCode) != this) return;
                            if (System.currentTimeMillis() > until) { held.remove(keyCode); t.key(1, keyCode, 0, 0); return; }
                            t.key(0, keyCode, ++count, 0);
                            ui.postDelayed(this, 80);
                        }
                    }
                };
                held.put(keyCode, repeat);
                ui.postDelayed(repeat, 450);
            } else if (action.equals("up")) {
                t.key(1, keyCode, 0, 0);
            } else {
                if (old != null) t.key(1, keyCode, 0, 0);
                t.press(keyCode);
            }
        }
    }

    private JSONObject state() throws Exception {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        String version = "";
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        return new JSONObject().put("name", Build.MODEL).put("maker", Build.MANUFACTURER).put("android", Build.VERSION.RELEASE).put("version", version)
                .put("adb", adbState).put("on", pm.isInteractive())
                .put("volume", am.getStreamVolume(AudioManager.STREAM_MUSIC)).put("volume_max", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                .put("muted", Build.VERSION.SDK_INT >= 23 && am.isStreamMute(AudioManager.STREAM_MUSIC));
    }

    // ---------------------------------------------------------------- pairing
    private synchronized HttpServer.Response pairStart() throws Exception {
        long now = System.currentTimeMillis();
        if (now - lastPairStart < 3000) return HttpServer.Response.error(429, "wait a moment");
        lastPairStart = now;
        code = String.format("%06d", random.nextInt(1000000));
        codeUntil = now + 120000;
        guesses = 0;
        // Since Android 10 a service may not open a screen by itself; the shell user (our ADB session) still may.
        Tv t = tv;
        boolean shown = false;
        if (t != null && t.ready()) {
            try { t.shell("am start -n " + getPackageName() + "/.PairActivity"); shown = true; } catch (Exception ignored) { }
        }
        if (!shown) {
            try { startActivity(new Intent(this, PairActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
        }
        return HttpServer.Response.json(new JSONObject().put("ok", true).put("seconds", 120));
    }

    private synchronized HttpServer.Response pairFinish(HttpServer.Request r) throws Exception {
        String want = pairingCode(), got = r.json().optString("code").replaceAll("\\D", "");
        if (want == null) return HttpServer.Response.error(400, "expired");
        if (!want.equals(got)) {
            if (++guesses >= 5) code = null;
            return HttpServer.Response.error(400, code == null ? "expired" : "wrong");
        }
        code = null;
        byte[] raw = new byte[24];
        random.nextBytes(raw);
        StringBuilder token = new StringBuilder();
        for (byte b : raw) token.append(String.format("%02x", b));
        Set<String> tokens = new HashSet<>(prefs.getStringSet("tokens", new HashSet<String>()));
        tokens.add(token.toString());
        prefs.edit().putStringSet("tokens", tokens).apply();
        HttpServer.Response ok = ok();
        ok.headers.put("Set-Cookie", COOKIE + "=" + token + "; Max-Age=315360000; Path=/; HttpOnly; SameSite=Strict");
        return ok;
    }

    // ---------------------------------------------------------------- apps on the TV's home screen
    private JSONArray apps() throws Exception {
        PackageManager pm = getPackageManager();
        JSONArray out = new JSONArray();
        Set<String> seen = new HashSet<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (!seen.add(pkg) || pkg.equals(getPackageName())) continue;
                out.put(new JSONObject().put("pkg", pkg).put("component", pkg + "/" + ri.activityInfo.name).put("label", String.valueOf(ri.loadLabel(pm))));
            }
        }
        return out;
    }

    private byte[] icon(String pkg) {
        byte[] png = icons.get(pkg);
        if (png != null) return png;
        try {
            PackageManager pm = getPackageManager();
            Drawable d = null;
            try { d = pm.getApplicationBanner(pkg); } catch (Exception ignored) { }   // TV apps carry a wide banner; fall back to the icon
            boolean banner = d != null;
            if (d == null) d = pm.getApplicationIcon(pkg);
            Bitmap bmp = Bitmap.createBitmap(banner ? 320 : 144, banner ? 180 : 144, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, bmp.getWidth(), bmp.getHeight());
            d.draw(new Canvas(bmp));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            png = out.toByteArray();
            if (icons.size() < 200) icons.put(pkg, png);
            return png;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] page() throws Exception {
        if (page == null) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (InputStream in = getAssets().open("index.html")) {
                byte[] b = new byte[65536];
                for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
            }
            page = buf.toByteArray();
        }
        return page;
    }
}
