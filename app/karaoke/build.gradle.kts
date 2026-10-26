plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// TV Karaoke: songs from YouTube (the official IFrame player in a WebView) queued from phones. Its own VERSION (bump it only when
// app/karaoke changes); it knows nothing of the other apps, and nothing of :shared but the TV look, the error guard and the QR.
val appVersion = (System.getenv("VERSION_NAME") ?: file("VERSION").readText()).trim()
val keystore = file("signing.jks")                                    // git-ignored; build.sh creates one on the first build
android {
    namespace = "dev.tvremote.karaoke"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.tvremote.karaoke"
        minSdk = 21
        targetSdk = 34
        versionName = appVersion
        versionCode = appVersion.split(".").map { it.toInt() }.let { 100000 + it[0] * 10000 + it[1] * 100 + it[2] }
    }
    signingConfigs {
        create("release") {
            storeFile = keystore
            storePassword = "tvkaraoke"
            keyAlias = "tvkaraoke"
            keyPassword = "tvkaraoke"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystore.exists()) signingConfig = signingConfigs.getByName("release")
        }
        debug { if (keystore.exists()) signingConfig = signingConfigs.getByName("release") }
    }
    lint { checkReleaseBuilds = false }
    sourceSets["main"].java.srcDirs(layout.buildDirectory.dir("generated/qr"), "../shared/src/guard/kotlin", "../shared/src/guard/android", "../shared/src/tvui/kotlin")
    sourceSets["main"].res.srcDirs("../shared/src/tvui/res")                 // tv_style.xml: ONE copy for the TV apps (app/DESIGN-TV.md)
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    kotlinOptions { jvmTarget = "11" }
    testOptions { unitTests.isReturnDefaultValues = true }
}
// Words: app/shared/i18n (langs.json + apps/<lang>/<module>.json) -> res/values-<lang> and the language list: app/gradle/i18n.gradle
extra["i18nApps"] = listOf("tvui")
apply(from = "../gradle/i18n.gradle")

dependencies {
    implementation("androidx.activity:activity:1.9.3")
    testImplementation("junit:junit:4.13.2")                          // ./gradlew :karaoke:testDebugUnitTest
    testImplementation("org.json:json:20231013")                      // android.jar's org.json is a stub on the JVM
}

// Qr.kt only needs android.graphics: copied from :shared (a srcDir of the whole folder would pull the web server in)
val copyQr = tasks.register<Copy>("copyQr") {
    from("../shared/src/main/kotlin/dev/tvremote/core/web/Qr.kt") { into("web") }
    into(layout.buildDirectory.dir("generated/qr/dev/tvremote/core"))
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(copyQr) }
