package dev.tvremote.home.widgets

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvremote.home.Font
import dev.tvremote.home.R
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

private const val DIMC = 0xA6FFFFFF.toInt()
private const val INK = 0xFF0B0E14.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val CHIP_ON = 0x4D4F7CFF                                   // a layer chip in use: a blue tint with a blue edge
private const val CHIP_REST = 0x1FFFFFFF
private const val EDGE = 0xFF62D2FF.toInt()
private const val BODY = 0xE6FFFFFF.toInt()

/** A focusable text button: a faint pill, a bright one with the focus. */
internal fun Activity.pillButton(label: String, on: Boolean = false, click: () -> Unit): TextView {
    val d = resources.displayMetrics.density
    fun bg(f: Boolean) = GradientDrawable().apply {
        cornerRadius = 22 * d; setColor(if (f) WHITE else if (on) CHIP_ON else CHIP_REST)
        if (on && !f) setStroke((2 * d).toInt(), EDGE)
    }
    return Font.on(TextView(this)).apply {
        text = label; textSize = 16f; typeface = Font.medium; gravity = Gravity.CENTER; setSingleLine(true)
        setTextColor(Color.WHITE); background = bg(false); isFocusable = true; isClickable = true
        setPadding((20 * d).toInt(), (10 * d).toInt(), (20 * d).toInt(), (10 * d).toInt())
        setOnFocusChangeListener { v, f -> background = bg(f); (v as TextView).setTextColor(if (f) INK else Color.WHITE) }
        setOnClickListener { click() }
    }
}

/**
 * The reader of one headline, from the News card: the picture, the source and time, the title and the summary the feed gave in big
 * text, "previous / next" and "Open in browser" (only where an app answers a web link). The article itself is never fetched here.
 */
class NewsActivity : Activity() {
    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(dev.tvremote.home.Lang.wrap(base))
    private var i = 0
    private var items: List<News.Item> = emptyList()
    private lateinit var root: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        items = News.cached(this)
        i = intent.getIntExtra("i", 0).coerceIn(0, maxOf(0, items.size - 1))
        if (items.isEmpty()) { finish(); return }
        root = LinearLayout(this).apply { setBackgroundColor(INK) }
        setContentView(root)
        draw()
    }

    private fun draw() {
        val d = resources.displayMetrics.density
        fun dp(v: Number) = (v.toFloat() * d).toInt()
        val it = items[i]
        root.removeAllViews()
        root.orientation = LinearLayout.HORIZONTAL
        root.setPadding(dp(64), dp(40), dp(64), dp(40))
        val pic = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(CHIP_REST) }
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() { override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(16).toFloat()) } }
        }
        if (it.image.isNotEmpty()) Pics.into(this, pic, it.image, dp(640))
        root.addView(pic, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 4f))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val ago = it.at.takeIf { t -> t > 0 }?.let { t -> SimpleDateFormat("HH:mm · d/M", Locale.getDefault()).format(t) }
        col.addView(Font.on(TextView(this)).apply { text = listOf(it.src, ago).filterNotNull().filter { s -> s.isNotEmpty() }.joinToString("  ·  "); textSize = 16f; setTextColor(DIMC) })
        col.addView(Font.on(TextView(this)).apply { text = it.title; textSize = 30f; typeface = Font.bold; setTextColor(Color.WHITE); maxLines = 4; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(8), 0, dp(12)) })
        val sum = ScrollView(this).apply { isFocusable = false; isVerticalScrollBarEnabled = false }
        sum.addView(Font.on(TextView(this)).apply { text = it.summary.ifEmpty { it.link }; textSize = 21f; setLineSpacing(0f, 1.15f); setTextColor(BODY) })
        col.addView(sum, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, 0); clipChildren = false; clipToPadding = false }
        val gap = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(12) }
        var first: View? = null
        if (i > 0) bar.addView(pillButton("‹ " + getString(R.string.rd_prev)) { i--; draw() }.also { f -> first = first ?: f }, gap)
        if (i < items.size - 1) bar.addView(pillButton(getString(R.string.rd_next) + " ›") { i++; draw() }.also { f -> first = f }, gap)
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(it.link))
        if (view.resolveActivity(packageManager) != null)
            bar.addView(pillButton(getString(R.string.rd_open)) { try { startActivity(view) } catch (e: Exception) { } }, gap)
        col.addView(bar)
        root.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 6f).apply { leftMargin = dp(40) })
        (first ?: bar.getChildAt(0))?.requestFocus()
    }
}

