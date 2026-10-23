package dev.tvremote.core.ontv
import dev.tvremote.core.adb.Pkg
import dev.tvremote.core.scenes.Scenes
import org.json.JSONObject
import java.io.IOException

/**
 * Check-out (Trả phòng) for a homestay or hotel, on each TV ticked in the several-TVs tool (Fleet; the phone's ManyTvs.kt, the
 * computer's Fleet.kt + js/fleet.js): the last guest is signed out of the apps, the volume is back to a level the owner chose,
 * the guests' browsers can no longer drive the TV from the Wi-Fi, and the TV is on its home screen. Nothing here is undoable
 * (`pm clear` wipes the sign-ins), so the clients ask first. [plan] says what runs and in which order for one TV, [run] does it.
 */
object Checkout {
    /** Apps worth clearing where they are installed (package -> name): streaming, music and browsers a guest signs in to. */
    val SUGGEST = linkedMapOf(
        "com.google.android.youtube.tv" to "YouTube", "com.google.android.youtube.tvkids" to "YouTube Kids", "com.google.android.youtube.tvmusic" to "YouTube Music",
        "com.netflix.ninja" to "Netflix", "com.amazon.amazonvideo.livingroom" to "Prime Video", "com.disney.disneyplus" to "Disney+",
        "in.startv.hotstar" to "Disney+ Hotstar", "com.wbd.stream" to "Max", "com.apple.atve.androidtv.appletv" to "Apple TV",
        "com.spotify.tv.android" to "Spotify", "com.plexapp.android" to "Plex", "tv.twitch.android.app" to "Twitch",
        "com.android.chrome" to "Chrome", "org.mozilla.firefox" to "Firefox", "com.brave.browser" to "Brave", "com.microsoft.emmx" to "Edge",
        "com.opera.browser" to "Opera", "com.puffin.tv" to "Puffin TV Browser", "com.tv.browser" to "TV Browser")

    /** Never cleared, whatever the owner picks (on top of [Setup.mustKeep]: the TV's system, Play services / Store, settings, every dev.tvremote app, Tailscale). */
    private val ALSO = Regex("com\\.android\\.(tv\\..*|launcher.*|provision.*|managedprovisioning|keychain|certinstaller|location|server.*|inputdevices|nfc|dynsystem)" +
        "|com\\.google\\.android\\.(tv|apps\\.tv\\.).*|com\\.google\\.android\\.(setupwizard|partnersetup|onetimeinitializer|backuptransport|syncadapters.*|ext\\..*|webview|trichromelibrary.*|play\\.games\\.tv)" +
        "|.*launcher.*|.*\\.systemui|.*\\.settings")

    /** Why [pkg] may not be cleared, or null: the hard block list, checked again on the TV right before `pm clear`. */
    fun blocked(pkg: String): String? =
        if (!Pkg.valid(pkg)) "bad package name"
        else if (Setup.mustKeep(pkg) || ALSO.matches(pkg)) "protected: the TV needs this app"
        else if (Bloat.factory(pkg)) "protected: the TV's own app" else null

    /** The owner's list cleaned: valid, not blocked, once each, in the order given. */
    fun clean(apps: Iterable<String>): List<String> = apps.map { it.trim() }.filter { it.isNotEmpty() && blocked(it) == null }.distinct()

    /** What to tick at the start: [SUGGEST] where installed (only those: a TV without Spotify is not asked about it). */
    fun preselect(installed: Collection<String>): List<String> = SUGGEST.keys.filter { it in installed }

    /**
     * The suggested apps this TV has even as part of its system (`pm list packages -3`, what the uninstall list shows, leaves out a
     * YouTube or Netflix that came with the TV: the very apps a guest signs in to). Joined to the TV's own apps for the list to pick from.
     */
    @Throws(IOException::class)
    fun here(sh: (String) -> String): List<String> =
        preselect(sh("pm list packages 2>/dev/null").lines().map { it.removePrefix("package:").trim() }.toSet())

    const val VOLUME = 25
    fun volumeOf(v: Int): Int = v.coerceIn(0, 100)

    /** The owner's choice, kept (phone: prefs; computer: tvs.json "checkout"). null apps = never chosen: [preselect] decides. */
    class Config(val apps: List<String>?, val volume: Int) {
        fun json(): JSONObject = JSONObject().put("volume", volume).also { o -> apps?.let { o.put("apps", org.json.JSONArray(it)) } }
        companion object {
            fun of(o: JSONObject?): Config = Config(o?.optJSONArray("apps")?.let { a -> clean((0 until a.length()).map { a.optString(it) }) },
                if (o?.has("volume") == true) volumeOf(o.optInt("volume", VOLUME)) else VOLUME)
        }
    }

    /** One thing to do on a TV. [skip] is the words' id of why it is not done on this one (the line says so; it is not an error). */
    class Step(val kind: String, val pkg: String = "", val skip: String = "") {
        override fun toString() = kind + (if (pkg.isNotEmpty()) ":$pkg" else "") + (if (skip.isNotEmpty()) " (skip $skip)" else "")
    }

