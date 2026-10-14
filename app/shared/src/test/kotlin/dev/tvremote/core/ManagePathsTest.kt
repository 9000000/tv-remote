package dev.tvremote.core

import org.junit.Assert.assertEquals
import org.junit.Test

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

    @Test fun garbageIsNull() {
        assertEquals(null, Manage.apkPackage(ByteArray(0)))
        assertEquals(null, Manage.apkPackage("not a manifest at all".toByteArray()))
    }
}
