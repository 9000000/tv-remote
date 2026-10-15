package dev.tvremote.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Scenes: one button that does several things on the TV ("Movie night": wake it, open YouTube, volume 12). The same JSON in
 * the three remotes; each keeps its own list.
 *
 *   { "id": "s1", "name": "Xem phim", "icon": "🎬", "steps": [ {"do": "wake"}, {"do": "app", "pkg": "com.google.android.youtube.tv"} ] }
 *
 * Steps: wake, sleep, key {key}, app {pkg}, link {url}, volume {level}, wait {s}, timer {min} (TV Setup's sleep timer, 0 cancels).
 */
object Scenes {
    /** What the steps need from the TV. ADB does all of it ([Shell]); the keys-only connection some. */
    interface Target {
        fun key(code: Int)
        fun app(pkg: String)
        fun link(url: String)
        fun volume(level: Int)
        fun timer(minutes: Int)
    }

    /** The key names the pages use, to Android key codes. */
    val KEYS: Map<String, Int> = HashMap<String, Int>().apply {
        putAll(mapOf("UP" to 19, "DOWN" to 20, "LEFT" to 21, "RIGHT" to 22, "OK" to 23, "BACK" to 4, "HOME" to 3, "MENU" to 82, "POWER" to 26, "WAKE" to 224,
            "SLEEP" to 223, "VOL_UP" to 24, "VOL_DOWN" to 25, "MUTE" to 164, "PLAY_PAUSE" to 85, "NEXT" to 87, "PREV" to 88, "SETTINGS" to 176, "INPUT" to 178,
            "DEL" to 67, "ENTER" to 66, "ASSIST" to 219, "CH_UP" to 166, "CH_DOWN" to 167,
            "INFO" to 165, "GUIDE" to 172, "LIVE" to 170, "SUBTITLE" to 175, "REWIND" to 89, "STOP" to 86, "FFWD" to 90))
        for (d in 0..9) put(d.toString(), 7 + d)
    }

    private val PKG = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
    private val STEPS = setOf("wake", "sleep", "key", "app", "link", "volume", "wait", "timer")

    /**
     * Runs the steps in order. A step that fails is skipped (the TV may simply not have that app) and named in the answer;
     * null when everything went through. [stop] is asked between steps.
     */
    fun run(steps: JSONArray, tv: Target, stop: () -> Boolean = { false }): String? {
        val failed = ArrayList<String>()
        for (i in 0 until minOf(steps.length(), 30)) {
            if (stop()) break
            val s = steps.optJSONObject(i) ?: continue
            try {
                when (s.optString("do")) {
                    "wake" -> tv.key(224)
                    "sleep" -> tv.key(223)
                    "key" -> tv.key(KEYS[s.optString("key").uppercase()] ?: throw IllegalArgumentException("unknown key"))
                    "app" -> tv.app(s.optString("pkg"))
                    "link" -> tv.link(s.optString("url"))
                    "volume" -> tv.volume(s.optInt("level").coerceIn(0, 100))
                    "wait" -> Thread.sleep((s.optDouble("s", 1.0).coerceIn(0.0, 30.0) * 1000).toLong())
                    "timer" -> tv.timer(s.optInt("min").coerceIn(0, 720))
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                failed.add((i + 1).toString() + ": " + (e.message ?: e.javaClass.simpleName))
            }
        }
        return if (failed.isEmpty()) null else failed.joinToString("; ")
    }

    /** Keeps only what can be run: at most 24 scenes of 30 steps, known steps with sane values, short names. */
    fun clean(list: JSONArray): JSONArray {
        val out = JSONArray()
        for (i in 0 until minOf(list.length(), 24)) {
            val sc = list.optJSONObject(i) ?: continue
            val steps = JSONArray()
            val raw = sc.optJSONArray("steps") ?: JSONArray()
            for (j in 0 until minOf(raw.length(), 30)) {
                val s = raw.optJSONObject(j) ?: continue
                val what = s.optString("do")
                if (what !in STEPS) continue
                val c = JSONObject().put("do", what)
                when (what) {
                    "key" -> { val k = s.optString("key").uppercase(); if (k !in KEYS) continue; c.put("key", k) }
                    "app" -> { val p = s.optString("pkg"); if (!PKG.matcher(p).matches()) continue; c.put("pkg", p) }
                    "link" -> { val u = s.optString("url").trim(); if (!u.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:\\S+$")) || u.length > 2000) continue; c.put("url", u) }
                    "volume" -> c.put("level", s.optInt("level").coerceIn(0, 100))
                    "wait" -> c.put("s", s.optDouble("s", 1.0).coerceIn(0.0, 30.0))
                    "timer" -> c.put("min", s.optInt("min").coerceIn(0, 720))
                }
                steps.put(c)
            }
            val name = sc.optString("name").trim().take(40)
            if (name.isEmpty() || steps.length() == 0) continue
            val id = sc.optString("id").let { if (it.matches(Regex("[A-Za-z0-9_-]{1,24}"))) it else "s" + System.nanoTime().toString(36) }
            out.put(JSONObject().put("id", id).put("name", name).put("icon", sc.optString("icon").take(8)).put("steps", steps))
        }
        return out
    }

    /** Opens an app on its own TV screen, or its phone-style one: the launcher activity is looked up first, `monkey` is slow and noisy. */
    fun launch(sh: (String) -> String, pkg: String) {
        if (!PKG.matcher(pkg).matches()) throw IllegalArgumentException("bad package name")
        for (cat in arrayOf("LEANBACK_LAUNCHER", "LAUNCHER")) {               // a TV app's own screen first, then a phone-style one
            val lines = sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.$cat $pkg 2>/dev/null").trim().split("\n")
            val component = lines[lines.size - 1].trim()
            if (!component.matches(Regex("[\\w.]+/[\\w.\$]+")) || !component.startsWith("$pkg/")) continue
            if (!sh("am start -n " + Adb.q(component) + " 2>&1").contains("Error")) return
        }
        throw IllegalArgumentException("this app cannot be opened (no launcher screen)")
    }

    /** Sets the media volume. `cmd media_session` is there from Android 10; older TVs have `media`. */
    fun volume(sh: (String) -> String, level: Int) {
        val bad = { out: String -> out.contains("Exception") || out.contains("Error") || out.contains("Unknown") || out.contains("not found") }
        val now = { Regex("volume is (\\d+)").find(sh("cmd media_session volume --stream 3 --get 2>&1"))?.groupValues?.get(1)?.toIntOrNull() }
        if (bad(sh("cmd media_session volume --stream 3 --set $level 2>&1")) && bad(sh("media volume --stream 3 --set $level 2>&1")))
            throw IllegalArgumentException("volume command failed")
        // Some TVs take the command and keep their volume (HDMI-CEC, the Google TV emulator): then the keys, one step each
        val got = now() ?: return
        if (got == level) return
        val code = if (level > got) 24 else 25
        sh((1..minOf(Math.abs(level - got), 30)).joinToString("; ") { "input keyevent $code" })
    }

    /** The scenes as a file to keep ("Export"): the page reads the same shape back ("Import"), also a bare list. */
    fun backup(scenes: JSONArray): HttpServer.Response {
        val body = JSONObject().put("app", "tv-remote").put("kind", "scenes").put("version", 1).put("scenes", scenes).toString(2)
        val out = HttpServer.Response.of("application/json; charset=utf-8", body.toByteArray())
        val name = "tv-remote-scenes-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".json"
        out.headers["Content-Disposition"] = "attachment; filename=\"$name\"; filename*=UTF-8''$name"
        return out
    }

    /** Everything through ADB shell commands. [timer] defaults to asking TV Setup over ADB (see [RulesClient]). */
    class Shell(val sh: (String) -> String, private val timer: ((Int) -> Unit)? = null) : Target {
        override fun key(code: Int) { sh("input keyevent $code") }
        override fun app(pkg: String) = launch(sh, pkg)
        override fun link(url: String) {
            val out = sh("am start -a android.intent.action.VIEW -d " + Adb.q(url) + " 2>&1")
            if (out.contains("Error") || out.lowercase().contains("unable to resolve")) throw IllegalArgumentException("the TV has no app that can open this")
        }
        override fun volume(level: Int) = volume(sh, level)
        override fun timer(minutes: Int) {
            if (timer != null) { timer.invoke(minutes); return }
            val r = RulesClient.call(sh, JSONObject().put("do", "sleep").put("minutes", minutes))
            if (r.has("setup") || r.has("error")) throw IllegalArgumentException("the sleep timer needs TV Setup 2.13.0 or newer on the TV")
        }
    }
}
