package dev.tvremote.desktop

import dev.tvremote.core.Words
import dev.tvremote.core.remote.Atv
import dev.tvremote.core.web.HttpServer
import org.json.JSONObject

/**
 * Turning ADB on with the remote buttons alone (port 6466), the page's /api/assist: TV Setup is downloaded onto the TV
 * through links the TV opens (Downloader first when the TV has no app for links), and any app or the TV's Settings can be
 * brought to the front. [cur]: the session of the TV in use.
 */
class Assist(private val cur: () -> Session) {
    @Volatile private var status = "idle"
    @Volatile private var msg: Pair<String, Array<out Pair<String, Any?>>>? = null   // an id and its args, said in the language of whoever asks (state)
    @Volatile private var stop = false
    @Volatile private var installed = false
    @Volatile private var checking = false
    @Volatile private var opening = false
    @Volatile private var step = 0                                    // of STEPS, for the page's "Step n/3"
    @Volatile private var task: Thread? = null

    private fun front(): String = cur().let { s -> if (s.available) s.atv?.app ?: "" else "" }

    /** [stoppable]: only the setup job listens to "Stop"; opening an app or Settings is short anyway. */
    private fun untilFront(seconds: Double, stoppable: Boolean = true, want: (String) -> Boolean): Boolean {
        for (i in 0 until (seconds * 5).toInt()) {
            if (stoppable && stop) return false
            if (want(front())) return true
            Thread.sleep(200)
        }
        return false
    }

    private fun sendLink(link: String) {
        for (i in 0 until 30) { if (cur().available) break; Thread.sleep(200) }   // the TV may have dropped us after a link it disliked
        cur().need().launch(link)
    }

    private fun pressOk() = cur().need().key(23, Atv.SHORT)

    /** Brings an app to the front without ADB: asks for it, else opens its Play Store page and presses its main button. */
    private fun openOnTv(pkg: String, stoppable: Boolean = true, play: Boolean = true): Boolean {
        if (front() == pkg) return true
        sendLink("market://launch?id=$pkg")
        if (untilFront(4.0, stoppable) { it == pkg }) return true
        if (!play) return false                                       // not on the Play Store (TV Home): its page has nothing to install
        sendLink("https://play.google.com/store/apps/details?id=$pkg")
        if (!untilFront(10.0, stoppable) { it == "com.android.vending" }) return false
        Thread.sleep(3000)                                            // the page draws, with its main button in focus
        if (front() != "com.android.vending") {                       // someone used the TV's remote: OK would press something else
            if (stoppable) throw Moved() else return false
        }
        pressOk()
        return untilFront(15.0, stoppable) { it == pkg }
    }

    private fun say(id: String?, vararg a: Pair<String, Any?>) { msg = id?.let { it to a } }

    private fun answerChooser() {
        for (i in 0 until 3) {                                        // "Open with": OK takes what is offered
            Thread.sleep(1500)
            if (front() != "android" || stop) break
            pressOk()
        }
    }

    /** [app]: a key of the Downloader codes (core/Downloader.kt): "setup" (TV Setup) or "home" (TV Home). The steps are the same. */
    private fun installApp(app: String) {
        try {
            if (front().isEmpty()) {
                status = "blind"; say("pc.assist_blind")
                return
            }
            // Downloader first, always: a TV's own browser takes the APK link too and downloads it, then stays on a white page,
            // unable to hand the file to the installer. Downloader's own link (the app's code) opens nothing else.
            step = 1; say("adb_help.opening_downloader_on_the")
            var there = openOnTv(DOWNLOADER)
            while (!there && !stop) {                                 // its Play Store page was told to install it
                say("pc.assist_no_downloader")
                for (i in 0 until 600) { if (stop || installed || front() == DOWNLOADER) break; Thread.sleep(200) }
                if (stop) break
                checking = true                                       // one check at a time: more presses of "Installed" change nothing
                say("adb_help.opening_downloader_on_the_tv")
                there = front() == DOWNLOADER || openOnTv(DOWNLOADER)
                installed = false; checking = false
            }
            if (stop) { status = "idle"; say(null); return }
            step = 2; say("adb_help.handing_the_download_link")
            Thread.sleep(3000)
            sendLink("downloader://" + dev.tvremote.core.Downloader.code(app).orEmpty())   // the AFTVnews code of the app's APK (docs/data/downloader.json): opens Downloader alone, no "Open with"
            answerChooser(); Thread.sleep(2500)
            val was = front()
            step = 3; say("pc.assist_downloading")
            untilFront(120.0) { it != was }                           // until the TV's installer (or its permission screen) comes up
            if (HOME.containsMatchIn(front())) throw Moved()          // back home instead: Back or Home pressed on the TV
            status = if (stop) "idle" else "look"; say(null)
        } catch (e: Moved) {
            status = "moved"; say(null)
        } catch (e: InterruptedException) {
            status = "idle"; say(null)
        } catch (e: Exception) {
            status = "failed"; say("pc.assist_lost", "why" to (e.message ?: e.javaClass.simpleName))
        } finally {
            checking = false; step = 0
        }
    }

