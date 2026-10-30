package dev.tvremote.desktop

import dev.tvremote.update.Release
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Updates of the packaged app from GitHub releases, as the two apps have: checked at start and every few hours; a newer
 * one is downloaded and unpacked next to the app at once, so "Cập nhật" only swaps it in and opens it again (a few seconds).
 * Quitting with one ready swaps it in too, so the next start is the new one. The swap is done by a small script once this
 * process is gone (a running app cannot replace itself on Windows); the app's data folder is not touched.
 * Where the app cannot write its own folder (Program Files, a Mac app run from the Downloads folder without moving it: macOS
 * runs it from a read-only copy), the page offers the download instead.
 */
class Updater(private val version: String, res: File?, private val quit: () -> Unit) {
    private val os = System.getProperty("os.name").lowercase().let { if ("mac" in it) "mac" else if ("win" in it) "windows" else "linux" }
    private val arch = if (System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }) "arm64" else "x64"
    val asset = if (os == "mac") "TV-Remote-mac.zip" else "TV-Remote-$os-$arch." + if (os == "linux") "tar.gz" else "zip"   // Mac: one app for both chips
    /** The app itself: TV Remote.app, the "TV Remote" folder, tv-remote; $APPDIR (res's parent) is inside it. null: run from the repo. */
    private val root: File? = res?.absoluteFile?.parentFile?.let { if (os == "windows") it.parentFile else it.parentFile?.parentFile }
    val bundle: File? get() = System.getenv("TVR_FAKE_BUNDLE")?.let { File(it) } ?: root   // tests: a made-up translocated path
                                       // the app's folder (Mover)
    private val work = root?.let { File(it.parentFile, ".tv-remote-update") }
    private val timer = Executors.newSingleThreadScheduledExecutor { Thread(it, "update").apply { isDaemon = true } }

    @Volatile private var found: Release? = null
    @Volatile private var state = "idle"                              // idle, checking, downloading, ready, installing, error
    @Volatile private var pct = 0
    private val installing = java.util.concurrent.atomic.AtomicBoolean(false)
    private val swapping = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var error = ""
    @Volatile private var fresh: File? = null                         // the new app, unpacked in [work]

    /** The app can replace itself: packaged, and its folder writable (not run from macOS's read-only copy). */
    private val writable: Boolean get() {
        val r = root ?: return false
        return r.isDirectory && "/AppTranslocation/" !in r.path && r.parentFile.canWrite() && r.canWrite()
    }

    fun start() {
        System.getenv("TVR_RELEASES")?.let {                          // tests: releases from a fake GitHub, signed with a key of its own
            Release.api = it
            Release.testKeys = listOfNotNull(System.getenv("TVR_RELEASE_KEY"))
        }
        if (root == null) return                                      // the repo: git pull is the update
        if (state == "idle") work?.deleteRecursively()                // an earlier download that was never swapped in
        timer.scheduleWithFixedDelay({ check() }, 5, 6 * 3600L, TimeUnit.SECONDS)
    }

    fun close() { timer.shutdownNow() }

    @Synchronized fun check() {
        if (state == "downloading" || state == "ready" || state == "installing") return
        state = "checking"
        try {
            found = Release.newer(version, "web", asset)      // the computer app's number: a release may carry the last one's again
            error = ""
            state = "idle"
            if (found != null && writable) {                         // in the background: a "check" from the page answers at once
                state = "downloading"; pct = 0
                Thread({ synchronized(dl) { download() } }, "update-download").apply { isDaemon = true }.start()
            }
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            state = "idle"                                            // offline: asked again soon, nothing to show
            println("update check: $error")
            try { timer.schedule({ check() }, 15, TimeUnit.MINUTES) } catch (ignored: Exception) { }
        }
    }

    private val dl = Any()                                            // the download's own lock: check() is not held up for minutes
    @Volatile private var freshFor: Release? = null

    private fun download() {
        val r = found ?: return
        val dir = work ?: return
        if (freshFor === r && fresh?.isDirectory == true) { state = "ready"; return }   // Update pressed while it was downloading
        state = "downloading"; pct = 0
        try {
            dir.deleteRecursively(); dir.mkdirs()
            val file = File(dir, asset)
            r.download(file) { pct = it }
            val out = File(dir, "new").also { it.mkdirs() }
            when (os) {
                "mac" -> run("ditto", "-x", "-k", file.path, out.path)   // keeps the app's signature and links
                "linux" -> run("tar", "-xzf", file.path, "-C", out.path)
                else -> unzip(file, out)
            }
            file.delete()
            fresh = out.listFiles()?.singleOrNull { it.isDirectory } ?: throw java.io.IOException("unexpected archive")
            freshFor = r
            state = "ready"
            println("update ${r.version} ready: $fresh")
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            state = "error"
            dir.deleteRecursively()
            println("update download: $error")
        }
    }

    /** The notes made short per language ([Release.brief], the computer app's lines first): the page picks the one it speaks. */
    private fun brief(r: Release?): JSONObject {
        val o = JSONObject()
        if (r == null) return o
        for ((lang, vi) in listOf("vi" to true, "en" to false)) {
            val b = Release.brief(r.notes, vi, listOf("app máy tính", "computer app", "và app máy tính", "and computer apps"))
            o.put(lang, JSONObject().put("items", org.json.JSONArray(b.items)).put("more", b.more))
        }
        return o
    }

    fun json(): JSONObject {
        val r = found
        return JSONObject().put("current", version).put("packaged", root != null).put("state", state).put("pct", pct).put("error", error)
            .put("version", r?.version ?: "").put("notes", r?.notes ?: "").put("brief", brief(r)).put("auto", writable).put("move", Mover.can(bundle))
            .put("url", "https://github.com/${Release.REPO}/releases/latest/download/$asset")
    }

    /** "Cập nhật": swaps the downloaded app in and opens it; downloads it first if it is not here yet (after an error). */
    @Synchronized fun install() {
        require(found != null) { "no update" }
        require(writable) { "cannot replace the app here" }
        if (state == "installing" || !installing.compareAndSet(false, true)) return   // a second press: the first is on its way
        val ready = state == "ready"
        state = if (ready) "installing" else "downloading"            // the page shows it from this very answer
        Thread({
            if (!ready) synchronized(dl) { download() }       // after the one in the background: it finds that one's
            if (!ready && state != "ready") { installing.set(false); return@Thread }
            state = "installing"
            if (swap(true)) quit() else { state = "error"; installing.set(false) }
        }, "update-install").apply { isDaemon = true }.start()
    }

    /** Leaving with an update ready: it is swapped in once this process is gone, not opened. */
    fun quitting() { if (state == "ready") swap(false) }

    /** Starts the script that waits for this process to end, puts the new app where the old one was, and opens it ([open]). */
    private fun swap(open: Boolean): Boolean {
        if (!swapping.compareAndSet(false, true)) return true         // already started (Update, then quitting): one script only
        val old = root ?: return false
        val new = fresh ?: return false
        val dir = work ?: return false
        val pid = ProcessHandle.current().pid().toString()
        return try {
            // not in [work]: the script removes that folder when done. A folder of our own (0700 where there are modes), never a
            // fixed name in a shared /tmp that another user could pre-create as a link or rewrite before it runs.
            val tmp = java.nio.file.Files.createTempDirectory("tv-remote-update").toFile()
            val log = File(tmp, "tv-remote-update.log")
            val cmd = if (os == "windows") {
                val exe = old.listFiles()?.firstOrNull { it.name.endsWith(".exe") }?.name ?: "TV Remote.exe"
                val ps = File(tmp, "tv-remote-update.ps1").apply { writeText(PS1) }
                listOf("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", ps.path,
                    pid, old.path, new.path, dir.path, if (open) exe else "")
            } else {
                val run = if (!open) "" else if (os == "mac") "open" else "bin/" + (File(old, "bin").list()?.firstOrNull() ?: "tv-remote")
                val sh = File(tmp, "tv-remote-update.sh").apply { writeText(SH) }
                listOf("/usr/bin/nohup", "/bin/sh", sh.path, pid, old.path, new.path, dir.path, run)
            }
            val pb = ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log)
            pb.environment().keys.removeIf { it.startsWith("_JPACKAGE") }   // Linux: set by our launcher, the new one would skip its setup
            pb.start()                                                // outlives this process
            println("update: swapping in ${found?.version}${if (open) ", then opening it" else ""} ($cmd, log $log)")
            true
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            println("update swap: $error")
            swapping.set(false)
            false
        }
    }

    private fun run(vararg cmd: String) {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (p.waitFor() != 0) throw java.io.IOException("${cmd[0]}: $out".trim())
    }

    private fun unzip(zip: File, to: File) {
        val base = to.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val f = File(to, e.name)
                if (!f.canonicalPath.startsWith(base)) throw java.io.IOException("bad path in the archive: ${e.name}")
                if (e.isDirectory) f.mkdirs() else { f.parentFile.mkdirs(); f.outputStream().use { z.copyTo(it) } }
            }
        }
    }

    companion object {
        // $1 pid  $2 the app  $3 the new app  $4 the work folder  $5 what to open ("" none, "open" on a Mac, else the launcher)
        private val SH = """
            while kill -0 "$1" 2>/dev/null; do sleep 0.3; done
            if mv "$2" "$4/old"; then
              if mv "$3" "$2"; then rm -rf "$4"; else mv "$4/old" "$2"; fi
            fi
            if [ "$5" = open ]; then open "$2"; elif [ -n "$5" ]; then "$2/$5" & fi   # its words go to the update log
        """.trimIndent() + "\n"

        // the same on Windows: files of a process just ended can stay locked a moment, so each move is tried for a while
        private val PS1 = """
            param(${'$'}p, ${'$'}old, ${'$'}new, ${'$'}work, ${'$'}exe)
            while (Get-Process -Id ${'$'}p -ErrorAction SilentlyContinue) { Start-Sleep -Milliseconds 300 }
            function Try-Move(${'$'}a, ${'$'}b) { for (${'$'}i = 0; ${'$'}i -lt 40; ${'$'}i++) { try { Move-Item -LiteralPath ${'$'}a -Destination ${'$'}b -ErrorAction Stop; return ${'$'}true } catch { Start-Sleep -Milliseconds 500 } }; return ${'$'}false }
            if (Try-Move ${'$'}old "${'$'}work\old") {
              if (Try-Move ${'$'}new ${'$'}old) { Remove-Item -LiteralPath ${'$'}work -Recurse -Force -ErrorAction SilentlyContinue }
              else { Try-Move "${'$'}work\old" ${'$'}old | Out-Null }
            }
            if (${'$'}exe) { Start-Process -FilePath (Join-Path ${'$'}old ${'$'}exe) }
        """.trimIndent() + "\r\n"
    }
}
