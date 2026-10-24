package dev.tvremote.core.ontv

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The binary AndroidManifest.xml (AXML) of an APK, edited without apktool, to make "the same app under another name": `package`
 * becomes `<package>.copy`, and what would clash with the original on the TV follows it (provider authorities, the app's own
 * permissions), and a literal label gets " (2)" after it. Class names stay as they are (fully qualified ones are valid whatever the package), and the relative ones
 * (".Main", "Main") are written out against the OLD package first, since the package under them changes. resources.arsc is left
 * alone: it does not hold the package name the code sees (Gradle's applicationId works the same way).
 *
 * Nothing is edited in place: a new string is added to the end of the pool and the attribute pointed at it, so a string shared
 * by two attributes (a name used as a class and as a label) cannot change under the other one. Both pool encodings are read and
 * written; a pool with styles (never in a manifest) is refused.
 */
object Axml {
    class Unusual(msg: String) : IOException(msg)
    class Result(val bytes: ByteArray, val oldPkg: String, val newPkg: String)

    private const val T_XML = 0x0003
    private const val T_POOL = 0x0001
    private const val T_START = 0x0102
    private const val STRING = 3                                       // TYPE_STRING of a typed value
    private val CLASS_ELEMENTS = setOf("application", "activity", "activity-alias", "service", "receiver", "provider")
    private val CLASS_ATTRS = setOf("targetActivity", "parentActivityName", "backupAgent", "manageSpaceActivity")
    private val PERM_ATTRS = setOf("permission", "readPermission", "writePermission")
    private val PERM_ELEMENTS = setOf("permission", "permission-tree", "permission-group")

    /** The attributes of every element, as (element, attribute, string value), for tests and for deciding what to rename. */
    fun attributes(src: ByteArray): List<Triple<String, String, String>> {
        val out = ArrayList<Triple<String, String, String>>()
        val p = readPool(src)
        walk(src, p) { el, name, at -> val v = p.strings.getOrNull(strIndex(src, at)); if (v != null) out.add(Triple(el, name, v)) }
        return out
    }

    fun clone(src: ByteArray, suffix: String = ".copy"): Result {
        val pool = readPool(src)
        val oldPkg = attributes(src).firstOrNull { it.first == "manifest" && it.second == "package" }?.third
            ?: throw Unusual("no package in the manifest")
        val newPkg = oldPkg + suffix
        val m = src.copyOf()
        val strings = ArrayList(pool.strings)
        val added = HashMap<String, Int>()
        fun idx(s: String): Int = added.getOrPut(s) { strings.add(s); strings.size - 1 }
        fun own(v: String) = if (v == oldPkg || v.startsWith("$oldPkg.")) newPkg + v.substring(oldPkg.length) else null
        fun cls(v: String) = if (v.startsWith(".")) oldPkg + v else if (!v.contains('.')) "$oldPkg.$v" else null
        walk(m, pool) { el, name, at ->
            val v = pool.strings.getOrNull(strIndex(m, at)) ?: return@walk
            val nv: String? = when {
                el == "manifest" && name == "package" -> newPkg
                name == "label" && (el == "application" || el == "activity" || el == "activity-alias") -> "$v (2)"   // told apart from the original in a launcher; a resource reference is not a string here, so it stays
                name == "name" && el in CLASS_ELEMENTS -> cls(v)
                name in CLASS_ATTRS -> cls(v)
                el == "provider" && name == "authorities" -> v.split(';').joinToString(";") { own(it) ?: it }.takeIf { it != v }
                name == "name" && (el in PERM_ELEMENTS || el.startsWith("uses-permission")) -> own(v)
                name in PERM_ATTRS -> own(v)
                else -> null
            }
            if (nv != null && nv != v) { val i = idx(nv); m.putInt(at + 8, i); m.putInt(at + 16, i) }
        }
        val rest = m.copyOfRange(pool.end, m.size)
        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putShort(T_XML.toShort()).putShort(8).putInt(0).array()
        val np = writePool(strings, pool.utf8)
        out.write(header); out.write(np); out.write(rest)
        val bytes = out.toByteArray()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(4, bytes.size)
        return Result(bytes, oldPkg, newPkg)
    }

    private fun ByteArray.putInt(at: Int, v: Int) { for (k in 0..3) this[at + k] = (v shr (8 * k)).toByte() }
    private fun ByteArray.u16(at: Int) = (this[at].toInt() and 0xff) or ((this[at + 1].toInt() and 0xff) shl 8)
    private fun ByteArray.i32(at: Int) = u16(at) or (u16(at + 2) shl 16)
    /** The string index of the attribute at [at]: its typed value when that is a string. */
    private fun strIndex(b: ByteArray, at: Int): Int = if ((b[at + 15].toInt() and 0xff) == STRING) b.i32(at + 16) else -1

    private class Pool(val strings: List<String>, val utf8: Boolean, val end: Int)

    private fun readPool(b: ByteArray): Pool {
        if (b.size < 36 || b.u16(0) != T_XML) throw Unusual("not a binary manifest")
        val s = b.u16(2)
        if (b.u16(s) != T_POOL) throw Unusual("no string pool")
        val hdr = b.u16(s + 2); val size = b.i32(s + 4); val count = b.i32(s + 8); val styles = b.i32(s + 12)
        val flags = b.i32(s + 16); val start = b.i32(s + 20)
        if (styles != 0) throw Unusual("styled strings in the manifest")
        if (count < 0 || count > 200000 || s + size > b.size) throw Unusual("bad string pool")
        val utf8 = flags and 0x100 != 0
        val list = ArrayList<String>(count)
        for (i in 0 until count) {
            var p = s + start + b.i32(s + hdr + i * 4)
            if (utf8) {
                fun len(): Int { var l = b[p++].toInt() and 0xff; if (l and 0x80 != 0) l = ((l and 0x7f) shl 8) or (b[p++].toInt() and 0xff); return l }
                len(); val n = len()
                list.add(String(b, p, n, Charsets.UTF_8))
            } else {
                var n = b.u16(p); p += 2
                if (n and 0x8000 != 0) { n = ((n and 0x7fff) shl 16) or b.u16(p); p += 2 }
                list.add(String(b, p, n * 2, Charsets.UTF_16LE))
            }
        }
        return Pool(list, utf8, s + size)
    }

    private fun writePool(strings: List<String>, utf8: Boolean): ByteArray {
        val data = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)
        strings.forEachIndexed { i, str ->
            offsets[i] = data.size()
            if (utf8) {
                val bytes = str.toByteArray(Charsets.UTF_8)
                fun len(n: Int) { if (n > 0x7f) { data.write((n shr 8) or 0x80); data.write(n and 0xff) } else data.write(n) }
                len(str.length); len(bytes.size); data.write(bytes); data.write(0)
            } else {
                fun u16(n: Int) { data.write(n and 0xff); data.write((n shr 8) and 0xff) }
                if (str.length > 0x7fff) { u16((str.length shr 16) or 0x8000); u16(str.length and 0xffff) } else u16(str.length)
                data.write(str.toByteArray(Charsets.UTF_16LE)); data.write(0); data.write(0)
            }
        }
        while (data.size() % 4 != 0) data.write(0)
        val hdr = 28 + strings.size * 4
        val total = hdr + data.size()
        val o = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        o.putShort(T_POOL.toShort()).putShort(28).putInt(total).putInt(strings.size).putInt(0).putInt(if (utf8) 0x100 else 0).putInt(hdr).putInt(0)
        offsets.forEach { o.putInt(it) }
        o.put(data.toByteArray())
        return o.array()
    }

    /** Calls [f] for each attribute of each start tag: the element's name, the attribute's name, the attribute's offset in [b]. */
    private fun walk(b: ByteArray, pool: Pool, f: (String, String, Int) -> Unit) {
        var p = pool.end
        while (p + 8 <= b.size) {
            val type = b.u16(p); val size = b.i32(p + 4)
            if (size < 8 || p + size > b.size) throw Unusual("bad chunk")
            if (type == T_START) {
                val el = pool.strings.getOrNull(b.i32(p + 20)) ?: ""
                val aStart = b.u16(p + 24); val aSize = b.u16(p + 26); val aCount = b.u16(p + 28)
                for (i in 0 until aCount) {
                    val at = p + 16 + aStart + i * aSize
                    f(el, pool.strings.getOrNull(b.i32(at + 4)) ?: "", at)
                }
            }
            p += size
        }
    }
}
