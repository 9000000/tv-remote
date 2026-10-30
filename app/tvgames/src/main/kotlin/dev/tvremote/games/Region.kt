package dev.tvremote.games

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32

/** The NES video region of a ROM (fceumm_region): never from the file name. Order: what was remembered for this ROM's sha256, the
 *  catalog entry's "options", the iNES / NES 2.0 header timing bits, a CRC of known PAL-only homebrew, else Auto. */
object Region {
    // CRC32 of PRG+CHR (the file without its 16-byte header) of ROMs whose header says nothing but that only run on PAL
    private val PAL_CRC = setOf(0x5548724AL)   // Robo Run NES (its header has the timing bits zero)

    fun of(c: Context, f: File): String {
        if (f.length() > 4 shl 20) return "Auto"
        val b = f.readBytes()
        val sha = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
        val kept = File(c.filesDir, "regions.json")
        val mem = try { JSONObject(kept.takeIf { it.exists() }?.readText() ?: "{}") } catch (e: Exception) { JSONObject() }
        mem.optString(sha).takeIf { it.isNotEmpty() }?.let { return it }
        val r = fromCatalog(c, sha) ?: fromHeader(b) ?: fromCrc(b) ?: return "Auto"
        remember(c, sha, r)
        return r
    }

    /** Called with a ROM the catalog just gave us, so the option survives the entry being hidden or removed. */
    fun remember(c: Context, sha: String, region: String) {
        try {
            val kept = File(c.filesDir, "regions.json")
            val mem = try { JSONObject(kept.takeIf { it.exists() }?.readText() ?: "{}") } catch (e: Exception) { JSONObject() }
            kept.writeText(mem.put(sha, region).toString())
        } catch (e: Exception) { }
    }

    private fun norm(s: String) = when (s.lowercase()) { "pal" -> "PAL"; "ntsc" -> "NTSC"; "dendy" -> "Dendy"; else -> null }

    fun fromCatalog(c: Context, sha: String): String? {
        val a = Catalog.peek(c).optJSONArray("homebrew") ?: return null
        for (i in 0 until a.length()) {
            val h = a.optJSONObject(i) ?: continue
            if (h.optString("sha256").lowercase() == sha) return norm(h.optJSONObject("options")?.optString("fceumm_region") ?: "")
        }
        return null
    }

    fun fromHeader(b: ByteArray): String? {
        if (b.size < 16 || b[0] != 'N'.code.toByte() || b[1] != 'E'.code.toByte() || b[2] != 'S'.code.toByte() || b[3] != 0x1A.toByte()) return null
        if (b[7].toInt() and 0x0C == 0x08) return when (b[12].toInt() and 3) { 1 -> "PAL"; 3 -> "Dendy"; else -> null }   // NES 2.0: 0 NTSC, 2 both
        return if (b[9].toInt() and 1 == 1) "PAL" else null                                                              // iNES: bit 0 of byte 9
    }

    fun fromCrc(b: ByteArray): String? {
        if (b.size <= 16) return null
        val k = CRC32().apply { update(b, 16, b.size - 16) }.value
        return if (k in PAL_CRC) "PAL" else null
    }
}
