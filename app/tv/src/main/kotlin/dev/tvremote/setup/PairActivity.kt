package dev.tvremote.setup

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Shows the pairing code on the TV while a new phone or computer asks to control it. Closes by itself. */
class PairActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val watch = object : Runnable {
        override fun run() {
            if (RemoteService.pairingCode() == null) { finish(); return }     // paired, or the code ran out
            ui.postDelayed(this, 500)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val code = RemoteService.pairingCode()
        if (code == null) { finish(); return }
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(Color.rgb(17, 20, 24))
        root.addView(line("Một thiết bị muốn điều khiển TV này", 26, Color.WHITE, false))
        root.addView(line("A device wants to control this TV", 18, Color.rgb(153, 163, 176), false))
        val big = line(code.substring(0, 3) + " " + code.substring(3), 96, Color.rgb(91, 140, 255), true)
        big.setPadding(0, 40, 0, 40)
        big.letterSpacing = 0.1f
        root.addView(big)
        root.addView(line("Nhập mã này trên thiết bị đó. Không phải bạn thì bấm Back.", 22, Color.WHITE, false))
        root.addView(line("Type this code on that device. If this is not you, press Back.", 18, Color.rgb(153, 163, 176), false))
        setContentView(root)
        ui.postDelayed(watch, 500)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() { RemoteService.cancelPairing(); super.onBackPressed() }

    override fun onDestroy() { ui.removeCallbacks(watch); super.onDestroy() }

    private fun line(s: String, sp: Int, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = s; t.textSize = sp.toFloat(); t.setTextColor(color); t.gravity = Gravity.CENTER
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }
}