    /**
     * The steps for one TV, in the order they run: clear each chosen app that is installed (the others were picked for other TVs),
     * volume, forget the guests' browsers (an old TV Setup, or none, cannot: said on the line), home.
     * [setup] is TV Setup's version on this TV, "" when it is not installed.
     */
    fun plan(installed: Set<String>, setup: String, apps: List<String>): List<Step> {
        val out = ArrayList<Step>()
        for (p in clean(apps)) if (p in installed) out += Step("clear", p)
        out += Step("volume")
        out += if (setup.isEmpty()) Step("web", skip = "missing") else if (!SetupNeed.ok(setup, "checkout")) Step("web", skip = "old") else Step("web")
        out += Step("home")
        return out
    }

    /** What happened on one TV; [token] is the line's text in the form the clients turn into words (js/checkout.js, ManyTvs.kt). */
    class Result(val cleared: Int, val failed: List<String>, val volume: Int, val web: String, val forgot: Int, val home: Boolean,
                 val apps: List<String> = emptyList()) {                // the packages cleared, so the result can name them
        /** `checkout:c=3,f=1,v=25,w=ok,n=2,h=1` — w: ok, old, missing, failed; then, when known, `,a=pkg+pkg` (cleared) and `,x=pkg+pkg` (not cleared). */
        fun token() = "checkout:c=$cleared,f=${failed.size},v=$volume,w=$web,n=$forgot,h=${if (home) 1 else 0}" +
            (if (apps.isNotEmpty()) ",a=" + apps.joinToString("+") else "") + (if (failed.isNotEmpty() && failed.none { it == "?" }) ",x=" + failed.joinToString("+") else "")
        companion object {
            fun parse(t: String): Result? {
                if (!t.startsWith("checkout:")) return null
                val kv = t.substring(9).split(',').mapNotNull { it.split('=').takeIf { p -> p.size == 2 } }.associate { it[0] to it[1] }
                val x = kv["x"]?.split('+')?.filter { it.isNotEmpty() }
                return Result(kv["c"]?.toIntOrNull() ?: 0, x ?: List(kv["f"]?.toIntOrNull() ?: 0) { "?" }, kv["v"]?.toIntOrNull() ?: 0, kv["w"] ?: "", kv["n"]?.toIntOrNull() ?: 0, kv["h"] == "1",
                    kv["a"]?.split('+')?.filter { it.isNotEmpty() } ?: emptyList())
            }
        }
    }

    /** Media volume index for [percent] of the TV's range ("volume is 3 in range [0..15]"; 15 when the TV does not say). */
    fun index(percent: Int, rangeOut: String): Int {
        val top = Regex("range \\[\\d+\\.\\.(\\d+)]").find(rangeOut)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 } ?: 15
        return Math.round(volumeOf(percent) * top / 100.0).toInt().coerceIn(0, top)
    }

    /**
     * Runs [plan] on one TV through [sh]. [say] gets the step now running (the TV's line); [home] is the home screen in use, its
     * component (an app that is the TV's launcher is never cleared). A failed clear is counted, never stops the rest: a guest
     * leaving matters more than one stubborn app. Throws only when the TV cannot be asked at all.
     */
    @Throws(IOException::class)
    fun run(sh: (String) -> String, cfg: Config, say: (String) -> Unit = {}): Result {
        val installed = sh("pm list packages 2>/dev/null").lines().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toSet()
        val setup = SetupNeed.have(sh)
        val home = LauncherSwap.last(sh(LauncherSwap.RESOLVE_HOME))
        val steps = plan(installed, setup, cfg.apps ?: preselect(installed))
        var cleared = 0; val failed = ArrayList<String>(); val done = ArrayList<String>(); var web = "ok"; var forgot = 0; var wentHome = false
        val total = steps.count { it.kind == "clear" }
        for (s in steps) when (s.kind) {
            "clear" -> {
                say("checkout clear ${cleared + failed.size + 1}/$total")
                if (home.startsWith(s.pkg + "/")) { failed += s.pkg; continue }       // the TV's home screen: never wiped
                val out = sh("pm clear ${s.pkg} 2>&1")
                if (out.contains("Success")) { cleared++; done += s.pkg } else failed += s.pkg
            }
            "volume" -> {
                say("checkout volume")
                try { Scenes.volume(sh, index(cfg.volume, sh("cmd media_session volume --stream 3 --get 2>&1"))) } catch (e: Exception) { throw IOException("volume: " + e.message) }
            }
            "web" -> {
                if (s.skip.isNotEmpty()) { web = s.skip; continue }
                say("checkout web")
                val r = RulesClient.call(sh, JSONObject().put("do", "forget_web"))
                if (r.has("forgot")) forgot = r.optInt("forgot") else web = if (r.optString("setup") == "missing") "missing" else if (r.has("setup") || r.optString("error").contains("unknown")) "old" else "failed"
            }
            "home" -> {
                say("checkout home")
                sh("am start -a android.intent.action.MAIN -c android.intent.category.HOME 2>&1")
                wentHome = true
            }
        }
        return Result(cleared, failed, cfg.volume, web, forgot, wentHome, done)
    }
}
