// Code both apps use: the ADB client, the control session, the small HTTP server, TV management and the update check.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.tvremote.core"
    compileSdk = 35
    defaultConfig { minSdk = 21 }
    lint { checkOnly += "NewApi" }                                   // the same check as the apps (release.sh): this code runs on their Android 5-6 too
    // assets/scrcpy-server.jar is started on the TV for the picture and the input; web/index.html is the page the apps serve
    sourceSets["main"].assets.srcDirs("assets", "web", layout.buildDirectory.dir("generated/assets/i18n"))   // + i18n/: the page's words (below)
    sourceSets["main"].java.srcDirs("src/home/kotlin")                 // HomeConfig: TV Home's settings, compiled into app/tvhome too
    sourceSets["main"].java.srcDirs("src/guard/kotlin", "src/guard/android")   // Guard: the error guard, compiled into app/tvhome, channels and games too
    sourceSets["main"].java.srcDirs("src/report/kotlin")               // Report + Scrub: the sending core, compiled into app/tvhome, channels and games too
    sourceSets["main"].java.srcDirs("src/sign/kotlin")                 // Ed25519: compiled into app/tvchannels too (its signed providers.json)
    sourceSets["main"].java.srcDirs("src/tvapps/kotlin")                // TV Apps' list and APK checks: compiled into app/tvapps too
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    kotlinOptions { jvmTarget = "11" }
}
// Words: app/shared/i18n (langs.json + apps/<lang>/<module>.json) -> res/values-<lang> and the language list: app/gradle/i18n.gradle
extra["i18nLangs"] = true
apply(from = "../gradle/i18n.gradle")
// The page's words (langs.json, <lang>.json) next to the page in the assets, at i18n/ as the page asks for them; apps/ is built into res
val i18nAssets by tasks.registering(Sync::class) {
    from("i18n") { exclude("apps/**") }
    into(layout.buildDirectory.dir("generated/assets/i18n/i18n"))
}
tasks.named("preBuild") { dependsOn(i18nAssets) }

// Unit tests of the plain logic (scenes, voice commands, the sound downmix, paths): ./gradlew :shared:testDebugUnitTest.
// org.json is the real library here: Android's own is only a stub off a device.
dependencies {
    api("com.android.tools.build:apksig:8.9.2")                      // AppCopy: signs the rewritten APK (Apache-2.0, ~0.4 MB; JVM, and Android 8+ for the phone app)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
// The tests read tests/*_cases.json and web/remote.json from disk: inputs, so a changed case table runs them again
tasks.withType<Test>().configureEach { inputs.dir("tests"); inputs.file("web/remote.json"); inputs.dir("web/voice"); inputs.dir("../../docs/voice") }

// assets/tvr-helper.jar: Meta, Caps, Playing and Screen alone, dexed for app_process on the TV, where the phone and the computer copy it for app
// names, pictures, the hardware report and what is playing (a few KB, where the whole phone APK was copied before). Java, so no Kotlin library
// has to go along. Made again with every Android build and kept in git, so the computer app builds without the Android SDK.
val helperClasses by tasks.registering(JavaCompile::class) {
    source = fileTree("src/main/java") { include("dev/tvremote/core/Meta.java", "dev/tvremote/core/Caps.java", "dev/tvremote/core/Playing.java", "dev/tvremote/core/Screen.java", "dev/tvremote/core/Speed.java") }
    classpath = files()
    options.bootstrapClasspath = files(android.bootClasspath)
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.compilerArgs.add("-Xlint:-options")
    destinationDirectory.set(layout.buildDirectory.dir("tvhelper/classes"))
}
val tvHelper by tasks.registering {
    dependsOn(helperClasses)
    val classes = layout.buildDirectory.dir("tvhelper/classes")
    val out = file("assets/tvr-helper.jar")
    inputs.dir(classes)
    outputs.file(out)
    doLast {
        val d8 = android.sdkDirectory.resolve("build-tools/${android.buildToolsVersion}/" + if ("win" in System.getProperty("os.name").lowercase()) "d8.bat" else "d8")
        val files = classes.get().asFile.walk().filter { it.isFile && it.name.endsWith(".class") }.map { it.path }.sorted().toList()
        val tmp = layout.buildDirectory.file("tvhelper/tvr-helper.jar").get().asFile
        tmp.delete()
        val p = ProcessBuilder(listOf(d8.path, "--release", "--min-api", "21", "--lib", android.bootClasspath.first().path, "--output", tmp.path) + files)
            .redirectErrorStream(true).start()
        val said = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { "d8: $said" }
        if (!out.isFile || !out.readBytes().contentEquals(tmp.readBytes())) tmp.copyTo(out, true)
    }
}
tasks.named("preBuild") { dependsOn(tvHelper) }
