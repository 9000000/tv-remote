package dev.tvremote.home.ui

import dev.tvremote.tvui.Motion
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvremote.home.Font

/** One round icon button of the [TopBar]: white on the glass, dark while the bright disc is under it (white under the glass pane). */
class BarBtn(c: Context, icon: Int, var label: String, dp: (Number) -> Int, onClick: () -> Unit) : ImageView(c) {
    init {
        isFocusable = true; isClickable = true
        setImageResource(icon)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        ink()
        contentDescription = label
        setOnClickListener { onClick() }
    }
    fun ink() { imageTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(if (Motion.glass) Color.WHITE else INK, Color.WHITE)) }
    companion object { const val INK = 0xFF111318.toInt() }   // dark on the bright disc
}

/**
 * The three round buttons (inputs, network, settings) in ONE small frosted capsule at the top right. A bright disc glides (a spring)
 * to the focused button and its word shows under the capsule. After owntv's top bar (MIT), the look only.
 */
class TopBar(c: Context, private val dp: (Number) -> Int) : LinearLayout(c) {
    val capsule = FrameLayout(c)                                      // the Drop menus hang under it
    private val stage = FrameLayout(c)
    private val row = LinearLayout(c).apply { orientation = HORIZONTAL; clipChildren = false }
    private val disc = View(c)
    private val word = Font.on(TextView(c))
    val buttons: List<BarBtn> get() = (0 until row.childCount).map { row.getChildAt(it) as BarBtn }
    var focusIn: (View) -> Unit = {}

    init {
        orientation = VERTICAL; gravity = Gravity.END; clipChildren = false; clipToPadding = false
        setPadding(0, dp(10), 0, 0)
        Frost.on(capsule, dp(25).toFloat(), dp(1))
        capsule.clipChildren = false
        stage.setPadding(dp(5), dp(5), dp(5), dp(5)); stage.clipChildren = false; stage.clipToPadding = false
        disc.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
        disc.alpha = 0f
        stage.addView(disc, FrameLayout.LayoutParams(dp(40), dp(40)))
        stage.addView(row, FrameLayout.LayoutParams(-2, dp(40)))
        capsule.addView(stage, FrameLayout.LayoutParams(-2, -2))
        addView(capsule, LayoutParams(-2, -2))
        word.apply {
            textSize = 15f; setTextColor(Color.WHITE); maxLines = 1; gravity = Gravity.END
            setShadowLayer(dp(6).toFloat(), 0f, dp(1).toFloat(), 0x80000000.toInt()); alpha = 0f
            setPadding(0, dp(6), dp(8), 0)
            minHeight = dp(30)                                        // the line's room is kept: the row below never moves
        }
        addView(word, LayoutParams(-2, -2))
    }

    fun add(b: BarBtn) {
        row.addView(b, LinearLayout.LayoutParams(dp(40), dp(40)).apply { if (row.childCount > 0) leftMargin = dp(6) })
        b.setOnFocusChangeListener { v, f ->
            if (f) { glideTo(v as BarBtn); focusIn(v) }
            else v.post { if (buttons.none { it.isFocused }) { disc.animate().cancel(); disc.animate().alpha(0f).setDuration(160).start(); word.animate().alpha(0f).setDuration(160).start() } }
        }
    }

    /** A Drop menu hangs under the capsule, over the word line: the word hid behind its 92% glass as a ghost half-line (smoke, "Settin" under the menu's top edge). */
    fun holdWord(on: Boolean) { word.animate().cancel(); word.alpha = if (on) 0f else if (buttons.any { it.isFocused }) 1f else 0f }

    /** Text under the capsule follows a change of the focused button's word (the network name arrives late). */
    fun relabel(b: BarBtn) { if (b.isFocused) word.text = b.label }

    private fun glideTo(b: BarBtn) {
        word.text = b.label
        disc.animate().cancel()
        if (b.imageTintList?.getColorForState(intArrayOf(android.R.attr.state_focused), 0) != (if (Motion.glass) Color.WHITE else BarBtn.INK)) for (x in buttons) x.ink()
        if (Motion.glass) {                                           // the glass pane comes here as on every other stop ([Glass]): no second look
            disc.alpha = 0f
            word.animate().cancel(); if (Motion.on) word.animate().alpha(1f).setDuration(200).start() else word.alpha = 1f
            return
        }
        val hidden = disc.alpha < 0.05f
        if (!Motion.on || hidden) {                                   // first arrival: no glide from a stale place
            disc.translationX = b.left.toFloat()
            disc.animate().alpha(1f).setDuration(if (Motion.on) 160 else 0).start()
        } else disc.animate().alpha(1f).translationX(b.left.toFloat()).setDuration(Motion.ms(460)).setInterpolator(Motion.spring).start()
        word.animate().cancel()
        if (Motion.on) word.animate().alpha(1f).setDuration(200).start() else word.alpha = 1f
    }
}
