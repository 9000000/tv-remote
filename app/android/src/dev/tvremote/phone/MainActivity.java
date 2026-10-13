package dev.tvremote.phone;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import dev.tvremote.core.Tv;
import dev.tvremote.update.Release;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Phone remote: pick a TV (saved, found on the Wi-Fi, or typed IP), then live picture with touch, and remote buttons. */
public final class MainActivity extends Activity implements Tv.Listener, SurfaceHolder.Callback {
    private static final int BG = 0xff111418, CARD = 0xff1c2026, BTN = 0xff2a3038, ACCENT = 0xff5b8cff, HELD = 0xffe0a030, TEXT = 0xffe8eaed, MUTED = 0xff9aa0a6;
    private static final String INSTALL_RESULT = "dev.tvremote.phone.INSTALL_RESULT";
    private static boolean VI = "vi".equals(Locale.getDefault().getLanguage());   // set again in onCreate from the Settings choice

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout picker, remote, controls, savedList, foundList;
    private View settings, about;
    private TextView pickerMsg, status;
    private EditText ipBox, textBox;
    private Button scanBtn, updateBtn;
    private TextView updateNote;
    private Release update;                                           // a newer release found on GitHub, or null
    private boolean updating;
    private VideoFrame frame;
    private SurfaceView surface;
    private boolean surfaceReady, video;                               // video: show the TV picture (off = buttons only, no load on the TV)
    private IconButton videoBtn;
    private Tv tv;
    private String host;                                              // the TV we want to be connected to, null on the picker
    private boolean wakeNext, waking;                                 // wakeNext: the next session turns the TV's screen on first

