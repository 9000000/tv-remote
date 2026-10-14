package dev.tvremote.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * "Optimize TV": the same steps as app/web/cli/tv_setup.py, run over an ADB connection. The TV app runs them on itself,
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
        private const val TMP = "/data/local/tmp/tvr-setup.apk"
        const val ANIM = "window_animation_scale transition_animation_scale animator_duration_scale"

        private fun last(out: String): String = out.split("\n").dropLastWhile { it.isEmpty() }.lastOrNull() ?: ""
    }

    fun interface Log { fun line(s: String) }

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

    @Throws(Exception::class)
    fun apply() {
        if (has(TS_PKG)) {
            adb.shell("dumpsys deviceidle whitelist +$TS_PKG")                     // exempt from Doze / battery optimisation
            adb.shell("am set-standby-bucket $TS_PKG active")
            adb.shell("cmd appops set $TS_PKG RUN_IN_BACKGROUND allow; cmd appops set $TS_PKG RUN_ANY_IN_BACKGROUND allow")
            adb.shell("settings put secure always_on_vpn_app $TS_PKG; settings put secure always_on_vpn_lockdown 0")
            log.line(t("✓ Tailscale: giữ sống qua standby và khởi động lại", "✓ Tailscale: kept alive through standby and restarts"))
        } else {
            log.line(t("– Tailscale chưa cài, bỏ qua bước giữ sống (cài xong hãy chạy lại)", "– Tailscale is not installed, skipping keep-alive (run this again once it is)"))
        }
        adb.shell("settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0.5")   // not 0 for the last one: loading spinners would freeze
        log.line(t("✓ Hiệu ứng chuyển cảnh: tắt", "✓ Transition animations: off"))

        var off = 0
        for (group in arrayOf(GOOGLE, SONY))
            for (p in group)
                if (has(p)) { adb.shell("pm disable-user --user 0 $p; am force-stop $p"); off++ }
        log.line(t("✓ Đã tắt $off app hệ thống không dùng", "✓ Disabled $off unused system apps"))

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
        for (group in arrayOf(LAUNCHER, GOOGLE, SONY))
            for (p in group)
                if (has(p)) { adb.shell("pm enable --user 0 $p"); on++ }
        adb.shell("for k in $ANIM; do settings put global \$k 1.0; done")
        log.line(t("✓ Đã bật lại $on app (gồm màn hình chính Google), hiệu ứng về 1.0x", "✓ Re-enabled $on apps (Google's home screen among them), animations back to 1.0x"))
        log.line(t("FLauncher và cài đặt Tailscale được giữ nguyên.", "FLauncher and the Tailscale settings are left as they are."))
    }

    /** Copies the APK to the TV through ADB and has the shell user install it there (an app may not install on its own). */
    @Throws(IOException::class)
    fun install(apk: File): String {
        val s = adb.exec("cat > $TMP")
        try {
            apk.inputStream().use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    s.write(buf, 0, n)
                }
            }
        } finally {
            s.close()
        }
        for (i in 0 until 60) {                                       // cat finishes writing shortly after the stream closes
            if (adb.shell("stat -c %s $TMP 2>/dev/null").trim() == apk.length().toString()) break
            Thread.sleep(150)
        }
        return try { adb.shell("cat $TMP | pm install -r -S " + apk.length() + " 2>&1") } finally { adb.shell("rm -f $TMP") }
    }

    @Throws(Exception::class)
    fun download(url: String, name: String, sha256: String?): File {
        val f = File(dir, name)
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 60000
        c.instanceFollowRedirects = true
        val md = MessageDigest.getInstance("SHA-256")
        try {
            c.inputStream.use { input ->
                FileOutputStream(f).use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n); md.update(buf, 0, n)
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
