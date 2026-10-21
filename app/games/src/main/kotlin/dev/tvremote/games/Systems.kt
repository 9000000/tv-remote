package dev.tvremote.games

import android.content.Context
import android.os.Environment
import java.io.File

/** What the library knows: file extension -> system. A system without a core yet is hidden (add its libretro core to jniLibs and set [Sys.core]). */
object Systems {
    class Sys(val id: String, val label: String, val color: Int, val core: String?, val exts: List<String>, val buttons: List<String>, val pad: String = id, val analog: Boolean = false,  // pad: the phone's layout
              val need: org.json.JSONObject? = null,
              val repo: String = "")                                // repo: the system's libretro-thumbnails repo (box art)                // need: the catalog's entry when the core is downloaded on first use

    private val PAD = listOf("A", "B", "START", "SELECT")
    val ALL = listOf(
        Sys("nes", "NES", 0xFFE11D48.toInt(), "libfceumm_libretro_android.so", listOf("nes"), PAD, repo = "Nintendo_-_Nintendo_Entertainment_System"),
        Sys("snes", "SNES", 0xFF7C3AED.toInt(), null, listOf("sfc", "smc"), PAD + listOf("X", "Y", "L", "R"), repo = "Nintendo_-_Super_Nintendo_Entertainment_System"),
        Sys("gba", "GBA", 0xFF2563EB.toInt(), null, listOf("gba"), PAD + listOf("L", "R"), repo = "Nintendo_-_Game_Boy_Advance"),
        Sys("psx", "PS1", 0xFF64748B.toInt(), null, listOf("cue", "chd", "pbp"), PAD + listOf("X", "Y", "L", "R", "L2", "R2"), pad = "ps", analog = true, repo = "Sony_-_PlayStation"),
    )

    private val LAYOUT_BUTTONS = mapOf("nes" to PAD, "snes" to PAD + listOf("X", "Y", "L", "R"), "gba" to PAD + listOf("L", "R"),
        "ps" to PAD + listOf("X", "Y", "L", "R", "L2", "R2"))

    /** The systems this device can play: the ones with a core packed in the APK, and those the catalog has a core for (this ABI, "on"). */
    fun list(ctx: Context): List<Sys> {
        val cat = Catalog.peek(ctx).optJSONObject("cores")
        return ALL.mapNotNull { s ->
            if (s.core != null && (File(ctx.applicationInfo.nativeLibraryDir, s.core).exists())) return@mapNotNull s
            val e = cat?.optJSONObject(s.id)?.takeIf { it.optBoolean("on", true) && Cores.abi(it) != null } ?: return@mapNotNull null
            val lay = e.optString("layout", s.pad)
            val ex = e.optJSONArray("exts")?.let { a -> (0 until a.length()).map { a.getString(it).lowercase() } } ?: s.exts
            val over = Catalog.peek(ctx).optJSONObject("layouts")?.optJSONObject(lay)?.optJSONArray("buttons")?.let { a -> (0 until a.length()).map { a.getString(it) } }
            Sys(s.id, s.label, s.color, e.optString("file", "${s.id}_libretro_android.so"), ex, over ?: LAYOUT_BUTTONS[lay] ?: s.buttons, lay, s.analog, e, s.repo)
        }
    }

    fun byExt(ctx: Context, ext: String): Sys? = list(ctx).firstOrNull { ext.lowercase() in it.exts }
    fun uploadExts(ctx: Context): Set<String> = list(ctx).flatMap { it.exts }.toSet()

    class Game(val file: File, val name: String, val sys: Sys)

    fun romsDir(ctx: Context) = File(ctx.getExternalFilesDir(null), "roms").also { it.mkdirs() }
    fun savesDir(ctx: Context) = File(ctx.getExternalFilesDir(null), "saves").also { it.mkdirs() }

    /** "Robo Run (USA) [!].nes" -> "Robo Run" */
    fun clean(file: String): String =
        file.substringBeforeLast('.').replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "").replace('_', ' ').trim().ifEmpty { file.substringBeforeLast('.') }

    /** The app's own folder, then /sdcard/Games and /sdcard/Download where Android lets the app list them (Android 11+ mostly hides them: use the upload). */
    fun scan(ctx: Context): List<Game> {
        val sd = Environment.getExternalStorageDirectory()
        val seen = HashSet<String>()
        val out = ArrayList<Game>()
        for (dir in listOf(romsDir(ctx), File(sd, "Games"), File(sd, "Download"))) {
            val fs = try { dir.listFiles() } catch (e: Exception) { null } ?: continue
            for (f in fs) {
                if (!f.isFile || f.name.startsWith(".")) continue
                val sys = byExt(ctx, f.extension) ?: continue
                if (seen.add(f.name.lowercase())) out.add(Game(f, clean(f.name), sys))
            }
        }
        return out.sortedBy { it.name.lowercase() }
    }

    /** A web game: a page to open in a WebView. url "builtin:<dir>" is one packed in assets/webgames/ and served by [GameServer]. */
    class Web(val name: String, val url: String)

    private fun webFile(ctx: Context) = File(ctx.getExternalFilesDir(null), "web.json")

    /** The built-in ones first, then the links people added (files/web.json: [{"name":..,"url":..}]). */
    fun webGames(ctx: Context): List<Web> {
        val out = arrayListOf(Web(ctx.getString(R.string.quiz_name), "builtin:quiz"))
        try {
            val a = org.json.JSONArray(webFile(ctx).takeIf { it.exists() }?.readText() ?: "[]")
            for (i in 0 until a.length()) a.getJSONObject(i).let { out.add(Web(it.optString("name"), it.optString("url"))) }
        } catch (e: Exception) { }
        return out
    }

    @Synchronized fun addWeb(ctx: Context, name: String, url: String): Boolean {
        val u = try { java.net.URI(url) } catch (e: Exception) { return false }
        if ((u.scheme != "http" && u.scheme != "https") || u.host.isNullOrEmpty() || name.isEmpty() || name.length > 40 || url.length > 500) return false
        val a = try { org.json.JSONArray(webFile(ctx).takeIf { it.exists() }?.readText() ?: "[]") } catch (e: Exception) { org.json.JSONArray() }
        for (i in 0 until a.length()) if (a.getJSONObject(i).optString("url") == url) return true
        a.put(org.json.JSONObject().put("name", name).put("url", url))
        webFile(ctx).writeText(a.toString())
        return true
    }

    @Synchronized fun removeWeb(ctx: Context, url: String) {
        val a = try { org.json.JSONArray(webFile(ctx).readText()) } catch (e: Exception) { return }
        val keep = org.json.JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).optString("url") != url) keep.put(a.getJSONObject(i))
        webFile(ctx).writeText(keep.toString())
    }
}
