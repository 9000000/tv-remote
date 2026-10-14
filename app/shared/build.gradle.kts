// Code both apps use: the ADB client, the control session, the small HTTP server, TV management and the update check.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.tvremote.core"
    compileSdk = 35
    defaultConfig { minSdk = 21 }
    // assets/scrcpy-server.jar is started on the TV for the picture and the input; web/index.html is the page the apps serve
    sourceSets["main"].assets.srcDirs("assets", "web")
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    kotlinOptions { jvmTarget = "11" }
}

// Unit tests of the plain logic (scenes, voice commands, the sound downmix, paths): ./gradlew :shared:testDebugUnitTest.
// org.json is the real library here: Android's own is only a stub off a device.
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