/**
 * The weather map, from the weather card: Windy's own embedded map at the card's place, with the layer to look at (rain, wind,
 * temperature, clouds, PM2.5) and the next days beside it. Back closes. Needs the internet, and says so before it shows a blank page.
 */
class WeatherMapActivity : Activity() {
    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(dev.tvremote.home.Lang.wrap(base))
    private var web: WebView? = null
    private var lat = 0.0; private var lon = 0.0
    private val layers = listOf("rain" to R.string.map_rain, "wind" to R.string.map_wind, "temp" to R.string.map_temp, "clouds" to R.string.map_clouds, "pm2p5" to R.string.map_pm)
    private val chips = HashMap<String, TextView>()

    private fun url(layer: String) = "https://embed.windy.com/embed2.html?lat=$lat&lon=$lon&detailLat=$lat&detailLon=$lon&width=900&height=700&zoom=7&level=surface" +
        "&overlay=$layer&product=ecmwf&menu=&message=true&marker=true&calendar=now&pressure=&type=map&location=coordinates&detail=&metricWind=km%2Fh&metricTemp=%C2%B0C&radarRange=-1"

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        fun dp(v: Number) = (v.toFloat() * d).toInt()
        val root = LinearLayout(this).apply { setBackgroundColor(INK); setPadding(dp(48), dp(32), dp(48), dp(32)) }
        setContentView(root)
        val place = try { JSONObject(getSharedPreferences("weather", MODE_PRIVATE).getString("place", "") ?: "") } catch (e: Exception) { null }
        if (place == null || !place.has("lat")) {
            root.gravity = Gravity.CENTER
            root.addView(Font.on(TextView(this)).apply { text = getString(R.string.map_none); textSize = 22f; setTextColor(DIMC); gravity = Gravity.CENTER })
            return
        }
        lat = place.optDouble("lat"); lon = place.optDouble("lon")
        val wv = WebView(this).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            isFocusable = false; isFocusableInTouchMode = false                        // the D-pad stays on the chips: a map would swallow it
            setBackgroundColor(INK); webViewClient = WebViewClient()
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() { override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(16).toFloat()) } }
        }
        web = wv
        root.addView(wv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 7f))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        col.addView(Font.on(TextView(this)).apply { text = place.optString("name"); textSize = 26f; typeface = Font.bold; setTextColor(Color.WHITE); maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
        val strip = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(14), 0, dp(14)); clipChildren = false; clipToPadding = false }
        for ((k, w) in layers) {
            val c = pillButton(getString(w), k == "rain") { show(k) }
            chips[k] = c; strip.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        col.addView(strip)
        val fc = Weather.cached(this)
        if (fc != null) {
            val fmtIn = SimpleDateFormat("yyyy-MM-dd", Locale.US); val fmtOut = SimpleDateFormat("EEE d/M", Locale.getDefault())
            for (day in fc.days) {
                val name = try { fmtOut.format(fmtIn.parse(day.date)!!) } catch (e: Exception) { day.date }
                col.addView(Font.on(TextView(this)).apply { text = "$name   ${day.max}° / ${day.min}°"; textSize = 17f; setTextColor(BODY); setPadding(0, dp(4), 0, dp(4)) })
            }
        }
        root.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f).apply { leftMargin = dp(32) })
        show("rain")
        chips["rain"]?.requestFocus()
    }

    private fun show(layer: String) {
        for ((k, c) in chips) c.alpha = if (k == layer) 1f else 0.75f
        web?.loadUrl(url(layer))
    }

    override fun onDestroy() { web?.destroy(); super.onDestroy() }
}
