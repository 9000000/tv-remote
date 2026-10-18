package dev.tvremote.core.ontv
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import dev.tvremote.core.Words
import dev.tvremote.core.adb.Adb

/**
 * "Optimize TV": the steps, run over an ADB connection. The TV app runs them on itself,
 * the phone app on the TV it is connected to. [dir] is where this device may keep a download for a moment.
 */
class Setup internal constructor(private val adb: Adb?, private val dir: File?, private val lang: String,
                                 private val sh: (String) -> String, private val log: Log) {   // sh: a test's TV in place of ADB (SetupTest)
    constructor(adb: Adb, dir: File?, lang: String, log: Log) : this(adb, dir, lang, { adb.shell(it) }, log)
    private fun t(id: String, vararg a: Pair<String, Any?>) = Words.get(lang, id, *a)

    companion object {
        const val FL_PKG = "me.efesser.flauncher"
        const val FL_URL = "https://github.com/osrosal/flauncher/releases/download/v2025.07.001/flauncher-armeabi-v7a-release.apk"
        const val FL_SHA = "467fd0befe6daa3993a8a34a3981a631858ace28b38d9836d691478af8b71e40"
        /** For TVs that run 64-bit apps only (newer Google TV, the emulator): the 32-bit one does not install there. */
        const val FL_URL64 = "https://github.com/osrosal/flauncher/releases/download/v2025.07.001/flauncher-arm64-v8a-release.apk"
        const val FL_SHA64 = "6e366d960d1c42ff923bddf86a034a231800924c0d395aefa7c448b1c4e8896f"
        const val TS_PKG = "com.tailscale.ipn"
        val GOOGLE = arrayOf("com.google.android.play.games", "com.google.android.videos", "com.google.android.youtube.tvmusic")
        /** Only present on Sony BRAVIA sets; skipped elsewhere. */
        val SONY = arrayOf(
            "com.sony.dtv.b2b.vendorprotocol", "com.sony.dtv.b2b.rs232csupport", "com.sony.dtv.b2b.hotelmode", "com.sony.dtv.b2b.prosettings",
            "com.sony.dtv.b2b.importexport", "com.sony.dtv.b2b.noderuntime.normal", "com.sony.dtv.b2b.noderuntime.privilege",
            "com.sony.dtv.b2b.webappruntimedrm", "com.sony.dtv.demomode", "com.sony.dtv.demosupport", "com.sony.dtv.multiscreendemo",
            "com.sony.dtv.sonyselect", "tv.samba.ssm")
        /** Disabled last, and only once FLauncher is installed, or Home would have nowhere to go. */
        val LAUNCHER = arrayOf("com.google.android.tvrecommendations", "com.google.android.tvlauncher")
        /** Readable names for the suggested apps, where the TV gives none. */
        val NAMES = mapOf(
            "com.google.android.play.games" to "Google Play Games", "com.google.android.videos" to "Google Play Movies & TV",
            "com.google.android.youtube.tvmusic" to "YouTube Music", "com.sony.dtv.b2b.vendorprotocol" to "Sony B2B vendor protocol",
            "com.sony.dtv.b2b.rs232csupport" to "Sony B2B RS-232C", "com.sony.dtv.b2b.hotelmode" to "Sony Hotel mode",
            "com.sony.dtv.b2b.prosettings" to "Sony Pro settings", "com.sony.dtv.b2b.importexport" to "Sony B2B import/export",
            "com.sony.dtv.b2b.noderuntime.normal" to "Sony B2B runtime", "com.sony.dtv.b2b.noderuntime.privilege" to "Sony B2B runtime (privileged)",
            "com.sony.dtv.b2b.webappruntimedrm" to "Sony B2B web app DRM", "com.sony.dtv.demomode" to "Sony Demo mode",
            "com.sony.dtv.demosupport" to "Sony Demo support", "com.sony.dtv.multiscreendemo" to "Sony Multi-screen demo",
            "com.sony.dtv.sonyselect" to "Sony Select", "tv.samba.ssm" to "Samba TV")
        /** Never offered or turned off: the TV would stop working, or this project would. The home screen goes through FLauncher only. */
        val PROTECTED = Regex("android|com\\.android\\.(systemui|settings|tv\\.settings|shell|phone|bluetooth|providers\\..*|packageinstaller|permissioncontroller|inputmethod.*|networkstack.*|vending)" +
            "|com\\.google\\.android\\.(gms|gsf|packageinstaller|permissioncontroller|tvlauncher|tvrecommendations|leanbacklauncher.*|inputmethod.*|tv\\.remote\\.service|katniss)" +
            "|dev\\.tvremote\\..*|me\\.efesser\\.flauncher|com\\.tailscale\\.ipn")
        /** What this project turned off beyond its own lists, so Undo can turn exactly that back on. Kept on the TV. */
        const val OFF_LIST = "/data/local/tmp/tvr-disabled.txt"
        private const val TMP = "/data/local/tmp/tvr-setup.apk"
        const val ANIM = "window_animation_scale transition_animation_scale animator_duration_scale"

        private fun last(out: String): String = out.split("\n").dropLastWhile { it.isEmpty() }.lastOrNull() ?: ""
    }

    fun interface Log { fun line(s: String) }

    /** Whether Optimize TV left something to undo: an app of its lists, or of its own record, turned off. */
    @Throws(IOException::class)
    fun optimized(): Boolean {
        val off = sh("pm list packages -d 2>/dev/null").split("\n").map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toSet()
        val mine = sh("cat $OFF_LIST 2>/dev/null").split("\n").map { it.trim() }.filter { it.isNotEmpty() }   // "" is in no list
        return (LAUNCHER + GOOGLE + SONY + mine).any { it in off }
    }

    @Throws(IOException::class)
    fun has(pkg: String): Boolean = sh("pm path $pkg").contains("package:")

    @Throws(IOException::class)
    fun describe(): String =
        sh("getprop ro.product.manufacturer") + " " + sh("getprop ro.product.model") + ", Android " + sh("getprop ro.build.version.release")

    /** "100.x.y.z" once Tailscale is signed in, else "". */
    @Throws(IOException::class)
    fun tailscaleIp(): String =
        Regex("inet (100\\.[0-9.]+)/32").find(sh("ip -4 addr show"))?.groupValues?.get(1) ?: ""

    @Throws(IOException::class)
    fun lanIp(): String =
        Regex("inet ((?:192\\.168|10|172\\.(?:1[6-9]|2[0-9]|3[01]))\\.[0-9.]+)/").find(sh("ip -4 addr show"))?.groupValues?.get(1) ?: ""

    /**
     * Optimize swaps the home screen and turns system apps off, which suits a TV only: a phone or tablet running TV Setup is
     * refused. Only a plain "false" counts, so an old TV whose pm has no has-feature is not turned away.
     */
    private fun tvOnly() {
        if (sh("pm has-feature android.software.leanback").trim() == "false")
            throw IOException(t("optimize.device_not_tv_optimize"))
    }

    /** What "Optimize TV" would do, for the person to choose: [label] gives an app's name where known. */
    @Throws(IOException::class)
    fun plan(label: (String) -> String?): org.json.JSONObject {
        tvOnly()
        val enabled = sh("pm list packages -e").split("\n").filter { it.startsWith("package:") }.map { it.substring(8).trim() }.toSet()
        val suggest = (GOOGLE + SONY).filter { it in enabled }
        val home = last(sh("cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN")).substringBefore('/')
        val shown = HashSet<String>()
        for (cat in arrayOf("LEANBACK_LAUNCHER", "LAUNCHER"))              // the apps a person sees on the TV
            for (line in sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.$cat 2>/dev/null").split("\n"))
                if (line.contains('/')) shown.add(line.trim().substringBefore('/'))
        val others = shown.filter { it in enabled && it !in suggest && it != home && !PROTECTED.matches(it) }.sortedBy { (label(it) ?: it).lowercase() }
        fun item(p: String) = org.json.JSONObject().put("pkg", p).put("label", label(p) ?: NAMES[p] ?: p.split('.').let { s -> if (s.last().length <= 3 && s.size > 1) s.takeLast(2).joinToString(".") else s.last() })
        return org.json.JSONObject()
            .put("anim", sh("settings get global window_animation_scale").trim() != "0.0")
            .put("flauncher", has(FL_PKG)).put("home", home)
            .put("suggest", org.json.JSONArray(suggest.map { item(it) }))
            .put("apps", org.json.JSONArray(others.map { item(it) }))
    }

    /** What to do: animations off, FLauncher as the home screen, and which apps to turn off (null: the suggested ones). */
    class Choice(val anim: Boolean = true, val home: Boolean = true, val disable: List<String>? = null)

    @Throws(Exception::class)
    fun apply(c: Choice = Choice()) {
        tvOnly()
        if (has(TS_PKG)) {                                            // the Tailscale button does this too; here for a Tailscale installed by hand
            Tailscale(adb!!, dir).keepAlive()
            log.line(t("optimize.tailscale_starts_again_by"))
        }
        if (c.anim) {
            sh("settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0.5")   // not 0 for the last one: loading spinners would freeze
            log.line(t("optimize.transition_animations_off"))
        }

        val pick = c.disable ?: (GOOGLE + SONY).toList()
        var off = 0
        for (p in pick.distinct()) {
            if (!p.matches(Regex("[A-Za-z][\\w]*(\\.[\\w]+)+")) || PROTECTED.matches(p) || !has(p)) continue
            sh("pm disable-user --user 0 $p; am force-stop $p")
            if (p !in GOOGLE && p !in SONY) sh("grep -qx '$p' $OFF_LIST 2>/dev/null || echo '$p' >> $OFF_LIST")   // for Undo
            off++
        }
        if (off > 0) log.line(t("optimize.turned_off_apps", "n" to off))

        if (!c.home) { log.line(t("optimize.done")); return }
        if (!has(FL_PKG)) {
            log.line(t("optimize.downloading_flauncher_10_mb"))
            val abis = sh("getprop ro.product.cpu.abilist")
            val only64 = "arm64-v8a" in abis && "armeabi-v7a" !in abis
            val apk = if (only64) download(FL_URL64, "flauncher.apk", FL_SHA64) else download(FL_URL, "flauncher.apk", FL_SHA)
            log.line(t("optimize.installing_flauncher"))
            val out = install(apk)
            apk.delete()
            if (!out.contains("Success")) {
                val why = if (out.contains("NO_MATCHING_ABIS")) t("optimize.flauncher_has_no_build") else last(out)
                log.line(t("optimize.flauncher_could_not_be", "why" to why))
                log.line(t("optimize.done"))
                return
            }
        }
        for (p in LAUNCHER)
            if (has(p)) sh("pm disable-user --user 0 $p; am force-stop $p")
        val home = last(sh("cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN"))
        log.line(t("optimize.home_screen", "home" to home))
        log.line(t("optimize.done_press_home_see"))
    }

    @Throws(Exception::class)
    fun undo() {
        var on = 0
        val mine = sh("cat $OFF_LIST 2>/dev/null").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        for (p in (LAUNCHER + GOOGLE + SONY + mine).distinct())
            if (has(p)) { sh("pm enable --user 0 $p"); on++ }
        sh("rm -f $OFF_LIST")
        sh("for k in $ANIM; do settings put global \$k 1.0; done")
        log.line(t("optimize.re_enabled_apps_google", "n" to on))
        log.line(t("optimize.flauncher_tailscale_settings_left"))
    }

    /** Copies the APK to the TV through ADB and has the shell user install it there (an app may not install on its own). */
    @Throws(IOException::class)
    fun install(apk: File): String {
        // On the TV itself the file sits in this app's folder on shared storage, which the shell user can read: no copy at all
        val q = "'" + apk.absolutePath.replace("'", "'\\''") + "'"
        if (sh("stat -c %s $q 2>/dev/null").trim() == apk.length().toString())
            return adb!!.shell("cat $q | pm install -r -S " + apk.length() + " 2>&1", 600000)
        return try { copyAndInstall(apk) } catch (e: IOException) { copyAndInstall(apk) }   // a copy that stalled: once more
    }

    @Throws(IOException::class)
    private fun copyAndInstall(apk: File): String {
        var whole = false
        try {
            adb!!.put(TMP, apk.length()) { s ->
                apk.inputStream().use { input ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        s.write(buf, 0, n)
                    }
                }
            }
            whole = true
        } finally {
            if (!whole) try { adb!!.shell("rm -f $TMP", 10000) } catch (ignored: IOException) { }   // no half file left behind
        }
        // cat is still writing for a while after the stream closes (seconds for a big file on a slow TV): install only a whole file
        var size = ""
        var still = 0                                                 // checks in a row without the file growing
        while (still < 30) {                                          // 15 s without growing: it will not finish
            val now = sh("stat -c %s $TMP 2>/dev/null").trim()
            if (now == apk.length().toString()) { size = now; break }
            still = if (now == size) still + 1 else 0
            size = now
            Thread.sleep(500)
        }
        if (size != apk.length().toString()) { sh("rm -f $TMP"); throw IOException("copy to the TV incomplete ($size of ${apk.length()} bytes)") }
        return try { adb!!.shell("cat $TMP | pm install -r -S " + apk.length() + " 2>&1", 600000) } finally { sh("rm -f $TMP") }
    }

    @Throws(Exception::class)
    fun download(url: String, name: String, sha256: String?, progress: ((Int) -> Unit)? = null): File {
        val f = File(dir, name)
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 60000
        c.instanceFollowRedirects = true
        val md = MessageDigest.getInstance("SHA-256")
        try {
            val total = c.contentLength.toLong()                      // -1 when the server does not say
            var done = 0L
            c.inputStream.use { input ->
                FileOutputStream(f).use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n); md.update(buf, 0, n)
                        done += n
                        if (progress != null && total > 0) progress((done * 100 / total).toInt())
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        val hex = md.digest().joinToString("") { String.format("%02x", it) }
        if (sha256 != null && hex != sha256) { f.delete(); throw IOException(t("optimize.checksum_mismatch_not_installing")) }
        return f
    }
}
