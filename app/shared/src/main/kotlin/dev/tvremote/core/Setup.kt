package dev.tvremote.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * "Optimize TV": the steps, run over an ADB connection. The TV app runs them on itself,
 * the phone app on the TV it is connected to. [dir] is where this device may keep a download for a moment.
 */
class Setup(private val adb: Adb, private val dir: File?, private val vi: Boolean, private val log: Log) {
    private fun t(v: String, e: String) = if (vi) v else e

    companion object {
        const val FL_PKG = "me.efesser.flauncher"
        const val FL_URL = "https://github.com/osrosal/flauncher/releases/download/v2025.07.001/flauncher-armeabi-v7a-release.apk"
        const val FL_SHA = "467fd0befe6daa3993a8a34a3981a631858ace28b38d9836d691478af8b71e40"
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
        val off = adb.shell("pm list packages -d 2>/dev/null").split("\n").map { it.removePrefix("package:").trim() }.toSet()
        val mine = adb.shell("cat $OFF_LIST 2>/dev/null").split("\n").map { it.trim() }
        return (LAUNCHER + GOOGLE + SONY + mine).any { it in off }
    }

    @Throws(IOException::class)
    fun has(pkg: String): Boolean = adb.shell("pm path $pkg").contains("package:")

    @Throws(IOException::class)
    fun describe(): String =
        adb.shell("getprop ro.product.manufacturer") + " " + adb.shell("getprop ro.product.model") + ", Android " + adb.shell("getprop ro.build.version.release")

    /** "100.x.y.z" once Tailscale is signed in, else "". */
    @Throws(IOException::class)
    fun tailscaleIp(): String =
        Regex("inet (100\\.[0-9.]+)/32").find(adb.shell("ip -4 addr show"))?.groupValues?.get(1) ?: ""

    @Throws(IOException::class)
    fun lanIp(): String =
        Regex("inet ((?:192\\.168|10|172\\.(?:1[6-9]|2[0-9]|3[01]))\\.[0-9.]+)/").find(adb.shell("ip -4 addr show"))?.groupValues?.get(1) ?: ""

    /**
     * Optimize swaps the home screen and turns system apps off, which suits a TV only: a phone or tablet running TV Setup is
     * refused. Only a plain "false" counts, so an old TV whose pm has no has-feature is not turned away.
     */
    private fun tvOnly() {
        if (adb.shell("pm has-feature android.software.leanback").trim() == "false")
            throw IOException(t("Máy này không phải TV. Tối ưu TV đổi màn hình chính và tắt app hệ thống, nên chỉ dùng cho TV.",
                "This device is not a TV. Optimize TV swaps the home screen and turns system apps off, so it is for TVs only."))
    }

    /** What "Optimize TV" would do, for the person to choose: [label] gives an app's name where known. */
    @Throws(IOException::class)
    fun plan(label: (String) -> String?): org.json.JSONObject {
        tvOnly()
        val enabled = adb.shell("pm list packages -e").split("\n").filter { it.startsWith("package:") }.map { it.substring(8).trim() }.toSet()
        val suggest = (GOOGLE + SONY).filter { it in enabled }
        val home = last(adb.shell("cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN")).substringBefore('/')
        val shown = HashSet<String>()
        for (cat in arrayOf("LEANBACK_LAUNCHER", "LAUNCHER"))              // the apps a person sees on the TV
            for (line in adb.shell("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.$cat 2>/dev/null").split("\n"))
                if (line.contains('/')) shown.add(line.trim().substringBefore('/'))
        val others = shown.filter { it in enabled && it !in suggest && it != home && !PROTECTED.matches(it) }.sortedBy { (label(it) ?: it).lowercase() }
        fun item(p: String) = org.json.JSONObject().put("pkg", p).put("label", label(p) ?: NAMES[p] ?: p.split('.').let { s -> if (s.last().length <= 3 && s.size > 1) s.takeLast(2).joinToString(".") else s.last() })
        return org.json.JSONObject()
            .put("anim", adb.shell("settings get global window_animation_scale").trim() != "0.0")
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
            Tailscale(adb, dir).keepAlive()
            log.line(t("✓ Tailscale: tự chạy lại sau khi TV ngủ hoặc khởi động lại", "✓ Tailscale: starts again by itself after standby or a restart"))
        }
        if (c.anim) {
            adb.shell("settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0.5")   // not 0 for the last one: loading spinners would freeze
            log.line(t("✓ Hiệu ứng chuyển cảnh: tắt", "✓ Transition animations: off"))
        }

