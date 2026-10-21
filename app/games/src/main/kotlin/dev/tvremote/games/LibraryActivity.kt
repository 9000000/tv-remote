package dev.tvremote.games

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.io.File

/** The launcher screen: a grid of the games found, + a tile that shows the "scan to add" QR. */
class LibraryActivity : ComponentActivity() {
    private lateinit var server: GameServer
    private lateinit var root: FrameLayout
    private var overlay: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(0xFF0B0D12.toInt()) }
        setContentView(root)
        server = GameServer.acquire(this)
        intent.getStringExtra("cat_base")?.let { Catalog.setDebug(this, it, intent.getStringExtra("cat_key")) }   // debug builds only
        firstRun()
        val prefs = getSharedPreferences("games", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT in 23..32 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            && !prefs.getBoolean("asked", false)) {
            prefs.edit().putBoolean("asked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
        reload()
        refreshCatalog()
    }

    /** The site's list (a day old at most) in the background; the library is drawn again when it changed. */
    private fun refreshCatalog() {
        Thread { if (Catalog.refresh(applicationContext, intent.hasExtra("cat_base"))) runOnUiThread { if (!isDestroyed && overlay == null) reload() } }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(code: Int, p: Array<String>, r: IntArray) { super.onRequestPermissionsResult(code, p, r); reload() }

    /** The bundled homebrew goes to files/roms once, so the library is never empty; deleting it sticks. */
    private fun firstRun() {
        val prefs = getSharedPreferences("games", MODE_PRIVATE)
        if (prefs.getBoolean("seeded", false)) return
        try {
            assets.open("homebrew/roborun.nes").use { i -> File(Systems.romsDir(this), "Robo Run (homebrew).nes").outputStream().use { i.copyTo(it) } }
            prefs.edit().putBoolean("seeded", true).apply()
        } catch (e: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        server.inGame = false; server.webMode = false; server.clearLayouts(); server.setSystem("nes")
        server.onKey = { _, _, _ -> }
        server.onPlayers = { }
        server.onAdded = { runOnUiThread { reload() } }
        reload()
    }

    override fun onDestroy() { GameServer.release(); super.onDestroy() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (overlay != null) closeOverlay() else super.onBackPressed() }

    private fun closeOverlay() { overlay?.let { root.removeView(it) }; overlay = null }

    private fun reload() {
        if (!::root.isInitialized) return
        overlay = null
        root.removeAllViews()
        val games = Systems.scan(this)
        val webs = Systems.webGames(this)
        val cat = Catalog.peek(this)
        val hidden = getSharedPreferences("games", MODE_PRIVATE).getStringSet("hidden", emptySet()) ?: emptySet()
        val have = games.map { it.name.lowercase() }.toSet()
        val en = java.util.Locale.getDefault().language != "vi"
        val feat = (0 until (cat.optJSONArray("web")?.length() ?: 0)).mapNotNull { cat.optJSONArray("web")?.optJSONObject(it) }
            .filter { it.optBoolean("on", true) && it.optString("url").matches(Regex("https?://.+")) && "w:" + it.optString("id") !in hidden }
        val brew = (0 until (cat.optJSONArray("homebrew")?.length() ?: 0)).mapNotNull { cat.optJSONArray("homebrew")?.optJSONObject(it) }
            .filter { it.optBoolean("on", true) && "h:" + it.optString("id") !in hidden && it.optString("name").lowercase() !in have
                && Systems.list(this).any { s -> s.id == it.optString("system") } }
        if (games.isEmpty() && webs.isEmpty() && feat.isEmpty() && brew.isEmpty()) {
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                addView(addPanel(server))
                addView(label(getString(R.string.empty_hint), 16f, 0xFFAAB0C0.toInt()).apply { setPadding(dp(80), dp(18), dp(80), 0) })
            }, FrameLayout.LayoutParams(-1, -1))
            return
        }
        val grid = GridLayout(this).apply { columnCount = 4 }
        val tiles = ArrayList<Pair<String, View>>()                    // one list by name, so the order never depends on the scan
        for (g in games) {
            val t = tile(g.name, g.sys.label, g.sys.color) {
                try { startActivity(Intent(this, GameActivity::class.java).putExtra("path", g.file.path)) } catch (e: Exception) { }
            }
            tiles += g.name to t
        }
        for (w in webs) {
            val t = tile(w.name, "WEB", 0xFF0891B2.toInt()) {
                try { startActivity(Intent(this, WebGameActivity::class.java).putExtra("name", w.name).putExtra("url", w.url)) } catch (e: Exception) { }
            }
            if (!w.url.startsWith("builtin:")) t.setOnLongClickListener { Systems.removeWeb(this, w.url); reload(); true }   // OK held on a link removes it
            tiles += w.name to t
        }
        tiles.sortBy { it.first.lowercase() }
        tiles.forEach { grid.addView(it.second) }
        val first: View? = tiles.firstOrNull()?.second
        grid.addView(tile("＋ " + getString(R.string.add_game), "", 0) { showOverlay() })
        val sug = GridLayout(this).apply { columnCount = 4 }
        val sugs = ArrayList<Pair<String, View>>()
        for (w in feat) {
            val nm = w.optJSONObject("name")?.let { it.optString(if (en) "en" else "vi").ifEmpty { it.optString("en") } } ?: w.optString("id")
            val t = tile(nm, "WEB", 0xFF0891B2.toInt()) {
                try { startActivity(Intent(this, WebGameActivity::class.java).putExtra("name", nm).putExtra("url", w.optString("url")).putExtra("pad", w.optString("pad", "web"))) } catch (e: Exception) { }
            }
            t.setOnLongClickListener { hide("w:" + w.optString("id")); true }
            sugs += nm to t
        }
        for (h in brew) {
            val s = Systems.list(this).first { it.id == h.optString("system") }
            val t = tile(getString(R.string.get_rom, h.optString("name")), s.label, s.color) { }
            t.setOnClickListener { getRom(h, s, t) }
            t.setOnLongClickListener { hide("h:" + h.optString("id")); true }
            sugs += h.optString("name") to t
        }
        sugs.sortBy { it.first.lowercase() }
        sugs.forEach { sug.addView(it.second) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(48), dp(32), dp(48), dp(32))
            addView(label(getString(R.string.lib_title), 30f).apply { gravity = Gravity.START; setPadding(dp(8), 0, 0, dp(14)) })
            addView(grid)
            if (sug.childCount > 0) {
                addView(label(getString(R.string.suggest), 22f, 0xFFAAB0C0.toInt()).apply { gravity = Gravity.START; setPadding(dp(8), dp(22), 0, dp(6)) })
                addView(sug)
                addView(label(getString(R.string.hide_hint), 14f, 0xFF6B7385.toInt()).apply { gravity = Gravity.START; setPadding(dp(8), dp(6), 0, 0) })
            }
        }
        root.addView(ScrollView(this).apply { addView(col) }, FrameLayout.LayoutParams(-1, -1))
        first?.requestFocus()
    }

    private fun hide(id: String) {
        val p = getSharedPreferences("games", MODE_PRIVATE)
        p.edit().putStringSet("hidden", HashSet(p.getStringSet("hidden", emptySet()) ?: emptySet()).apply { add(id) }).apply(); reload()
    }

    /** One OK: the ROM from the catalog into files/roms, its sha256 checked first (a mismatch is thrown away). */
    private fun getRom(h: org.json.JSONObject, s: Systems.Sys, tile: View) {
        val tv = ((tile as FrameLayout).getChildAt(tile.childCount - 1)) as TextView
        tv.text = getString(R.string.getting); tile.isEnabled = false
        Thread {
            var msg: String? = null
            try {
                val c = java.net.URL(h.getString("url")).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 15000
                val bytes = c.inputStream.use { it.readBytes().take(64 shl 20).toByteArray() }
                val sum = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                val ext = h.getString("url").substringAfterLast('.').takeIf { it in s.exts } ?: s.exts.first()
                if (sum != h.optString("sha256").lowercase()) msg = getString(R.string.rom_bad)
                else File(Systems.romsDir(this), h.optString("name").replace(Regex("[^A-Za-z0-9 ._-]"), "") + ".$ext").writeBytes(bytes)
            } catch (e: Exception) { msg = getString(R.string.core_net) }
            runOnUiThread { if (isDestroyed) return@runOnUiThread; if (msg != null) { tv.text = msg; tile.isEnabled = true } else reload() }
        }.start()
    }

    private fun tile(name: String, badge: String, color: Int, click: () -> Unit): View = FrameLayout(this).apply {
        isFocusable = true; isClickable = true
        background = focusBg(14, 0xFF1A1F2B.toInt(), 0xFF2B3350.toInt())
        layoutParams = GridLayout.LayoutParams().apply { width = dp(200); height = dp(125); setMargins(dp(8), dp(8), dp(8), dp(8)) }
        if (badge.isNotEmpty()) addView(TextView(context).apply {
            text = badge; textSize = 13f; setTextColor(Color.WHITE); typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(3), dp(10), dp(3))
            background = GradientDrawable().apply { setColor(color); cornerRadius = dp(8).toFloat() }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(12), dp(12), 0, 0) })
        addView(label(name, 19f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(12), dp(30), dp(12), 0) },
            FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        setOnClickListener { click() }
    }

    private fun showOverlay() {
        val v = FrameLayout(this).apply {
            setBackgroundColor(0xE60B0D12.toInt()); isClickable = true
            addView(addPanel(server), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        overlay = v
        root.addView(v, ViewGroup.LayoutParams(-1, -1))
    }
}
