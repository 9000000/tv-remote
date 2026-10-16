package dev.tvremote.phone

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

// Colours, words in two languages, and the small view helpers every screen is built from.

internal const val BG = 0xff111418.toInt()
internal const val DL_CODE = "2397346"                         // AFTVnews short code for releases/latest/download/tv-setup.apk: Downloader takes it as downloader://<code> or in its address field
internal const val CARD = 0xff1c2026.toInt()
internal const val BTN = 0xff2a3038.toInt()
internal const val ACCENT = 0xff3366e8.toInt()                 // fills, with white on top (5:1); the old light #5b8cff took only 3:1 with light text
internal const val ACCENT_TEXT = 0xff7ba2ff.toInt()            // the same blue as words or an icon on the dark screen (6.7:1)
internal const val HELD = 0xffe0a030.toInt()
internal const val TEXT = 0xffe8eaed.toInt()
internal const val MUTED = 0xff9aa0a6.toInt()
internal const val RED = 0xffff8a80.toInt()                    // the power icon, "Disconnect"
internal const val GREEN = 0xff2e7d4f.toInt()                  // the update banner
internal const val ERR_TEXT = 0xffffb4a8.toInt()
internal const val ERR_BG = 0xff3a2020.toInt()
internal const val INSTALL_RESULT = "dev.tvremote.phone.INSTALL_RESULT"
internal const val PICK_FILE = 77
internal const val SPEAK = 78

internal var VI = "vi" == Locale.getDefault().language             // set again in MainActivity.onCreate from the Settings choice

/** The Vietnamese or the English words, by the language chosen in Settings (or the phone's). */
internal fun t(vi: String, en: String): String = if (VI) vi else en

/** The language chosen in the app's Settings ("" = the phone's), for the parts that run without the main screen too. */
internal fun loadLang(c: Context) {
    val lang = c.getSharedPreferences("tvs", Context.MODE_PRIVATE).getString("lang", "") ?: ""
    VI = "vi" == (if (lang.isEmpty()) Locale.getDefault().language else lang)
}

internal fun Context.dp(v: Float): Int = (v * resources.displayMetrics.density).roundToInt()
internal fun Context.dp(v: Int): Int = dp(v.toFloat())

internal fun Context.col(): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.VERTICAL
    return l
}

internal fun Context.gap(top: Int): LinearLayout.LayoutParams {
    val p = LinearLayout.LayoutParams(-1, -2)
    p.topMargin = dp(top)
    return p
}

internal fun Context.cell(height: Int, leftGap: Boolean): LinearLayout.LayoutParams {
    val p = LinearLayout.LayoutParams(0, dp(height), 1f)
    if (leftGap) p.leftMargin = dp(6)
    return p
}

internal fun Context.box(color: Int, radius: Int): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = dp(radius).toFloat()
    return g
}

internal fun Context.label(s: String, sp: Int, color: Int): TextView {
    val v = TextView(this)
    v.text = s
    v.textSize = sp.toFloat()
    v.setTextColor(color)
    return v
}

internal fun Context.head(s: String): TextView {
    val v = label(s, 12, MUTED)
    v.typeface = Typeface.DEFAULT_BOLD
    v.letterSpacing = 0.08f
    return v
}

internal fun Context.note(s: String): TextView {
    val v = label(s, 13, MUTED)
    v.setPadding(0, dp(6), 0, 0)
    v.setLineSpacing(dp(3).toFloat(), 1f)
    return v
}

internal fun Context.button(s: String, color: Int): Button {
    val b = Button(this)
    b.text = s
    b.setAllCaps(false)
    b.setTextColor(if (color == ACCENT) Color.WHITE else TEXT)
    b.textSize = 15f
    b.background = box(color, 14)
    b.stateListAnimator = null
    b.setPadding(dp(14), 0, dp(14), 0)
    b.minHeight = 0
    b.minimumHeight = 0
    return b
}

/** A button that shows only an icon; the words go to screen readers. "@name" = res/drawable/ic_name (dev/gen_icons.py), anything else is text. */
/** The remote's top line, "TV name · what is going on": the name above, the rest under it, smaller; one line each, so a long
 *  name never pushes the rest away. Without " · " the words take both lines. [name] is the line above, added by the screen. */
internal class StatusText(c: Context) : TextView(c) {
    val name = TextView(c).apply {
        setTextColor(TEXT); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END; visibility = GONE
    }
    init { setTextColor(MUTED); textSize = 12f; ellipsize = android.text.TextUtils.TruncateAt.END; maxLines = 2 }
    override fun setText(text: CharSequence?, type: BufferType?) {
        val s = text?.toString() ?: ""
        val i = s.indexOf(" · ")
        @Suppress("SENSELESS_COMPARISON") val n = name as TextView?          // null while TextView's own constructor sets its first text
        if (n == null) { super.setText(text, type); return }
        n.text = if (i < 0) "" else s.substring(0, i)
        n.visibility = if (i < 0) GONE else VISIBLE
        maxLines = if (i < 0) 2 else 1
        super.setText(if (i < 0) s else s.substring(i + 3), type)
    }
}

