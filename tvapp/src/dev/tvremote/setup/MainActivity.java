package dev.tvremote.setup;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** One screen, driven with the TV remote: connect to the TV's own ADB, then optimise / undo / set up Tailscale. */
public final class MainActivity extends Activity {
    private TextView status, logView;
    private ScrollView scroll;
    private Button[] buttons;
    private Adb adb;
    private Setup setup;
    private boolean busy;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = dp(32);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(17, 20, 24));

        TextView title = text("TV Setup", 30, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        status = text("Đang kết nối ADB...", 18, Color.rgb(153, 163, 176));
        status.setPadding(0, dp(6), 0, dp(18));
        root.addView(status);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        buttons = new Button[]{
                button("⚡ Tối ưu TV", v -> run(() -> setup.apply())),
                button("Hoàn tác", v -> run(() -> setup.undo())),
                button("Tailscale", v -> tailscale()),
                button("Kết nối lại", v -> connect())};
        for (Button b : buttons) row.addView(b);
        root.addView(row);

        logView = text("", 17, Color.rgb(233, 236, 241));
        logView.setLineSpacing(dp(4), 1f);
        scroll = new ScrollView(this);
        scroll.setPadding(0, dp(18), 0, 0);
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        log("Cần bật trước: Cài đặt → Tùy chọn thiết bị → Giới thiệu → bấm OK 7 lần vào \"Bản dựng\",");
        log("rồi Tùy chọn nhà phát triển → bật \"Gỡ lỗi ADB\" (hoặc \"Gỡ lỗi USB\").");
        connect();
    }

    @Override protected void onResume() {
        super.onResume();
        if (setup != null && !busy) run(() -> showStatus());          // back from Tailscale / Play Store: refresh the IPs
    }

    private void connect() {
        setEnabled(false);
        new Thread(() -> {
            try {
                if (adb != null) adb.close();
                Adb a = new Adb(getFilesDir());
                a.connect("127.0.0.1", 5555, () -> ui(() -> {
                    status.setText("TV đang hỏi \"Cho phép gỡ lỗi USB?\" → tích \"Luôn cho phép\" rồi chọn OK");
                    status.setTextColor(Color.rgb(240, 179, 74));
                }));
                adb = a;
                setup = new Setup(a, getExternalFilesDir(null), this::log);
                showStatus();
                ui(() -> setEnabled(true));
            } catch (Exception e) {
                adb = null; setup = null;
                ui(() -> {
                    status.setText("Chưa kết nối được ADB");
                    status.setTextColor(Color.rgb(255, 112, 112));
                    log("✗ " + reason(e));
                    log("Kiểm tra đã bật \"Gỡ lỗi ADB\" chưa, rồi chọn \"Kết nối lại\". Nếu TV có hỏi thì phải chọn \"Luôn cho phép\".");
                    buttons[3].setEnabled(true);
                    buttons[3].requestFocus();
                });
            }
        }).start();
    }

    private void showStatus() throws Exception {
        String name = setup.describe(), ts = setup.tailscaleIp(), lan = setup.lanIp();
        boolean tsInstalled = setup.has(Setup.TS_PKG);
        ui(() -> {
            status.setTextColor(Color.rgb(70, 210, 131));
            status.setText(name + "\nIP trong nhà: " + (lan.isEmpty() ? "?" : lan) + "   ·   IP Tailscale: "
                    + (!ts.isEmpty() ? ts : tsInstalled ? "chưa đăng nhập" : "chưa cài"));
        });
    }

    private interface Job { void run() throws Exception; }

    private void run(Job job) {
        if (setup == null || busy) return;
        busy = true;
        setEnabled(false);
        new Thread(() -> {
            try {
                job.run();
                showStatus();
            } catch (Exception e) {
                log("✗ " + reason(e));
            }
            busy = false;
            ui(() -> setEnabled(true));
        }).start();
    }

    /** Installed: open it so the person can sign in (it shows a QR code). Not installed: its Play Store page. */
    private void tailscale() {
        run(() -> {
            if (setup.has(Setup.TS_PKG)) {
                Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(Setup.TS_PKG);
                if (i == null) i = getPackageManager().getLaunchIntentForPackage(Setup.TS_PKG);
                if (i == null) { log("✗ Không mở được Tailscale"); return; }
                log("Mở Tailscale: chọn Sign in, quét mã QR bằng điện thoại (hoặc mở link trên máy khác) để đăng nhập.");
                startActivity(i);
            } else {
                log("Mở Google Play: chọn Cài đặt (Install), xong quay lại đây bấm \"Tailscale\" lần nữa để đăng nhập.");
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Setup.TS_PKG)));
                } catch (Exception e) {
                    log("✗ TV này không có Google Play. Cài Tailscale từ máy tính bằng deploy/tv-new.");
                }
            }
        });
    }

    private void log(String line) {
        ui(() -> {
            logView.append(line + "\n");
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private static String reason(Exception e) {
        String m = e.getMessage();
        return e.getClass().getSimpleName() + (m == null ? "" : ": " + m);
    }

    private void ui(Runnable r) { runOnUiThread(r); }

    private void setEnabled(boolean on) {
        View had = getCurrentFocus();
        for (Button b : buttons) { b.setEnabled(on); b.setAlpha(on ? 1f : 0.45f); }
        if (on && !(had instanceof Button)) buttons[0].requestFocus();
        else if (on && had != null) had.requestFocus();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    /** The stock button barely shows focus on a TV; make the focused one unmistakable. */
    private GradientDrawable background(boolean focused) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(10));
        d.setColor(focused ? Color.rgb(47, 109, 246) : Color.rgb(39, 45, 53));
        if (focused) d.setStroke(dp(3), Color.WHITE);
        return d;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(18);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);                              // keep D-pad focus even after a mouse / touch event
        b.setTextColor(Color.WHITE);
        b.setBackground(background(false));
        b.setOnFocusChangeListener((v, focused) -> v.setBackground(background(focused)));
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMarginEnd(dp(12));
        b.setLayoutParams(p);
        b.setPadding(dp(22), dp(12), dp(22), dp(12));
        return b;
    }
}