        val pick = c.disable ?: (GOOGLE + SONY).toList()
        var off = 0
        for (p in pick.distinct()) {
            if (!p.matches(Regex("[A-Za-z][\\w]*(\\.[\\w]+)+")) || PROTECTED.matches(p) || !has(p)) continue
            adb.shell("pm disable-user --user 0 $p; am force-stop $p")
            if (p !in GOOGLE && p !in SONY) adb.shell("grep -qx '$p' $OFF_LIST 2>/dev/null || echo '$p' >> $OFF_LIST")   // for Undo
            off++
        }
        if (off > 0) log.line(t("✓ Đã tắt $off app", "✓ Turned off $off apps"))

        if (!c.home) { log.line(t("Xong.", "Done.")); return }
        if (!has(FL_PKG)) {
            log.line(t("… Đang tải FLauncher (10 MB)", "… Downloading FLauncher (10 MB)"))
            val apk = download(FL_URL, "flauncher.apk", FL_SHA)
            log.line(t("… Đang cài FLauncher", "… Installing FLauncher"))
            val out = install(apk)
            apk.delete()
            if (!out.contains("Success")) {
                val why = if (out.contains("NO_MATCHING_ABIS")) t("TV này chỉ chạy app 64-bit, FLauncher chỉ có bản 32-bit", "this TV runs 64-bit apps only and FLauncher is 32-bit") else last(out)
                log.line(t("– Không cài được FLauncher (", "– FLauncher could not be installed (") + why + t(") — giữ nguyên màn hình chính", "), the home screen is left as it is"))
                log.line(t("Xong.", "Done."))
                return
            }
        }
        for (p in LAUNCHER)
            if (has(p)) adb.shell("pm disable-user --user 0 $p; am force-stop $p")
        log.line(t("✓ Màn hình chính: ", "✓ Home screen: ") + last(adb.shell("cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN")))
        log.line(t("Xong. Bấm nút Home để thấy màn hình chính mới.", "Done. Press Home to see the new home screen."))
    }

    @Throws(Exception::class)
    fun undo() {
        var on = 0
        val mine = adb.shell("cat $OFF_LIST 2>/dev/null").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        for (p in (LAUNCHER + GOOGLE + SONY + mine).distinct())
            if (has(p)) { adb.shell("pm enable --user 0 $p"); on++ }
        adb.shell("rm -f $OFF_LIST")
        adb.shell("for k in $ANIM; do settings put global \$k 1.0; done")
        log.line(t("✓ Đã bật lại $on app (gồm màn hình chính Google), hiệu ứng về 1.0x", "✓ Re-enabled $on apps (Google's home screen among them), animations back to 1.0x"))
        log.line(t("FLauncher và cài đặt Tailscale được giữ nguyên.", "FLauncher and the Tailscale settings are left as they are."))
    }

    /** Copies the APK to the TV through ADB and has the shell user install it there (an app may not install on its own). */
    @Throws(IOException::class)
    fun install(apk: File): String {
        // On the TV itself the file sits in this app's folder on shared storage, which the shell user can read: no copy at all
        val q = "'" + apk.absolutePath.replace("'", "'\\''") + "'"
        if (adb.shell("stat -c %s $q 2>/dev/null").trim() == apk.length().toString())
            return adb.shell("cat $q | pm install -r -S " + apk.length() + " 2>&1", 600000)
        return try { copyAndInstall(apk) } catch (e: IOException) { copyAndInstall(apk) }   // a copy that stalled: once more
    }

    @Throws(IOException::class)
    private fun copyAndInstall(apk: File): String {
        val s = adb.exec("cat > $TMP")
        var whole = false
        try {
            apk.inputStream().use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    s.write(buf, 0, n)
                }
            }
            whole = true
        } finally {
            s.close()
            if (!whole) try { adb.shell("rm -f $TMP", 10000) } catch (ignored: IOException) { }   // no half file left behind
        }
        // cat is still writing for a while after the stream closes (seconds for a big file on a slow TV): install only a whole file
        var size = ""
        var still = 0                                                 // checks in a row without the file growing
        while (still < 30) {                                          // 15 s without growing: it will not finish
            val now = adb.shell("stat -c %s $TMP 2>/dev/null").trim()
            if (now == apk.length().toString()) { size = now; break }
            still = if (now == size) still + 1 else 0
            size = now
            Thread.sleep(500)
        }
        if (size != apk.length().toString()) { adb.shell("rm -f $TMP"); throw IOException("copy to the TV incomplete ($size of ${apk.length()} bytes)") }
        return try { adb.shell("cat $TMP | pm install -r -S " + apk.length() + " 2>&1", 600000) } finally { adb.shell("rm -f $TMP") }
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
        if (sha256 != null && hex != sha256) { f.delete(); throw IOException(t("checksum không khớp, không cài", "checksum mismatch, not installing")) }
        return f
    }
}
