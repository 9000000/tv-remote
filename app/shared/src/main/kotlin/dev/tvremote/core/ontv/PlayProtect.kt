package dev.tvremote.core.ontv
import java.io.File
import java.io.IOException

/**
 * Google Play Protect on a TV can refuse one build of our own apps: a cloud verdict on that file, not on the code (v0.35.1's
 * tv-home.apk on the FPT box, 2026-10-30, while v0.34.0's and v0.36.0's builds of the same code went in). Every try shows
 * "Harmful app blocked" on the TV, so the updates TV Setup makes by itself must not try that file again every few hours: its
 * SHA-256 is kept in [dir] and skipped until a release carries another file.
 */
object PlayProtect {
    const val REFUSED = "INSTALL_FAILED_VERIFICATION_FAILURE"         // what pm says; the page words it (core.fr_play_protect)
    private const val FILE = "play-protect-blocked.txt"

    fun blocked(dir: File?, sha256: String): Boolean =
        dir != null && sha256.isNotEmpty() && try { File(dir, FILE).takeIf { it.isFile }?.readLines()?.contains(sha256) == true } catch (e: IOException) { false }

    /** Throws when [sha256] was refused before; after an install, keeps it when [out] says Play Protect refused it. */
    @Throws(IOException::class)
    fun check(dir: File?, sha256: String, out: String? = null) {
        if (out == null) { if (blocked(dir, sha256)) throw IOException("launcher:install:$REFUSED"); return }
        if (out.contains(REFUSED) && dir != null && sha256.isNotEmpty() && !blocked(dir, sha256))
            try { File(dir, FILE).appendText(sha256 + "\n") } catch (e: IOException) { }
    }
}
