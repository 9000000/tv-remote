package dev.tvremote.setup

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** A row of buttons that continues on the next line when the screen is too narrow (a phone, a TV in portrait). */
internal class FlowRow(context: Context, private val gap: Int) : ViewGroup(context) {
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val width = MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
        val limited = MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED
        var x = 0
        var y = 0
        var line = 0
        var widest = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == View.GONE) continue
            c.measure(if (limited) MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST) else widthSpec, MeasureSpec.UNSPECIFIED)
            if (limited && x > 0 && x + c.measuredWidth > width) {     // does not fit: start the next line
                x = 0
                y += line + gap
                line = 0
            }
            x += c.measuredWidth + gap
            line = maxOf(line, c.measuredHeight)
            widest = maxOf(widest, x - gap)
        }
        setMeasuredDimension(if (limited) MeasureSpec.getSize(widthSpec) else widest + paddingLeft + paddingRight, y + line + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var line = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == View.GONE) continue
            if (x > 0 && x + c.measuredWidth > width) {
                x = 0
                y += line + gap
                line = 0
            }
            c.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + c.measuredWidth, paddingTop + y + c.measuredHeight)
            x += c.measuredWidth + gap
            line = maxOf(line, c.measuredHeight)
        }
    }
}
