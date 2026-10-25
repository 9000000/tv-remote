package dev.tvremote.desktop

import dev.tvremote.core.Words
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Mac only, like LetsMove: an app opened from Downloads runs from a read-only translocated copy and cannot update itself, so
 * once per version it offers to move itself to Applications (the page's update card has the same button). The copy is made with
 * ditto next to its final place, checked, swapped in, and opened by a script once this process is gone (port 8765 is ours until
 * then). Any failure leaves the running app and the old copy where they are. The data folder is never touched.
 */
object Mover {
    const val ID = "dev.tvremote.desktop"

    /** Pure: this bundle is somewhere the updater cannot swap it: a translocated copy, or a folder other than the two Applications. */
    fun needsMove(mac: Boolean, bundle: String?, home: String): Boolean {
        if (!mac || bundle == null || !bundle.endsWith(".app")) return false
        if ("/AppTranslocation/" in bundle) return true
        val parent = File(bundle).parent
        return parent != "/Applications" && parent != "$home/Applications"
    }

    /** Pure: ask at start: it needs moving and "later" was not chosen for this version. */
    fun asks(mac: Boolean, bundle: String?, home: String, later: String, version: String): Boolean =
        needsMove(mac, bundle, home) && (later.trim().isEmpty() || later.trim() != version)

    private val mac = "mac" in System.getProperty("os.name").lowercase()
    private val home = System.getProperty("user.home")
    private fun laterFile() = File(App.data(), "move-later")
    private fun lang() = Words.lang

    fun can(bundle: File?) = needsMove(mac, bundle?.path, home)

    /** At start of the packaged app: the question, once per version. [quit] ends the app. TVR_MOVE_ANSWER=move|later answers it (tests). */
    fun offer(bundle: File?, version: String, quit: () -> Unit) {
        val later = try { laterFile().readText() } catch (_: Exception) { "" }
        if (!asks(mac, bundle?.path, home, later, version)) return
        val answer = System.getenv("TVR_MOVE_ANSWER") ?: ask()
        if (answer == "later") { try { laterFile().writeText(version) } catch (_: Exception) { } ; return }
        if (answer != "move") return
        val err = move(bundle!!, quit)
        if (err != null) App.alert(err)
    }

    private fun ask(): String {
        val yes = Words.get(lang(), "pc.move_yes"); val no = Words.get(lang(), "pc.move_later")
        val q = Words.get(lang(), "pc.move_ask").replace("\"", "'")
        val out = sh(120, "osascript", "-e", "display dialog \"$q\" buttons {\"$no\", \"$yes\"} default button \"$yes\" with title \"TV Remote\"").second
        return if (out.contains("button returned:$yes")) "move" else "later"   // closed with Esc counts as later
    }

    /** Moves [bundle] to Applications and opens the copy; returns the error words, or null when done (then [quit] has been called). */
    fun move(bundle: File, quit: () -> Unit): String? {
        return try {
            val src = original(bundle) ?: return Words.get(lang(), "pc.move_where", "name" to bundle.name).also { sh(10, "open", "-R", bundle.path) }
            val destDir = System.getenv("TVR_MOVE_DEST")?.let { File(it).also { d -> d.mkdirs() } }   // tests: not the real Applications
                ?: File("/Applications").takeIf { it.canWrite() } ?: File(home, "Applications").also { it.mkdirs() }
            val dest = File(destDir, src.name)
            if (src.canonicalPath == dest.canonicalPath) return null
            val tmp = File(destDir, ".${src.name}.new")
            sh(60, "rm", "-rf", tmp.path)
            run("ditto", src.path, tmp.path)
            sh(30, "xattr", "-dr", "com.apple.quarantine", tmp.path)
            if (plist(tmp, "CFBundleVersion") != plist(src, "CFBundleVersion") || plist(tmp, "CFBundleIdentifier") != ID) {
                sh(60, "rm", "-rf", tmp.path); throw java.io.IOException("copy does not match")
            }
            if (dest.exists()) {                                          // an older TV Remote.app: the same app, replaced
                if (!ours(dest) || !trash(dest)) { sh(60, "rm", "-rf", tmp.path); throw java.io.IOException(dest.path) }
            }
            run("mv", tmp.path, dest.path)
            val args = System.getenv("TVR_MOVE_ARGS").orEmpty()      // tests: another port, or "none" = do not open the copy
            val pid = ProcessHandle.current().pid()
            // opened once this process is gone (it holds the port); the old copy goes to the Trash right away
            val script = "while kill -0 $pid 2>/dev/null; do sleep 0.3; done; open -n \"\$1\" --args \$2"       // $2: arguments for the copy (tests: another port)
            if (args != "none") ProcessBuilder("/usr/bin/nohup", "/bin/sh", "-c", script, "sh", dest.path, args).redirectErrorStream(true)
                .redirectOutput(File(System.getProperty("java.io.tmpdir"), "tv-remote-move.log")).start()
            if (ours(src)) trash(src)
            println("moved to ${dest.path}")
            quit()
            null
        } catch (e: Exception) {
            println("move: $e")
            Words.get(lang(), "pc.move_failed", "why" to (e.message ?: e.javaClass.simpleName))
        }
    }

    /** The folder the app was unzipped in: translocation mounts it (`mount` lists the real path), else Downloads when it is the same build. */
    private fun original(bundle: File): File? {
        System.getenv("TVR_ORIGINAL")?.let { return File(it) }        // tests: where a translocated copy came from
        if ("/AppTranslocation/" !in bundle.path) return bundle
        val root = Regex(".*/AppTranslocation/[^/]+/d").find(bundle.path)?.value
        if (root != null) {
            for (l in sh(10, "mount").second.lines()) {
                val i = l.indexOf(" on $root ")
                if (i > 0) File(l.substring(0, i)).let { f -> if (ours(f)) return f }
            }
        }
        val d = File(home, "Downloads/${bundle.name}")
        return d.takeIf { ours(it) && plist(it, "CFBundleVersion") == plist(bundle, "CFBundleVersion") }
    }

    /** Only a TV Remote.app is ever removed or replaced. */
    fun ours(f: File) = f.isDirectory && f.name.endsWith(".app") && plist(f, "CFBundleIdentifier") == ID

    private fun plist(app: File, key: String): String =
        sh(10, "plutil", "-extract", key, "raw", File(app, "Contents/Info.plist").path).second.trim()

    /** To the Trash through Finder (it can be put back); where Finder will not be driven, moved into ~/.Trash. */
    private fun trash(f: File): Boolean {
        if (!ours(f)) return false
        if (System.getenv("TVR_MOVE_NOFINDER") == null && '"' !in f.path && '\\' !in f.path &&
            sh(20, "osascript", "-e", "tell application \"Finder\" to delete POSIX file \"${f.path}\"").first == 0 && !f.exists()) return true
        val t = File(home, ".Trash").also { it.mkdirs() }
        return sh(30, "mv", f.path, File(t, f.name.removeSuffix(".app") + " " + System.currentTimeMillis() + ".app").path).first == 0
    }

    private fun run(vararg cmd: String) {
        val r = sh(120, *cmd)
        if (r.first != 0) throw java.io.IOException("${cmd[0]}: ${r.second}".trim())
    }

    private fun sh(secs: Long, vararg cmd: String): Pair<Int, String> = try {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = StringBuilder()
        val t = Thread { out.append(p.inputStream.bufferedReader().readText()) }.also { it.start() }
        if (!p.waitFor(secs, TimeUnit.SECONDS)) { p.destroyForcibly(); -1 to "timeout" } else { t.join(2000); p.exitValue() to out.toString() }
    } catch (e: Exception) {
        -1 to (e.message ?: "")
    }
}
