package dev.tvremote.setup

import dev.tvremote.core.Adb
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** The same steps as deploy/tv_setup.py, run from the TV itself through its own ADB shell. */
internal class Setup(private val adb: Adb, private val dir: File?, private val log: Log) {
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
            log.line("✓ Tailscale: giữ sống qua standby và khởi động lại")
        } else {
            log.line("– Tailscale chưa cài, bỏ qua bước giữ sống (cài xong hãy chạy lại)")
        }
        adb.shell("for k in $ANIM; do settings put global \$k 0.5; done")
        log.line("✓ Hiệu ứng chuyển cảnh: 0.5x")

        var off = 0
        for (group in arrayOf(GOOGLE, SONY))
            for (p in group)
                if (has(p)) { adb.shell("pm disable-user --user 0 $p; am force-stop $p"); off++ }
        log.line("✓ Đã tắt $off app hệ thống không dùng")

        if (!has(FL_PKG)) {
            log.line("… Đang tải FLauncher (10 MB)")
            val apk = download(FL_URL, "flauncher.apk", FL_SHA)
            log.line("… Đang cài FLauncher")
            val out = install(apk)
            apk.delete()
            if (!out.contains("Success")) { log.line("✗ Cài FLauncher lỗi: " + last(out) + " — giữ nguyên màn hình chính Google"); return }
        }
        for (p in LAUNCHER)
            if (has(p)) adb.shell("pm disable-user --user 0 $p; am force-stop $p")
        log.line("✓ Màn hình chính: " + last(adb.shell("cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN")))
        log.line("Xong. Bấm nút Home để thấy màn hình chính mới.")
    }

    @Throws(Exception::class)
    fun undo() {
        var on = 0
        for (group in arrayOf(LAUNCHER, GOOGLE, SONY))
            for (p in group)
                if (has(p)) { adb.shell("pm enable --user 0 $p"); on++ }
        adb.shell("for k in $ANIM; do settings put global \$k 1.0; done")
        log.line("✓ Đã bật lại $on app (gồm màn hình chính Google), hiệu ứng về 1.0x")
        log.line("FLauncher và cài đặt Tailscale được giữ nguyên.")
    }

    /** The shell user may install an APK; this app may not, so hand the file to `pm` through the ADB shell. */
    @Throws(IOException::class)
    fun install(apk: File): String =
        adb.shell("cat '" + apk.absolutePath + "' | pm install -r -S " + apk.length() + " 2>&1")

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
        if (sha256 != null && hex != sha256) { f.delete(); throw IOException("checksum không khớp, không cài") }
        return f
    }
}
