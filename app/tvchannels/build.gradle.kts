plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// TV Channels, the player of iptv-org's public playlists: its own VERSION (bump it only when app/tvchannels changes). It knows
// nothing of TV Home or the clients: they reach it through its deeplinks (tvchannels://play/<id>) and Android's pinned shortcuts.
val appVersion = (System.getenv("VERSION_NAME") ?: file("VERSION").readText()).trim()
val keystore = file("signing.jks")                                    // git-ignored; build.sh creates one on the first build

android {
    namespace = "dev.tvremote.channels"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.tvremote.channels"
        minSdk = 21
        targetSdk = 34
        versionName = appVersion
        versionCode = appVersion.split(".").map { it.toInt() }.let { 100000 + it[0] * 10000 + it[1] * 100 + it[2] }
    }
    signingConfigs {
        create("release") {
            storeFile = keystore
            storePassword = "tvchannels"
            keyAlias = "tvchannels"
            keyPassword = "tvchannels"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (keystore.exists()) signingConfig = signingConfigs.getByName("release")
        }
        debug { if (keystore.exists()) signingConfig = signingConfigs.getByName("release") }
    }
    lint { checkReleaseBuilds = false; checkOnly += "NewApi" }        // lintDebug: a call newer than minSdk 21 (Android 5-6 boxes crashed); release.sh runs it
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    kotlinOptions { jvmTarget = "11" }
    sourceSets["main"].java.srcDirs("../shared/src/guard/kotlin", "../shared/src/guard/android", "../shared/src/report/kotlin", "../shared/src/report/android", "../shared/src/tvui/kotlin", "../shared/src/marks/kotlin", "../shared/src/sign/kotlin", "../shared/src/selfupdate/kotlin")   // Ed25519: providers.json is signed; Release + SelfUpdate: its own updates
    sourceSets["main"].res.srcDirs("../shared/src/tvui/res")                 // tv_style.xml: ONE copy for the TV apps (app/DESIGN-TV.md)
    sourceSets["main"].assets.srcDirs("../../docs/channels")          // providers.json + .sig: the copy used until the site's is read
}
// Words: app/shared/i18n (langs.json + apps/<lang>/<module>.json) -> res/values-<lang> and the language list: app/gradle/i18n.gradle
extra["i18nApps"] = listOf("channels", "tvui")
extra["i18nLangs"] = true
apply(from = "../gradle/i18n.gradle")

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.4.1")          // HLS and plain HTTP streams; no media3-ui: one SurfaceView is enough
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")        // a country can have 2 000 channels
    testImplementation("junit:junit:4.13.2")                          // ./gradlew :tvchannels:testDebugUnitTest
    testImplementation("org.json:json:20240303")                      // the real org.json: Android's own is only a stub off a device
}
