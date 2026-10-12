package dev.tvremote.phone;

import android.app.Activity;
import android.app.AlertDialog;
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
    private static final boolean VI = "vi".equals(Locale.getDefault().getLanguage());

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout picker, remote, controls, savedList, foundList;
    private TextView pickerMsg, status;
    private EditText ipBox, textBox;
    private Button scanBtn;
    private VideoFrame frame;
    private SurfaceView surface;
    private boolean surfaceReady;
    private Tv tv;
    private String host;                                              // the TV we want to be connected to, null on the picker

    static String t(String vi, String en) { return VI ? vi : en; }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ---------------------------------------------------------------- lifecycle
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("tvs", MODE_PRIVATE);
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
        setContentView(root);
        show(false);
        if (b != null) host = b.getString("host");
        if (host != null) show(true);
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("host", host); }

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

    @Override public void onBackPressed() { if (host != null) disconnect(null); else super.onBackPressed(); }

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
        tv = new Tv(this, host, surface.getHolder().getSurface(), this);
    }

    private void stopSession() { if (tv != null) { tv.stop(); tv = null; } }

    private void disconnect(String message) {
        stopSession();
        host = null;
        show(false);
        pickerMsg.setText(message == null ? "" : message);
        pickerMsg.setVisibility(message == null ? View.GONE : View.VISIBLE);
        refreshSaved();
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
                     "Cannot reach the TV at this address. Is it on? The phone must be on the TV’s Wi-Fi, or both must have Tailscale on (then use the TV’s 100.x IP).") + "\n\n(" + err + ")";
        return t("Lỗi: ", "Error: ") + err;
    }

    @Override public void onVideoSize(final int w, final int h) { ui.post(() -> frame.setAspect(w, h)); }

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
            b.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setMessage(t("Xóa TV này khỏi danh sách?", "Remove this TV from the list?") + "\n" + ip)
                        .setPositiveButton(t("Xóa", "Remove"), (d, w) -> {
                            List<String> ips = savedIps();
                            ips.remove(ip);
                            prefs.edit().putString("ips", android.text.TextUtils.join(",", ips)).remove("name:" + ip).apply();
                            refreshSaved();
                        }).setNegativeButton(android.R.string.cancel, null).show();
                return true;
            });
            savedList.addView(b);
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
                    pool.execute(() -> { if (open(ip, 5555) || open(ip, 6466)) found.add(ip); });
                }
                pool.shutdown();
                pool.awaitTermination(20, TimeUnit.SECONDS);
            } catch (Exception ignored) { }
            ui.post(() -> {
                scanBtn.setEnabled(true);
                scanBtn.setText(t("Quét Wi-Fi", "Scan the Wi-Fi"));
                Collections.sort(found);
                for (final String ip : found) foundList.addView(row(ip, () -> connect(ip)));
                if (found.isEmpty()) foundList.addView(note(nets.isEmpty()
                        ? t("Điện thoại chưa nối Wi-Fi. Khác mạng thì nhập IP Tailscale của TV bên dưới.", "The phone is not on Wi-Fi. On another network, type the TV’s Tailscale IP below.")
                        : t("Không thấy TV nào trong Wi-Fi này. TV có đang bật và đã bật “Gỡ lỗi ADB” chưa?", "No TV found on this Wi-Fi. Is it on, with “ADB debugging” enabled?")));
            });
        }).start();
    }

    private static boolean open(String ip, int port) {
        try (Socket s = new Socket()) { s.connect(new InetSocketAddress(ip, port), 500); return true; } catch (Exception e) { return false; }
    }

    // ---------------------------------------------------------------- picker screen
    private void buildPicker() {
        picker = col();
        picker.setPadding(dp(16), dp(20), dp(16), dp(24));
        TextView title = label("TV Remote", 24, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        picker.addView(title);
        pickerMsg = label("", 14, 0xffffb4a8);
        pickerMsg.setBackground(box(0xff3a2020, 12));
        pickerMsg.setPadding(dp(12), dp(10), dp(12), dp(10));
        pickerMsg.setVisibility(View.GONE);
        picker.addView(pickerMsg, gap(12));

        picker.addView(head(t("TV ĐÃ DÙNG", "YOUR TVS")), gap(18));
        savedList = col();
        picker.addView(savedList);

        picker.addView(head(t("TÌM TRONG WI-FI", "FIND ON THE WI-FI")), gap(18));
        scanBtn = button(t("Quét Wi-Fi", "Scan the Wi-Fi"), ACCENT);
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
        Button go = button(t("Kết nối", "Connect"), ACCENT);
        go.setOnClickListener(v -> connect(ipBox.getText().toString()));
        line.addView(ipBox, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(48));
        lp.leftMargin = dp(8);
        line.addView(go, lp);
        picker.addView(line, gap(6));
        picker.addView(note(t("Cùng Wi-Fi: dùng IP trong nhà của TV (Cài đặt → Mạng và Internet → tên Wi-Fi).\nKhác mạng: bật Tailscale trên cả điện thoại và TV, dùng IP 100.x của TV (xem trong app Tailscale). TV chưa vào Tailscale: mở Tailscale trên TV, nó hiện một mã; nhập mã đó ở login.tailscale.com/admin/machines → Add device, không cần gõ tài khoản trên TV.\nTV cần bật “Gỡ lỗi ADB”; lần đầu TV sẽ hỏi cho phép.",
                "Same Wi-Fi: use the TV’s home IP (Settings → Network & Internet → the Wi-Fi name).\nOther network: turn on Tailscale on both the phone and the TV and use the TV’s 100.x IP (see the Tailscale app). TV not on Tailscale yet: open Tailscale on the TV, it shows a code; enter it at login.tailscale.com/admin/machines → Add device, no account typing on the TV.\nThe TV needs “ADB debugging” on; the first time it asks for permission.")), gap(10));
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
        controls.addView(keys(54, t("Nguồn", "Power"), 26, t("Home", "Home"), 3, t("Quay lại", "Back"), 4, "Menu", 82));
        controls.addView(keys(58, "", 0, "▲", 19, "", 0), gap(12));
        controls.addView(keys(58, "◀", 21, "OK", 23, "▶", 22), gap(6));
        controls.addView(keys(58, "", 0, "▼", 20, "", 0), gap(6));
        controls.addView(keys(54, t("Âm −", "Vol −"), 25, t("Tắt tiếng", "Mute"), 164, t("Âm +", "Vol +"), 24), gap(12));
        controls.addView(keys(50, "⏮", 88, "⏯", 85, "⏭", 87), gap(6));

        LinearLayout line = new LinearLayout(this);
        textBox = input(t("Gõ chữ gửi lên TV", "Type text for the TV"));
        textBox.setImeOptions(EditorInfo.IME_ACTION_SEND);
        textBox.setSingleLine(true);
        textBox.setOnEditorActionListener((v, a, e) -> { sendText(); return true; });
        line.addView(textBox, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button send = button(t("Gửi", "Send"), ACCENT);
        send.setOnClickListener(v -> sendText());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(48));
        lp.leftMargin = dp(6);
        line.addView(send, lp);
        controls.addView(line, gap(12));
        controls.addView(keys(50, "⌫", 67, "Enter", 66, t("Cài đặt", "Settings"), 176), gap(6));

        LinearLayout last = new LinearLayout(this);
        Button apps = button(t("Ứng dụng", "Apps"), BTN);
        apps.setOnClickListener(v -> showApps());
        Button bye = button(t("Ngắt kết nối", "Disconnect"), BTN);
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
            String label = (String) labelAndCode[i];
            final int code = (Integer) labelAndCode[i + 1];
            View v;
            if (label.isEmpty()) v = new View(this);
            else {
                final Button b = button(label, code == 23 ? ACCENT : BTN);
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
