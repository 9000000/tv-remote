package dev.tvremote.home

import android.content.Context
import dev.tvremote.update.SignedList
import java.io.File

/**
 * The ready-made themes: the site's signed list (docs/home/themes.json), else GitHub, else the copy kept in files/, else the one packed
 * in the APK. A list that is not signed by the project, or that holds no theme, is ignored ([HomeConfig.parseReady] cleans each look).
 */
object Themes {
    private val BASES = listOf("https://tvremote.pages.dev/home/", "https://raw.githubusercontent.com/HoangTran0410/tv-remote/main/docs/home/")
    private const val DAY = 24 * 3600_000L
    private const val NAME = "themes.json"
    /** The rev of the list in use: the previews' cache is keyed by it, so a new list draws them again. */
    @Volatile var rev = 0L
    @Volatile var onThumbs: (() -> Unit)? = null              // the Themes page: some pictures came, draw the previews again

    /** The ready-made themes' small pictures (`thumb`), fetched once and kept (ThemeShots); off the main thread, never twice at once. */
    @Volatile private var fetching = false
    fun fetchThumbs(c: Context) {
        val app = c.applicationContext
        if (fetching || HomeConfig.ready.none { it.thumb.isNotEmpty() && ThemeShots.ready(app, it) == null }) return
        fetching = true
        Thread({
            try { if (ThemeShots.fetch(app, HomeConfig.ready)) onThumbs?.invoke() } finally { fetching = false }
        }, "themethumbs").apply { isDaemon = true }.start()
    }

    fun start(c: Context) {
        val app = c.applicationContext
        val have = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0" } catch (e: Exception) { "0" }
        val f = File(app.filesDir, NAME)
        // the kept copy (cheap to check once seen) or the packed one, whichever has the higher "rev" (an APK newer than the site carries a newer list than
        // the site's); the packed one is kept in turn so its check is paid once, dated 1 ms so the site is asked at once
        var bytes = SignedList.kept(f)
        val packed = SignedList.packed({ app.assets.open(it) }, NAME)
        if (packed != null && rev(packed) > (bytes?.let { rev(it) } ?: -1)) {
            bytes = packed
            try { SignedList.keep(f, packed, app.assets.open("$NAME.sig").use { String(it.readBytes(), Charsets.UTF_8) }); f.setLastModified(1) } catch (e: Exception) { }
        }
        val mine = bytes?.let { rev(it) } ?: -1
        use(bytes, have); rev = maxOf(mine, 0)
        fetchThumbs(app)
        if (System.currentTimeMillis() - f.lastModified() < DAY) return
        Thread({
            try {
                SignedList.fetch(BASES, NAME, 1 shl 20, connectMs = 6000, readMs = 8000) { list, sig, _ ->
                    val t = HomeConfig.parseReady(String(list, Charsets.UTF_8), have)
                    if (t.isEmpty() || rev(list) < mine) null          // a site older than this app's list (not deployed yet) changes nothing else { SignedList.keep(f, list, sig); HomeConfig.ready = t; rev = rev(list); fetchThumbs(app); t }
                }
            } catch (e: Exception) { }
        }, "themes").apply { isDaemon = true }.start()
    }

    private fun rev(b: ByteArray): Long = try { org.json.JSONObject(String(b, Charsets.UTF_8)).optLong("rev", 0) } catch (e: Exception) { 0 }

    private fun use(bytes: ByteArray?, have: String) {
        val t = bytes?.let { HomeConfig.parseReady(String(it, Charsets.UTF_8), have) }.orEmpty()
        if (t.isNotEmpty()) HomeConfig.ready = t
    }
}
