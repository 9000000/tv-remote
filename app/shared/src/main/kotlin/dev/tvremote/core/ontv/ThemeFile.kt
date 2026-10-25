package dev.tvremote.core.ontv
import dev.tvremote.core.B64
import dev.tvremote.home.HomeConfig
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream

/**
 * A TV Home theme as a file to keep, `<name>.tvtheme.json`: `{tvtheme:1, name, conf, media?}`. `media` carries the theme's own photos and
 * videos (the keys its `wall` / `walls` use: photo, photo2 .. vid:xxxxxxxx) as `data:image/jpeg;base64,...` / `data:video/mp4;base64,...`;
 * a file without it (version 1 as it always was) still reads. Written here for the computer and the phone, which stream the bytes from the
 * TV through to the browser (a video never sits whole in memory); TV Home's own page makes its file in the page (js/home_media.js, the
 * same shape, the same limits). The import side is the page's: it uploads each item and rewrites the keys.
 */
object ThemeFile {
    /** One photo or video in a file: more than this is refused, on both sides (js `HOME_MEDIA_MAX`). */
    const val MEDIA_MAX = 60L * 1024 * 1024

    /** The theme's own photos and videos in order of use (the wall first, then its rotation), each once. */
    fun mediaKeys(conf: JSONObject): List<String> {
        val rot = conf.optJSONArray("walls")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        return (listOf(conf.optString("wall")) + rot).filter { HomeConfig.isPhoto(it) || HomeConfig.isVideo(it) }.distinct()
    }

    // TV Home keeps every video as <id>.mp4 whatever it holds: a WebM (EBML 1A 45 DF A3) saved from a link is told by its first bytes
    fun mime(key: String, head: ByteArray, len: Int) = when {
        !HomeConfig.isVideo(key) -> "image/jpeg"
        len >= 4 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte() -> "video/webm"
        else -> "video/mp4"
    }

    /**
     * Writes the file with the items of [keys] (the caller has checked their sizes against [MEDIA_MAX]: a refusal can still be an error
     * reply only before the first byte); [feed] sends an item's bytes to the sink it is given.
     */
    @Throws(IOException::class)
    fun write(out: OutputStream, name: String, conf: JSONObject, keys: List<String>, feed: (String, (ByteArray, Int) -> Unit) -> Unit) {
        val head = JSONObject().put("tvtheme", 1).put("name", name).put("conf", conf).toString()
        out.write(head.substring(0, head.length - 1).toByteArray())                 // up to the last }: media follows
        if (keys.isNotEmpty()) {
            out.write(",\"media\":{".toByteArray())
            keys.forEachIndexed { i, k ->
                out.write((if (i > 0) "," else "").toByteArray()); out.write((JSONObject.quote(k) + ":\"data:").toByteArray())
                val buf = ByteArray(48 * 1024); var n = 0                            // a multiple of 3 goes out at once, so no '=' lands mid-way
                var typed = false                                                    // the type waits for the first bytes
                fun type(b: ByteArray, len: Int) { if (!typed) { typed = true; out.write((mime(k, b, len) + ";base64,").toByteArray()) } }
                feed(k) { b, len ->
                    type(b, len)
                    var off = 0
                    while (off < len) {
                        val take = minOf(len - off, buf.size - n); System.arraycopy(b, off, buf, n, take); n += take; off += take
                        if (n == buf.size) { out.write(B64.encode(buf).toByteArray()); n = 0 }
                    }
                }
                type(buf, 0)
                if (n > 0) out.write(B64.encode(buf.copyOf(n)).toByteArray())
                out.write('"'.code)
            }
            out.write('}'.code)
        }
        out.write('}'.code)
    }
}
