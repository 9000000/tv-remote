plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// TV Apps, the free open-source apps of docs/apps/apps.json for the TV: its own VERSION (bump it only when app/tvapps changes).
val appVersion = (System.getenv("VERSION_NAME") ?: file("VERSION").readText()).trim()
val keystore = file("signing.jks")                                    // git-ignored; build.sh creates one on the first build

android {
    namespace = "dev.tvremote.apps"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.tvremote.apps"
        minSdk = 21
        targetSdk = 34
        versionName = appVersion
        versionCode = appVersion.split(".").map { it.toInt() }.let { 100000 + it[0] * 10000 + it[1] * 100 + it[2] }
    }
    signingConfigs {
        create("release") {
            storeFile = keystore
            storePassword = "tvapps"
            keyAlias = "tvapps"
            keyPassword = "tvapps"
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
    sourceSets["main"].java.srcDirs("../shared/src/guard/kotlin", "../shared/src/guard/android", "../shared/src/report/kotlin", "../shared/src/report/android", "../shared/src/tvui/kotlin", "../shared/src/sign/kotlin", "../shared/src/selfupdate/kotlin", "../shared/src/tvapps/kotlin")   // the signed list, ApkCert, SelfUpdate: shared with the page's TvAppsApi
    sourceSets["main"].res.srcDirs("../shared/src/tvui/res")                 // tv_style.xml: ONE copy for the TV apps (app/DESIGN-TV.md)
}
// Words: app/shared/i18n (langs.json + apps/<lang>/<module>.json) -> res/values-<lang> and the language list: app/gradle/i18n.gradle
extra["i18nApps"] = listOf("tvapps", "tvui")
extra["i18nLangs"] = true
apply(from = "../gradle/i18n.gradle")

dependencies {
    testImplementation("junit:junit:4.13.2")
}
