package dev.tvremote.home

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A menu that drops from a button on the head (Inputs, Settings): a dark rounded panel under it, a row per choice, a line between
 * groups, the focused row white like the buttons. A Dialog, so the home's Home press and Back close it as they close the others.
 */
object Drop {
    /** [on]: the state on the right with a green dot ("Connected"); [group]: rows of another group get a line above them. */
    class Row(val icon: Int, val label: String, val on: String? = null, val group: Int = 0, val go: () -> Unit)

    private const val DIM = 0x8CFFFFFF.toInt()

    fun show(a: Activity, anchor: View, rows: List<Row>): Dialog {
        val d = a.resources.displayMetrics.density
        fun dp(v: Number) = (v.toFloat() * d).toInt()
        val dialog = Dialog(a, R.style.Menu)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(0xF2191B21.toInt()); setStroke(dp(1), 0x26FFFFFF) }
        }
        val tint = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(0xFF111318.toInt(), Color.WHITE))
        var last = rows.firstOrNull()?.group
        for (r in rows) {
            if (r.group != last) {
                col.addView(View(a).apply { setBackgroundColor(0x1FFFFFFF) }, LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(14), dp(6), dp(14), dp(6)) })
                last = r.group
            }
            col.addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                isFocusable = true; isClickable = true
                minimumHeight = dp(50); setPadding(dp(14), 0, dp(16), 0)
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.WHITE) })
                    addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
                }
                addView(ImageView(a).apply { setImageResource(r.icon); imageTintList = tint; isDuplicateParentStateEnabled = true }, LinearLayout.LayoutParams(dp(22), dp(22)))
                addView(TextView(a).apply {
                    text = r.label; textSize = 16f; setTextColor(tint); isDuplicateParentStateEnabled = true
                    maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
                if (r.on != null) {
                    addView(View(a).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF34C759.toInt()) } },
                        LinearLayout.LayoutParams(dp(7), dp(7)).apply { leftMargin = dp(12) })
                    addView(TextView(a).apply {
                        text = r.on; textSize = 12f; isDuplicateParentStateEnabled = true
                        setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(0xFF5B5F6B.toInt(), DIM)))
                    }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6) })
                }
                setOnClickListener { dialog.dismiss(); r.go() }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        dialog.setContentView(col)
        // under the button, its left edge on the button's, kept inside the screen (Settings sits near the right edge)
        val at = IntArray(2).also { anchor.getLocationOnScreen(it) }
        val sw = a.resources.displayMetrics.widthPixels
        val w = maxOf(anchor.width, dp(300))
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.TOP or Gravity.START)
            setDimAmount(0.25f)
            attributes = attributes.apply {
                x = minOf(at[0], sw - w - dp(32)).coerceAtLeast(dp(16)); y = at[1] + anchor.height + dp(8)
                width = w; height = WindowManager.LayoutParams.WRAP_CONTENT
            }
        }
        dialog.show()
        return dialog
    }
}
