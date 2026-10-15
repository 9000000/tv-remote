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
    val asset = "TV-Remote-$os-$arch." + if (os == "linux") "tar.gz" else "zip"
    /** The app itself: TV Remote.app, the "TV Remote" folder, tv-remote; $APPDIR (res's parent) is inside it. null: run from the repo. */
    private val root: File? = res?.absoluteFile?.parentFile?.let { if (os == "windows") it.parentFile else it.parentFile?.parentFile }
    private val work = root?.let { File(it.parentFile, ".tv-remote-update") }
    private val launcher = ProcessHandle.current().info().command().orElse(null)?.let { File(it) }
    private val timer = Executors.newSingleThreadScheduledExecutor { Thread(it, "update").apply { isDaemon = true } }

    @Volatile private var found: Release? = null
    @Volatile private var state = "idle"                              // idle, checking, downloading, ready, installing, error
    @Volatile private var pct = 0
    @Volatile private var error = ""
    @Volatile private var fresh: File? = null                         // the new app, unpacked in [work]

    /** The app can replace itself: packaged, and its folder writable (not run from macOS's read-only copy). */
    private val writable: Boolean get() {
        val r = root ?: return false
        return r.isDirectory && "/AppTranslocation/" !in r.path && r.parentFile.canWrite() && r.canWrite()
    }

    fun start() {
        System.getenv("TVR_RELEASES")?.let { Release.api = it }       // tests: releases from a fake GitHub
        if (root == null) return                                      // the repo: git pull is the update
        if (state == "idle") work?.deleteRecursively()                // an earlier download that was never swapped in
        timer.scheduleWithFixedDelay({ check() }, 5, 6 * 3600L, TimeUnit.SECONDS)
    }

    fun close() { timer.shutdownNow() }

    @Synchronized fun check() {
        if (state == "downloading" || state == "ready" || state == "installing") return
        state = "checking"
        try {
            found = Release.newer(version, "web", asset)
            error = ""
            state = "idle"
            if (found != null && writable) {                         // in the background: a "check" from the page answers at once
                state = "downloading"; pct = 0
                Thread({ synchronized(this) { download() } }, "update-download").apply { isDaemon = true }.start()
            }
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            state = "idle"                                            // offline: asked again later, nothing to show
            println("update check: $error")
        }
    }

    private fun download() {
        val r = found ?: return
        val dir = work ?: return
        state = "downloading"; pct = 0
        try {
            dir.deleteRecursively(); dir.mkdirs()
            val file = File(dir, asset)
            Release.download(r.url, file) { pct = it }
            val out = File(dir, "new").also { it.mkdirs() }
            when (os) {
                "mac" -> run("ditto", "-x", "-k", file.path, out.path)   // keeps the app's signature and links
                "linux" -> run("tar", "-xzf", file.path, "-C", out.path)
                else -> unzip(file, out)
            }
            file.delete()
            fresh = out.listFiles()?.singleOrNull { it.isDirectory } ?: throw java.io.IOException("unexpected archive")
            state = "ready"
            println("update ${r.version} ready: $fresh")
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            state = "error"
            dir.deleteRecursively()
            println("update download: $error")
        }
    }

    fun json(): JSONObject {
        val r = found
        return JSONObject().put("current", version).put("packaged", root != null).put("state", state).put("pct", pct).put("error", error)
            .put("version", r?.version ?: "").put("notes", r?.notes ?: "").put("auto", writable)
            .put("url", "https://github.com/${Release.REPO}/releases/latest/download/$asset")
    }

    /** "Cập nhật": swaps the downloaded app in and opens it; downloads it first if it is not here yet (after an error). */
    fun install() {
        require(found != null) { "no update" }
        require(writable) { "cannot replace the app here" }
        val ready = state == "ready"
        state = if (ready) "installing" else "downloading"            // the page shows it from this very answer
        Thread({
            if (!ready) synchronized(this) { download() }
            if (!ready && state != "ready") return@Thread
            state = "installing"
            if (swap(true)) quit() else state = "error"
        }, "update-install").apply { isDaemon = true }.start()
    }

    /** Leaving with an update ready: it is swapped in once this process is gone, not opened. */
    fun quitting() { if (state == "ready") swap(false) }

    /** Starts the script that waits for this process to end, puts the new app where the old one was, and opens it ([open]). */
    private fun swap(open: Boolean): Boolean {
        val old = root ?: return false
        val new = fresh ?: return false
        val dir = work ?: return false
        val pid = ProcessHandle.current().pid().toString()
        return try {
            val tmp = File(System.getProperty("java.io.tmpdir"))     // not in [work]: the script removes that folder when done
            val log = File(tmp, "tv-remote-update.log")
            val cmd = if (os == "windows") {
                val exe = launcher?.takeIf { it.path.startsWith(old.path) }?.relativeTo(old)?.path ?: "TV Remote.exe"
                val ps = File(tmp, "tv-remote-update.ps1").apply { writeText(PS1) }
                listOf("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", ps.path,
                    pid, old.path, new.path, dir.path, if (open) exe else "")
            } else {
                val run = if (!open) "" else if (os == "mac") "open" else launcher?.takeIf { it.path.startsWith(old.path) }?.relativeTo(old)?.path ?: "bin/tv-remote"
                val sh = File(tmp, "tv-remote-update.sh").apply { writeText(SH) }
                listOf("/usr/bin/nohup", "/bin/sh", sh.path, pid, old.path, new.path, dir.path, run)
            }
            ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log).start()   // outlives this process
            println("update: swapping in ${found?.version}${if (open) ", then opening it" else ""}")
            true
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            println("update swap: $error")
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
            if [ "$5" = open ]; then open "$2"; elif [ -n "$5" ]; then "$2/$5" >/dev/null 2>&1 & fi
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
