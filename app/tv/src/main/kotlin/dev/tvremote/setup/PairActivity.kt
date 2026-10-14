package dev.tvremote.setup

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

/** Shows the pairing code on the TV while a new phone or computer asks to control it. Closes by itself. */
class PairActivity : Activity() {
    private val vi = Locale.getDefault().language == "vi"
    private fun t(v: String, e: String) = if (vi) v else e
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
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(Color.rgb(13, 16, 20))

        val card = LinearLayout(this)                                 // one card in the middle of the screen
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.background = box(Color.rgb(25, 29, 35), 24)
        card.setPadding(dp(56), dp(36), dp(56), dp(36))
        val icon = ImageView(this)
        icon.setImageResource(R.drawable.ic_phone)
        icon.setColorFilter(Color.rgb(91, 140, 255))
        card.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))
        card.addView(line(t("Một thiết bị muốn điều khiển TV này", "A device wants to control this TV"), 24, Color.WHITE, true), gap(14))
        card.addView(line(t("Nhập mã này trên thiết bị đó", "Type this code on that device"), 16, Color.rgb(140, 150, 163), false), gap(6))
        val big = line(code.substring(0, 3) + " " + code.substring(3), 72, Color.WHITE, true)
        big.letterSpacing = 0.12f
        big.background = box(Color.rgb(34, 39, 47), 18)
        big.setPadding(dp(36), dp(10), dp(36), dp(10))
        card.addView(big, gap(22))
        card.addView(line(t("Mã dùng được 2 phút. Không phải bạn thì bấm Back.", "The code is good for 2 minutes. If this is not you, press Back."), 15, Color.rgb(140, 150, 163), false), gap(22))
        root.addView(card)
        setContentView(root)
        ui.postDelayed(watch, 500)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() { RemoteService.cancelPairing(); super.onBackPressed() }

    override fun onDestroy() { ui.removeCallbacks(watch); super.onDestroy() }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun gap(top: Int) = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(top); gravity = Gravity.CENTER_HORIZONTAL }

    private fun box(color: Int, radius: Int) = GradientDrawable().apply { cornerRadius = dp(radius).toFloat(); setColor(color) }

    private fun line(s: String, sp: Int, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = s; t.textSize = sp.toFloat(); t.setTextColor(color); t.gravity = Gravity.CENTER
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }
}
