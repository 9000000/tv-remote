package dev.tvremote.core.web
import dev.tvremote.core.adb.Adb
import dev.tvremote.core.adb.AdbLink
import dev.tvremote.core.ontv.LauncherInstall
import dev.tvremote.core.ontv.LauncherSwap
import dev.tvremote.core.ontv.Launchers
import dev.tvremote.core.ontv.RulesClient
import dev.tvremote.core.ontv.Setup
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * The launcher gallery's /api (`/api/launchers`, `/api/launcher/...`), for Manage on all three cores: what the signed list holds and how it fits
 * this TV, installing one with a confirmation, and going back to the TV's own. [job] runs work the page polls (Manage.job); [cancel] is the Stop of
 * the job running on this thread.
 */
internal class LauncherApi(private val link: AdbLink, private val dir: File?, private val job: (String, (JSONObject) -> String) -> JSONObject,
                           private val cancel: () -> Cancel?) {
    private companion object {
        const val CLEAN_PKG = "com.gathin.tvhome"
        const val CLEAN_DIR = "/sdcard/Android/data/$CLEAN_PKG/files"
        const val SIX_COLUMNS_ID = "cleanyourtv-six-columns"
        const val SIX_COLUMNS_CONFIG = """{"columns":6,"rows_below_dock":1,"background_color":"#000000","wallpaper":false}"""
    }

    private fun sh(c: String) = link.get().shell(c)

    /** The gallery for this TV: each launcher with its fit, whether it is on the TV and whether it is the home now. */
    fun list(): JSONObject {
        val g = Launchers.fetch()
        val f = Launchers.facts { sh(it) }
        val have = sh("pm list packages").split("\n").map { it.removePrefix("package:").trim() }.toSet()
        val swap = LauncherSwap({ sh(it) })
        val home = swap.homeNow().substringBefore('/')
        val rec = swap.state()
        val lang = Launchers.tvLang { sh(it) }
        val a = JSONArray()
        for (e in g.entries) {
            val fit = Launchers.fits(e, f)
            a.put(JSONObject().put("id", e.id).put("pkg", e.pkg).put("name", e.name).put("desc", JSONObject(e.desc)).put("notes", JSONObject(e.notes))
                .put("thumb", "/api/launcher/thumb?id=" + e.id).put("source", e.source).put("license", e.license).put("kind", e.kind).put("version", e.version)
                .put("fit", fit.ok).put("why", fit.why).put("min_sdk", e.minSdk).put("installed", e.pkg in have).put("in_use", e.pkg == home)
                .put("shows", Launchers.shows(e, lang, f.sdk)))
        }
        return JSONObject().put("updated", g.updated).put("launchers", a).put("home", home)
            .put("back", rec?.active == true || (home !in Setup.LAUNCHER && home != "android" && Setup.LAUNCHERX in have + Setup.LAUNCHER))   // something of ours is home, or a stock one exists to go back to
            .put("clean_installed", CLEAN_PKG in have)
            .put("record", rec?.state ?: "").put("tv_setup", LauncherInstall.tvSetupHelps { sh(it) })
    }

    fun thumb(id: String): HttpServer.Response {
        val g = Launchers.fetch()
        val e = g[id] ?: return HttpServer.Response.error(404, "not found")
        return HttpServer.Response.of("image/jpeg", Launchers.thumb(g, e))
    }

    /** Download (once, kept for the next TV), install, make it the home, then wait for the person's Keep (here, or on the TV by TV Setup). */
    fun install(id: String, lang: String): JSONObject {
        val e = Launchers.fetch()[id] ?: throw IllegalArgumentException("no such launcher")
        return job("launcher " + e.name) { j ->
            val adb = link.get()
            val d = dir ?: throw IOException("no folder for the download")
            val inst = LauncherInstall(adb, d, Launchers.fetch(), lang)
            val c = cancel()
            val state = inst.run(e, LauncherSwap.CONFIRM_SEC, { step, pct -> j.put("step", step).put("pct", pct) }, { c?.stopped == true })
            if (state == "pending") {
                j.put("step", "confirm").put("pct", 95)
                inst.askOnTv(e)
                val end = inst.await({ left -> j.put("confirm", left) }, { c?.stopped == true })
                j.remove("confirm")
                if (end != "kept") throw IOException("launcher:$end")
            }
            "kept"
        }
    }

    /** Apply the bundled CleanYourTV theme through its own provider, then restart the launcher. */
    fun theme(id: String, lang: String): JSONObject {
        if (id != SIX_COLUMNS_ID) throw IllegalArgumentException("no such theme")
        return job("theme CleanYourTV") { j ->
            val adb = link.get()
            val stop = cancel()
            stop?.check()
            if (!sh("pm path $CLEAN_PKG 2>/dev/null").contains("package:")) throw IOException("launcher:theme_missing")
            j.put("pct", 10)
            putThemeFile(adb, "config.json", SIX_COLUMNS_CONFIG.toByteArray(Charsets.UTF_8), stop)
            stop?.check()
            j.put("pct", 45)
            putThemeFile(adb, "theme_revision", java.util.UUID.randomUUID().toString().replace("-", "").toByteArray(Charsets.UTF_8), stop)
            stop?.check()
            j.put("pct", 75)
            val restart = "a=\$(settings get secure enabled_accessibility_services); am force-stop $CLEAN_PKG; " +
                "case \"\$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1)\" in " +
                "$CLEAN_PKG/*) am start -W -a android.intent.action.MAIN -c android.intent.category.HOME ;; " +
                "*) am start -W -n $CLEAN_PKG/.MainActivity ;; esac; " +
                "case \"\$a\" in *$CLEAN_PKG*) sleep 1; settings put secure enabled_accessibility_services \"\$a\"; " +
                "settings put secure accessibility_enabled 1; sleep 1; settings put secure enabled_accessibility_services ''; " +
                "settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services \"\$a\"; " +
                "settings put secure accessibility_enabled 1 ;; esac"
            if (!adb.shell(restart).contains("Status: ok")) throw IOException("launcher:theme_restart")
            dev.tvremote.core.Words.get(lang, "launcher.theme_done")
        }
    }

    private fun putThemeFile(adb: Adb, name: String, bytes: ByteArray, stop: Cancel?) {
        val stage = "/data/local/tmp/tvr-six-columns-${name.replace('.', '-')}-${java.util.UUID.randomUUID().toString().take(8)}"
        val qStage = Adb.q(stage)
        try {
            stop?.check()
            if (!adb.put(stage, bytes, readable = true)) throw IOException("launcher:theme_write")
            stop?.check()
            val dest = Adb.q("$CLEAN_DIR/$name")
            val uri = Adb.q("content://$CLEAN_PKG.theme/$name")
            val command = "mkdir -p ${Adb.q(CLEAN_DIR)} 2>/dev/null; rm -f $dest 2>/dev/null; " +
                "{ o=\$(content write --uri $uri < $qStage 2>&1) && case \"\$o\" in *'Unsupported operation'*) false ;; esac; } " +
                "|| cp $qStage $dest 2>/dev/null || exit 1; rm -f $qStage; echo ok"
            if (!adb.shell(command).trim().endsWith("ok")) throw IOException("launcher:theme_write")
            stop?.check()
        } finally {
            try { adb.shell("rm -f $qStage") } catch (ignored: IOException) { }
        }
    }

    /** The person's answer from the page. */
    fun answer(keep: Boolean): JSONObject {
        val swap = LauncherSwap({ sh(it) })
        if (keep) swap.keep() else swap.rollback("user")
        try { sh("am broadcast -a dev.tvremote.setup.LAUNCHER_ANSWERED -p ${RulesClient.SETUP} >/dev/null 2>&1") } catch (e: IOException) { }   // the question on the TV goes away too
        return JSONObject().put("ok", true)
    }

    /** "Back to the TV's own launcher": what the TV's record says was turned off comes back and Home is asked again. */
    fun restore(): JSONObject {
        LauncherSwap({ sh(it) }).rollback("user")
        return JSONObject().put("ok", true).put("home", LauncherSwap({ sh(it) }).homeNow().substringBefore('/'))
    }

    /** A launcher that is on Google Play only: its page opens on the TV, nothing is downloaded here. */
    fun play(id: String): JSONObject {
        val e = Launchers.fetch()[id] ?: throw IllegalArgumentException("no such launcher")
        if (e.kind != "play" || !e.playUrl.startsWith("market://details?id=" + e.pkg)) throw IllegalArgumentException("not a Play Store launcher")
        val out = sh("am start -a android.intent.action.VIEW -d " + Adb.q(e.playUrl) + " 2>&1")
        if (out.contains("Error")) throw IOException("launcher:noplay")
        return JSONObject().put("ok", true)
    }
}
