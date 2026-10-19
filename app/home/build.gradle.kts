import java.time.LocalDate

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// TV Home, the launcher: its own VERSION (bump it only when app/home or app/shared/src/home changes). It does not use :shared,
// which would bring the web page and the helper jars (1.8 MB) along: only HomeConfig.kt, the settings both sides read.
val appVersion = (System.getenv("VERSION_NAME") ?: file("VERSION").readText()).trim()
val keystore = file("signing.jks")                                    // git-ignored; build.sh creates one on the first build

android {
    namespace = "dev.tvremote.home"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.tvremote.home"
        minSdk = 21
        targetSdk = 34
        versionName = appVersion
        resValue("string", "released", LocalDate.now().toString())
        versionCode = appVersion.split(".").map { it.toInt() }.let { 100000 + it[0] * 10000 + it[1] * 100 + it[2] }
    }
    sourceSets["main"].java.srcDirs("../shared/src/home/kotlin")
    signingConfigs {
        create("release") {
            storeFile = keystore
            storePassword = "tvhome"
            keyAlias = "tvhome"
            keyPassword = "tvhome"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true                                  // the shared icon set: only the few drawn here stay
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (keystore.exists()) signingConfig = signingConfigs.getByName("release")
        }
        debug { if (keystore.exists()) signingConfig = signingConfigs.getByName("release") }
    }
    lint { checkReleaseBuilds = false }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    kotlinOptions { jvmTarget = "11" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")                          // ./gradlew :home:testDebugUnitTest
    testImplementation("org.json:json:20240303")
}
