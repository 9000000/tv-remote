// The computer's server on the same Kotlin core the two apps use (a trial, next to the Python one in app/web): the core's
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
}

application {
    mainClass.set("dev.tvremote.desktop.MainKt")
}

// TV Remote.app (or the Windows / Linux app folder): its own trimmed Java, the page and the files the server sends to TVs,
// all inside, so nothing has to be installed. jpackage builds for the machine it runs on (an arm64 Mac gives an arm64 app).
//   ./gradlew :desktop:packageApp   ->  app/desktop/build/package/TV Remote.app  (+ TV-Remote-mac.zip)
val stage = layout.buildDirectory.dir("package-input")
val stageApp by tasks.registering(Sync::class) {
    dependsOn("installDist")
    into(stage)
    from(layout.buildDirectory.dir("install/desktop/lib"))
    into("res") {                                                     // the repo's own layout: Server.kt reads the same paths
        from("../..") { include("VERSION", "CHANGELOG.md") }
        from("../shared/web") { into("app/shared/web") }
        from("../shared/assets/scrcpy-server.jar") { into("app/shared/assets") }
        from("../android/tv-remote.apk") { into("app/android") }      // the released phone app: Meta and Caps, run on the TV
    }
}
tasks.register<Exec>("packageApp") {
    dependsOn(stageApp)
    val out = layout.buildDirectory.dir("package").get().asFile
    val version = rootDir.resolve("../../VERSION").readText().trim()
    val mac = System.getProperty("os.name").lowercase().contains("mac")
    val jpackage = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }.get().metadata.installationPath.file("bin/jpackage").asFile
    doFirst { out.deleteRecursively(); out.mkdirs() }
    commandLine(listOf(jpackage.path, "--type", "app-image", "--name", "TV Remote", "--app-version", version, "--vendor", "tv-remote",
        "--input", stage.get().asFile.path, "--main-jar", "desktop.jar", "--main-class", "dev.tvremote.desktop.MainKt", "--dest", out.path,
        "--add-modules", "java.base,java.desktop,jdk.crypto.ec",           // jdk.crypto.ec: the TLS of the remote protocol (6467, 6466)
        "--jlink-options", "--strip-debug --no-man-pages --no-header-files --compress=2",
        "--java-options", "-Dtvr.res=\$APPDIR/res", "--java-options", "-Dapple.awt.UIElement=true",   // no Dock icon: the page is the app, its "Tắt" quits
        "--java-options", "-Xmx256m") +
        (if (mac) listOf("--icon", rootDir.resolve("../../TV Remote.app/Contents/Resources/icon.icns").path, "--mac-package-identifier", "dev.tvremote.desktop") else emptyList()))
    doLast {
        if (mac) exec { workingDir = out; commandLine("ditto", "-c", "-k", "--keepParent", "TV Remote.app", "TV-Remote-mac.zip") }
    }
}
