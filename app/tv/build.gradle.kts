plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// This app's own version: the VERSION file next to this one; bump it only when this app (or app/shared) changes.
val appVersion = (System.getenv("VERSION_NAME") ?: file("VERSION").readText()).trim()
val keystore = file("signing.jks")                                    // git-ignored; build.sh creates one on the first build

android {
    namespace = "dev.tvremote.setup"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.tvremote.setup"
        minSdk = 21
        targetSdk = 34
        versionName = appVersion
        versionCode = appVersion.split(".").map { it.toInt() }.let { it[0] * 10000 + it[1] * 100 + it[2] }
    }
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
            isMinifyEnabled = true                                    // drops the unused part of the Kotlin library; names are kept (see proguard-rules.pro)
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "../proguard-rules.pro")
            if (keystore.exists()) signingConfig = signingConfigs.getByName("release")
        }
        debug { if (keystore.exists()) signingConfig = signingConfigs.getByName("release") }   // same key, so a debug build installs over a release
    }
    androidResources { noCompress += "jar" }
    lint { checkReleaseBuilds = false }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    kotlinOptions { jvmTarget = "11" }
}

dependencies { implementation(project(":shared")) }
