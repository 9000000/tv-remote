package dev.tvremote.setup;

import dev.tvremote.core.Adb;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/** The same steps as deploy/tv_setup.py, run from the TV itself through its own ADB shell. */
final class Setup {
    static final String FL_PKG = "me.efesser.flauncher";
    static final String FL_URL = "https://github.com/osrosal/flauncher/releases/download/v2025.07.001/flauncher-armeabi-v7a-release.apk";
    static final String FL_SHA = "467fd0befe6daa3993a8a34a3981a631858ace28b38d9836d691478af8b71e40";
    static final String TS_PKG = "com.tailscale.ipn";
    static final String[] GOOGLE = {"com.google.android.play.games", "com.google.android.videos", "com.google.android.youtube.tvmusic"};
    /** Only present on Sony BRAVIA sets; skipped elsewhere. */
    static final String[] SONY = {
            "com.sony.dtv.b2b.vendorprotocol", "com.sony.dtv.b2b.rs232csupport", "com.sony.dtv.b2b.hotelmode", "com.sony.dtv.b2b.prosettings",
            "com.sony.dtv.b2b.importexport", "com.sony.dtv.b2b.noderuntime.normal", "com.sony.dtv.b2b.noderuntime.privilege",
            "com.sony.dtv.b2b.webappruntimedrm", "com.sony.dtv.demomode", "com.sony.dtv.demosupport", "com.sony.dtv.multiscreendemo",
            "com.sony.dtv.sonyselect", "tv.samba.ssm"};
    /** Disabled last, and only once FLauncher is installed, or Home would have nowhere to go. */
    static final String[] LAUNCHER = {"com.google.android.tvrecommendations", "com.google.android.tvlauncher"};
    static final String ANIM = "window_animation_scale transition_animation_scale animator_duration_scale";

    interface Log { void line(String s); }

    private final Adb adb;
    private final Log log;
    private final File dir;

    Setup(Adb adb, File downloadDir, Log log) { this.adb = adb; this.dir = downloadDir; this.log = log; }

    boolean has(String pkg) throws IOException { return adb.shell("pm path " + pkg).contains("package:"); }

    private static String last(String out) { String[] l = out.split("\n"); return l.length == 0 ? "" : l[l.length - 1]; }

    String describe() throws IOException {
        return adb.shell("getprop ro.product.manufacturer") + " " + adb.shell("getprop ro.product.model") + ", Android " + adb.shell("getprop ro.build.version.release");
    }

    /** "100.x.y.z" once Tailscale is signed in, else "". */
    String tailscaleIp() throws IOException {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("inet (100\\.[0-9.]+)/32").matcher(adb.shell("ip -4 addr show"));
        return m.find() ? m.group(1) : "";
    }

    String lanIp() throws IOException {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("inet ((?:192\\.168|10|172\\.(?:1[6-9]|2[0-9]|3[01]))\\.[0-9.]+)/").matcher(adb.shell("ip -4 addr show"));
        return m.find() ? m.group(1) : "";
    }

    void apply() throws Exception {
        if (has(TS_PKG)) {
            adb.shell("dumpsys deviceidle whitelist +" + TS_PKG);                  // exempt from Doze / battery optimisation
            adb.shell("am set-standby-bucket " + TS_PKG + " active");
            adb.shell("cmd appops set " + TS_PKG + " RUN_IN_BACKGROUND allow; cmd appops set " + TS_PKG + " RUN_ANY_IN_BACKGROUND allow");
            adb.shell("settings put secure always_on_vpn_app " + TS_PKG + "; settings put secure always_on_vpn_lockdown 0");
            log.line("✓ Tailscale: giữ sống qua standby và khởi động lại");
        } else {
            log.line("– Tailscale chưa cài, bỏ qua bước giữ sống (cài xong hãy chạy lại)");
        }
        adb.shell("for k in " + ANIM + "; do settings put global $k 0.5; done");
        log.line("✓ Hiệu ứng chuyển cảnh: 0.5x");

        int off = 0;
        for (String[] group : new String[][]{GOOGLE, SONY})
            for (String p : group)
                if (has(p)) { adb.shell("pm disable-user --user 0 " + p + "; am force-stop " + p); off++; }
        log.line("✓ Đã tắt " + off + " app hệ thống không dùng");

        if (!has(FL_PKG)) {
            log.line("… Đang tải FLauncher (10 MB)");
            File apk = download(FL_URL, "flauncher.apk", FL_SHA);
            log.line("… Đang cài FLauncher");
            String out = install(apk);
            apk.delete();
            if (!out.contains("Success")) { log.line("✗ Cài FLauncher lỗi: " + last(out) + " — giữ nguyên màn hình chính Google"); return; }
        }
        for (String p : LAUNCHER)
            if (has(p)) adb.shell("pm disable-user --user 0 " + p + "; am force-stop " + p);
        log.line("✓ Màn hình chính: " + last(adb.shell("cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN")));
        log.line("Xong. Bấm nút Home để thấy màn hình chính mới.");
    }

    void undo() throws Exception {
        int on = 0;
        for (String[] group : new String[][]{LAUNCHER, GOOGLE, SONY})
            for (String p : group)
                if (has(p)) { adb.shell("pm enable --user 0 " + p); on++; }
        adb.shell("for k in " + ANIM + "; do settings put global $k 1.0; done");
        log.line("✓ Đã bật lại " + on + " app (gồm màn hình chính Google), hiệu ứng về 1.0x");
        log.line("FLauncher và cài đặt Tailscale được giữ nguyên.");
    }

    /** The shell user may install an APK; this app may not, so hand the file to `pm` through the ADB shell. */
    String install(File apk) throws IOException {
        return adb.shell("cat '" + apk.getAbsolutePath() + "' | pm install -r -S " + apk.length() + " 2>&1");
    }

    File download(String url, String name, String sha256) throws Exception {
        File f = new File(dir, name);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(f)) {
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) { out.write(buf, 0, n); md.update(buf, 0, n); }
        } finally {
            c.disconnect();
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest()) hex.append(String.format("%02x", b));
        if (sha256 != null && !hex.toString().equals(sha256)) { f.delete(); throw new IOException("checksum không khớp, không cài"); }
        return f;
    }
}
