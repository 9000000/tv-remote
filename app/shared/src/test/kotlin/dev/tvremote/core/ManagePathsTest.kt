package dev.tvremote.core

import org.junit.Assert.assertEquals
import org.junit.Test
import dev.tvremote.core.web.Manage

private typealias Proc = Manage.Companion.Proc

/** The checks that keep the file manager inside the TV's shared storage. */
class ManagePathsTest {
    @Test fun pathsStayInSharedStorage() {
        assertEquals("/sdcard/Download", Manage.safePath(""))
        assertEquals("/sdcard/Movies/a b", Manage.safePath("/sdcard//Movies/./a b/"))
        assertEquals("/storage/ABCD-1234", Manage.safePath("/storage/ABCD-1234"))
    }

    @Test(expected = IllegalArgumentException::class) fun noDotDot() { Manage.safePath("/sdcard/../data") }
    @Test(expected = IllegalArgumentException::class) fun noOtherRoots() { Manage.safePath("/data/local/tmp") }

    @Test fun safeNames() {
        assertEquals("passwd", Manage.safeName("../../etc/passwd"))
        assertEquals("a_b.txt", Manage.safeName("a;b.txt"))
        assertEquals("phim Việt (1).mp4", Manage.safeName("phim Việt (1).mp4"))
        assertEquals("file", Manage.safeName("..."))
        assertEquals(120, Manage.safeName("x".repeat(300)).length)
    }

    @Test fun memoryPerApp() {
        val sample = """Total PSS by process:
    119,516K: com.google.android.youtube.tv (pid 4709 / activities)
     73,623K: com.google.android.katniss:search (pid 4380)
     40,000K: com.google.android.katniss:interactor (pid 24727)
      3,000K: com.tiny.app (pid 1)

Total PSS by OOM adjustment:
    119,516K: com.google.android.youtube.tv (pid 4709 / activities)
"""
        assertEquals(listOf("com.google.android.youtube.tv" to 116, "com.google.android.katniss" to 110), Manage.memoryByApp(sample))
        assertEquals(emptyList<Pair<String, Int>>(), Manage.memoryByApp("nothing"))
    }

    @Test fun taskManager() {
        val sample = """Total PSS by OOM adjustment:
    307,656K: Native
         49,156K: app_process (pid 5284)
         16,486K: android.hardware.graphics.composer3-service.ranchu (pid 356)
    148,979K: System
        148,979K: system (pid 511)
     70,393K: Persistent
         47,985K: com.android.systemui (pid 732)
           4,995K: com.android.se (pid 871)
    148,933K: Foreground
         77,285K: com.google.android.webview:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0 (pid 5417)
         71,648K: com.netflix.ninja (pid 5382 / activities)
    115,189K: Perceptible
         61,749K: com.google.android.inputmethod.latin (pid 1076)
         22,557K: com.google.android.apps.tv.launcherx:coreservices (pid 1171)
     46,319K: A Services
         46,319K: com.spotify.tv.android (pid 1134)
     62,429K: Home
         62,429K: com.google.android.apps.tv.launcherx (pid 1175 / activities)
    352,376K: Cached
         31,952K: com.spotify.tv.android:background (pid 2489)
         21,124K: com.google.android.youtube.tv (pid 2016)
         10,977K: android.process.acore (pid 1287)
          8,406K: com.android.keychain (pid 1257)

Total PSS by category:
"""
        val keep = Manage.keepNow("com.google.android.apps.tv.launcherx/.home.HomeActivity\ncom.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME\n" +
            "package:com.netflix.ninja\npackage:com.spotify.tv.android\n" +
            "com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity\ncom.netflix.ninja/.MainActivity")
        assertEquals(listOf(
            Proc("com.google.android.apps.tv.launcherx", 82, "home", "home"),   // its coreservices process counts for it
            Proc("com.spotify.tv.android", 76, "bg", null),                      // the busiest of its processes: playing
            Proc("com.netflix.ninja", 69, "front", null),
            Proc("com.google.android.inputmethod.latin", 60, "bg", "keyboard"),
            Proc("com.android.systemui", 46, "system", "system"),
            Proc("com.google.android.youtube.tv", 20, "cached", null),
            Proc("android.process.acore", 10, "cached", "system"),
            Proc("com.android.keychain", 8, "cached", "system"),          // a system app with no tile: not for people to stop
            Proc("com.android.se", 4, "system", "system"),
        ), Manage.procsOf(sample, keep))
        assertEquals("us", Manage.keepWhy("dev.tvremote.setup", keep))
        assertEquals(emptyList<Proc>(), Manage.procsOf("nothing", keep))
    }

    @Test fun mimeTypes() {
        assertEquals("video/mp4", Manage.mime("/sdcard/a.MP4"))
        assertEquals("application/vnd.android.package-archive", Manage.mime("x.apk"))
        assertEquals("*/*", Manage.mime("x.unknown"))
    }
}

/** The package name read from an APK's binary manifest, to check after an install that the TV kept the app. */
class ApkPackageTest {
    @Test fun readsPackage() {
        val m = javaClass.classLoader!!.getResourceAsStream("setup-manifest.bin").readBytes()
        assertEquals("dev.tvremote.setup", Manage.apkPackage(m))
    }

    @Test fun infoAndFit() {
        val i = Manage.apkInfo(javaClass.classLoader!!.getResourceAsStream("setup-manifest.bin").readBytes())
        assertEquals("dev.tvremote.setup", i["package"])
        assertEquals("21", i["minSdkVersion"])
        assertEquals(true, i["versionCode"]?.toIntOrNull() != null)
        assertEquals(listOf("arm64-v8a", "x86"), Manage.apkAbis(listOf("lib/arm64-v8a/a.so", "lib/x86/a.so", "lib/x86/b.so", "lib/readme.txt", "assets/lib/x/c.so")))
        assertEquals(null, Manage.apkFits(i, emptyList(), 28, listOf("armeabi-v7a")))
        assertEquals("apk needs sdk 29, the tv has sdk 28", Manage.apkFits(mapOf("minSdkVersion" to "29"), emptyList(), 28, emptyList()))
        assertEquals("apk is built for arm64-v8a, the tv runs armeabi-v7a,armeabi", Manage.apkFits(emptyMap(), listOf("arm64-v8a"), 28, listOf("armeabi-v7a", "armeabi")))
        assertEquals(null, Manage.apkFits(emptyMap(), listOf("arm64-v8a", "armeabi-v7a"), 28, listOf("armeabi-v7a")))
    }

    @Test fun garbageIsNull() {
        assertEquals(null, Manage.apkPackage(ByteArray(0)))
        assertEquals(null, Manage.apkPackage("not a manifest at all".toByteArray()))
    }
}
