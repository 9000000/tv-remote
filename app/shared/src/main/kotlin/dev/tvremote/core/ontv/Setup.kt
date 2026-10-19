package dev.tvremote.core.ontv
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import dev.tvremote.core.Words
import dev.tvremote.core.adb.Adb
import dev.tvremote.core.adb.PmInstall

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
        /** Disabled last, and only once FLauncher is installed, or Home would have nowhere to go. launcherx: Google TV's. */
        val LAUNCHER = arrayOf("com.google.android.tvrecommendations", "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx")
        const val LAUNCHERX = "com.google.android.apps.tv.launcherx"
        /**
         * Google TV's setup wizard answers HOME too, above any installed app (RecoveryActivity, priority 1): with launcherx off
         * it took Home and the TV restarted about every minute (cleanyourtv, a Xiaomi on Google TV 14). Off before launcherx,
         * back on with Undo: a factory reset needs it.
         */
        val WIZARD = arrayOf("com.google.android.tungsten.setupwraith")
        private const val RESOLVE_HOME = "cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN"
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
            "|com\\.google\\.android\\.(gms|gsf|packageinstaller|permissioncontroller|tvlauncher|tvrecommendations|leanbacklauncher.*|inputmethod.*|tv\\.remote\\.service|katniss|apps\\.tv\\.launcherx|tungsten\\.setupwraith)" +
            "|dev\\.tvremote\\..*|me\\.efesser\\.flauncher|com\\.tailscale\\.ipn")
        /** What the Apps tab marks "must keep" and refuses to stop, clear, turn off or remove: [PROTECTED] and the brands' own [Bloat.KEEP]. */
        fun mustKeep(p: String) = PROTECTED.matches(p) || Bloat.KEEP.matches(p)
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
        val off = off()
        return undoable().any { it in off }
    }

    private fun off(): Set<String> =
        sh("pm list packages -d 2>/dev/null").split("\n").map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toSet()

    private fun record(): List<String> = sh("cat $OFF_LIST 2>/dev/null").split("\n").map { it.trim() }.filter { it.isNotEmpty() }   // "" is in no list

    /**
     * What Undo turns back on: what the record names, and the suggested lists (older versions kept no record of them). Older
     * versions also turned the old home screen off without a record: that one comes back too, except on Google TV, where it
     * was off from the factory and turning it on would give Home a second owner. Home screens first, so the wizard ([WIZARD])
     * never finds Home empty.
     */
    private fun undoable(): List<String> {
        val mine = record()
        val legacy = if (mine.none { it in LAUNCHER } && !has(LAUNCHERX)) LAUNCHER.toList() - LAUNCHERX else emptyList()
        return (LAUNCHER.filter { it in mine } + legacy + GOOGLE + SONY + mine).distinct()
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
        val suggest = suggested().filter { it in enabled }
        val home = homeNow()
        val shown = HashSet<String>()
        for (cat in arrayOf("LEANBACK_LAUNCHER", "LAUNCHER"))              // the apps a person sees on the TV
            for (line in sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.$cat 2>/dev/null").split("\n"))
                if (line.contains('/')) shown.add(line.trim().substringBefore('/'))
        val others = shown.filter { it in enabled && it !in suggest && it != home && !kept(it) }.sortedBy { (label(it) ?: it).lowercase() }
        fun item(p: String) = org.json.JSONObject().put("pkg", p).put("label", Bloat.SUGGEST[p] ?: label(p) ?: NAMES[p] ?: p.split('.').let { s -> if (s.last().length <= 3 && s.size > 1) s.takeLast(2).joinToString(".") else s.last() })
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

        val pick = c.disable ?: suggested()
        val keep = setOf(homeNow(), keyboardNow().substringBefore('/'))   // whatever the list says, the TV keeps a home and a keyboard
        var off = 0
        for (p in pick.distinct()) {
            if (!p.matches(Regex("[A-Za-z][\\w]*(\\.[\\w]+)+")) || kept(p) || p in keep || !has(p)) continue
            turnOff(p)
            off++
        }
        if (off > 0) log.line(t("optimize.turned_off_apps", "n" to off))
        keyboard()

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
        if (takeHome()) {
            log.line(t("optimize.home_screen", "home" to last(sh(RESOLVE_HOME))))
            log.line(t("optimize.done_press_home_see"))
        } else {
            log.line(t("optimize.home_kept", "home" to homeNow()))
            log.line(t("optimize.done"))
        }
    }

    private fun suggested(): List<String> = (GOOGLE + SONY).toList() + Bloat.SUGGEST.keys

    private fun kept(p: String) = mustKeep(p)

    /** The app the Home button opens now ("android" when Android asks which one). */
    private fun homeNow(): String = last(sh(RESOLVE_HOME)).substringBefore('/')

    /** Turned off one command per shell call: chained, some TVs' pm stopped after the first (cleanyourtv). Kept for Undo. */
    private fun turnOff(p: String) {
        sh("pm disable-user --user 0 $p")
        sh("am force-stop $p")
        sh("grep -qx '$p' $OFF_LIST 2>/dev/null || echo '$p' >> $OFF_LIST")
    }

    /**
     * FLauncher takes Home, by the same steps as any launcher of the gallery ([LauncherSwap]: the TV's own home screens off with the
     * wizard first, the new one made the home last, then Home is asked where it goes). Anywhere else (a brand's own home screen we do
     * not know, Fire OS, a TV that ignores the choice) and the home screens come back on: a Home button that lands on a setup wizard
     * or a picker is worse than the old home screen. Recorded on the TV, so the watchdog of TV Setup guards it too.
     */
    private fun takeHome(): Boolean = LauncherSwap(sh).take(FL_PKG)

    private fun keyboardNow(): String = sh("settings get secure default_input_method").trim()

    /**
     * A TV whose keyboard is gone has none at all (a Xiaomi set to a Baidu keyboard not installed, cleanyourtv): the chosen one
     * is checked against those that work, and Gboard (else the first) is chosen in its place.
     */
    private fun keyboard() {
        fun whole(c: String) = c.substringBefore('/') + "/" + c.substringAfter('/').let { if (it.startsWith(".")) c.substringBefore('/') + it else it }
        val now = whole(keyboardNow())
        val ok = sh("ime list -s").split("\n").map { it.trim() }.filter { '/' in it }.map { whole(it) }
        if (ok.isEmpty() || now in ok) return
        val pick = ok.firstOrNull { it.startsWith("com.google.android.inputmethod.latin/") } ?: ok[0]
        sh("ime set $pick")
        log.line(t("optimize.keyboard_set", "ime" to pick.substringBefore('/')))
    }

    @Throws(Exception::class)
    fun undo() {
        var on = 0
        for (p in undoable())
            if (has(p.substringBefore('/'))) { sh("pm enable --user 0 $p"); on++ }   // p may be one screen of an app (the wizard's Home)
        sh("rm -f $OFF_LIST")
        LauncherSwap(sh).forget()
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
            return pmInstall(q, apk.length())
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
        return try { pmInstall(TMP, apk.length()) } finally { sh("rm -f $TMP") }
    }

    /** `pm install`; on firmware that refuses it, a PackageInstaller session instead ([PmInstall]). */
    @Throws(IOException::class)
    private fun pmInstall(quoted: String, size: Long): String {
        val out = adb!!.shell("cat $quoted | pm install -r -S $size 2>&1", 600000)
        return PmInstall.fallback(out, { adb!!.shell(it, 600000) }, quoted, size)
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