    fun state(lang: String): JSONObject = JSONObject().put("status", status).put("msg", msg?.let { Words.get(lang, it.first, *it.second) } ?: "").put("front", front()).put("connected", cur().available)
        .put("step", step).put("steps", STEPS)
        .put("checking", checking || installed)

    fun handle(d: JSONObject, lang: String): HttpServer.Response {
        val ok = { HttpServer.Response.ok() }
        when (val what = d.optString("do")) {
            "stop" -> { stop = true; return ok() }
            "installed" -> { if (!checking) installed = true; return ok() }
            else -> {
                cur().need()
                if ((what == "open" || what == "settings" || what == "direct") && (status == "running" || opening))   // one job on the TV at a time
                    return HttpServer.Response.error(409, Words.get(lang, "pc.assist_busy"))
                when (what) {
                    "setup" -> {
                        val app = d.optString("app", "setup").takeIf { it in APPS } ?: return HttpServer.Response.error(400, "unknown app")
                        val old = task
                        if (status == "running" && stop && old != null && old.isAlive) { old.interrupt(); old.join(5000) }   // stopped a moment ago, still winding down
                        if (status != "running") {
                            status = "running"; say(null); stop = false; installed = false; checking = false
                            task = Thread({ installApp(app) }, "assist").apply { isDaemon = true; start() }
                        }
                        return ok()
                    }
                    "open" -> {                                       // an app by package name, or any link
                        val target = d.optString("what").trim()
                        if (target.isEmpty()) return HttpServer.Response.error(400, "what required")
                        if (':' in target) { sendLink(target); return HttpServer.Response.json(JSONObject().put("ok", true).put("front", front())) }
                        if (!PKG.matches(target)) return HttpServer.Response.error(400, "not a package name")
                        opening = true
                        try { return HttpServer.Response.json(JSONObject().put("ok", openOnTv(target, false, d.optBoolean("play", true))).put("front", front())) } finally { opening = false }
                    }
                    "direct" -> {                                     // the fallback: the APK link to whatever app takes it; some TVs never install it
                        opening = true
                        try { sendLink(SETUP_APK); answerChooser() } finally { opening = false }
                        return HttpServer.Response.json(JSONObject().put("ok", true).put("front", front()))
                    }
                    "settings" -> {                                   // every way a TV may open its Settings when asked
                        val isSettings = { a: String -> "settings" in a.lowercase() }
                        val tries = listOf({ cur().need().key(176, Atv.SHORT) }, { sendLink("android-app://com.android.tv.settings") },
                            { sendLink("intent:#Intent;action=android.settings.SETTINGS;end") })
                        var done = isSettings(front())
                        for (t in tries) {
                            if (done) break
                            try { t() } catch (e: Exception) { continue }
                            done = untilFront(2.4, false, isSettings)
                        }
                        return HttpServer.Response.json(JSONObject().put("ok", done).put("blind", front().isEmpty()))
                    }
                    else -> return HttpServer.Response.error(400, "unknown action")
                }
            }
        }
    }

    /** The TV's screen is not where the job left it: someone pressed keys on the TV in the middle. */
    private class Moved : Exception()

    companion object {
        const val STEPS = 3
        val APPS = setOf("setup", "home")
        val HOME = Regex("launcher|\\.home\\b|tvhome", RegexOption.IGNORE_CASE)   // the TV's home screens (Google TV, Android TV, makers')
        const val DOWNLOADER = "com.esaba.downloader"
        const val SETUP_APK = "https://github.com/hoangtran0410/tv-remote/releases/latest/download/tv-setup.apk"
        val PKG = dev.tvremote.core.adb.Pkg.RE
    }
}
