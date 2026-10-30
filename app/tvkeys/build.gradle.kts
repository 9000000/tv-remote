plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// TV Setup Keys: the two accessibility services of TV Setup (the dead remote buttons, the key that wakes the screen), apart from it so TV Setup
// itself declares none (Play Protect blocks an app that does, when it comes from a browser or Downloader; dev/decisions/apps.md "TV Setup declares no"). It has NO number of its
// own: it is TV Setup's (app/tv/VERSION) and is packed inside tv-setup.apk, so the two always move together. Signed with TV Setup's key,
// which is what lets them trust each other (a signature permission, KeysProtocol.PERMISSION).
val appVersion = (System.getenv("VERSION_NAME") ?: file("../tv/VERSION").readText()).trim()
val keystore = file("../tv/signing.jks")                              // git-ignored; app/tv/build.sh creates one on the first build

android {
    namespace = "dev.tvremote.setup.keys"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.tvremote.setup.keys"
        minSdk = 21
        targetSdk = 34
        versionName = appVersion
        versionCode = appVersion.split(".").map { it.toInt() }.let { 100000 + it[0] * 10000 + it[1] * 100 + it[2] }   // as TV Setup's
    }
    sourceSets["main"].java.srcDirs("src/protocol/kotlin")             // KeysProtocol: ONE copy, TV Setup compiles it too
    signingConfigs {
        create("release") {
            storeFile = keystore
            storePassword = "tvsetup"
            keyAlias = "tvsetup"
            keyPassword = "tvsetup"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true                                    // the Kotlin library alone is 600 KB; the services are kept by the manifest
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (keystore.exists()) signingConfig = signingConfigs.getByName("release")
        }
        debug { if (keystore.exists()) signingConfig = signingConfigs.getByName("release") }     // same key as TV Setup's debug build
    }
    lint { checkReleaseBuilds = false; checkOnly += "NewApi" }        // lintDebug: a call newer than minSdk 21 (Android 5-6 boxes crashed); release.sh runs it
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    kotlinOptions { jvmTarget = "11" }
}
// Words: app/shared/i18n (langs.json + apps/<lang>/<module>.json) -> res/values-<lang> and the language list: app/gradle/i18n.gradle
extra["i18nApps"] = listOf("tvkeys")
apply(from = "../gradle/i18n.gradle")
