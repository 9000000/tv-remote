package dev.tvremote.core.ontv

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The manifest rewrite behind "Install as a separate app", on a hand-made binary manifest (both string pool encodings). */
class AxmlTest {
    private fun le(n: Int) = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN)

    /** A manifest: elements with string attributes only, in order (no nesting needed: the walker reads start tags one after another). */
    private fun build(utf8: Boolean, elems: List<Pair<String, List<Pair<String, String>>>>, styled: Boolean = false): ByteArray {
        val strings = ArrayList<String>()
        fun id(s: String): Int { val i = strings.indexOf(s); if (i >= 0) return i; strings.add(s); return strings.size - 1 }
        val chunks = ByteArrayOutputStream()
        for ((el, attrs) in elems) {
            val body = le(36 + 20 * attrs.size).putShort(0x0102).putShort(16).putInt(36 + 20 * attrs.size).putInt(1).putInt(-1)
                .putInt(-1).putInt(id(el)).putShort(20).putShort(20).putShort(attrs.size.toShort()).putShort(0).putShort(0).putShort(0)
            for ((n, v) in attrs) { val vi = id(v); body.putInt(-1).putInt(id(n)).putInt(vi).putShort(8).put(0).put(3).putInt(vi) }
            chunks.write(body.array())
        }
        val data = ByteArrayOutputStream(); val offs = ArrayList<Int>()
        for (s in strings) {
            offs.add(data.size())
            if (utf8) { val b = s.toByteArray(); data.write(s.length); data.write(b.size); data.write(b); data.write(0) }
            else { data.write(s.length); data.write(0); data.write(s.toByteArray(Charsets.UTF_16LE)); data.write(0); data.write(0) }
        }
        while (data.size() % 4 != 0) data.write(0)
        val hdr = 28 + 4 * strings.size
        val pool = le(hdr + data.size()).putShort(1).putShort(28).putInt(hdr + data.size()).putInt(strings.size).putInt(if (styled) 1 else 0)
            .putInt(if (utf8) 0x100 else 0).putInt(hdr).putInt(0)
        offs.forEach { pool.putInt(it) }
        pool.put(data.toByteArray())
        val total = 8 + pool.capacity() + chunks.size()
        return le(8).putShort(3).putShort(8).putInt(total).array() + pool.array() + chunks.toByteArray()
    }

    private val fixture = listOf(
        "manifest" to listOf("package" to "com.acme.tv", "versionName" to "1.0"),
        "uses-permission" to listOf("name" to "android.permission.INTERNET"),
        "uses-permission" to listOf("name" to "com.acme.tv.permission.PUSH"),
        "permission" to listOf("name" to "com.acme.tv.permission.PUSH"),
        "application" to listOf("name" to ".App", "label" to "com.acme.tv"),
        "activity" to listOf("name" to "com.acme.tv.ui.Main", "permission" to "com.acme.tv.permission.PUSH"),
        "service" to listOf("name" to "Sync"),
        "provider" to listOf("name" to "androidx.core.content.FileProvider", "authorities" to "com.acme.tv.files;other.authority"),
        "meta-data" to listOf("name" to "com.acme.tv.key"),
    )

    private fun check(utf8: Boolean) {
        val r = Axml.clone(build(utf8, fixture))
        assertEquals("com.acme.tv", r.oldPkg); assertEquals("com.acme.tv.copy", r.newPkg)
        val a = Axml.attributes(r.bytes)
        fun v(el: String, n: String, nth: Int = 0) = a.filter { it.first == el && it.second == n }[nth].third
        assertEquals("com.acme.tv.copy", v("manifest", "package"))
        assertEquals("android.permission.INTERNET", v("uses-permission", "name", 0))
        assertEquals("com.acme.tv.copy.permission.PUSH", v("uses-permission", "name", 1))
        assertEquals("com.acme.tv.copy.permission.PUSH", v("permission", "name"))
        assertEquals("com.acme.tv.copy.permission.PUSH", v("activity", "permission"))
        assertEquals("com.acme.tv.copy.files;other.authority", v("provider", "authorities"))
        assertEquals("com.acme.tv.App", v("application", "name"))             // relative: written against the OLD package
        assertEquals("com.acme.tv.Sync", v("service", "name"))
        assertEquals("com.acme.tv.ui.Main", v("activity", "name"))            // qualified: left alone
        assertEquals("androidx.core.content.FileProvider", v("provider", "name"))
        assertEquals("com.acme.tv (2)", v("application", "label"))             // a literal label is told apart
        assertEquals("com.acme.tv.key", v("meta-data", "name"))
        assertEquals("1.0", v("manifest", "versionName"))
        val again = Axml.clone(r.bytes)                                       // the result is a manifest still readable
        assertEquals("com.acme.tv.copy.copy", again.newPkg)
        assertEquals(r.bytes.size, ByteBuffer.wrap(r.bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(4))
    }

    @Test fun utf8Pool() = check(true)
    @Test fun utf16Pool() = check(false)

    @Test fun refusesWhatItDoesNotKnow() {
        try { Axml.clone(byteArrayOf(1, 2, 3)); fail() } catch (e: Axml.Unusual) { assertTrue(e.message!!.isNotEmpty()) }
        try { Axml.clone(build(true, fixture, styled = true)); fail() } catch (e: Axml.Unusual) { }
    }
}
