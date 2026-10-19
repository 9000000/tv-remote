package dev.tvremote.core

import dev.tvremote.core.ontv.Launchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException

/** The list the apps fetch: the real one in docs/launchers (signed), what a tampered one does, and which launcher fits which TV. */
class LaunchersTest {
    private val dir = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "docs/launchers") }.first { it.isDirectory }
    private val list get() = File(dir, "launchers.json").readBytes()
    private val sig get() = File(dir, "launchers.json.sig").readText()

    @Test fun theShippedListIsSignedByTheAppsKey() = assertTrue(Launchers.signed(list, sig))

    @Test fun aChangedByteBreaksTheSignature() {
        val b = list.copyOf(); b[b.indexOf('"'.code.toByte()) + 5] = 'X'.code.toByte()
        assertFalse(Launchers.signed(b, sig))
    }

    @Test fun anotherKeysOrMissingSignatureIsRefused() {
        assertFalse(Launchers.signed(list, ""))
        assertFalse(Launchers.signed(list, "not base64 !!"))
        assertFalse(Launchers.signed(list, java.util.Base64.getEncoder().encodeToString(ByteArray(64))))
    }

    @Test fun everyEntryHasBothLanguagesAndAPinnedHash() {
        val g = Launchers.parse(list)
        assertTrue(g.entries.size >= 3)
        for (e in g.entries) {
            assertTrue(e.id, e.desc("vi").isNotEmpty() && e.desc("en").isNotEmpty())
            assertTrue(e.id, e.thumbSha.length == 64)
            when (e.kind) {
                "apk" -> assertTrue(e.id, e.files.isNotEmpty() && e.files.all { it.url.startsWith("https://") && it.sha256.length == 64 })
                "self" -> assertEquals(e.id, "dev.tvremote.home", e.pkg)       // its file is pinned by the release's signed SHA256SUMS
                else -> assertTrue(e.playUrl.startsWith("market://details?id=" + e.pkg))
            }
        }
    }

    @Test fun aListThatIsNotOursIsAnErrorNotAPartialList() {
        for (bad in listOf("{}", "{\"v\":2,\"launchers\":[]}", "not json", "{\"v\":1,\"launchers\":[{\"id\":\"x\"}]}"))
            try { Launchers.parse(bad.toByteArray()); fail(bad) } catch (e: IOException) { }
    }

    private fun e(id: String) = Launchers.parse(list)[id]!!
    private fun tv(sdk: Int = 34, abis: String = "arm64-v8a", lean: Boolean = true, gtv: Boolean = true, fire: Boolean = false) =
        Launchers.Facts(sdk, abis.split(','), lean, gtv, fire)

    @Test fun fitFollowsTheTv() {
        assertTrue(Launchers.fits(e("flauncher"), tv()).ok)
        assertEquals("sdk", Launchers.fits(e("flauncher"), tv(sdk = 19)).why)
        assertEquals("not_tv", Launchers.fits(e("flauncher"), tv(lean = false)).why)
        assertEquals("fire", Launchers.fits(e("flauncher"), tv(fire = true)).why)
        assertEquals("sdk", Launchers.fits(e("projectivy"), tv(sdk = 22)).why)
        assertTrue(Launchers.fits(e("just-a-launcher"), tv(abis = "x86_64")).ok)    // no native code: any chip
        assertEquals("abi", Launchers.fits(e("flauncher"), tv(abis = "x86_64")).why)
    }

    @Test fun aLauncherWithoutTheTvsLanguageShowsEnglish() {
        assertTrue(Launchers.english(e("ltvlauncher"), "vi"))           // Flutter: Arabic, its first language, otherwise
        assertTrue(Launchers.english(e("unitedu"), "vi"))               // its default words are Chinese
        assertFalse(Launchers.english(e("ltvlauncher"), "de"))
        assertFalse(Launchers.english(e("projectivy"), "vi"))
        assertFalse(Launchers.english(e("tv-home"), "vi"))
        assertFalse(Launchers.english(e("ltvlauncher"), ""))            // the TV's language unknown: left alone
        assertFalse(Launchers.english(e("monet"), "vi"))                // its languages unknown: left alone
        assertEquals(listOf("vi", "id", "he", "zh"), listOf("vi-VN", "in", "iw_IL", "zh-Hans-CN").map(Launchers::lang))
        assertEquals("vi", Launchers.tvLang { if (it.startsWith("settings")) "vi-VN,en-US\n" else "" })
        assertEquals("en", Launchers.tvLang { "null\n\nen-US\n" })
    }

    @Test fun tvHomeComesFirstAndFitsEveryChip() {
        assertEquals("tv-home", Launchers.parse(list).entries.first().id)
        assertTrue(Launchers.fits(e("tv-home"), tv(abis = "x86_64")).ok)       // no native code, and no file listed to pick from
        assertEquals("fire", Launchers.fits(e("tv-home"), tv(fire = true)).why)
    }

    @Test fun the32BitFileIsPreferredWhereBothRun() {
        assertEquals("arm64-v8a", Launchers.apk(e("flauncher"), listOf("arm64-v8a"))!!.abi)
        assertEquals("armeabi-v7a", Launchers.apk(e("flauncher"), listOf("arm64-v8a", "armeabi-v7a", "armeabi"))!!.abi)
        assertNull(Launchers.apk(e("flauncher"), listOf("x86")))
        assertNotNull(Launchers.apk(e("just-a-launcher"), listOf("x86")))
    }

    @Test fun aDownloadWhoseHashDiffersIsDeleted() {
        val srv = java.net.ServerSocket(0, 5, java.net.InetAddress.getByName("127.0.0.1"))
        Thread {
            while (!srv.isClosed) try {
                srv.accept().use { c ->
                    val r = c.getInputStream().bufferedReader(); while (r.readLine().orEmpty().isNotEmpty()) { }
                    c.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nhello".toByteArray())
                }
            } catch (e: IOException) { }
        }.apply { isDaemon = true }.start()
        val f = File.createTempFile("launcher", ".apk")
        try {
            val url = "http://127.0.0.1:${srv.localPort}/a.apk"
            try { Launchers.download(Launchers.Apk("any", url, "0".repeat(64)), f); fail() } catch (e: IOException) { assertEquals(Launchers.MISMATCH, e.message) }
            assertFalse(f.exists())
            val sha = java.security.MessageDigest.getInstance("SHA-256").digest("hello".toByteArray()).joinToString("") { "%02x".format(it) }
            Launchers.download(Launchers.Apk("any", url, sha), f)
            assertTrue(f.isFile)
        } finally { srv.close(); f.delete() }
    }
}
