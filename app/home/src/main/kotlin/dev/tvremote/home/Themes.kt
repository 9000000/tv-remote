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

    fun start(c: Context) {
        val app = c.applicationContext
        val have = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0" } catch (e: Exception) { "0" }
        val f = File(app.filesDir, NAME)
        // the kept copy (cheap to check once seen), else the packed one, kept in turn so its check is paid once; dated 1 ms so the site is asked at once
        var bytes = SignedList.kept(f)
        if (bytes == null) {
            bytes = SignedList.packed({ app.assets.open(it) }, NAME)
            if (bytes != null) try {
                SignedList.keep(f, bytes, app.assets.open("$NAME.sig").use { String(it.readBytes(), Charsets.UTF_8) }); f.setLastModified(1)
            } catch (e: Exception) { }
        }
        use(bytes, have)
        if (System.currentTimeMillis() - f.lastModified() < DAY) return
        Thread({
            try {
                SignedList.fetch(BASES, NAME, 1 shl 20, connectMs = 6000, readMs = 8000) { list, sig, _ ->
                    val t = HomeConfig.parseReady(String(list, Charsets.UTF_8), have)
                    if (t.isEmpty()) null else { SignedList.keep(f, list, sig); HomeConfig.ready = t; t }
                }
            } catch (e: Exception) { }
        }, "themes").apply { isDaemon = true }.start()
    }

    private fun use(bytes: ByteArray?, have: String) {
        val t = bytes?.let { HomeConfig.parseReady(String(it, Charsets.UTF_8), have) }.orEmpty()
        if (t.isNotEmpty()) HomeConfig.ready = t
    }
}