    static String t(String vi, String en) { return VI ? vi : en; }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ---------------------------------------------------------------- lifecycle
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("tvs", MODE_PRIVATE);
        video = prefs.getBoolean("video", true);
        String lang = prefs.getString("lang", "");                    // "" = follow the phone
        VI = "vi".equals(lang.isEmpty() ? Locale.getDefault().getLanguage() : lang);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);
        buildPicker();
        buildRemote();
        ScrollView sv = new ScrollView(this);
        sv.addView(picker);
        sv.setTag("picker");
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        root.addView(remote, new FrameLayout.LayoutParams(-1, -1));
        settings = buildSettings();
        root.addView(settings, new FrameLayout.LayoutParams(-1, -1));
        about = buildAbout();
        root.addView(about, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        show(false);
        if (b != null) host = b.getString("host");
        if (host != null) show(true);
        if (b != null && b.getBoolean("settings")) settings.setVisibility(View.VISIBLE);
        if (b != null && b.getBoolean("about")) about.setVisibility(View.VISIBLE);
        if (prefs.getBoolean("autoUpdate", true)) checkUpdate(false);
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("host", host); out.putBoolean("settings", settings.getVisibility() == View.VISIBLE); out.putBoolean("about", about.getVisibility() == View.VISIBLE); }

    @Override protected void onStart() { super.onStart(); refreshSaved(); startSession(); }

    @Override protected void onStop() { super.onStop(); stopSession(); }     // not watching: stop the encoder on the TV

    @Override public void surfaceCreated(SurfaceHolder h) { surfaceReady = true; startSession(); }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) { }
    @Override public void surfaceDestroyed(SurfaceHolder h) { surfaceReady = false; if (video) stopSession(); }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        layoutRemote();
    }

    private void layoutRemote() {
        boolean full = video && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;   // landscape: the picture fills the screen
        ((View) controls.getParent()).setVisibility(full ? View.GONE : View.VISIBLE);
        ((View) status.getParent()).setVisibility(full ? View.GONE : View.VISIBLE);
        frame.setLayoutParams(new LinearLayout.LayoutParams(-1, full ? -1 : -2));
        frame.setVisibility(video ? View.VISIBLE : View.GONE);
        videoBtn.setIcon(video ? "@eye_off" : "@eye", TEXT);
        videoBtn.setContentDescription(video ? t("Ẩn hình TV", "Hide the TV picture") : t("Xem hình TV", "Show the TV picture"));
    }

    /** Picture on or off. Off: the TV encodes nothing, only the buttons work. The session restarts either way. */
    private void toggleVideo() {
        stopSession();
        video = !video;
        prefs.edit().putBoolean("video", video).apply();
        layoutRemote();                                               // showing the picture creates the surface, which starts the session
        status.setText(t("Đang kết nối ", "Connecting to ") + host + "...");
        startSession();
    }

    @Override public void onBackPressed() {
        if (about.getVisibility() == View.VISIBLE) about.setVisibility(View.GONE);
        else if (settings.getVisibility() == View.VISIBLE) settings.setVisibility(View.GONE);
        else if (host != null) disconnect(null);
        else super.onBackPressed();
    }

    /** The phone's volume buttons change the TV's volume while connected. */
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int c = e.getKeyCode();
        if (tv != null && tv.ready() && (c == KeyEvent.KEYCODE_VOLUME_UP || c == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) tv.press(c);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ---------------------------------------------------------------- session
    private void connect(String ip) {
        host = ip.trim();
        if (host.isEmpty()) return;
        hideKeyboard();
        status.setText(t("Đang kết nối ", "Connecting to ") + host + "...");
        show(true);
        startSession();
    }

    private void startSession() {
        if (host == null || tv != null || video && !surfaceReady) return;
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        tv = new Tv(this, host, video ? surface.getHolder().getSurface() : null, prefs.getInt("quality", 1), Math.max(dm.widthPixels, dm.heightPixels), wakeNext, this);
        wakeNext = false;
    }

    private void stopSession() { if (tv != null) { tv.stop(); tv = null; } }

    private void disconnect(String message) {
        stopSession();
        host = null;
        show(false);
        message(message, true);
        refreshSaved();
    }

    /** The box at the top of the picker: an error (red) or a progress note. */
    private void message(String text, boolean error) {
        pickerMsg.setText(text == null ? "" : text);
        pickerMsg.setTextColor(error ? 0xffffb4a8 : TEXT);
        pickerMsg.setBackground(box(error ? 0xff3a2020 : CARD, 12));
        pickerMsg.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    private void show(boolean remoteScreen) {
        remote.setVisibility(remoteScreen ? View.VISIBLE : View.GONE);
        ((View) picker.getParent()).setVisibility(remoteScreen ? View.GONE : View.VISIBLE);
    }

    @Override public void onStatus(final String key, final String detail) {
        final Tv from = tv;
        ui.post(() -> {
            if (tv != from || host == null) return;                   // from a session that has been replaced
            switch (key) {
                case "connecting": status.setText(t("Đang kết nối ", "Connecting to ") + detail + "..."); break;
                case "allow": status.setText(t("Nhìn lên TV: tích “Luôn cho phép từ máy tính này” rồi chọn OK.", "Look at the TV: tick “Always allow from this computer”, then OK.")); break;
                case "starting":
                    save(host, detail);
                    status.setText(t("Đang mở hình từ ", "Opening the picture from ") + (detail.isEmpty() ? host : detail) + "...");
                    break;
                case "rate": if (video) status.setText((name(host).isEmpty() ? host : name(host)) + " · " + detail + " KB/s"); break;
                case "live": status.setText((name(host).isEmpty() ? host : name(host)) + " · " + (video ? t("chạm vào hình để bấm trên TV", "touch the picture to tap on the TV")
                        : t("chỉ điều khiển, không xem hình (bấm nút con mắt để xem)", "buttons only, no picture (tap the eye to watch)"))); break;
                case "closed": tv = null; disconnect(t("TV đã ngắt kết nối.", "The TV closed the connection.")); break;
                default: tv = null; disconnect(explain(detail));
            }
        });
    }

    private String explain(String err) {
        String e = err == null ? "" : err.toLowerCase(Locale.ROOT);
        if (e.contains("refused") && !e.contains("refused:") || e.contains("econnrefused"))
            return t("TV từ chối kết nối: chưa bật “Gỡ lỗi ADB”.\n\nTrên TV: Cài đặt → Tùy chọn thiết bị → Giới thiệu → bấm OK 7 lần vào “Bản dựng”. Rồi Tùy chọn nhà phát triển → bật “Gỡ lỗi ADB” (hoặc “Gỡ lỗi USB”).",
                     "The TV refused the connection: “ADB debugging” is off.\n\nOn the TV: Settings → Device Preferences → About → press OK 7 times on “Build”. Then Developer options → turn on “ADB debugging” (or “USB debugging”).");
        if (e.contains("timed out") || e.contains("timeout") || e.contains("unreachable") || e.contains("no route") || e.contains("failed to connect"))
            return t("Không tới được TV ở địa chỉ này. TV có đang bật không? Điện thoại phải cùng Wi-Fi với TV, hoặc cả hai cùng bật Tailscale (khi đó dùng IP 100.x của TV).",
                     "Cannot reach the TV at this address. Is it on? The phone must be on the TV’s Wi-Fi, or both must have Tailscale on (then use the TV’s 100.x IP).") + "\n\n"
                    + t("Nếu TV đang tắt, bấm nút nguồn cạnh tên TV để đánh thức.", "If the TV is off, tap the power button next to it to wake it.") + "\n\n(" + err + ")";
        return t("Lỗi: ", "Error: ") + err;
    }

    @Override public void onVideoSize(final int w, final int h) { ui.post(() -> frame.setAspect(w, h)); }

    @Override public void onNetwork(final String mac, final String lan) {
        final String ip = host;
        if (ip != null) prefs.edit().putString("mac:" + ip, mac).putString("lan:" + ip, lan).apply();
    }

    // ---------------------------------------------------------------- updates (GitHub releases)
    private String version() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "0"; }
    }

    private void checkUpdate(final boolean manual) {
        if (manual) updateNote.setText(t("Đang kiểm tra...", "Checking..."));
        new Thread(() -> {
            Release found = null;
            String error = null;
            try { found = Release.newer(version(), "android", "tv-remote.apk"); } catch (Exception e) { error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
            final Release r = found;
            final String err = error;
            ui.post(() -> {
                if (isFinishing()) return;
                if (err == null) { update = r; showUpdate(); }
                if (manual) updateNote.setText(err != null ? t("Không kiểm tra được: ", "Could not check: ") + err
                        : r == null ? t("Đang dùng bản mới nhất (", "You have the latest version (") + version() + ")."
                        : t("Có bản mới ", "New version ") + r.version + t(". Bấm nút ở màn chọn TV để cập nhật.", ". Tap the button on the TV list to update."));
            });
        }).start();
    }

    private void showUpdate() {
        updateBtn.setVisibility(update == null ? View.GONE : View.VISIBLE);
        if (update != null && !updating) updateBtn.setText("🆕  " + t("Có bản mới ", "New version ") + update.version + t(" · bấm để cập nhật", " · tap to update"));
    }

    private void askUpdate() {
        final Release r = update;
        if (r == null || updating) return;
        String all = r.notes.replace("**", "").replace("\r", ""), notes = all.length() > 900 ? all.substring(0, 900) + "..." : all;   // release notes are Markdown

        new AlertDialog.Builder(this).setTitle(t("Bản mới ", "Version ") + r.version + t(" (đang dùng ", " (you have ") + version() + ")")
                .setMessage(notes.isEmpty() ? t("Tải và cài bản mới?", "Download and install the new version?") : notes)
                .setPositiveButton(t("Cập nhật", "Update"), (d, w) -> startUpdate(r)).setNegativeButton(t("Để sau", "Later"), null).show();
    }

    /** Downloads the APK from the release, then hands it to Android, which asks the person to confirm the install. */
    private void startUpdate(final Release r) {
        updating = true;
        updateBtn.setText(t("Đang tải bản ", "Downloading ") + r.version + "...");
        new Thread(() -> {
            String error = null;
            try {
                java.io.File apk = new java.io.File(getCacheDir(), "update.apk");
                Release.download(r.url, apk, pct -> ui.post(() -> updateBtn.setText(t("Đang tải bản ", "Downloading ") + r.version + "... " + pct + "%")));
                android.content.pm.PackageInstaller pi = getPackageManager().getPackageInstaller();
                int id = pi.createSession(new android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL));
                try (android.content.pm.PackageInstaller.Session session = pi.openSession(id)) {
                    try (java.io.OutputStream out = session.openWrite("apk", 0, apk.length()); java.io.InputStream in = new java.io.FileInputStream(apk)) {
                        byte[] b = new byte[65536];
                        for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
                        session.fsync(out);
                    }
                    Intent back = new Intent(this, MainActivity.class).setAction(INSTALL_RESULT);
                    session.commit(android.app.PendingIntent.getActivity(this, id, back,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 31 ? 0x02000000 /* FLAG_MUTABLE */ : 0)).getIntentSender());
                }
                apk.delete();
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final String err = error;
            ui.post(() -> {
                if (err == null) { updateBtn.setText(t("Đang chờ Android xác nhận cài đặt...", "Waiting for Android to confirm the install...")); return; }
                updating = false;
                showUpdate();
                message(t("Không cập nhật được: ", "Could not update: ") + err, true);
            });
        }).start();
    }

    /** Android reports back here: first "the person must confirm" (with the screen to show), then the result. */
    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (!INSTALL_RESULT.equals(i.getAction())) return;
        int status = i.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, android.content.pm.PackageInstaller.STATUS_FAILURE);
        if (status == android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) startActivity(confirm);
            return;
        }
        if (status == android.content.pm.PackageInstaller.STATUS_SUCCESS) return;    // the app is restarted by the install anyway
        updating = false;
        showUpdate();
        message(status == android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED ? t("Đã hủy cập nhật.", "Update cancelled.")
                : t("Không cài được bản mới: ", "Could not install the new version: ") + i.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE), true);
    }

    // ---------------------------------------------------------------- waking a TV that is off
    /**
     * Standby with the network still on: the TV answers, so connect and press "wake". Network off too: only a
     * Wake-on-LAN packet helps, and that reaches the TV from its own Wi-Fi only (never through Tailscale).
     */
    private void wake(final String ip) {
        if (waking) return;
        waking = true;
        final String mac = prefs.getString("mac:" + ip, ""), lan = prefs.getString("lan:" + ip, "");
        message(t("Đang đánh thức ", "Waking ") + (name(ip).isEmpty() ? ip : name(ip)) + t("... (tối đa 45 giây)", "... (up to 45 seconds)"), false);
        new Thread(() -> {
            boolean up = open(ip, 5555, 1500);
            int sent = 0;
            for (int i = 0; i < 22 && !up; i++) {
                if (i % 6 == 0 && !mac.isEmpty()) sent += Wol.send(mac, lan);
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                up = open(ip, 5555, 600) || !lan.isEmpty() && !lan.equals(ip) && Wol.sameNetwork(lan) && open(lan, 5555, 400);
            }
            final boolean ok = up, packets = sent > 0, home = Wol.sameNetwork(lan);
            ui.post(() -> {
                waking = false;
                if (isFinishing() || host != null) return;
                if (ok) { wakeNext = true; connect(ip); return; }
                String why;
                if (mac.isEmpty()) why = t("App chưa biết địa chỉ MAC của TV này (nó tự lưu sau lần kết nối đầu tiên bằng bản app này), nên chưa gửi được gói đánh thức.",
                        "The app does not know this TV’s MAC address yet (it is saved after the first connection with this version), so no wake packet was sent.");
                else if (!packets || !home) why = t("TV đã tắt cả mạng nên không nghe qua Tailscale. Gói đánh thức chỉ tới được TV từ đúng Wi-Fi nhà TV, mà điện thoại đang ở mạng khác. Cách mở: nối điện thoại vào Wi-Fi đó rồi bấm lại, hoặc dùng nút Đánh thức trên web ở máy tính (nó nhờ một máy ở nhà gửi giúp).",
                        "The TV’s network is off too, so it cannot hear anything over Tailscale. A wake packet only reaches the TV from its own Wi-Fi, and the phone is on another network. Join that Wi-Fi and try again, or use Wake in the web UI on a computer (it relays through a machine at home).");
                else why = t("Đã gửi gói đánh thức trong Wi-Fi nhưng TV không lên. Kiểm tra trên TV: Cài đặt → Mạng và Internet → “Khởi động từ xa” phải bật.",
                        "Wake packets were sent on the Wi-Fi but the TV did not come up. On the TV check: Settings → Network & Internet → “Remote start” must be on.");
                message(t("Chưa đánh thức được TV.\n\n", "Could not wake the TV.\n\n") + why, true);
            });
        }).start();
    }

    // ---------------------------------------------------------------- saved TVs
    private String name(String ip) { return prefs.getString("name:" + ip, ""); }

    private List<String> savedIps() {
        List<String> out = new ArrayList<>();
        for (String s : prefs.getString("ips", "").split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    private void save(String ip, String model) {
        List<String> ips = savedIps();
        ips.remove(ip);
        ips.add(0, ip);
        SharedPreferences.Editor e = prefs.edit().putString("ips", android.text.TextUtils.join(",", ips));
        if (!model.isEmpty()) e.putString("name:" + ip, model);
        e.apply();
    }

    private void refreshSaved() {
        savedList.removeAllViews();
        for (final String ip : savedIps()) {
            Button b = row(name(ip).isEmpty() ? ip : name(ip) + "\n" + ip, () -> connect(ip));
            Button wk = icon("@power", t("Đánh thức", "Wake"), BTN);
            wk.setOnClickListener(v -> wake(ip));
            LinearLayout line = new LinearLayout(this);
            line.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(dp(60), -1);
            wp.leftMargin = dp(6);
            line.addView(wk, wp);
            line.setLayoutParams(gap(6));
            b.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setMessage(t("Xóa TV này khỏi danh sách?", "Remove this TV from the list?") + "\n" + ip)
                        .setPositiveButton(t("Xóa", "Remove"), (d, w) -> {
                            List<String> ips = savedIps();
                            ips.remove(ip);
                            prefs.edit().putString("ips", android.text.TextUtils.join(",", ips)).remove("name:" + ip).remove("mac:" + ip).remove("lan:" + ip).apply();
                            refreshSaved();
                        }).setNegativeButton(android.R.string.cancel, null).show();
                return true;
            });
            savedList.addView(line);
        }
        if (savedList.getChildCount() == 0) savedList.addView(note(t("Chưa có TV nào. Quét Wi-Fi hoặc nhập IP bên dưới.", "No TVs yet. Scan the Wi-Fi or type an IP below.")));

    }

    // ---------------------------------------------------------------- finding TVs on the Wi-Fi
    private void scan() {
        scanBtn.setEnabled(false);
        scanBtn.setText(t("Đang quét...", "Scanning..."));
        foundList.removeAllViews();
        new Thread(() -> {
            final List<String> nets = new ArrayList<>(), found = Collections.synchronizedList(new ArrayList<String>());
            try {
                for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    if (!ni.isUp() || ni.isLoopback()) continue;
                    for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                        String ip = a.getHostAddress();
                        if (a instanceof Inet4Address && a.isSiteLocalAddress()) nets.add(ip.substring(0, ip.lastIndexOf('.') + 1));
                    }
                }
                ExecutorService pool = Executors.newFixedThreadPool(64);
                for (final String net : nets) for (int i = 1; i < 255; i++) {
                    final String ip = net + i;
                    pool.execute(() -> { if (open(ip, 5555, 500) || open(ip, 6466, 500)) found.add(ip); });
                }
                pool.shutdown();
                pool.awaitTermination(20, TimeUnit.SECONDS);
            } catch (Exception ignored) { }
            ui.post(() -> {
                scanBtn.setEnabled(true);
                scanBtn.setText("🔍  " + t("Quét Wi-Fi", "Scan the Wi-Fi"));
                Collections.sort(found);
                for (final String ip : found) foundList.addView(row(ip, () -> connect(ip)));
                if (found.isEmpty()) foundList.addView(note(nets.isEmpty()
                        ? t("Điện thoại chưa nối Wi-Fi. Khác mạng thì nhập IP Tailscale của TV bên dưới.", "The phone is not on Wi-Fi. On another network, type the TV’s Tailscale IP below.")
                        : t("Không thấy TV nào trong Wi-Fi này. TV có đang bật và đã bật “Gỡ lỗi ADB” chưa?", "No TV found on this Wi-Fi. Is it on, with “ADB debugging” enabled?")));
            });
        }).start();
    }

    private static boolean open(String ip, int port, int timeoutMs) {
        try (Socket s = new Socket()) { s.connect(new InetSocketAddress(ip, port), timeoutMs); return true; } catch (Exception e) { return false; }
    }

    // ---------------------------------------------------------------- picker screen
    private void buildPicker() {
        picker = col();
        picker.setPadding(dp(16), dp(20), dp(16), dp(24));
        TextView title = label("TV Remote", 24, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button set = icon("@settings", t("Cài đặt", "Settings"), BTN);
        set.setOnClickListener(v -> { hideKeyboard(); settings.setVisibility(View.VISIBLE); });
        top.addView(set, new LinearLayout.LayoutParams(dp(52), dp(44)));
        picker.addView(top);
        pickerMsg = label("", 14, 0xffffb4a8);
        pickerMsg.setBackground(box(0xff3a2020, 12));
        pickerMsg.setPadding(dp(12), dp(10), dp(12), dp(10));
        pickerMsg.setVisibility(View.GONE);
        picker.addView(pickerMsg, gap(12));
        updateBtn = button("", 0xff2e7d4f);
        updateBtn.setOnClickListener(v -> askUpdate());
        updateBtn.setVisibility(View.GONE);
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(-1, dp(48));
        up.topMargin = dp(12);
        picker.addView(updateBtn, up);

        picker.addView(head(t("TV ĐÃ DÙNG", "YOUR TVS")), gap(18));
        savedList = col();
        picker.addView(savedList);

        picker.addView(head(t("TÌM TRONG WI-FI", "FIND ON THE WI-FI")), gap(18));
        scanBtn = button("🔍  " + t("Quét Wi-Fi", "Scan the Wi-Fi"), ACCENT);
        scanBtn.setOnClickListener(v -> scan());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(48));
        sp.topMargin = dp(6);
        picker.addView(scanBtn, sp);
        foundList = col();
        picker.addView(foundList);

        picker.addView(head(t("NHẬP IP CỦA TV", "TYPE THE TV’S IP")), gap(18));
        LinearLayout line = new LinearLayout(this);
        ipBox = input(t("vd 192.168.1.88 hoặc 100.x.x.x", "e.g. 192.168.1.88 or 100.x.x.x"));
        ipBox.setInputType(InputType.TYPE_CLASS_PHONE);
        ipBox.setImeOptions(EditorInfo.IME_ACTION_GO);
        ipBox.setOnEditorActionListener((v, a, e) -> { connect(ipBox.getText().toString()); return true; });
        Button go = icon("@right", t("Kết nối", "Connect"), ACCENT);
        go.setOnClickListener(v -> connect(ipBox.getText().toString()));
        line.addView(ipBox, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(48));
        lp.leftMargin = dp(8);
        lp.width = dp(60);
        line.addView(go, lp);
        picker.addView(line, gap(6));
        Button help = button(t("Chưa biết IP? Xem cách kết nối", "No IP yet? How to connect"), 0);
        help.setTextColor(ACCENT);
        help.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(t("Cách kết nối", "How to connect")).setMessage(t(
                "1. BẬT ADB TRÊN TV (một lần)\nCài đặt → Tùy chọn thiết bị → Giới thiệu → bấm OK 7 lần vào “Bản dựng”. Rồi Tùy chọn nhà phát triển → bật “Gỡ lỗi ADB”.\n\n"
                + "2A. CÙNG WI-FI VỚI TV\nBấm “Quét Wi-Fi” rồi chọn TV. Hoặc xem IP trên TV: Cài đặt → Mạng và Internet → tên Wi-Fi.\n\n"
                + "2B. KHÁC MẠNG\nBật Tailscale trên cả điện thoại và TV, rồi nhập IP 100.x của TV (xem trong app Tailscale).\n\n"
                + "TV chưa có Tailscale? Mở Tailscale trên TV, nó hiện một mã. Nhập mã ở login.tailscale.com/admin/machines → Add device.\n\n"
                + "3. LẦN ĐẦU KẾT NỐI\nTV hỏi cho phép: tích “Luôn cho phép” rồi OK.\n\n"
                + "MẸO\nNút nguồn cạnh tên TV: đánh thức TV đang tắt. Giữ lâu vào tên TV: xóa khỏi danh sách.",
                "1. TURN ON ADB ON THE TV (once)\nSettings → Device Preferences → About → press OK 7 times on “Build”. Then Developer options → turn on “ADB debugging”.\n\n"
                + "2A. SAME WI-FI AS THE TV\nTap “Scan the Wi-Fi” and pick the TV. Or read the IP on the TV: Settings → Network & Internet → the Wi-Fi name.\n\n"
                + "2B. ANOTHER NETWORK\nTurn on Tailscale on both the phone and the TV, then type the TV’s 100.x IP (see the Tailscale app).\n\n"
                + "No Tailscale on the TV yet? Open Tailscale on the TV, it shows a code. Enter it at login.tailscale.com/admin/machines → Add device.\n\n"
                + "3. THE FIRST CONNECTION\nThe TV asks for permission: tick “Always allow”, then OK.\n\n"
                + "TIPS\nThe power button next to a TV wakes it when it is off. Long-press a TV to remove it from the list."))
                .setPositiveButton(t("Đã hiểu", "Got it"), null).show());
        picker.addView(help, new LinearLayout.LayoutParams(-1, dp(44)));
    }

    // ---------------------------------------------------------------- settings screen
    private View buildSettings() {
        LinearLayout box = col();
        box.setPadding(dp(16), dp(20), dp(16), dp(24));
        TextView title = label(t("Cài đặt", "Settings"), 24, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button done = icon("@check", t("Xong", "Done"), ACCENT);
        done.setOnClickListener(v -> settings.setVisibility(View.GONE));
        top.addView(done, new LinearLayout.LayoutParams(dp(52), dp(44)));
        box.addView(top);

        box.addView(head(t("NGÔN NGỮ", "LANGUAGE")), gap(18));
        final String now = prefs.getString("lang", "");
        String[][] langs = {{"", "📱  " + t("Theo điện thoại", "Same as the phone")}, {"vi", "🇻🇳  Tiếng Việt"}, {"en", "🇬🇧  English"}};
        for (final String[] l : langs) {
            Button b = row(l[1] + (l[0].equals(now) ? "   ✓" : ""), () -> {
                if (l[0].equals(now)) return;
                prefs.edit().putString("lang", l[0]).apply();
                recreate();                                           // every label is built in onCreate
            });
            if (l[0].equals(now)) b.setTextColor(ACCENT);
            box.addView(b);
        }

        box.addView(head(t("CHẤT LƯỢNG HÌNH", "PICTURE QUALITY")), gap(18));
        final String[] levels = {"🪶  " + t("Tiết kiệm · 480 px · tối đa khoảng 40 KB/s", "Data saver · 480 px · up to about 40 KB/s"),
                "⚖️  " + t("Vừa · 720 px · tối đa khoảng 100 KB/s", "Balanced · 720 px · up to about 100 KB/s"),
                "🔍  " + t("Nét · 960 px · tối đa khoảng 250 KB/s", "Sharp · 960 px · up to about 250 KB/s")};
        final Button[] qb = new Button[levels.length];
        final Runnable marks = () -> {
            for (int i = 0; i < qb.length; i++) { boolean on = prefs.getInt("quality", 1) == i; qb[i].setText(levels[i] + (on ? "   ✓" : "")); qb[i].setTextColor(on ? ACCENT : TEXT); }
        };
        for (int i = 0; i < levels.length; i++) {
            final int level = i;
            qb[i] = row("", () -> { prefs.edit().putInt("quality", level).apply(); marks.run(); });
            box.addView(qb[i]);
        }
        marks.run();
        box.addView(note(t("Áp dụng từ lần kết nối sau. Hình đứng yên thì gần như không tốn dữ liệu; con số trên là khi hình chuyển động liên tục (đang phát video). Mức thấp cũng nhẹ cho TV hơn. Không xem hình thì bấm nút con mắt ở màn remote: không tốn gì.",
                "Applies from the next connection. A still picture uses almost no data; the figures are for a constantly moving picture (video playing). Lower levels are also lighter on the TV. To not watch at all, tap the eye on the remote screen: that costs nothing.")));

        box.addView(head(t("CẬP NHẬT", "UPDATES")), gap(18));
        final Button[] auto = new Button[1];
        final Runnable autoLabel = () -> auto[0].setText((prefs.getBoolean("autoUpdate", true) ? "✅  " : "⬜  ") + t("Tự kiểm tra bản mới khi mở app", "Check for a new version when the app opens"));
        auto[0] = row("", () -> { prefs.edit().putBoolean("autoUpdate", !prefs.getBoolean("autoUpdate", true)).apply(); autoLabel.run(); });
        autoLabel.run();
        box.addView(auto[0]);
        box.addView(row("🔄  " + t("Kiểm tra bản mới ngay", "Check for a new version now"), () -> checkUpdate(true)));
        updateNote = note(t("App hỏi GitHub xem có bản phát hành mới hơn không; ngoài ra không gửi gì đi.", "The app asks GitHub whether a newer release exists; nothing else is sent."));
        box.addView(updateNote);

        box.addView(head(t("GIỚI THIỆU", "ABOUT")), gap(18));
        box.addView(row("ℹ️  " + t("Giới thiệu, tác giả, mã nguồn", "About, author, source code"), () -> about.setVisibility(View.VISIBLE)));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setClickable(true);                                        // do not let touches fall through to the screen below
        sv.addView(box);
        sv.setVisibility(View.GONE);
        return sv;
    }

    // ---------------------------------------------------------------- about screen
    private void web(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))); }
        catch (Exception e) { message(url, false); }                  // no browser on this device
    }

    private View buildAbout() {
        LinearLayout box = col();
        box.setPadding(dp(16), dp(20), dp(16), dp(24));
        TextView title = label(t("Giới thiệu", "About"), 24, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button done = icon("@check", t("Xong", "Done"), ACCENT);
        done.setOnClickListener(v -> about.setVisibility(View.GONE));
        top.addView(done, new LinearLayout.LayoutParams(dp(52), dp(44)));
        box.addView(top);

        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.icon);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(84), dp(84));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(22);
        box.addView(logo, lp);
        TextView name = label("TV Remote " + version(), 20, TEXT);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        box.addView(name, gap(10));
        TextView what = note(t("Remote cho Android TV / Google TV: hình trực tiếp, chạm, nút điều khiển, gõ chữ, mở app.",
                "A remote for Android TV / Google TV: live picture, touch, remote buttons, typing, opening apps."));
        what.setGravity(Gravity.CENTER);
        box.addView(what);

        box.addView(head(t("TÁC GIẢ", "AUTHOR")), gap(22));
        box.addView(row("👤  Hoang Tran\n@HoangTran0410", () -> web("https://github.com/HoangTran0410")));

        box.addView(head(t("MÃ NGUỒN", "SOURCE CODE")), gap(18));
        box.addView(row("🔗  github.com/HoangTran0410/tv-remote", () -> web("https://github.com/" + Release.REPO)));
        box.addView(row("📦  " + t("Các bản phát hành và nhật ký thay đổi", "Releases and change log"), () -> web("https://github.com/" + Release.REPO + "/releases")));
        box.addView(row("🐞  " + t("Báo lỗi, góp ý", "Report a problem"), () -> web("https://github.com/" + Release.REPO + "/issues")));

        box.addView(head(t("GHI CÔNG", "CREDITS")), gap(18));
        box.addView(row("scrcpy · Genymobile · Apache-2.0\n" + t("hình trực tiếp và điều khiển", "live picture and input"), () -> web("https://github.com/Genymobile/scrcpy")));
        box.addView(note(t("App nói chuyện thẳng với TV qua ADB, không qua máy chủ nào. Khóa ADB của app nằm trong điện thoại này; gỡ app là mất khóa và TV sẽ hỏi cho phép lại.",
                "The app talks straight to the TV over ADB, with no server in between. Its ADB key lives on this phone; uninstalling the app loses it and the TV will ask for permission again.")), gap(12));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setClickable(true);
        sv.addView(box);
        sv.setVisibility(View.GONE);
        return sv;
    }

    // ---------------------------------------------------------------- remote screen
    private void buildRemote() {
        remote = col();
        status = label("", 13, MUTED);
        status.setPadding(dp(14), dp(8), dp(8), dp(8));
        videoBtn = icon("@eye_off", "", BTN);
        videoBtn.setOnClickListener(v -> toggleVideo());
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams vb = new LinearLayout.LayoutParams(dp(52), dp(40));
        vb.rightMargin = dp(10);
        vb.topMargin = dp(6);
        bar.addView(videoBtn, vb);
        remote.addView(bar);

        frame = new VideoFrame(this);
        frame.setBackgroundColor(Color.BLACK);
        surface = new SurfaceView(this);
        surface.getHolder().addCallback(this);
        frame.addView(surface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        surface.setOnTouchListener((v, e) -> {
            if (tv == null || !tv.ready()) return true;
            int a = e.getActionMasked();
            int action = a == MotionEvent.ACTION_DOWN ? 0 : a == MotionEvent.ACTION_MOVE ? 2 : a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL ? 1 : -1;
            if (action >= 0) tv.touch(action, e.getX() / v.getWidth(), e.getY() / v.getHeight());
            return true;
        });
        remote.addView(frame, new LinearLayout.LayoutParams(-1, -2));

        controls = col();
        controls.setPadding(dp(14), dp(12), dp(14), dp(20));
        LinearLayout first = keys(52, "@power|" + t("Nguồn", "Power"), 26, "@menu|Menu", 82, "@settings|" + t("Cài đặt của TV", "TV settings"), 176);
        IconButton apps = icon("@apps", t("Ứng dụng", "Apps"), BTN);
        apps.setOnClickListener(v -> showApps());
        first.addView(apps, cell(52, true));
        controls.addView(first);
        controls.addView(dpad(), gap(16));
        controls.addView(keys(56, "@back|" + t("Quay lại", "Back"), 4, "@home|Home", 3, "@mute|" + t("Tắt tiếng", "Mute"), 164), gap(16));
        controls.addView(keys(52, "@vol_down|" + t("Giảm âm lượng", "Volume down"), 25, "@prev|" + t("Bài trước", "Previous"), 88, "@play_pause|" + t("Phát / Dừng", "Play / Pause"), 85,
                "@next|" + t("Bài sau", "Next"), 87, "@vol_up|" + t("Tăng âm lượng", "Volume up"), 24), gap(8));

        LinearLayout line = new LinearLayout(this);
        textBox = input(t("Gõ chữ gửi lên TV", "Type text for the TV"));
        textBox.setImeOptions(EditorInfo.IME_ACTION_SEND);
        textBox.setSingleLine(true);
        textBox.setOnEditorActionListener((v, a, e) -> { sendText(); return true; });
        line.addView(textBox, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button send = icon("@send", t("Gửi", "Send"), ACCENT);
        send.setOnClickListener(v -> sendText());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(48));
        lp.leftMargin = dp(6);
        lp.width = dp(60);
        line.addView(send, lp);
        controls.addView(line, gap(12));
        controls.addView(keys(48, "@backspace|" + t("Xóa", "Delete"), 67, "@enter|Enter", 66), gap(8));

        Button bye = button(t("Ngắt kết nối", "Disconnect"), 0);
        bye.setTextColor(0xffff8a80);
        bye.setOnClickListener(v -> disconnect(null));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(48));
        bp.topMargin = dp(10);
        controls.addView(bye, bp);

        ScrollView sv = new ScrollView(this);
        sv.addView(controls);
        remote.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        layoutRemote();
    }

    private void sendText() {
        String s = textBox.getText().toString();
        if (tv != null && tv.ready() && !s.isEmpty()) { tv.text(s); textBox.setText(""); }
    }

    private void showApps() {
        final Tv session = tv;
        if (session == null || !session.ready()) return;
        new Thread(() -> {
            List<String> list;
            try { list = session.apps(); } catch (Exception e) { list = new ArrayList<>(); }
            final List<String> apps = list;
            ui.post(() -> {
                if (isFinishing() || tv != session) return;
                String[] names = new String[apps.size()];
                for (int i = 0; i < names.length; i++) names[i] = apps.get(i).substring(0, apps.get(i).indexOf('/'));
                new AlertDialog.Builder(this).setTitle(t("Mở ứng dụng trên TV", "Open an app on the TV"))
                        .setItems(names, (d, i) -> session.launch(apps.get(i))).setNegativeButton(android.R.string.cancel, null).show();
            });
        }).start();
    }

    /** A row of equal-width remote keys: label, Android key code, label, key code... "@icon|words" shows an icon. An empty label leaves a gap. */
    private LinearLayout keys(int height, Object... labelAndCode) {
        LinearLayout row = new LinearLayout(this);
        for (int i = 0; i < labelAndCode.length; i += 2) {
            String label = (String) labelAndCode[i], say = label;
            int bar = label.indexOf('|');
            if (bar > 0) { say = label.substring(bar + 1); label = label.substring(0, bar); }
            int code = (Integer) labelAndCode[i + 1];
            View v;
            if (label.isEmpty()) v = new View(this);
            else {
                Button b = bar > 0 ? icon(label, say, BTN) : button(label, BTN);
                if (bar < 0) b.setTextSize(18);
                bindKey(b, code, box(HELD, 16));
                v = b;
            }
            row.addView(v, cell(height, i > 0));
        }
        return row;
    }

    /** Down and up are sent separately, so holding the button = a long press on the TV; it turns amber while held. */
    private void bindKey(final Button b, final int code, final android.graphics.drawable.Drawable held) {
        final android.graphics.drawable.Drawable normal = b.getBackground();
        final Runnable[] repeat = new Runnable[1];
        final int[] count = {0};
        b.setOnTouchListener((view, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                if (tv == null || !tv.ready()) return true;
                b.setBackground(held);
                count[0] = 0;
                tv.key(0, code, 0, 0);
                repeat[0] = new Runnable() { @Override public void run() { if (tv != null) tv.key(0, code, ++count[0], 0); ui.postDelayed(this, 80); } };
                ui.postDelayed(repeat[0], 450);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                b.setBackground(normal);
                if (repeat[0] != null) { ui.removeCallbacks(repeat[0]); repeat[0] = null; if (tv != null) tv.key(1, code, 0, 0); }
            }
            return true;
        });
    }

    private GradientDrawable oval(int color) { GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(color); return g; }

    /** The round direction pad: four arrows on a disc, OK in the middle. */
    private View dpad() {
        FrameLayout pad = new FrameLayout(this);
        pad.setBackground(oval(CARD));
        int[][] arrows = {{19, Gravity.TOP | Gravity.CENTER_HORIZONTAL}, {20, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL}, {21, Gravity.START | Gravity.CENTER_VERTICAL}, {22, Gravity.END | Gravity.CENTER_VERTICAL}};
        String[] names = {"@up", "@down", "@left", "@right"}, says = {t("Lên", "Up"), t("Xuống", "Down"), t("Trái", "Left"), t("Phải", "Right")};
        for (int i = 0; i < 4; i++) {
            IconButton b = icon(names[i], says[i], 0);
            b.setIcon(names[i], MUTED);
            b.size = 30;
            b.setBackground(oval(0));
            bindKey(b, arrows[i][0], oval(HELD));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(80), dp(80), arrows[i][1]);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            pad.addView(b, lp);
        }
        Button ok = button("OK", ACCENT);
        ok.setTypeface(Typeface.DEFAULT_BOLD);
        ok.setTextSize(18);
        ok.setPadding(0, 0, 0, 0);
        ok.setBackground(oval(ACCENT));
        ok.setElevation(dp(6));
        bindKey(ok, 23, oval(HELD));
        pad.addView(ok, new FrameLayout.LayoutParams(dp(92), dp(92), Gravity.CENTER));
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.CENTER);
        wrap.addView(pad, new LinearLayout.LayoutParams(dp(248), dp(248)));
        return wrap;
    }

    // ---------------------------------------------------------------- small view helpers
    private LinearLayout col() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }

    private LinearLayout.LayoutParams gap(int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); return p; }

    private LinearLayout.LayoutParams cell(int height, boolean leftGap) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(height), 1);
        if (leftGap) p.leftMargin = dp(6);
        return p;
    }

    private GradientDrawable box(int color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g; }

    private TextView label(String s, int sp, int color) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); return v; }

    private TextView head(String s) { TextView v = label(s, 12, MUTED); v.setTypeface(Typeface.DEFAULT_BOLD); v.setLetterSpacing(0.08f); return v; }

    private TextView note(String s) { TextView v = label(s, 13, MUTED); v.setPadding(0, dp(6), 0, 0); v.setLineSpacing(dp(3), 1); return v; }

    private Button button(String s, int color) {
        Button b = new Button(this);
        b.setText(s); b.setAllCaps(false); b.setTextColor(TEXT); b.setTextSize(15);
        b.setBackground(box(color, 14));
        b.setStateListAnimator(null);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setMinHeight(0); b.setMinimumHeight(0);
        return b;
    }

    /** A button that shows only an icon; the words go to screen readers. "@name" = res/drawable/ic_name (dev/gen_icons.py), anything else is text. */
    private IconButton icon(String symbol, String says, int color) {
        IconButton b = new IconButton(this);
        b.setIcon(symbol, symbol.equals("@power") ? 0xffff8a80 : TEXT);
        b.setAllCaps(false); b.setTextColor(TEXT); b.setTextSize(20);
        b.setContentDescription(says);
        b.setBackground(box(color, 16));
        b.setStateListAnimator(null);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        return b;
    }

    static final class IconButton extends Button {
        private android.graphics.drawable.Drawable art;
        float size = 24;                                              // dp

        IconButton(android.content.Context c) { super(c); }

        void setIcon(String symbol, int tint) {
            if (!symbol.startsWith("@")) { art = null; setText(symbol); return; }
            int id = getResources().getIdentifier("ic_" + symbol.substring(1), "drawable", getContext().getPackageName());
            art = id == 0 ? null : getResources().getDrawable(id, null).mutate();
            if (art != null) art.setTint(tint);
            setText("");
            invalidate();
        }

        @Override protected void onDraw(android.graphics.Canvas c) {
            super.onDraw(c);
            if (art == null) return;
            int h = Math.round(size * getResources().getDisplayMetrics().density / 2), cx = getWidth() / 2, cy = getHeight() / 2;
            art.setBounds(cx - h, cy - h, cx + h, cy + h);
            art.draw(c);
        }
    }

    private Button row(String s, final Runnable onClick) {
        Button b = button(s, CARD);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(14), dp(12), dp(14), dp(12));
        b.setOnClickListener(v -> onClick.run());
        b.setLayoutParams(gap(6));
        return b;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setHintTextColor(MUTED); e.setTextColor(TEXT); e.setTextSize(15); e.setSingleLine(true);
        e.setBackground(box(CARD, 12));
        e.setPadding(dp(12), 0, dp(12), 0);
        return e;
    }

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(f.getWindowToken(), 0);
    }

    /** Keeps the picture at the TV's aspect ratio: full width in portrait, as large as fits in landscape. */
    static final class VideoFrame extends FrameLayout {
        private int vw = 16, vh = 9;

        VideoFrame(android.content.Context c) { super(c); }

        void setAspect(int w, int h) { if (w > 0 && h > 0) { vw = w; vh = h; requestLayout(); } }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec), maxH = MeasureSpec.getMode(hSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE : MeasureSpec.getSize(hSpec);
            int h = Math.min((int) ((long) w * vh / vw), maxH), cw = (int) ((long) h * vw / vh);
            for (int i = 0; i < getChildCount(); i++)
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            setMeasuredDimension(w, MeasureSpec.getMode(hSpec) == MeasureSpec.EXACTLY ? maxH : h);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                int x = (r - l - c.getMeasuredWidth()) / 2, y = (b - t - c.getMeasuredHeight()) / 2;
                c.layout(x, y, x + c.getMeasuredWidth(), y + c.getMeasuredHeight());
            }
        }
    }
}
