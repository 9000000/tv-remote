package dev.tvremote.core

/**
 * The one check for an Android package name before it goes into a shell command. Each later segment starts with a letter or
 * underscore, as Android requires (a segment led by a digit is no package), so every screen refuses the same names.
 */
object Pkg {
    /** For java.util.regex and grep -E alike. */
    const val PATTERN = "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"
    val RE = Regex(PATTERN)
    fun valid(s: String): Boolean = RE.matches(s)
}
