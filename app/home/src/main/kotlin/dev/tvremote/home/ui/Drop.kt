package dev.tvremote.home.ui

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
import dev.tvremote.home.DIM
import dev.tvremote.home.Font
import dev.tvremote.home.R

/**
 * A menu that drops from a button on the head (Inputs, Settings): a dark rounded panel under it, a row per choice, a line between
 * groups, the focused row white like the buttons. A Dialog, so the home's Home press and Back close it as they close the others.
 */
object Drop {
    /** Home dimmed behind a menu: 0.15 left it as bright as the menu, which did not stand out. Customize's first level the same. */
    const val MENU_DIM = 0.45f

    /** [on]: the state on the right with a green dot ("Connected"); [group]: rows of another group get a line above them. */
    class Row(val icon: Int, val label: String, val on: String? = null, val group: Int = 0, val folds: Boolean = false, val more: List<Row>? = null, val go: () -> Unit = {})

    private const val DIM = 0x8CFFFFFF.toInt()

    fun show(a: Activity, anchor: View, rows: List<Row>): Dialog {
        val d = a.resources.displayMetrics.density
        fun dp(v: Number) = (v.toFloat() * d).toInt()
        val dialog = Dialog(a, R.style.Menu)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8))
            // dark glass: lighter at the top like the cards, and dark enough below to read over a busy wallpaper (no blur before Android 12)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xEB343842.toInt(), 0xF2181A20.toInt())).apply {
                cornerRadius = dp(20).toFloat(); setStroke(dp(1), 0x40FFFFFF)
            }
        }
        val tint = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(0xFF111318.toInt(), Color.WHITE))
        var last = rows.firstOrNull()?.group
        fun add(list: List<Row>) {
        for (r in list) {
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
                addView(Font.on(TextView(a)).apply {
                    text = r.label; textSize = 16f; setTextColor(tint); isDuplicateParentStateEnabled = true
                    maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
                if (r.on != null) {
                    addView(View(a).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF34C759.toInt()) } },
                        LinearLayout.LayoutParams(dp(7), dp(7)).apply { leftMargin = dp(12) })
                    addView(Font.on(TextView(a)).apply {
                        text = r.on; textSize = 12f; isDuplicateParentStateEnabled = true
                        setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(0xFF5B5F6B.toInt(), DIM)))
                    }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(6) })
                }
                setOnClickListener {
                    val more = r.more
                    if (more == null) { dialog.dismiss(); r.go(); return@setOnClickListener }
                    // "More inputs": the folded rows open in its place, the menu stays
                    val idx = col.indexOfChild(this); col.removeViewAt(idx)
                    if (idx > 0 && col.getChildAt(idx - 1).layoutParams.height == dp(1)) col.removeViewAt(idx - 1)   // its own divider
                    last = rows.lastOrNull { x -> x.more == null }?.group
                    val first = col.childCount
                    add(more)
                    col.getChildAt(if (col.getChildAt(first).isFocusable) first else first + 1)?.requestFocus()   // a divider may come first
                }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        }
        add(rows)
        dialog.setContentView(col)
        // under the button, its left edge on the button's (a button in the right half: its right edge), kept inside the screen (Settings sits near the right edge)
        val at = IntArray(2).also { anchor.getLocationOnScreen(it) }
        val sw = a.resources.displayMetrics.widthPixels
        val w = maxOf(anchor.width, dp(300))
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.TOP or Gravity.START)
            setDimAmount(MENU_DIM)
            attributes = attributes.apply {
                x = (if (at[0] > sw / 2) at[0] + anchor.width - w else minOf(at[0], sw - w - dp(32))).coerceIn(dp(16), sw - w - dp(16)); y = at[1] + anchor.height + dp(8)
                width = w; height = WindowManager.LayoutParams.WRAP_CONTENT
            }
        }
        val bar = anchor.parent as? TopBar
        bar?.holdWord(true)
        dialog.setOnDismissListener { bar?.holdWord(false) }
        dialog.show()
        return dialog
    }
}
