import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// The computer's server on the same Kotlin core the two apps use (the computer app): the core's
// files are compiled here as they are, without the few that only run on Android (picture, sound, the on-TV helpers).
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(17)
    sourceSets["main"].kotlin.srcDir("../shared/src/main/kotlin")
}
// Android only: decoding the picture and sound (TvAndroid, AudioOut), the classes started on the TV (Meta, Caps), the QR bitmap
sourceSets["main"].kotlin.exclude("**/core/TvAndroid.kt", "**/core/AudioOut.kt", "**/core/Meta.kt", "**/core/Caps.kt", "**/core/Qr.kt")

dependencies {
    implementation("org.json:json:20240303")                         // on Android org.json is part of the system
    implementation("net.java.dev.jna:jna:5.15.0")                    // the window: Window.kt calls native/<system>/webview
}

application {
    mainClass.set("dev.tvremote.desktop.MainKt")
    if ("mac" in System.getProperty("os.name").lowercase()) applicationDefaultJvmArgs = listOf("-XstartOnFirstThread")   // --window: Cocoa
}

// TV Remote.app, TV Remote.exe or tv-remote: its own trimmed Java, the page and the files the server sends to TVs, all
// inside, so nothing has to be installed. jpackage only builds for the system it runs on (an arm64 Mac gives an arm64 Mac app):
// the GitHub workflow (.github/workflows/desktop.yml) runs this on each system for a release.
//   ./gradlew :desktop:packageApp   ->  app/desktop/build/package/<the app>  +  TV-Remote-<os>-<arch>.zip / .tar.gz
val os = System.getProperty("os.name").lowercase().let { if ("mac" in it) "mac" else if ("win" in it) "windows" else "linux" }
val arch = System.getProperty("os.arch").let { if (it == "aarch64" || it == "arm64") "arm64" else "x64" }
val stage = layout.buildDirectory.dir("package-input")
// The phone's APK carries Meta and Caps (dex, which only the Android build makes), started on the TV for app names and
// pictures. Not kept in git: the one just built (app/android/build.sh; the release workflow puts the release's there), else
// the latest release's. Without it (offline) a dev run shows package names; the packaged app refuses to build.
val phoneApk = layout.buildDirectory.file("phone/tv-remote.apk").get().asFile
val fetchPhoneApk by tasks.registering {
    outputs.upToDateWhen { false }
    doLast {
        val built = rootDir.resolve("../android/build/tv-remote.apk")
        phoneApk.parentFile.mkdirs()
        if (built.isFile) { built.copyTo(phoneApk, true); return@doLast }
        if (phoneApk.isFile && System.currentTimeMillis() - phoneApk.lastModified() < 86_400_000) return@doLast
        try {
            URI("https://github.com/HoangTran0410/tv-remote/releases/latest/download/tv-remote.apk").toURL().openStream().use { i ->
                phoneApk.resolveSibling("tv-remote.apk.part").also { p -> p.outputStream().use { i.copyTo(it) }; p.renameTo(phoneApk) }
            }
        } catch (e: Exception) {
            logger.warn("tv-remote.apk: not downloaded ($e); app names on the TV show as package names")
        }
    }
}
tasks.named("installDist") { dependsOn(fetchPhoneApk) }
val stageApp by tasks.registering(Sync::class) {
    dependsOn("installDist")
    doFirst { check(phoneApk.isFile) { "no tv-remote.apk: build it (app/android/build.sh) or go online" } }
    into(stage)
    from(layout.buildDirectory.dir("install/desktop/lib"))
    into("res") {                                                     // the repo's own layout: Server.kt reads the same paths
        from("../..") { include("VERSION", "CHANGELOG.md") }
        from("../shared/web") { into("app/shared/web") }
        from("../shared/assets/scrcpy-server.jar") { into("app/shared/assets") }
        from(phoneApk) { into("app/android") }                       // Meta and Caps, see fetchPhoneApk
        from("native") { include(if (os == "mac") "mac/**" else "$os-$arch/**"); into("app/desktop/native") }   // the window (Window.kt)
    }
}
val appName = if (os == "linux") "tv-remote" else "TV Remote"         // Linux: a launcher name without a space
val packageOut = layout.buildDirectory.dir("package").get().asFile
val packageApp by tasks.registering(Exec::class) {
    dependsOn(stageApp)
    val version = rootDir.resolve("../../VERSION").readText().trim()
    val jpackage = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }.get().metadata.installationPath
        .file(if (os == "windows") "bin/jpackage.exe" else "bin/jpackage").asFile
    doFirst { packageOut.deleteRecursively(); packageOut.mkdirs() }
    commandLine(listOf(jpackage.path, "--type", "app-image", "--name", appName, "--app-version", version, "--vendor", "tv-remote",
        "--input", stage.get().asFile.path, "--main-jar", "desktop.jar", "--main-class", "dev.tvremote.desktop.MainKt", "--dest", packageOut.path,
        "--add-modules", "java.base,java.logging,jdk.crypto.ec",           // TLS of the remote protocol (6467, 6466); JNA logs
        "--jlink-options", "--strip-debug --no-man-pages --no-header-files --compress=2",
        "--java-options", "-Dtvr.res=\$APPDIR/res", "--java-options", "-Xmx256m") + when (os) {
            "mac" -> listOf("--java-options", "-XstartOnFirstThread",   // the window's Cocoa loop must run on the first thread
                "--icon", projectDir.resolve("icon.icns").path, "--mac-package-identifier", "dev.tvremote.desktop")
            "windows" -> listOf("--icon", projectDir.resolve("icon.ico").path)   // no --win-console: no black window; the log is server.log
            else -> listOf("--icon", rootDir.resolve("../shared/web/icon-512.png").path)
        })
    doLast {                                                          // without its languages listed, WebKit tells the page "en" on a Vietnamese Mac
        if (os != "mac") return@doLast
        val app = packageOut.resolve("TV Remote.app")
        exec { commandLine("plutil", "-replace", "CFBundleLocalizations", "-json", "[\"vi\",\"en\"]", app.resolve("Contents/Info.plist").path) }
        exec { commandLine("codesign", "--force", "--deep", "--sign", "-", app.path) }   // signed again over the changed Info.plist
    }
}
// the archive people download: zip on Mac (ditto keeps the app's signature and links) and Windows, tar.gz on Linux (modes)
val packageArchive by tasks.registering {
    dependsOn(packageApp)
    doLast {
        val base = "TV-Remote-$os-$arch"
        when (os) {
            "mac" -> exec { workingDir = packageOut; commandLine("ditto", "-c", "-k", "--keepParent", "TV Remote.app", "$base.zip") }
            "linux" -> exec { workingDir = packageOut; commandLine("tar", "-czf", "$base.tar.gz", appName) }
            else -> ZipOutputStream(packageOut.resolve("$base.zip").outputStream()).use { z ->   // not Ant's zip: it splits "TV Remote/**" at the space
                packageOut.resolve(appName).walkTopDown().filter { it.isFile }.forEach { f ->
                    z.putNextEntry(ZipEntry(f.relativeTo(packageOut).invariantSeparatorsPath))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
    }
}
packageApp { finalizedBy(packageArchive) }
