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

import dev.tvremote.core.Adb;
import dev.tvremote.update.Release;

import java.io.File;

/** One screen, driven with the TV remote: connect to the TV's own ADB, then optimise / undo / set up Tailscale. */
public final class MainActivity extends Activity {
    private TextView status, logView;
    private ScrollView scroll;
    private Button[] buttons;
    private Button webBtn;
    private TextView webInfo;
    private String lanIp = "", tsIp = "";
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
        root.addView(text("Bản " + version() + "  ·  Tác giả: Hoang Tran (@HoangTran0410)  ·  github.com/HoangTran0410/tv-remote", 14, Color.rgb(120, 130, 143)));
        status = text("Đang kết nối ADB...", 18, Color.rgb(153, 163, 176));
        status.setPadding(0, dp(6), 0, dp(18));
        root.addView(status);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        buttons = new Button[]{
                button("⚡ Tối ưu TV", v -> run(() -> setup.apply())),
                button("Hoàn tác", v -> run(() -> setup.undo())),
                button("Tailscale", v -> tailscale()),
                button("Kết nối lại", v -> connect()),
                button("Cập nhật", v -> update())};
        for (Button b : buttons) row.addView(b);
        root.addView(row);

        // the web remote served by this TV: any phone or computer opens http://<TV's IP>:8765 and pairs with a code
        LinearLayout web = new LinearLayout(this);
        web.setOrientation(LinearLayout.HORIZONTAL);
        web.setPadding(0, dp(12), 0, 0);
        webBtn = button("", v -> { RemoteService.setEnabled(this, !RemoteService.enabled(this)); showWeb(); });
        web.addView(webBtn);
        web.addView(button("Hủy ghép mọi thiết bị", v -> { RemoteService.forgetAll(this); log("✓ Đã hủy ghép. Thiết bị nào muốn điều khiển phải nhập mã mới."); showWeb(); }));
        root.addView(web);
        webInfo = text("", 17, Color.rgb(233, 236, 241));
        webInfo.setPadding(0, dp(10), 0, 0);
        root.addView(webInfo);
        if (RemoteService.enabled(this)) RemoteService.start(this);
        showWeb();

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
        new Thread(() -> {                                            // asks GitHub for the latest release, nothing else is sent
            try {
                Release r = Release.newer(version(), "tv-setup.apk");
                if (r != null) { log("🆕 Có bản TV Setup mới " + r.version + " (đang dùng " + version() + "). Chọn \"Cập nhật\" để cài."); ui(() -> buttons[4].setText("🆕 Cập nhật " + r.version)); }
            } catch (Exception ignored) { }                           // offline: stay quiet, the button still checks on demand
        }).start();
    }

    private String version() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "0"; }
    }

    /** Downloads this app's APK from the latest GitHub release and installs it through the TV's own ADB (no "unknown sources" needed). */
    private void update() {
        run(() -> {
            log("Đang kiểm tra bản mới...");
            Release r = Release.newer(version(), "tv-setup.apk");
            if (r == null) { log("✓ Đang dùng bản mới nhất (" + version() + ")."); return; }
            log("Đang tải bản " + r.version + "...");
            File apk = new File(getExternalFilesDir(null), "tv-setup-update.apk");
            int[] last = {0};
            Release.download(r.url, apk, pct -> { if (pct >= last[0] + 25) { last[0] = pct; log("  " + pct + "%"); } });
            log("Đang cài. App sẽ tự đóng; vài giây sau mở lại TV Setup là bản " + r.version + ".");
            log(setup.install(apk));
        });
    }

    @Override protected void onResume() {
        super.onResume();
        showWeb();
        if (setup != null && !busy) run(() -> showStatus());          // back from Tailscale / Play Store: refresh the IPs
    }

    private void showWeb() {
        boolean on = RemoteService.enabled(this);
        webBtn.setText(on ? "Remote qua web: ĐANG BẬT" : "Remote qua web: đang tắt");
        if (!on) { webInfo.setText("Bật \"Remote qua web\" để điều khiển TV này từ trình duyệt của điện thoại hay máy tính, không cần cài gì."); return; }
        String where = lanIp.isEmpty() && tsIp.isEmpty() ? "http://<IP của TV>:" + RemoteService.PORT
                : (lanIp.isEmpty() ? "" : "http://" + lanIp + ":" + RemoteService.PORT + "  (cùng Wi-Fi)") + (lanIp.isEmpty() || tsIp.isEmpty() ? "" : "     hoặc     ")
                + (tsIp.isEmpty() ? "" : "http://" + tsIp + ":" + RemoteService.PORT + "  (qua Tailscale)");
        int n = RemoteService.pairedCount(this);
        webInfo.setText("Mở trên điện thoại / máy tính:   " + where + "\nLần đầu TV sẽ hiện mã 6 số để ghép.   Đã ghép: " + n + " thiết bị.   Tự chạy lại khi TV khởi động.");
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
        lanIp = lan; tsIp = ts;
        ui(() -> {
            showWeb();
            status.setTextColor(Color.rgb(70, 210, 131));
            status.setText(name + "\nIP trong nhà: " + (lan.isEmpty() ? "?" : lan) + "   ·   IP Tailscale: "
                    + (!ts.isEmpty() ? ts : tsInstalled ? "chưa vào mạng" : "chưa cài"));
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

    /** Installed: open it so it shows its code, which the person enters in the Tailscale admin console. Not installed: its Play Store page. */
    private void tailscale() {
        run(() -> {
            if (setup.has(Setup.TS_PKG)) {
                Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(Setup.TS_PKG);
                if (i == null) i = getPackageManager().getLaunchIntentForPackage(Setup.TS_PKG);
                if (i == null) { log("✗ Không mở được Tailscale"); return; }
                log("Mở Tailscale: TV hiện một mã. Không cần gõ tài khoản trên TV: trên máy tính/điện thoại mở login.tailscale.com/admin/machines, chọn Add device rồi nhập mã đó (hoặc quét mã QR). Xong thì vào menu của TV trong trang đó, chọn Disable key expiry.");
                startActivity(i);
            } else {
                log("Mở Google Play: chọn Cài đặt (Install), xong quay lại đây bấm \"Tailscale\" lần nữa để lấy mã thêm TV.");
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
