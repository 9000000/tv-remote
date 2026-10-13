package dev.tvremote.setup;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shows the pairing code on the TV while a new phone or computer asks to control it. Closes by itself. */
public final class PairActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable watch = new Runnable() {
        @Override public void run() {
            if (RemoteService.pairingCode() == null) { finish(); return; }     // paired, or the code ran out
            ui.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String code = RemoteService.pairingCode();
        if (code == null) { finish(); return; }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(17, 20, 24));
        root.addView(line("Một thiết bị muốn điều khiển TV này", 26, Color.WHITE, false));
        root.addView(line("A device wants to control this TV", 18, Color.rgb(153, 163, 176), false));
        TextView big = line(code.substring(0, 3) + " " + code.substring(3), 96, Color.rgb(91, 140, 255), true);
        big.setPadding(0, 40, 0, 40);
        big.setLetterSpacing(0.1f);
        root.addView(big);
        root.addView(line("Nhập mã này trên thiết bị đó. Không phải bạn thì bấm Back.", 22, Color.WHITE, false));
        root.addView(line("Type this code on that device. If this is not you, press Back.", 18, Color.rgb(153, 163, 176), false));
        setContentView(root);
        ui.postDelayed(watch, 500);
    }

    @Override public void onBackPressed() { RemoteService.cancelPairing(); super.onBackPressed(); }

    @Override protected void onDestroy() { ui.removeCallbacks(watch); super.onDestroy(); }

    private TextView line(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
}
