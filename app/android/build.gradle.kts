import java.time.LocalDate

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// This app's own version: the VERSION file next to this one; bump it only when this app (or app/shared) changes.
val appVersion = (System.getenv("VERSION_NAME") ?: file("VERSION").readText()).trim()
val keystore = file("signing.jks")                                    // git-ignored; build.sh creates one on the first build

android {
    namespace = "dev.tvremote.phone"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.tvremote.phone"
        minSdk = 21
        targetSdk = 34
        versionName = appVersion
        resValue("string", "released", LocalDate.now().toString())   // shown next to the version
        // 100000 +: above every code of the numbers before the restart at 0.1.0 (2.24.x was 22400), so Android installs over them
        versionCode = appVersion.split(".").map { it.toInt() }.let { 100000 + it[0] * 10000 + it[1] * 100 + it[2] }
        // Conscrypt's native code is 2/3 of the APK: no 32-bit x86 (no phone has one); x86_64 stays for emulators on Intel and Chromebooks
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    signingConfigs {
        create("release") {
            storeFile = keystore
            storePassword = "tvremote"
            keyAlias = "tvremote"
            keyPassword = "tvremote"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true                                    // drops the unused part of the Kotlin library; names are kept (see proguard-rules.pro)
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "../shared/proguard-rules.pro")
            if (keystore.exists()) signingConfig = signingConfigs.getByName("release")
        }
        debug { if (keystore.exists()) signingConfig = signingConfigs.getByName("release") }   // same key, so a debug build installs over a release
    }
    androidResources {
        noCompress += "jar"
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~:!README.md"   // the default, and the notes beside the jars
    }
    lint { checkReleaseBuilds = false }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    kotlinOptions { jvmTarget = "11" }
    sourceSets["main"].java.srcDirs("../shared/conscrypt", "../shared/src/selfupdate/kotlin", "../shared/src/marks/kotlin")             // ConscryptBackend: TLS 1.3 for Wireless debugging (core/AdbTls.kt); SelfUpdate.stage: the shared download + install session
}
// Words: app/shared/i18n (langs.json + apps/<lang>/<module>.json) -> res/values-<lang> and the language list: app/gradle/i18n.gradle
extra["i18nApps"] = listOf("android")
apply(from = "../gradle/i18n.gradle")

dependencies {
    implementation(project(":shared"))
    implementation("org.conscrypt:conscrypt-android:2.7.0")           // adb pairing: TLS 1.3 and the exported keying material, on Android 10+
    testImplementation("junit:junit:4.13.2")                          // RemoteSpecTest: remote.json against the handlers of PhoneRemote.kt
    testImplementation("org.json:json:20240303")                      // the real org.json: Android's own is only a stub off a device
}

tasks.withType<Test>().configureEach { inputs.file("../shared/web/remote.json") }   // read from disk: a changed file runs the tests again
