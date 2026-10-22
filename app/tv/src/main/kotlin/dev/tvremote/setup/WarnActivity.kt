package dev.tvremote.setup

import dev.tvremote.core.T
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Shown over whatever is on the TV by [Guard]: the sleep timer is about to end (with +15 minutes, now, or cancel), bedtime
 * has come (the TV goes to standby in a minute), or an app's minutes for today are used up. Bedtime and limits offer no way
 * out here, on purpose: more time is granted from a paired phone or computer. Or the memory has stayed nearly full: free it
 * (the apps waiting in the background closed), see what holds it (the Health tab, on the TV), or skip.
 */
class WarnActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var count: TextView
    private var kind = ""
    private var until = 0L

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        kind = intent.getStringExtra("kind") ?: "sleep"
        until = intent.getLongExtra("until", 0L)
        val pkg = intent.getStringExtra("pkg") ?: ""
        val name = if (pkg.isEmpty()) "" else try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }

        val page = LinearLayout(this)
        page.gravity = Gravity.CENTER
        page.setBackgroundColor(Color.argb(200, 0, 0, 0))
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.background = GradientDrawable().apply { setColor(Color.rgb(25, 29, 35)); cornerRadius = dp(20).toFloat() }
        card.setPadding(dp(40), dp(32), dp(40), dp(28))
        val icon = when (kind) { "sleep" -> "😴"; "bed" -> "🌙"; "ram" -> "🧠"; else -> "⏳" }
        val (total, avail) = Guard.memory()
        card.addView(text(icon, 48, false))
        card.addView(text(when (kind) {
            "sleep" -> T("warn.the_tv_is_turning")
            "bed" -> T("warn.it_is_bedtime")
            "ram" -> T("warn.the_tv_s_memory")
            else -> T("warn.time_is_up_for", "name" to name)
        }, 28, true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        val bed = Rules.bedNow(Rules.load(this), System.currentTimeMillis())
        card.addView(text(when (kind) {
            "sleep" -> T("warn.the_sleep_timer_is")
            "ram" -> T("warn.in_use", "pct" to (total - avail) * 100 / maxOf(1L, total), "used" to total - avail, "total" to total)
            "bed" -> T("warn.bed_range", "from" to (bed?.optString("from") ?: ""), "to" to (bed?.optString("to") ?: ""))
            else -> T("warn.the_app_was_closed")
        }, 16, false).apply { setTextColor(Color.rgb(170, 178, 189)); maxWidth = dp(560) }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        count = text("", 22, true)
        if (kind != "limit") card.addView(count, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })

        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER
        val buttons = ArrayList<Button>()
        if (kind == "sleep") {
            buttons.add(button(T("auto.plus_15")) { Rules.change(this, JSONObject().put("do", "sleep_add").put("minutes", 15)); finish() })
            buttons.add(button(T("warn.turn_off_now")) { Rules.change(this, JSONObject().put("do", "sleep").put("minutes", 0)); standby(); finish() })
            buttons.add(button(T("warn.cancel_the_timer")) { Rules.change(this, JSONObject().put("do", "sleep").put("minutes", 0)); finish() })
        } else if (kind == "ram") {
            buttons.add(button(T("warn.free_ram")) { freeRam(buttons) })
            buttons.add(button(T("warn.see_the_apps")) {
                startActivity(android.content.Intent(this, ManageActivity::class.java).putExtra(ManageActivity.PASS, RemoteService.selfPass).putExtra(ManageActivity.TAB, "health"))
                finish()
            })
            buttons.add(button(T("warn.skip")) { finish() })
        } else buttons.add(button(T("common.ok")) { finish() })
        buttons.forEachIndexed { i, b -> row.addView(b, LinearLayout.LayoutParams(-2, dp(52)).apply { if (i > 0) leftMargin = dp(12) }) }
        card.addView(row, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(22) })
        page.addView(card, LinearLayout.LayoutParams(-2, -2))
        setContentView(page)
        buttons[0].isFocusableInTouchMode = true                    // opened by ADB, the TV is still in touch mode: OK must land somewhere
        buttons[0].requestFocus()
        tick.run()
    }

    /** The countdown; the screen goes away by itself when the time is up, or the timer or bedtime was changed from a remote. */
    private val tick = object : Runnable {
        override fun run() {
            if (kind == "limit" || kind == "ram") return
            val left = until - System.currentTimeMillis()
            val o = Rules.load(this@WarnActivity)
            val off = kind == "sleep" && o.optLong("sleep_at") != until
                || kind == "bed" && !(Rules.inBedtime(o, System.currentTimeMillis()) && System.currentTimeMillis() >= o.optLong("snooze_until"))   // more time granted, bedtime off or moved: the Guard's own test
            if (left <= 0 || off) { finish(); return }
            val s = ((left + 999) / 1000).toInt()
            count.text = T("warn.off_in", "time" to "%d:%02d".format(s / 60, s % 60))
            ui.postDelayed(this, 500)
        }
    }

    /** Free RAM: the Guard closes what waits in the background, then the screen says how much came back and goes. */
    private fun freeRam(buttons: List<Button>) {
        buttons.forEach { it.isEnabled = false }
        count.text = T("warn.freeing")
        Thread {
            val got = try { Guard.freeNow() } catch (e: Exception) { null }
            ui.post {
                if (isFinishing || isDestroyed) return@post
                count.text = if (got == null) T("warn.could_not_tv_setup")
                    else T("warn.got_back_got_mb", "got" to got)
                ui.postDelayed({ finish() }, if (got == null) 4000 else 2500)
            }
        }.start()
    }

    override fun onDestroy() { ui.removeCallbacks(tick); super.onDestroy() }

    /** "Turn off now" from this screen: the Guard would do it within a minute, this is sooner. */
    private fun standby() { Thread { try { Guard.standbyNow() } catch (ignored: Exception) { } }.start() }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun text(s: String, sp: Int, bold: Boolean): TextView = TextView(this).apply {
        text = s; textSize = sp.toFloat(); setTextColor(Color.rgb(233, 236, 241)); gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(label: String, run: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 17f
        setTextColor(Color.WHITE)
        setPadding(dp(22), 0, dp(22), 0)
        stateListAnimator = null
        val idle = GradientDrawable().apply { setColor(Color.rgb(34, 39, 47)); cornerRadius = dp(14).toFloat() }
        val focus = GradientDrawable().apply { setColor(Color.rgb(59, 115, 246)); cornerRadius = dp(14).toFloat() }
        background = idle
        setOnFocusChangeListener { v, f -> v.background = if (f) focus else idle }
        setOnClickListener { run() }
        visibility = View.VISIBLE
    }
}
