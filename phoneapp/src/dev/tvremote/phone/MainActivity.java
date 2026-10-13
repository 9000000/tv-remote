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
    private View settings;
    private TextView pickerMsg, status;
    private EditText ipBox, textBox;
    private Button scanBtn, updateBtn;
    private TextView updateNote;
    private Release update;                                           // a newer release found on GitHub, or null
    private boolean updating;
    private VideoFrame frame;
    private SurfaceView surface;
    private boolean surfaceReady;
    private Tv tv;
    private String host;                                              // the TV we want to be connected to, null on the picker
    private boolean wakeNext, waking;                                 // wakeNext: the next session turns the TV's screen on first

    static String t(String vi, String en) { return VI ? vi : en; }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ---------------------------------------------------------------- lifecycle
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("tvs", MODE_PRIVATE);
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
        setContentView(root);
        show(false);
        if (b != null) host = b.getString("host");
        if (host != null) show(true);
        if (b != null && b.getBoolean("settings")) settings.setVisibility(View.VISIBLE);
        if (prefs.getBoolean("autoUpdate", true)) checkUpdate(false);
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("host", host); out.putBoolean("settings", settings.getVisibility() == View.VISIBLE); }

    @Override protected void onStart() { super.onStart(); refreshSaved(); startSession(); }

    @Override protected void onStop() { super.onStop(); stopSession(); }     // not watching: stop the encoder on the TV

    @Override public void surfaceCreated(SurfaceHolder h) { surfaceReady = true; startSession(); }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) { }
    @Override public void surfaceDestroyed(SurfaceHolder h) { surfaceReady = false; stopSession(); }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        boolean land = c.orientation == Configuration.ORIENTATION_LANDSCAPE;   // landscape: the picture fills the screen
        ((View) controls.getParent()).setVisibility(land ? View.GONE : View.VISIBLE);
        status.setVisibility(land ? View.GONE : View.VISIBLE);
        frame.setLayoutParams(new LinearLayout.LayoutParams(-1, land ? -1 : -2));
    }

    @Override public void onBackPressed() {
        if (settings.getVisibility() == View.VISIBLE) settings.setVisibility(View.GONE);
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
        if (host == null || tv != null || !surfaceReady) return;
        tv = new Tv(this, host, surface.getHolder().getSurface(), wakeNext, this);
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
                case "live": status.setText((name(host).isEmpty() ? host : name(host)) + " · " + t("chạm vào hình để bấm trên TV", "touch the picture to tap on the TV")); break;
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
                    + t("TV đang tắt thì bấm nút nguồn cạnh tên TV để đánh thức.", "If the TV is off, tap the power button next to it to wake it.") + "\n\n(" + err + ")";
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
            try { found = Release.newer(version(), "tv-remote.apk"); } catch (Exception e) { error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
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
        String notes = r.notes.length() > 900 ? r.notes.substring(0, 900) + "..." : r.notes;
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
            Button wk = icon("⏻", t("Đánh thức", "Wake"), BTN);
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
        else savedList.addView(note(t("TV đang tắt: bấm nút nguồn bên cạnh để đánh thức. Giữ lâu vào một TV để xóa.", "TV is off: tap the power button next to it to wake it. Long-press a TV to remove it.")));
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
        Button set = icon("⚙️", t("Cài đặt", "Settings"), BTN);
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
        Button go = icon("➜", t("Kết nối", "Connect"), ACCENT);
        go.setOnClickListener(v -> connect(ipBox.getText().toString()));
        line.addView(ipBox, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(48));
        lp.leftMargin = dp(8);
        lp.width = dp(60);
        line.addView(go, lp);
        picker.addView(line, gap(6));
        picker.addView(note(t("Cùng Wi-Fi: dùng IP trong nhà của TV (Cài đặt → Mạng và Internet → tên Wi-Fi).\nKhác mạng: bật Tailscale trên cả điện thoại và TV, dùng IP 100.x của TV (xem trong app Tailscale). TV chưa vào Tailscale: mở Tailscale trên TV, nó hiện một mã; nhập mã đó ở login.tailscale.com/admin/machines → Add device, không cần gõ tài khoản trên TV.\nTV cần bật “Gỡ lỗi ADB”; lần đầu TV sẽ hỏi cho phép.",
                "Same Wi-Fi: use the TV’s home IP (Settings → Network & Internet → the Wi-Fi name).\nOther network: turn on Tailscale on both the phone and the TV and use the TV’s 100.x IP (see the Tailscale app). TV not on Tailscale yet: open Tailscale on the TV, it shows a code; enter it at login.tailscale.com/admin/machines → Add device, no account typing on the TV.\nThe TV needs “ADB debugging” on; the first time it asks for permission.")), gap(10));
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
        Button done = icon("✓", t("Xong", "Done"), ACCENT);
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
        String ver = version();
        box.addView(note("TV Remote " + ver + "\ngithub.com/HoangTran0410/tv-remote\n\n" + t(
                "App nói chuyện thẳng với TV qua ADB, không qua máy chủ nào. Khóa ADB của app nằm trong điện thoại này; gỡ app là mất khóa và TV sẽ hỏi cho phép lại.",
                "The app talks straight to the TV over ADB, with no server in between. Its ADB key lives on this phone; uninstalling the app loses it and the TV will ask for permission again.")));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setClickable(true);                                        // do not let touches fall through to the screen below
        sv.addView(box);
        sv.setVisibility(View.GONE);
        return sv;
    }

    // ---------------------------------------------------------------- remote screen
    private void buildRemote() {
        remote = col();
        status = label("", 13, MUTED);
        status.setPadding(dp(14), dp(8), dp(14), dp(8));
        remote.addView(status);

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
        controls.addView(keys(54, "⏻|" + t("Nguồn", "Power"), 26, "🏠|Home", 3, "↩️|" + t("Quay lại", "Back"), 4, "☰|Menu", 82));
        controls.addView(keys(58, "", 0, "▲", 19, "", 0), gap(12));
        controls.addView(keys(58, "◀", 21, "OK", 23, "▶", 22), gap(6));
        controls.addView(keys(58, "", 0, "▼", 20, "", 0), gap(6));
        controls.addView(keys(54, "🔉|" + t("Giảm âm lượng", "Volume down"), 25, "🔇|" + t("Tắt tiếng", "Mute"), 164, "🔊|" + t("Tăng âm lượng", "Volume up"), 24), gap(12));
        controls.addView(keys(50, "⏮", 88, "⏯", 85, "⏭", 87), gap(6));

        LinearLayout line = new LinearLayout(this);
        textBox = input(t("Gõ chữ gửi lên TV", "Type text for the TV"));
        textBox.setImeOptions(EditorInfo.IME_ACTION_SEND);
        textBox.setSingleLine(true);
        textBox.setOnEditorActionListener((v, a, e) -> { sendText(); return true; });
        line.addView(textBox, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button send = icon("➤", t("Gửi", "Send"), ACCENT);
        send.setOnClickListener(v -> sendText());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(48));
        lp.leftMargin = dp(6);
        lp.width = dp(60);
        line.addView(send, lp);
        controls.addView(line, gap(12));
        controls.addView(keys(50, "⌫|" + t("Xóa", "Delete"), 67, "↵|Enter", 66, "⚙️|" + t("Cài đặt của TV", "TV settings"), 176), gap(6));

        LinearLayout last = new LinearLayout(this);
        Button apps = button("📺  " + t("Ứng dụng", "Apps"), BTN);
        apps.setOnClickListener(v -> showApps());
        Button bye = button("✕  " + t("Ngắt kết nối", "Disconnect"), BTN);
        bye.setTextColor(0xffff8a80);
        bye.setOnClickListener(v -> disconnect(null));
        last.addView(apps, cell(50, false));
        last.addView(bye, cell(50, true));
        controls.addView(last, gap(12));

        ScrollView sv = new ScrollView(this);
        sv.addView(controls);
        remote.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
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

    /** A row of equal-width remote keys: label, Android key code, label, key code... An empty label leaves a gap. */
    private LinearLayout keys(int height, Object... labelAndCode) {
        LinearLayout row = new LinearLayout(this);
        for (int i = 0; i < labelAndCode.length; i += 2) {
            String label = (String) labelAndCode[i], say = label;
            int bar = label.indexOf('|');                             // "icon|what a screen reader says"
            if (bar > 0) { say = label.substring(bar + 1); label = label.substring(0, bar); }
            final int code = (Integer) labelAndCode[i + 1];
            View v;
            if (label.isEmpty()) v = new View(this);
            else {
                final Button b = bar > 0 ? icon(label, say, code == 23 ? ACCENT : BTN) : button(label, code == 23 ? ACCENT : BTN);
                if (bar < 0) b.setTextSize(18);
                final int normal = code == 23 ? ACCENT : BTN;
                final Runnable[] repeat = new Runnable[1];
                final int[] count = {0};
                b.setOnTouchListener((view, e) -> {                    // down and up are sent separately, so holding = long press on the TV
                    int a = e.getActionMasked();
                    if (a == MotionEvent.ACTION_DOWN) {
                        if (tv == null || !tv.ready()) return true;
                        b.setBackground(box(HELD, 14));
                        count[0] = 0;
                        tv.key(0, code, 0, 0);
                        repeat[0] = new Runnable() { @Override public void run() { if (tv != null) tv.key(0, code, ++count[0], 0); ui.postDelayed(this, 80); } };
                        ui.postDelayed(repeat[0], 450);
                    } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                        b.setBackground(box(normal, 14));
                        if (repeat[0] != null) { ui.removeCallbacks(repeat[0]); repeat[0] = null; if (tv != null) tv.key(1, code, 0, 0); }
                    }
                    return true;
                });
                v = b;
            }
            row.addView(v, cell(height, i > 0));
        }
        return row;
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

    /** A button that shows only a symbol; the words go to screen readers. "⏻" is drawn, because phone fonts lack it. */
    private Button icon(String symbol, String says, int color) {
        final boolean power = "⏻".equals(symbol);
        final float d = getResources().getDisplayMetrics().density;
        Button b = !power ? new Button(this) : new Button(this) {
            private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas c) {
                super.onDraw(c);
                float cx = getWidth() / 2f, cy = getHeight() / 2f, r = 9 * d;
                p.setStyle(android.graphics.Paint.Style.STROKE); p.setStrokeWidth(2.4f * d); p.setStrokeCap(android.graphics.Paint.Cap.ROUND); p.setColor(0xffff8a80);
                c.drawArc(cx - r, cy - r + d, cx + r, cy + r + d, -60, 300, false, p);
                c.drawLine(cx, cy - r - d, cx, cy - d, p);
            }
        };
        b.setText(power ? "" : symbol); b.setAllCaps(false); b.setTextColor(TEXT); b.setTextSize(20);
        b.setContentDescription(says);
        b.setBackground(box(color, 14));
        b.setStateListAnimator(null);
        b.setPadding(0, 0, 0, 0);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
        return b;
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