/** The app's own icon, rounded, at the head of the list of TVs and of the remote. */
internal fun Context.logo(sizeDp: Int): android.widget.ImageView = android.widget.ImageView(this).apply {
    setImageResource(R.drawable.icon)
    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
    background = GradientDrawable().apply { cornerRadius = sizeDp * resources.displayMetrics.density / 4 }
    clipToOutline = true
    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
}

internal fun Context.icon(symbol: String, says: String, color: Int): IconButton {
    val b = IconButton(this)
    val ink = if (color == ACCENT) Color.WHITE else TEXT
    b.setIcon(symbol, if (symbol == "@power") RED else ink)
    b.setAllCaps(false)
    b.setTextColor(ink)
    b.textSize = 20f
    b.contentDescription = says
    b.background = box(color, 16)
    b.stateListAnimator = null
    b.setPadding(0, 0, 0, 0)
    b.minHeight = 0
    b.minimumHeight = 0
    b.minWidth = 0
    b.minimumWidth = 0
    return b
}

internal fun Context.row(s: String, onClick: () -> Unit): Button {
    val b = button(s, CARD)
    b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
    b.setPadding(dp(14), dp(12), dp(14), dp(12))
    b.setOnClickListener { onClick() }
    b.layoutParams = gap(6)
    return b
}

internal fun Context.input(hint: String): EditText {
    val e = EditText(this)
    e.hint = hint
    e.setHintTextColor(MUTED)
    e.setTextColor(TEXT)
    e.textSize = 15f
    e.setSingleLine(true)
    e.background = box(CARD, 12)
    e.setPadding(dp(14), dp(12), dp(14), dp(12))
    e.minHeight = dp(48)
    return e
}

/** [v] as a dialog's body: room around it, and a box lighter than the dialog, which is CARD itself. */
internal fun Context.inDialog(v: View): FrameLayout {
    v.background = box(BTN, 12)
    val wrap = FrameLayout(this)
    wrap.setPadding(dp(24), dp(12), dp(24), dp(4))
    wrap.addView(v, FrameLayout.LayoutParams(-1, -2))
    return wrap
}

internal fun Activity.hideKeyboard() {
    val f = currentFocus
    if (f != null) (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(f.windowToken, 0)
}

internal fun Context.oval(color: Int): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.OVAL
    g.setColor(color)
    return g
}

/**
 * A menu whose choices look like what they are: buttons. Each has a title, a line saying what it does, and an arrow.
 * [primary] is the one to press first. Each item: title, explanation, action. A title "@name|Title" gets the icon ic_name in
 * front, so the list can be scanned without reading every line (drawn icons, not emoji: those look different on every phone).
 */
internal fun Activity.menu(title: String, primary: Int, items: List<Triple<String, String, () -> Unit>>) {
    val list = col()
    list.setPadding(dp(16), dp(8), dp(16), dp(4))
    var dialog: AlertDialog? = null
    items.forEachIndexed { i, (title, what, run) ->
        val art = if (title.startsWith("@")) title.substringBefore("|").substring(1) else null
        val name = if (art != null) title.substringAfter("|") else title
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(14), dp(12), dp(10), dp(12))
        val color = if (i == primary) ACCENT else BTN
        val normal = box(color, 14)
        row.background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x40ffffff), normal, null)
        val id = if (art != null) resources.getIdentifier("ic_$art", "drawable", packageName) else 0
        if (id != 0) {
            val img = android.widget.ImageView(this)
            img.setImageResource(id)
            img.setColorFilter(if (i == primary) Color.WHITE else ACCENT_TEXT)
            val ilp = LinearLayout.LayoutParams(dp(24), dp(24))
            ilp.rightMargin = dp(14)
            row.addView(img, ilp)
        }
        val texts = col()
        val head = label(name, 16, if (i == primary) Color.WHITE else TEXT)
        head.typeface = Typeface.DEFAULT_BOLD
        texts.addView(head)
        if (what.isNotEmpty()) texts.addView(label(what, 13, if (i == primary) 0xffe3ebff.toInt() else MUTED))
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label("›", 24, if (i == primary) Color.WHITE else MUTED))
        row.isClickable = true
        row.isFocusable = true
        row.contentDescription = name
        row.setOnClickListener { dialog?.dismiss(); run() }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(8)
        list.addView(row, lp)
    }
    val sv = ScrollView(this)
    sv.addView(list)
    dialog = AlertDialog.Builder(this).setTitle(title).setView(sv).setNegativeButton(t("Đóng", "Close"), null).show()
}
